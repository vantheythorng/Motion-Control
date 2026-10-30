package com.kwan.motioncontrol.motion

import com.kwan.motioncontrol.data.CustomPose
import com.kwan.motioncontrol.data.PoseSpace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PoseMatcherTest {

    /** Fingerprints of [hand] seen at a few tilts, like a recording with some movement. */
    private fun samples(hand: List<FloatArray>) = listOf(-0.3f, -0.1f, 0f, 0.1f, 0.3f).map { angle ->
        PoseFingerprint.image(TestHands.image(hand, angle = angle), false, 0.75f)!!.toList()
    }

    private fun pose(id: String, hand: List<FloatArray>) = CustomPose(id, id, imageSamples = samples(hand))

    private fun frame(hand: List<FloatArray>) =
        PoseFingerprint.image(TestHands.image(hand, cx = 0.4f, size = 0.3f, angle = 0.2f), false, 0.75f)

    private val matcher = PoseMatcher(listOf(pose("open", TestHands.open), pose("fist", TestHands.fist)), PoseSpace.IMAGE)

    @Test fun eachPoseMatchesItself() {
        assertEquals("open", matcher.match(frame(TestHands.open))?.pose?.id)
        assertEquals("fist", matcher.match(frame(TestHands.fist))?.pose?.id)
    }

    @Test fun anotherShapeMatchesNothing() = assertNull(matcher.match(frame(TestHands.victory)))

    @Test fun twoPosesTooAlikeToTellApartMatchNothing() {
        val twins = PoseMatcher(listOf(pose("a", TestHands.victory), pose("b", TestHands.victory)), PoseSpace.IMAGE)
        assertNull(twins.match(frame(TestHands.victory)))
    }

    @Test fun poseRecordedOnlyInTheOtherSpaceIsIgnored() =
        assertTrue(PoseMatcher(listOf(pose("open", TestHands.open)), PoseSpace.WORLD).isEmpty)

    @Test fun matchScoreIsHighForTheRecordedShape() {
        val score = matcher.match(frame(TestHands.open))!!.score
        assertTrue("score $score", score > 0.9f)
    }

    @Test fun thresholdStaysWithinItsLimits() {
        val one = listOf(FloatArray(40))
        assertEquals(PoseMatcher.MIN_THRESHOLD, PoseMatcher.thresholdFor(one))
        val wild = listOf(FloatArray(40), FloatArray(40) { 5f })
        assertEquals(PoseMatcher.MAX_THRESHOLD, PoseMatcher.thresholdFor(wild))
    }
}
