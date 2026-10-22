package com.kwan.mygesture.ui.profiles

import com.kwan.mygesture.data.DefaultProfiles
import com.kwan.mygesture.data.DeviceAction
import com.kwan.mygesture.data.Motion
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileConflictsTest {

    @Test fun defaultProfilesHaveNoWarnings() =
        DefaultProfiles.all.forEach { assertEquals(emptyList<String>(), ProfileConflicts.check(it)) }

    @Test fun pauseGesturesIsWarnedAbout() {
        val profile = DefaultProfiles.shortVideo.withBinding(Motion.VICTORY, DeviceAction.TogglePause)
        assertEquals(1, ProfileConflicts.check(profile).size)
    }

    @Test fun confusablePosesWithDifferentActionsAreWarnedAbout() {
        val profile = DefaultProfiles.shortVideo
            .withBinding(Motion.VICTORY, DeviceAction.VolumeUp)
            .withBinding(Motion.POINTING_UP, DeviceAction.VolumeDown)
        assertEquals(1, ProfileConflicts.check(profile).size)
    }

    @Test fun confusablePosesWithTheSameActionAreFine() {
        val profile = DefaultProfiles.shortVideo
            .withBinding(Motion.VICTORY, DeviceAction.VolumeUp)
            .withBinding(Motion.POINTING_UP, DeviceAction.VolumeUp)
        assertEquals(emptyList<String>(), ProfileConflicts.check(profile))
    }
}
