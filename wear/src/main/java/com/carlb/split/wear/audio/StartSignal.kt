package com.carlb.split.wear.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlin.math.PI
import kotlin.math.sin

/**
 * The start tone and the start haptic. Both fire on the watch — the phone is
 * never in the timing path, so a dropped Bluetooth link cannot cost you a
 * string.
 *
 * The haptic is not a nicety. At a range you are in muffs and a watch speaker
 * is a small speaker; a wrist buzz cuts through hearing protection when a
 * 2 kHz tone does not.
 */
class StartSignal(context: Context) {

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }

    private val rate = 48_000
    private val toneMs = 320
    private val parToneMs = 200

    private fun buildTone(freq: Double, ms: Int): ShortArray {
        val n = rate * ms / 1000
        val out = ShortArray(n)
        // 4 ms raised-cosine edges. A hard square edge is a click that the
        // detector would happily count as a shot.
        val edge = (rate * 0.004).toInt().coerceAtLeast(1)
        for (i in 0 until n) {
            val env = when {
                i < edge -> 0.5 * (1 - kotlin.math.cos(PI * i / edge))
                i > n - edge -> 0.5 * (1 - kotlin.math.cos(PI * (n - i) / edge))
                else -> 1.0
            }
            out[i] = (sin(2.0 * PI * freq * i / rate) * env * 0.92 * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private val startTone by lazy { buildTone(2100.0, toneMs) }
    private val parTone by lazy { buildTone(1400.0, parToneMs) }

    data class Emission(
        /** CLOCK_MONOTONIC nanos at which frame 0 of the tone hit the speaker. */
        val startNanos: Long,
        /** When the tone finishes; blank the detector until then. */
        val endNanos: Long,
        val exact: Boolean,
    )

    /**
     * Plays the start tone and reports exactly when it sounded.
     *
     * [AudioTrack.getTimestamp] returns a (framePosition, nanoTime) pair for
     * frames already presented. Rewinding that to frame 0 gives the true
     * emission instant on the same monotonic clock the detector timestamps
     * onsets with — so first-shot time is a difference of two HAL timestamps,
     * not a difference of two thread wakeups.
     */
    fun playStart(withTone: Boolean, withHaptic: Boolean): Emission {
        if (withHaptic) {
            vibrator.vibrate(VibrationEffect.createOneShot(140, VibrationEffect.DEFAULT_AMPLITUDE))
        }
        if (!withTone) {
            val t = System.nanoTime()
            return Emission(t, t, exact = false)
        }
        return play(startTone, toneMs)
    }

    fun playPar() {
        play(parTone, parToneMs)
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 60, 50, 60), -1))
    }

    /** Pass/fail buzz so you can read the result without looking at the watch. */
    fun verdict(pass: Boolean) {
        val pattern = if (pass) longArrayOf(0, 50, 60, 50) else longArrayOf(0, 240)
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }

    fun shotTick() {
        vibrator.vibrate(VibrationEffect.createOneShot(12, 90))
    }

    private fun play(pcm: ShortArray, ms: Int): Emission {
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        track.write(pcm, 0, pcm.size)
        val fallback = System.nanoTime()
        track.play()

        var start = fallback
        var exact = false
        val ts = AudioTimestamp()
        // The HAL needs a moment before it can report a presented frame.
        repeat(12) {
            if (track.getTimestamp(ts) && ts.framePosition > 0) {
                start = ts.nanoTime - (ts.framePosition * 1_000_000_000.0 / rate).toLong()
                exact = true
                return@repeat
            }
            Thread.sleep(2)
        }

        track.setNotificationMarkerPosition(pcm.size)
        track.playbackHeadPosition.let { /* touch to keep track alive */ }
        Thread {
            Thread.sleep((ms + 120).toLong())
            runCatching { track.stop(); track.release() }
        }.start()

        return Emission(start, start + ms * 1_000_000L, exact)
    }
}
