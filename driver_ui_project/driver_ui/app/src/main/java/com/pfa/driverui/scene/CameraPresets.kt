package com.pfa.driverui.scene

/** A camera shot, in glTF coordinates (see ui/3d-model-constants.md). Tune these numbers by eye. */
data class CamShot(val pos: V3, val target: V3, val fovDeg: Float)

/**
 * [anchor] = the point of the car (glTF coordinates) where the tappable hotspot of this part is drawn in the
 * overview. It is only used for the tap markers, tune it if a marker does not sit on its part.
 */
enum class CarView(val label: String, val shot: CamShot, val anchor: V3) {
    // Tuned with the debug screen on 12/10 (camera + marker positions).
    OVERVIEW("Car", CamShot(V3(0.00249f, 0.00877f, -0.03133f), V3(0.00138f, 0.00038f, -0.01042f), 66f), V3(0.00230f, 0.00400f, -0.02450f)),
    SEAT("Seat", CamShot(V3(-0.00143f, 0.00832f, -0.02373f), V3(0.00732f, 0.00450f, -0.02309f), 65f), V3(0.00598f, 0.00394f, -0.02734f)),
    WHEEL("Steering wheel", CamShot(V3(-0.00271f, 0.00691f, -0.02312f), V3(0.00719f, 0.00368f, -0.01852f), 50f), V3(0.00595f, 0.00505f, -0.02051f)),
    MIRROR_LEFT("Left mirror", CamShot(V3(0.00770f, 0.00748f, -0.02525f), V3(0.00976f, 0.00695f, -0.02053f), 40f), V3(0.01160f, 0.00610f, -0.01800f)),
    MIRROR_RIGHT("Right mirror", CamShot(V3(0.00417f, 0.00672f, -0.02585f), V3(-0.00051f, 0.00637f, -0.02253f), 40f), V3(-0.00710f, 0.00610f, -0.01800f)),
    MIRROR_INTERIOR("Interior mirror", CamShot(V3(0.00567f, 0.00769f, -0.02574f), V3(0.00238f, 0.00839f, -0.02070f), 45f), V3(0.00230f, 0.00830f, -0.02000f)),
}

/** The parts the driver can tap in the settings view (everything except the overview). */
val EDITABLE_VIEWS: List<CarView> = CarView.entries.filter { it != CarView.OVERVIEW }

fun lerpShot(a: CamShot, b: CamShot, t: Float): CamShot {
    fun l(x: Float, y: Float) = x + (y - x) * t
    fun v(p: V3, q: V3) = V3(l(p.x, q.x), l(p.y, q.y), l(p.z, q.z))
    return CamShot(v(a.pos, b.pos), v(a.target, b.target), l(a.fovDeg, b.fovDeg))
}