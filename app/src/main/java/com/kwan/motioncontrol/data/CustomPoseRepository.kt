package com.kwan.motioncontrol.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray

/**
 * The user's custom poses, in the app's single DataStore. Like profiles, entries this version
 * can't decode are kept as raw JSON and written back unchanged.
 */
class CustomPoseRepository(
    private val dataStore: DataStore<Preferences>,
    private val profiles: ProfileRepository,
) {
    private class Stored(val poses: List<CustomPose>, val unreadable: List<JsonElement>)

    val poses: Flow<List<CustomPose>> = dataStore.data.map { decode(it).poses }.distinctUntilChanged()

    /**
     * Adds [pose] or replaces the one with the same id. Refused (false) for a new pose once there
     * are [CustomPose.MAX_POSES].
     */
    suspend fun save(pose: CustomPose): Boolean = update { list ->
        when {
            list.any { it.id == pose.id } -> list.map { if (it.id == pose.id) pose else it }
            list.size >= CustomPose.MAX_POSES -> null
            else -> list + pose
        }
    }

    suspend fun rename(id: String, name: String): Boolean = update { list ->
        list.map { if (it.id == id) it.copy(name = name) else it }
    }

    suspend fun setTrigger(id: String, trigger: PoseTrigger): Boolean = update { list ->
        list.map { if (it.id == id) it.copy(trigger = trigger) else it }
    }

    /** Deletes the pose and its bindings in every profile (bindings first, so none is left dangling). */
    suspend fun delete(id: String) {
        profiles.removeCustomBindings(id)
        update { list -> list.filterNot { it.id == id } }
    }

    private suspend fun update(transform: (List<CustomPose>) -> List<CustomPose>?): Boolean {
        var applied = false
        dataStore.edit { prefs ->
            val stored = decode(prefs)
            val updated = transform(stored.poses) ?: return@edit
            val elements = updated.map { json.encodeToJsonElement(CustomPose.serializer(), it) } + stored.unreadable
            prefs[POSES] = json.encodeToString(JsonArray.serializer(), JsonArray(elements))
            applied = true
        }
        return applied
    }

    private fun decode(prefs: Preferences): Stored {
        val raw = prefs[POSES] ?: return Stored(emptyList(), emptyList())
        val elements = runCatching { json.parseToJsonElement(raw).jsonArray }.getOrNull()
            ?: return Stored(emptyList(), emptyList())
        val poses = mutableListOf<CustomPose>()
        val unreadable = mutableListOf<JsonElement>()
        for (element in elements) {
            runCatching { json.decodeFromJsonElement(CustomPose.serializer(), element) }
                .onSuccess { poses += it }
                .onFailure { unreadable += element }
        }
        return Stored(poses, unreadable)
    }

    private companion object {
        val POSES = stringPreferencesKey("custom_poses")
        val json = ProfileRepository.json
    }
}
