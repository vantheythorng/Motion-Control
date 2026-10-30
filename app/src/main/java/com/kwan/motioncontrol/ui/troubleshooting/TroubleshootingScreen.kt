package com.kwan.motioncontrol.ui.troubleshooting

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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.kwan.motioncontrol.system.AccessibilityStatus
import com.kwan.motioncontrol.system.BatteryOptimization
import com.kwan.motioncontrol.system.OemGuides
import com.kwan.motioncontrol.ui.common.AccessibilityDisclosureDialog
import com.kwan.motioncontrol.ui.common.RestrictedSettingsHint
import com.kwan.motioncontrol.ui.common.ScreenHeader
import com.kwan.motioncontrol.ui.common.rememberOnResume

/**
 * Everything motion control needs, re-checked each time the screen resumes, and how to stop this
 * phone's maker from closing it in the background (SPEC §7, §8).
 */
@Composable
fun TroubleshootingScreen(
    /** The accessibility service is bound and running (not just switched on in Settings). */
    accessibilityRunning: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader("Troubleshooting", onBack)
        Text("Checks", style = MaterialTheme.typography.titleMedium)
        PermissionCheck(
            title = "Camera",
            permission = Manifest.permission.CAMERA,
            problem = "Not allowed. Motion control can't see your hand.",
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PermissionCheck(
                title = "Notifications",
                permission = Manifest.permission.POST_NOTIFICATIONS,
                problem = "Not allowed. You won't see the Pause and Stop controls in the notification.",
            )
        }
        AccessibilityCheck(accessibilityRunning)
        val batteryExempt = rememberOnResume { BatteryOptimization.isExempt(context) }
        Check(
            title = "Battery optimization",
            ok = batteryExempt,
            okText = "Motion Control is not restricted.",
            problem = "Restricted. Android may stop motion control in the background.",
            action = "Allow" to { BatteryOptimization.requestExemption(context) },
        )
        HorizontalDivider()
        BrandGuide()
    }
}

/** One line per requirement: OK, or what's wrong with a button to fix it. */
@Composable
private fun Check(
    title: String,
    ok: Boolean,
    okText: String,
    problem: String,
    action: Pair<String, () -> Unit>?,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                if (ok) okText else problem,
                style = MaterialTheme.typography.bodySmall,
                color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
        if (!ok && action != null) TextButton(onClick = action.second) { Text(action.first) }
    }
}

@Composable
private fun PermissionCheck(title: String, permission: String, problem: String) {
    val context = LocalContext.current
    // Closing the permission dialog resumes this screen, which re-reads the permission.
    val granted = rememberOnResume { context.has(permission) }
    var refusedOnce by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        refusedOnce = !ok
    }
    Check(
        title = title,
        ok = granted,
        okText = "Allowed.",
        problem = problem,
        // After a refusal Android may not ask again, so the app's info page is the way to allow it.
        action = if (refusedOnce) "App info" to { OemGuides.openAppInfo(context) }
        else "Allow" to { launcher.launch(permission) },
    )
}

@Composable
private fun AccessibilityCheck(running: Boolean) {
    val context = LocalContext.current
    val switchedOn = rememberOnResume { AccessibilityStatus.isEnabled(context) }
    var showDisclosure by remember { mutableStateOf(false) }
    Check(
        title = "Accessibility service",
        ok = switchedOn && running,
        okText = "On and running.",
        problem = if (!switchedOn) "Off. Motion control can't swipe or tap for you."
        // Typical after the phone's battery manager killed the app: the setting stays on.
        else "Switched on but not running. Turn it off and on again in Accessibility settings.",
        action = if (!switchedOn) "Turn on" to { showDisclosure = true }
        else "Open" to { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
    )
    if (!switchedOn && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) RestrictedSettingsHint()
    if (showDisclosure) AccessibilityDisclosureDialog(onDismiss = { showDisclosure = false })
}

/** Steps for this phone's maker, with shortcuts to its settings. */
@Composable
private fun BrandGuide() {
    val context = LocalContext.current
    val guide = remember { OemGuides.forThisDevice() }
    Text("Keep motion control running on ${guide.brand}", style = MaterialTheme.typography.titleMedium)
    Text(
        "Many phones close apps in the background to save battery. Menu names differ between system versions.",
        style = MaterialTheme.typography.bodySmall,
    )
    guide.steps.forEachIndexed { i, step -> Text("${i + 1}. $step", style = MaterialTheme.typography.bodyMedium) }
    if (guide.autoStartScreens.isNotEmpty()) {
        OutlinedButton(
            onClick = { if (!OemGuides.openAutoStart(context, guide)) OemGuides.openAppInfo(context) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Open auto-start settings") }
    }
    OutlinedButton(onClick = { OemGuides.openAppInfo(context) }, modifier = Modifier.fillMaxWidth()) {
        Text("Open Motion Control's app info")
    }
}

private fun Context.has(permission: String) =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
