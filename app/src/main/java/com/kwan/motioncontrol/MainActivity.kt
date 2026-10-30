package com.kwan.motioncontrol

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
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
import com.kwan.motioncontrol.accessibility.ActionDispatcher
import com.kwan.motioncontrol.accessibility.AirScrollAccessibilityService
import com.kwan.motioncontrol.accessibility.ForegroundAppTracker
import com.kwan.motioncontrol.data.ProfileRepository
import com.kwan.motioncontrol.data.ProfileResolver
import com.kwan.motioncontrol.data.SettingsRepository
import com.kwan.motioncontrol.system.InstalledApps
import com.kwan.motioncontrol.system.TileStatus
import com.kwan.motioncontrol.ui.profiles.AppPickerScreen
import com.kwan.motioncontrol.motion.MotionStateStore
import com.kwan.motioncontrol.ui.home.HomeScreen
import com.kwan.motioncontrol.ui.profiles.ProfileEditorScreen
import com.kwan.motioncontrol.ui.profiles.ProfilesScreen
import com.kwan.motioncontrol.ui.testmode.TestModeScreen
import com.kwan.motioncontrol.ui.theme.MotionControlTheme
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val dispatcher: ActionDispatcher by inject()
    private val motionStore: MotionStateStore by inject()
    private val profileRepository: ProfileRepository by inject()
    private val settings: SettingsRepository by inject()
    private val installedApps: InstalledApps by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MotionControlTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AppContent(
                        repository = profileRepository,
                        settings = settings,
                        installedApps = installedApps,
                        store = motionStore,
                        dispatcher = dispatcher,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }
}

private sealed interface Screen {
    data object Home : Screen
    data object Profiles : Screen
    data class ProfileEditor(val profileId: String) : Screen
    data class AppPicker(val profileId: String) : Screen
    /** Tests [profileId], or the default profile when null. */
    data class TestMode(val profileId: String?) : Screen
}

private fun Screen.encode(): String = when (this) {
    Screen.Home -> "home"
    Screen.Profiles -> "profiles"
    is Screen.ProfileEditor -> "editor:$profileId"
    is Screen.AppPicker -> "apps:$profileId"
    is Screen.TestMode -> "test:${profileId.orEmpty()}"
}

private fun decodeScreen(s: String): Screen = when {
    s == "profiles" -> Screen.Profiles
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
    settings: SettingsRepository,
    installedApps: InstalledApps,
    store: MotionStateStore,
    dispatcher: ActionDispatcher,
    modifier: Modifier = Modifier,
) {
    var backStack by rememberSaveable(stateSaver = backStackSaver) { mutableStateOf(listOf<Screen>(Screen.Home)) }
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
            onOpenTestMode = { navigate(Screen.TestMode(null)) },
            tileAdded = tileAdded,
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
                    store = store,
                    accessibilityEnabled = connected != null,
                    onDispatch = dispatcher::dispatch,
                    onBack = ::back,
                    modifier = modifier,
                )
            }
        }
    }
}
