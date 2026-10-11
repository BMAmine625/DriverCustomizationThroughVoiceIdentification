package com.pfa.driverui.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp as lerpDp
import androidx.compose.ui.unit.sp
import com.pfa.driverui.model.ClimatePreferences
import com.pfa.driverui.ui.theme.CarCool
import com.pfa.driverui.ui.theme.CarGlass
import com.pfa.driverui.ui.theme.CarOnDark
import com.pfa.driverui.ui.theme.CarOutline
import com.pfa.driverui.ui.theme.CarTeal
import com.pfa.driverui.ui.theme.CarWarm
import kotlin.math.roundToInt

private const val TEMP_MIN = 16f
private const val TEMP_MAX = 30f
private const val FAN_MAX = 7

/**
 * Permanent climate band: temperature with a cold/warm button pair (big number, colour follows the temperature)
 * and a segmented fan bar (filled segments = fan level, dots = unused levels).
 * When [editable] is false it only displays the values (the buttons are hidden, the segments are not tappable).
 */
@Composable
fun ClimateBand(
    climate: ClimatePreferences,
    editable: Boolean,
    onChange: (ClimatePreferences) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = CarGlass,
        shape = RoundedCornerShape(30.dp),
        border = BorderStroke(1.dp, CarOutline.copy(alpha = 0.7f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 30.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(36.dp),
        ) {
            TemperatureControl(
                temperature = climate.temperatureCelsius,
                editable = editable,
                onChange = { onChange(climate.copy(temperatureCelsius = it)) },
            )
            Box(
                Modifier
                    .width(1.dp)
                    .height(42.dp)
                    .background(CarOutline.copy(alpha = 0.7f)),
            )
            FanControl(
                speed = climate.vitesseVentilation,
                editable = editable,
                onChange = { onChange(climate.copy(vitesseVentilation = it)) },
            )
        }
    }
}

@Composable
private fun TemperatureControl(temperature: Float, editable: Boolean, onChange: (Float) -> Unit) {
    val animated by animateFloatAsState(temperature, tween(350), label = "temperature")
    val tint = lerpColor(CarCool, CarWarm, ((animated - TEMP_MIN) / (TEMP_MAX - TEMP_MIN)).coerceIn(0f, 1f))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        RoundArrow(Icons.Filled.ChevronLeft, CarCool, enabled = temperature > TEMP_MIN, visible = editable) {
            onChange((temperature - 1f).coerceAtLeast(TEMP_MIN))
        }
        Row(
            modifier = Modifier.width(100.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = temperature.roundToInt().toString(),
                fontSize = 46.sp,
                fontWeight = FontWeight.Light,
                color = tint,
            )
            Text(
                text = "°C",
                fontSize = 16.sp,
                color = tint.copy(alpha = 0.8f),
                modifier = Modifier.padding(start = 2.dp, top = 9.dp),
            )
        }
        RoundArrow(Icons.Filled.ChevronRight, CarWarm, enabled = temperature < TEMP_MAX, visible = editable) {
            onChange((temperature + 1f).coerceAtMost(TEMP_MAX))
        }
    }
}

@Composable
private fun RoundArrow(
    icon: ImageVector,
    color: Color,
    enabled: Boolean,
    visible: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .alpha(if (visible) 1f else 0f)
            .clip(CircleShape)
            .background(color.copy(alpha = if (enabled) 0.22f else 0.08f))
            .clickable(enabled = enabled && visible, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color.copy(alpha = if (enabled) 1f else 0.35f),
            modifier = Modifier.size(30.dp),
        )
    }
}

@Composable
private fun FanControl(speed: Float, editable: Boolean, onChange: (Float) -> Unit) {
    val level = speed.roundToInt().coerceIn(1, FAN_MAX)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Filled.Air, contentDescription = "Fan", tint = CarTeal, modifier = Modifier.size(28.dp))
        Text("MIN", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = CarOnDark.copy(alpha = 0.55f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            for (i in 1..FAN_MAX) {
                FanSegment(filled = i <= level, enabled = editable) { onChange(i.toFloat()) }
            }
        }
        Text("MAX", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = CarOnDark.copy(alpha = 0.55f))
    }
}

@Composable
private fun FanSegment(filled: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val f by animateFloatAsState(if (filled) 1f else 0f, tween(240), label = "fanSegment")
    Box(
        modifier = Modifier
            .width(36.dp)
            .height(44.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(lerpDp(8.dp, 30.dp, f))
                .height(lerpDp(8.dp, 24.dp, f))
                .clip(RoundedCornerShape(lerpDp(4.dp, 7.dp, f)))
                .background(lerpColor(CarOutline, CarTeal, f)),
        )
    }
}
