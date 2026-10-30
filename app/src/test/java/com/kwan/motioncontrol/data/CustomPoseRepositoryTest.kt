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

class CustomPoseRepositoryTest {

    @get:Rule val tmp = TemporaryFolder()

    private lateinit var profiles: ProfileRepository

    private fun TestScope.repository(): CustomPoseRepository {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { File(tmp.root, "test.preferences_pb") },
        )
        profiles = ProfileRepository(dataStore)
        return CustomPoseRepository(dataStore, profiles)
    }

    private fun pose(id: String) = CustomPose(id, "Pose $id", imageSamples = listOf(listOf(0f, 1f)))

    @Test fun startsEmpty() = runTest {
        assertEquals(emptyList<CustomPose>(), repository().poses.first())
    }

    @Test fun saveAddsThenReplaces() = runTest {
        val repo = repository()
        assertTrue(repo.save(pose("a")))
        assertTrue(repo.save(pose("a").copy(name = "Rock")))
        assertEquals(listOf("Rock"), repo.poses.first().map { it.name })
    }

    @Test fun refusesANewPoseBeyondTheLimit() = runTest {
        val repo = repository()
        repeat(CustomPose.MAX_POSES) { assertTrue(repo.save(pose("$it"))) }
        assertFalse(repo.save(pose("one too many")))
        // Re-recording an existing pose is still allowed.
        assertTrue(repo.save(pose("0").copy(name = "Re-recorded")))
        assertEquals(CustomPose.MAX_POSES, repo.poses.first().size)
    }

    @Test fun renameAndTrigger() = runTest {
        val repo = repository()
        repo.save(pose("a"))
        repo.rename("a", "Rock")
        repo.setTrigger("a", PoseTrigger.QUICK)
        assertEquals(pose("a").copy(name = "Rock", trigger = PoseTrigger.QUICK), repo.poses.first().single())
    }

    @Test fun deletingAPoseRemovesItsBindingsFromEveryProfile() = runTest {
        val repo = repository()
        repo.save(pose("a"))
        repo.save(pose("b"))
        DefaultProfiles.all.forEach { profile ->
            profiles.upsert(
                profile.withCustomBinding("a", DeviceAction.TapCenter).withCustomBinding("b", DeviceAction.VolumeUp)
            )
        }
        repo.delete("a")
        assertEquals(listOf("b"), repo.poses.first().map { it.id })
        profiles.profiles.first().forEach { profile ->
            assertEquals(mapOf<String, DeviceAction>("b" to DeviceAction.VolumeUp), profile.customBindings)
        }
    }
}
