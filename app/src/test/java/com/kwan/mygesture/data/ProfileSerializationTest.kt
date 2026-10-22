package com.kwan.mygesture.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileSerializationTest {

    private val json = ProfileRepository.json

    @Test fun everyActionRoundTrips() {
        DeviceAction.choices.forEach { action ->
            val encoded = json.encodeToString(DeviceAction.serializer(), action)
            assertEquals(action, json.decodeFromString(DeviceAction.serializer(), encoded))
        }
    }

    @Test fun storedTypeNamesAreShortAndStable() {
        // Renaming or moving a class must not change what's on disk.
        assertEquals(
            """{"type":"swipe","direction":"UP"}""",
            json.encodeToString(DeviceAction.serializer(), DeviceAction.Swipe(Direction.UP)),
        )
        assertEquals("""{"type":"tap"}""", json.encodeToString(DeviceAction.serializer(), DeviceAction.TapCenter))
    }

    @Test fun profileRoundTrips() {
        val profile = DefaultProfiles.shortVideo.copy(sensitivity = 0.7f, cooldownMs = 1200)
        val encoded = json.encodeToString(Profile.serializer(), profile)
        assertEquals(profile, json.decodeFromString(Profile.serializer(), encoded))
        assertTrue("defaults are written", encoded.contains("\"version\":1"))
    }

    @Test fun unknownKeysAreIgnored() {
        val stored = """{"id":"x","name":"X","bindings":{"AIR_SWIPE_UP":{"type":"tap","color":"red"}},"future":true}"""
        val profile = json.decodeFromString(Profile.serializer(), stored)
        assertEquals(mapOf(Motion.AIR_SWIPE_UP to DeviceAction.TapCenter), profile.bindings)
    }

    @Test fun profilesSavedBeforeDistanceSettingsGetTheDefaults() {
        val stored = """{"id":"old","name":"Old","bindings":{},"sensitivity":0.5,"cooldownMs":800,"version":1}"""
        val profile = json.decodeFromString(Profile.serializer(), stored)
        assertEquals(Profile.DEFAULT_MIN_DISTANCE_CM, profile.minDistanceCm)
        assertEquals(Profile.DEFAULT_MAX_DISTANCE_CM, profile.maxDistanceCm)
    }

    @Test fun choosingNoneRemovesTheBinding() {
        val profile = DefaultProfiles.shortVideo.withBinding(Motion.AIR_SWIPE_UP, DeviceAction.None)
        assertTrue(Motion.AIR_SWIPE_UP !in profile.bindings)
    }
}
