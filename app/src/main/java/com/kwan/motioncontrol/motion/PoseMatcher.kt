package com.kwan.motioncontrol.motion

import com.kwan.motioncontrol.data.CustomPose
import com.kwan.motioncontrol.data.PoseSpace

/**
 * Nearest-match recognition of custom poses (SPEC §5.7), no model training: a frame matches the
 * pose whose closest recorded sample is nearest, if that is within the pose's threshold and
 * clearly closer than any other pose.
 */
class PoseMatcher(poses: List<CustomPose>, val space: PoseSpace) {

    data class Match(val pose: CustomPose, val distance: Float, val threshold: Float) {
        /** 100 % = identical to a recorded sample; 50 % and up is within the threshold. */
        val score: Float get() = (1f - distance / (2 * threshold)).coerceIn(0f, 1f)
        val withinThreshold: Boolean get() = distance <= threshold
    }

    private class Entry(val pose: CustomPose, val samples: List<FloatArray>, val threshold: Float)

    private val entries = poses.mapNotNull { pose ->
        val samples = pose.samples(space).map { it.toFloatArray() }
        if (samples.isEmpty()) null else Entry(pose, samples, thresholdFor(samples))
    }

    val isEmpty: Boolean get() = entries.isEmpty()

    /** Every pose with its distance to [fingerprint], closest first. */
    fun rank(fingerprint: FloatArray): List<Match> = entries
        .filter { it.samples.first().size == fingerprint.size }
        .map { e -> Match(e.pose, e.samples.minOf { PoseFingerprint.distance(it, fingerprint) }, e.threshold) }
        .sortedBy { it.distance }

    /** The pose [fingerprint] shows, or null when none is close enough or two are too alike to tell. */
    fun match(fingerprint: FloatArray?): Match? = fingerprint?.let { pick(rank(it)) }

    /** [match] for an already [rank]ed list. */
    fun pick(ranked: List<Match>): Match? {
        val best = ranked.firstOrNull()?.takeIf { it.withinThreshold } ?: return null
        val second = ranked.getOrNull(1)
        if (second != null && second.distance < best.distance * MARGIN) return null
        return best
    }

    companion object {
        /** The runner-up must be this many times farther than the best match. */
        const val MARGIN = 1.25f
        /** Threshold = this × the spread of the pose's own samples, within the limits below. */
        const val SPREAD_FACTOR = 2.5f
        /** In palm lengths. Starting values; tune on device with the readout in Test mode. */
        const val MIN_THRESHOLD = 0.12f
        const val MAX_THRESHOLD = 0.35f

        /**
         * How far a new frame of the same pose may land: the spread of the recorded samples
         * (90th percentile of their distance to the average sample), scaled and clamped.
         */
        fun thresholdFor(samples: List<FloatArray>): Float {
            val size = samples.first().size
            val mean = FloatArray(size) { i -> samples.sumOf { it[i].toDouble() }.toFloat() / samples.size }
            val spreads = samples.map { PoseFingerprint.distance(it, mean) }.sorted()
            val p90 = spreads[((spreads.size - 1) * 0.9f).toInt()]
            return (p90 * SPREAD_FACTOR).coerceIn(MIN_THRESHOLD, MAX_THRESHOLD)
        }
    }
}
