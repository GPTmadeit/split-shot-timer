package com.carlb.split.core

import kotlinx.serialization.Serializable
import kotlin.math.sqrt

/**
 * A drill standard. [shots] of 0 means an open string with no cap;
 * [par] of 0 means no par tone.
 */
@Serializable
data class Drill(
    val id: String,
    val name: String,
    val shots: Int,
    val par: Double,
    val brief: String,
    val goalFirst: Double = 0.0,
    val goalSplit: Double = 0.0,
) {
    val hasGoal: Boolean get() = goalFirst > 0.0
}

object DrillLibrary {
    val all: List<Drill> = listOf(
        Drill("free", "Freestyle", 0, 0.0, "Open string. No shot cap, no par."),
        Drill("bill", "Bill Drill", 6, 2.00, "7 yd. Draw, six rounds, all A-zone.", 1.50, 0.20),
        Drill("moz", "Failure to Stop", 3, 2.50, "7 yd. Two body, one head.", 1.30, 0.30),
        Drill("fast", "F.A.S.T.", 6, 5.00, "Two on the 3x5 card, reload, four on the 8 in. circle.", 1.80, 0.45),
        Drill("r1", "1-Reload-1", 2, 2.50, "One round, slide-lock reload, one round.", 1.20, 1.60),
        Drill("blake", "Blake Drill", 6, 3.00, "Three targets, two rounds each, on the clock.", 1.50, 0.30),
        Drill("elpz", "El Presidente", 12, 10.00, "Three targets at 10 yd. Turn, 2 each, reload, 2 each.", 2.00, 0.35),
        Drill("casino", "Casino Drill", 21, 21.00, "21 rounds, two reloads, descending 6-5-4-3-2-1.", 1.60, 0.40),
        Drill("dot", "Dot Torture", 5, 0.00, "Accuracy standard. Counts shots, par is off."),
    )

    val default: Drill get() = all.first()
    fun byId(id: String?): Drill = all.firstOrNull { it.id == id } ?: default
}

/**
 * One recorded string. [shotsSec] are offsets from the start tone, in seconds.
 * Recorded on the watch; this is the object that crosses to the phone.
 */
@Serializable
data class ShotString(
    val id: String,
    val epochMillis: Long,
    val drillId: String,
    val drillName: String,
    val shotsSec: List<Double>,
    val hitFactor: Double? = null,
    val powerFactor: String? = null,
    val note: String? = null,
    /** How many shots the recoil gate vetoed as someone else's gunfire. */
    val rejectedByRecoil: Int = 0,
    val sampleRateHz: Int = 0,
) {
    val count: Int get() = shotsSec.size
    val first: Double? get() = shotsSec.firstOrNull()
    val total: Double? get() = shotsSec.lastOrNull()

    val splits: List<Double>
        get() = if (shotsSec.size < 2) {
            emptyList()
        } else {
            shotsSec.zipWithNext { a, b -> b - a }
        }

    val fastestSplit: Double? get() = splits.minOrNull()
    val slowestSplit: Double? get() = splits.maxOrNull()

    /** Standard deviation of splits. The number that says whether you're
     *  actually shooting a cadence or just got lucky once. */
    val splitSigma: Double?
        get() {
            val s = splits
            if (s.size < 2) return null
            val mean = s.average()
            return sqrt(s.sumOf { (it - mean) * (it - mean) } / s.size)
        }

    fun meetsStandard(drill: Drill): Boolean {
        if (!drill.hasGoal) return false
        val f = first ?: return false
        val t = total ?: return false
        val parOk = drill.par <= 0.0 || t <= drill.par
        val splitOk = drill.goalSplit <= 0.0 || (slowestSplit ?: 0.0) <= drill.goalSplit
        return parOk && f <= drill.goalFirst && splitOk
    }
}

/** USPSA scoring. */
@Serializable
data class Score(val a: Int = 0, val c: Int = 0, val d: Int = 0, val m: Int = 0, val ns: Int = 0) {
    fun points(major: Boolean): Int = a * 5 + c * (if (major) 4 else 3) + d * (if (major) 2 else 1) - m * 10 - ns * 10

    fun hitFactor(timeSec: Double, major: Boolean): Double =
        if (timeSec <= 0.0) 0.0 else (points(major).coerceAtLeast(0)) / timeSec
}

/** Live events streamed watch -> phone while a string is in progress. */
@Serializable
sealed interface LiveEvent {
    @Serializable
    data class Armed(val drillId: String, val delayMillis: Long) : LiveEvent

    @Serializable
    data class Started(val drillId: String, val startedAtEpochMs: Long) : LiveEvent

    @Serializable
    data class Shot(val index: Int, val atSec: Double) : LiveEvent

    @Serializable
    data class Ended(val stringId: String, val shots: Int, val totalSec: Double) : LiveEvent

    @Serializable
    data object Cancelled : LiveEvent
}
