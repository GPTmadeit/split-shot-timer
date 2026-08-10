package com.carlb.split.wear.timer

import android.content.Context
import android.hardware.SensorManager
import android.media.AudioManager
import android.util.Log
import com.carlb.split.core.Drill
import com.carlb.split.core.DrillLibrary
import com.carlb.split.core.LiveEvent
import com.carlb.split.core.ShotString
import com.carlb.split.core.TimerConfig
import com.carlb.split.wear.audio.ShotDetector
import com.carlb.split.wear.audio.StartSignal
import com.carlb.split.wear.sensor.RecoilGate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.random.Random

sealed interface TimerPhase {
    data object Idle : TimerPhase
    data class Armed(val delayMillis: Long, val armedAtNanos: Long) : TimerPhase
    data class Running(val startNanos: Long) : TimerPhase
    data class Complete(val string: ShotString, val metStandard: Boolean) : TimerPhase
}

data class TimerUiState(
    val phase: TimerPhase = TimerPhase.Idle,
    val drill: Drill = DrillLibrary.default,
    val shots: List<Double> = emptyList(),
    val config: TimerConfig = TimerConfig(),
    val levelDbfs: Double = -100.0,
    val clipping: Boolean = false,
    val micReady: Boolean = false,
    val sourceName: String = "-",
    val sampleRate: Int = 0,
    val rejectedByRecoil: Int = 0,
    val phoneConnected: Boolean = false,
    /** Whether the accelerometer is actually streaming for the recoil gate. */
    val recoilReady: Boolean = false,
)

/**
 * The state machine. Owns the microphone, the tone, and the clock.
 *
 * Everything time-critical happens here on the watch. The phone is a listener,
 * never a participant: [onLive] and [onString] are fire-and-forget hooks, and
 * nothing in this class waits on either.
 */
