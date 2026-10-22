package com.kwan.mygesture.motion

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Runtime state of motion control, shared by [MotionService], the UI and (later) the bubble. */
data class MotionState(
    /** MotionService is running in the foreground. */
    val running: Boolean = false,
    val paused: Boolean = false,
    /** The camera is bound; false while the screen is off or locked. */
    val cameraActive: Boolean = false,
    /** The service died and couldn't restart the camera from the background. */
    val needsReArm: Boolean = false,
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

    fun update(transform: (MotionState) -> MotionState) = _state.update(transform)

    fun publishDebug(frame: DebugFrame?) {
        _debug.value = frame
    }
}
