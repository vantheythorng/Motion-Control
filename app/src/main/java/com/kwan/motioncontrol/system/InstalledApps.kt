package com.kwan.motioncontrol.system

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

data class InstalledApp(val packageName: String, val label: String)

/**
 * Launchable apps and their labels. Visible to us through the MAIN/LAUNCHER and MAIN/HOME
 * <queries> in the manifest (no QUERY_ALL_PACKAGES).
 */
class InstalledApps(private val context: Context) {

    private val pm: PackageManager = context.packageManager
    private val labels = ConcurrentHashMap<String, String>()
    private val homePackages: Set<String> by lazy {
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            .map { it.activityInfo.packageName }
            .toSet()
    }

    /** Apps with a launcher icon, except this one, sorted by label. */
    suspend fun launchable(): List<InstalledApp> = withContext(Dispatchers.IO) {
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.activityInfo.packageName }
            .distinct() // an app can have several launcher activities
            .filter { it != context.packageName }
            .map { InstalledApp(it, label(it)) }
            .sortedBy { it.label.lowercase() }
    }

    /** The app's name; "Home screen" for the launcher; the package name if it isn't visible to us. */
    fun label(packageName: String): String = labels.getOrPut(packageName) {
        if (packageName in homePackages) return@getOrPut "Home screen"
        try {
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    fun isInstalled(packageName: String): Boolean = try {
        pm.getApplicationInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    suspend fun icon(packageName: String, sizePx: Int): Bitmap? = withContext(Dispatchers.IO) {
        try {
            pm.getApplicationIcon(packageName).toBitmap(sizePx, sizePx)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    companion object {
        /** Case-insensitive match on the app's name or package. */
        fun search(apps: List<InstalledApp>, query: String): List<InstalledApp> {
            val q = query.trim()
            if (q.isEmpty()) return apps
            return apps.filter { it.label.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true) }
        }
    }
}
