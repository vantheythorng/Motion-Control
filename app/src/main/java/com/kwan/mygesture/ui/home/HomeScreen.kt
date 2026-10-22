package com.kwan.mygesture.ui.home

import android.Manifest
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.kwan.mygesture.R
import com.kwan.mygesture.data.DefaultProfiles
import com.kwan.mygesture.data.Profile
import com.kwan.mygesture.data.label
import com.kwan.mygesture.motion.DebugFrame
import com.kwan.mygesture.motion.MotionService
import com.kwan.mygesture.motion.MotionState
import com.kwan.mygesture.motion.ProfileTuning
import com.kwan.mygesture.ui.common.ChoiceDropdown
import com.kwan.mygesture.ui.theme.MyGestureTheme

@Composable
fun HomeScreen(
    accessibilityEnabled: Boolean,
    foregroundApp: String?,
    motion: MotionState,
    debug: DebugFrame?,
    profiles: List<Profile>,
    activeProfile: Profile?,
    onSelectProfile: (String) -> Unit,
    onEditProfile: (String) -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenTestMode: () -> Unit,
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
        MotionSection(motion, debug, activeProfile)
        HorizontalDivider()
        ProfileSection(profiles, activeProfile, onSelectProfile, onEditProfile, onOpenProfiles)
        HorizontalDivider()
        OutlinedButton(onClick = onOpenTestMode, modifier = Modifier.fillMaxWidth()) {
            Text("Test mode (camera preview, no actions)")
        }
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

/** Motion control only ever starts from the Start button here; nothing starts it automatically. */
@Composable
private fun MotionSection(motion: MotionState, debug: DebugFrame?, activeProfile: Profile?) {
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

    Text("Motion control", style = MaterialTheme.typography.titleMedium)
    Text(
        when {
            !motion.running -> "Off"
            motion.paused -> "Paused: gestures are ignored. Tap Resume in the notification."
            !motion.cameraActive -> "Waiting: camera is off (screen off or locked, or Test mode open)"
            else -> "Active. Keep your hand " +
                (activeProfile?.let(ProfileTuning::distanceLabel) ?: "within range") + " from the camera."
        },
        color = if (motion.running && !motion.paused) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (motion.running && motion.cameraActive) {
        val s = debug?.snapshot
        Text(
            "Hand: ${if (s?.handPresent == true) "yes" else "no"} · " +
                "Last gesture: ${s?.lastMotion?.label ?: "-"}",
            style = MaterialTheme.typography.bodySmall,
        )
    }
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
private fun ProfileSection(
    profiles: List<Profile>,
    activeProfile: Profile?,
    onSelectProfile: (String) -> Unit,
    onEditProfile: (String) -> Unit,
    onOpenProfiles: () -> Unit,
) {
    Text("Active profile", style = MaterialTheme.typography.titleMedium)
    if (activeProfile == null || profiles.isEmpty()) {
        Text("Loading…", style = MaterialTheme.typography.bodySmall)
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        ChoiceDropdown(
            selected = activeProfile,
            options = profiles,
            optionLabel = { it.name },
            onSelect = { onSelectProfile(it.id) },
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { onEditProfile(activeProfile.id) }) { Text("Edit") }
    }
    Text(
        "Changes apply immediately, even while motion control runs.",
        style = MaterialTheme.typography.bodySmall,
    )
    OutlinedButton(onClick = onOpenProfiles, modifier = Modifier.fillMaxWidth()) {
        Text("Manage profiles")
    }
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
            profiles = DefaultProfiles.all,
            activeProfile = DefaultProfiles.shortVideo,
            onSelectProfile = {},
            onEditProfile = {},
            onOpenProfiles = {},
            onOpenTestMode = {},
        )
    }
}
