package com.pfa.driverui.scene

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.tan

/**
 * Pinhole camera maths shared by the tap markers and the debug screen.
 * Works in any frame that is a uniform scale + translation of the glTF frame (so glTF coordinates are fine).
 * Camera: at [eye] looking at [target], Y up, VERTICAL field of view [fovDeg].
 */
internal fun dot(a: V3, b: V3) = a.x * b.x + a.y * b.y + a.z * b.z

internal fun cross(a: V3, b: V3) = V3(a.y * b.z - a.z * b.y, a.z * b.x - a.x * b.z, a.x * b.y - a.y * b.x)

/** Camera axes: [f] forward, [r] right, [u] up. */
internal class CamBasis(val f: V3, val r: V3, val u: V3)

internal fun camBasis(eye: V3, target: V3): CamBasis {
    val f = (target - eye).normalized()
    val r = cross(f, V3(0f, 1f, 0f)).normalized()
    val u = cross(r, f)
    return CamBasis(f, r, u)
}

/** Screen position (pixels) of [p], or null if it is behind the camera. */
internal fun projectToScreen(p: V3, eye: V3, target: V3, fovDeg: Float, w: Float, h: Float): Offset? {
    val b = camBasis(eye, target)
    val d = p - eye
    val z = dot(d, b.f)
    if (z <= 1e-6f) return null
    val tanHalf = tan(fovDeg * PI / 360.0).toFloat()
    val ndcX = dot(d, b.r) / (z * tanHalf * (w / h))
    val ndcY = dot(d, b.u) / (z * tanHalf)
    return Offset((ndcX * 0.5f + 0.5f) * w, (0.5f - ndcY * 0.5f) * h)
}

/** The point that appears at [screen] (pixels) at distance [depth] in front of the camera (along the view axis). */
internal fun unprojectAtDepth(screen: Offset, depth: Float, eye: V3, target: V3, fovDeg: Float, w: Float, h: Float): V3 {
    val b = camBasis(eye, target)
    val tanHalf = tan(fovDeg * PI / 360.0).toFloat()
    val ndcX = (screen.x / w) * 2f - 1f
    val ndcY = 1f - (screen.y / h) * 2f
    return eye + b.f * depth + b.r * (ndcX * depth * tanHalf * (w / h)) + b.u * (ndcY * depth * tanHalf)
}

/** Depth (distance along the view axis) of [p]. */
internal fun depthOf(p: V3, eye: V3, target: V3): Float = dot(p - eye, camBasis(eye, target).f)
