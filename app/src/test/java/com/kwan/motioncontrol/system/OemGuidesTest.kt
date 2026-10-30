package com.kwan.motioncontrol.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OemGuidesTest {

    @Test fun xiaomiBrands() {
        assertEquals(OemGuides.xiaomi, OemGuides.forDevice("Xiaomi", "Redmi"))
        assertEquals(OemGuides.xiaomi, OemGuides.forDevice("Xiaomi", "POCO"))
        assertEquals(OemGuides.xiaomi, OemGuides.forDevice("Xiaomi", "xiaomi"))
    }

    @Test fun colorOsBrands() {
        assertEquals(OemGuides.colorOs, OemGuides.forDevice("OPPO", "OPPO"))
        assertEquals(OemGuides.colorOs, OemGuides.forDevice("realme", "realme"))
        assertEquals(OemGuides.colorOs, OemGuides.forDevice("OnePlus", "OnePlus"))
    }

    @Test fun vivoBrands() {
        assertEquals(OemGuides.vivo, OemGuides.forDevice("vivo", "vivo"))
        assertEquals(OemGuides.vivo, OemGuides.forDevice("vivo", "iQOO"))
    }

    @Test fun samsung() = assertEquals(OemGuides.samsung, OemGuides.forDevice("samsung", "samsung"))

    @Test fun otherPhonesGetTheGenericGuide() = assertEquals(OemGuides.other, OemGuides.forDevice("Google", "google"))

    @Test fun everyGuideHasSteps() =
        listOf(OemGuides.xiaomi, OemGuides.colorOs, OemGuides.vivo, OemGuides.samsung, OemGuides.other)
            .forEach { assertTrue(it.brand, it.steps.isNotEmpty()) }
}
