package com.carlb.split.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The watch/phone contract. A break here means strings silently fail to cross
 * the Data Layer, which is the one failure mode that loses a shooter's session,
 * so the round trips are checked explicitly.
 */
class WireTest {

    private val sample = ShotString(
        id = "9f1c-abc",
        epochMillis = 1_754_000_000_000L,
        drillId = "bill",
        drillName = "Bill Drill",
        shotsSec = listOf(1.404, 1.585, 1.742, 1.951, 2.133, 2.331),
        hitFactor = 11.86,
        powerFactor = "minor",
        note = "weak hand",
        rejectedByRecoil = 2,
        sampleRateHz = 48_000,
    )

    @Test
    fun `shot string survives a round trip intact`() {
        val decoded = Wire.decodeString(Wire.encodeString(sample))
        assertEquals(sample, decoded)
    }

    @Test
    fun `round trip preserves millisecond timing precision`() {
        val decoded = Wire.decodeString(Wire.encodeString(sample))
        sample.shotsSec.forEachIndexed { i, v ->
            assertEquals(v, decoded.shotsSec[i], 0.0)
        }
    }

    @Test
    fun `optional fields survive being absent`() {
        val bare = sample.copy(hitFactor = null, powerFactor = null, note = null)
        assertEquals(bare, Wire.decodeString(Wire.encodeString(bare)))
    }

    @Test
    fun `decoding tolerates unknown fields from a newer sender`() {
        // A newer watch build adding a field must not break an older phone.
        val json = """{"id":"x","epochMillis":1,"drillId":"free","drillName":"Freestyle",
            "shotsSec":[1.0,1.2],"somethingNewInV3":true}"""
        val decoded = Wire.decodeString(json)
        assertEquals("x", decoded.id)
        assertEquals(2, decoded.count)
    }

    @Test
    fun `every live event round trips`() {
        val events = listOf(
            LiveEvent.Armed("bill", 2400L),
            LiveEvent.Started("bill", 1_754_000_000_000L),
            LiveEvent.Shot(3, 1.742),
            LiveEvent.Ended("9f1c-abc", 6, 2.331),
            LiveEvent.Cancelled,
        )
        events.forEach { e ->
            assertEquals(e, Wire.decodeLive(Wire.encodeLive(e)))
        }
    }

    @Test
    fun `config round trips`() {
        val cfg = TimerConfig(
            drillId = "elpz", sensitivityDb = -18, clipGate = false, recoilGate = true,
            blankingMs = 90, delayMode = "random_2_5", startSignal = "haptic",
            parTone = false, autoRepeatSec = 8,
        )
        assertEquals(cfg, Wire.decodeConfig(Wire.encodeConfig(cfg)))
    }

    @Test
    fun `data item paths are namespaced per string`() {
        val path = Wire.stringPath("9f1c-abc")
        assertTrue(path.startsWith(Wire.PATH_STRING))
        assertEquals("/split/string/9f1c-abc", path)
        // Distinct ids must not collide, or one string overwrites another.
        assertTrue(Wire.stringPath("a") != Wire.stringPath("b"))
    }

    @Test
    fun `live and durable transports use different paths`() {
        assertTrue(Wire.PATH_LIVE != Wire.PATH_STRING)
        assertTrue(Wire.PATH_CONFIG != Wire.PATH_STRING)
    }
}

class TimerConfigTest {

    @Test
    fun `instant delay is short but never zero`() {
        // A literally instant beep is unusable: you cannot tell the app started.
        val d = TimerConfig(delayMode = "instant").drawDelayMillis(Random(1))
        assertTrue(d in 1..500)
    }

    @Test
    fun `fixed three seconds is exact`() {
        assertEquals(3000L, TimerConfig(delayMode = "fixed_3").drawDelayMillis(Random(1)))
    }

    @Test
    fun `random ranges stay inside their advertised bounds`() {
        val rnd = Random(42)
        repeat(500) {
            assertTrue(TimerConfig(delayMode = "random_1_4").drawDelayMillis(rnd) in 1000..4000)
            assertTrue(TimerConfig(delayMode = "random_2_5").drawDelayMillis(rnd) in 2000..5000)
        }
    }

    @Test
    fun `random delay actually varies`() {
        // A constant "random" delay would let you anticipate the beep, which
        // defeats the entire point of the start signal.
        val rnd = Random(7)
        val seen = (1..200).map { TimerConfig().drawDelayMillis(rnd) }.toSet()
        assertTrue("expected varied delays, got ${seen.size} distinct", seen.size > 50)
    }

    @Test
    fun `unknown delay mode falls back to the default range`() {
        val d = TimerConfig(delayMode = "nonsense").drawDelayMillis(Random(3))
        assertTrue(d in 1000..4000)
    }

    @Test
    fun `advertised mode lists are all handled`() {
        val rnd = Random(11)
        TimerConfig.DELAY_MODES.forEach { mode ->
            assertTrue(TimerConfig(delayMode = mode).drawDelayMillis(rnd) > 0)
        }
        TimerConfig.SIGNALS.forEach { assertNotNull(TimerConfig(startSignal = it)) }
    }
}

class DrillLibraryTest {

    @Test
    fun `library is non-empty and ids are unique`() {
        assertTrue(DrillLibrary.all.isNotEmpty())
        assertEquals(DrillLibrary.all.size, DrillLibrary.all.map { it.id }.toSet().size)
    }

    @Test
    fun `unknown id falls back to the default drill instead of throwing`() {
        assertEquals(DrillLibrary.default, DrillLibrary.byId("does-not-exist"))
        assertEquals(DrillLibrary.default, DrillLibrary.byId(null))
    }

    @Test
    fun `freestyle is open ended`() {
        val free = DrillLibrary.byId("free")
        assertEquals(0, free.shots)
        assertEquals(0.0, free.par, 0.0)
    }

    @Test
    fun `drills with a par declare a shot count`() {
        // A par with no shot cap can never auto-stop, which would strand the
        // timer running after the last round.
        DrillLibrary.all.filter { it.par > 0 }.forEach {
            assertTrue("${it.id} has par but no shot count", it.shots > 0)
        }
    }

    @Test
    fun `par leaves room for the draw`() {
        // goalFirst and goalSplit are independent ceilings, not a time budget
        // that has to sum inside par -- a 1.20 draw with .15 splits clears all
        // three of Bill Drill's constraints at once. The invariant that does
        // have to hold is weaker: you must at least be able to draw in time,
        // or the drill is unwinnable by construction.
        DrillLibrary.all.filter { it.hasGoal && it.par > 0 }.forEach { d ->
            assertTrue(
                "${d.id}: draw goal ${d.goalFirst} exceeds par ${d.par}",
                d.goalFirst < d.par,
            )
        }
    }

    @Test
    fun `drills with goals declare both a draw and a split target`() {
        DrillLibrary.all.filter { it.hasGoal }.forEach { d ->
            assertTrue("${d.id} has no draw goal", d.goalFirst > 0.0)
            assertTrue("${d.id} has no split goal", d.goalSplit > 0.0)
        }
    }
}
