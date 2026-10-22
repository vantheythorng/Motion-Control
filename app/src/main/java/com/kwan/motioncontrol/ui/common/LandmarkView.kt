package com.kwan.motioncontrol.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kwan.motioncontrol.motion.HandPoint

private val HAND_CONNECTIONS = listOf(
    0 to 1, 1 to 2, 2 to 3, 3 to 4,
    0 to 5, 5 to 6, 6 to 7, 7 to 8,
    5 to 9, 9 to 10, 10 to 11, 11 to 12,
    9 to 13, 13 to 14, 14 to 15, 15 to 16,
    13 to 17, 0 to 17, 17 to 18, 18 to 19, 19 to 20,
)

/**
 * Draws hand landmarks in the camera frame's coordinate space (portrait, mirrored). With
 * [transparent] it has no background, for drawing over a camera preview.
 */
@Composable
fun LandmarkView(landmarks: List<HandPoint>?, modifier: Modifier = Modifier, transparent: Boolean = false) {
    val bone = MaterialTheme.colorScheme.primary
    val joint = MaterialTheme.colorScheme.tertiary
    val background = if (transparent) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant
    Canvas(
        modifier
            .aspectRatio(3f / 4f)
            .background(background)
    ) {
        val points = landmarks ?: return@Canvas
        fun HandPoint.offset() = Offset(x * size.width, y * size.height)
        HAND_CONNECTIONS.forEach { (a, b) ->
            drawLine(bone, points[a].offset(), points[b].offset(), strokeWidth = 3.dp.toPx())
        }
        points.forEach { drawCircle(joint, radius = 4.dp.toPx(), center = it.offset()) }
    }
}
