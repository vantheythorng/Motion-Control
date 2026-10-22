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

    private fun range(minCm: Float, maxCm: Float) =
        ProfileTuning.distanceRange(Profile(id = "p", name = "P", minDistanceCm = minCm, maxDistanceCm = maxCm))

    @Test fun distanceRangeComesFromTheProfile() {
        val c = ProfileTuning.configFor(Profile(id = "p", name = "P", minDistanceCm = 50f, maxDistanceCm = 150f))
        assertEquals(50f, c.minDistanceCm)
        assertEquals(150f, c.maxDistanceCm)
    }

    @Test fun distanceRangeIsClampedToSupportedLimits() =
        assertEquals(10f to 200f, range(0f, 500f))

    @Test fun distanceRangeIsNeverTooNarrow() {
        assertEquals(40f to 50f, range(48f, 50f)) // min pushed down to keep a 10 cm span
        assertEquals(10f to 20f, range(10f, 12f)) // max raised to the smallest allowed
    }

    @Test fun distanceLabels() {
        fun label(minCm: Float, maxCm: Float) =
            ProfileTuning.distanceLabel(Profile(id = "p", name = "P", minDistanceCm = minCm, maxDistanceCm = maxCm))
        assertEquals("30 cm – 1 m", label(30f, 100f))
        assertEquals("15 cm – 80 cm", label(15f, 80f))
        assertEquals("50 cm – 150 cm", label(50f, 150f))
        assertEquals("1 m – 2 m", label(100f, 200f))
    }
}
