package com.kwan.mygesture.motion

/** Normalized (0..1) image coordinates in upright, mirrored (selfie-view) orientation. */
data class HandPoint(val x: Float, val y: Float)

/** One analyzed camera frame. */
data class HandFrame(
    val timestampMs: Long,
    /** The 21 MediaPipe hand landmarks, or null when no hand is visible. */
    val landmarks: List<HandPoint>?,
    /** MediaPipe canned gesture category, e.g. "Open_Palm" or "None". */
    val gesture: String?,
    val gestureScore: Float,
    /** Estimated hand distance from the camera, or null when it can't be estimated. */
    val distanceCm: Float? = null,
)
