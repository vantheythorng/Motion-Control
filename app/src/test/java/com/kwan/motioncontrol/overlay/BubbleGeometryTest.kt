package com.kwan.motioncontrol.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BubbleGeometryTest {

    // 1080 × 2400 screen with a 100 px status bar and a 60 px navigation bar.
    private val area = Area(left = 0, top = 100, right = 1080, bottom = 2340)
    private val size = 140
    private val margin = 16

    @Test fun placesAgainstTheRightEdge() =
        assertEquals(1080 - 140 - 16 to 100, BubbleGeometry.place(BubblePosition(true, 0f), area, size, margin))

    @Test fun placesAgainstTheLeftEdgeAtTheBottom() =
        assertEquals(16 to 2340 - 140, BubbleGeometry.place(BubblePosition(false, 1f), area, size, margin))

    @Test fun releaseSnapsToTheNearerSide() {
        assertFalse(BubbleGeometry.release(x = 300, y = 500, area, size).right)
        assertTrue(BubbleGeometry.release(x = 600, y = 500, area, size).right)
    }

    @Test fun releaseAboveOrBelowTheAreaStaysInside() {
        assertEquals(0f, BubbleGeometry.release(x = 0, y = -400, area, size).yFraction)
        assertEquals(1f, BubbleGeometry.release(x = 0, y = 5000, area, size).yFraction)
    }

    @Test fun releaseThenPlaceKeepsTheHeight() {
        val position = BubbleGeometry.release(x = 900, y = 1200, area, size)
        assertEquals(1080 - 140 - 16 to 1200, BubbleGeometry.place(position, area, size, margin))
    }

    @Test fun heightIsKeptAcrossARotation() {
        val portrait = BubbleGeometry.release(x = 900, y = 100 + (2340 - 100 - 140) / 2, area, size)
        val landscape = Area(left = 0, top = 100, right = 2400, bottom = 1020)
        assertEquals(2400 - 140 - 16 to 100 + (1020 - 100 - 140) / 2, BubbleGeometry.place(portrait, landscape, size, margin))
    }

    @Test fun dropTarget() {
        assertTrue(BubbleGeometry.overTarget(540f, 2200f, 540f, 2240f, radius = 100f))
        assertFalse(BubbleGeometry.overTarget(540f, 1900f, 540f, 2240f, radius = 100f))
    }
}
