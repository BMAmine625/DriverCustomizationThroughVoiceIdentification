package com.pfa.driverui.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pfa.driverui.model.SteeringWheelPreferences
import com.pfa.driverui.ui.theme.CarElectricBlue
import com.pfa.driverui.ui.theme.CarOutline
import com.pfa.driverui.ui.theme.CarSurfaceVariant

/**
 * Steering wheel adjustment: a wheel icon moving within a small
 * bounded track — hauteur (%) controls vertical position, profondeur
 * (%, telescope in/out) controls horizontal position.
 */
@Composable
fun SteeringWheelView(preferences: SteeringWheelPreferences, modifier: Modifier = Modifier) {
    val trackWidth = 70.dp
    val trackHeight = 60.dp
    val wheelSize = 34.dp
    val maxOffsetX = (trackWidth - wheelSize) / 2f
    val maxOffsetY = (trackHeight - wheelSize) / 2f

    val xFraction = (preferences.profondeur / 100f - 0.5f) * 2f
    val yFraction = (0.5f - preferences.hauteur / 100f) * 2f

    val offsetX by animateFloatAsState(
        targetValue = xFraction * maxOffsetX.value,
        animationSpec = tween(durationMillis = 500),
        label = "wheelOffsetX",
    )
    val offsetY by animateFloatAsState(
        targetValue = yFraction * maxOffsetY.value,
        animationSpec = tween(durationMillis = 500),
        label = "wheelOffsetY",
    )

    Box(
        modifier = modifier
            .width(trackWidth)
            .height(trackHeight)
            .background(CarSurfaceVariant, RoundedCornerShape(8.dp))
            .border(1.dp, CarOutline, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .offset(x = offsetX.dp, y = offsetY.dp)
                .size(wheelSize)
                .border(4.dp, CarElectricBlue, CircleShape),
        )
    }
}
