package com.kwan.motioncontrol.motion

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CameraDecisionTest {

    private val running = MutableStateFlow(true)
    private val appAllowed = MutableStateFlow(true)
    private val testMode = MutableStateFlow(false)
    private val screen = MutableStateFlow(true)

    /** Collects the decisions; returns the live list. */
    private fun TestScope.decisions(): List<Boolean> {
        val out = mutableListOf<Boolean>()
        backgroundScope.launch { cameraWanted(running, appAllowed, testMode, screen, offDelayMs = 500).collect { out += it } }
        runCurrent()
        return out
    }

    @Test fun profiledAppTurnsTheCameraOnImmediately() = runTest {
        appAllowed.value = false
        val out = decisions()
        advanceTimeBy(600)
        appAllowed.value = true
        runCurrent()
        assertEquals(listOf(false, true), out)
    }

    @Test fun briefVisitToAnAppWithoutAProfileKeepsTheCameraOn() = runTest {
        val out = decisions()
        appAllowed.value = false
        advanceTimeBy(300)
        appAllowed.value = true
        advanceTimeBy(1000)
        assertEquals(listOf(true), out)
    }

    @Test fun stayingInAnAppWithoutAProfileTurnsTheCameraOffAfterTheDelay() = runTest {
        val out = decisions()
        appAllowed.value = false
        advanceTimeBy(400)
        assertEquals(listOf(true), out)
        advanceTimeBy(200)
        assertEquals(listOf(true, false), out)
    }

    @Test fun testModeAndScreenOffTurnItOffImmediately() = runTest {
        val out = decisions()
        testMode.value = true
        runCurrent()
        testMode.value = false
        runCurrent()
        screen.value = false
        runCurrent()
        assertEquals(listOf(true, false, true, false), out)
    }

    @Test fun stoppingMotionControlTurnsItOffImmediately() = runTest {
        val out = decisions()
        running.value = false
        runCurrent()
        assertEquals(listOf(true, false), out)
    }
}
