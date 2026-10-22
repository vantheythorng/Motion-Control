package com.kwan.mygesture

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.kwan.mygesture.accessibility.ActionDispatcher
import com.kwan.mygesture.accessibility.AirScrollAccessibilityService
import com.kwan.mygesture.accessibility.ForegroundAppTracker
import com.kwan.mygesture.motion.MotionStateStore
import com.kwan.mygesture.ui.home.HomeScreen
import com.kwan.mygesture.ui.theme.MyGestureTheme
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val dispatcher: ActionDispatcher by inject()
    private val motionStore: MotionStateStore by inject()

    /** Set when opened from the "re-arm" notification; the UI then starts motion control. */
    private var startRequested by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        startRequested = intent.getBooleanExtra(EXTRA_START_MOTION, false)
        setContent {
            MyGestureTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val connected by AirScrollAccessibilityService.instance.collectAsState()
                    val foregroundApp by ForegroundAppTracker.current.collectAsState()
                    val motion by motionStore.state.collectAsState()
                    val debug by motionStore.debug.collectAsState()
                    HomeScreen(
                        accessibilityEnabled = connected != null,
                        foregroundApp = foregroundApp,
                        motion = motion,
                        debug = debug,
                        startRequested = startRequested,
                        onStartRequestHandled = { startRequested = false },
                        onDispatch = dispatcher::dispatch,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_START_MOTION, false)) startRequested = true
    }

    companion object {
        const val EXTRA_START_MOTION = "com.kwan.mygesture.extra.START_MOTION"
    }
}
