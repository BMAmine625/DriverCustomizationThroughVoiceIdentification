package com.pfa.driverui.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pfa.driverui.model.ClimatePreferences
import com.pfa.driverui.ui.theme.CarAmber
import com.pfa.driverui.ui.theme.CarElectricBlue
import com.pfa.driverui.ui.theme.CarOutline
import com.pfa.driverui.ui.theme.CarSurfaceVariant

/**
 * Climate control: a thermometer-style bar for temperature (16-30°C),
 * and a row of bars for fan speed (1-7 palier).
 */
@Composable
fun ClimateView(preferences: ClimatePreferences, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val fillFraction = ((preferences.temperatureCelsius - 16f) / (30f - 16f)).coerceIn(0f, 1f)
            val animatedFraction by animateFloatAsState(
                targetValue = fillFraction,
                animationSpec = tween(durationMillis = 500),
                label = "tempFraction",
            )
            val barHeight = 60.dp
            Box(
                modifier = Modifier
                    .width(16.dp)
                    .height(barHeight)
                    .background(CarSurfaceVariant, RoundedCornerShape(8.dp))
                    .border(1.dp, CarOutline, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Box(
                    modifier = Modifier
                        .width(16.dp)
                        .height(barHeight * animatedFraction)
                        .background(CarAmber, RoundedCornerShape(8.dp)),
                )
            }
            Text(
                text = "${preferences.temperatureCelsius.toInt()}°C",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                val speed = preferences.vitesseVentilation.toInt().coerceIn(1, 7)
                for (i in 1..7) {
                    Box(
                        modifier = Modifier
                            .width(6.dp)
                            .height((10 + i * 4).dp)
                            .background(
                                if (i <= speed) CarElectricBlue else CarOutline,
                                RoundedCornerShape(2.dp),
                            ),
                    )
                }
            }
            Text(
                text = "Fan ${preferences.vitesseVentilation.toInt()}/7",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
