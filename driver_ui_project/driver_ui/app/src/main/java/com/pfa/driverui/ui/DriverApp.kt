package com.pfa.driverui.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.pfa.driverui.model.DEFAULT_PREFERENCES
import com.pfa.driverui.network.ApiClient
import com.pfa.driverui.network.ServerConfig
import kotlinx.coroutines.delay

/**
 * Root of the app: owns the current screen, the WebSocket connection
 * (open only while on the Listening screen), and overlays a settings
 * gear button in the top-right corner on every screen except Settings
 * itself.
 */
@Composable
fun DriverApp() {
    val context = LocalContext.current
    var host by remember { mutableStateOf(ServerConfig.getHost(context)) }
    var screen by remember { mutableStateOf<AppScreen>(AppScreen.Listening) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val client = remember(host) { ApiClient(host) }
    val isListening = screen is AppScreen.Listening

    // Connexion WebSocket ouverte uniquement pendant l'écoute — fermée
    // dès qu'on quitte cet écran (préférences affichées, réglages).
    DisposableEffect(host, isListening) {
        val socket = if (isListening) {
            client.connectIdentifyWebSocket(
                onEvent = { event ->
                    if (event.speaker == "INCONNU") {
                        statusMessage = "Not recognized, please try again"
                    } else {
                        statusMessage = null
                        screen = AppScreen.Identified(event.speaker, event.preferences ?: DEFAULT_PREFERENCES)
                    }
                },
                onFailure = { message -> statusMessage = "Connection error: $message" },
            )
        } else {
            null
        }
        onDispose { socket?.close(1000, "leaving listening screen") }
    }

    // Transition automatique : bref écran "Identified" puis affichage
    // des préférences (déjà reçues dans l'événement WebSocket).
    LaunchedEffect(screen) {
        val current = screen
        if (current is AppScreen.Identified) {
            delay(1200)
            screen = AppScreen.ShowingPreferences(current.driverName, current.preferences)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val current = screen) {
            is AppScreen.Listening -> ListeningScreen(statusMessage = statusMessage)
            is AppScreen.Identified -> IdentifiedScreen(driverName = current.driverName)
            is AppScreen.ShowingPreferences -> CarPreferencesScreen(
                driverName = current.driverName,
                preferences = current.preferences,
                onDone = {
                    statusMessage = null
                    screen = AppScreen.Listening
                },
            )
            is AppScreen.Settings -> SettingsScreen(onBack = { screen = AppScreen.Listening })
        }

        if (screen !is AppScreen.Settings) {
            IconButton(
                onClick = { screen = AppScreen.Settings },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
    }
}
