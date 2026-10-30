package com.kwan.motioncontrol.motion

import org.junit.Assert.assertEquals
import org.junit.Test

class ArmCameraDecisionTest {

    @Test fun missingSetupOpensTheApp() =
        assertEquals(ArmStep.OPEN_APP, ArmCameraDecision.next(setupOk = false, MotionState(), 0))

    @Test fun waitsUntilTheServiceRuns() =
        assertEquals(ArmStep.WAIT, ArmCameraDecision.next(true, MotionState(), 200))

    @Test fun finishesOnceRunning() =
        assertEquals(ArmStep.FINISH, ArmCameraDecision.next(true, MotionState(running = true), 200))

    @Test fun startErrorOpensTheApp() =
        assertEquals(ArmStep.OPEN_APP, ArmCameraDecision.next(true, MotionState(error = "Couldn't start"), 200))

    @Test fun timeoutOpensTheApp() =
        assertEquals(ArmStep.OPEN_APP, ArmCameraDecision.next(true, MotionState(), ArmCameraDecision.TIMEOUT_MS))
}
