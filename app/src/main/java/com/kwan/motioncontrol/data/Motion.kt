package com.kwan.motioncontrol.data

enum class Motion {
    AIR_SWIPE_UP, AIR_SWIPE_DOWN, AIR_SWIPE_LEFT, AIR_SWIPE_RIGHT,
    OPEN_PALM_HOLD, THUMBS_UP, THUMBS_DOWN, FIST_HOLD, VICTORY, POINTING_UP,
}

val Motion.label: String
    get() = when (this) {
        Motion.AIR_SWIPE_UP -> "Swipe Up"
        Motion.AIR_SWIPE_DOWN -> "Swipe Down"
        Motion.AIR_SWIPE_LEFT -> "Swipe Left"
        Motion.AIR_SWIPE_RIGHT -> "Swipe Right"
        Motion.OPEN_PALM_HOLD -> "Open palm (hold)"
        Motion.THUMBS_UP -> "Thumbs up"
        Motion.THUMBS_DOWN -> "Thumbs down"
        Motion.FIST_HOLD -> "Fist (hold)"
        Motion.VICTORY -> "Victory ✌"
        Motion.POINTING_UP -> "Pointing up"
    }
