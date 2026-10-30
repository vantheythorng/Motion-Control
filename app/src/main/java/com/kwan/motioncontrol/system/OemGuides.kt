package com.kwan.motioncontrol.system

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.net.toUri

/** An activity in another app (e.g. a phone maker's auto-start manager), as package and class. */
data class SettingsScreen(val packageName: String, val className: String)

/**
 * What a user has to change on one brand's phones so Android doesn't stop motion control in the
 * background. Based on dontkillmyapp.com; menu names differ between system versions.
 */
data class OemGuide(
    val brand: String,
    val steps: List<String>,
    /** The brand's auto-start / background settings, tried in order; empty when there is none. */
    val autoStartScreens: List<SettingsScreen> = emptyList(),
)

object OemGuides {

    private const val TAG = "MotionControl"
    private const val LOCK_IN_RECENTS = "Open Recents, then lock Motion Control's card (long-press it, or use its menu) " +
        "so clearing Recents doesn't close it."

    val xiaomi = OemGuide(
        brand = "Xiaomi, Redmi or POCO",
        steps = listOf(
            "Settings → Apps → Manage apps → Motion Control → Autostart: on.",
            "On the same page, Battery saver → No restrictions.",
            LOCK_IN_RECENTS,
        ),
        autoStartScreens = listOf(
            SettingsScreen("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        ),
    )

    val colorOs = OemGuide(
        brand = "Oppo, Realme or OnePlus",
        steps = listOf(
            "Settings → Apps → App management → Motion Control → Battery usage: allow background activity " +
                "and auto launch.",
            LOCK_IN_RECENTS,
        ),
        autoStartScreens = listOf(
            SettingsScreen("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            SettingsScreen("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
            SettingsScreen("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
        ),
    )

    val vivo = OemGuide(
        brand = "Vivo or iQOO",
        steps = listOf(
            "Settings → Battery → Background power consumption management → Motion Control: Allow.",
            "i Manager → App manager → Autostart manager → Motion Control: on.",
            LOCK_IN_RECENTS,
        ),
        autoStartScreens = listOf(
            SettingsScreen("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            SettingsScreen("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
            SettingsScreen("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
        ),
    )

    val samsung = OemGuide(
        brand = "Samsung",
        steps = listOf(
            "Settings → Apps → Motion Control → Battery: Unrestricted.",
            "Settings → Battery → Background usage limits → Never sleeping apps → add Motion Control.",
        ),
    )

    val other = OemGuide(
        brand = "this phone",
        steps = listOf("Settings → Apps → Motion Control → Battery: Unrestricted (or \"Don't optimize\")."),
    )

    /** The guide for a phone, from Build.MANUFACTURER and Build.BRAND (e.g. "Xiaomi" and "Redmi"). */
    fun forDevice(manufacturer: String, brand: String): OemGuide {
        val names = setOf(manufacturer.lowercase(), brand.lowercase())
        return when {
            names.any { it in setOf("xiaomi", "redmi", "poco") } -> xiaomi
            names.any { it in setOf("oppo", "realme", "oneplus") } -> colorOs
            names.any { it in setOf("vivo", "iqoo") } -> vivo
            "samsung" in names -> samsung
            else -> other
        }
    }

    fun forThisDevice(): OemGuide = forDevice(Build.MANUFACTURER, Build.BRAND)

    /**
     * Opens the first of [guide]'s auto-start screens that starts; these are private activities
     * that move between system versions. Returns false when none did, so the caller can fall back
     * to the app's info page.
     */
    fun openAutoStart(context: Context, guide: OemGuide): Boolean = guide.autoStartScreens.any { screen ->
        try {
            context.startActivity(
                Intent().setComponent(ComponentName(screen.packageName, screen.className))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        } catch (e: Exception) {
            // ActivityNotFoundException, or SecurityException where the activity isn't exported.
            Log.d(TAG, "Can't open ${screen.className}: ${e.javaClass.simpleName}")
            false
        }
    }

    fun openAppInfo(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
