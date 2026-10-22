package com.kwan.motioncontrol.motion

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HandShapeTest {

    /**
     * Builds an upright hand: wrist at the bottom, each finger's MCP/PIP/DIP/TIP stacked upwards.
     * [extended] says per finger (index, middle, ring, pinky) whether it's straight or curled
     * back down towards the palm.
     */
    private fun hand(extended: List<Boolean>): List<HandPoint> {
        val points = MutableList(21) { HandPoint(0.5f, 0.8f) }
        points[0] = HandPoint(0.5f, 0.8f)
        (1..4).forEach { points[it] = HandPoint(0.4f - 0.02f * it, 0.75f - 0.03f * it) } // thumb
        extended.forEachIndexed { f, straight ->
            val x = 0.42f + 0.05f * f
            val base = 5 + 4 * f
            points[base] = HandPoint(x, 0.65f) // MCP
            points[base + 1] = HandPoint(x, 0.58f) // PIP
            if (straight) {
                points[base + 2] = HandPoint(x, 0.52f)
                points[base + 3] = HandPoint(x, 0.47f)
            } else {
                points[base + 2] = HandPoint(x, 0.62f)
                points[base + 3] = HandPoint(x, 0.68f)
            }
        }
        return points
    }

    @Test fun allFingersExtendedIsOpenPalm() =
        assertTrue(HandShape.isOpenPalm(hand(listOf(true, true, true, true))))

    @Test fun fistIsNotOpenPalm() =
        assertFalse(HandShape.isOpenPalm(hand(listOf(false, false, false, false))))

    @Test fun victoryIsNotOpenPalm() =
        assertFalse(HandShape.isOpenPalm(hand(listOf(true, true, false, false))))

    @Test fun pointingIsNotOpenPalm() =
        assertFalse(HandShape.isOpenPalm(hand(listOf(true, false, false, false))))
}
