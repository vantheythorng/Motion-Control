package com.kwan.motioncontrol.data

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

    @Test fun customBindingsRoundTrip() {
        val profile = DefaultProfiles.global.withCustomBinding("pose-1", DeviceAction.TapCenter)
        val encoded = json.encodeToString(Profile.serializer(), profile)
        assertEquals(profile, json.decodeFromString(Profile.serializer(), encoded))
    }

    @Test fun profilesSavedBeforeCustomPosesHaveNoCustomBindings() {
        val stored = """{"id":"old","name":"Old","bindings":{"AIR_SWIPE_UP":{"type":"tap"}},"version":1}"""
        assertEquals(emptyMap<String, DeviceAction>(), json.decodeFromString(Profile.serializer(), stored).customBindings)
    }

    @Test fun choosingNoneRemovesTheCustomBinding() {
        val profile = DefaultProfiles.global.withCustomBinding("pose-1", DeviceAction.TapCenter)
            .withCustomBinding("pose-1", DeviceAction.None)
        assertTrue("pose-1" !in profile.customBindings)
    }

    @Test fun customPoseRoundTrips() {
        val pose = CustomPose(
            "p", "Rock", PoseTrigger.QUICK, listOf(listOf(0.1f, 0.2f)), listOf(listOf(0.1f, 0.2f, 0.3f)),
            resembles = Motion.THUMBS_UP.name,
        )
        val encoded = json.encodeToString(CustomPose.serializer(), pose)
        assertEquals(pose, json.decodeFromString(CustomPose.serializer(), encoded))
    }

    @Test fun bindingsForRemovedMotionsAreDroppedNotTheWholeProfile() {
        val stored = """{"id":"sv","name":"Short video","bindings":{"AIR_SWIPE_UP":{"type":"swipe","direction":"UP"},""" +
            """"FIST_HOLD":{"type":"tap"},"VICTORY":{"type":"volume_up"},"POINTING_UP":{"type":"tap"}},"version":1}"""
        val profile = json.decodeFromString(Profile.serializer(), stored)
        assertEquals(
            mapOf(Motion.AIR_SWIPE_UP to DeviceAction.Swipe(Direction.UP), Motion.FIST_HOLD to DeviceAction.TapCenter),
            profile.bindings,
        )
    }

    @Test fun bindingsAreStoredByMotionName() {
        val profile = Profile("p", "P", bindings = mapOf(Motion.THUMBS_UP to DeviceAction.TapCenter))
        assertTrue(json.encodeToString(Profile.serializer(), profile).contains(""""bindings":{"THUMBS_UP":{"type":"tap"}}"""))
    }
}
