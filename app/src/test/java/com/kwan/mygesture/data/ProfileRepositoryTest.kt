package com.kwan.mygesture.data

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
        assertEquals(DefaultProfiles.SHORT_VIDEO_ID, repo.activeProfile.first().id)
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

    @Test fun setActiveSwitchesTheActiveProfile() = runTest {
        val repo = repository()
        repo.setActive(DefaultProfiles.GLOBAL_ID)
        assertEquals(DefaultProfiles.global, repo.activeProfile.first())
    }

    @Test fun deletingTheLastProfileIsRefused() = runTest {
        val repo = repository()
        assertTrue(repo.delete(DefaultProfiles.GLOBAL_ID))
        assertFalse(repo.delete(DefaultProfiles.SHORT_VIDEO_ID))
        assertEquals(listOf(DefaultProfiles.shortVideo), repo.profiles.first())
    }

    @Test fun activeFallsBackToFirstWhenDeleted() = runTest {
        val repo = repository()
        repo.setActive(DefaultProfiles.GLOBAL_ID)
        repo.delete(DefaultProfiles.GLOBAL_ID)
        assertEquals(DefaultProfiles.shortVideo, repo.activeProfile.first())
    }

    @Test fun duplicateGetsANewId() = runTest {
        val repo = repository()
        val copy = repo.duplicate(DefaultProfiles.shortVideo)!!
        assertTrue(copy.id != DefaultProfiles.SHORT_VIDEO_ID)
        assertEquals(DefaultProfiles.shortVideo.bindings, copy.bindings)
        assertEquals(3, repo.profiles.first().size)
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
        assertEquals(DefaultProfiles.SHORT_VIDEO_ID, repo.activeProfile.first().id)
    }

    private companion object {
        val PROFILES = stringPreferencesKey("profiles")
    }
}
