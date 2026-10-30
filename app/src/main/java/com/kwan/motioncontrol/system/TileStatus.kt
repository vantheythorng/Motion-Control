package com.kwan.motioncontrol.system

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Whether our Quick Settings tile is in the user's tiles, so Home can hide "Add to Quick
 * Settings". Kept in its own SharedPreferences file that is excluded from backup (a restored
 * phone has no tile), and repaired whenever the tile starts listening (e.g. after "clear data").
 * Writes use apply(): the tile service may be destroyed right after onTileAdded.
 */
object TileStatus {
    private const val FILE = "tile_status"
    private const val KEY_ADDED = "added"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isAdded(context: Context): Boolean = prefs(context).getBoolean(KEY_ADDED, false)

    fun setAdded(context: Context, added: Boolean) {
        val p = prefs(context)
        if (p.getBoolean(KEY_ADDED, false) != added) p.edit { putBoolean(KEY_ADDED, added) }
    }

    fun added(context: Context): Flow<Boolean> = callbackFlow {
        val p = prefs(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == KEY_ADDED) trySend(prefs.getBoolean(KEY_ADDED, false))
        }
        p.registerOnSharedPreferenceChangeListener(listener)
        trySend(p.getBoolean(KEY_ADDED, false))
        awaitClose { p.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()
}
