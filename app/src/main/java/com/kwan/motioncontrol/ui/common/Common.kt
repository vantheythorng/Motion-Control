package com.kwan.motioncontrol.ui.common

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.kwan.motioncontrol.R
import com.kwan.motioncontrol.system.OemGuides
import com.kwan.motioncontrol.motion.DebugFrame
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

/** Title row with a Back button, for screens below Home. */
@Composable
fun ScreenHeader(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) { Text("‹ Back") }
        Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A button showing the current choice that opens a menu of [options]. */
@Composable
fun <T> ChoiceDropdown(
    selected: T,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(optionLabel(selected), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

/**
 * The prominent disclosure shown before sending the user to accessibility settings: what the
 * service does and what it doesn't.
 */
@Composable
fun AccessibilityDisclosureDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Accessibility permission") },
        text = { Text(stringResource(R.string.accessibility_description)) },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }) { Text("Agree and open settings") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Not now") }
        },
    )
}

/**
 * Android 13+ greys out the accessibility switch for apps installed from a file or browser (not
 * from an app store) until the user allows "restricted settings" for the app.
 */
@Composable
fun RestrictedSettingsHint() {
    val context = LocalContext.current
    Text(
        "Switch greyed out? Open App info, tap ⋮ (top right), choose \"Allow restricted settings\", then try again.",
        style = MaterialTheme.typography.bodySmall,
    )
    OutlinedButton(onClick = { OemGuides.openAppInfo(context) }, modifier = Modifier.fillMaxWidth()) {
        Text("Open app info")
    }
}

/**
 * [read] now and again each time the screen resumes, e.g. after the user comes back from a
 * system settings page where they changed it. With [recheckFor], it keeps re-reading for that long
 * after resuming, for settings the system applies a moment later.
 */
@Composable
fun <T> rememberOnResume(recheckFor: Duration = Duration.ZERO, read: () -> T): T {
    var value by remember { mutableStateOf(read()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            value = read()
            repeat((recheckFor / RECHECK_EVERY).toInt()) {
                delay(RECHECK_EVERY)
                value = read()
            }
        }
    }
    return value
}

private val RECHECK_EVERY = 500.milliseconds

/**
 * How long to keep re-checking battery optimization after resuming. Xiaomi's battery page (which
 * answers the exemption request on HyperOS) closes before Android has added the app to the
 * whitelist; on a Redmi with HyperOS 3 it landed about 0.1 s after the app resumed.
 */
val BATTERY_RECHECK = 3.seconds

/** Live engine values for one camera frame, shared by Test mode. */
@Composable
fun DebugReadout(debug: DebugFrame?, delegate: String?, modifier: Modifier = Modifier) {
    val s = debug?.snapshot
    val style = MaterialTheme.typography.bodySmall
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("State: ${s?.state ?: "-"}", style = style)
        Text("Hand: ${if (s?.handPresent == true) "yes" else "no"}", style = style)
        Text("Pose: ${s?.pose ?: "-"} (${(s?.poseScore ?: 0f).signed()})", style = style)
        Text("Acting on: ${s?.activePose ?: "-"}", style = style)
        s?.nearestCustom?.let { near ->
            Text(
                "Custom pose: ${near.pose.name} ${(near.score * 100).roundToInt()} %" +
                    String.format(Locale.US, " (d %.2f / %.2f)", near.distance, near.threshold),
                style = style,
                color = if (near.withinThreshold) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        }
        Text("dx: ${(s?.dx ?: 0f).signed()}  dy: ${(s?.dy ?: 0f).signed()}", style = style)
        Text(
            "Distance: " + (s?.distanceCm?.let { String.format(Locale.US, "%.0f cm", it) } ?: "-") +
                if (s != null && !s.inRange) " (out of range)" else "",
            style = style,
            color = if (s != null && !s.inRange) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurface,
        )
        Text("FPS: ${String.format(Locale.US, "%.1f", debug?.fps ?: 0f)}", style = style)
        Text("Delegate: ${delegate ?: "-"}", style = style)
    }
}

private fun Float.signed() = String.format(Locale.US, "%+.2f", this)
