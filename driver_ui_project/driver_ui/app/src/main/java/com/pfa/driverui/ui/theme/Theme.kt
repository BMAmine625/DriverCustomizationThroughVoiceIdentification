package com.pfa.driverui.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Modern-flat automotive palette (Tesla/BMW-style dashboard look):
 * dark charcoal background, electric-blue structural accent, warm
 * amber for moving indicators (mirror aim dot), on dark panels.
 */
val CarBackground = Color(0xFF12151C)
val CarSurface = Color(0xFF1B1F29)
val CarSurfaceVariant = Color(0xFF262B37)
val CarElectricBlue = Color(0xFF3DDCFF)
val CarAmber = Color(0xFFFFA94D)
val CarOnDark = Color(0xFFE7ECF3)
val CarOutline = Color(0xFF3A4150)

private val DriverUiDarkColors = darkColorScheme(
    primary = CarElectricBlue,
    onPrimary = Color(0xFF00232B),
    secondary = CarAmber,
    onSecondary = Color(0xFF3A2200),
    background = CarBackground,
    onBackground = CarOnDark,
    surface = CarSurface,
    onSurface = CarOnDark,
    surfaceVariant = CarSurfaceVariant,
    onSurfaceVariant = CarOnDark,
    outline = CarOutline,
)

@Composable
fun DriverUiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DriverUiDarkColors,
        content = content,
    )
}
