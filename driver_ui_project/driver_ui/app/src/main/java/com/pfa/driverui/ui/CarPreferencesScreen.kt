package com.pfa.driverui.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pfa.driverui.data.MockPreferencesRepository
import com.pfa.driverui.model.DEFAULT_PREFERENCES

/**
 * Main demo screen: manual driver selector (test-only stand-in for real
 * voice identification) plus animated seat and mirrors reflecting the
 * selected driver's preferences.
 */
@Composable
fun CarPreferencesScreen() {
    var currentDriver by remember { mutableStateOf<String?>(null) }

    val preferences = MockPreferencesRepository.preferencesFor(currentDriver) ?: DEFAULT_PREFERENCES

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(color = MaterialTheme.colorScheme.background)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "DRIVER PREFERENCES",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = currentDriver?.let { "Identified: $it" } ?: "No driver identified",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 2.dp, bottom = 16.dp),
        )

        // Test-only driver selector (replaced by voice identification events later)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MockPreferencesRepository.availableDrivers.forEach { driverName ->
                val selected = currentDriver == driverName
                Button(
                    onClick = { currentDriver = driverName },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    Text(driverName)
                }
            }
            Button(
                onClick = { currentDriver = null },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (currentDriver == null) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (currentDriver == null) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                Text("None")
            }
        }

        DashboardPanel(title = "Seat", modifier = Modifier.padding(top = 20.dp)) {
            SeatView(preferences = preferences.seat)
        }

        DashboardPanel(title = "Mirrors", modifier = Modifier.padding(top = 16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                MirrorView(label = "Left", mirror = preferences.mirrors.gauche)
                MirrorView(label = "Interior", mirror = preferences.mirrors.interieur)
                MirrorView(label = "Right", mirror = preferences.mirrors.droit)
            }
        }
    }
}

/** A rounded dark panel, giving each preference group a "dashboard module" look. */
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
