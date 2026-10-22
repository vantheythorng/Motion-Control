package com.kwan.motioncontrol.motion

import com.kwan.motioncontrol.data.Motion
import kotlin.math.abs

/**
 * Detects air swipes from the net palm displacement over a short sliding window.
 * Cooldown is owned by [GestureEngine]; the engine also resets this detector when the hand is lost.
 * Coordinates are already mirrored, so LEFT/RIGHT are from the user's point of view.
 */
class AirSwipeDetector(
    /** Minimum travel along the swipe axis, as a fraction of the frame. */
    var minTravel: Float = 0.18f,
    /**
     * How much larger the travel along the swipe axis must be than across it. A ratio rather than
     * a fixed drift limit, because natural arm swipes arc.
     */
    var dominance: Float = 2f,
    private val windowMs: Long = 600,
) {
    private data class Sample(val x: Float, val y: Float, val t: Long)

    private val buf = ArrayDeque<Sample>()

    /** Displacement across the current window, exposed for the debug view. */
    var dx = 0f
        private set
    var dy = 0f
        private set

    fun onFrame(x: Float, y: Float, now: Long): Motion? {
        buf.addLast(Sample(x, y, now))
        while (now - buf.first().t > windowMs) buf.removeFirst()
        if (buf.size < MIN_SAMPLES) {
            dx = 0f; dy = 0f
            return null
        }
        dx = buf.last().x - buf.first().x
        dy = buf.last().y - buf.first().y
        val vertical = abs(dy) >= dominance * abs(dx)
        val horizontal = abs(dx) >= dominance * abs(dy)
        val result = when {
            vertical && dy < -minTravel -> Motion.AIR_SWIPE_UP
            vertical && dy > minTravel -> Motion.AIR_SWIPE_DOWN
            horizontal && dx < -minTravel -> Motion.AIR_SWIPE_LEFT
            horizontal && dx > minTravel -> Motion.AIR_SWIPE_RIGHT
            else -> null
        }
        if (result != null) reset()
        return result
    }

    fun reset() {
        buf.clear()
        dx = 0f; dy = 0f
    }

    private companion object {
        // Fast swipes blur and drop frames, so a few samples are enough.
        const val MIN_SAMPLES = 3
    }
}
