package com.kwan.motioncontrol.data

/** Everything needed to pick a profile for the open app (see [ProfileRepository.routing]). */
data class Routing(
    val profiles: List<Profile>,
    val defaultProfile: Profile,
    /** True: apps without their own profile get no profile (camera off). */
    val onlyInSelectedApps: Boolean,
)

/** What motion control does for the open app. */
sealed interface AppRoute {
    val app: String?

    /** Use [profile]; [viaDefault] when the app has no profile of its own. */
    data class Use(val profile: Profile, override val app: String?, val viaDefault: Boolean) : AppRoute

    /** Camera off. */
    data class Off(val reason: OffReason, override val app: String?) : AppRoute
}

enum class OffReason {
    /** Without it the open app is unknown and no action can be performed. */
    ACCESSIBILITY_OFF,
    /** No app opened since motion control (or the app) started. */
    NO_APP_YET,
    NO_PROFILE_FOR_APP,
}

object ProfileResolver {

    fun resolve(app: String?, accessibilityOn: Boolean, routing: Routing): AppRoute {
        if (!accessibilityOn) return AppRoute.Off(OffReason.ACCESSIBILITY_OFF, app)
        if (app != null) {
            routing.profiles.firstOrNull { app in it.packages }?.let {
                return AppRoute.Use(it, app, viaDefault = false)
            }
        }
        if (!routing.onlyInSelectedApps) return AppRoute.Use(routing.defaultProfile, app, viaDefault = true)
        return AppRoute.Off(if (app == null) OffReason.NO_APP_YET else OffReason.NO_PROFILE_FOR_APP, app)
    }
}

/** User-facing reason the camera is off, shared by the notification and Home. */
fun cameraOffText(reason: OffReason, appLabel: String?): String = when (reason) {
    OffReason.ACCESSIBILITY_OFF -> "Camera off: the accessibility service is off."
    OffReason.NO_APP_YET -> "Camera off until you open an app with a profile."
    OffReason.NO_PROFILE_FOR_APP -> "Camera off: no profile for ${appLabel ?: "this app"}."
}
