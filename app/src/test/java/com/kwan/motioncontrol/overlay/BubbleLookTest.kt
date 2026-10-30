package com.kwan.motioncontrol.overlay

import com.kwan.motioncontrol.motion.MotionState
import org.junit.Assert.assertEquals
import org.junit.Test

class BubbleLookTest {

    private val watching = MotionState(running = true, cameraActive = true)

    @Test fun offWhenNotRunning() =
        assertEquals(BubbleLook.OFF, BubbleLook.from(MotionState(), handInRange = true, flashing = true))

    @Test fun offWhenPaused() =
        assertEquals(BubbleLook.OFF, BubbleLook.from(watching.copy(paused = true), handInRange = true, flashing = false))

    @Test fun offWhenTheCameraIsOff() =
        assertEquals(BubbleLook.OFF, BubbleLook.from(watching.copy(cameraActive = false), handInRange = false, flashing = false))

    @Test fun watchingWithoutAHand() =
        assertEquals(BubbleLook.WATCHING, BubbleLook.from(watching, handInRange = false, flashing = false))

    @Test fun handInRange() =
        assertEquals(BubbleLook.HAND, BubbleLook.from(watching, handInRange = true, flashing = false))

    @Test fun flashWinsOverHand() =
        assertEquals(BubbleLook.FIRED, BubbleLook.from(watching, handInRange = true, flashing = true))
}
