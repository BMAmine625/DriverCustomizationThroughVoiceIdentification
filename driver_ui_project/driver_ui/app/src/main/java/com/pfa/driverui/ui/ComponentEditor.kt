package com.pfa.driverui.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pfa.driverui.model.DriverPreferences
import com.pfa.driverui.model.MirrorPreferences
import com.pfa.driverui.scene.CarView
import com.pfa.driverui.ui.theme.CarGlass
import com.pfa.driverui.ui.theme.CarOnDark
import com.pfa.driverui.ui.theme.CarOutline
import com.pfa.driverui.ui.theme.CarSurfaceVariant
import com.pfa.driverui.ui.theme.CarTeal
import kotlin.math.roundToInt

/**
 * Floating controls for the part the camera is looking at (seat, wheel, one of the 3 mirrors). Every change is
 * pushed to [onChange] immediately, so the 3D car moves live while the driver adjusts.
 */
@Composable
fun ComponentEditorPanel(
    view: CarView,
    prefs: DriverPreferences,
    onChange: (DriverPreferences) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (title, subtitle) = when (view) {
        CarView.SEAT -> "Seat" to "Position, height and recline"
        CarView.WHEEL -> "Steering wheel" to "Height and depth"
        CarView.MIRROR_LEFT -> "Left mirror" to "Drag the dot to aim"
        CarView.MIRROR_RIGHT -> "Right mirror" to "Drag the dot to aim"
        CarView.MIRROR_INTERIOR -> "Interior mirror" to "Drag the dot to aim"
        CarView.OVERVIEW -> "" to ""
    }

    Surface(
        modifier = modifier,
        color = CarGlass,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, CarOutline.copy(alpha = 0.7f)),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back to car", tint = CarOnDark)
                }
                Column {
                    Text(title, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = CarOnDark)
                    Text(subtitle, fontSize = 12.sp, color = CarOnDark.copy(alpha = 0.6f))
                }
            }

            when (view) {
                CarView.SEAT -> {
                    val s = prefs.seat
                    ValueSlider("Position front / back", "%", 0f..100f, 5f, s.positionAvantArriere) {
                        onChange(prefs.copy(seat = s.copy(positionAvantArriere = it)))
                    }
                    ValueSlider("Height", "%", 0f..100f, 5f, s.hauteur) {
                        onChange(prefs.copy(seat = s.copy(hauteur = it)))
                    }
                    ValueSlider("Backrest recline", "°", 90f..160f, 5f, s.inclinaisonDossier) {
                        onChange(prefs.copy(seat = s.copy(inclinaisonDossier = it)))
                    }
                }
                CarView.WHEEL -> {
                    val w = prefs.steeringWheel
                    ValueSlider("Height", "%", 0f..100f, 5f, w.hauteur) {
                        onChange(prefs.copy(steeringWheel = w.copy(hauteur = it)))
                    }
                    ValueSlider("Depth", "%", 0f..100f, 5f, w.profondeur) {
                        onChange(prefs.copy(steeringWheel = w.copy(profondeur = it)))
                    }
                }
                CarView.MIRROR_LEFT -> MirrorEditor(prefs.mirrors.gauche) {
                    onChange(prefs.copy(mirrors = prefs.mirrors.copy(gauche = it)))
                }
                CarView.MIRROR_RIGHT -> MirrorEditor(prefs.mirrors.droit) {
                    onChange(prefs.copy(mirrors = prefs.mirrors.copy(droit = it)))
                }
                CarView.MIRROR_INTERIOR -> MirrorEditor(prefs.mirrors.interieur) {
                    onChange(prefs.copy(mirrors = prefs.mirrors.copy(interieur = it)))
                }
                CarView.OVERVIEW -> Unit
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------

private const val H_MAX = 30f   // mirror horizontal, degrees
private const val V_MAX = 20f   // mirror vertical, degrees

@Composable
private fun MirrorEditor(mirror: MirrorPreferences, onChange: (MirrorPreferences) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AdjustPad(
            horizontal = mirror.horizontal,
            vertical = mirror.vertical,
            onChange = { h, v -> onChange(MirrorPreferences(horizontal = h, vertical = v)) },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Readout("Horizontal", mirror.horizontal)
            Readout("Vertical", mirror.vertical)
        }
        OutlinedButton(
            onClick = { onChange(MirrorPreferences(horizontal = 0f, vertical = 0f)) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Center") }
    }
}

@Composable
private fun Readout(label: String, value: Float) {
    Column {
        Text(label, fontSize = 12.sp, color = CarOnDark.copy(alpha = 0.6f))
        Text("${formatValue(value)}°", fontSize = 20.sp, fontWeight = FontWeight.Medium, color = CarTeal)
    }
}

/** A square pad: the dot position is (horizontal, vertical) in degrees. Drag or tap to aim. Up = positive vertical. */
@Composable
private fun AdjustPad(horizontal: Float, vertical: Float, onChange: (Float, Float) -> Unit) {
    val currentOnChange by rememberUpdatedState(onChange)

    Box(
        modifier = Modifier
            .size(230.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(CarSurfaceVariant)
            .pointerInput("drag") {
                fun update(o: Offset) {
                    val nx = (o.x / size.width).coerceIn(0f, 1f)
                    val ny = (o.y / size.height).coerceIn(0f, 1f)
                    val h = ((nx * 2f - 1f) * H_MAX).roundToInt().toFloat()
                    val v = ((1f - ny * 2f) * V_MAX).roundToInt().toFloat()
                    currentOnChange(h, v)
                }
                detectDragGestures(onDragStart = { update(it) }) { change, _ ->
                    change.consume()
                    update(change.position)
                }
            }
            .pointerInput("tap") {
                detectTapGestures { o ->
                    val nx = (o.x / size.width).coerceIn(0f, 1f)
                    val ny = (o.y / size.height).coerceIn(0f, 1f)
                    currentOnChange(
                        ((nx * 2f - 1f) * H_MAX).roundToInt().toFloat(),
                        ((1f - ny * 2f) * V_MAX).roundToInt().toFloat(),
                    )
                }
            },
    ) {
        Canvas(modifier = Modifier.size(230.dp)) {
            val w = size.width
            val h = size.height
            val line = Color.White.copy(alpha = 0.14f)
            drawLine(line, Offset(w / 2f, 12.dp.toPx()), Offset(w / 2f, h - 12.dp.toPx()), strokeWidth = 1.dp.toPx())
            drawLine(line, Offset(12.dp.toPx(), h / 2f), Offset(w - 12.dp.toPx(), h / 2f), strokeWidth = 1.dp.toPx())
            drawCircle(line, radius = w * 0.32f, center = Offset(w / 2f, h / 2f), style = Stroke(1.dp.toPx()))
            drawCircle(line, radius = w * 0.16f, center = Offset(w / 2f, h / 2f), style = Stroke(1.dp.toPx()))

            val cx = (horizontal / H_MAX * 0.5f + 0.5f).coerceIn(0f, 1f) * w
            val cy = (0.5f - vertical / V_MAX * 0.5f).coerceIn(0f, 1f) * h
            drawCircle(CarTeal.copy(alpha = 0.25f), radius = 26.dp.toPx(), center = Offset(cx, cy))
            drawCircle(CarTeal, radius = 13.dp.toPx(), center = Offset(cx, cy))
            drawCircle(Color.White, radius = 5.dp.toPx(), center = Offset(cx, cy))
        }
    }
}

// ---------------------------------------------------------------------------------------------

@Composable
private fun ValueSlider(
    label: String,
    unit: String,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    value: Float,
    onChange: (Float) -> Unit,
) {
    fun snap(v: Float): Float =
        (((v - range.start) / step).roundToInt() * step + range.start).coerceIn(range.start, range.endInclusive)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, fontSize = 13.sp, color = CarOnDark.copy(alpha = 0.7f))
            Text("${formatValue(value)}$unit", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = CarTeal)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepButton("−") { onChange(snap(value - step)) }
            Slider(
                value = value.coerceIn(range.start, range.endInclusive),
                onValueChange = { onChange(snap(it)) },
                valueRange = range,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
                colors = SliderDefaults.colors(
                    thumbColor = CarTeal,
                    activeTrackColor = CarTeal,
                    inactiveTrackColor = CarOutline,
                ),
            )
            StepButton("+") { onChange(snap(value + step)) }
        }
    }
}

@Composable
private fun StepButton(symbol: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(CarSurfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, fontSize = 22.sp, color = CarOnDark)
    }
}

private fun formatValue(v: Float): String =
    if (v == v.toInt().toFloat()) v.toInt().toString() else "%.1f".format(v)
