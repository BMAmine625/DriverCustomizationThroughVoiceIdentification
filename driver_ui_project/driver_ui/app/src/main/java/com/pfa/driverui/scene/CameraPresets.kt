package com.pfa.driverui.scene

/** A camera shot, in glTF coordinates (see ui/3d-model-constants.md). Tune these numbers by eye. */
data class CamShot(val pos: V3, val target: V3, val fovDeg: Float)

enum class CarView(val label: String, val shot: CamShot) {
    OVERVIEW("Car", CamShot(V3(0.050f, 0.030f, 0.020f), V3(0.0023f, 0.0040f, -0.0245f), 35f)),
    SEAT("Seat", CamShot(V3(0.0023f, 0.0060f, -0.0200f), V3(0.0068f, 0.0010f, -0.0268f), 65f)),
    WHEEL("Wheel", CamShot(V3(0.0010f, 0.0052f, -0.0225f), V3(0.0068f, 0.0027f, -0.0166f), 50f)),
    MIRROR_LEFT("Left mirror", CamShot(V3(0.0165f, 0.0080f, -0.0240f), V3(0.0116f, 0.0061f, -0.0180f), 40f)),
    MIRROR_RIGHT("Right mirror", CamShot(V3(-0.0120f, 0.0080f, -0.0240f), V3(-0.0071f, 0.0061f, -0.0180f), 40f)),
    MIRROR_INTERIOR("Interior mirror", CamShot(V3(0.0068f, 0.0050f, -0.0250f), V3(0.0023f, 0.0083f, -0.0200f), 45f)),
}

fun lerpShot(a: CamShot, b: CamShot, t: Float): CamShot {
    fun l(x: Float, y: Float) = x + (y - x) * t
    fun v(p: V3, q: V3) = V3(l(p.x, q.x), l(p.y, q.y), l(p.z, q.z))
    return CamShot(v(a.pos, b.pos), v(a.target, b.target), l(a.fovDeg, b.fovDeg))
}
