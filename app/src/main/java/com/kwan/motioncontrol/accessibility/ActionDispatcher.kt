package com.kwan.motioncontrol.accessibility

import android.content.Context
import android.media.AudioManager
import com.kwan.motioncontrol.data.DeviceAction

class ActionDispatcher(context: Context) {

    private val audioManager = context.getSystemService(AudioManager::class.java)

    /** Returns false when the action couldn't be performed (e.g. accessibility service off). */
    fun dispatch(action: DeviceAction): Boolean {
        when (action) {
            DeviceAction.VolumeUp -> return adjustVolume(AudioManager.ADJUST_RAISE)
            DeviceAction.VolumeDown -> return adjustVolume(AudioManager.ADJUST_LOWER)
            // Handled by GestureEngine once it exists (Phase 2).
            DeviceAction.TogglePause, DeviceAction.None -> return true
            else -> Unit
        }
        val service = AirScrollAccessibilityService.instance.value ?: return false
        return when (action) {
            is DeviceAction.Swipe -> service.swipe(action.direction)
            DeviceAction.TapCenter -> service.tapCenter(times = 1)
            DeviceAction.DoubleTapCenter -> service.tapCenter(times = 2)
            is DeviceAction.Global -> service.global(action.action)
            else -> false
        }
    }

    private fun adjustVolume(direction: Int): Boolean {
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        return true
    }
}