class TimerEngine(
    context: Context,
    private val scope: CoroutineScope,
    private val onLive: (LiveEvent) -> Unit,
    private val onString: (ShotString) -> Unit,
) {
    private val app = context.applicationContext
    private val signal = StartSignal(app)
    private val recoil = RecoilGate(app.getSystemService(SensorManager::class.java))
    private val detector = ShotDetector(app.getSystemService(AudioManager::class.java), ::handleOnset)

    private val _state = MutableStateFlow(TimerUiState())
    val state: StateFlow<TimerUiState> = _state.asStateFlow()

    private var armJob: Job? = null
    private var parJob: Job? = null
    private var repeatJob: Job? = null
    private var meterJob: Job? = null

    private var startNanos: Long = 0L
    private val shots = mutableListOf<Double>()
    private var rejected = 0

    fun setConfig(cfg: TimerConfig) {
        detector.config = ShotDetector.Config(cfg.sensitivityDb, cfg.clipGate, cfg.blankingMs)
        _state.value = _state.value.copy(config = cfg, drill = DrillLibrary.byId(cfg.drillId))
        // Toggling the gate mid-session has to take effect without reopening
        // the mic, so follow the setting here rather than only at openMic().
        if (_state.value.micReady) syncRecoilSensor(cfg.recoilGate)
    }

    /**
     * Bring the accelerometer up or down to match the setting. Never throws:
     * if the sensor cannot be had, the gate simply reports itself unavailable
     * and detection carries on acoustically.
     */
    private fun syncRecoilSensor(wanted: Boolean) {
        runCatching {
            if (wanted && !recoil.running) {
                val started = recoil.start()
                _state.value = _state.value.copy(recoilReady = started)
            } else if (!wanted && recoil.running) {
                recoil.stop()
                _state.value = _state.value.copy(recoilReady = false)
            }
        }.onFailure { Log.w(TAG, "recoil sensor toggle failed", it) }
    }

    fun setDrill(d: Drill) {
        _state.value = _state.value.copy(drill = d, config = _state.value.config.copy(drillId = d.id))
    }

    fun setPhoneConnected(connected: Boolean) {
        _state.value = _state.value.copy(phoneConnected = connected)
    }

    /** Bring up the mic. Must be called while the app is foregrounded. */
    fun openMic(): Boolean {
        if (_state.value.micReady) return true
        val ok = runCatching { detector.start() }
            .onFailure { Log.e(TAG, "microphone unavailable", it) }
            .getOrDefault(false)
        if (ok) {
            // Only spin the accelerometer if the gate is actually in use. It is
            // off by default, and an optional feature must never be able to
            // take the timer down with it.
            if (_state.value.config.recoilGate) syncRecoilSensor(true)
            _state.value = _state.value.copy(
                micReady = true,
                sourceName = detector.sourceName,
                sampleRate = detector.sampleRate,
            )
            meterJob = scope.launch {
                while (true) {
                    _state.value = _state.value.copy(
                        levelDbfs = detector.levelDbfs,
                        clipping = detector.clipping,
                    )
                    delay(60)
                }
            }
        }
        return ok
    }

    fun closeMic() {
        meterJob?.cancel()
        detector.stop()
        recoil.stop()
        _state.value = _state.value.copy(micReady = false)
    }

    fun arm() {
        cancelJobs()
        shots.clear()
        rejected = 0
        val cfg = _state.value.config
        val wait = cfg.drawDelayMillis(Random.Default)

        _state.value = _state.value.copy(
            phase = TimerPhase.Armed(wait, System.nanoTime()),
            shots = emptyList(),
            rejectedByRecoil = 0,
        )
        onLive(LiveEvent.Armed(_state.value.drill.id, wait))

        armJob = scope.launch {
            delay(wait)
            go()
        }
    }

    private suspend fun go() {
        val cfg = _state.value.config
        val withTone = cfg.startSignal != "haptic"
        val withHaptic = cfg.startSignal != "beep"

        val emission = withContext(Dispatchers.Default) {
            signal.playStart(withTone, withHaptic)
        }
        startNanos = emission.startNanos
        // Never let the start tone count as shot one.
        detector.suppressUntil(emission.endNanos + 15_000_000L)

        _state.value = _state.value.copy(phase = TimerPhase.Running(startNanos))
        onLive(LiveEvent.Started(_state.value.drill.id, System.currentTimeMillis()))

        val drill = _state.value.drill
        if (cfg.parTone && drill.par > 0) {
            parJob = scope.launch {
                delay((drill.par * 1000).toLong())
                if (_state.value.phase is TimerPhase.Running) {
                    withContext(Dispatchers.Default) { signal.playPar() }
                }
            }
        }
    }

    /** Called on the audio thread. Keep it cheap. */
    private fun handleOnset(e: ShotDetector.OnsetEvent) {
        val phase = _state.value.phase
        if (phase !is TimerPhase.Running) return

        if (_state.value.config.recoilGate && !recoil.sawRecoilNear(e.monotonicNanos)) {
            rejected++
            _state.value = _state.value.copy(rejectedByRecoil = rejected)
            return
        }

        val t = (e.monotonicNanos - startNanos) / 1_000_000_000.0
        if (t < 0.02) return

        shots.add(t)
        _state.value = _state.value.copy(shots = shots.toList())
        onLive(LiveEvent.Shot(shots.size, t))
        signal.shotTick()

        val drill = _state.value.drill
        if (drill.shots > 0 && shots.size >= drill.shots) {
            scope.launch {
                delay(120)
                stop()
            }
        }
    }

    fun stop() {
        val phase = _state.value.phase
        armJob?.cancel()
        parJob?.cancel()

        if (phase is TimerPhase.Running && shots.isNotEmpty()) {
            val drill = _state.value.drill
            val s = ShotString(
                id = UUID.randomUUID().toString(),
                epochMillis = System.currentTimeMillis(),
                drillId = drill.id,
                drillName = drill.name,
                shotsSec = shots.map { (it * 1000).toInt() / 1000.0 },
                rejectedByRecoil = rejected,
                sampleRateHz = detector.sampleRate,
            )
            val met = s.meetsStandard(drill)
            _state.value = _state.value.copy(phase = TimerPhase.Complete(s, met))

            onString(s)
            onLive(LiveEvent.Ended(s.id, s.count, s.total ?: 0.0))
            if (drill.hasGoal) scope.launch(Dispatchers.Default) { signal.verdict(met) }

            val repeat = _state.value.config.autoRepeatSec
            if (repeat > 0) {
                repeatJob = scope.launch {
                    delay(repeat * 1000L)
                    if (_state.value.phase is TimerPhase.Complete) arm()
                }
            }
        } else {
            _state.value = _state.value.copy(phase = TimerPhase.Idle)
            onLive(LiveEvent.Cancelled)
        }
    }

    fun reset() {
        cancelJobs()
        shots.clear()
        rejected = 0
        _state.value = _state.value.copy(
            phase = TimerPhase.Idle,
            shots = emptyList(),
            rejectedByRecoil = 0,
        )
    }

    private fun cancelJobs() {
        armJob?.cancel()
        parJob?.cancel()
        repeatJob?.cancel()
    }

    fun release() {
        cancelJobs()
        closeMic()
    }

    /** Seconds since the tone. Read from the UI frame loop. */
    fun elapsedSec(): Double {
        val p = _state.value.phase
        return when (p) {
            is TimerPhase.Running -> (System.nanoTime() - p.startNanos) / 1_000_000_000.0
            is TimerPhase.Complete -> p.string.total ?: 0.0
            else -> 0.0
        }
    }

    private companion object {
        const val TAG = "TimerEngine"
    }
}
