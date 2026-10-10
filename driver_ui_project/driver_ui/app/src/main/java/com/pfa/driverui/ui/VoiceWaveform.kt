package com.pfa.driverui.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Waveform: a flat row of small dots at rest, animated bars when [level] (0..1) is high.
 * [level] comes from the server ({"type":"level","rms":..}).
 */
@Composable
fun VoiceWaveform(
    level: Float,
    modifier: Modifier = Modifier,
    bars: Int = 31,
    color: Color = Color(0xFFE0E0E0),
    barWidth: Dp = 4.dp,
    maxHeight: Dp = 72.dp,
) {
    val smooth by animateFloatAsState(
        targetValue = level.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 90, easing = LinearEasing),
        label = "level",
    )
    val infinite = rememberInfiniteTransition(label = "wave")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "phase",
    )
    Canvas(modifier.fillMaxWidth().height(maxHeight)) {
        val w = barWidth.toPx()
        val gap = (size.width - bars * w) / (bars - 1).coerceAtLeast(1)
        val mid = size.height / 2f
        val center = (bars - 1) / 2f
        for (i in 0 until bars) {
            val x = i * (w + gap)
            val env = 1f - abs(i - center) / center * 0.65f
            val wobble = 0.5f + 0.5f * sin(phase + i * 0.55f)
            val h = w + (size.height - w) * smooth * env * (0.35f + 0.65f * wobble)
            drawRoundRect(
                color = color,
                topLeft = Offset(x, mid - h / 2f),
                size = Size(w, h),
                cornerRadius = CornerRadius(w / 2f),
            )
        }
    }
}
