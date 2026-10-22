package com.kwan.motioncontrol.motion

import com.kwan.motioncontrol.data.DefaultProfiles
import com.kwan.motioncontrol.data.DeviceAction
import com.kwan.motioncontrol.data.Direction
import com.kwan.motioncontrol.data.Motion
import org.junit.Assert.assertEquals
import org.junit.Test

class GestureEngineTest {

    private val fired = mutableListOf<Motion>()
    private var paused = false
    private var now = 1_000L

    private val engine = GestureEngine(
        bindings = DefaultProfiles.shortVideo.bindings + (Motion.OPEN_PALM_HOLD to DeviceAction.TapCenter),
        isPaused = { paused },
        onAction = { motion, action ->
            fired += motion
            if (action == DeviceAction.TogglePause) paused = !paused
        },
    )

    /** Hand distance used by [hand]; null = no estimate. */
    private var distanceCm: Float? = 60f

    private fun hand(x: Float, y: Float, gesture: String = "None", score: Float = 0.9f) {
        engine.onFrame(HandFrame(now, List(21) { HandPoint(x, y) }, gesture, score, distanceCm))
        now += FRAME_MS
    }

    private fun noHand() {
        engine.onFrame(HandFrame(now, null, null, 0f))
        now += FRAME_MS
    }

    /** Holds the hand at its rest position (y = 0.8), where every upward swipe starts. */
    private fun holdStill(gesture: String, ms: Long, score: Float = 0.9f) =
        repeat((ms / FRAME_MS).toInt()) { hand(0.5f, REST_Y, gesture, score) }

    private fun moveVertically(fromY: Float, toY: Float, frames: Int = 6, gesture: String = "None") =
        (0..frames).forEach { i -> hand(0.5f, fromY + (toY - fromY) * i / frames, gesture) }

    @Test fun swipeUpFiresAfterArming() {
        holdStill("None", 200)
        moveVertically(0.8f, 0.3f)
        assertEquals(listOf(Motion.AIR_SWIPE_UP), fired)
    }

    @Test fun returnStrokeAfterSwipeDoesNotFireOppositeSwipe() {
        holdStill("None", 200)
        moveVertically(0.8f, 0.3f)
        moveVertically(0.3f, 0.8f) // hand drops back down within the cooldown
        holdStill("None", 1000)
        assertEquals(listOf(Motion.AIR_SWIPE_UP), fired)
    }

    @Test fun secondSwipeFiresAfterCooldown() {
        holdStill("None", 200)
        moveVertically(0.8f, 0.3f)
        moveVertically(0.3f, 0.8f)
        holdStill("None", 500)
        moveVertically(0.8f, 0.3f)
        assertEquals(listOf(Motion.AIR_SWIPE_UP, Motion.AIR_SWIPE_UP), fired)
    }

    @Test fun briefTrackingDropoutDoesNotBreakSwipe() {
        holdStill("None", 200)
        moveVertically(0.8f, 0.6f, frames = 2)
        noHand()
        moveVertically(0.55f, 0.3f, frames = 3)
        assertEquals(listOf(Motion.AIR_SWIPE_UP), fired)
    }

    @Test fun swipeSurvivesSeveralDroppedFrames() {
        holdStill("None", 200)
        moveVertically(0.8f, 0.65f, frames = 2)
        repeat(4) { noHand() } // ~260 ms of motion blur
        moveVertically(0.45f, 0.35f, frames = 2)
        assertEquals(listOf(Motion.AIR_SWIPE_UP), fired)
    }

    @Test fun flickeringDetectionStillArmsAndSwipes() {
        // Hand found only on every other frame, as seen on device with fast movement.
        (0..12).forEach { i ->
            if (i % 2 == 0) hand(0.5f, 0.8f - 0.04f * i) else noHand()
        }
        assertEquals(listOf(Motion.AIR_SWIPE_UP), fired)
    }

    @Test fun handLeavingLongerThanLossTimeoutDisarms() {
        holdStill("None", 200)
        repeat(8) { noHand() } // ~530 ms
        hand(0.5f, 0.8f)
        assertEquals(GestureEngine.State.IDLE, engine.state)
    }

    @Test fun holdFiresOncePerHold() {
        holdStill("Closed_Fist", 3000)
        assertEquals(listOf(Motion.FIST_HOLD), fired)
        holdStill("None", 500) // release the pose
        holdStill("Closed_Fist", 1200)
        assertEquals(listOf(Motion.FIST_HOLD, Motion.FIST_HOLD), fired)
    }

    @Test fun classifierFlickerDuringLongHoldDoesNotRefire() {
        // Seen on device: one long fist toggled pause twice because a single frame
        // classified as something else counted as letting go.
        repeat(6) {
            holdStill("Closed_Fist", 600)
            hand(0.5f, REST_Y, "None")
        }
        assertEquals(listOf(Motion.FIST_HOLD), fired)
    }

