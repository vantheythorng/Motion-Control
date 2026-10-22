package com.kwan.motioncontrol.system

import org.junit.Assert.assertEquals
import org.junit.Test

class InstalledAppsTest {

    private val apps = listOf(
        InstalledApp("com.android.chrome", "Chrome"),
        InstalledApp("com.ss.android.ugc.trill", "TikTok"),
        InstalledApp("com.google.android.youtube", "YouTube"),
    )

    @Test fun emptyQueryReturnsEverything() = assertEquals(apps, InstalledApps.search(apps, "  "))

    @Test fun matchesNameIgnoringCase() =
        assertEquals(listOf(apps[1]), InstalledApps.search(apps, "tik"))

    @Test fun matchesPackageName() =
        assertEquals(listOf(apps[1]), InstalledApps.search(apps, "trill"))
}
