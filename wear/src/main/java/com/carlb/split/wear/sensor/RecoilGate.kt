package com.carlb.split.wear.sensor

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Recoil correlation — the one thing a wrist beats a dedicated timer at.
 *
 * A CED7000 hears a bang and counts it. It cannot tell your muzzle from the
 * shooter in the next bay, which is why every timer on the market has a
 * sensitivity knob you fiddle with all day. The watch has an accelerometer
 * strapped to the hand that is holding the gun, so it can ask a second,
 * independent question: did this wrist just take an impulse?
 *
 * A report with no matching impulse within [WINDOW_MS] is somebody else's.
 *
 * Honest caveats, because this is worth tuning against your own gun before you
 * trust it:
 *  - Transfer depends on grip and on which wrist wears the watch. Support-hand
 *    on a two-handed grip reads cleanly; strong hand reads harder; a one-handed
 *    string on the off hand may read nothing at all.
 *  - .22 conversions and heavy-buffered PCCs can fall under the threshold.
 * So this is a scored vote, not a hard veto, and it ships off by default.
 */
class RecoilGate(private val sensorManager: SensorManager) : SensorEventListener {

    private val accel: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    /**
     * SensorEvent.timestamp is boot-based; AudioRecord/AudioTrack timestamps are
     * CLOCK_MONOTONIC. They tick at the same rate while awake, so one offset
     * measured at start-up converts between them.
     */
    private var bootMinusMonotonic: Long = 0L

    private val times = LongArray(CAPACITY)
    private val mags = FloatArray(CAPACITY)
    private var writeIdx = 0
    private var filled = 0

    private var gravity = 9.81f

    val available: Boolean get() = accel != null

    fun start() {
        val s = accel ?: return
        bootMinusMonotonic = SystemClock.elapsedRealtimeNanos() - System.nanoTime()
        writeIdx = 0; filled = 0
        sensorManager.registerListener(this, s, SensorManager.SENSOR_DELAY_FASTEST)
    }

    fun stop() = sensorManager.unregisterListener(this)

    override fun onSensorChanged(e: SensorEvent) {
        val m = sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2])
        // Slow follower tracks gravity + arm motion; the recoil impulse is what
        // spikes above it over a couple of milliseconds.
        gravity += (m - gravity) * 0.02f
        val dev = abs(m - gravity)

        times[writeIdx] = e.timestamp
        mags[writeIdx] = dev
        writeIdx = (writeIdx + 1) % CAPACITY
        if (filled < CAPACITY) filled++
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    /**
     * True if a recoil-sized impulse landed within [WINDOW_MS] of [monotonicNanos].
     * The window is symmetric: the muzzle report and the impulse are effectively
     * simultaneous, but the two subsystems report with different latencies.
     */
    fun sawRecoilNear(monotonicNanos: Long, thresholdMs2: Float = DEFAULT_THRESHOLD): Boolean {
        if (filled == 0) return true // no sensor data — do not veto
        val bootNanos = monotonicNanos + bootMinusMonotonic
        val window = WINDOW_MS * 1_000_000L
        for (i in 0 until filled) {
            if (abs(times[i] - bootNanos) <= window && mags[i] >= thresholdMs2) return true
        }
        return false
    }

    /** Peak impulse seen near a report — exposed so the UI can show a confidence. */
    fun peakNear(monotonicNanos: Long): Float {
        if (filled == 0) return 0f
        val bootNanos = monotonicNanos + bootMinusMonotonic
        val window = WINDOW_MS * 1_000_000L
        var peak = 0f
        for (i in 0 until filled) {
            if (abs(times[i] - bootNanos) <= window && mags[i] > peak) peak = mags[i]
        }
        return peak
    }

    companion object {
        private const val CAPACITY = 2048
        /** Estimated, not measured. Characterise against your own gun. */
        const val WINDOW_MS = 40L
        const val DEFAULT_THRESHOLD = 18f // m/s^2 above the slow follower
    }
}
