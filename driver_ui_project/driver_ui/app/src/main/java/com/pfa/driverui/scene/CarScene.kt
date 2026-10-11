package com.pfa.driverui.scene

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pfa.driverui.model.DriverPreferences
import com.pfa.driverui.ui.theme.CarTeal
import io.github.sceneview.SceneView
import io.github.sceneview.SurfaceType
import io.github.sceneview.math.Position
import io.github.sceneview.node.ModelNode as ModelNodeClass
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.tan

private const val MODEL_ASSET = "camry.glb"   // app/src/main/assets/camry.glb
private const val CAR_UNITS = 4.89f            // car length in world units (meters): 1 world unit = 1 m
private const val POSE_ANIM_MS = 800
private const val CAMERA_ANIM_MS = 1200

private class RigHolder {
    var node: ModelNodeClass? = null
    var rig: CarRig? = null
    fun bind(n: ModelNodeClass) {
        if (node !== n) { node = n; rig = CarRig(n) }
    }
}

/**
 * The 3D car: pose driven by [preferences], camera driven by [view] with a cinematic move.
 *
 * [blurDp] blurs only the 3D surface. [hotspots] are parts that get a tappable marker drawn over the car at the
 * position where the part currently appears on screen (projected from the live camera); a tap near a marker calls
 * [onHotspotTap]. The scene keeps working when its size changes (the settings view shrinks it).
 */
@Composable
fun CarScene(
    preferences: DriverPreferences,
    view: CarView,
    modifier: Modifier = Modifier,
    blurDp: Float = 0f,
    hotspots: List<CarView> = emptyList(),
    onHotspotTap: (CarView) -> Unit = {},
) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val cameraNode = rememberCameraNode(engine)
    val holder = remember { RigHolder() }
    var modelReady by remember { mutableStateOf(false) }
    var shownPrefs by remember { mutableStateOf<DriverPreferences?>(null) }
    var shownShot by remember { mutableStateOf<CamShot?>(null) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    fun place(s: CamShot) {
        val rig = holder.rig ?: return
        cameraNode.worldPosition = rig.toWorld(s.pos)
        cameraNode.lookAt(rig.toWorld(s.target))
        // Filament lens projection uses a 24 mm tall sensor: f = 12 / tan(vfov / 2)
        cameraNode.focalLength = 12.0 / tan(s.fovDeg * PI / 360.0)
    }

    Box(modifier = modifier.onSizeChanged { boxSize = it }) {
        SceneView(
            modifier = Modifier.fillMaxSize().blur(blurDp.dp),
            surfaceType = SurfaceType.TextureSurface,   // TextureView: Compose effects (blur) apply to it
            engine = engine,
            modelLoader = modelLoader,
            cameraNode = cameraNode,
            cameraManipulator = null,                   // we move the camera ourselves
            autoCenterContent = false,
        ) {
            val instance = rememberModelInstance(modelLoader, MODEL_ASSET)
            instance?.let {
                ModelNode(
                    modelInstance = it,
                    autoAnimate = false,
                    scaleToUnits = CAR_UNITS,
                    centerOrigin = Position(0f, 0f, 0f),
                    apply = { holder.bind(this) },
                )
            }
            LaunchedEffect(instance) {
                if (instance != null) {
                    while (holder.rig == null) delay(50)   // the rig is bound when the ModelNode is created
                    modelReady = true
                }
            }
        }

        val rig = holder.rig
        val shot = shownShot
        if (modelReady && rig != null && shot != null && hotspots.isNotEmpty() && boxSize.width > 0 && boxSize.height > 0) {
            HotspotLayer(views = hotspots, shot = shot, rig = rig, size = boxSize, onTap = onHotspotTap)
        }
    }

    // Pose: animate from the shown preferences to the new ones.
    LaunchedEffect(modelReady, preferences) {
        val rig = holder.rig ?: return@LaunchedEffect
        if (!modelReady) return@LaunchedEffect
        val from = shownPrefs
        if (from == null) {
            rig.apply(preferences); shownPrefs = preferences
        } else {
            animate(0f, 1f, animationSpec = tween(POSE_ANIM_MS, easing = FastOutSlowInEasing)) { t, _ ->
                val p = lerpPreferences(from, preferences, t)
                rig.apply(p); shownPrefs = p
            }
        }
    }

    // Camera: cinematic move between shots.
    LaunchedEffect(modelReady, view) {
        if (!modelReady || holder.rig == null) return@LaunchedEffect
        val from = shownShot
        val to = view.shot
        if (from == null) {
            place(to); shownShot = to
        } else {
            animate(0f, 1f, animationSpec = tween(CAMERA_ANIM_MS, easing = FastOutSlowInEasing)) { t, _ ->
                val s = lerpShot(from, to, t)
                place(s); shownShot = s
            }
        }
    }

    // The scene was resized (settings view shrinks it): re-apply the lens so the field of view survives.
    LaunchedEffect(boxSize) {
        val s = shownShot ?: return@LaunchedEffect
        if (modelReady) { delay(60); place(s) }
    }
}

