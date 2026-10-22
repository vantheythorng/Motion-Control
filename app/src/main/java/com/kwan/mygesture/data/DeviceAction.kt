package com.kwan.mygesture.data

import android.accessibilityservice.AccessibilityService
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class Direction { UP, DOWN, LEFT, RIGHT }

/**
 * Something motion control does when a [Motion] fires. Stored in profiles as JSON, so every
 * subtype has a short, stable [SerialName]: renaming or moving a class must not break saved data.
 */
@Serializable
sealed interface DeviceAction {
    /** Finger direction on screen: UP moves the finger up, so feeds advance to the next item. */
    @Serializable @SerialName("swipe")
    data class Swipe(val direction: Direction) : DeviceAction

    @Serializable @SerialName("tap")
    data object TapCenter : DeviceAction

    @Serializable @SerialName("double_tap")
    data object DoubleTapCenter : DeviceAction

    /** One of AccessibilityService.GLOBAL_ACTION_* (BACK, HOME, RECENTS, TAKE_SCREENSHOT). */
    @Serializable @SerialName("global")
    data class Global(val action: Int) : DeviceAction

    @Serializable @SerialName("volume_up")
    data object VolumeUp : DeviceAction

    @Serializable @SerialName("volume_down")
    data object VolumeDown : DeviceAction

    @Serializable @SerialName("toggle_pause")
    data object TogglePause : DeviceAction

    /** "Do nothing". Never stored: choosing it removes the binding (see [Profile.withBinding]). */
    @Serializable @SerialName("none")
    data object None : DeviceAction

    companion object {
        /** The actions offered in the profile editor, in display order. */
        val choices: List<DeviceAction> = listOf(
            None,
            Swipe(Direction.UP),
            Swipe(Direction.DOWN),
            Swipe(Direction.LEFT),
            Swipe(Direction.RIGHT),
            TapCenter,
            DoubleTapCenter,
            Global(AccessibilityService.GLOBAL_ACTION_BACK),
            Global(AccessibilityService.GLOBAL_ACTION_HOME),
            Global(AccessibilityService.GLOBAL_ACTION_RECENTS),
            Global(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT),
            VolumeUp,
            VolumeDown,
            TogglePause,
        )
    }
}

val DeviceAction.label: String
    get() = when (this) {
        is DeviceAction.Swipe -> when (direction) {
            Direction.UP -> "Next (swipe up)"
            Direction.DOWN -> "Previous (swipe down)"
            Direction.LEFT -> "Swipe left"
            Direction.RIGHT -> "Swipe right"
        }
        DeviceAction.TapCenter -> "Play/pause (tap)"
        DeviceAction.DoubleTapCenter -> "Like (double tap)"
        is DeviceAction.Global -> when (action) {
            AccessibilityService.GLOBAL_ACTION_BACK -> "Back"
            AccessibilityService.GLOBAL_ACTION_HOME -> "Home"
            AccessibilityService.GLOBAL_ACTION_RECENTS -> "Recent apps"
            AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT -> "Screenshot"
            else -> "System action $action"
        }
        DeviceAction.VolumeUp -> "Volume up"
        DeviceAction.VolumeDown -> "Volume down"
        DeviceAction.TogglePause -> "Pause gestures"
        DeviceAction.None -> "None"
    }
