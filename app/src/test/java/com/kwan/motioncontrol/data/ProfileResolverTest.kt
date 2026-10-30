package com.kwan.motioncontrol.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileResolverTest {

    private val tiktok = "com.ss.android.ugc.trill"
    private val chrome = "com.android.chrome"
    /** The user has added TikTok to Short video (the defaults ship without apps). */
    private val shortVideo = DefaultProfiles.shortVideo.copy(packages = setOf(tiktok))
    private fun routing(onlySelected: Boolean = true, profiles: List<Profile> = listOf(shortVideo, DefaultProfiles.global)) =
        Routing(profiles, DefaultProfiles.global, onlySelected)

    @Test fun appWithAProfileUsesIt() = assertEquals(
        AppRoute.Use(shortVideo, tiktok, viaDefault = false),
        ProfileResolver.resolve(tiktok, accessibilityOn = true, routing()),
    )

    @Test fun defaultProfilesShipWithoutApps() {
        DefaultProfiles.all.forEach { assertEquals(emptySet<String>(), it.packages) }
        assertEquals(
            AppRoute.Off(OffReason.NO_PROFILE_FOR_APP, tiktok),
            ProfileResolver.resolve(tiktok, accessibilityOn = true, routing(profiles = DefaultProfiles.all)),
        )
    }

    @Test fun otherAppsGetNoProfileByDefault() = assertEquals(
        AppRoute.Off(OffReason.NO_PROFILE_FOR_APP, chrome),
        ProfileResolver.resolve(chrome, accessibilityOn = true, routing()),
    )

    @Test fun otherAppsUseTheDefaultWhenUsedInAllApps() = assertEquals(
        AppRoute.Use(DefaultProfiles.global, chrome, viaDefault = true),
        ProfileResolver.resolve(chrome, accessibilityOn = true, routing(onlySelected = false)),
    )

    @Test fun noAppYet() = assertEquals(
        AppRoute.Off(OffReason.NO_APP_YET, null),
        ProfileResolver.resolve(null, accessibilityOn = true, routing()),
    )

    @Test fun accessibilityOffTurnsTheCameraOffEvenForProfiledApps() = assertEquals(
        AppRoute.Off(OffReason.ACCESSIBILITY_OFF, tiktok),
        ProfileResolver.resolve(tiktok, accessibilityOn = false, routing(onlySelected = false)),
    )

    @Test fun shortReasonsFitATileSubtitle() =
        OffReason.entries.forEach { assert(shortOffText(it).length <= 20) { shortOffText(it) } }

    @Test fun duplicateAssignmentUsesTheFirstProfile() {
        val other = Profile(id = "other", name = "Other", packages = setOf(tiktok))
        val route = ProfileResolver.resolve(tiktok, true, routing(profiles = listOf(shortVideo, DefaultProfiles.global, other)))
        assertEquals(shortVideo, (route as AppRoute.Use).profile)
    }
}
