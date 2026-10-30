package com.kwan.motioncontrol.system

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.core.net.toUri

/**
 * Android's battery optimization (Doze, app standby) may stop motion control in the background;
 * on many Oppo, Vivo and Xiaomi phones it does so within minutes. The user can exempt the app.
 */
object BatteryOptimization {

    fun isExempt(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    /**
     * Asks Android to exempt the app (the system shows its own Allow / Deny dialog), or opens the
     * battery optimization list where that dialog isn't available.
     */
    // Play only allows the direct request for some kinds of apps; this app is installed directly,
    // not through Play (SPEC §9).
    @SuppressLint("BatteryLife")
    fun requestExemption(context: Context) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:${context.packageName}".toUri())
            )
        } catch (_: ActivityNotFoundException) {
            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }
}
