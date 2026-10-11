package com.pfa.driverui.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pfa.driverui.model.DEFAULT_PREFERENCES
import com.pfa.driverui.network.ApiClient
import com.pfa.driverui.network.ServerConfig
import com.pfa.driverui.scene.CarScene
import com.pfa.driverui.scene.CarView
import com.pfa.driverui.scene.EDITABLE_VIEWS
import com.pfa.driverui.ui.theme.CarGlass
import com.pfa.driverui.ui.theme.CarOnDark
import com.pfa.driverui.ui.theme.CarOutline
import com.pfa.driverui.ui.theme.CarTeal
import kotlin.math.sqrt

private const val MAX_BLUR_DP = 24f
private const val TRANSITION_SECONDS = 1.9f   // identification -> sharp 3D view, must stay < 2 s
private val PANEL_WIDTH = 330.dp

private fun seg(t: Float, from: Float, to: Float) = ((t - from) / (to - from)).coerceIn(0f, 1f)

/**
 * Root of the app. ONE persistent 3D car is the view of every screen:
 *  - Listening: blurred car + mic/waveform
 *  - Identified (1.9 s): mic fades out, welcome fades in, then welcome fades out while the blur clears
 *  - ShowingPreferences: sharp car + driver name + read-only climate band
 *  - Settings: the profile menu slides in on the left and the car view shrinks to the remaining space. The driver
 *    taps a part of the car (seat, wheel, mirror): the camera glides to it and its controls appear. Climate is
 *    edited in the band at the bottom. Leaving with unsaved changes asks "save?".
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
    val settings = remember(client) { SettingsState(client) }
    val isListening = screen is AppScreen.Listening
    val inSettings = screen is AppScreen.Settings

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

    // Entering the settings: load the profile list, select the identified driver, camera back to the overview.
    LaunchedEffect(inSettings, settings) {
        val s = screen as? AppScreen.Settings
        if (s != null) {
            carView = CarView.OVERVIEW
            settings.open(s.driverName, s.preferences)
        }
    }

    fun exitSettings() {
        val s = screen as? AppScreen.Settings
        carView = CarView.OVERVIEW
        val name = s?.driverName
        screen = if (name != null && name in settings.drivers) {
            AppScreen.ShowingPreferences(name, settings.cache[name] ?: s.preferences ?: DEFAULT_PREFERENCES)
        } else {
            statusMessage = null
            AppScreen.Listening
        }
    }

    val scenePrefs = when (val s = screen) {
        is AppScreen.Identified -> s.preferences
        is AppScreen.ShowingPreferences -> s.preferences
        is AppScreen.Settings -> settings.working
        else -> DEFAULT_PREFERENCES
    }
    val blurDp = when (screen) {
        is AppScreen.Listening -> MAX_BLUR_DP
        is AppScreen.Identified -> MAX_BLUR_DP * (1f - seg(t, 1.2f, 1.9f))
        else -> 0f
    }
    val micAlpha = if (screen is AppScreen.Identified) 1f - seg(t, 0f, 0.4f) else 1f
    val welcomeAlpha = seg(t, 0.2f, 0.8f) * (1f - seg(t, 1.2f, 1.9f))

    // Settings layout animation: the menu gets wider, the car card gets smaller and rounded.
    val panelWidth by animateDpAsState(if (inSettings) PANEL_WIDTH else 0.dp, tween(450, easing = FastOutSlowInEasing), label = "panelWidth")
    val inset by animateDpAsState(if (inSettings) 12.dp else 0.dp, tween(450, easing = FastOutSlowInEasing), label = "inset")
    val radius by animateDpAsState(if (inSettings) 24.dp else 0.dp, tween(450, easing = FastOutSlowInEasing), label = "radius")

    Row(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (panelWidth > 0.dp) {
            Box(modifier = Modifier.width(panelWidth).fillMaxHeight().clipToBounds()) {
                ProfilePanel(
                    state = settings,
                    host = host,
                    onHostSaved = { host = it },
                    onClose = { settings.guard { exitSettings() } },
                    modifier = Modifier
                        .wrapContentWidth(Alignment.Start, unbounded = true)
                        .width(PANEL_WIDTH)
                        .fillMaxHeight(),
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(inset)
                .clip(RoundedCornerShape(radius)),
        ) {
            val showHotspots = inSettings && settings.selected != null && carView == CarView.OVERVIEW
            CarScene(
                preferences = scenePrefs,
                view = carView,
                modifier = Modifier.fillMaxSize(),
                blurDp = blurDp,
                hotspots = if (showHotspots) EDITABLE_VIEWS else emptyList(),
                onHotspotTap = { carView = it },
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
                    onDone = {
                        statusMessage = null
                        carView = CarView.OVERVIEW
                        screen = AppScreen.Listening
                    },
                )
                is AppScreen.Settings -> SettingsSceneOverlay(
                    settings = settings,
                    carView = carView,
                    onViewChange = { carView = it },
                )
            }

            if (!inSettings) {
                Surface(
                    onClick = {
                        val (driver, prefs) = when (val s = screen) {
                            is AppScreen.Identified -> s.driverName to s.preferences
                            is AppScreen.ShowingPreferences -> s.driverName to s.preferences
                            else -> null to null
                        }
                        screen = AppScreen.Settings(driver, prefs)
                    },
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
                    color = CarGlass,
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, CarOutline.copy(alpha = 0.7f)),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Settings",
                        tint = CarOnDark,
                        modifier = Modifier.padding(10.dp),
                    )
                }
            }
        }
    }

    if (inSettings) UnsavedChangesDialog(settings)
}

/** Everything drawn over the shrunken 3D view in the settings mode. */
@Composable
private fun SettingsSceneOverlay(
    settings: SettingsState,
    carView: CarView,
    onViewChange: (CarView) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = settings.selected
    Box(modifier = modifier.fillMaxSize()) {
        // top-left: what to do (overview) or the way back (component view)
        if (carView == CarView.OVERVIEW) {
            val hint = when {
                selected == null -> "Pick a profile on the left"
                else -> "Editing $selected · tap the seat, the wheel or a mirror"
            }
            Surface(
                modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
                color = CarGlass,
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, CarOutline.copy(alpha = 0.7f)),
            ) {
                Text(
                    text = hint,
                    color = CarOnDark,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                )
            }
        } else {
            Surface(
                onClick = { settings.guard { onViewChange(CarView.OVERVIEW) } },
                modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
                color = CarGlass,
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, CarOutline.copy(alpha = 0.7f)),
            ) {
                Row(
                    modifier = Modifier.padding(start = 10.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = null, tint = CarTeal)
                    Text("Back to car", color = CarOnDark, fontSize = 14.sp)
                }
            }
        }

        // right: the controls of the part the camera looks at
        if (carView != CarView.OVERVIEW && selected != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 16.dp, end = 16.dp, bottom = 104.dp),
            ) {
                ComponentEditorPanel(
                    view = carView,
                    prefs = settings.working,
                    onChange = { settings.working = it },
                    onBack = { settings.guard { onViewChange(CarView.OVERVIEW) } },
                    modifier = Modifier.width(300.dp),
                )
            }
        }

        // bottom: climate band, editable once a profile is selected
        if (selected != null) {
            ClimateBand(
                climate = settings.working.climate,
                editable = true,
                onChange = { settings.working = settings.working.copy(climate = it) },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
            )
        }
    }
}
