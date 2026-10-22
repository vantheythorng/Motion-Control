package com.kwan.mygesture.ui.profiles

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import com.kwan.mygesture.data.Profile
import com.kwan.mygesture.ui.common.ScreenHeader

@Composable
fun ProfilesScreen(
    profiles: List<Profile>,
    activeProfileId: String?,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onSetActive: (String) -> Unit,
    onNew: () -> Unit,
    onDelete: (String) -> Unit,
    onResetToDefaults: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var deleting by remember { mutableStateOf<Profile?>(null) }
    var confirmReset by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ScreenHeader("Profiles", onBack)
        profiles.forEach { profile ->
            val active = profile.id == activeProfileId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEdit(profile.id) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(profile.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (active) "Active · ${profile.bindings.size} gestures"
                        else "${profile.bindings.size} gestures",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!active) TextButton(onClick = { onSetActive(profile.id) }) { Text("Use") }
                TextButton(
                    onClick = { deleting = profile },
                    enabled = profiles.size > 1,
                ) { Text("Delete") }
            }
            HorizontalDivider()
        }
        Button(onClick = onNew, modifier = Modifier.fillMaxWidth()) {
            Text("New profile (copy of the active one)")
        }
        OutlinedButton(onClick = { confirmReset = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Reset to defaults")
        }
    }

    deleting?.let { profile ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete \"${profile.name}\"?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    onDelete(profile.id)
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset all profiles?") },
            text = { Text("Your profiles and changes are replaced by the default Short video and Global profiles.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    onResetToDefaults()
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}
