package com.carlb.split.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * USPSA scoring. Point values are fixed by the rulebook, so these are exact
 * expectations rather than tolerances on anything the implementation chose.
 */
class ScoringTest {

    @Test
    fun `minor point values`() {
        // A=5, C=3, D=1 under minor
        assertEquals(5, Score(a = 1).points(major = false))
        assertEquals(3, Score(c = 1).points(major = false))
        assertEquals(1, Score(d = 1).points(major = false))
    }

    @Test
    fun `major point values`() {
        // A stays 5; C and D are each worth one more than minor.
        assertEquals(5, Score(a = 1).points(major = true))
        assertEquals(4, Score(c = 1).points(major = true))
        assertEquals(2, Score(d = 1).points(major = true))
    }

    @Test
    fun `misses and no-shoots are ten point penalties`() {
        assertEquals(-10, Score(m = 1).points(major = false))
        assertEquals(-10, Score(ns = 1).points(major = false))
        assertEquals(-20, Score(m = 1, ns = 1).points(major = true))
    }

    @Test
    fun `hit factor is points over time`() {
        // 5A + 1C minor = 25 + 3 = 28 points over 2.36 s
        val s = Score(a = 5, c = 1)
        assertEquals(28, s.points(major = false))
        assertEquals(28 / 2.36, s.hitFactor(2.36, major = false), 1e-9)
    }

    @Test
    fun `major scoring raises hit factor for the same hits`() {
        val s = Score(a = 5, c = 1)
        val minor = s.hitFactor(2.36, major = false)
        val major = s.hitFactor(2.36, major = true)
        assertTrue(major > minor)
        assertEquals(29 / 2.36, major, 1e-9)
    }

    @Test
    fun `hit factor floors at zero rather than going negative`() {
        // Two A hits (10) against three misses (-30) is -20 raw. A negative
        // hit factor is meaningless on a scoreboard, so it clamps to zero.
        val s = Score(a = 2, m = 3)
        assertEquals(-20, s.points(major = false))
        assertEquals(0.0, s.hitFactor(3.0, major = false), 1e-12)
    }

    @Test
    fun `zero or negative time yields zero rather than dividing by zero`() {
        val s = Score(a = 6)
        assertEquals(0.0, s.hitFactor(0.0, major = false), 1e-12)
        assertEquals(0.0, s.hitFactor(-1.0, major = false), 1e-12)
    }

    @Test
    fun `an empty score is zero not a crash`() {
        assertEquals(0, Score().points(major = false))
        assertEquals(0.0, Score().hitFactor(5.0, major = false), 1e-12)
    }
}
