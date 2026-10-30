package com.kwan.motioncontrol.motion

/** Normalized (0..1) image coordinates in upright, mirrored (selfie-view) orientation. */
data class HandPoint(val x: Float, val y: Float)

/** MediaPipe world landmark: metres, origin near the hand's centre, axes as in [HandPoint]. */
data class HandPoint3(val x: Float, val y: Float, val z: Float)

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
    /** The same 21 landmarks in 3D (for custom poses), or null. */
    val world: List<HandPoint3>? = null,
    /** True for a left hand; custom poses mirror it so either hand matches. */
    val leftHand: Boolean = false,
    /** Frame width / height, so image landmarks can be measured in equal units on both axes. */
    val aspect: Float = 1f,
)
