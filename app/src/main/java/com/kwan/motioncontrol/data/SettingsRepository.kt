package com.kwan.motioncontrol.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** App settings, in the same DataStore as the profiles. */
class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    /**
     * True (default): the camera is off in apps without their own profile. False ("Use in all
     * apps"): those apps use the default profile.
     */
    val onlyInSelectedApps: Flow<Boolean> = dataStore.data.map(::onlyInSelectedApps).distinctUntilChanged()

    suspend fun setOnlyInSelectedApps(only: Boolean) {
        dataStore.edit { it[ONLY_IN_SELECTED_APPS] = only }
    }

    companion object {
        private val ONLY_IN_SELECTED_APPS = booleanPreferencesKey("only_in_selected_apps")

        fun onlyInSelectedApps(prefs: Preferences): Boolean = prefs[ONLY_IN_SELECTED_APPS] ?: true
    }
}