    @Test fun openPalmHoldPlaysOrPausesVideo() {
        val actions = mutableListOf<DeviceAction>()
        val e = GestureEngine(DefaultProfiles.shortVideo.bindings) { _, action -> actions += action }
        repeat(20) { e.onFrame(HandFrame(now, List(21) { HandPoint(0.5f, 0.8f) }, "Open_Palm", 0.9f)); now += FRAME_MS }
        assertEquals(listOf<DeviceAction>(DeviceAction.TapCenter), actions)
    }

    @Test fun lowConfidenceOpenPalmFiresWhenFingersAreExtended() {
        val actions = mutableListOf<DeviceAction>()
        val e = GestureEngine(DefaultProfiles.shortVideo.bindings) { _, action -> actions += action }
        // Classifier unsure (0.55, below the 0.6 threshold) but all four fingertips are well
        // above their PIP joints.
        val palm = List(21) { HandPoint(0.5f, 0.8f) }.toMutableList().apply {
            listOf(6, 10, 14, 18).forEach { this[it] = HandPoint(0.5f, 0.6f) }
            listOf(8, 12, 16, 20).forEach { this[it] = HandPoint(0.5f, 0.45f) }
        }
        repeat(20) { e.onFrame(HandFrame(now, palm, "Open_Palm", 0.55f)); now += FRAME_MS }
        assertEquals(listOf<DeviceAction>(DeviceAction.TapCenter), actions)
    }

    @Test fun handCloserThan30cmIsIgnored() {
        distanceCm = 20f
        holdStill("None", 200)
        moveVertically(0.8f, 0.3f)
        holdStill("Closed_Fist", 1500)
        assertEquals(emptyList<Motion>(), fired)
    }

    @Test fun handFartherThan1mIsIgnored() {
        distanceCm = 150f
        holdStill("None", 200)
        moveVertically(0.8f, 0.3f)
        assertEquals(emptyList<Motion>(), fired)
        assertEquals(GestureEngine.State.IDLE, engine.state)
    }

    @Test fun rangeLimitsAreInclusive() {
        distanceCm = 30f
        holdStill("None", 200)
        moveVertically(0.8f, 0.3f)
        moveVertically(0.3f, 0.8f)
        distanceCm = 100f
        holdStill("None", 500)
        moveVertically(0.8f, 0.3f)
        assertEquals(listOf(Motion.AIR_SWIPE_UP, Motion.AIR_SWIPE_UP), fired)
    }

    @Test fun profileCanWidenTheDistanceRange() {
        distanceCm = 150f // ignored with the default 30 cm – 1 m
        engine.applyProfile(
            DefaultProfiles.shortVideo.bindings,
            ProfileTuning.configFor(DefaultProfiles.shortVideo.copy(maxDistanceCm = 200f)),
        )
        holdStill("None", 200)
        moveVertically(0.8f, 0.3f)
        assertEquals(listOf(Motion.AIR_SWIPE_UP), fired)
    }

    @Test fun unknownDistanceIsAllowed() {
        distanceCm = null
        holdStill("None", 200)
        moveVertically(0.8f, 0.3f)
        assertEquals(listOf(Motion.AIR_SWIPE_UP), fired)
    }

    @Test fun resetForgetsAPoseHeldBeforeTheCameraStopped() {
        // Fist held, then the camera stops (left the app) before the hold completes.
        holdStill("Closed_Fist", 800)
        engine.reset()
        // Back in the app with the fist still up: the hold starts over instead of firing at once.
        holdStill("Closed_Fist", 600)
        assertEquals(emptyList<Motion>(), fired)
        holdStill("Closed_Fist", 600)
        assertEquals(listOf(Motion.FIST_HOLD), fired)
    }

    @Test fun holdNeedsAFullSecond() {
        holdStill("Closed_Fist", 800)
        assertEquals(emptyList<Motion>(), fired)
    }

    @Test fun movingPalmDoesNotTriggerHold() {
        repeat(30) { i -> hand(0.3f + (i % 10) * 0.04f, 0.5f, "Open_Palm") }
        assertEquals(emptyList<Motion>(), fired)
    }

    @Test fun palmHeldAfterSwipeDoesNotAlsoFireHold() {
        holdStill("Open_Palm", 200)
        moveVertically(0.8f, 0.3f, gesture = "Open_Palm")
        holdStill("Open_Palm", 2000)
        assertEquals(listOf(Motion.AIR_SWIPE_UP), fired)
    }

    @Test fun lowConfidencePoseIsIgnored() {
        holdStill("Closed_Fist", 2000, score = 0.4f)
        assertEquals(emptyList<Motion>(), fired)
    }

