package com.kwan.motioncontrol.ui.home

import android.Manifest
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.graphics.drawable.Icon
import androidx.annotation.RequiresApi
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.kwan.motioncontrol.R
import com.kwan.motioncontrol.data.AppRoute
import com.kwan.motioncontrol.data.DefaultProfiles
import com.kwan.motioncontrol.data.OffReason
import com.kwan.motioncontrol.data.cameraOffText
import com.kwan.motioncontrol.data.Profile
import com.kwan.motioncontrol.data.ThemeMode
import com.kwan.motioncontrol.data.label
import com.kwan.motioncontrol.motion.DebugFrame
import com.kwan.motioncontrol.motion.MotionService
import com.kwan.motioncontrol.motion.MotionState
import com.kwan.motioncontrol.motion.ProfileTuning
import com.kwan.motioncontrol.system.BatteryOptimization
import com.kwan.motioncontrol.system.TileStatus
import com.kwan.motioncontrol.tile.MotionTileService
import com.kwan.motioncontrol.ui.common.AccessibilityDisclosureDialog
import com.kwan.motioncontrol.ui.common.ChoiceDropdown
import com.kwan.motioncontrol.ui.common.rememberOnResume
import com.kwan.motioncontrol.ui.theme.MotionControlTheme

@Composable
fun HomeScreen(
    accessibilityEnabled: Boolean,
    /** Which profile the last opened app uses, or why the camera is off there. */
    route: AppRoute?,
    appLabel: String?,
    motion: MotionState,
    debug: DebugFrame?,
    profiles: List<Profile>,
    defaultProfile: Profile?,
    useInAllApps: Boolean,
    onUseInAllAppsChange: (Boolean) -> Unit,
    onSelectDefault: (String) -> Unit,
    onEditProfile: (String) -> Unit,
    onAddAppToProfile: (profileId: String, packageName: String) -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenCustomPoses: () -> Unit,
    onOpenTestMode: () -> Unit,
    onOpenTroubleshooting: () -> Unit,
    /** Whether the Quick Settings tile is already in the user's tiles. */
    tileAdded: Boolean,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    dynamicColor: Boolean,
    onDynamicColorChange: (Boolean) -> Unit,
    bubbleEnabled: Boolean,
    onBubbleEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Motion Assessment", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            ThemeMenu(themeMode, onThemeModeChange, dynamicColor, onDynamicColorChange)
        }
        AccessibilitySection(accessibilityEnabled)
        HorizontalDivider()
        MotionSection(motion, debug, route, appLabel)
        BubbleSection(bubbleEnabled, accessibilityEnabled, onBubbleEnabledChange)
        HorizontalDivider()
        AppProfileSection(route, appLabel, profiles, onEditProfile, onAddAppToProfile)
        HorizontalDivider()
        OtherAppsSection(
            profiles, defaultProfile, useInAllApps, onUseInAllAppsChange, onSelectDefault, onEditProfile,
            onOpenProfiles,
        )
        // Poses are shared by all profiles, so they're managed from here, not inside a profile.
        OutlinedButton(onClick = onOpenCustomPoses, modifier = Modifier.fillMaxWidth()) {
            Text("Custom poses")
        }
        if (!tileAdded) {
            HorizontalDivider()
            QuickSettingsSection()
        }
        HorizontalDivider()
        OutlinedButton(onClick = onOpenTestMode, modifier = Modifier.fillMaxWidth()) {
            Text("Test mode (live hand view, no actions)")
        }
        KeepRunningSection(onOpenTroubleshooting)
    }
}

/**
 * The theme action on Home, showing the current choice: light, dark, or follow the phone; plus
 * wallpaper colors (dynamic color) on Android 12+.
 */
@Composable
private fun ThemeMenu(
    mode: ThemeMode,
    onChange: (ThemeMode) -> Unit,
    dynamicColor: Boolean,
    onDynamicColorChange: (Boolean) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(painterResource(mode.icon), contentDescription = "Theme: ${mode.label}")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Text(
                "Theme",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
            ThemeMode.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    leadingIcon = { Icon(painterResource(option.icon), contentDescription = null) },
                    trailingIcon = {
                        if (option == mode) Icon(painterResource(R.drawable.ic_check), contentDescription = "Selected")
                    },
                    onClick = {
                        expanded = false
                        onChange(option)
                    },
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Wallpaper colors") },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_palette), contentDescription = null) },
                    trailingIcon = {
                        if (dynamicColor) Icon(painterResource(R.drawable.ic_check), contentDescription = "On")
                    },
                    onClick = {
                        expanded = false
                        onDynamicColorChange(!dynamicColor)
                    },
                )
            }
        }
    }
}