// ---------------------------------------------------------------------------------------------
// Tappable markers over the 3D car
// ---------------------------------------------------------------------------------------------

private fun dot(a: V3, b: V3) = a.x * b.x + a.y * b.y + a.z * b.z
private fun cross(a: V3, b: V3) = V3(a.y * b.z - a.z * b.y, a.z * b.x - a.x * b.z, a.x * b.y - a.y * b.x)

/** Pinhole projection of a world point for a camera at [eye] looking at [target] (Y up, vertical fov). Null if behind. */
private fun projectToScreen(p: V3, eye: V3, target: V3, fovDeg: Float, w: Float, h: Float): Offset? {
    val f = (target - eye).normalized()
    val r = cross(f, V3(0f, 1f, 0f)).normalized()
    val u = cross(r, f)
    val d = p - eye
    val z = dot(d, f)
    if (z <= 1e-4f) return null
    val tanHalf = tan(fovDeg * PI / 360.0).toFloat()
    val ndcX = dot(d, r) / (z * tanHalf * (w / h))
    val ndcY = dot(d, u) / (z * tanHalf)
    return Offset((ndcX * 0.5f + 0.5f) * w, (0.5f - ndcY * 0.5f) * h)
}

private fun Position.toV3() = V3(x, y, z)

@Composable
private fun HotspotLayer(
    views: List<CarView>,
    shot: CamShot,
    rig: CarRig,
    size: IntSize,
    onTap: (CarView) -> Unit,
) {
    val eye = rig.toWorld(shot.pos).toV3()
    val target = rig.toWorld(shot.target).toV3()
    val points: List<Pair<CarView, Offset>> = views.mapNotNull { v ->
        projectToScreen(rig.toWorld(v.anchor).toV3(), eye, target, shot.fovDeg, size.width.toFloat(), size.height.toFloat())
            ?.let { v to it }
    }
    val pulse by rememberInfiniteTransition(label = "hotspotPulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1700, easing = LinearEasing), RepeatMode.Restart),
        label = "pulse",
    )
    val currentPoints by rememberUpdatedState(points)
    val currentOnTap by rememberUpdatedState(onTap)

    Box(
        modifier = Modifier.fillMaxSize().pointerInput(Unit) {
            val radius = 72.dp.toPx()
            detectTapGestures { tap ->
                val nearest = currentPoints.minByOrNull { (_, p) -> (p - tap).getDistance() }
                if (nearest != null && (nearest.second - tap).getDistance() <= radius) currentOnTap(nearest.first)
            }
        },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            points.forEach { (_, p) ->
                val base = 9.dp.toPx()
                drawCircle(Color.Black.copy(alpha = 0.35f), radius = base + 3.dp.toPx(), center = p)
                drawCircle(Color.White, radius = base * 0.55f, center = p)
                drawCircle(CarTeal, radius = base, center = p, style = Stroke(width = 2.5.dp.toPx()))
                drawCircle(
                    CarTeal.copy(alpha = (1f - pulse) * 0.7f),
                    radius = base + pulse * 20.dp.toPx(),
                    center = p,
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }
        points.forEach { (v, p) ->
            Surface(
                color = Color(0xB3101113),
                shape = RoundedCornerShape(50),
                modifier = Modifier.offset {
                    IntOffset((p.x + 18.dp.toPx()).roundToInt(), (p.y - 13.dp.toPx()).roundToInt())
                },
            ) {
                Text(
                    text = v.label,
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}
