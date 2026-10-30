package com.kwan.motioncontrol.overlay

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.edit
import com.kwan.motioncontrol.MainActivity
import com.kwan.motioncontrol.R
import com.kwan.motioncontrol.data.ProfileRepository
import com.kwan.motioncontrol.data.SettingsRepository
import com.kwan.motioncontrol.motion.ArmCameraActivity
import com.kwan.motioncontrol.motion.MotionService
import com.kwan.motioncontrol.motion.MotionStateStore
import com.kwan.motioncontrol.system.ScreenState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * The floating bubble (SPEC §5.5, D1): an accessibility overlay owned by
 * AirScrollAccessibilityService, so it needs no overlay permission and is there whenever the
 * service is on, even while motion control is off. Its color shows the state ([BubbleLook]).
 * Tap: start motion control (through [ArmCameraActivity]) or open the [QuickPanel]. Drag: snaps
 * to the nearer edge, or onto the × target to turn the bubble off.
 */
class BubbleOverlay(
    private val context: Context,
    private val store: MotionStateStore,
    private val settings: SettingsRepository,
    private val profiles: ProfileRepository,
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val size = dp(56)
    private val margin = dp(8)
    private val targetSize = dp(72)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private var bubble: ImageView? = null
    private var params: WindowManager.LayoutParams? = null
    private var target: TextView? = null
    private var snap: ValueAnimator? = null
    private var position = loadPosition()
    private var look = BubbleLook.OFF

    private val panel = QuickPanel(
        context = context,
        windowManager = windowManager,
        store = store,
        profiles = profiles,
        onPause = { pause -> service { if (pause) MotionService.pause(context) else MotionService.resume(context) } },
        onStop = { service { MotionService.stop(context) } },
        onSetSensitivity = { profile, sensitivity -> scope.launch { profiles.upsert(profile.copy(sensitivity = sensitivity)) } },
        onEditProfile = { id -> context.startActivity(MainActivity.editProfileIntent(context, id)) },
    )

    fun start() {
        // Hidden on the lock screen: motion control can't run there, and the bubble would show
        // to anyone holding the phone.
        scope.launch {
            combine(settings.bubbleEnabled, ScreenState.usable(context)) { enabled, usable -> enabled && usable }
                .distinctUntilChanged()
                .collect { visible -> if (visible) show() else hide() }
        }
        val handInRange = store.debug
            .map { frame -> frame?.snapshot?.let { it.handPresent && it.inRange } == true }
            .distinctUntilChanged()
        val flashing = MutableStateFlow(false)
        scope.launch {
            store.fired.collectLatest {
                flashing.value = true
                delay(FLASH_MS)
                flashing.value = false
            }
        }
        scope.launch {
            combine(store.state, handInRange, flashing) { state, hand, flash -> BubbleLook.from(state, hand, flash) }
                .distinctUntilChanged()
                .collect {
                    look = it
                    applyLook()
                }
        }
    }

    fun stop() {
        scope.cancel()
        hide()
    }

    /** After a rotation, puts the bubble back at the same side and relative height. */
    fun onConfigurationChanged() {
        val view = bubble ?: return
        val lp = params ?: return
        snap?.cancel()
        hideTarget()
        val (x, y) = BubbleGeometry.place(position, area(), size, margin)
        lp.x = x
        lp.y = y
        windowManager.updateViewLayout(view, lp)
    }

    @SuppressLint("ClickableViewAccessibility") // Taps go through performClick() in DragListener.
    private fun show() {
        if (bubble != null) return
        val view = ImageView(context).apply {
            setImageResource(R.drawable.ic_motion)
            scaleType = ImageView.ScaleType.FIT_CENTER
            val pad = dp(14)
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL }
            setOnClickListener { onTap() }
            setOnTouchListener(DragListener())
        }
        val (x, y) = BubbleGeometry.place(position, area(), size, margin)
        val lp = overlayParams(size, size, x, y, touchable = true)
        windowManager.addView(view, lp)
        bubble = view
        params = lp
        applyLook()
    }

    private fun hide() {
        snap?.cancel()
        snap = null
        hideTarget()
        panel.dismiss()
        bubble?.let(windowManager::removeView)
        bubble = null
        params = null
    }

    private fun onTap() {
        if (store.state.value.running) {
            panel.show()
        } else {
            // A camera foreground service may only start while one of our activities is visible.
            context.startActivity(Intent(context, ArmCameraActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun applyLook() {
        val view = bubble ?: return
        (view.background as GradientDrawable).setColor(
            when (look) {
                BubbleLook.OFF -> 0xFF757575.toInt()
                BubbleLook.WATCHING -> 0xFF1E88E5.toInt()
                BubbleLook.HAND -> 0xFF43A047.toInt()
                BubbleLook.FIRED -> 0xFFFFB300.toInt()
            }
        )
        view.alpha = if (look == BubbleLook.OFF) 0.75f else 1f
        view.contentDescription = when (look) {
            BubbleLook.OFF -> if (store.state.value.running) "Motion control paused or camera off. Open quick controls"
            else "Motion control off. Start motion control"
            else -> "Motion control on. Open quick controls"
        }
    }

    /** Drags the bubble; on release it snaps to an edge, or turns the bubble off over the × target. */
    private inner class DragListener : View.OnTouchListener {
        private var downX = 0f
        private var downY = 0f
        private var startX = 0
        private var startY = 0
        private var dragging = false

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            val lp = params ?: return false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    snap?.cancel()
                    downX = event.rawX
                    downY = event.rawY
                    startX = lp.x
                    startY = lp.y
                    dragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!dragging && hypot(dx, dy) >= touchSlop) {
                        dragging = true
                        showTarget()
                    }
                    if (dragging) {
                        lp.x = startX + dx.roundToInt()
                        lp.y = startY + dy.roundToInt()
                        windowManager.updateViewLayout(view, lp)
                        highlightTarget(isOverTarget(lp))
                    }
                }
                MotionEvent.ACTION_UP -> if (!dragging) view.performClick() else drop(view, lp)
                MotionEvent.ACTION_CANCEL -> if (dragging) {
                    hideTarget()
                    settle(view, lp)
                }
            }
            return true
        }
    }

    private fun drop(view: View, lp: WindowManager.LayoutParams) {
        val overTarget = isOverTarget(lp)
        hideTarget()
        if (!overTarget) return settle(view, lp)
        // The settings flow then removes the bubble; it comes back from Home or the notification.
        hide()
        scope.launch {
            if (settings.hideBubble()) {
                Toast.makeText(context, "Bubble hidden. Turn it back on in Motion Control.", Toast.LENGTH_LONG).show()
            }
        }
    }

    /** Animates the bubble to the nearer edge and remembers where it rests. */
    private fun settle(view: View, lp: WindowManager.LayoutParams) {
        val area = area()
        position = BubbleGeometry.release(lp.x, lp.y, area, size)
        savePosition(position)
        val (toX, toY) = BubbleGeometry.place(position, area, size, margin)
        val fromX = lp.x
        val fromY = lp.y
        snap = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = SNAP_MS
            addUpdateListener { animator ->
                if (!view.isAttachedToWindow) return@addUpdateListener
                val f = animator.animatedFraction
                lp.x = (fromX + (toX - fromX) * f).roundToInt()
                lp.y = (fromY + (toY - fromY) * f).roundToInt()
                windowManager.updateViewLayout(view, lp)
            }
            start()
        }
    }

    private fun targetCenter(): Pair<Float, Float> {
        val area = area()
        return (area.left + area.right) / 2f to area.bottom - dp(32) - targetSize / 2f
    }

    private fun isOverTarget(lp: WindowManager.LayoutParams): Boolean {
        val (tx, ty) = targetCenter()
        return BubbleGeometry.overTarget(lp.x + size / 2f, lp.y + size / 2f, tx, ty, radius = targetSize.toFloat())
    }

    private fun showTarget() {
        if (target != null) return
        val view = TextView(context).apply {
            text = "×"
            setTextColor(0xFFFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 32f)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(TARGET_COLOR)
            }
            contentDescription = "Drop here to hide the bubble"
        }
        val (cx, cy) = targetCenter()
        windowManager.addView(
            view,
            overlayParams(targetSize, targetSize, (cx - targetSize / 2f).roundToInt(), (cy - targetSize / 2f).roundToInt(), touchable = false),
        )
        target = view
    }

    private fun highlightTarget(over: Boolean) {
        val view = target ?: return
        (view.background as GradientDrawable).setColor(if (over) TARGET_ACTIVE_COLOR else TARGET_COLOR)
        val scale = if (over) 1.2f else 1f
        view.scaleX = scale
        view.scaleY = scale
    }

    private fun hideTarget() {
        target?.let(windowManager::removeView)
        target = null
    }

    /** Overlay window placed in screen coordinates (top-left), the same space as MotionEvent.rawX/Y. */
    private fun overlayParams(width: Int, height: Int, x: Int, y: Int, touchable: Boolean) =
        WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                (if (touchable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE),
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                fitInsetsTypes = 0
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

    /** The screen minus system bars and cutouts, recomputed each time so rotation is respected. */
    private fun area(): Area {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            val bounds = metrics.bounds
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
            )
            return Area(
                bounds.left + insets.left,
                bounds.top + insets.top,
                bounds.right - insets.right,
                bounds.bottom - insets.bottom,
            )
        }
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(dm)
        return Area(0, dp(24), dm.widthPixels, dm.heightPixels - dp(48))
    }

    private fun loadPosition() = BubblePosition(
        right = prefs.getBoolean(KEY_RIGHT, BubblePosition.DEFAULT.right),
        yFraction = prefs.getFloat(KEY_Y, BubblePosition.DEFAULT.yFraction),
    )

    private fun savePosition(position: BubblePosition) = prefs.edit {
        putBoolean(KEY_RIGHT, position.right)
        putFloat(KEY_Y, position.yFraction)
    }

    /** Sends a command to the running MotionService; it may have stopped meanwhile. */
    private inline fun service(block: () -> Unit) {
        try {
            block()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Couldn't reach motion control from the bubble", e)
        }
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).roundToInt()

    private companion object {
        const val TAG = "MotionControl"
        const val PREFS = "bubble_position"
        const val KEY_RIGHT = "right"
        const val KEY_Y = "y"
        const val FLASH_MS = 300L
        const val SNAP_MS = 200L
        const val TARGET_COLOR = 0xAA000000.toInt()
        const val TARGET_ACTIVE_COLOR = 0xDDD32F2F.toInt()
    }
}
