package com.kwan.mygesture.data

enum class Direction { UP, DOWN, LEFT, RIGHT }

// @Serializable annotations are added in Phase 3 together with profile persistence.
sealed interface DeviceAction {
    /** Finger direction on screen: UP moves the finger up, so feeds advance to the next item. */
    data class Swipe(val direction: Direction) : DeviceAction
    data object TapCenter : DeviceAction
    data object DoubleTapCenter : DeviceAction
    /** One of AccessibilityService.GLOBAL_ACTION_* (BACK, HOME, RECENTS, TAKE_SCREENSHOT). */
    data class Global(val action: Int) : DeviceAction
    data object VolumeUp : DeviceAction
    data object VolumeDown : DeviceAction
    data object TogglePause : DeviceAction
    data object None : DeviceAction
}