    @Test fun fistHoldPlaysOrPausesVideoAndNeverBlocksOtherMotions() {
        val actions = mutableListOf<DeviceAction>()
        val e = GestureEngine(DefaultProfiles.shortVideo.bindings) { _, action -> actions += action }
        fun frame(y: Float, gesture: String) {
            e.onFrame(HandFrame(now, List(21) { HandPoint(0.5f, y) }, gesture, 0.9f))
            now += FRAME_MS
        }
        repeat(18) { frame(0.8f, "Closed_Fist") } // ~1.2 s fist hold
        repeat(14) { frame(0.8f, "None") } // past the cooldown
        (0..6).forEach { frame(0.8f - 0.5f * it / 6, "None") } // swipe up
        assertEquals(
            listOf(DeviceAction.TapCenter, DeviceAction.Swipe(Direction.UP)),
            actions,
        )
    }

    @Test fun pausedFromNotificationIgnoresEveryMotion() {
        paused = true
        holdStill("Closed_Fist", 1200)
        holdStill("None", 900)
        moveVertically(0.8f, 0.3f)
        moveVertically(0.3f, 0.8f) // also ignored while paused
        assertEquals(emptyList<Motion>(), fired)
        paused = false
        holdStill("None", 900)
        moveVertically(0.8f, 0.3f)
        assertEquals(listOf(Motion.AIR_SWIPE_UP), fired)
    }

    @Test fun nothingFiresWithoutAHand() {
        repeat(50) { noHand() }
        assertEquals(GestureEngine.State.IDLE, engine.state)
        assertEquals(emptyList<Motion>(), fired)
    }

    /** A hand at x = 0.5 and [y] showing no pose, fed to [e]. */
    private fun frameAt(e: GestureEngine, y: Float, gesture: String = "None") {
        e.onFrame(HandFrame(now, List(21) { HandPoint(0.5f, y) }, gesture, 0.9f))
        now += FRAME_MS
    }

    private fun swipe(e: GestureEngine, fromY: Float, toY: Float) =
        (0..6).forEach { frameAt(e, fromY + (toY - fromY) * it / 6) }

    @Test fun applyProfileRemapsLiveWithoutResettingState() {
        val actions = mutableListOf<DeviceAction>()
        val e = GestureEngine(DefaultProfiles.shortVideo.bindings) { _, action -> actions += action }
        repeat(3) { frameAt(e, 0.8f) }
        swipe(e, 0.8f, 0.3f) // next
        // Remapped from the editor while the hand stays in view; the engine doesn't re-arm.
        e.applyProfile(
            mapOf(Motion.AIR_SWIPE_UP to DeviceAction.Swipe(Direction.DOWN)),
            ProfileTuning.configFor(DefaultProfiles.shortVideo),
        )
        swipe(e, 0.3f, 0.8f) // return stroke, inside the cooldown
        repeat(14) { frameAt(e, 0.8f) }
        swipe(e, 0.8f, 0.3f) // now previous
        assertEquals(
            listOf(DeviceAction.Swipe(Direction.UP), DeviceAction.Swipe(Direction.DOWN)),
            actions,
        )
    }

    @Test fun noneBindingDoesNotSwallowGesture() {
        // If "None" counted as an action, the swipe up would start a cooldown and block the
        // swipe down right after it.
        val actions = mutableListOf<DeviceAction>()
        val e = GestureEngine(
            mapOf(
                Motion.AIR_SWIPE_UP to DeviceAction.None,
                Motion.AIR_SWIPE_DOWN to DeviceAction.Swipe(Direction.DOWN),
            )
        ) { _, action -> actions += action }
        repeat(3) { frameAt(e, 0.8f) }
        swipe(e, 0.8f, 0.3f)
        swipe(e, 0.3f, 0.8f)
        assertEquals(listOf<DeviceAction>(DeviceAction.Swipe(Direction.DOWN)), actions)
    }

    @Test fun sensitivityChangesSwipeThreshold() {
        fun firedAt(sensitivity: Float): List<Motion> {
            val got = mutableListOf<Motion>()
            val profile = DefaultProfiles.shortVideo.copy(sensitivity = sensitivity)
            val e = GestureEngine(profile.bindings, ProfileTuning.configFor(profile)) { motion, _ -> got += motion }
            repeat(3) { frameAt(e, 0.8f) }
            swipe(e, 0.8f, 0.68f) // a small swipe: 12 % of the frame
            return got
        }
        assertEquals(emptyList<Motion>(), firedAt(0.5f)) // default needs 18 %
        assertEquals(listOf(Motion.AIR_SWIPE_UP), firedAt(1f)) // most sensitive needs 9 %
    }

    private companion object {
        const val FRAME_MS = 66L
        const val REST_Y = 0.8f
    }
}
