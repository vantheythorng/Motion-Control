package com.kwan.motioncontrol.motion

import com.kwan.motioncontrol.data.CustomPose
import com.kwan.motioncontrol.data.Motion
import com.kwan.motioncontrol.data.PoseSpace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PoseRecorderTest {

    private val rock = TestHands.shape(setOf(1, 4))
    private var now = 0L

    private fun frame(hand: List<FloatArray>, angle: Float = 0f, gesture: String = "None", score: Float = 0.9f) =
        HandFrame(
            timestampMs = now++,
            landmarks = TestHands.image(hand, angle = angle),
            gesture = gesture,
            gestureScore = score,
            world = TestHands.world(hand),
            aspect = 0.75f,
        )

    @Test fun collectsTwoRoundsOfSteadyFrames() {
        val recorder = PoseRecorder(perRound = 5)
        recorder.startRound()
        var completed = false
        repeat(10) { completed = recorder.onFrame(frame(rock)) || completed }
        assertTrue(completed)
        assertEquals(5, recorder.imageSamples.size)
        assertFalse(recorder.done)

        recorder.startRound()
        repeat(10) { recorder.onFrame(frame(rock)) }
        assertTrue(recorder.done)
        assertEquals(10, recorder.imageSamples.size)
        assertEquals(10, recorder.worldSamples.size)
    }

    @Test fun ignoresFramesBeforeARoundStarts() {
        val recorder = PoseRecorder(perRound = 5)
        repeat(10) { recorder.onFrame(frame(rock)) }
        assertEquals(0, recorder.imageSamples.size)
    }

    @Test fun skipsFramesWhileTheHandChangesShape() {
        val recorder = PoseRecorder(perRound = 5)
        recorder.startRound()
        // Alternating shapes: no frame is steady relative to the previous one.
        repeat(10) { i -> recorder.onFrame(frame(if (i % 2 == 0) rock else TestHands.open)) }
        assertEquals(0, recorder.imageSamples.size)
    }

    @Test fun skipsFramesWithoutWorldLandmarks() {
        val recorder = PoseRecorder(perRound = 5)
        recorder.startRound()
        repeat(10) { recorder.onFrame(frame(rock).copy(world = null)) }
        assertEquals(0, recorder.imageSamples.size)
    }

    @Test fun noticesWhenTheClassifierSeesABuiltInPose() {
        val recorder = PoseRecorder(perRound = 5, rounds = 1)
        recorder.startRound()
        repeat(10) { recorder.onFrame(frame(rock, gesture = "Thumb_Down")) }
        assertEquals(Motion.THUMBS_DOWN, recorder.mostlyBuiltIn())
    }

    @Test fun countsAnOpenPalmShapeAsTheBuiltInPalm() {
        // The classifier sees nothing, but the engine's own shape check sees an open palm.
        val recorder = PoseRecorder(perRound = 5, rounds = 1)
        recorder.startRound()
        repeat(10) { recorder.onFrame(frame(TestHands.open)) }
        assertEquals(Motion.OPEN_PALM_HOLD, recorder.mostlyBuiltIn())
    }

    @Test fun ignoresUnsureClassifierLabels() {
        val recorder = PoseRecorder(perRound = 5, rounds = 1)
        recorder.startRound()
        repeat(10) { recorder.onFrame(frame(rock, gesture = "Thumb_Down", score = 0.4f)) }
        assertNull(recorder.mostlyBuiltIn())
    }

    @Test fun findsExistingPosesTheNewOneWouldBeMistakenFor() {
        val sample = PoseFingerprint.image(TestHands.image(rock), false, 0.75f)!!.toList()
        val existing = CustomPose("old", "Old rock", imageSamples = listOf(sample))
        val different = CustomPose(
            "open", "Open",
            imageSamples = listOf(PoseFingerprint.image(TestHands.image(TestHands.open), false, 0.75f)!!.toList()),
        )
        assertEquals(listOf(existing), PoseRecorder.similarPoses(List(5) { sample }, listOf(existing, different), PoseSpace.IMAGE))
        assertEquals(emptyList<CustomPose>(), PoseRecorder.similarPoses(List(5) { sample }, listOf(different), PoseSpace.IMAGE))
    }
}
