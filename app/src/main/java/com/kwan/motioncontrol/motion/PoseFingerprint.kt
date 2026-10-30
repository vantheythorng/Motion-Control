package com.kwan.motioncontrol.motion

import com.kwan.motioncontrol.data.PoseSpace
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A hand's shape as numbers, independent of where the hand is, how big it looks, how it's turned
 * and which hand it is (SPEC §5.7):
 * - the wrist is the origin
 * - lengths are in palm lengths (wrist → middle-finger knuckle)
 * - wrist → middle knuckle points up (in 3D, the palm is also turned to face the same way)
 * - a left hand is mirrored to a right hand
 *
 * The result lists landmarks 1–20 (the wrist is always 0): 2 numbers each in image space, 3 in
 * world space. Null when the hand is too small or flat to measure.
 */
object PoseFingerprint {
    private const val WRIST = 0
    private const val INDEX_MCP = 5
    private const val MIDDLE_MCP = 9
    private const val PINKY_MCP = 17
    private const val POINTS = 20
    private const val EPSILON = 1e-4f

    fun of(frame: HandFrame, space: PoseSpace): FloatArray? = when (space) {
        PoseSpace.IMAGE -> frame.landmarks?.let { image(it, frame.leftHand, frame.aspect) }
        PoseSpace.WORLD -> frame.world?.let { world(it, frame.leftHand) }
    }

    fun image(landmarks: List<HandPoint>, leftHand: Boolean, aspect: Float): FloatArray? {
        if (landmarks.size <= PINKY_MCP + 3) return null
        val wrist = landmarks[WRIST]
        // Equal units on both axes (x is a fraction of the width, y of the height); mirror left hands.
        val sx = if (leftHand) -aspect else aspect
        val xs = FloatArray(landmarks.size) { (landmarks[it].x - wrist.x) * sx }
        val ys = FloatArray(landmarks.size) { landmarks[it].y - wrist.y }
        val length = hypot(xs[MIDDLE_MCP], ys[MIDDLE_MCP])
        if (length < EPSILON) return null
        // Rotate so wrist → middle knuckle points up (−y, image y grows downwards).
        val angle = (-Math.PI / 2 - atan2(ys[MIDDLE_MCP], xs[MIDDLE_MCP])).toFloat()
        val c = cos(angle)
        val s = sin(angle)
        val out = FloatArray(POINTS * 2)
        for (i in 1..POINTS) {
            out[(i - 1) * 2] = (xs[i] * c - ys[i] * s) / length
            out[(i - 1) * 2 + 1] = (xs[i] * s + ys[i] * c) / length
        }
        return out
    }

    fun world(points: List<HandPoint3>, leftHand: Boolean): FloatArray? {
        if (points.size <= PINKY_MCP + 3) return null
        val w = points[WRIST]
        val sx = if (leftHand) -1f else 1f
        val p = Array(points.size) { floatArrayOf((points[it].x - w.x) * sx, points[it].y - w.y, points[it].z - w.z) }
        // A frame fixed to the hand: up = wrist → middle knuckle, across = pinky → index knuckle
        // (made perpendicular to up), normal = up × across (out of the palm).
        val length = norm(p[MIDDLE_MCP])
        if (length < EPSILON) return null
        val up = scale(p[MIDDLE_MCP], 1 / length)
        val rawAcross = minus(p[INDEX_MCP], p[PINKY_MCP])
        val acrossInPlane = minus(rawAcross, scale(up, dot(rawAcross, up)))
        val acrossLength = norm(acrossInPlane)
        if (acrossLength < EPSILON) return null
        val across = scale(acrossInPlane, 1 / acrossLength)
        val normal = cross(up, across)
        val out = FloatArray(POINTS * 3)
        for (i in 1..POINTS) {
            out[(i - 1) * 3] = dot(p[i], across) / length
            out[(i - 1) * 3 + 1] = dot(p[i], up) / length
            out[(i - 1) * 3 + 2] = dot(p[i], normal) / length
        }
        return out
    }

    /** Root-mean-square distance between corresponding landmarks of two fingerprints, in palm lengths. */
    fun distance(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Fingerprints from different spaces" }
        var sum = 0f
        for (i in a.indices) {
            val d = a[i] - b[i]
            sum += d * d
        }
        return sqrt(sum / POINTS)
    }

    private fun dot(a: FloatArray, b: FloatArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
    private fun norm(a: FloatArray) = sqrt(dot(a, a))
    private fun scale(a: FloatArray, k: Float) = floatArrayOf(a[0] * k, a[1] * k, a[2] * k)
    private fun minus(a: FloatArray, b: FloatArray) = floatArrayOf(a[0] - b[0], a[1] - b[1], a[2] - b[2])
    private fun cross(a: FloatArray, b: FloatArray) = floatArrayOf(
        a[1] * b[2] - a[2] * b[1],
        a[2] * b[0] - a[0] * b[2],
        a[0] * b[1] - a[1] * b[0],
    )
}
