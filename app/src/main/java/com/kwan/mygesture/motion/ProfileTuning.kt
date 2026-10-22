package com.kwan.mygesture.motion

import com.kwan.mygesture.data.Profile
import kotlin.math.roundToLong

/** Maps a profile's sensitivity, cooldown and working distance onto [GestureEngine.Config]. */
object ProfileTuning {

    /**
     * Sensitivity 0.5 reproduces the values tuned on device (swipe travel 0.18, confidence 0.6,
     * hold 1 s). Higher sensitivity accepts smaller swipes, less certain poses and shorter holds.
     * Everything else (dropout handling, arming, …) keeps its default.
     */
    fun configFor(profile: Profile): GestureEngine.Config {
        val s = profile.sensitivity.coerceIn(0f, 1f)
        val (minCm, maxCm) = distanceRange(profile)
        return GestureEngine.Config().copy(
            swipeMinTravel = 0.18f * (1.5f - s),
            minConfidence = 0.6f + (0.5f - s) * 0.3f,
            holdMs = (1000 * (1.5f - s)).roundToLong(),
            cooldownMs = profile.cooldownMs,
            minDistanceCm = minCm,
            maxDistanceCm = maxCm,
        )
    }

    /** The profile's range clamped to the supported limits, at least [Profile.MIN_DISTANCE_SPAN_CM] wide. */
    fun distanceRange(profile: Profile): Pair<Float, Float> {
        val maxCm = profile.maxDistanceCm
            .coerceIn(Profile.DISTANCE_LIMIT_MIN_CM + Profile.MIN_DISTANCE_SPAN_CM, Profile.DISTANCE_LIMIT_MAX_CM)
        val minCm = profile.minDistanceCm
            .coerceIn(Profile.DISTANCE_LIMIT_MIN_CM, maxCm - Profile.MIN_DISTANCE_SPAN_CM)
        return minCm to maxCm
    }

    /** E.g. "30 cm – 1 m" or "15 cm – 80 cm", for status text. */
    fun distanceLabel(profile: Profile): String {
        val (minCm, maxCm) = distanceRange(profile)
        fun cm(v: Float) = if (v >= 100f && v % 100f == 0f) "${(v / 100).toInt()} m" else "${v.toInt()} cm"
        return "${cm(minCm)} – ${cm(maxCm)}"
    }
}
