package com.kwan.motioncontrol.overlay

import com.kwan.motioncontrol.motion.MotionState

/** What the floating bubble's color says about motion control (SPEC §5.5). */
enum class BubbleLook {
    /** Off, paused, or the camera is off (no profile for this app, screen locked, Test mode). */
    OFF,
    /** The camera is on but no hand is in range. */
    WATCHING,
    /** A hand is in the working range. */
    HAND,
    /** Brief flash after a gesture fired. */
    FIRED;

    companion object {
        fun from(state: MotionState, handInRange: Boolean, flashing: Boolean): BubbleLook = when {
            !state.running || state.paused || !state.cameraActive -> OFF
            flashing -> FIRED
            handInRange -> HAND
            else -> WATCHING
        }
    }
}
