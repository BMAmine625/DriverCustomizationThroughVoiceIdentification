package com.pfa.driverui.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pfa.driverui.model.DEFAULT_PREFERENCES
import com.pfa.driverui.network.ApiClient
import com.pfa.driverui.network.ServerConfig
import com.pfa.driverui.scene.CarScene
import com.pfa.driverui.scene.CarView
import kotlin.math.sqrt

private const val MAX_BLUR_DP = 24f
private const val TRANSITION_SECONDS = 1.9f   // identification -> sharp 3D view, must stay < 2 s

private fun seg(t: Float, from: Float, to: Float) = ((t - from) / (to - from)).coerceIn(0f, 1f)

/**
 * Root of the app. One persistent 3D car is the background of every screen:
 *  - Listening: blurred car + mic/waveform
 *  - Identified (1.9 s): mic fades out, welcome fades in, then welcome fades out while the blur clears
 *  - ShowingPreferences: sharp car, camera views, climate band
 *  - Settings: opaque screen on top (the 3D scene stays loaded underneath)
 */
@Composable
fun DriverApp() {
    val context = LocalContext.current
    var host by remember { mutableStateOf(ServerConfig.getHost(context)) }
    var screen by remember { mutableStateOf<AppScreen>(AppScreen.Listening) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var audioLevel by remember { mutableFloatStateOf(0f) }
    var carView by remember { mutableStateOf(CarView.OVERVIEW) }

    val client = remember(host) { ApiClient(host) }
    val isListening = screen is AppScreen.Listening

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
                onLevel = { rms, _ -> audioLevel = sqrt((rms * 8f).coerceIn(0f, 1f)) },
            )
        } else {
            audioLevel = 0f
            null
        }
        onDispose { socket?.close(1000, "leaving listening screen") }
    }

    // Transition timeline (seconds): 0-0.4 mic out | 0.2-0.8 welcome in | 1.2-1.9 welcome out + blur clears
    val timeline = remember { Animatable(0f) }
    LaunchedEffect(screen) {
        val current = screen
        if (current is AppScreen.Identified) {
            carView = CarView.OVERVIEW
            timeline.snapTo(0f)
            timeline.animateTo(TRANSITION_SECONDS, tween((TRANSITION_SECONDS * 1000).toInt(), easing = LinearEasing))
            screen = AppScreen.ShowingPreferences(current.driverName, current.preferences)
        }
    }
    val t = timeline.value

    val scenePrefs = when (val s = screen) {
        is AppScreen.Identified -> s.preferences
        is AppScreen.ShowingPreferences -> s.preferences
        else -> DEFAULT_PREFERENCES
    }
    val blurDp = when (screen) {
        is AppScreen.Listening -> MAX_BLUR_DP
        is AppScreen.Identified -> MAX_BLUR_DP * (1f - seg(t, 1.2f, 1.9f))
        else -> 0f
    }
    val micAlpha = if (screen is AppScreen.Identified) 1f - seg(t, 0f, 0.4f) else 1f
    val welcomeAlpha = seg(t, 0.2f, 0.8f) * (1f - seg(t, 1.2f, 1.9f))

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        CarScene(
            preferences = scenePrefs,
            view = carView,
            modifier = Modifier.fillMaxSize().blur(blurDp.dp),
        )

        when (val current = screen) {
            is AppScreen.Listening -> ListeningScreen(statusMessage = statusMessage, audioLevel = audioLevel)
            is AppScreen.Identified -> {
                ListeningScreen(statusMessage = null, audioLevel = 0f, alpha = micAlpha)
                IdentifiedScreen(driverName = current.driverName, alpha = welcomeAlpha)
            }
            is AppScreen.ShowingPreferences -> CarPreferencesScreen(
                driverName = current.driverName,
                preferences = current.preferences,
                view = carView,
                onViewChange = { carView = it },
                onDone = {
                    statusMessage = null
                    carView = CarView.OVERVIEW
                    screen = AppScreen.Listening
                },
            )
            is AppScreen.Settings -> Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                SettingsScreen(onBack = { screen = AppScreen.Listening })
            }
        }

        if (screen !is AppScreen.Settings) {
            IconButton(
                onClick = { screen = AppScreen.Settings },
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
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
