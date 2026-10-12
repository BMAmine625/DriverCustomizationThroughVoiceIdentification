package com.pfa.driverui.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pfa.driverui.model.DriverPreferences
import com.pfa.driverui.ui.theme.CarGlass
import com.pfa.driverui.ui.theme.CarOnDark
import com.pfa.driverui.ui.theme.CarOutline
import com.pfa.driverui.ui.theme.CarTeal

/**
 * Overlay on top of the sharp 3D car after identification: the driver's name, the "identify another driver" button,
 * and the permanent climate band at the bottom (read-only here, it is edited in the settings).
 */
@Composable
fun CarPreferencesScreen(
    driverName: String,
    preferences: DriverPreferences,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
            color = CarGlass,
            shape = RoundedCornerShape(50),
            border = BorderStroke(1.dp, CarOutline.copy(alpha = 0.7f)),
        ) {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(CarTeal))
                Text(driverName, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = CarOnDark)
                TextButton(onClick = onDone) { Text("Identify another driver", color = CarTeal, fontSize = 13.sp) }
            }
        }

        ClimateBand(
            climate = preferences.climate,
            editable = false,
            onChange = {},
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
        )
    }
}
