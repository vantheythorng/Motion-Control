package com.kwan.mygesture.motion

import com.kwan.mygesture.data.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileTuningTest {

    private fun config(sensitivity: Float, cooldownMs: Long = 800) =
        ProfileTuning.configFor(Profile(id = "p", name = "P", sensitivity = sensitivity, cooldownMs = cooldownMs))

    @Test fun defaultSensitivityReproducesTheTunedValues() =
        assertEquals(GestureEngine.Config(), config(0.5f))

    @Test fun higherSensitivityIsMoreLenient() {
        val low = config(0f)
        val high = config(1f)
        assertTrue(high.swipeMinTravel < low.swipeMinTravel)
        assertTrue(high.minConfidence < low.minConfidence)
        assertTrue(high.holdMs < low.holdMs)
    }

    @Test fun onlyTunedFieldsChange() {
        val defaults = GestureEngine.Config()
        listOf(0f, 1f).forEach { s ->
            val c = config(s)
            assertEquals(defaults.minDistanceCm, c.minDistanceCm)
            assertEquals(defaults.maxDistanceCm, c.maxDistanceCm)
            assertEquals(defaults.handLostMs, c.handLostMs)
            assertEquals(defaults.armFrames, c.armFrames)
        }
    }

    @Test fun cooldownComesFromTheProfile() =
        assertEquals(1500L, config(0.5f, cooldownMs = 1500).cooldownMs)
}
