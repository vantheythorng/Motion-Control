package com.kwan.motioncontrol.system

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityStatusTest {

    private val pkg = "com.kwan.motioncontrol"
    private val cls = "com.kwan.motioncontrol.accessibility.AirScrollAccessibilityService"
    private fun enabled(setting: String?) = AccessibilityStatus.isEnabledIn(setting, pkg, cls)

    @Test fun fullForm() = assertTrue(enabled("$pkg/$cls"))

    @Test fun shortForm() = assertTrue(enabled("$pkg/.accessibility.AirScrollAccessibilityService"))

    @Test fun amongOtherServices() =
        assertTrue(enabled("com.other/.Svc:$pkg/$cls:com.third/com.third.Svc"))

    @Test fun otherServicesOnly() = assertFalse(enabled("com.other/.Svc:com.kwan.mygesture/$cls"))

    @Test fun emptyOrNull() {
        assertFalse(enabled(""))
        assertFalse(enabled(null))
    }

    @Test fun samePackageDifferentService() = assertFalse(enabled("$pkg/.accessibility.SomethingElse"))
}
