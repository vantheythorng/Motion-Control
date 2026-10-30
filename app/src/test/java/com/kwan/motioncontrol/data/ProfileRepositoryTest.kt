package com.kwan.motioncontrol.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
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

class ProfileRepositoryTest {

    @get:Rule val tmp = TemporaryFolder()

    private lateinit var dataStore: DataStore<Preferences>

    /** One store per file; the file name must end in .preferences_pb. */
    private fun TestScope.repository(): ProfileRepository {
        dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { File(tmp.root, "test.preferences_pb") },
        )
        return ProfileRepository(dataStore)
    }

    private suspend fun rawProfiles(): String? = dataStore.data.first()[PROFILES]

    @Test fun emptyStoreReturnsDefaultsWithoutWriting() = runTest {
        val repo = repository()
        assertEquals(DefaultProfiles.all, repo.profiles.first())
        assertEquals(DefaultProfiles.GLOBAL_ID, repo.defaultProfile.first().id)
        assertTrue(dataStore.data.first().asMap().isEmpty())
    }

    @Test fun upsertAddsAndReplaces() = runTest {
        val repo = repository()
        val mine = Profile(id = "mine", name = "Mine")
        assertTrue(repo.upsert(mine))
        assertEquals(DefaultProfiles.all + mine, repo.profiles.first())

        val renamed = DefaultProfiles.shortVideo.copy(name = "Reels")
        assertTrue(repo.upsert(renamed))
        assertEquals(listOf(renamed, DefaultProfiles.global, mine), repo.profiles.first())
    }

    @Test fun setDefaultSwitchesTheDefaultProfile() = runTest {
        val repo = repository()
        repo.setDefault(DefaultProfiles.SHORT_VIDEO_ID)
        assertEquals(DefaultProfiles.shortVideo, repo.defaultProfile.first())
    }

    @Test fun phase3ActiveProfileKeyIsIgnored() = runTest {
        // Its Short video fallback would have given every other app tap/double-tap actions.
        val repo = repository()
        dataStore.edit { it[stringPreferencesKey("active_profile_id")] = DefaultProfiles.SHORT_VIDEO_ID }
        assertEquals(DefaultProfiles.global, repo.defaultProfile.first())
    }

    @Test fun deletingTheLastProfileIsRefused() = runTest {
        val repo = repository()
        assertTrue(repo.delete(DefaultProfiles.GLOBAL_ID))
        assertFalse(repo.delete(DefaultProfiles.SHORT_VIDEO_ID))
        assertEquals(listOf(DefaultProfiles.shortVideo), repo.profiles.first())
    }

    @Test fun defaultFallsBackToAProfileWithoutAppsWhenDeleted() = runTest {
        val repo = repository()
        val other = Profile(id = "other", name = "Other")
        repo.upsert(other)
        repo.assignApps(DefaultProfiles.SHORT_VIDEO_ID, setOf("com.ss.android.ugc.trill"))
        repo.setDefault(DefaultProfiles.GLOBAL_ID)
        repo.delete(DefaultProfiles.GLOBAL_ID)
        // Short video has an app, so the first profile without apps is preferred.
        assertEquals(other, repo.defaultProfile.first())
    }

    @Test fun duplicateGetsANewIdAndNoApps() = runTest {
        val repo = repository()
        val copy = repo.duplicate(DefaultProfiles.shortVideo)!!
        assertTrue(copy.id != DefaultProfiles.SHORT_VIDEO_ID)
        assertEquals(DefaultProfiles.shortVideo.bindings, copy.bindings)
        // An app belongs to one profile: the copy must not claim (and be shadowed for) the same apps.
        assertTrue(copy.packages.isEmpty())
        assertEquals(3, repo.profiles.first().size)
    }

    @Test fun assignAppsMovesThemFromOtherProfiles() = runTest {
        val repo = repository()
        val tiktok = "com.ss.android.ugc.trill"
        assertTrue(repo.assignApps(DefaultProfiles.SHORT_VIDEO_ID, setOf(tiktok)))
        assertTrue(repo.assignApps(DefaultProfiles.GLOBAL_ID, setOf(tiktok, "com.android.chrome")))
        val byId = repo.profiles.first().associateBy { it.id }
        assertTrue(tiktok in byId.getValue(DefaultProfiles.GLOBAL_ID).packages)
        assertTrue(tiktok !in byId.getValue(DefaultProfiles.SHORT_VIDEO_ID).packages)
        // Every app is in at most one profile.
        val all = byId.values.flatMap { it.packages }
        assertEquals(all.size, all.toSet().size)
    }

    @Test fun assignAppsToAMissingProfileIsRefused() = runTest {
        val repo = repository()
        assertFalse(repo.assignApps("nope", setOf("com.android.chrome")))
    }

    @Test fun unassignAppRemovesOnlyFromThatProfile() = runTest {
        val repo = repository()
        repo.assignApps(DefaultProfiles.SHORT_VIDEO_ID, setOf("com.ss.android.ugc.trill", "com.instagram.android"))
        repo.unassignApp(DefaultProfiles.SHORT_VIDEO_ID, "com.instagram.android")
        val shortVideo = repo.profiles.first().first { it.id == DefaultProfiles.SHORT_VIDEO_ID }
        assertEquals(setOf("com.ss.android.ugc.trill"), shortVideo.packages)
    }

    @Test fun routingReadsProfilesDefaultAndSettingTogether() = runTest {
        val repo = repository()
        assertEquals(Routing(DefaultProfiles.all, DefaultProfiles.global, onlyInSelectedApps = true), repo.routing.first())
        SettingsRepository(dataStore).setOnlyInSelectedApps(false)
        assertFalse(repo.routing.first().onlyInSelectedApps)
    }

    @Test fun unreadableEntriesAreSkippedButKeptOnWrite() = runTest {
        val repo = repository()
        // e.g. written by a newer app version with an action this one doesn't know.
        val future = """{"id":"future","name":"Future","bindings":{"AIR_SWIPE_UP":{"type":"teleport"}}}"""
        val known = ProfileRepository.json.encodeToString(Profile.serializer(), DefaultProfiles.global)
        dataStore.edit { it[PROFILES] = "[$known,$future]" }

        assertEquals(listOf(DefaultProfiles.global), repo.profiles.first())
        assertTrue(repo.upsert(Profile(id = "mine", name = "Mine")))
        assertEquals(2, repo.profiles.first().size)
        assertTrue("unreadable entry preserved", rawProfiles()!!.contains("teleport"))
    }

    @Test fun unparsableDataIsNeverOverwritten() = runTest {
        val repo = repository()
        dataStore.edit { it[PROFILES] = "not json" }

        assertEquals(DefaultProfiles.all, repo.profiles.first())
        assertFalse(repo.upsert(Profile(id = "mine", name = "Mine")))
        assertEquals("not json", rawProfiles())
    }

    @Test fun resetToDefaultsReplacesEverything() = runTest {
        val repo = repository()
        dataStore.edit { it[PROFILES] = "not json" }
        repo.resetToDefaults()
        assertEquals(DefaultProfiles.all, repo.profiles.first())
        assertEquals(DefaultProfiles.GLOBAL_ID, repo.defaultProfile.first().id)
    }

    private companion object {
        val PROFILES = stringPreferencesKey("profiles")
    }
}
