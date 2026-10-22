package com.kwan.mygesture.motion

import com.kwan.mygesture.data.Profile
import kotlin.math.roundToLong

/** Maps a profile's sensitivity and cooldown onto [GestureEngine.Config]. */
object ProfileTuning {

    /**
     * Sensitivity 0.5 reproduces the values tuned on device (swipe travel 0.18, confidence 0.6,
     * hold 1 s). Higher sensitivity accepts smaller swipes, less certain poses and shorter holds.
     * Everything else (distance range, dropout handling, …) keeps its default.
     */
    fun configFor(profile: Profile): GestureEngine.Config {
        val s = profile.sensitivity.coerceIn(0f, 1f)
        return GestureEngine.Config().copy(
            swipeMinTravel = 0.18f * (1.5f - s),
            minConfidence = 0.6f + (0.5f - s) * 0.3f,
            holdMs = (1000 * (1.5f - s)).roundToLong(),
            cooldownMs = profile.cooldownMs,
        )
    }
}
