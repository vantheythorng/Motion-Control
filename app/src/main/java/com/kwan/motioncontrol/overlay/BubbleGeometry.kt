package com.kwan.motioncontrol.overlay

import kotlin.math.hypot
import kotlin.math.roundToInt

/** The part of the screen the bubble may use (the screen minus system bars), in pixels. */
data class Area(val left: Int, val top: Int, val right: Int, val bottom: Int)

/**
 * Where the bubble rests: against the left or right edge, at a height given as a fraction of the
 * usable height, so it lands in the same place after a rotation.
 */
data class BubblePosition(val right: Boolean, val yFraction: Float) {
    companion object {
        val DEFAULT = BubblePosition(right = true, yFraction = 0.35f)
    }
}

/** Placing, snapping and the × drop target, in window coordinates (top-left of the bubble). */
object BubbleGeometry {

    /** Top-left corner of a bubble of [size] resting at [position], [margin] in from the side. */
    fun place(position: BubblePosition, area: Area, size: Int, margin: Int): Pair<Int, Int> {
        val x = if (position.right) area.right - size - margin else area.left + margin
        val y = area.top + (position.yFraction.coerceIn(0f, 1f) * travel(area, size)).roundToInt()
        return x to y
    }

    /** Where a bubble released with its top-left at ([x], [y]) settles: the nearer side, same height. */
    fun release(x: Int, y: Int, area: Area, size: Int): BubblePosition {
        val right = x + size / 2f >= (area.left + area.right) / 2f
        val travel = travel(area, size)
        val yFraction = if (travel == 0) 0f else ((y - area.top).toFloat() / travel).coerceIn(0f, 1f)
        return BubblePosition(right, yFraction)
    }

    /** Whether the bubble's centre is within [radius] of the × target's centre. */
    fun overTarget(bubbleX: Float, bubbleY: Float, targetX: Float, targetY: Float, radius: Float): Boolean =
        hypot(bubbleX - targetX, bubbleY - targetY) <= radius

    /** How far the bubble's top can move down inside [area]. */
    private fun travel(area: Area, size: Int): Int = (area.bottom - area.top - size).coerceAtLeast(0)
}
