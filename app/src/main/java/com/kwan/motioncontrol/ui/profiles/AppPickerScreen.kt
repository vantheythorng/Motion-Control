package com.kwan.motioncontrol.ui.profiles

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.kwan.motioncontrol.data.Profile
import com.kwan.motioncontrol.system.InstalledApp
import com.kwan.motioncontrol.system.InstalledApps
import com.kwan.motioncontrol.ui.common.ScreenHeader

/**
 * Picks which apps use [profile]. Each change saves immediately. An app belongs to at most one
 * profile, so taking one from another profile asks first and then moves it.
 */
@Composable
fun AppPickerScreen(
    profile: Profile?,
    profiles: List<Profile>,
    installedApps: InstalledApps,
    onBack: () -> Unit,
    onAssign: (String) -> Unit,
    onUnassign: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val apps by produceState<List<InstalledApp>?>(initialValue = null) { value = installedApps.launchable() }
    var query by rememberSaveable { mutableStateOf("") }
    var moving by remember { mutableStateOf<Pair<InstalledApp, Profile>?>(null) }

    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ScreenHeader("Apps for ${profile?.name ?: "profile"}", onBack)
        if (profile == null) {
            Text("This profile no longer exists.")
            return@Column
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search apps") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        val list = apps
        if (list == null) {
            Text("Loading apps…", style = MaterialTheme.typography.bodySmall)
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(InstalledApps.search(list, query), key = { it.packageName }) { app ->
                val owner = profiles.firstOrNull { app.packageName in it.packages }
                val checked = owner?.id == profile.id
                fun toggle() {
                    when {
                        checked -> onUnassign(app.packageName)
                        owner != null -> moving = app to owner
                        else -> onAssign(app.packageName)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().clickable(onClick = ::toggle).padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AppIcon(app.packageName, installedApps)
                    Column(Modifier.weight(1f)) {
                        Text(app.label)
                        Text(
                            if (owner != null && !checked) "In ${owner.name}" else app.packageName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Checkbox(checked = checked, onCheckedChange = { toggle() })
                }
            }
        }
    }

    moving?.let { (app, owner) ->
        AlertDialog(
            onDismissRequest = { moving = null },
            title = { Text("Move ${app.label}?") },
            text = { Text("${app.label} uses ${owner.name} now. An app can use only one profile.") },
            confirmButton = {
                TextButton(onClick = {
                    moving = null
                    onAssign(app.packageName)
                }) { Text("Move here") }
            },
            dismissButton = { TextButton(onClick = { moving = null }) { Text("Cancel") } },
        )
    }
}

@Composable
fun AppIcon(packageName: String, installedApps: InstalledApps, modifier: Modifier = Modifier) {
    val sizePx = with(LocalDensity.current) { 36.dp.roundToPx() }
    val icon by produceState<ImageBitmap?>(initialValue = null, packageName) {
        value = installedApps.icon(packageName, sizePx)?.asImageBitmap()
    }
    Box(modifier.size(36.dp)) {
        icon?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
    }
}
