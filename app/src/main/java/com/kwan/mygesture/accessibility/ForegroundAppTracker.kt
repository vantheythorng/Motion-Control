package com.kwan.mygesture.accessibility

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Package name of the app the user is currently looking at, fed by the accessibility service. */
object ForegroundAppTracker {
    private val _current = MutableStateFlow<String?>(null)
    val current: StateFlow<String?> = _current.asStateFlow()

    fun update(packageName: String) {
        _current.value = packageName
    }
}