private val ThemeMode.label: String
    get() = when (this) {
        ThemeMode.SYSTEM -> "System default"
        ThemeMode.LIGHT -> "Light"
        ThemeMode.DARK -> "Dark"
    }

private val ThemeMode.icon: Int
    get() = when (this) {
        ThemeMode.SYSTEM -> R.drawable.ic_brightness_auto
        ThemeMode.LIGHT -> R.drawable.ic_light_mode
        ThemeMode.DARK -> R.drawable.ic_dark_mode
    }

/** Warns when Android may stop motion control in the background, and links to Troubleshooting. */
@Composable
private fun KeepRunningSection(onOpenTroubleshooting: () -> Unit) {
    val context = LocalContext.current
    val batteryExempt = rememberOnResume { BatteryOptimization.isExempt(context) }
    if (!batteryExempt) {
        Text(
            "Battery optimization may stop motion control in the background. Fix it in Troubleshooting.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    OutlinedButton(onClick = onOpenTroubleshooting, modifier = Modifier.fillMaxWidth()) {
        Text("Troubleshooting")
    }
}

@Composable
private fun AccessibilitySection(enabled: Boolean) {
    var showDisclosure by remember { mutableStateOf(false) }

    Text(
        if (enabled) "Accessibility service: enabled" else "Accessibility service: off",
        color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
    )
    if (!enabled) {
        Button(onClick = { showDisclosure = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Enable accessibility service")
        }
    }

    if (showDisclosure) AccessibilityDisclosureDialog(onDismiss = { showDisclosure = false })
}

private val motionPermissions = buildList {
    add(Manifest.permission.CAMERA)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

private fun Context.hasCameraPermission() =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

/** Motion control only ever starts from the Start button here; nothing starts it automatically. */
@Composable
private fun MotionSection(motion: MotionState, debug: DebugFrame?, route: AppRoute?, appLabel: String?) {
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
            motion.paused -> "Paused: gestures are ignored. Tap Resume in the notification or the bubble."
            route is AppRoute.Off -> cameraOffText(route.reason, appLabel)
            !motion.cameraActive -> "Waiting: camera is off (screen off or locked, or Test mode open)"
            route is AppRoute.Use -> "Active with ${route.profile.name}. Keep your hand " +
                ProfileTuning.distanceLabel(route.profile) + " from the camera."
            else -> "Active"
        },
        color = if (motion.running && !motion.paused) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (motion.running && motion.cameraActive) {
        val s = debug?.snapshot
        Text(
            "Hand: ${if (s?.handPresent == true) "yes" else "no"} · " +
                "Last gesture: ${s?.lastGesture?.label ?: "-"}",
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

/** The floating bubble switch; the bubble's × target and the notification change the same setting. */
@Composable
private fun BubbleSection(enabled: Boolean, accessibilityEnabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Floating bubble", style = MaterialTheme.typography.titleMedium)
            Text(
                when {
                    !accessibilityEnabled -> "Appears once the accessibility service is on."
                    enabled -> "Tap it to start motion control or open quick controls. Drag it onto × to hide it."
                    else -> "Off."
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Switch(checked = enabled, onCheckedChange = onChange)
    }
}

/** Which profile the last opened app uses, with a shortcut to add an app that has none. */
@Composable
private fun AppProfileSection(
    route: AppRoute?,
    appLabel: String?,
    profiles: List<Profile>,
    onEditProfile: (String) -> Unit,
    onAddAppToProfile: (profileId: String, packageName: String) -> Unit,
) {
    Text("Profile for the last app", style = MaterialTheme.typography.titleMedium)
    val app = appLabel ?: "this app"
    when (route) {
        null -> Text("Loading…", style = MaterialTheme.typography.bodySmall)
        is AppRoute.Use -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (route.viaDefault) "$app uses the default profile, ${route.profile.name}."
                else "$app uses ${route.profile.name}.",
                Modifier.weight(1f),
            )
            TextButton(onClick = { onEditProfile(route.profile.id) }) { Text("Edit") }
        }
        is AppRoute.Off -> when (route.reason) {
            OffReason.ACCESSIBILITY_OFF ->
                Text("Turn on the accessibility service so motion control knows which app is open.")
            OffReason.NO_APP_YET -> Text("Open an app to see which profile it uses.")
            OffReason.NO_PROFILE_FOR_APP -> {
                Text("$app has no profile, so the camera is off there.")
                val pkg = route.app
                if (pkg != null && profiles.isNotEmpty()) {
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Add $app to a profile")
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            profiles.forEach { profile ->
                                DropdownMenuItem(
                                    text = { Text(profile.name) },
                                    onClick = {
                                        expanded = false
                                        onAddAppToProfile(profile.id, pkg)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Offers the Quick Settings tile (start/stop from the notification shade) until it's added. */
@Composable
private fun QuickSettingsSection() {
    val context = LocalContext.current
    Text("Quick Settings tile", style = MaterialTheme.typography.titleMedium)
    Text(
        "Turn motion control on and off from the notification shade, without opening this app.",
        style = MaterialTheme.typography.bodySmall,
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        OutlinedButton(onClick = { requestAddTile(context) }, modifier = Modifier.fillMaxWidth()) {
            Text("Add to Quick Settings")
        }
    } else {
        Text(
            "To add it: open Quick Settings, tap the edit (pencil) button and drag Motion Control in.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun requestAddTile(context: Context) {
    context.getSystemService(StatusBarManager::class.java).requestAddTileService(
        ComponentName(context, MotionTileService::class.java),
        context.getString(R.string.app_name),
        Icon.createWithResource(context, R.drawable.ic_motion),
        ContextCompat.getMainExecutor(context),
    ) { result ->
        // The system shows its own prompt; "already added" also means the button can go.
        if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
            result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED
        ) TileStatus.setAdded(context, true)
    }
}

/** What happens in apps without their own profile. */
@Composable
private fun OtherAppsSection(
    profiles: List<Profile>,
    defaultProfile: Profile?,
    useInAllApps: Boolean,
    onUseInAllAppsChange: (Boolean) -> Unit,
    onSelectDefault: (String) -> Unit,
    onEditProfile: (String) -> Unit,
    onOpenProfiles: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Use in all apps", style = MaterialTheme.typography.titleMedium)
            Text(
                if (useInAllApps) "Apps without their own profile use the default profile."
                else "Off: the camera turns off in apps without their own profile.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Switch(checked = useInAllApps, onCheckedChange = onUseInAllAppsChange)
    }
    if (useInAllApps && defaultProfile != null && profiles.isNotEmpty()) {
        Text("Default profile for other apps", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ChoiceDropdown(
                selected = defaultProfile,
                options = profiles,
                optionLabel = { it.name },
                onSelect = { onSelectDefault(it.id) },
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { onEditProfile(defaultProfile.id) }) { Text("Edit") }
        }
    }
    OutlinedButton(onClick = onOpenProfiles, modifier = Modifier.fillMaxWidth()) {
        Text("Manage profiles and apps")
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    MotionControlTheme {
        HomeScreen(
            accessibilityEnabled = true,
            route = AppRoute.Use(DefaultProfiles.shortVideo, "com.zhiliaoapp.musically", viaDefault = false),
            appLabel = "TikTok",
            motion = MotionState(running = true, cameraActive = true, delegate = "GPU"),
            debug = null,
            profiles = DefaultProfiles.all,
            defaultProfile = DefaultProfiles.global,
            useInAllApps = false,
            onUseInAllAppsChange = {},
            onSelectDefault = {},
            onEditProfile = {},
            onAddAppToProfile = { _, _ -> },
            onOpenProfiles = {},
            onOpenCustomPoses = {},
            onOpenTestMode = {},
            onOpenTroubleshooting = {},
            tileAdded = false,
            themeMode = ThemeMode.SYSTEM,
            onThemeModeChange = {},
            dynamicColor = true,
            onDynamicColorChange = {},
            bubbleEnabled = true,
            onBubbleEnabledChange = {},
        )
    }
}
