package com.kwan.motioncontrol.tile

import com.kwan.motioncontrol.data.AppRoute
import com.kwan.motioncontrol.data.shortOffText
import com.kwan.motioncontrol.motion.MotionState

/** What the Quick Settings tile shows. */
data class TileContent(val active: Boolean, val subtitle: String) {
    companion object {
        fun from(state: MotionState, accessibilityEnabled: Boolean): TileContent = when {
            state.running && state.paused -> TileContent(active = true, subtitle = "Paused")
            state.running -> TileContent(
                active = true,
                subtitle = when (val route = state.route) {
                    is AppRoute.Use -> listOfNotNull(route.profile.name, state.appLabel).joinToString(" · ")
                    is AppRoute.Off -> shortOffText(route.reason)
                    null -> "Starting…"
                },
            )
            !accessibilityEnabled -> TileContent(active = false, subtitle = "Set up in app")
            else -> TileContent(active = false, subtitle = "Off")
        }
    }
}
