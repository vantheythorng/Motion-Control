package com.kwan.motioncontrol.ui.poses

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
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.kwan.motioncontrol.data.CustomPose
import com.kwan.motioncontrol.data.PoseSpace
import com.kwan.motioncontrol.data.PoseTrigger
import com.kwan.motioncontrol.ui.common.ChoiceDropdown
import com.kwan.motioncontrol.ui.common.ScreenHeader

val PoseTrigger.label: String
    get() = when (this) {
        PoseTrigger.HOLD -> "Hold still ~1 s"
        PoseTrigger.QUICK -> "Quick ~0.3 s"
    }

val PoseSpace.label: String
    get() = when (this) {
        PoseSpace.IMAGE -> "2D (image landmarks)"
        PoseSpace.WORLD -> "3D (world landmarks)"
    }

/** The user's own hand poses (SPEC §5.7), shared by all profiles; each profile binds them to actions. */
@Composable
fun CustomPosesScreen(
    poses: List<CustomPose>,
    poseSpace: PoseSpace,
    onSetPoseSpace: (PoseSpace) -> Unit,
    onNew: () -> Unit,
    onRerecord: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onSetTrigger: (String, PoseTrigger) -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var renaming by remember { mutableStateOf<CustomPose?>(null) }
    var deleting by remember { mutableStateOf<CustomPose?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ScreenHeader("Custom poses", onBack)
        Text(
            "Record your own hand shapes, then choose what each one does in a profile's editor. " +
                "Only hand landmark positions are stored, never images.",
            style = MaterialTheme.typography.bodySmall,
        )
        if (poses.isEmpty()) Text("No custom poses yet.")
        poses.forEach { pose ->
            Card {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(pose.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Fires", Modifier.weight(1f))
                        ChoiceDropdown(
                            selected = pose.trigger,
                            options = PoseTrigger.entries,
                            optionLabel = { it.label },
                            onSelect = { onSetTrigger(pose.id, it) },
                            modifier = Modifier.weight(1.5f),
                        )
                    }
                    Row {
                        TextButton(onClick = { renaming = pose }) { Text("Rename") }
                        TextButton(onClick = { onRerecord(pose.id) }) { Text("Re-record") }
                        TextButton(onClick = { deleting = pose }) { Text("Delete") }
                    }
                }
            }
        }
        val full = poses.size >= CustomPose.MAX_POSES
        Button(onClick = onNew, enabled = !full, modifier = Modifier.fillMaxWidth()) { Text("Record a new pose") }
        if (full) {
            Text("You have ${CustomPose.MAX_POSES} poses, the most allowed. Delete one to record another.", style = MaterialTheme.typography.bodySmall)
        }
        HorizontalDivider()
        // D6 is decided by comparing both on device; this switch goes once it's settled.
        Text("Matching (comparing)", style = MaterialTheme.typography.titleMedium)
        Text(
            "Which hand landmarks poses are matched on. 3D copes better with a turned hand; 2D is " +
                "steadier. Every pose is recorded in both, so switching needs no re-recording.",
            style = MaterialTheme.typography.bodySmall,
        )
        ChoiceDropdown(
            selected = poseSpace,
            options = PoseSpace.entries,
            optionLabel = { it.label },
            onSelect = onSetPoseSpace,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    renaming?.let { pose ->
        var name by remember(pose.id) { mutableStateOf(pose.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename pose") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Name") })
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        onRename(pose.id, name.trim())
                        renaming = null
                    },
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        )
    }
    deleting?.let { pose ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete \"${pose.name}\"?") },
            text = { Text("It's also removed from every profile that uses it.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(pose.id)
                    deleting = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}
