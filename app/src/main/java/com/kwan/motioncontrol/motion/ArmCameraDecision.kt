package com.kwan.motioncontrol.motion

/** What [ArmCameraActivity] does next while it waits for motion control to start. */
enum class ArmStep { WAIT, FINISH, OPEN_APP }

object ArmCameraDecision {
    const val TIMEOUT_MS = 1_500L

    /**
     * Stay until the service is running; then finish, returning the user to the app they were in.
     * Missing setup, a start error or a timeout open the app instead, where the problem is shown.
     */
    fun next(setupOk: Boolean, state: MotionState, elapsedMs: Long): ArmStep = when {
        !setupOk -> ArmStep.OPEN_APP
        state.running -> ArmStep.FINISH
        state.error != null -> ArmStep.OPEN_APP
        elapsedMs >= TIMEOUT_MS -> ArmStep.OPEN_APP
        else -> ArmStep.WAIT
    }
}
