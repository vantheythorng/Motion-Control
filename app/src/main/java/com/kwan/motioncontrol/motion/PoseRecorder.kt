package com.kwan.motioncontrol.motion

import com.kwan.motioncontrol.data.CustomPose
import com.kwan.motioncontrol.data.Motion
import com.kwan.motioncontrol.data.PoseSpace

/**
 * Collects the samples for a new custom pose (SPEC §5.7): in each round, frames where the hand is
 * tracked in both landmark spaces and held steady (barely changed since the previous frame).
 * Not thread-safe: the caller serializes [startRound] and [onFrame].
 */
class PoseRecorder(val perRound: Int = 20, val rounds: Int = 2) {

    val imageSamples = mutableListOf<List<Float>>()
    val worldSamples = mutableListOf<List<Float>>()

    /** Rounds finished so far. */
    var roundsDone = 0
        private set
    var capturing = false
        private set
    private var roundStart = 0
    private var previous: FloatArray? = null
    /** How often the built-in classifier saw each built-in pose in the captured frames. */
    private val builtInSeen = mutableMapOf<Motion, Int>()

    val done: Boolean get() = roundsDone >= rounds

    /** Samples in the current (or last) round. */
    val roundProgress: Int get() = imageSamples.size - roundStart

    fun startRound() {
        if (done) return
        capturing = true
        roundStart = imageSamples.size
        previous = null
    }

    /** Starts over, e.g. after "Record again". */
    fun clear() {
        imageSamples.clear()
        worldSamples.clear()
        builtInSeen.clear()
        roundsDone = 0
        roundStart = 0
        capturing = false
        previous = null
    }

    /** Returns true when this frame completed the round. */
    fun onFrame(frame: HandFrame): Boolean {
        if (!capturing) return false
        val image = PoseFingerprint.of(frame, PoseSpace.IMAGE)
        val world = PoseFingerprint.of(frame, PoseSpace.WORLD)
        if (image == null || world == null) {
            previous = null
            return false
        }
        val last = previous
        previous = image
        if (last == null || PoseFingerprint.distance(last, image) > STEADY) return false
        imageSamples += image.toList()
        worldSamples += world.toList()
        builtIn(frame)?.let { builtInSeen[it] = (builtInSeen[it] ?: 0) + 1 }
        if (roundProgress < perRound) return false
        capturing = false
        roundsDone++
        return true
    }

    /**
     * The built-in pose the engine would mostly have seen while recording (in at least half the
     * samples), or null. In profiles that use the new pose, it replaces that built-in one.
     */
    fun mostlyBuiltIn(): Motion? {
        val (motion, count) = builtInSeen.maxByOrNull { it.value } ?: return null
        return motion.takeIf { count * 2 >= imageSamples.size }
    }

    /** What the engine would see as built-in in this frame: the classifier, or the open-palm shape check. */
    private fun builtIn(frame: HandFrame): Motion? {
        val label = frame.gesture?.takeIf { frame.gestureScore >= BUILT_IN_CONFIDENCE }
        return label?.let(GestureEngine::motionForPose)
            ?: Motion.OPEN_PALM_HOLD.takeIf { frame.landmarks?.let(HandShape::isOpenPalm) == true }
    }

    companion object {
        /** Max change between consecutive frames, in palm lengths, for a frame to count as steady. */
        const val STEADY = 0.08f
        const val BUILT_IN_CONFIDENCE = 0.6f

        /**
         * Existing custom poses (other than the one being re-recorded) that the new [samples]
         * would be mistaken for: at least 30 % of them match that pose in [space].
         */
        fun similarPoses(samples: List<List<Float>>, others: List<CustomPose>, space: PoseSpace): List<CustomPose> {
            if (samples.isEmpty()) return emptyList()
            val matcher = PoseMatcher(others, space)
            if (matcher.isEmpty) return emptyList()
            val hits = samples.mapNotNull { sample ->
                matcher.rank(sample.toFloatArray()).firstOrNull()?.takeIf { it.withinThreshold }?.pose
            }
            return hits.groupingBy { it }.eachCount()
                .filterValues { it * 10 >= samples.size * 3 }
                .keys.toList()
        }
    }
}
