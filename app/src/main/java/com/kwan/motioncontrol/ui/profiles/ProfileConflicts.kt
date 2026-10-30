package com.kwan.motioncontrol.ui.profiles

import com.kwan.motioncontrol.data.CustomPose
import com.kwan.motioncontrol.data.DeviceAction
import com.kwan.motioncontrol.data.Profile
import com.kwan.motioncontrol.data.label

/** Warnings shown at the top of the profile editor. */
object ProfileConflicts {

    /** [customPoses] names the profile's custom bindings. */
    fun check(profile: Profile, customPoses: List<CustomPose> = emptyList()): List<String> = buildList {
        val pausing = profile.bindings.filterValues { it == DeviceAction.TogglePause }.keys.map { it.label } +
            customPoses.filter { profile.customBindings[it.id] == DeviceAction.TogglePause }.map { it.name }
        pausing.forEach { name ->
            add(
                "$name pauses gestures: all other gestures are ignored until you " +
                    "repeat it or tap Resume in the notification."
            )
        }
        // A custom pose this profile uses wins over the built-in gesture it looks like.
        customPoses.filter { it.id in profile.customBindings }.forEach { pose ->
            val motion = pose.resemblesMotion ?: return@forEach
            val hidden = profile.bindings[motion] ?: return@forEach
            add(
                "${pose.name} looks like ${motion.label}. In this profile ${pose.name} wins, so " +
                    "${motion.label} (${hidden.label}) won't fire. Remove one of them, or re-record ${pose.name}."
            )
        }
    }
}
