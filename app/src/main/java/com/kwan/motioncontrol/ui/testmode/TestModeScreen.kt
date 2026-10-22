package com.kwan.motioncontrol.ui.testmode

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kwan.motioncontrol.data.DeviceAction
import com.kwan.motioncontrol.data.Direction
import com.kwan.motioncontrol.data.Profile
import com.kwan.motioncontrol.data.label
import com.kwan.motioncontrol.motion.DebugFrame
import com.kwan.motioncontrol.motion.GestureEngine
import com.kwan.motioncontrol.motion.MotionStateStore
import com.kwan.motioncontrol.motion.ProfileTuning
import com.kwan.motioncontrol.motion.camera.HandTracker
import com.kwan.motioncontrol.ui.common.DebugReadout
import com.kwan.motioncontrol.ui.common.LandmarkView
import com.kwan.motioncontrol.ui.common.ScreenHeader
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Camera preview with the landmark overlay and live engine values for [profile]. Recognized
 * gestures are only shown ("would fire"), never performed.
 *
 * While this screen is started it owns the front camera: [MotionStateStore.testModeActive] makes
 * the motion service release it, and it's handed back when the screen stops (including the user
 * pressing Home), not only when it's closed.
 */
@Composable
fun TestModeScreen(
    profile: Profile,
    store: MotionStateStore,
    accessibilityEnabled: Boolean,
    onDispatch: (DeviceAction) -> Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCamera by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCamera = granted }

    // Written from MediaPipe's result thread, read by Compose.
    val frames = remember { MutableStateFlow<DebugFrame?>(null) }
    val wouldFire = remember { MutableStateFlow<String?>(null) }
    val error = remember { MutableStateFlow<String?>(null) }
    val delegate = remember { MutableStateFlow<String?>(null) }

    val engine = remember {
        GestureEngine(
            bindings = profile.bindings,
            config = ProfileTuning.configFor(profile),
            onIgnored = { motion, reason -> wouldFire.value = "${motion.label}: ignored ($reason)" },
            onAction = { motion, action -> wouldFire.value = "${motion.label} → ${action.label}" },
        )
    }
    LaunchedEffect(profile) { engine.applyProfile(profile.bindings, ProfileTuning.configFor(profile)) }

    val tracker = remember {
        var lastAt = 0L
        var fps = 0f
        HandTracker(
            context = context.applicationContext,
            onFrame = { frame ->
                val dt = frame.timestampMs - lastAt
                if (lastAt > 0 && dt > 0) fps = fps * 0.8f + (1000f / dt) * 0.2f
                lastAt = frame.timestampMs
                frames.value = DebugFrame(frame.landmarks, engine.onFrame(frame), fps)
            },
            onError = { e -> error.value = e.message ?: e.javaClass.simpleName },
            onDelegateChosen = { delegate.value = it.name },
        )
    }
    DisposableEffect(Unit) { onDispose { tracker.close() } }

    LifecycleStartEffect(hasCamera) {
        store.setTestModeActive(true)
        if (hasCamera) tracker.start(lifecycleOwner)
        onStopOrDispose {
            tracker.stop()
            frames.value = null
            store.setTestModeActive(false)
        }
    }

    val debug by frames.collectAsStateWithLifecycle()
    val fired by wouldFire.collectAsStateWithLifecycle()
    val errorText by error.collectAsStateWithLifecycle()
    val delegateName by delegate.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ScreenHeader("Test mode", onBack)
        Text(
            "Testing \"${profile.name}\". Gestures are shown here but not performed. " +
                "Motion control's camera is paused while this screen is open.",
            style = MaterialTheme.typography.bodySmall,
        )
        if (!hasCamera) {
            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                Text("Allow camera")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Only the tracked hand is drawn, never the camera image.
            LandmarkView(debug?.landmarks, Modifier.width(180.dp))
            DebugReadout(debug, delegateName)
        }
        Text(
            "Would fire: ${fired ?: "-"}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        errorText?.let { Text("Error: $it", color = MaterialTheme.colorScheme.error) }
        HorizontalDivider()
        InjectionTestSection(accessibilityEnabled, onDispatch)
    }
}

private val testActions = listOf(
    DeviceAction.Swipe(Direction.UP),
    DeviceAction.Swipe(Direction.DOWN),
    DeviceAction.TapCenter,
    DeviceAction.DoubleTapCenter,
    DeviceAction.Global(AccessibilityService.GLOBAL_ACTION_BACK),
)

private const val TRIGGER_DELAY_SECONDS = 3

/** Phase 1 check that actions reach other apps: fires after a delay so you can switch apps. */
@Composable
private fun InjectionTestSection(serviceEnabled: Boolean, onDispatch: (DeviceAction) -> Boolean) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<Job?>(null) }

    Text("Injection test", style = MaterialTheme.typography.titleMedium)
    Text(
        "Tap an action, then switch to TikTok within $TRIGGER_DELAY_SECONDS s. " +
            "The action fires in whatever app is open.",
        style = MaterialTheme.typography.bodyMedium,
    )
    testActions.forEach { action ->
        OutlinedButton(
            enabled = serviceEnabled,
            onClick = {
                pending?.cancel()
                pending = scope.launch {
                    for (s in TRIGGER_DELAY_SECONDS downTo 1) {
                        status = "${action.label} in $s s…"
                        delay(1000)
                    }
                    status = if (onDispatch(action)) "${action.label}: dispatched"
                    else "${action.label}: failed (service off?)"
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(action.label) }
    }
    if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodyMedium)
}
