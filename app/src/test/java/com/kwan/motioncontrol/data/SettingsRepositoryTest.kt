package com.kwan.motioncontrol.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
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

    @Test fun bubbleIsOnByDefaultAndCanBeTurnedOff() = runTest {
        val settings = settings()
        assertTrue(settings.bubbleEnabled.first())
        settings.setBubbleEnabled(false)
        assertFalse(settings.bubbleEnabled.first())
    }

    @Test fun hidingTheBubbleTurnsItOffAndAsksForTheHintOnlyOnce() = runTest {
        val settings = settings()
        assertTrue(settings.hideBubble())
        assertFalse(settings.bubbleEnabled.first())
        settings.setBubbleEnabled(true)
        assertFalse(settings.hideBubble())
        assertFalse(settings.bubbleEnabled.first())
    }
}
