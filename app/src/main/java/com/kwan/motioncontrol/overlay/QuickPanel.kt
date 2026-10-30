package com.kwan.motioncontrol.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.FrameLayout
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.kwan.motioncontrol.data.AppRoute
import com.kwan.motioncontrol.data.Profile
import com.kwan.motioncontrol.data.ProfileRepository
import com.kwan.motioncontrol.data.ThemeMode
import com.kwan.motioncontrol.data.cameraOffText
import com.kwan.motioncontrol.motion.MotionState
import com.kwan.motioncontrol.motion.MotionStateStore
import com.kwan.motioncontrol.ui.theme.MotionControlTheme
import com.kwan.motioncontrol.ui.theme.isDark
import kotlinx.coroutines.flow.Flow
import kotlin.math.roundToInt

/**
 * Quick controls opened from the bubble: pause/resume, stop, the profile in use with an edit
 * shortcut, and its sensitivity. An overlay window rather than an activity, so the app underneath
 * stays in front and keeps playing.
 */
class QuickPanel(
    private val context: Context,
    private val windowManager: WindowManager,
    private val store: MotionStateStore,
    private val profiles: ProfileRepository,
    /** The app's theme choice, so the panel matches the app. */
    private val themeMode: Flow<ThemeMode>,
    private val dynamicColor: Flow<Boolean>,
    private val onPause: (Boolean) -> Unit,
    private val onStop: () -> Unit,
    private val onSetSensitivity: (Profile, Float) -> Unit,
    private val onEditProfile: (String) -> Unit,
) {
    private var root: FrameLayout? = null
    private var owner: OverlayOwner? = null
    private var backCallback: Any? = null

    fun show() {
        if (root != null) return
        val owner = OverlayOwner().also { this.owner = it }
        val compose = ComposeView(context).apply {
            setContent {
                val mode by themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
                val wallpaperColors by dynamicColor.collectAsStateWithLifecycle(initialValue = true)
                MotionControlTheme(darkTheme = mode.isDark(), dynamicColor = wallpaperColors) {
                    val state by store.state.collectAsStateWithLifecycle()
                    val list by profiles.profiles.collectAsStateWithLifecycle(initialValue = emptyList())
                    val profileId = (state.route as? AppRoute.Use)?.profile?.id
                    // Stopped elsewhere (notification, tile): nothing left to control here.
                    LaunchedEffect(state.running) { if (!state.running) dismiss() }
                    QuickPanelContent(
                        state = state,
                        profile = list.firstOrNull { it.id == profileId },
                        onDismiss = ::dismiss,
                        onPause = onPause,
                        onStop = { onStop(); dismiss() },
                        onSetSensitivity = onSetSensitivity,
                        onEditProfile = { id -> onEditProfile(id); dismiss() },
                    )
                }
            }
        }
        val root = BackKeyFrame(context, ::dismiss).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            addView(compose)
        }
        this.root = root
        windowManager.addView(
            root,
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                // Focusable, so Back closes the panel instead of reaching the app underneath.
                WindowManager.LayoutParams.FLAG_DIM_BEHIND,
                PixelFormat.TRANSLUCENT,
            ).apply { dimAmount = 0.3f },
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // From Android 13 (and always for apps targeting 16) Back arrives as a callback, not a key.
            val callback = OnBackInvokedCallback { dismiss() }
            root.findOnBackInvokedDispatcher()
                ?.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback)
            backCallback = callback
        }
    }

    fun dismiss() {
        val root = root ?: return
        this.root = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            (backCallback as? OnBackInvokedCallback)?.let {
                root.findOnBackInvokedDispatcher()?.unregisterOnBackInvokedCallback(it)
            }
        }
        backCallback = null
        windowManager.removeView(root)
        owner?.destroy()
        owner = null
    }

    /** Closes the panel on the Back key, for Android 10–12. */
    @SuppressLint("ViewConstructor")
    private class BackKeyFrame(context: Context, private val onBack: () -> Unit) : FrameLayout(context) {
        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode != KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(event)
            if (event.action == KeyEvent.ACTION_UP) onBack()
            return true
        }
    }

    /** Compose needs a lifecycle and saved state; outside an activity this window provides its own. */
    private class OverlayOwner : SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val savedState = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

        init {
            savedState.performRestore(null)
            registry.currentState = Lifecycle.State.RESUMED
        }

        fun destroy() {
            registry.currentState = Lifecycle.State.DESTROYED
        }
    }
}

@Composable
private fun QuickPanelContent(
    state: MotionState,
    profile: Profile?,
    onDismiss: () -> Unit,
    onPause: (Boolean) -> Unit,
    onStop: () -> Unit,
    onSetSensitivity: (Profile, Float) -> Unit,
    onEditProfile: (String) -> Unit,
) {
    // Tapping outside the card closes the panel.
    Box(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = null, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp,
            modifier = Modifier
                .padding(16.dp)
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                // Taps on the card itself must not close it.
                .clickable(interactionSource = null, indication = null) {},
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Motion control", style = MaterialTheme.typography.titleLarge)
                Text(
                    statusText(state),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onPause(!state.paused) }, modifier = Modifier.weight(1f)) {
                        Text(if (state.paused) "Resume" else "Pause")
                    }
                    OutlinedButton(onClick = onStop, modifier = Modifier.weight(1f)) { Text("Stop") }
                }
                if (profile != null) SensitivityRow(profile, onSetSensitivity, onEditProfile)
            }
        }
    }
}

/** The profile in use with an edit shortcut, and its sensitivity (saved when the slider is released). */
@Composable
private fun SensitivityRow(
    profile: Profile,
    onSetSensitivity: (Profile, Float) -> Unit,
    onEditProfile: (String) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Profile: ${profile.name}",
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { onEditProfile(profile.id) }) { Text("Edit") }
    }
    var sensitivity by remember(profile.id, profile.sensitivity) { mutableFloatStateOf(profile.sensitivity) }
    Text("Sensitivity: ${(sensitivity * 100).roundToInt()} %", style = MaterialTheme.typography.bodyMedium)
    Slider(
        value = sensitivity,
        onValueChange = { sensitivity = (it * 20).roundToInt() / 20f }, // 0.05 steps, as in the editor
        onValueChangeFinished = { onSetSensitivity(profile, sensitivity) },
        valueRange = 0f..1f,
        steps = 19,
    )
}

private fun statusText(state: MotionState): String {
    val route = state.route
    return when {
        state.paused -> "Paused: gestures are ignored."
        route is AppRoute.Off -> cameraOffText(route.reason, state.appLabel)
        !state.cameraActive -> "Waiting: the camera is off."
        route is AppRoute.Use -> listOfNotNull("Active", route.profile.name, state.appLabel).joinToString(" · ")
        else -> "Starting…"
    }
}
