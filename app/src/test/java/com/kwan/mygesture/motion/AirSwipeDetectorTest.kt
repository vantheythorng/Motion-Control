package com.kwan.mygesture.motion

import com.kwan.mygesture.data.Motion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AirSwipeDetectorTest {

    /** Feeds a straight-line movement at ~15 fps and returns the first motion detected. */
    private fun AirSwipeDetector.move(
        fromX: Float, fromY: Float, toX: Float, toY: Float, durationMs: Long, startMs: Long = 0,
    ): Motion? {
        val steps = (durationMs / FRAME_MS).toInt()
        for (i in 0..steps) {
            val f = i.toFloat() / steps
            onFrame(fromX + (toX - fromX) * f, fromY + (toY - fromY) * f, startMs + i * FRAME_MS)
                ?.let { return it }
        }
        return null
    }

    @Test fun swipeUp() =
        assertEquals(Motion.AIR_SWIPE_UP, AirSwipeDetector().move(0.5f, 0.8f, 0.5f, 0.3f, 330))

    @Test fun swipeDown() =
        assertEquals(Motion.AIR_SWIPE_DOWN, AirSwipeDetector().move(0.5f, 0.2f, 0.5f, 0.7f, 330))

    @Test fun swipeLeft() =
        assertEquals(Motion.AIR_SWIPE_LEFT, AirSwipeDetector().move(0.8f, 0.5f, 0.3f, 0.5f, 330))

    @Test fun swipeRight() =
        assertEquals(Motion.AIR_SWIPE_RIGHT, AirSwipeDetector().move(0.2f, 0.5f, 0.7f, 0.5f, 330))

    @Test fun arcedSwipeUpStillCounts() =
        assertEquals(Motion.AIR_SWIPE_UP, AirSwipeDetector().move(0.45f, 0.75f, 0.57f, 0.4f, 400))

    @Test fun diagonalIsIgnored() =
        assertNull(AirSwipeDetector().move(0.2f, 0.8f, 0.6f, 0.4f, 330))

    @Test fun slowDriftIsIgnored() =
        assertNull(AirSwipeDetector().move(0.5f, 0.9f, 0.5f, 0.3f, 3000))

    @Test fun shortMovementIsIgnored() =
        assertNull(AirSwipeDetector().move(0.5f, 0.6f, 0.5f, 0.45f, 200))

    @Test fun resetDropsBufferedSamples() {
        val d = AirSwipeDetector()
        d.onFrame(0.5f, 0.8f, 0)
        d.onFrame(0.5f, 0.7f, 66)
        d.reset()
        // Without the earlier samples, the remaining travel is below the threshold.
        assertNull(d.move(0.5f, 0.6f, 0.5f, 0.45f, 200, startMs = 132))
    }

    private companion object {
        const val FRAME_MS = 66L
    }
}
