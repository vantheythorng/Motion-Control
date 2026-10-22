package com.kwan.mygesture.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import java.util.UUID

/**
 * Profiles and the active profile id, stored in the app's single Preferences DataStore.
 *
 * Reading never writes: until something is saved, the defaults are returned. Entries that can't be
 * decoded (e.g. written by a newer app version) are kept as raw JSON and written back unchanged,
 * so they're never lost.
 */
class ProfileRepository(private val dataStore: DataStore<Preferences>) {

    private class Stored(val profiles: List<Profile>, val unreadable: List<JsonElement>)

    val profiles: Flow<List<Profile>> =
        dataStore.data.map { decode(it)?.profiles ?: DefaultProfiles.all }.distinctUntilChanged()

    val activeProfile: Flow<Profile> = dataStore.data.map { prefs ->
        val list = decode(prefs)?.profiles ?: DefaultProfiles.all
        list.firstOrNull { it.id == prefs[ACTIVE_PROFILE_ID] } ?: list.first()
    }.distinctUntilChanged()

    /** Adds [profile] or replaces the one with the same id. */
    suspend fun upsert(profile: Profile): Boolean = update { list ->
        if (list.any { it.id == profile.id }) list.map { if (it.id == profile.id) profile else it }
        else list + profile
    }

    /** Refused (returns false) for the last remaining profile. */
    suspend fun delete(id: String): Boolean = update { list ->
        if (list.size <= 1 || list.none { it.id == id }) null else list.filterNot { it.id == id }
    }

    /** Saves a copy of [profile] under a new id and returns it. */
    suspend fun duplicate(profile: Profile): Profile? {
        val copy = profile.copy(id = UUID.randomUUID().toString(), name = "${profile.name} copy")
        return copy.takeIf { upsert(it) }
    }

    suspend fun setActive(id: String) {
        dataStore.edit { it[ACTIVE_PROFILE_ID] = id }
    }

    /** Explicit user reset: replaces everything, including entries this version can't read. */
    suspend fun resetToDefaults() {
        dataStore.edit {
            it[PROFILES] = json.encodeToString(ListSerializer(Profile.serializer()), DefaultProfiles.all)
            it[ACTIVE_PROFILE_ID] = DefaultProfiles.SHORT_VIDEO_ID
        }
    }

    /**
     * Applies [transform] to the stored list (or the defaults if nothing is stored yet). Returns
     * false if [transform] refuses (returns null) or if the stored data can't be parsed at all.
     */
    private suspend fun update(transform: (List<Profile>) -> List<Profile>?): Boolean {
        var applied = false
        dataStore.edit { prefs ->
            val stored = decode(prefs) ?: return@edit // unparsable: never overwrite it
            val updated = transform(stored.profiles) ?: return@edit
            write(prefs, updated, stored.unreadable)
            applied = true
        }
        return applied
    }

    private fun write(prefs: MutablePreferences, profiles: List<Profile>, unreadable: List<JsonElement>) {
        val elements = profiles.map { json.encodeToJsonElement(Profile.serializer(), it) } + unreadable
        prefs[PROFILES] = json.encodeToString(JsonArray.serializer(), JsonArray(elements))
    }

    /** Null when the stored value isn't a JSON array at all. */
    private fun decode(prefs: Preferences): Stored? {
        val raw = prefs[PROFILES] ?: return Stored(DefaultProfiles.all, emptyList())
        val elements = runCatching { json.parseToJsonElement(raw).jsonArray }.getOrNull() ?: return null
        val profiles = mutableListOf<Profile>()
        val unreadable = mutableListOf<JsonElement>()
        for (element in elements) {
            runCatching { json.decodeFromJsonElement(Profile.serializer(), element) }
                .onSuccess { profiles += it }
                .onFailure { unreadable += element }
        }
        return Stored(profiles.ifEmpty { DefaultProfiles.all }, unreadable)
    }

    companion object {
        private val PROFILES = stringPreferencesKey("profiles")
        private val ACTIVE_PROFILE_ID = stringPreferencesKey("active_profile_id")

        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
