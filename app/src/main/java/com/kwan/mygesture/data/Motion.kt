package com.kwan.mygesture.data

enum class Motion {
    AIR_SWIPE_UP, AIR_SWIPE_DOWN, AIR_SWIPE_LEFT, AIR_SWIPE_RIGHT,
    OPEN_PALM_HOLD, THUMBS_UP, THUMBS_DOWN, FIST_HOLD, VICTORY, POINTING_UP,
}

/** The spec's "Short video" profile, hard-coded until DataStore-backed profiles (Phase 3). */
object DefaultBindings {
    val shortVideo: Map<Motion, DeviceAction> = mapOf(
        Motion.AIR_SWIPE_UP to DeviceAction.Swipe(Direction.UP),
        Motion.AIR_SWIPE_DOWN to DeviceAction.Swipe(Direction.DOWN),
        Motion.OPEN_PALM_HOLD to DeviceAction.TapCenter, // pause/play the video
        Motion.THUMBS_UP to DeviceAction.DoubleTapCenter, // like
        // No gesture pauses motion control (that blocked every other gesture); pausing is only
        // done from the notification.
        Motion.FIST_HOLD to DeviceAction.TapCenter, // pause/play the video
    )
}
