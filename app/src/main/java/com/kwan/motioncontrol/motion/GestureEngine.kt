package com.kwan.motioncontrol.motion

import com.kwan.motioncontrol.data.DeviceAction
import com.kwan.motioncontrol.data.Motion

/**
 * Turns per-frame hand observations into [Motion]s and emits the bound [DeviceAction].
 *
 * State machine: IDLE → ARMED → TRIGGERED → COOLDOWN → IDLE. Pure Kotlin so it can be unit-tested;
 * [onFrame] must be called from a single thread. [applyProfile] may be called from any thread.
 */
class GestureEngine(
    bindings: Map<Motion, DeviceAction>,
    config: Config = Config(),
    private val isPaused: () -> Boolean = { false },
    /** Called when a motion is recognized but not acted on, with the reason (for diagnostics). */
    private val onIgnored: (Motion, String) -> Unit = { _, _ -> },
    private val onAction: (Motion, DeviceAction) -> Unit,
) {
    enum class State { IDLE, ARMED, TRIGGERED, COOLDOWN }

    data class Config(
        /** Frames with a hand before anything fires (filters people walking past). Brief dropouts
         *  shorter than [handLostMs] don't reset the count. */
        val armFrames: Int = 3,
        val minConfidence: Float = 0.6f,
        /** OPEN_PALM_HOLD / FIST_HOLD must be held still this long. */
        val holdMs: Long = 1000,
        /** Other poses (thumbs up, victory, …) must be stable this long. */
        val poseMs: Long = 300,
        /** Max palm travel within [STILL_WINDOW_MS] for a hold to count as still. */
        val maxStillTravel: Float = 0.06f,
        /** Tracking dropouts shorter than this (e.g. motion blur mid-swipe) are ignored. */
        val handLostMs: Long = 400,
        val cooldownMs: Long = 800,
        /** Working range: hands closer or farther than this are ignored (set per profile). */
        val minDistanceCm: Float = 30f,
        val maxDistanceCm: Float = 100f,
        /** A fired pose must be gone this long before it can fire again; single-frame
         *  classification flicker during a long hold doesn't count as letting go. */
        val releaseMs: Long = 400,
        /** A new pose must be seen this long before it replaces the current one. */
        val poseSwitchMs: Long = 200,
        val swipeMinTravel: Float = 0.18f,
        val swipeDominance: Float = 2f,
    )

    data class Snapshot(
        val state: State,
        val handPresent: Boolean,
        val pose: String?,
        val poseScore: Float,
        /** The smoothed pose the engine acts on (may come from the landmark shape check). */
        val activePose: String?,
        val distanceCm: Float?,
        /** False when a hand is visible but outside the working distance range. */
        val inRange: Boolean,
        val dx: Float,
        val dy: Float,
        val lastMotion: Motion?,
    )

    private data class TimedPoint(val x: Float, val y: Float, val t: Long)

    /** Bindings and config are swapped together, so a frame never sees half of a new profile. */
    private class Tuning(val bindings: Map<Motion, DeviceAction>, val config: Config)

    @Volatile private var tuning = Tuning(bindings.withoutNone(), config)

    // Snapshot of [tuning] for the frame being processed; only touched on the frame thread.
    private var bindings = tuning.bindings
    private var config = tuning.config

    private val swipe = AirSwipeDetector(config.swipeMinTravel, config.swipeDominance)
    private val palmTrail = ArrayDeque<TimedPoint>()

    var state = State.IDLE
        private set
    private var handFrames = 0
    private var lastHandAt = 0L
    /** The smoothed pose; single-frame classifier flicker doesn't change it. */
    private var pose: String? = null
    private var poseSince = 0L
    private var candidate: String? = null
    private var candidateSince = 0L
    /** A pose that already fired (or was held through a swipe); it must be released first. */
    private var spentPose: String? = null
    /** When the spent pose was last seen; it's released after [Config.releaseMs] without it. */
    private var spentSeenAt = 0L
    private var lastFireAt = 0L
    private var lastMotion: Motion? = null

    @Volatile private var resetRequested = false

    /**
     * Forgets the current hand and pose (call when the camera stops), so a pose held before
     * leaving an app can't fire on re-entry. Applied on the frame thread at the next frame.
     */
    fun reset() {
        resetRequested = true
    }

    /** Switches to a new profile's bindings and tuning without resetting the gesture state. */
    fun applyProfile(bindings: Map<Motion, DeviceAction>, config: Config) {
        tuning = Tuning(bindings.withoutNone(), config)
    }

    fun onFrame(frame: HandFrame): Snapshot {
        if (resetRequested) {
            resetRequested = false
            onHandLost()
            state = State.IDLE
        }
        val current = tuning
        bindings = current.bindings
        config = current.config
        swipe.minTravel = config.swipeMinTravel
        swipe.dominance = config.swipeDominance

        val now = frame.timestampMs
        if (state == State.TRIGGERED) state = State.COOLDOWN
        if (state == State.COOLDOWN && now - lastFireAt >= config.cooldownMs) state = State.IDLE

        val hand = frame.landmarks?.takeIf { inRange(frame.distanceCm) }
        if (hand == null) {
            if (now - lastHandAt > config.handLostMs) onHandLost()
            return snapshot(frame, handPresent = false)
        }
        lastHandAt = now
        handFrames++
        if (state == State.IDLE && handFrames >= config.armFrames) state = State.ARMED

        val label = frame.gesture?.takeIf { frame.gestureScore >= config.minConfidence && it in POSES }
            ?: OPEN_PALM.takeIf { HandShape.isOpenPalm(hand) }
        updatePose(label, now)
        if (spentPose != null) {
            if (pose == spentPose) spentSeenAt = now
            else if (now - spentSeenAt >= config.releaseMs) spentPose = null
        }

        val palm = hand[PALM_CENTER]
        palmTrail.addLast(TimedPoint(palm.x, palm.y, now))
        while (now - palmTrail.first().t > STILL_WINDOW_MS) palmTrail.removeFirst()

        // Swipes are only tracked while armed, so a cooldown always starts a fresh window and the
        // hand's return stroke after a swipe doesn't fire the opposite swipe.
        if (state == State.ARMED) detect(palm, now)
        return snapshot(frame, handPresent = true)
    }

    private fun detect(palm: HandPoint, now: Long) {
        swipe.onFrame(palm.x, palm.y, now)?.let { motion ->
            spend(pose, now)
            fire(motion, now)
            return
        }
        val label = pose ?: return
        if (label == spentPose) return
        val motion = POSES.getValue(label)
        if (motion in HOLDS) {
            if (!isStill()) {
                poseSince = now
                return
            }
            if (now - poseSince < config.holdMs) return
        } else if (now - poseSince < config.poseMs) {
            return
        }
        // Spend even when it wasn't acted on (unbound or paused), so a held pose is reported once.
        fire(motion, now)
        spend(label, now)
    }

    private fun updatePose(label: String?, now: Long) {
        if (label == pose) {
            candidate = pose
            return
        }
        if (label != candidate) {
            candidate = label
            candidateSince = now
        }
        if (now - candidateSince >= config.poseSwitchMs) {
            pose = candidate
            poseSince = candidateSince
        }
    }

    private fun spend(label: String?, now: Long) {
        spentPose = label
        spentSeenAt = now
    }

    private fun fire(motion: Motion, now: Long): Boolean {
        val action = bindings[motion]
        if (action == null) {
            onIgnored(motion, "no binding")
            return false
        }
        if (isPaused() && action != DeviceAction.TogglePause) {
            onIgnored(motion, "paused")
            return false
        }
        state = State.TRIGGERED
        lastFireAt = now
        lastMotion = motion
        swipe.reset()
        onAction(motion, action)
        return true
    }

    /** Unknown distance counts as in range, so a missing estimate never blocks gestures. */
    private fun inRange(distanceCm: Float?) =
        distanceCm == null || distanceCm in config.minDistanceCm..config.maxDistanceCm

    private fun isStill(): Boolean {
        val xs = palmTrail.map { it.x }
        val ys = palmTrail.map { it.y }
        return xs.max() - xs.min() <= config.maxStillTravel &&
            ys.max() - ys.min() <= config.maxStillTravel
    }

    private fun onHandLost() {
        handFrames = 0
        swipe.reset()
        palmTrail.clear()
        pose = null
        candidate = null
        spentPose = null
        if (state == State.ARMED) state = State.IDLE
    }

    private fun snapshot(frame: HandFrame, handPresent: Boolean) = Snapshot(
        state = state,
        handPresent = handPresent,
        pose = frame.gesture,
        poseScore = frame.gestureScore,
        activePose = pose,
        distanceCm = frame.distanceCm,
        inRange = inRange(frame.distanceCm),
        dx = swipe.dx,
        dy = swipe.dy,
        lastMotion = lastMotion,
    )

    private companion object {
        /** "None" means unbound: as a binding it would start a cooldown and swallow the gesture. */
        fun Map<Motion, DeviceAction>.withoutNone() = filterValues { it != DeviceAction.None }

        /** MIDDLE_FINGER_MCP: stable for both open and closed hands. */
        const val PALM_CENTER = 9
        const val STILL_WINDOW_MS = 300L

        const val OPEN_PALM = "Open_Palm"

        val POSES = mapOf(
            OPEN_PALM to Motion.OPEN_PALM_HOLD,
            "Closed_Fist" to Motion.FIST_HOLD,
            "Thumb_Up" to Motion.THUMBS_UP,
            "Thumb_Down" to Motion.THUMBS_DOWN,
            "Victory" to Motion.VICTORY,
            "Pointing_Up" to Motion.POINTING_UP,
        )
        val HOLDS = setOf(Motion.OPEN_PALM_HOLD, Motion.FIST_HOLD)
    }
}
