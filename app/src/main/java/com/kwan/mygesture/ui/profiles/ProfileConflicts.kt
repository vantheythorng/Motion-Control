package com.kwan.mygesture.ui.profiles

import com.kwan.mygesture.data.DeviceAction
import com.kwan.mygesture.data.Motion
import com.kwan.mygesture.data.Profile
import com.kwan.mygesture.data.label

/** Warnings shown at the top of the profile editor. */
object ProfileConflicts {

    /**
     * Poses the recognizer often confuses with each other. Fist vs thumbs up isn't listed: the fist
     * needs a full second held still, and the default profile deliberately binds both.
     */
    private val confusablePairs = listOf(
        Motion.VICTORY to Motion.POINTING_UP, // differ by one finger
    )

    fun check(profile: Profile): List<String> = buildList {
        profile.bindings.filterValues { it == DeviceAction.TogglePause }.keys.forEach { motion ->
            add(
                "${motion.label} pauses gestures: all other gestures are ignored until you " +
                    "repeat it or tap Resume in the notification."
            )
        }
        for ((a, b) in confusablePairs) {
            val actionA = profile.bindings[a] ?: continue
            val actionB = profile.bindings[b] ?: continue
            if (actionA != actionB) {
                add("${a.label} and ${b.label} are easily confused, but do different things.")
            }
        }
    }
}
