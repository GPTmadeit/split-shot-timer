package com.carlb.split.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Statistics derived from a recorded string. These are the numbers a shooter
 * actually reads off the watch, so they get exercised against hand-checked
 * values rather than against the implementation.
 */
class ShotStringTest {

    private fun string(vararg shots: Double, drill: String = "bill") = ShotString(
        id = "t",
        epochMillis = 0L,
        drillId = drill,
        drillName = drill,
        shotsSec = shots.toList(),
    )

    // A clean Bill Drill: 1.40 draw then five splits of .18/.16/.21/.18/.20
    private val bill = string(1.40, 1.58, 1.74, 1.95, 2.13, 2.33)

    @Test
    fun `splits are the gaps between consecutive shots`() {
        val s = bill.splits
        assertEquals(5, s.size)
        listOf(0.18, 0.16, 0.21, 0.18, 0.20).forEachIndexed { i, expected ->
            assertEquals(expected, s[i], 1e-9)
        }
    }

    @Test
    fun `draw is excluded from splits`() {
        // The 1.40 draw must never be counted as a split; it dwarfs them and
        // would wreck both the fastest-split figure and sigma.
        assertTrue(bill.splits.none { it > 1.0 })
        assertEquals(1.40, bill.first!!, 1e-9)
    }

    @Test
    fun `first total and count`() {
        assertEquals(1.40, bill.first!!, 1e-9)
        assertEquals(2.33, bill.total!!, 1e-9)
        assertEquals(6, bill.count)
    }

    @Test
    fun `fastest and slowest split`() {
        assertEquals(0.16, bill.fastestSplit!!, 1e-9)
        assertEquals(0.21, bill.slowestSplit!!, 1e-9)
    }

    @Test
    fun `split sigma matches hand computation`() {
        // splits          .18    .16    .21    .18    .20
        // mean            0.93 / 5 = 0.186
        // squared devs    3.6e-5 + 6.76e-4 + 5.76e-4 + 3.6e-5 + 1.96e-4 = 1.52e-3
        // population var  1.52e-3 / 5 = 3.04e-4
        // sigma           sqrt(3.04e-4) = 0.01743559...
        assertEquals(0.01743559, bill.splitSigma!!, 1e-8)
    }

    @Test
    fun `a perfectly even cadence has zero sigma`() {
        val even = string(1.0, 1.2, 1.4, 1.6)
        assertEquals(0.0, even.splitSigma!!, 1e-12)
    }

    @Test
    fun `single shot has no splits and no sigma`() {
        val one = string(1.23)
        assertTrue(one.splits.isEmpty())
        assertNull(one.splitSigma)
        assertNull(one.fastestSplit)
        assertEquals(1.23, one.first!!, 1e-9)
        assertEquals(1.23, one.total!!, 1e-9)
    }

    @Test
    fun `two shots give one split but still no sigma`() {
        // Sigma of a single sample is meaningless; it must be null, not zero.
        val two = string(1.0, 1.25)
        assertEquals(1, two.splits.size)
        assertNull(two.splitSigma)
    }

    @Test
    fun `empty string is fully null-safe`() {
        val none = string()
        assertNull(none.first)
        assertNull(none.total)
        assertNull(none.splitSigma)
        assertEquals(0, none.count)
        assertTrue(none.splits.isEmpty())
    }

    @Test
    fun `meetsStandard passes when draw par and split are all inside`() {
        val drill = DrillLibrary.byId("bill") // par 2.00, draw 1.50, split 0.20
        val good = string(1.42, 1.58, 1.72, 1.88, 2.04, 2.20) // splits <= .16, total 2.20
        // total 2.20 exceeds par 2.00, so this must fail on par alone
        assertFalse(good.meetsStandard(drill))

        val inside = string(1.20, 1.35, 1.50, 1.65, 1.80, 1.95)
        assertTrue(inside.meetsStandard(drill))
    }

    @Test
    fun `meetsStandard fails on a slow draw even when total is inside par`() {
        val drill = DrillLibrary.byId("bill")
        val slowDraw = string(1.60, 1.70, 1.78, 1.85, 1.90, 1.98)
        assertTrue(slowDraw.total!! <= drill.par)
        assertTrue(slowDraw.first!! > drill.goalFirst)
        assertFalse(slowDraw.meetsStandard(drill))
    }

    @Test
    fun `meetsStandard fails on one blown split`() {
        val drill = DrillLibrary.byId("bill")
        // Draw and total are fine, but one split of .40 doubles the .20 goal.
        val hiccup = string(1.10, 1.25, 1.65, 1.78, 1.88, 1.98)
        assertTrue(hiccup.first!! <= drill.goalFirst)
        assertTrue(hiccup.total!! <= drill.par)
        assertFalse(hiccup.meetsStandard(drill))
    }

    @Test
    fun `drills without a goal never claim a standard was met`() {
        val freestyle = DrillLibrary.byId("free")
        assertFalse(freestyle.hasGoal)
        assertFalse(bill.meetsStandard(freestyle))
    }

    @Test
    fun `empty string never meets a standard`() {
        assertFalse(string().meetsStandard(DrillLibrary.byId("bill")))
    }
}
