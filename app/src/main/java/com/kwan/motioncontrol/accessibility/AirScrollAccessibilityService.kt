package com.kwan.motioncontrol.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import com.kwan.motioncontrol.data.Direction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AirScrollAccessibilityService : AccessibilityService() {

    private var ignoredPackages: Set<String> = emptySet()

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Our own windows, the system UI and keyboards must not change the active app.
        val imePackages = getSystemService(InputMethodManager::class.java)
            .enabledInputMethodList.map { it.packageName }
        ignoredPackages = setOf(packageName, SYSTEM_UI_PACKAGE) + imePackages
        _instance.value = this
        Log.d(TAG, "Accessibility service connected")
    }

    // Only clear the reference if it still points at this instance, in case a new instance was
    // bound before this one is torn down.
    override fun onUnbind(intent: Intent?): Boolean {
        _instance.compareAndSet(this, null)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        _instance.compareAndSet(this, null)
        super.onDestroy()
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        if (e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = e.packageName?.toString() ?: return
        if (pkg in ignoredPackages) return
        if (pkg != ForegroundAppTracker.current.value) Log.d(TAG, "Foreground app: $pkg")
        ForegroundAppTracker.update(pkg)
    }

    override fun onInterrupt() {}

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
        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"

        private val _instance = MutableStateFlow<AirScrollAccessibilityService?>(null)
        /** The connected service, or null when the user hasn't enabled it. */
        val instance: StateFlow<AirScrollAccessibilityService?> = _instance.asStateFlow()
    }
}
