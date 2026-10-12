package com.pfa.driverui.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pfa.driverui.model.DEFAULT_PREFERENCES
import com.pfa.driverui.model.DriverPreferences
import com.pfa.driverui.scene.CamShot
import com.pfa.driverui.scene.CarTuning
import com.pfa.driverui.scene.CarView
import com.pfa.driverui.scene.EDITABLE_VIEWS
import com.pfa.driverui.scene.V3
import com.pfa.driverui.scene.camBasis
import com.pfa.driverui.scene.depthOf
import com.pfa.driverui.scene.lerpShot
import com.pfa.driverui.scene.projectToScreen
import com.pfa.driverui.scene.unprojectAtDepth
import com.pfa.driverui.ui.theme.CarAmber
import com.pfa.driverui.ui.theme.CarGlass
import com.pfa.driverui.ui.theme.CarOnDark
import com.pfa.driverui.ui.theme.CarOutline
import com.pfa.driverui.ui.theme.CarTeal
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

enum class DebugMode(val label: String) { ORBIT("Orbit"), PAN("Pan"), MARKERS("Move markers") }

/** State of the "Customize view" screen. The camera ([shot]) is owned here and handed to the 3D scene as an override. */
class DebugState {
    var shot by mutableStateOf(CarView.OVERVIEW.shot)
    var prefs by mutableStateOf<DriverPreferences>(DEFAULT_PREFERENCES)
    var activeView by mutableStateOf(CarView.OVERVIEW)
    var mode by mutableStateOf(DebugMode.ORBIT)
    var showMarkers by mutableStateOf(true)
    var showPose by mutableStateOf(false)
    var selectedMarker by mutableStateOf<CarView?>(null)
    var note by mutableStateOf<String?>(null)
    val anchors = mutableStateMapOf<CarView, V3>()
    var glide: Job? = null

    /** Called when the "Customize view" screen opens. */
    fun reset() {
        glide?.cancel()
        shot = CarTuning.shot(CarView.OVERVIEW)
        activeView = CarView.OVERVIEW
        prefs = DEFAULT_PREFERENCES
        mode = DebugMode.ORBIT
        showMarkers = true
        showPose = false
        selectedMarker = null
        note = null
        anchors.clear()
        EDITABLE_VIEWS.forEach { anchors[it] = CarTuning.anchor(it) }
    }

    private fun len(v: V3) = sqrt(v.x * v.x + v.y * v.y + v.z * v.z)

    /** Drag on the scene: the camera turns around its target (drag right = the car turns right). */
    fun orbit(dx: Float, dy: Float) {
        val off = shot.pos - shot.target
        val dist = len(off)
        if (dist < 1e-6f) return
        val yaw = atan2(off.x, off.z) - dx * 0.008f
        val pitch = (asin((off.y / dist).coerceIn(-1f, 1f)) + dy * 0.008f).coerceIn(-1.45f, 1.45f)
        val dir = V3(cos(pitch) * sin(yaw), sin(pitch), cos(pitch) * cos(yaw))
        shot = shot.copy(pos = shot.target + dir * dist)
    }

    /** Drag on the scene in pan mode: camera and target slide together (the scene follows the finger). */
    fun pan(dx: Float, dy: Float, heightPx: Float) {
        if (heightPx <= 0f) return
        val b = camBasis(shot.pos, shot.target)
        val dist = len(shot.pos - shot.target)
        val tanHalf = tan(shot.fovDeg * PI / 360.0).toFloat()
        val s = dist * 2f * tanHalf / heightPx
        val delta = b.u * (dy * s) - b.r * (dx * s)
        shot = shot.copy(pos = shot.pos + delta, target = shot.target + delta)
    }

    /** [factor] < 1 moves the camera closer to its target, > 1 farther. */
    fun zoom(factor: Float) {
        val off = shot.pos - shot.target
        shot = shot.copy(pos = shot.target + off * factor)
    }

