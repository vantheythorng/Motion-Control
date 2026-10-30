package com.kwan.motioncontrol.ui.profiles

import com.kwan.motioncontrol.data.CustomPose
import com.kwan.motioncontrol.data.DefaultProfiles
import com.kwan.motioncontrol.data.DeviceAction
import com.kwan.motioncontrol.data.Motion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileConflictsTest {

    @Test fun defaultProfilesHaveNoWarnings() =
        DefaultProfiles.all.forEach { assertEquals(emptyList<String>(), ProfileConflicts.check(it)) }

    @Test fun pauseGesturesIsWarnedAbout() {
        val profile = DefaultProfiles.shortVideo.withBinding(Motion.THUMBS_DOWN, DeviceAction.TogglePause)
        assertEquals(1, ProfileConflicts.check(profile).size)
    }

    private val rock = CustomPose("rock", "Rock", resembles = Motion.THUMBS_UP.name)

    @Test fun customPoseThatHidesABuiltInGestureIsWarnedAbout() {
        // Short video binds Thumbs up (like); using Rock there hides it.
        val profile = DefaultProfiles.shortVideo.withCustomBinding("rock", DeviceAction.VolumeUp)
        val warnings = ProfileConflicts.check(profile, listOf(rock))
        assertEquals(1, warnings.size)
        assertTrue(warnings.single(), warnings.single().startsWith("Rock looks like Thumbs up"))
    }

    @Test fun noWarningWhenTheProfileDoesNotUseThePose() =
        assertEquals(emptyList<String>(), ProfileConflicts.check(DefaultProfiles.shortVideo, listOf(rock)))

    @Test fun noWarningWhenTheLookAlikeBuiltInIsUnbound() {
        val profile = DefaultProfiles.global.withCustomBinding("rock", DeviceAction.VolumeUp) // Global doesn't bind Thumbs up
        assertEquals(emptyList<String>(), ProfileConflicts.check(profile, listOf(rock)))
    }

    @Test fun poseThatResemblesARemovedGestureGetsNoWarning() {
        val old = CustomPose("v", "Peace", resembles = "VICTORY")
        val profile = DefaultProfiles.shortVideo.withCustomBinding("v", DeviceAction.VolumeUp)
        assertEquals(emptyList<String>(), ProfileConflicts.check(profile, listOf(old)))
    }
}
