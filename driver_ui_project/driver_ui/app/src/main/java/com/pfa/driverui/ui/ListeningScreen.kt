package com.pfa.driverui.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Overlay of the identification screen, drawn over the blurred 3D car: mic icon + waveform (flat at rest,
 * animated when someone speaks) and a status line. [alpha] lets the caller fade it out after identification.
 */
@Composable
fun ListeningScreen(
    statusMessage: String?,
    audioLevel: Float,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
) {
    Column(
        modifier = modifier.fillMaxSize().alpha(alpha),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Mic,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(64.dp),
        )
        VoiceWaveform(
            level = audioLevel,
            modifier = Modifier.padding(top = 24.dp).width(420.dp),
        )
        Text(
            text = "Please speak to identify yourself",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            modifier = Modifier.padding(top = 24.dp),
        )
        if (statusMessage != null) {
            Text(
                text = statusMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
