package com.carlb.split.wear.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTimestamp
import android.media.AudioManager
import android.media.MediaRecorder
import android.util.Log
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow

/**
 * Gunshot onset detection off the watch microphone.
 *
 * The physics, up front: a 9mm at the shooter's ear runs 160-165 dB SPL. The
 * MEMS capsule in the watch clips around 120-130 dB. Every shot overdrives the
 * capsule by 30-45 dB, so the amplitude we read is meaningless — it is simply
 * "railed". That is fine, because a shot timer does not need a level, it needs
 * an *arrival time*.
 *
 * It is better than fine, actually. Saturation is the single best discriminator
 * available: your own muzzle rails the converter for milliseconds, while the
 * shooter two bays over arrives ~30 dB down and never rails at all. That is what
 * [clipGate] keys on, and it is the reason this rejects neighbours when a plain
 * energy threshold cannot.
 *
 * Timing does NOT come from when a buffer happened to arrive — that is jittered
 * by scheduling. It comes from [AudioRecord.getTimestamp], which hands back a
 * (framePosition, nanoTime) pair from the audio HAL. Any sample's true wall
 * clock is interpolated from that anchor, which is why the resolution below is
 * one sample (20.8 us at 48 kHz) rather than one buffer (~10 ms).
 */
class ShotDetector(
    private val audioManager: AudioManager,
    private val onOnset: (OnsetEvent) -> Unit,
) {

    data class OnsetEvent(
        /** CLOCK_MONOTONIC nanos of the onset sample. Same timebase as the beep. */
        val monotonicNanos: Long,
        /** Consecutive railed samples in this transient. High == your gun. */
        val clipRun: Int,
        val peakDbfs: Double,
    )

    data class Config(
        val sensitivityDb: Int = -22,
        val clipGate: Boolean = true,
        val blankingMs: Int = 60,
    )

    @Volatile var config: Config = Config()
    @Volatile private var running = false

    /** Live input level for the calibration meter, dBFS. */
    @Volatile var levelDbfs: Double = -100.0
        private set
    @Volatile var clipping: Boolean = false
        private set

    var sampleRate: Int = 0
        private set
    var sourceName: String = "none"
        private set

    private var record: AudioRecord? = null
    private var thread: Thread? = null

    /**
     * Pin the rawest source the device exposes. This matters more than any
     * other line in the file: with AGC live, the gain ducks hard after the
     * first round and every split after it is measured through a moving
     * target. VOICE_RECOGNITION is the usual fallback because it is the one
     * legacy source specified to leave AGC and NS off.
     */
    private fun pickSource(): Pair<Int, String> {
        val unprocessedOk =
            audioManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
        return if (unprocessedOk) {
            MediaRecorder.AudioSource.UNPROCESSED to "UNPROCESSED"
        } else {
            MediaRecorder.AudioSource.VOICE_RECOGNITION to "VOICE_RECOGNITION"
        }
    }

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (running) return true

        val (source, name) = pickSource()
        sourceName = name

        val rate = intArrayOf(48000, 44100, 16000).firstOrNull { r ->
            AudioRecord.getMinBufferSize(r, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT) > 0
        } ?: return false
        sampleRate = rate

        val minBuf = AudioRecord.getMinBufferSize(
            rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        // Small buffers keep latency down; we still time by HAL timestamp, but a
        // short buffer means the UI tick and the haptic land closer to the shot.
        val bufBytes = maxOf(minBuf, BLOCK * 2 * 4)

        val r = try {
            AudioRecord(source, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufBytes)
        } catch (e: Exception) {
            Log.e(TAG, "AudioRecord construction failed", e); return false
        }
        if (r.state != AudioRecord.STATE_INITIALIZED) { r.release(); return false }

        record = r
        running = true
        r.startRecording()

        thread = Thread({ loop(r) }, "shot-detector").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
        return true
    }

    fun stop() {
        running = false
        thread?.join(500)
        thread = null
        record?.runCatching { stop() }
        record?.release()
        record = null
    }

    /** Discard onsets until this monotonic time — used to blank the start tone. */
    @Volatile private var suppressUntilNanos: Long = 0L
    fun suppressUntil(monotonicNanos: Long) { suppressUntilNanos = monotonicNanos }

    private fun loop(r: AudioRecord) {
        val buf = ShortArray(BLOCK)
        val ts = AudioTimestamp()

        var framesRead = 0L
        var anchorFrame = 0L
        var anchorNanos = 0L
        var haveAnchor = false

        var lastOnsetNanos = Long.MIN_VALUE
        var carryClipRun = 0

        while (running) {
            val n = r.read(buf, 0, BLOCK)
            if (n <= 0) continue

            // Refresh the frame<->nanotime anchor. Cheap, and it keeps us honest
            // against clock drift over a long session.
            if (r.getTimestamp(ts, AudioTimestamp.TIMEBASE_MONOTONIC) == AudioRecord.SUCCESS) {
                anchorFrame = ts.framePosition
                anchorNanos = ts.nanoTime
                haveAnchor = true
            }

            val threshold = 32767.0 * 10.0.pow(config.sensitivityDb / 20.0)
            val blankNanos = config.blankingMs * 1_000_000L
            val gate = config.clipGate

            var peak = 0
            var run = carryClipRun
            var maxRun = 0

            // Pass 1 - block statistics. The clip run has to be known before we
            // decide, because "did this rail?" is answered by the samples AFTER
            // the leading edge, not at it.
            for (i in 0 until n) {
                val a = abs(buf[i].toInt())
                if (a > peak) peak = a
                if (a >= CLIP_LEVEL) { run++; if (run > maxRun) maxRun = run } else run = 0
            }
            carryClipRun = run
            levelDbfs = if (peak <= 0) -100.0 else 20.0 * log10(peak / 32767.0)
            clipping = maxRun > 0

            // Pass 2 - onset. Only worth walking if the block could contain one.
            if (peak >= threshold && !(gate && maxRun < MIN_CLIP_RUN)) {
                for (i in 0 until n) {
                    if (abs(buf[i].toInt()) < threshold) continue

                    val absFrame = framesRead + i
                    val nanos = if (haveAnchor) {
                        anchorNanos + ((absFrame - anchorFrame) * 1_000_000_000.0 / sampleRate).toLong()
                    } else {
                        System.nanoTime()
                    }

                    if (nanos < suppressUntilNanos) continue
                    if (nanos - lastOnsetNanos < blankNanos) continue

                    lastOnsetNanos = nanos
                    onOnset(OnsetEvent(nanos, maxRun, levelDbfs))
                }
            }

            framesRead += n
        }
    }

    companion object {
        private const val TAG = "ShotDetector"
        private const val BLOCK = 512
        /** 16-bit full scale is 32767; treat the top ~2% as railed. */
        private const val CLIP_LEVEL = 32100
        /** Consecutive railed samples required when the clip gate is armed. */
        private const val MIN_CLIP_RUN = 3
    }
}
