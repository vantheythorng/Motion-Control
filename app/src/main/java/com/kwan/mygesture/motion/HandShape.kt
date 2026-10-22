package com.kwan.mygesture.motion

import kotlin.math.hypot

/** Pose checks computed directly from the 21 MediaPipe hand landmarks. */
object HandShape {
    private const val WRIST = 0
    /** (tip, PIP joint) for index, middle, ring and pinky. */
    private val FINGERS = listOf(8 to 6, 12 to 10, 16 to 14, 20 to 18)
    /** How much farther from the wrist a fingertip must be than its PIP joint to count as straight. */
    private const val EXTENDED_RATIO = 1.15f

    /**
     * True when all four fingers are extended. MediaPipe's canned classifier often scores an open
     * palm below its confidence threshold (median 0.55 on device), so this backs it up.
     */
    fun isOpenPalm(landmarks: List<HandPoint>): Boolean {
        val wrist = landmarks[WRIST]
        return FINGERS.all { (tip, pip) ->
            dist(landmarks[tip], wrist) > dist(landmarks[pip], wrist) * EXTENDED_RATIO
        }
    }

    private fun dist(a: HandPoint, b: HandPoint) = hypot(a.x - b.x, a.y - b.y)
}
