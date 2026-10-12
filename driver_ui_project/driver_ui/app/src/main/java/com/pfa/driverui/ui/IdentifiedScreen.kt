package com.pfa.driverui.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color

/** Welcome text drawn over the (still blurred) 3D car; [alpha] is driven by the transition timeline. */
@Composable
fun IdentifiedScreen(driverName: String, modifier: Modifier = Modifier, alpha: Float = 1f) {
    Column(
        modifier = modifier.fillMaxSize().alpha(alpha),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Welcome, $driverName",
            style = MaterialTheme.typography.displayMedium,
            color = Color.White,
        )
    }
}
