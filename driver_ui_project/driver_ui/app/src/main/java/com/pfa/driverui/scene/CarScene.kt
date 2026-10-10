package com.pfa.driverui.scene

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import com.pfa.driverui.model.DriverPreferences
import io.github.sceneview.SceneView
import io.github.sceneview.SurfaceType
import io.github.sceneview.math.Position
import io.github.sceneview.node.ModelNode as ModelNodeClass
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader
import kotlin.math.PI
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
 * Blur / alpha are applied by the caller through [modifier].
 */
@Composable
fun CarScene(
    preferences: DriverPreferences,
    view: CarView,
    modifier: Modifier = Modifier,
) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val cameraNode = rememberCameraNode(engine)
    val holder = remember { RigHolder() }
    var modelReady by remember { mutableStateOf(false) }
    var shownPrefs by remember { mutableStateOf<DriverPreferences?>(null) }
    var shownShot by remember { mutableStateOf<CamShot?>(null) }

    SceneView(
        modifier = modifier,
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
        val rig = holder.rig ?: return@LaunchedEffect
        if (!modelReady) return@LaunchedEffect
        fun place(s: CamShot) {
            cameraNode.worldPosition = rig.toWorld(s.pos)
            cameraNode.lookAt(rig.toWorld(s.target))
            // Filament lens projection uses a 24 mm tall sensor: f = 12 / tan(vfov / 2)
            cameraNode.focalLength = 12.0 / tan(s.fovDeg * PI / 360.0)
        }
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
}
