package com.kwan.motioncontrol.motion

import com.kwan.motioncontrol.data.CustomPose
import com.kwan.motioncontrol.data.DeviceAction
import com.kwan.motioncontrol.data.Gesture
import com.kwan.motioncontrol.data.Motion
import com.kwan.motioncontrol.data.PoseSpace
import com.kwan.motioncontrol.data.PoseTrigger
import org.junit.Assert.assertEquals
import org.junit.Test

class GestureEngineCustomPoseTest {

    private val fired = mutableListOf<String>()
    private val ignored = mutableListOf<String>()
    private var now = 1_000L

    private fun pose(id: String, hand: List<FloatArray>, trigger: PoseTrigger) = CustomPose(
        id = id,
        name = id,
        trigger = trigger,
        imageSamples = listOf(PoseFingerprint.image(TestHands.image(hand), false, 0.75f)!!.toList()),
    )

    private val rock = pose("rock", TestHands.shape(setOf(1, 4)), PoseTrigger.QUICK)
    private val three = pose("three", TestHands.shape(setOf(1, 2, 3)), PoseTrigger.HOLD)

    private val engine = GestureEngine(
        bindings = mapOf(Motion.THUMBS_UP to DeviceAction.VolumeUp),
        customPoses = CustomPoses(
            listOf(rock, three),
            mapOf("rock" to DeviceAction.TapCenter, "three" to DeviceAction.DoubleTapCenter),
            PoseSpace.IMAGE,
        ),
        onIgnored = { gesture, reason -> ignored += "${gesture.label}: $reason" },
        onAction = { gesture, _ -> fired += gesture.label },
    )

    private fun show(hand: List<FloatArray>, ms: Long, gesture: String = "None", cx: Float = 0.5f) =
        repeat((ms / FRAME_MS).toInt()) {
            engine.onFrame(HandFrame(now, TestHands.image(hand, cx = cx), gesture, 0.9f, 60f, aspect = 0.75f))
            now += FRAME_MS
        }

    @Test fun quickPoseFiresOnceAfterAMoment() {
        show(TestHands.shape(setOf(1, 4)), 1500)
        assertEquals(listOf("rock"), fired)
    }

    @Test fun holdPoseNeedsAStillSecond() {
        show(TestHands.shape(setOf(1, 2, 3)), 700)
        assertEquals(emptyList<String>(), fired)
        show(TestHands.shape(setOf(1, 2, 3)), 800)
        assertEquals(listOf("three"), fired)
    }

    @Test fun holdPoseDoesNotFireWhileMoving() {
        var x = 0.3f
        repeat(25) {
            show(TestHands.shape(setOf(1, 2, 3)), FRAME_MS, cx = x)
            x += if (it % 2 == 0) 0.08f else -0.08f
        }
        assertEquals(emptyList<String>(), fired)
    }

    @Test fun customPoseBeatsTheBuiltInLabel() {
        // The classifier calls it a thumbs up, but the landmarks clearly show the custom pose.
        show(TestHands.shape(setOf(1, 4)), 1500, gesture = "Thumb_Up")
        assertEquals(listOf("rock"), fired)
    }

    @Test fun builtInStillFiresWhenNoCustomPoseMatches() {
        show(TestHands.fist, 1500, gesture = "Thumb_Up")
        assertEquals(listOf(Gesture.BuiltIn(Motion.THUMBS_UP).label), fired)
    }

    @Test fun poseTheProfileDoesNotUseDoesNotHideTheBuiltIn() {
        // Same hand shape as above, but this profile doesn't bind "rock": the thumbs up fires.
        engine.applyProfile(
            mapOf(Motion.THUMBS_UP to DeviceAction.VolumeUp),
            GestureEngine.Config(),
            CustomPoses(listOf(rock, three), mapOf("three" to DeviceAction.DoubleTapCenter), PoseSpace.IMAGE),
        )
        show(TestHands.shape(setOf(1, 4)), 1500, gesture = "Thumb_Up")
        assertEquals(listOf(Gesture.BuiltIn(Motion.THUMBS_UP).label), fired)
        assertEquals(emptyList<String>(), ignored)
    }

    @Test fun poseBoundToNoneCountsAsUnused() {
        engine.applyProfile(
            mapOf(Motion.THUMBS_UP to DeviceAction.VolumeUp),
            GestureEngine.Config(),
            CustomPoses(listOf(rock), mapOf("rock" to DeviceAction.None), PoseSpace.IMAGE),
        )
        show(TestHands.shape(setOf(1, 4)), 1500, gesture = "Thumb_Up")
        assertEquals(listOf(Gesture.BuiltIn(Motion.THUMBS_UP).label), fired)
    }

    private companion object {
        const val FRAME_MS = 66L
    }
}
