package com.kwan.motioncontrol.ui.onboarding

/** First-run steps, in order (SPEC §7). */
enum class OnboardingStep { WELCOME, CAMERA, NOTIFICATIONS, ACCESSIBILITY, BATTERY, DONE }

/** What is already set up. [notifications] is true where no permission is needed (Android 12 and below). */
data class SetupStatus(
    val camera: Boolean,
    val notifications: Boolean,
    val accessibility: Boolean,
    val batteryExempt: Boolean,
)

object Onboarding {

    /** Whether [step] needs nothing more from the user; Welcome and Done always show. */
    fun isSatisfied(step: OnboardingStep, status: SetupStatus): Boolean = when (step) {
        OnboardingStep.CAMERA -> status.camera
        OnboardingStep.NOTIFICATIONS -> status.notifications
        OnboardingStep.ACCESSIBILITY -> status.accessibility
        OnboardingStep.BATTERY -> status.batteryExempt
        OnboardingStep.WELCOME, OnboardingStep.DONE -> false
    }

    /** The step after [step], skipping what's already set up. */
    fun next(step: OnboardingStep, status: SetupStatus): OnboardingStep =
        OnboardingStep.entries.drop(step.ordinal + 1)
            .firstOrNull { it == OnboardingStep.DONE || !isSatisfied(it, status) }
            ?: OnboardingStep.DONE
}
