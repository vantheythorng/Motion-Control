package com.kwan.motioncontrol.motion

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.transformLatest

/** Default delay before the camera is released for an app without a profile. */
const val APP_OFF_DELAY_MS = 500L

/**
 * Whether motion control should hold the camera. All inputs are combined in one place so the
 * screen, Test mode and app changes can't race each other.
 *
 * Leaving for an app without a profile releases the camera only after [offDelayMs], so a brief
 * visit (a quick app switch) doesn't flap it. Turning the camera on, and every other reason to
 * turn it off (motion control stopped, Test mode, screen off or locked), applies immediately.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun cameraWanted(
    running: Flow<Boolean>,
    appAllowed: Flow<Boolean>,
    testModeActive: Flow<Boolean>,
    screenUsable: Flow<Boolean>,
    offDelayMs: Long = APP_OFF_DELAY_MS,
): Flow<Boolean> {
    val appAllowedSettled = appAllowed.distinctUntilChanged().transformLatest { allowed ->
        if (!allowed) delay(offDelayMs)
        emit(allowed)
    }
    return combine(running, appAllowedSettled, testModeActive, screenUsable) { run, app, test, screen ->
        run && app && !test && screen
    }.distinctUntilChanged()
}
