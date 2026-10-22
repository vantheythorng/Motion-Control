package com.kwan.mygesture.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kwan.mygesture.motion.DebugFrame
import java.util.Locale

/** Title row with a Back button, for screens below Home. */
@Composable
fun ScreenHeader(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
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