    fun setFov(fov: Float) {
        shot = shot.copy(fovDeg = fov)
    }

    /** Moves the selected marker along the line of sight (keeps its place on the screen). */
    fun moveMarkerDepth(factor: Float) {
        val v = selectedMarker ?: return
        val a = anchors[v] ?: return
        val eye = shot.pos
        anchors[v] = eye + (a - eye) * factor
    }
}

private fun fmt(v: Float) = String.format(Locale.US, "%.5f", v)

private fun describe(s: CamShot) =
    "CamShot(V3(${fmt(s.pos.x)}f, ${fmt(s.pos.y)}f, ${fmt(s.pos.z)}f), " +
        "V3(${fmt(s.target.x)}f, ${fmt(s.target.y)}f, ${fmt(s.target.z)}f), ${s.fovDeg.roundToInt()}f)"

private fun copyText(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("car tuning", text))
    Log.d("CarTuning", "\n$text")   // also in Logcat (filter: CarTuning)
}

/**
 * "Customize view" tool (formerly the debug tool): no identification, the 3D car with every view, a free camera and movable tap markers.
 * Tuned values can be saved on the device (they then apply to the real app) and copied as Kotlin.
 */
@Composable
fun DebugScreen(debug: DebugState, onExit: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var toolsOpen by remember { mutableStateOf(true) }

    fun glideTo(view: CarView) {
        debug.activeView = view
        debug.glide?.cancel()
        val from = debug.shot
        val to = CarTuning.shot(view)
        debug.glide = scope.launch {
            animate(0f, 1f, animationSpec = tween(1200, easing = FastOutSlowInEasing)) { t, _ ->
                debug.shot = lerpShot(from, to, t)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        DebugGestureLayer(debug)

        // top: views + drag mode
        Surface(
            modifier = Modifier.align(Alignment.TopStart).padding(12.dp).fillMaxWidth(0.82f),
            color = CarGlass,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, CarOutline.copy(alpha = 0.7f)),
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("CUSTOMIZE VIEW", color = CarAmber, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    CarView.entries.forEach { v ->
                        FilterChip(
                            selected = debug.activeView == v,
                            onClick = { glideTo(v) },
                            label = { Text(v.label) },
                        )
                    }
                }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("drag the scene to", color = CarOnDark.copy(alpha = 0.7f), fontSize = 12.sp)
                    DebugMode.entries.forEach { m ->
                        FilterChip(
                            selected = debug.mode == m,
                            onClick = { debug.mode = m },
                            label = { Text(m.label) },
                        )
                    }
                }
            }
        }

        Button(
            onClick = onExit,
            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CarAmber, contentColor = Color(0xFF3A2200)),
        ) { Text("Exit customize view") }

        // pose controls of the active part (same panel as the settings), to test the 3D movements
        if (debug.showPose && debug.activeView != CarView.OVERVIEW) {
            Box(modifier = Modifier.align(Alignment.TopEnd).padding(top = 76.dp, end = 12.dp, bottom = 12.dp)) {
                ComponentEditorPanel(
                    view = debug.activeView,
                    prefs = debug.prefs,
                    onChange = { debug.prefs = it },
                    onBack = { debug.showPose = false },
                    modifier = Modifier.width(300.dp),
                )
            }
        }

        // bottom-left: tools
        if (toolsOpen) {
            Surface(
                modifier = Modifier.align(Alignment.BottomStart).padding(12.dp).width(360.dp).heightIn(max = 420.dp),
                color = CarGlass,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, CarOutline.copy(alpha = 0.7f)),
            ) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Tools", color = CarOnDark, fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { toolsOpen = false }) { Text("Hide") }
                    }

                    // ---- camera
                    Text("Camera (${debug.activeView.label})", color = CarTeal, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Zoom", color = CarOnDark, fontSize = 13.sp)
                        Compact("Out") { debug.zoom(1.1f) }
                        Compact("In") { debug.zoom(0.9f) }
                        Compact("Reset view") { glideTo(debug.activeView) }
                    }
                    Text("FOV ${debug.shot.fovDeg.roundToInt()}°", color = CarOnDark, fontSize = 13.sp)
                    Slider(
                        value = debug.shot.fovDeg.coerceIn(15f, 100f),
                        onValueChange = { debug.setFov(it) },
                        valueRange = 15f..100f,
                        colors = SliderDefaults.colors(thumbColor = CarTeal, activeTrackColor = CarTeal, inactiveTrackColor = CarOutline),
                    )
                    Mono("pos ${fmt(debug.shot.pos.x)} ${fmt(debug.shot.pos.y)} ${fmt(debug.shot.pos.z)}")
                    Mono("tgt ${fmt(debug.shot.target.x)} ${fmt(debug.shot.target.y)} ${fmt(debug.shot.target.z)}")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                CarTuning.saveShot(context, debug.activeView, debug.shot)
                                debug.note = "Camera saved for ${debug.activeView.label}"
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = CarTeal, contentColor = Color(0xFF00201B)),
                        ) { Text("Save camera", maxLines = 1, fontSize = 12.sp) }
                        Compact("Forget") {
                            CarTuning.resetShot(context, debug.activeView)
                            debug.note = "Camera of ${debug.activeView.label} back to the default"
                        }
                    }

                    // ---- markers
                    Text("Markers", color = CarTeal, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Switch(checked = debug.showMarkers, onCheckedChange = { debug.showMarkers = it })
                        Text("Show markers (mode \"Move markers\" to drag them)", color = CarOnDark, fontSize = 12.sp)
                    }
                    val sel = debug.selectedMarker
                    Text(
                        text = if (sel == null) "Selected: none (tap a marker)" else "Selected: ${sel.label}",
                        color = CarOnDark,
                        fontSize = 13.sp,
                    )
                    if (sel != null) {
                        debug.anchors[sel]?.let { a -> Mono("at  ${fmt(a.x)} ${fmt(a.y)} ${fmt(a.z)}") }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Depth", color = CarOnDark, fontSize = 13.sp)
                            Compact("Closer") { debug.moveMarkerDepth(0.97f) }
                            Compact("Farther") { debug.moveMarkerDepth(1.03f) }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                CarTuning.saveAnchors(context, debug.anchors.toMap())
                                debug.note = "Markers saved"
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = CarTeal, contentColor = Color(0xFF00201B)),
                        ) { Text("Save markers", fontSize = 12.sp) }
                        Compact("Forget") {
                            CarTuning.resetAnchors(context)
                            EDITABLE_VIEWS.forEach { debug.anchors[it] = it.anchor }
                            debug.note = "Markers back to the defaults"
                        }
                    }

                    // ---- export
                    Text("Export", color = CarTeal, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Compact("Copy saved values (Kotlin)") {
                            copyText(context, CarTuning.exportKotlin())
                            debug.note = "Copied (also in Logcat, filter CarTuning)"
                        }
                        Compact("Copy current camera") {
                            copyText(context, "${debug.activeView.name}: ${describe(debug.shot)}")
                            debug.note = "Copied (also in Logcat, filter CarTuning)"
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Switch(checked = debug.showPose, onCheckedChange = { debug.showPose = it })
                        Text("Pose controls of the active part (test the 3D movement)", color = CarOnDark, fontSize = 12.sp)
                    }

                    debug.note?.let { Text(it, color = CarAmber, fontSize = 12.sp) }
                }
            }
        } else {
            TextButton(
                onClick = { toolsOpen = true },
                modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
            ) { Text("Tools") }
        }
    }
}

