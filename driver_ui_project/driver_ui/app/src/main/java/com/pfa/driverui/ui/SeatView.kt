package com.pfa.driverui.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.pfa.driverui.model.SeatPreferences
import com.pfa.driverui.ui.theme.CarElectricBlue
import com.pfa.driverui.ui.theme.CarOutline
import com.pfa.driverui.ui.theme.CarSurfaceVariant

@Composable
fun SeatView(preferences: SeatPreferences, modifier: Modifier = Modifier) {
    val trackWidth = 130.dp
    val trackHalfRangeX = 34.dp
    val heightRange = 22.dp

    val maxVisualTiltDeg = 40f
    val rawFraction = ((preferences.inclinaisonDossier - 90f) / (160f - 90f)).coerceIn(0f, 1f)
    val targetTiltDeg = rawFraction * maxVisualTiltDeg

    val offsetX by animateFloatAsState(
        targetValue = (preferences.positionAvantArriere / 100f - 0.5f) * 2f * trackHalfRangeX.value,
        animationSpec = tween(durationMillis = 500),
        label = "seatOffsetX",
    )
    val offsetY by animateFloatAsState(
        targetValue = (0.5f - preferences.hauteur / 100f) * 2f * heightRange.value,
        animationSpec = tween(durationMillis = 500),
        label = "seatOffsetY",
    )
    val backrestTilt by animateFloatAsState(
        targetValue = targetTiltDeg,
        animationSpec = tween(durationMillis = 500),
        label = "backrestTilt",
    )

    Box(
        modifier = modifier
            .width(trackWidth + trackHalfRangeX * 2)
            .height(120.dp),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .width(trackWidth + trackHalfRangeX)
                .height(3.dp)
                .background(CarOutline, RoundedCornerShape(2.dp)),
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = 2.dp, y = (-2).dp)
        ) {
            Box(
                modifier = Modifier
                    .width(14.dp)
                    .height(46.dp)
                    .background(CarSurfaceVariant, RoundedCornerShape(topStart = 4.dp, topEnd = 10.dp)),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 6.dp, y = 6.dp)
                    .width(20.dp)
                    .height(20.dp)
                    .border(2.dp, CarOutline, CircleShape),
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (34 + offsetX).dp, y = offsetY.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .width(56.dp)
                    .height(14.dp)
                    .background(CarElectricBlue, RoundedCornerShape(5.dp)),
            )

            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = 38.dp, y = (-12).dp)
                    .graphicsLayer {
                        rotationZ = backrestTilt
                        transformOrigin = TransformOrigin(0f, 1f)
                    },
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .width(14.dp)
                        .height(52.dp)
                        .background(CarElectricBlue, RoundedCornerShape(5.dp)),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset(y = (-50).dp)
                        .width(18.dp)
                        .height(14.dp)
                        .background(CarElectricBlue, RoundedCornerShape(5.dp)),
                )
            }
        }
    }
}
