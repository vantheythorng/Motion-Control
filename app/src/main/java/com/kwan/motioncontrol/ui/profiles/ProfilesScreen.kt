package com.kwan.motioncontrol.ui.profiles

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kwan.motioncontrol.data.Profile
import com.kwan.motioncontrol.ui.common.ScreenHeader

@Composable
fun ProfilesScreen(
    profiles: List<Profile>,
    defaultProfileId: String?,
    appLabel: (String) -> String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onMakeDefault: (String) -> Unit,
    onNew: () -> Unit,
    onOpenCustomPoses: () -> Unit,
    onDelete: (String) -> Unit,
    onResetToDefaults: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var deleting by remember { mutableStateOf<Profile?>(null) }
    var confirmReset by remember { mutableStateOf(false) }

    fun appsText(profile: Profile): String {
        val labels = profile.packages.map(appLabel).sortedBy { it.lowercase() }
        return when {
            labels.isEmpty() -> "No apps"
            labels.size <= 3 -> labels.joinToString(", ")
            else -> labels.take(3).joinToString(", ") + " +${labels.size - 3} more"
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ScreenHeader("Profiles", onBack)
        Text(
            "Each app uses the profile it's added to. The default profile is used for other apps " +
                "when \"Use in all apps\" is on.",
            style = MaterialTheme.typography.bodySmall,
        )
        profiles.forEach { profile ->
            val isDefault = profile.id == defaultProfileId
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
                        appsText(profile),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (isDefault) {
                        Text(
                            "Default for other apps",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (!isDefault) TextButton(onClick = { onMakeDefault(profile.id) }) { Text("Make default") }
                TextButton(
                    onClick = { deleting = profile },
                    enabled = profiles.size > 1,
                ) { Text("Delete") }
            }
            HorizontalDivider()
        }
        Button(onClick = onNew, modifier = Modifier.fillMaxWidth()) {
            Text("New profile (copy of the default, no apps)")
        }
        OutlinedButton(onClick = onOpenCustomPoses, modifier = Modifier.fillMaxWidth()) {
            Text("Custom poses")
        }
        OutlinedButton(onClick = { confirmReset = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Reset to defaults")
        }
    }

    deleting?.let { profile ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete \"${profile.name}\"?") },
            text = {
                Text(
                    if (profile.packages.isEmpty()) "This can't be undone."
                    else "Motion control will stop working in ${appsText(profile)} unless you add " +
                        "them to another profile. This can't be undone."
                )
            },
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
