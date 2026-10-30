package com.kwan.motioncontrol

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.kwan.motioncontrol.accessibility.ActionDispatcher
import com.kwan.motioncontrol.accessibility.AirScrollAccessibilityService
import com.kwan.motioncontrol.accessibility.ForegroundAppTracker
import com.kwan.motioncontrol.data.CustomPoseRepository
import com.kwan.motioncontrol.data.PoseSpace
import com.kwan.motioncontrol.data.ProfileRepository
import com.kwan.motioncontrol.data.ProfileResolver
import com.kwan.motioncontrol.data.SettingsRepository
import com.kwan.motioncontrol.data.ThemeMode
import com.kwan.motioncontrol.system.InstalledApps
import com.kwan.motioncontrol.system.TileStatus
import com.kwan.motioncontrol.ui.profiles.AppPickerScreen
import com.kwan.motioncontrol.motion.MotionStateStore
import com.kwan.motioncontrol.ui.home.HomeScreen
import com.kwan.motioncontrol.ui.onboarding.OnboardingScreen
import com.kwan.motioncontrol.ui.poses.CustomPosesScreen
import com.kwan.motioncontrol.ui.poses.RecordPoseScreen
import com.kwan.motioncontrol.ui.profiles.ProfileEditorScreen
import com.kwan.motioncontrol.ui.profiles.ProfilesScreen
import com.kwan.motioncontrol.ui.testmode.TestModeScreen
import com.kwan.motioncontrol.ui.theme.MotionControlTheme
import com.kwan.motioncontrol.ui.theme.isDark
import com.kwan.motioncontrol.ui.troubleshooting.TroubleshootingScreen
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val dispatcher: ActionDispatcher by inject()
    private val motionStore: MotionStateStore by inject()
    private val profileRepository: ProfileRepository by inject()
    private val customPoseRepository: CustomPoseRepository by inject()
    private val settings: SettingsRepository by inject()
    private val installedApps: InstalledApps by inject()

    /** A profile to open in the editor, asked for by the bubble's quick panel. */
    private var editRequest by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Only for a fresh launch: after a recreation the back stack is already restored.
        if (savedInstanceState == null) editRequest = intent.getStringExtra(EXTRA_EDIT_PROFILE)
        setContent {
            val themeMode by settings.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            val dark = themeMode.isDark()
            val dynamicColor by settings.dynamicColor.collectAsStateWithLifecycle(initialValue = true)
            // Status and navigation bar icons follow the app's theme, not only the phone's.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
                )
                onDispose {}
            }
            MotionControlTheme(darkTheme = dark, dynamicColor = dynamicColor) {
                // Null while DataStore loads, so neither screen flashes.
                val onboardingDone by settings.onboardingDone.collectAsStateWithLifecycle(initialValue = null)
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    when (onboardingDone) {
                        null -> Unit
                        false -> OnboardingScreen(
                            onFinish = { lifecycleScope.launch { settings.setOnboardingDone() } },
                            modifier = Modifier.padding(innerPadding),
                        )
                        true -> AppContent(
                            repository = profileRepository,
                            poseRepository = customPoseRepository,
                            settings = settings,
                            installedApps = installedApps,
                            store = motionStore,
                            dispatcher = dispatcher,
                            editRequest = editRequest,
                            onEditRequestHandled = { editRequest = null },
                            themeMode = themeMode,
                            dynamicColor = dynamicColor,
                            modifier = Modifier.padding(innerPadding),
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_EDIT_PROFILE)?.let { editRequest = it }
    }

    companion object {
        // The navigation bar scrims enableEdgeToEdge() uses by default.
        private val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        private val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
        private const val EXTRA_EDIT_PROFILE = "com.kwan.motioncontrol.extra.EDIT_PROFILE"

        /** Opens the profile editor for [profileId] from outside the app, e.g. the quick panel. */
        fun editProfileIntent(context: Context, profileId: String): Intent =
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_EDIT_PROFILE, profileId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}

private sealed interface Screen {
    data object Home : Screen
    data object Profiles : Screen
    data class ProfileEditor(val profileId: String) : Screen
    data class AppPicker(val profileId: String) : Screen
    /** Tests [profileId], or the default profile when null. */
    data class TestMode(val profileId: String?) : Screen
    data object Troubleshooting : Screen
    data object CustomPoses : Screen
    /** Records a new pose, or re-records [poseId]. */
    data class RecordPose(val poseId: String?) : Screen
}

private fun Screen.encode(): String = when (this) {
    Screen.Home -> "home"
    Screen.Profiles -> "profiles"
    is Screen.ProfileEditor -> "editor:$profileId"
    is Screen.AppPicker -> "apps:$profileId"
    is Screen.TestMode -> "test:${profileId.orEmpty()}"
    Screen.Troubleshooting -> "troubleshooting"
    Screen.CustomPoses -> "poses"
    is Screen.RecordPose -> "record:${poseId.orEmpty()}"
}

private fun decodeScreen(s: String): Screen = when {
    s == "profiles" -> Screen.Profiles
    s == "troubleshooting" -> Screen.Troubleshooting
    s == "poses" -> Screen.CustomPoses
    s.startsWith("record:") -> Screen.RecordPose(s.removePrefix("record:").ifEmpty { null })
    s.startsWith("editor:") -> Screen.ProfileEditor(s.removePrefix("editor:"))
    s.startsWith("apps:") -> Screen.AppPicker(s.removePrefix("apps:"))
    s.startsWith("test:") -> Screen.TestMode(s.removePrefix("test:").ifEmpty { null })
    else -> Screen.Home
}

private val backStackSaver = Saver<List<Screen>, ArrayList<String>>(
    save = { stack -> ArrayList(stack.map { it.encode() }) },
    restore = { saved -> saved.map(::decodeScreen).ifEmpty { listOf(Screen.Home) } },
)

@Composable
private fun AppContent(
    repository: ProfileRepository,
    poseRepository: CustomPoseRepository,
    settings: SettingsRepository,
    installedApps: InstalledApps,
    store: MotionStateStore,
    dispatcher: ActionDispatcher,
    /** A profile to open in the editor, on top of Home; [onEditRequestHandled] clears it. */
    editRequest: String?,
    onEditRequestHandled: () -> Unit,
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    modifier: Modifier = Modifier,
) {
    var backStack by rememberSaveable(stateSaver = backStackSaver) { mutableStateOf(listOf<Screen>(Screen.Home)) }
    LaunchedEffect(editRequest) {
        val id = editRequest ?: return@LaunchedEffect
        backStack = listOf(Screen.Home, Screen.ProfileEditor(id))
        onEditRequestHandled()
    }
    fun navigate(screen: Screen) {
        backStack = backStack + screen
    }
    fun back() {
        backStack = backStack.dropLast(1)
    }
    BackHandler(enabled = backStack.size > 1, onBack = ::back)

    val scope = rememberCoroutineScope()
    val profiles by repository.profiles.collectAsStateWithLifecycle(initialValue = emptyList())
    val defaultProfile by repository.defaultProfile.collectAsStateWithLifecycle(initialValue = null)
    val routing by repository.routing.collectAsStateWithLifecycle(initialValue = null)
    val connected by AirScrollAccessibilityService.instance.collectAsStateWithLifecycle()
    val lastApp by ForegroundAppTracker.current.collectAsStateWithLifecycle()
    val motion by store.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val tileAdded by remember { TileStatus.added(context) }.collectAsStateWithLifecycle(initialValue = true)
    val bubbleEnabled by settings.bubbleEnabled.collectAsStateWithLifecycle(initialValue = false)
    val customPoses by poseRepository.poses.collectAsStateWithLifecycle(initialValue = emptyList())
    val poseSpace by settings.poseSpace.collectAsStateWithLifecycle(initialValue = PoseSpace.IMAGE)
    val debug by store.debug.collectAsStateWithLifecycle()
    // Same resolution the service uses, so Home is right even while motion control is off.
    val route = routing?.let { ProfileResolver.resolve(lastApp, connected != null, it) }

    when (val screen = backStack.last()) {
        Screen.Home -> HomeScreen(
            accessibilityEnabled = connected != null,
            route = route,
            appLabel = lastApp?.let(installedApps::label),
            motion = motion,
            debug = debug,
            profiles = profiles,
            defaultProfile = defaultProfile,
            useInAllApps = routing?.onlyInSelectedApps == false,
            onUseInAllAppsChange = { all -> scope.launch { settings.setOnlyInSelectedApps(!all) } },
            onSelectDefault = { id -> scope.launch { repository.setDefault(id) } },
            onEditProfile = { id -> navigate(Screen.ProfileEditor(id)) },
            onAddAppToProfile = { id, pkg -> scope.launch { repository.assignApps(id, setOf(pkg)) } },
            onOpenProfiles = { navigate(Screen.Profiles) },
            onOpenCustomPoses = { navigate(Screen.CustomPoses) },
            onOpenTestMode = { navigate(Screen.TestMode(null)) },
            onOpenTroubleshooting = { navigate(Screen.Troubleshooting) },
            tileAdded = tileAdded,
            themeMode = themeMode,
            onThemeModeChange = { mode -> scope.launch { settings.setThemeMode(mode) } },
            dynamicColor = dynamicColor,
            onDynamicColorChange = { enabled -> scope.launch { settings.setDynamicColor(enabled) } },
            bubbleEnabled = bubbleEnabled,
            onBubbleEnabledChange = { enabled -> scope.launch { settings.setBubbleEnabled(enabled) } },
            modifier = modifier,
        )
        Screen.Profiles -> ProfilesScreen(
            profiles = profiles,
            defaultProfileId = defaultProfile?.id,
            appLabel = installedApps::label,
            onBack = ::back,
            onEdit = { id -> navigate(Screen.ProfileEditor(id)) },
            onMakeDefault = { id -> scope.launch { repository.setDefault(id) } },
            onNew = {
                val source = defaultProfile ?: return@ProfilesScreen
                scope.launch {
                    repository.duplicate(source)?.let { navigate(Screen.ProfileEditor(it.id)) }
                }
            },
            onDelete = { id -> scope.launch { repository.delete(id) } },
            onOpenCustomPoses = { navigate(Screen.CustomPoses) },
            onResetToDefaults = { scope.launch { repository.resetToDefaults() } },
            modifier = modifier,
        )
        is Screen.ProfileEditor -> ProfileEditorScreen(
            profile = profiles.firstOrNull { it.id == screen.profileId },
            isDefault = defaultProfile?.id == screen.profileId,
            installedApps = installedApps,
            onBack = ::back,
            onChange = { updated -> scope.launch { repository.upsert(updated) } },
            onAddApps = { navigate(Screen.AppPicker(screen.profileId)) },
            onRemoveApp = { pkg -> scope.launch { repository.unassignApp(screen.profileId, pkg) } },
            onTest = { navigate(Screen.TestMode(screen.profileId)) },
            customPoses = customPoses,
            onOpenCustomPoses = { navigate(Screen.CustomPoses) },
            modifier = modifier,
        )
        is Screen.AppPicker -> AppPickerScreen(
            profile = profiles.firstOrNull { it.id == screen.profileId },
            profiles = profiles,
            installedApps = installedApps,
            onBack = ::back,
            onAssign = { pkg -> scope.launch { repository.assignApps(screen.profileId, setOf(pkg)) } },
            onUnassign = { pkg -> scope.launch { repository.unassignApp(screen.profileId, pkg) } },
            modifier = modifier,
        )
        is Screen.TestMode -> {
            val profile = profiles.firstOrNull { it.id == screen.profileId } ?: defaultProfile
            if (profile != null) {
                TestModeScreen(
                    profile = profile,
                    customPoses = customPoses,
                    poseSpace = poseSpace,
                    store = store,
                    accessibilityEnabled = connected != null,
                    onDispatch = dispatcher::dispatch,
                    onBack = ::back,
                    modifier = modifier,
                )
            }
        }
        Screen.Troubleshooting -> TroubleshootingScreen(
            accessibilityRunning = connected != null,
            onBack = ::back,
            modifier = modifier,
        )
        Screen.CustomPoses -> CustomPosesScreen(
            poses = customPoses,
            poseSpace = poseSpace,
            onSetPoseSpace = { space -> scope.launch { settings.setPoseSpace(space) } },
            onNew = { navigate(Screen.RecordPose(null)) },
            onRerecord = { id -> navigate(Screen.RecordPose(id)) },
            onRename = { id, name -> scope.launch { poseRepository.rename(id, name) } },
            onSetTrigger = { id, trigger -> scope.launch { poseRepository.setTrigger(id, trigger) } },
            onDelete = { id -> scope.launch { poseRepository.delete(id) } },
            onBack = ::back,
            modifier = modifier,
        )
        is Screen.RecordPose -> RecordPoseScreen(
            existing = customPoses.firstOrNull { it.id == screen.poseId },
            otherPoses = customPoses.filterNot { it.id == screen.poseId },
            poseSpace = poseSpace,
            store = store,
            onSave = { pose -> scope.launch { if (poseRepository.save(pose)) back() } },
            onBack = ::back,
            modifier = modifier,
        )
    }
}
