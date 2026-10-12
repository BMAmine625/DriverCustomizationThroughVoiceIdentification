package com.pfa.driverui.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val CarBackground = Color(0xFF1C1D20)
val CarSurface = Color(0xFF26272B)
val CarSurfaceVariant = Color(0xFF303136)
val CarElectricBlue = Color(0xFF3DDCFF)
val CarAmber = Color(0xFFFFA94D)
val CarOnDark = Color(0xFFE7ECF3)
val CarOutline = Color(0xFF45474D)

// Settings / climate accents
val CarTeal = Color(0xFF5CC8B6)      // active segments, sliders, selected profile
val CarCool = Color(0xFF58A6FF)      // temperature "colder" button
val CarWarm = Color(0xFFFF7A59)      // temperature "warmer" button
val CarGlass = Color(0xE0202226)     // translucent dark gray, floating panels over the 3D scene

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
