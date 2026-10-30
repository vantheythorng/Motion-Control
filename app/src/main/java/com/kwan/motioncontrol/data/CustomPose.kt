package com.kwan.motioncontrol.data

import kotlinx.serialization.Serializable

/** How a custom pose fires (D7): chosen per pose. */
enum class PoseTrigger {
    /** Stable for a moment (~0.3 s), like thumbs up. */
    QUICK,
    /** Held still for about a second, like open palm or fist. Fewer false triggers. */
    HOLD,
}

/** Which hand landmarks custom poses are matched on (D6, being compared on device). */
enum class PoseSpace {
    /** MediaPipe's 2D image landmarks: simple; turning the hand changes the shape. */
    IMAGE,
    /** MediaPipe's 3D world landmarks: robust to turning the hand, slightly noisier. */
    WORLD,
}

/**
 * A hand pose the user recorded (SPEC §5.7). Only normalized landmark coordinates are stored,
 * never images. Both landmark spaces are kept for every sample until D6 is decided, so switching
 * spaces never needs re-recording. Shared by all profiles (D5); profiles bind it by [id].
 */
@Serializable
data class CustomPose(
    val id: String,
    val name: String,
    val trigger: PoseTrigger = PoseTrigger.HOLD,
    /** One fingerprint per recorded frame (see PoseFingerprint), in image space. */
    val imageSamples: List<List<Float>> = emptyList(),
    /** The same frames in world space. */
    val worldSamples: List<List<Float>> = emptyList(),
    /**
     * The built-in gesture (Motion name) the recognizer mostly saw while this pose was recorded,
     * or null. A profile using both gets a warning: the custom pose wins. Stored as a name so a
     * removed gesture never makes the pose unreadable.
     */
    val resembles: String? = null,
    /** Schema version of this stored entry, for future migrations. */
    val version: Int = 1,
) {
    val resemblesMotion: Motion? get() = Motion.entries.firstOrNull { it.name == resembles }

    fun samples(space: PoseSpace): List<List<Float>> = when (space) {
        PoseSpace.IMAGE -> imageSamples
        PoseSpace.WORLD -> worldSamples
    }

    companion object {
        const val MAX_POSES = 10
    }
}

/** What fired: a built-in motion or one of the user's poses. */
sealed interface Gesture {
    val label: String

    data class BuiltIn(val motion: Motion) : Gesture {
        override val label: String get() = motion.label
    }

    data class Custom(val id: String, val name: String) : Gesture {
        override val label: String get() = name
    }
}
