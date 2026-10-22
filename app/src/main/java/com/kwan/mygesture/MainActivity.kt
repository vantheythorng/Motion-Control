package com.kwan.mygesture

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kwan.mygesture.accessibility.ActionDispatcher
import com.kwan.mygesture.accessibility.AirScrollAccessibilityService
import com.kwan.mygesture.accessibility.ForegroundAppTracker
import com.kwan.mygesture.data.ProfileRepository
import com.kwan.mygesture.motion.MotionStateStore
import com.kwan.mygesture.ui.home.HomeScreen
import com.kwan.mygesture.ui.profiles.ProfileEditorScreen
import com.kwan.mygesture.ui.profiles.ProfilesScreen
import com.kwan.mygesture.ui.testmode.TestModeScreen
import com.kwan.mygesture.ui.theme.MyGestureTheme
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val dispatcher: ActionDispatcher by inject()
    private val motionStore: MotionStateStore by inject()
    private val profileRepository: ProfileRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyGestureTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AppContent(
                        repository = profileRepository,
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
    /** Tests [profileId], or the active profile when null. */
    data class TestMode(val profileId: String?) : Screen
}

private fun Screen.encode(): String = when (this) {
    Screen.Home -> "home"
    Screen.Profiles -> "profiles"
    is Screen.ProfileEditor -> "editor:$profileId"
    is Screen.TestMode -> "test:${profileId.orEmpty()}"
}

private fun decodeScreen(s: String): Screen = when {
    s == "profiles" -> Screen.Profiles
    s.startsWith("editor:") -> Screen.ProfileEditor(s.removePrefix("editor:"))
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
    val activeProfile by repository.activeProfile.collectAsStateWithLifecycle(initialValue = null)
    val connected by AirScrollAccessibilityService.instance.collectAsStateWithLifecycle()
    val foregroundApp by ForegroundAppTracker.current.collectAsStateWithLifecycle()
    val motion by store.state.collectAsStateWithLifecycle()
    val debug by store.debug.collectAsStateWithLifecycle()

    when (val screen = backStack.last()) {
        Screen.Home -> HomeScreen(
            accessibilityEnabled = connected != null,
            foregroundApp = foregroundApp,
            motion = motion,
            debug = debug,
            profiles = profiles,
            activeProfile = activeProfile,
            onSelectProfile = { id -> scope.launch { repository.setActive(id) } },
            onEditProfile = { id -> navigate(Screen.ProfileEditor(id)) },
            onOpenProfiles = { navigate(Screen.Profiles) },
            onOpenTestMode = { navigate(Screen.TestMode(null)) },
            modifier = modifier,
        )
        Screen.Profiles -> ProfilesScreen(
            profiles = profiles,
            activeProfileId = activeProfile?.id,
            onBack = ::back,
            onEdit = { id -> navigate(Screen.ProfileEditor(id)) },
            onSetActive = { id -> scope.launch { repository.setActive(id) } },
            onNew = {
                val source = activeProfile ?: return@ProfilesScreen
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
            isActive = activeProfile?.id == screen.profileId,
            onBack = ::back,
            onChange = { updated -> scope.launch { repository.upsert(updated) } },
            onMakeActive = { scope.launch { repository.setActive(screen.profileId) } },
            onTest = { navigate(Screen.TestMode(screen.profileId)) },
            modifier = modifier,
        )
        is Screen.TestMode -> {
            val profile = profiles.firstOrNull { it.id == screen.profileId } ?: activeProfile
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
