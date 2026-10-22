package com.kwan.motioncontrol.accessibility

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The last real app the user opened, fed by the accessibility service. Our own UI and system
 * overlays don't count, so while Motion Control is open this is still the app opened before it.
 * Null after a process restart until the next window change, or while the accessibility service
 * is off.
 */
object ForegroundAppTracker {
    private val _current = MutableStateFlow<String?>(null)
    val current: StateFlow<String?> = _current.asStateFlow()

    fun update(packageName: String) {
        _current.value = packageName
    }

    fun clear() {
        _current.value = null
    }
}
