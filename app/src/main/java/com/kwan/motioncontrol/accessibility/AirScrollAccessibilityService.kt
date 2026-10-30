package com.kwan.motioncontrol.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Path
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import com.kwan.motioncontrol.data.Direction
import com.kwan.motioncontrol.data.ProfileRepository
import com.kwan.motioncontrol.data.SettingsRepository
import com.kwan.motioncontrol.motion.MotionStateStore
import com.kwan.motioncontrol.overlay.BubbleOverlay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.android.ext.android.inject

class AirScrollAccessibilityService : AccessibilityService() {

    private val store: MotionStateStore by inject()
    private val settings: SettingsRepository by inject()
    private val profiles: ProfileRepository by inject()

    /** The floating bubble lives here (D1), so it needs no overlay permission. */
    private var bubble: BubbleOverlay? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        _instance.value = this
        bubble = BubbleOverlay(this, store, settings, profiles).also { it.start() }
        Log.d(TAG, "Accessibility service connected")
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        bubble?.onConfigurationChanged()
    }

    // Only clear the reference if it still points at this instance, in case a new instance was
    // bound before this one is torn down. Without the service the open app is unknown, so the
    // tracker is cleared too (a stale "TikTok" would otherwise keep the camera on).
    override fun onUnbind(intent: Intent?): Boolean {
        disconnect()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        disconnect()
        super.onDestroy()
    }

    private fun disconnect() {
        bubble?.stop()
        bubble = null
        if (_instance.compareAndSet(this, null)) ForegroundAppTracker.clear()
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        if (e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        // Pane appearing/disappearing and title changes come as the same event type but with a
        // content change type set; only 0 means another window came to the front. Seen on Pixel:
        // after returning to an app from Recents, the launcher's "Recent apps" pane closing
        // (PANE_DISAPPEARED) would otherwise make the launcher the open app and turn the camera off.
        if (e.contentChangeTypes != AccessibilityEvent.CONTENT_CHANGE_TYPE_UNDEFINED) return
        val pkg = e.packageName?.toString() ?: return
        if (isIgnored(pkg)) return
        if (!isAppScreen(pkg, e.className)) {
            Log.d(TAG, "Ignored window over the open app: $pkg ${e.className}")
            return
        }
        if (pkg != ForegroundAppTracker.current.value) Log.d(TAG, "Foreground app: $pkg")
        ForegroundAppTracker.update(pkg)
    }

    override fun onInterrupt() {}

    /**
     * Windows that must not change the open app: our own UI, the system UI, keyboards, and
     * dialogs drawn over another app. When such a dialog closes, the app underneath sends no new
     * event, so tracking it would leave the camera off inside e.g. TikTok.
     */
    private fun isIgnored(pkg: String): Boolean {
        if (pkg == packageName || pkg in OVERLAY_PACKAGES) return true
        // Re-read each time (window changes are infrequent) so a newly installed keyboard counts.
        return getSystemService(InputMethodManager::class.java)
            .enabledInputMethodList.any { it.packageName == pkg }
    }

    /**
     * Whether the window is one of [pkg]'s activities rather than something it draws over another
     * app. Seen on Xiaomi (HyperOS 3): opening TikTok shows the Video Toolbox handle
     * (com.miui.securitycenter's VtbAssistantView), which made Security the open app and turned
     * the camera off inside TikTok. Activities report their class name; overlays and dialogs don't.
     * Packages we can't see (no launcher icon, see <queries>) can't be checked and count as apps.
     */
    private fun isAppScreen(pkg: String, className: CharSequence?): Boolean {
        val pm = packageManager
        if (className != null) {
            try {
                pm.getActivityInfo(ComponentName(pkg, className.toString()), 0)
                return true
            } catch (_: PackageManager.NameNotFoundException) {
            }
        }
        return try {
            pm.getApplicationInfo(pkg, 0)
            false
        } catch (_: PackageManager.NameNotFoundException) {
            true
        }
    }

    fun swipe(direction: Direction, durationMs: Long = 250): Boolean {
        val (w, h) = screenSize()
        val path = Path().apply {
            when (direction) {
                Direction.UP -> { moveTo(w / 2, h * 0.75f); lineTo(w / 2, h * 0.25f) }
                Direction.DOWN -> { moveTo(w / 2, h * 0.25f); lineTo(w / 2, h * 0.75f) }
                Direction.LEFT -> { moveTo(w * 0.8f, h / 2); lineTo(w * 0.2f, h / 2) }
                Direction.RIGHT -> { moveTo(w * 0.2f, h / 2); lineTo(w * 0.8f, h / 2) }
            }
        }
        return dispatch(GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs)))
    }

    fun tapCenter(times: Int = 1): Boolean {
        val (w, h) = screenSize()
        val builder = GestureDescription.Builder()
        repeat(times) { i ->
            val p = Path().apply { moveTo(w / 2, h / 2) }
            builder.addStroke(GestureDescription.StrokeDescription(p, i * 120L, 40))
        }
        return dispatch(builder)
    }

    fun global(action: Int): Boolean = performGlobalAction(action)

    private fun dispatch(builder: GestureDescription.Builder): Boolean =
        dispatchGesture(builder.build(), null, null)

    /** Full physical screen size in pixels, recomputed per gesture so rotation is respected. */
    private fun screenSize(): Pair<Float, Float> {
        val wm = getSystemService(WindowManager::class.java)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            bounds.width().toFloat() to bounds.height().toFloat()
        } else {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(dm)
            dm.widthPixels.toFloat() to dm.heightPixels.toFloat()
        }
    }

    companion object {
        private const val TAG = "MotionControl"
        private val OVERLAY_PACKAGES = setOf(
            "com.android.systemui", // notification shade, Recents on some devices
            "android", // system dialogs
            "com.android.intentresolver", // share sheet
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller",
        )

        private val _instance = MutableStateFlow<AirScrollAccessibilityService?>(null)
        /** The connected service, or null when the user hasn't enabled it. */
        val instance: StateFlow<AirScrollAccessibilityService?> = _instance.asStateFlow()
    }
}
