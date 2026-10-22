package com.kwan.mygesture.ui.home

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.kwan.mygesture.R
import com.kwan.mygesture.data.DeviceAction
import com.kwan.mygesture.data.Direction
import com.kwan.mygesture.motion.DebugFrame
import com.kwan.mygesture.motion.MotionService
import com.kwan.mygesture.motion.MotionState
import com.kwan.mygesture.ui.theme.MyGestureTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun HomeScreen(
    accessibilityEnabled: Boolean,
    foregroundApp: String?,
    motion: MotionState,
    debug: DebugFrame?,
    startRequested: Boolean,
    onStartRequestHandled: () -> Unit,
    onDispatch: (DeviceAction) -> Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Motion Assessment", style = MaterialTheme.typography.headlineSmall)
        AccessibilitySection(accessibilityEnabled, foregroundApp)
        HorizontalDivider()
        MotionSection(motion, startRequested, onStartRequestHandled)
        if (motion.running) DebugSection(debug, motion)
        HorizontalDivider()
        InjectionTestSection(accessibilityEnabled, onDispatch)
    }
}

@Composable
private fun AccessibilitySection(enabled: Boolean, foregroundApp: String?) {
    val context = LocalContext.current
    var showDisclosure by remember { mutableStateOf(false) }

    Text(
        if (enabled) "Accessibility service: enabled" else "Accessibility service: off",
        color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
    )
    Text("Foreground app: ${foregroundApp ?: "unknown"}")
    if (!enabled) {
        Button(onClick = { showDisclosure = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Enable accessibility service")
        }
    }

    if (showDisclosure) {
        AlertDialog(
            onDismissRequest = { showDisclosure = false },
            title = { Text("Accessibility permission") },
            text = { Text(stringResource(R.string.accessibility_description)) },
            confirmButton = {
                TextButton(onClick = {
                    showDisclosure = false
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }) { Text("Agree and open settings") }
            },
            dismissButton = {
                TextButton(onClick = { showDisclosure = false }) { Text("Not now") }
            },
        )
    }
}

private val motionPermissions = buildList {
    add(Manifest.permission.CAMERA)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

private fun Context.hasCameraPermission() =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

@Composable
private fun MotionSection(
    motion: MotionState,
    startRequested: Boolean,
    onStartRequestHandled: () -> Unit,
) {
    val context = LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }

    // Notifications are optional (the service still runs without them); the camera is not.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (context.hasCameraPermission()) {
            message = null
            MotionService.start(context)
        } else {
            message = "Camera permission is required for motion control."
        }
    }
    fun start() {
        if (motionPermissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        ) MotionService.start(context)
        else permissionLauncher.launch(motionPermissions)
    }

    // Tapping the "re-arm" notification opens the app with a start request.
    LaunchedEffect(startRequested) {
        if (startRequested) {
            onStartRequestHandled()
            start()
        }
    }

    Text("Motion control", style = MaterialTheme.typography.titleMedium)
    Text(
        when {
            motion.needsReArm && !motion.running -> "Stopped by the system. Tap Start to turn it back on."
            !motion.running -> "Off"
            motion.paused -> "Paused from the notification: gestures are ignored. Tap Resume there."
            !motion.cameraActive -> "Waiting: camera is off while the screen is off or locked"
            else -> "Active. Swipe up/down = next/previous, open palm (1 s) = play/pause video, " +
                "thumbs up = like, fist (1 s) = play/pause video. Keep your hand 30 cm–1 m from the camera."
        },
        color = if (motion.running && !motion.paused) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
    )
    motion.error?.let { Text("Error: $it", color = MaterialTheme.colorScheme.error) }
    message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    if (motion.running) {
        OutlinedButton(onClick = { MotionService.stop(context) }, modifier = Modifier.fillMaxWidth()) {
            Text("Stop motion control")
        }
    } else {
        Button(onClick = ::start, modifier = Modifier.fillMaxWidth()) { Text("Start motion control") }
        Text(
            "The camera indicator stays on while motion control runs. " +
                "Frames are processed on this device and never stored or uploaded.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun DebugSection(debug: DebugFrame?, motion: MotionState) {
    Text("Live debug", style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LandmarkView(debug?.landmarks, Modifier.width(120.dp))
        val s = debug?.snapshot
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val style = MaterialTheme.typography.bodySmall
            Text("State: ${s?.state ?: "-"}", style = style)
            Text("Hand: ${if (s?.handPresent == true) "yes" else "no"}", style = style)
            Text("Pose: ${s?.pose ?: "-"} (${(s?.poseScore ?: 0f).fmt()})", style = style)
            Text("dx: ${(s?.dx ?: 0f).fmt()}  dy: ${(s?.dy ?: 0f).fmt()}", style = style)
            Text(
                "Distance: " + (s?.distanceCm?.let { String.format(Locale.US, "%.0f cm", it) } ?: "-") +
                    if (s != null && !s.inRange) " (out of range)" else "",
                style = style,
                color = if (s != null && !s.inRange) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface,
            )
            Text("Last motion: ${s?.lastMotion ?: "-"}", style = style)
            Text("FPS: ${String.format(Locale.US, "%.1f", debug?.fps ?: 0f)}", style = style)
            Text("Delegate: ${motion.delegate ?: "-"}", style = style)
        }
    }
}

private fun Float.fmt() = String.format(Locale.US, "%+.2f", this)

private val testActions = listOf(
    "Next (swipe up)" to DeviceAction.Swipe(Direction.UP),
    "Previous (swipe down)" to DeviceAction.Swipe(Direction.DOWN),
    "Tap center (pause/play)" to DeviceAction.TapCenter,
    "Double-tap center (like)" to DeviceAction.DoubleTapCenter,
    "Back" to DeviceAction.Global(AccessibilityService.GLOBAL_ACTION_BACK),
)

private const val TRIGGER_DELAY_SECONDS = 3

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
    testActions.forEach { (label, action) ->
        OutlinedButton(
            enabled = serviceEnabled,
            onClick = {
                pending?.cancel()
                pending = scope.launch {
                    for (s in TRIGGER_DELAY_SECONDS downTo 1) {
                        status = "$label in $s s…"
                        delay(1000)
                    }
                    status = if (onDispatch(action)) "$label: dispatched"
                    else "$label: failed (service off?)"
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(label) }
    }
    if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodyMedium)
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    MyGestureTheme {
        HomeScreen(
            accessibilityEnabled = true,
            foregroundApp = "com.zhiliaoapp.musically",
            motion = MotionState(running = true, cameraActive = true, delegate = "GPU"),
            debug = null,
            startRequested = false,
            onStartRequestHandled = {},
            onDispatch = { true },
        )
    }
}
