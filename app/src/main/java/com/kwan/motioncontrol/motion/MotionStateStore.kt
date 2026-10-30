package com.kwan.motioncontrol.motion

import com.kwan.motioncontrol.data.AppRoute
import com.kwan.motioncontrol.data.Motion
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Runtime state of motion control, shared by [MotionService], the UI and the bubble. */
data class MotionState(
    /** MotionService is running in the foreground. */
    val running: Boolean = false,
    val paused: Boolean = false,
    /** The camera is bound; false while the screen is off or locked, or the app has no profile. */
    val cameraActive: Boolean = false,
    /** Which profile is used for the last opened app, or why the camera is off. */
    val route: AppRoute? = null,
    /** Display name of [AppRoute.app]. */
    val appLabel: String? = null,
    /** "GPU" or "CPU" once the recognizer is created. */
    val delegate: String? = null,
    val error: String? = null,
)

/** Live per-frame data for the debug view. */
data class DebugFrame(
    val landmarks: List<HandPoint>?,
    val snapshot: GestureEngine.Snapshot,
    val fps: Float,
)

class MotionStateStore {
    private val _state = MutableStateFlow(MotionState())
    val state: StateFlow<MotionState> = _state.asStateFlow()

    private val _debug = MutableStateFlow<DebugFrame?>(null)
    val debug: StateFlow<DebugFrame?> = _debug.asStateFlow()

    /** True while the Test mode screen has the front camera; the service releases it meanwhile. */
    private val _testModeActive = MutableStateFlow(false)
    val testModeActive: StateFlow<Boolean> = _testModeActive.asStateFlow()

    fun setTestModeActive(active: Boolean) {
        _testModeActive.value = active
    }

    /** Each motion that triggered an action, so the bubble can flash. */
    private val _fired = MutableSharedFlow<Motion>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val fired: SharedFlow<Motion> = _fired.asSharedFlow()

    fun notifyFired(motion: Motion) {
        _fired.tryEmit(motion)
    }

    fun update(transform: (MotionState) -> MotionState) = _state.update(transform)

    fun publishDebug(frame: DebugFrame?) {
        _debug.value = frame
    }
}
