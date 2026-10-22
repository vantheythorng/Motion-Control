package com.kwan.motioncontrol.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SettingsRepositoryTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test fun onlyInSelectedAppsDefaultsToTrueAndCanBeChanged() = runTest {
        val settings = SettingsRepository(
            PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { File(tmp.root, "s.preferences_pb") })
        )
        assertTrue(settings.onlyInSelectedApps.first())
        settings.setOnlyInSelectedApps(false)
        assertFalse(settings.onlyInSelectedApps.first())
    }
}
