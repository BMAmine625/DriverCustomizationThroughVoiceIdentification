package com.pfa.driverui.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pfa.driverui.model.DEFAULT_PREFERENCES
import com.pfa.driverui.model.DriverPreferences
import com.pfa.driverui.network.ApiClient
import com.pfa.driverui.network.EnrollEvent
import com.pfa.driverui.network.ServerConfig

private sealed class SettingsSubScreen {
    object DriverList : SettingsSubScreen()
    data class Enrolling(val driverName: String) : SettingsSubScreen()
    data class Editing(val driverName: String, val preferences: DriverPreferences, val isNew: Boolean) : SettingsSubScreen()
}

/**
 * Settings screen: configure the server address, browse enrolled
 * driver profiles, and add/edit a profile (name, voice enrollment,
 * and preferences for seat/steering wheel/mirrors/climate), with a
 * live visual preview and rename/delete support.
 */
@Composable
fun SettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var host by remember { mutableStateOf(ServerConfig.getHost(context)) }
    var sub by remember { mutableStateOf<SettingsSubScreen>(SettingsSubScreen.DriverList) }
    var drivers by remember { mutableStateOf<List<String>>(emptyList()) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var newDriverName by remember { mutableStateOf("") }

    val client = remember(host) { ApiClient(host) }

    fun refreshDrivers() {
        client.fetchDrivers(
            onResult = { drivers = it },
            onError = { statusMessage = "Could not load drivers: $it" },
        )
    }

    LaunchedEffect(host) { refreshDrivers() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                if (sub is SettingsSubScreen.DriverList) {
                    onBack()
                } else {
                    sub = SettingsSubScreen.DriverList
                }
            }) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
            }
            Text(
                text = "SETTINGS",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        when (val current = sub) {
            is SettingsSubScreen.DriverList -> {
                Spacer(modifier = Modifier.height(16.dp))
                Text("Server address", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        label = { Text("host:port") },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        ServerConfig.setHost(context, host)
                        refreshDrivers()
                        statusMessage = "Server address saved"
                    }) {
                        Text("Save")
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Text("Driver profiles", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

                drivers.forEach { driverName ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.medium,
                        onClick = {
                            client.fetchPreferences(
                                driverName = driverName,
                                onResult = { prefs ->
                                    sub = SettingsSubScreen.Editing(driverName, prefs ?: DEFAULT_PREFERENCES, isNew = false)
                                },
                                onError = { statusMessage = "Could not load preferences: $it" },
                            )
                        },
                    ) {
                        Text(
                            text = driverName,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Text("Add new profile", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(
                        value = newDriverName,
                        onValueChange = { newDriverName = it },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        label = { Text("Driver name") },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            sub = SettingsSubScreen.Enrolling(newDriverName.trim())
                        },
                    ) {
                        Text("Create")
                    }
                }

                statusMessage?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }

            is SettingsSubScreen.Enrolling -> {
                val samplesTarget = 5
                var confirmedName by remember(current.driverName) { mutableStateOf(current.driverName) }
                var started by remember(current.driverName) { mutableStateOf(false) }
                var recordedCount by remember(current.driverName) { mutableStateOf(0) }
                var enrollError by remember(current.driverName) { mutableStateOf<String?>(null) }
                var isDone by remember(current.driverName) { mutableStateOf(false) }

                DisposableEffect(started) {
                    if (!started) {
                        onDispose { }
                    } else {
                        val socket = client.connectEnrollWebSocket(
                            driverName = confirmedName,
                            samples = samplesTarget,
                            onEvent = { event ->
                                when (event) {
                                    is EnrollEvent.SampleRecorded -> recordedCount = event.index
                                    is EnrollEvent.Done -> {
                                        isDone = true
                                        sub = SettingsSubScreen.Editing(confirmedName, DEFAULT_PREFERENCES, isNew = true)
                                    }
                                    is EnrollEvent.Error -> enrollError = event.message
                                }
                            },
                            onFailure = { message -> enrollError = message },
                        )
                        onDispose { if (!isDone) socket.close(1000, "leaving enrolling screen") }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (!started) {
                    Text(
                        text = "New profile",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = confirmedName,
                        onValueChange = { confirmedName = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Driver name") },
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val trimmed = confirmedName.trim()
                            if (trimmed.isNotBlank()) {
                                confirmedName = trimmed
                                started = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Start voice enrollment")
                    }
                } else {
                    Text(
                        text = "Enrolling voice: $confirmedName",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Speak a short phrase, pause, and repeat until all samples are recorded.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "$recordedCount / $samplesTarget samples recorded",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (i in 1..samplesTarget) {
                            Box(
                                modifier = Modifier
                                    .width(28.dp)
                                    .height(8.dp)
                                    .background(
                                        if (i <= recordedCount) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outline,
                                        RoundedCornerShape(4.dp),
                                    ),
                            )
                        }
                    }

                    enrollError?.let { message ->
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = message, color = MaterialTheme.colorScheme.secondary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { sub = SettingsSubScreen.DriverList }) {
                            Text("Back")
                        }
                    }
                }
            }

            is SettingsSubScreen.Editing -> {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (current.isNew) "New profile: ${current.driverName}" else "Editing: ${current.driverName}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )

                var prefs by remember(current.driverName) { mutableStateOf(current.preferences) }
                var renameText by remember(current.driverName) { mutableStateOf(current.driverName) }
                var showDeleteConfirm by remember(current.driverName) { mutableStateOf(false) }

                Spacer(modifier = Modifier.height(12.dp))
                Text("Rename profile", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        label = { Text("Name") },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val trimmed = renameText.trim()
                            if (trimmed.isNotBlank() && trimmed != current.driverName) {
                                client.renameDriver(
                                    oldName = current.driverName,
                                    newName = trimmed,
                                    onSuccess = {
                                        statusMessage = "Renamed to $trimmed"
                                        refreshDrivers()
                                        sub = SettingsSubScreen.Editing(trimmed, prefs, isNew = false)
                                    },
                                    onError = { statusMessage = "Rename failed: $it" },
                                )
                            }
                        },
                    ) {
                        Text("Rename")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Seat", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                SeatView(preferences = prefs.seat)
                NumberField("Position front/back", "%", 0f..100f, 5f, prefs.seat.positionAvantArriere) {
                    prefs = prefs.copy(seat = prefs.seat.copy(positionAvantArriere = it))
                }
                NumberField("Height", "%", 0f..100f, 5f, prefs.seat.hauteur) {
                    prefs = prefs.copy(seat = prefs.seat.copy(hauteur = it))
                }
                NumberField("Backrest recline", "deg", 90f..160f, 5f, prefs.seat.inclinaisonDossier) {
                    prefs = prefs.copy(seat = prefs.seat.copy(inclinaisonDossier = it))
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Steering wheel", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                SteeringWheelView(preferences = prefs.steeringWheel)
                NumberField("Height", "%", 0f..100f, 5f, prefs.steeringWheel.hauteur) {
                    prefs = prefs.copy(steeringWheel = prefs.steeringWheel.copy(hauteur = it))
                }
                NumberField("Depth", "%", 0f..100f, 5f, prefs.steeringWheel.profondeur) {
                    prefs = prefs.copy(steeringWheel = prefs.steeringWheel.copy(profondeur = it))
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Left mirror", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                MirrorView(label = "Left", mirror = prefs.mirrors.gauche)
                NumberField("Horizontal", "deg", -30f..30f, 5f, prefs.mirrors.gauche.horizontal) {
                    prefs = prefs.copy(mirrors = prefs.mirrors.copy(gauche = prefs.mirrors.gauche.copy(horizontal = it)))
                }
                NumberField("Vertical", "deg", -20f..20f, 5f, prefs.mirrors.gauche.vertical) {
                    prefs = prefs.copy(mirrors = prefs.mirrors.copy(gauche = prefs.mirrors.gauche.copy(vertical = it)))
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Right mirror", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                MirrorView(label = "Right", mirror = prefs.mirrors.droit)
                NumberField("Horizontal", "deg", -30f..30f, 5f, prefs.mirrors.droit.horizontal) {
                    prefs = prefs.copy(mirrors = prefs.mirrors.copy(droit = prefs.mirrors.droit.copy(horizontal = it)))
                }
                NumberField("Vertical", "deg", -20f..20f, 5f, prefs.mirrors.droit.vertical) {
                    prefs = prefs.copy(mirrors = prefs.mirrors.copy(droit = prefs.mirrors.droit.copy(vertical = it)))
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Interior mirror", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                MirrorView(label = "Interior", mirror = prefs.mirrors.interieur)
                NumberField("Horizontal", "deg", -30f..30f, 5f, prefs.mirrors.interieur.horizontal) {
                    prefs = prefs.copy(mirrors = prefs.mirrors.copy(interieur = prefs.mirrors.interieur.copy(horizontal = it)))
                }
                NumberField("Vertical", "deg", -20f..20f, 5f, prefs.mirrors.interieur.vertical) {
                    prefs = prefs.copy(mirrors = prefs.mirrors.copy(interieur = prefs.mirrors.interieur.copy(vertical = it)))
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Climate", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                ClimateView(preferences = prefs.climate)
                NumberField("Temperature", "°C", 16f..30f, 1f, prefs.climate.temperatureCelsius) {
                    prefs = prefs.copy(climate = prefs.climate.copy(temperatureCelsius = it))
                }
                NumberField("Fan speed", "palier", 1f..7f, 1f, prefs.climate.vitesseVentilation) {
                    prefs = prefs.copy(climate = prefs.climate.copy(vitesseVentilation = it))
                }

                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = {
                        client.savePreferences(
                            driverName = current.driverName,
                            preferences = prefs,
                            onSuccess = {
                                statusMessage = "Saved"
                                refreshDrivers()
                                sub = SettingsSubScreen.DriverList
                            },
                            onError = { statusMessage = "Save failed: $it" },
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Save profile")
                }

                Spacer(modifier = Modifier.height(24.dp))
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Delete profile")
                }

                if (showDeleteConfirm) {
                    AlertDialog(
                        onDismissRequest = { showDeleteConfirm = false },
                        title = { Text("Delete ${current.driverName}?") },
                        text = { Text("This removes their voice profile, preferences, and recorded samples. This cannot be undone.") },
                        confirmButton = {
                            TextButton(onClick = {
                                showDeleteConfirm = false
                                client.deleteDriver(
                                    driverName = current.driverName,
                                    onSuccess = {
                                        statusMessage = "Deleted ${current.driverName}"
                                        refreshDrivers()
                                        sub = SettingsSubScreen.DriverList
                                    },
                                    onError = { statusMessage = "Delete failed: $it" },
                                )
                            }) {
                                Text("Delete", color = MaterialTheme.colorScheme.error)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDeleteConfirm = false }) {
                                Text("Cancel")
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    unit: String,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    value: Float,
    onValueChange: (Float) -> Unit,
) {
    var text by remember(value) { mutableStateOf(formatNumber(value)) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 6.dp),
    ) {
        IconButton(onClick = { onValueChange((value - step).coerceIn(range)) }) {
            Text("\u2212", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedTextField(
            value = text,
            onValueChange = { newText ->
                text = newText
                newText.toFloatOrNull()?.let { parsed ->
                    onValueChange(parsed.coerceIn(range))
                }
            },
            label = { Text("$label ($unit, ${range.start.toInt()}\u2013${range.endInclusive.toInt()})") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { onValueChange((value + step).coerceIn(range)) }) {
            Text("+", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatNumber(value: Float): String =
    if (value == value.toInt().toFloat()) value.toInt().toString() else value.toString()
