package com.pfa.driverui.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pfa.driverui.model.DriverPreferences

@Composable
fun CarPreferencesScreen(
    driverName: String,
    preferences: DriverPreferences,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "DRIVER PREFERENCES",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "Identified: $driverName",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 2.dp, bottom = 16.dp),
        )

        DashboardPanel(title = "Seat") {
            SeatView(preferences = preferences.seat)
        }

        DashboardPanel(title = "Steering Wheel", modifier = Modifier.padding(top = 16.dp)) {
            SteeringWheelView(preferences = preferences.steeringWheel)
        }

        DashboardPanel(title = "Mirrors", modifier = Modifier.padding(top = 16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                MirrorView(label = "Left", mirror = preferences.mirrors.gauche)
                MirrorView(label = "Interior", mirror = preferences.mirrors.interieur)
                MirrorView(label = "Right", mirror = preferences.mirrors.droit)
            }
        }

        DashboardPanel(title = "Climate", modifier = Modifier.padding(top = 16.dp, bottom = 16.dp)) {
            ClimateView(preferences = preferences.climate)
        }

        OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text("Identify another driver")
        }
    }
}

@Composable
private fun DashboardPanel(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            content()
        }
    }
}
