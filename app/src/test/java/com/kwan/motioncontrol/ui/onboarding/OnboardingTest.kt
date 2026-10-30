package com.kwan.motioncontrol.ui.onboarding

import org.junit.Assert.assertEquals
import org.junit.Test

class OnboardingTest {

    private val nothing = SetupStatus(camera = false, notifications = false, accessibility = false, batteryExempt = false)
    private val everything = SetupStatus(camera = true, notifications = true, accessibility = true, batteryExempt = true)

    @Test fun freshInstallGoesThroughEveryStep() {
        val steps = generateSequence(OnboardingStep.WELCOME) { step ->
            if (step == OnboardingStep.DONE) null else Onboarding.next(step, nothing)
        }.toList()
        assertEquals(OnboardingStep.entries, steps)
    }

    @Test fun everythingSetUpGoesStraightToDone() =
        assertEquals(OnboardingStep.DONE, Onboarding.next(OnboardingStep.WELCOME, everything))

    @Test fun skipsWhatIsAlreadySetUp() {
        val status = nothing.copy(camera = true, notifications = true)
        assertEquals(OnboardingStep.ACCESSIBILITY, Onboarding.next(OnboardingStep.WELCOME, status))
    }

    @Test fun skippingAStepMovesOnEvenIfItIsNotSetUp() =
        assertEquals(OnboardingStep.NOTIFICATIONS, Onboarding.next(OnboardingStep.CAMERA, nothing))

    @Test fun doneStaysDone() = assertEquals(OnboardingStep.DONE, Onboarding.next(OnboardingStep.DONE, nothing))
}
