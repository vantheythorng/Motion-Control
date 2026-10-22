package com.kwan.mygesture.data

import kotlinx.serialization.Serializable

/** A named set of motion → action bindings plus tuning. Stored as JSON by [ProfileRepository]. */
@Serializable
data class Profile(
    val id: String,
    val name: String,
    /** Apps this profile is for; empty = global/default profile. Used from Phase 4. */
    val packages: Set<String> = emptySet(),
    val bindings: Map<Motion, DeviceAction> = emptyMap(),
    /** 0..1; higher = smaller motions and lower confidence are accepted. See ProfileTuning. */
    val sensitivity: Float = 0.5f,
    val cooldownMs: Long = 800,
    /** Schema version of this stored entry, for future migrations. */
    val version: Int = 1,
) {
    /** Binding [motion] to [DeviceAction.None] removes it, so "None" never reaches the engine. */
    fun withBinding(motion: Motion, action: DeviceAction): Profile = copy(
        bindings = if (action == DeviceAction.None) bindings - motion else bindings + (motion to action),
    )
}

object DefaultProfiles {
    const val SHORT_VIDEO_ID = "short_video"
    const val GLOBAL_ID = "global"

    val shortVideo = Profile(
        id = SHORT_VIDEO_ID,
        name = "Short video",
        packages = setOf(
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.trill",
            "com.instagram.android",
            "com.google.android.youtube",
            "com.facebook.katana",
        ),
        bindings = mapOf(
            Motion.AIR_SWIPE_UP to DeviceAction.Swipe(Direction.UP),
            Motion.AIR_SWIPE_DOWN to DeviceAction.Swipe(Direction.DOWN),
            Motion.OPEN_PALM_HOLD to DeviceAction.TapCenter, // pause/play the video
            Motion.THUMBS_UP to DeviceAction.DoubleTapCenter, // like
            // Not TogglePause as in the spec: pausing gestures with a gesture blocked every other
            // gesture, so pausing is only done from the notification.
            Motion.FIST_HOLD to DeviceAction.TapCenter, // pause/play the video
        ),
    )

    val global = Profile(
        id = GLOBAL_ID,
        name = "Global",
        bindings = mapOf(
            Motion.AIR_SWIPE_UP to DeviceAction.Swipe(Direction.UP),
            Motion.AIR_SWIPE_DOWN to DeviceAction.Swipe(Direction.DOWN),
        ),
    )

    val all = listOf(shortVideo, global)
}