@Composable
private fun Compact(text: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
        Text(text, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun Mono(text: String) {
    Text(text, color = CarOnDark.copy(alpha = 0.85f), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
}

/** Full-size layer: drags move the camera (orbit / pan) or a marker, taps select a marker, markers are drawn here. */
@Composable
private fun DebugGestureLayer(debug: DebugState) {
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val w = boxSize.width.toFloat()
    val h = boxSize.height.toFloat()
    // the gesture handlers below live longer than one composition: they must read the CURRENT size

    val marks: List<Pair<CarView, Offset>> =
        if (debug.showMarkers && boxSize.width > 0 && boxSize.height > 0) {
            EDITABLE_VIEWS.mapNotNull { v ->
                val a = debug.anchors[v] ?: return@mapNotNull null
                projectToScreen(a, debug.shot.pos, debug.shot.target, debug.shot.fovDeg, w, h)?.let { v to it }
            }
        } else {
            emptyList()
        }
    val currentMarks by rememberUpdatedState(marks)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { boxSize = it }
            .pointerInput("drag") {
                var dragMarker: CarView? = null
                var grab = Offset.Zero
                var depth = 1f
                detectDragGestures(
                    onDragStart = { start ->
                        debug.glide?.cancel()
                        dragMarker = null
                        if (debug.mode == DebugMode.MARKERS) {
                            val near = currentMarks.minByOrNull { (_, p) -> (p - start).getDistance() }
                            val anchor = near?.let { debug.anchors[it.first] }
                            if (near != null && anchor != null && (near.second - start).getDistance() <= 70.dp.toPx()) {
                                dragMarker = near.first
                                debug.selectedMarker = near.first
                                grab = near.second - start
                                depth = depthOf(anchor, debug.shot.pos, debug.shot.target)
                            }
                        }
                    },
                    onDragEnd = { dragMarker = null },
                    onDragCancel = { dragMarker = null },
                ) { change, drag ->
                    change.consume()
                    when (debug.mode) {
                        DebugMode.MARKERS -> {
                            val m = dragMarker
                            if (m != null && depth > 0f) {
                                val s = debug.shot
                                debug.anchors[m] = unprojectAtDepth(
                                    change.position + grab, depth, s.pos, s.target, s.fovDeg,
                                    boxSize.width.toFloat(), boxSize.height.toFloat(),
                                )
                            }
                        }
                        DebugMode.PAN -> debug.pan(drag.x, drag.y, boxSize.height.toFloat())
                        DebugMode.ORBIT -> debug.orbit(drag.x, drag.y)
                    }
                }
            }
            .pointerInput("tap") {
                detectTapGestures { tap ->
                    val near = currentMarks.minByOrNull { (_, p) -> (p - tap).getDistance() }
                    if (near != null && (near.second - tap).getDistance() <= 70.dp.toPx()) debug.selectedMarker = near.first
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // centre cross = the point the camera looks at
            val c = Offset(size.width / 2f, size.height / 2f)
            val arm = 8.dp.toPx()
            drawLine(Color.White.copy(alpha = 0.5f), Offset(c.x - arm, c.y), Offset(c.x + arm, c.y), strokeWidth = 1.dp.toPx())
            drawLine(Color.White.copy(alpha = 0.5f), Offset(c.x, c.y - arm), Offset(c.x, c.y + arm), strokeWidth = 1.dp.toPx())

            marks.forEach { (v, p) ->
                val selected = v == debug.selectedMarker
                val color = if (selected) CarAmber else CarTeal
                drawCircle(Color.Black.copy(alpha = 0.35f), radius = 13.dp.toPx(), center = p)
                drawCircle(Color.White, radius = 4.dp.toPx(), center = p)
                drawCircle(color, radius = 10.dp.toPx(), center = p, style = Stroke(width = 2.5.dp.toPx()))
            }
        }
        marks.forEach { (v, p) ->
            Surface(
                color = Color(0xB3101113),
                shape = RoundedCornerShape(50),
                modifier = Modifier.offset { IntOffset((p.x + 16.dp.toPx()).roundToInt(), (p.y - 12.dp.toPx()).roundToInt()) },
            ) {
                Text(
                    text = v.label,
                    color = if (v == debug.selectedMarker) CarAmber else Color.White,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
    }
}
