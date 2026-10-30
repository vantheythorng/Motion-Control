package com.kwan.motioncontrol.system

import android.content.Context
import android.provider.Settings
import com.kwan.motioncontrol.accessibility.AirScrollAccessibilityService

/**
 * Whether the user has switched on our accessibility service. Read from the system setting rather
 * than from the bound service instance, which isn't connected yet right after a process start.
 */
object AccessibilityStatus {

    fun isEnabled(context: Context): Boolean = isEnabledIn(
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
        context.packageName,
        AirScrollAccessibilityService::class.java.name,
    )

    /**
     * [setting] is a ':'-separated list of "package/class" entries, where the class may be written
     * short (".accessibility.Service", relative to the package).
     */
    fun isEnabledIn(setting: String?, packageName: String, className: String): Boolean =
        setting.orEmpty().split(':').any { entry ->
            val pkg = entry.substringBefore('/', missingDelimiterValue = "")
            val cls = entry.substringAfter('/', missingDelimiterValue = "")
            pkg == packageName && (if (cls.startsWith('.')) pkg + cls else cls) == className
        }
}
