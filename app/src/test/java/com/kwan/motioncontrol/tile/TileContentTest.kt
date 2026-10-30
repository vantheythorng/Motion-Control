package com.kwan.motioncontrol.tile

import com.kwan.motioncontrol.data.AppRoute
import com.kwan.motioncontrol.data.DefaultProfiles
import com.kwan.motioncontrol.data.OffReason
import com.kwan.motioncontrol.motion.MotionState
import org.junit.Assert.assertEquals
import org.junit.Test

class TileContentTest {

    private val tiktok = AppRoute.Use(DefaultProfiles.shortVideo, "com.ss.android.ugc.trill", viaDefault = false)

    @Test fun off() = assertEquals(TileContent(false, "Off"), TileContent.from(MotionState(), accessibilityEnabled = true))

    @Test fun setupNeededWhenAccessibilityIsOffAndNotRunning() =
        assertEquals(TileContent(false, "Set up in app"), TileContent.from(MotionState(), accessibilityEnabled = false))

    @Test fun runningShowsProfileAndApp() = assertEquals(
        TileContent(true, "Short video · TikTok"),
        TileContent.from(MotionState(running = true, route = tiktok, appLabel = "TikTok"), true),
    )

    @Test fun runningWithoutAKnownAppLabelShowsTheProfile() = assertEquals(
        TileContent(true, "Short video"),
        TileContent.from(MotionState(running = true, route = tiktok), true),
    )

    @Test fun runningWithTheCameraOffShowsAShortReason() {
        val off = AppRoute.Off(OffReason.NO_PROFILE_FOR_APP, "com.android.chrome")
        assertEquals(TileContent(true, "No profile here"), TileContent.from(MotionState(running = true, route = off), true))
    }

    @Test fun runningButAccessibilityTurnedOffStaysActive() {
        // Tapping must still be able to stop it, so it doesn't fall back to "Set up in app".
        val off = AppRoute.Off(OffReason.ACCESSIBILITY_OFF, null)
        assertEquals(TileContent(true, "Accessibility off"), TileContent.from(MotionState(running = true, route = off), false))
    }

    @Test fun paused() = assertEquals(
        TileContent(true, "Paused"),
        TileContent.from(MotionState(running = true, paused = true, route = tiktok), true),
    )

    @Test fun startingBeforeTheFirstRoute() =
        assertEquals(TileContent(true, "Starting…"), TileContent.from(MotionState(running = true), true))
}
