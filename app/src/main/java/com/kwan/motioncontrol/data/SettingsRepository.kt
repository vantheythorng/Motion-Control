package com.kwan.motioncontrol.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
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

    /**
     * Whether the floating bubble shows (default off; turned on from Home). Home's switch, the bubble's × target and
     * the notification's "Show bubble" action all write this one value.
     */
    val bubbleEnabled: Flow<Boolean> = dataStore.data.map { it[BUBBLE_ENABLED] ?: false }.distinctUntilChanged()

    suspend fun setBubbleEnabled(enabled: Boolean) {
        dataStore.edit { it[BUBBLE_ENABLED] = enabled }
    }

    /** Which landmarks custom poses are matched on (D6); switchable on the Custom poses screen. */
    val poseSpace: Flow<PoseSpace> = dataStore.data
        .map { prefs -> PoseSpace.entries.firstOrNull { it.name == prefs[POSE_SPACE] } ?: PoseSpace.IMAGE }
        .distinctUntilChanged()

    suspend fun setPoseSpace(space: PoseSpace) {
        dataStore.edit { it[POSE_SPACE] = space.name }
    }

    /** False until the user finishes (or skips through) the first-run onboarding. */
    val onboardingDone: Flow<Boolean> = dataStore.data.map { it[ONBOARDING_DONE] ?: false }.distinctUntilChanged()

    suspend fun setOnboardingDone() {
        dataStore.edit { it[ONBOARDING_DONE] = true }
    }

    /**
     * Turns the bubble off from its × target. Returns true the first time only, when the caller
     * shows how to turn it back on.
     */
    suspend fun hideBubble(): Boolean {
        var firstTime = false
        dataStore.edit {
            it[BUBBLE_ENABLED] = false
            firstTime = it[BUBBLE_HIDDEN_HINT_SHOWN] != true
            it[BUBBLE_HIDDEN_HINT_SHOWN] = true
        }
        return firstTime
    }

    companion object {
        private val ONLY_IN_SELECTED_APPS = booleanPreferencesKey("only_in_selected_apps")
        private val BUBBLE_ENABLED = booleanPreferencesKey("bubble_enabled")
        private val BUBBLE_HIDDEN_HINT_SHOWN = booleanPreferencesKey("bubble_hidden_hint_shown")
        private val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        private val POSE_SPACE = stringPreferencesKey("pose_space")

        fun onlyInSelectedApps(prefs: Preferences): Boolean = prefs[ONLY_IN_SELECTED_APPS] ?: true
    }
}
