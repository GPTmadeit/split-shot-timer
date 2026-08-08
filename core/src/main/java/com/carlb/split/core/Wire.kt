package com.carlb.split.core

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The watch/phone contract.
 *
 * Two transports, deliberately different:
 *
 *  - [PATH_LIVE] rides MessageClient. Low latency, best effort, dropped if the
 *    link is down. Used only to mirror a running string on the phone. Nothing
 *    the timer needs is ever carried here.
 *
 *  - [PATH_STRING] rides DataClient. Durable and replicated: a completed string
 *    written while the phone is out of range syncs the moment it returns. At a
 *    range your phone is in the bag thirty feet away, so this is the one that
 *    actually matters.
 *
 * Direction is strictly watch -> phone for results, phone -> watch for config
 * only ([PATH_CONFIG]). The watch never blocks on the phone for anything.
 */
object Wire {
    const val PATH_LIVE = "/split/live"
    const val PATH_STRING = "/split/string"
    const val PATH_CONFIG = "/split/config"

    const val KEY_PAYLOAD = "payload"
    const val KEY_UPDATED_AT = "updatedAt"

    const val CAPABILITY_WATCH = "split_timer_watch"
    const val CAPABILITY_PHONE = "split_timer_phone"

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun stringPath(id: String): String = "$PATH_STRING/$id"

    fun encodeString(s: ShotString): String = json.encodeToString(s)
    fun decodeString(raw: String): ShotString = json.decodeFromString(raw)

    fun encodeLive(e: LiveEvent): ByteArray = json.encodeToString(e).toByteArray()
    fun decodeLive(raw: ByteArray): LiveEvent = json.decodeFromString(String(raw))

    fun encodeConfig(c: TimerConfig): String = json.encodeToString(c)
    fun decodeConfig(raw: String): TimerConfig = json.decodeFromString(raw)
}

/** Timer settings. Lives on the watch; the phone may push edits. */
@kotlinx.serialization.Serializable
data class TimerConfig(
    val drillId: String = "free",
    /** Onset threshold in dBFS. */
    val sensitivityDb: Int = -22,
    /** Require the ADC to rail before counting a shot — rejects the next bay. */
    val clipGate: Boolean = true,
    /** Correlate the report against a wrist recoil impulse. */
    val recoilGate: Boolean = false,
    /** Dead time after each detected shot, milliseconds. */
    val blankingMs: Int = 60,
    val delayMode: String = "random_1_4",
    val startSignal: String = "beep_haptic",
    val parTone: Boolean = true,
    val autoRepeatSec: Int = 0,
) {
    companion object {
        val DELAY_MODES = listOf("instant", "random_1_4", "random_2_5", "fixed_3")
        val SIGNALS = listOf("beep_haptic", "beep", "haptic")
    }

    fun drawDelayMillis(rnd: kotlin.random.Random): Long = when (delayMode) {
        "instant" -> 250L
        "random_2_5" -> (2000 + rnd.nextInt(3000)).toLong()
        "fixed_3" -> 3000L
        else -> (1000 + rnd.nextInt(3000)).toLong()
    }
}
