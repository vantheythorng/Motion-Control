package com.kwan.motioncontrol.motion

import kotlin.math.cos
import kotlin.math.sin

/**
 * Synthetic right hands for pose tests, in palm lengths: wrist at the origin, middle knuckle at
 * (0, −1), fingers pointing up (−y), z towards the camera. Fingers are 0 = thumb … 4 = pinky.
 */
object TestHands {
    private val knuckles = listOf(
        floatArrayOf(0.35f, -0.25f, 0f), // thumb CMC
        floatArrayOf(0.30f, -0.95f, 0f),
        floatArrayOf(0f, -1f, 0f),
        floatArrayOf(-0.25f, -0.92f, 0f),
        floatArrayOf(-0.45f, -0.80f, 0f),
    )

    /** The 21 landmarks of a hand with [extended] fingers straight and the others curled into the palm. */
    fun shape(extended: Set<Int>): List<FloatArray> {
        val out = mutableListOf(floatArrayOf(0f, 0f, 0f))
        knuckles.forEachIndexed { finger, base ->
            val joints = when {
                finger == 0 && 0 in extended -> listOf(f(0.2f, -0.2f, 0f), f(0.35f, -0.35f, 0f), f(0.45f, -0.5f, 0f))
                finger == 0 -> listOf(f(0.1f, -0.25f, -0.05f), f(0f, -0.4f, -0.1f), f(-0.15f, -0.45f, -0.15f))
                finger in extended -> listOf(f(0f, -0.35f, 0f), f(0f, -0.65f, 0f), f(0f, -0.9f, 0f))
                else -> listOf(f(0f, -0.3f, -0.15f), f(0f, -0.15f, -0.3f), f(0f, 0.05f, -0.25f))
            }
            out += base
            joints.forEach { out += floatArrayOf(base[0] + it[0], base[1] + it[1], base[2] + it[2]) }
        }
        return out
    }

    val open = shape(setOf(0, 1, 2, 3, 4))
    val fist = shape(emptySet())
    val victory = shape(setOf(1, 2))

    /**
     * [hand] as MediaPipe image landmarks: turned by [angle] radians, [size] of the frame height
     * per palm length, at ([cx], [cy]), in a frame of width/height [aspect]; [mirror] gives the
     * left hand.
     */
    fun image(
        hand: List<FloatArray>,
        cx: Float = 0.5f,
        cy: Float = 0.6f,
        size: Float = 0.2f,
        angle: Float = 0f,
        aspect: Float = 0.75f,
        mirror: Boolean = false,
    ): List<HandPoint> = hand.map { p ->
        val x = if (mirror) -p[0] else p[0]
        val rx = x * cos(angle) - p[1] * sin(angle)
        val ry = x * sin(angle) + p[1] * cos(angle)
        HandPoint(cx + rx * size / aspect, cy + ry * size)
    }

    /** [hand] as world landmarks: rotated about the x, y and z axes, in metres, moved by [offset]. */
    fun world(
        hand: List<FloatArray>,
        rx: Float = 0f,
        ry: Float = 0f,
        rz: Float = 0f,
        metres: Float = 0.08f,
        offset: Float = 0.01f,
        mirror: Boolean = false,
    ): List<HandPoint3> = hand.map { p ->
        var x = if (mirror) -p[0] else p[0]
        var y = p[1]
        var z = p[2]
        // About x, then y, then z.
        run { val ny = y * cos(rx) - z * sin(rx); val nz = y * sin(rx) + z * cos(rx); y = ny; z = nz }
        run { val nx = x * cos(ry) + z * sin(ry); val nz = -x * sin(ry) + z * cos(ry); x = nx; z = nz }
        run { val nx = x * cos(rz) - y * sin(rz); val ny = x * sin(rz) + y * cos(rz); x = nx; y = ny }
        HandPoint3(x * metres + offset, y * metres - offset, z * metres + offset)
    }

    private fun f(x: Float, y: Float, z: Float) = floatArrayOf(x, y, z)
}
