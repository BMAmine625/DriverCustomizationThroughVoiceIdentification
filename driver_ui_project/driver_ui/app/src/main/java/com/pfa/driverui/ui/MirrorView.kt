package com.pfa.driverui.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pfa.driverui.model.MirrorPreferences
import com.pfa.driverui.ui.theme.CarAmber
import com.pfa.driverui.ui.theme.CarElectricBlue
import com.pfa.driverui.ui.theme.CarOutline
import com.pfa.driverui.ui.theme.CarSurfaceVariant

/**
 * Mirror representation, restyled to match the modern-flat automotive
 * palette: a bezeled housing with a thin outline, and an amber aim
 * marker moving inside the glass — kept from the earlier version since
 * a moving marker proved far more visible than a subtle 3D tilt at
 * these angle ranges.
 */
@Composable
fun MirrorView(label: String, mirror: MirrorPreferences, modifier: Modifier = Modifier) {
    val glassWidth = 46.dp
    val glassHeight = 30.dp
    val markerSize = 9.dp
    val maxOffsetX = (glassWidth - markerSize) / 2f
    val maxOffsetY = (glassHeight - markerSize) / 2f

    val horizontalFraction = (mirror.horizontal / 30f).coerceIn(-1f, 1f)
    val verticalFraction = (-mirror.vertical / 20f).coerceIn(-1f, 1f)

    val markerX by animateFloatAsState(
        targetValue = horizontalFraction * maxOffsetX.value,
        animationSpec = tween(durationMillis = 500),
        label = "mirrorMarkerX",
    )
    val markerY by animateFloatAsState(
        targetValue = verticalFraction * maxOffsetY.value,
        animationSpec = tween(durationMillis = 500),
        label = "mirrorMarkerY",
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Housing / bezel
        Box(
            modifier = Modifier
                .width(60.dp)
                .height(42.dp)
                .background(CarSurfaceVariant, RoundedCornerShape(10.dp))
                .border(1.dp, CarOutline, RoundedCornerShape(10.dp))
                .padding(7.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Glass
            Box(
                modifier = Modifier
                    .width(glassWidth)
                    .height(glassHeight)
                    .background(CarElectricBlue.copy(alpha = 0.18f), RoundedCornerShape(5.dp))
                    .border(1.dp, CarElectricBlue.copy(alpha = 0.5f), RoundedCornerShape(5.dp)),
            ) {
                // Aim marker
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(x = markerX.dp, y = markerY.dp)
                        .size(markerSize)
                        .background(CarAmber, CircleShape),
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
