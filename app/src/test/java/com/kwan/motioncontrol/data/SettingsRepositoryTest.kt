package com.kwan.motioncontrol.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SettingsRepositoryTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun TestScope.settings() = SettingsRepository(
        PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { File(tmp.root, "s.preferences_pb") })
    )

    @Test fun onlyInSelectedAppsDefaultsToTrueAndCanBeChanged() = runTest {
        val settings = settings()
        assertTrue(settings.onlyInSelectedApps.first())
        settings.setOnlyInSelectedApps(false)
        assertFalse(settings.onlyInSelectedApps.first())
    }

    @Test fun bubbleIsOffByDefaultAndCanBeTurnedOn() = runTest {
        val settings = settings()
        assertFalse(settings.bubbleEnabled.first())
        settings.setBubbleEnabled(true)
        assertTrue(settings.bubbleEnabled.first())
    }

    @Test fun hidingTheBubbleTurnsItOffAndAsksForTheHintOnlyOnce() = runTest {
        val settings = settings()
        assertTrue(settings.hideBubble())
        assertFalse(settings.bubbleEnabled.first())
        settings.setBubbleEnabled(true)
        assertFalse(settings.hideBubble())
        assertFalse(settings.bubbleEnabled.first())
    }

    @Test fun onboardingIsNotDoneUntilFinished() = runTest {
        val settings = settings()
        assertFalse(settings.onboardingDone.first())
        settings.setOnboardingDone()
        assertTrue(settings.onboardingDone.first())
    }

    @Test fun themeFollowsThePhoneByDefaultAndCanBeChanged() = runTest {
        val settings = settings()
        assertEquals(ThemeMode.SYSTEM, settings.themeMode.first())
        settings.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, settings.themeMode.first())
    }

    @Test fun wallpaperColorsAreOnByDefaultAndCanBeTurnedOff() = runTest {
        val settings = settings()
        assertTrue(settings.dynamicColor.first())
        settings.setDynamicColor(false)
        assertFalse(settings.dynamicColor.first())
    }
}
