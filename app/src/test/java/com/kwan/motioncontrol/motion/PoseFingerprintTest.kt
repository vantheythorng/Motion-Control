package com.kwan.motioncontrol.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PoseFingerprintTest {

    private fun image(points: List<HandPoint>, left: Boolean = false, aspect: Float = 0.75f) =
        PoseFingerprint.image(points, left, aspect)!!

    private fun world(points: List<HandPoint3>, left: Boolean = false) = PoseFingerprint.world(points, left)!!

    @Test fun imageIgnoresPositionSizeAndTilt() {
        val reference = image(TestHands.image(TestHands.victory))
        val moved = image(TestHands.image(TestHands.victory, cx = 0.3f, cy = 0.4f, size = 0.35f, angle = 0.6f))
        assertEquals(0f, PoseFingerprint.distance(reference, moved), 1e-4f)
    }

    @Test fun imageMeasuresBothAxesInTheSameUnits() {
        val portrait = image(TestHands.image(TestHands.victory, angle = 0.8f, aspect = 0.75f), aspect = 0.75f)
        val landscape = image(TestHands.image(TestHands.victory, angle = 0.8f, aspect = 1.33f), aspect = 1.33f)
        assertEquals(0f, PoseFingerprint.distance(portrait, landscape), 1e-4f)
    }

    @Test fun leftHandMatchesTheRightHand() {
        val right = image(TestHands.image(TestHands.victory))
        val left = image(TestHands.image(TestHands.victory, mirror = true), left = true)
        assertEquals(0f, PoseFingerprint.distance(right, left), 1e-4f)
    }

    @Test fun worldIgnoresTurningTheHandIn3D() {
        val reference = world(TestHands.world(TestHands.victory))
        val turned = world(TestHands.world(TestHands.victory, rx = 0.5f, ry = -0.7f, rz = 1.2f, metres = 0.1f))
        assertEquals(0f, PoseFingerprint.distance(reference, turned), 1e-4f)
    }

    @Test fun worldLeftHandMatchesTheRightHand() {
        val right = world(TestHands.world(TestHands.victory, ry = 0.3f))
        val left = world(TestHands.world(TestHands.victory, ry = -0.3f, mirror = true), left = true)
        assertEquals(0f, PoseFingerprint.distance(right, left), 1e-4f)
    }

    @Test fun differentPosesAreFarApart() {
        assertTrue(PoseFingerprint.distance(image(TestHands.image(TestHands.open)), image(TestHands.image(TestHands.fist))) > 0.4f)
        assertTrue(PoseFingerprint.distance(world(TestHands.world(TestHands.open)), world(TestHands.world(TestHands.victory))) > 0.3f)
    }

    @Test fun aHandWithNoSizeHasNoFingerprint() {
        assertNull(PoseFingerprint.image(List(21) { HandPoint(0.5f, 0.5f) }, false, 1f))
        assertNull(PoseFingerprint.world(List(21) { HandPoint3(0f, 0f, 0f) }, false))
    }
}
