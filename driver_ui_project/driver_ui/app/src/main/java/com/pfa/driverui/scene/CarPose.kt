package com.pfa.driverui.scene

import com.pfa.driverui.model.ClimatePreferences
import com.pfa.driverui.model.DriverPreferences
import com.pfa.driverui.model.MirrorPreferences
import com.pfa.driverui.model.MirrorsPreferences
import com.pfa.driverui.model.SeatPreferences
import com.pfa.driverui.model.SteeringWheelPreferences
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Minimal math with no engine dependency (see ui/3d-model-constants.md for the numbers). */
data class V3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: V3) = V3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: V3) = V3(x - o.x, y - o.y, z - o.z)
    operator fun times(k: Float) = V3(x * k, y * k, z * k)
    fun normalized(): V3 { val l = sqrt(x * x + y * y + z * z); return V3(x / l, y / l, z / l) }
}

/** Quaternion (x, y, z, w). */
data class Q(val x: Float, val y: Float, val z: Float, val w: Float) {
    operator fun times(o: Q) = Q(
        w * o.x + x * o.w + y * o.z - z * o.y,
        w * o.y - x * o.z + y * o.w + z * o.x,
        w * o.z + x * o.y - y * o.x + z * o.w,
        w * o.w - x * o.x - y * o.y - z * o.z,
    )

    /** Rotates v by this quaternion. */
    fun rotate(v: V3): V3 {
        val r = this * Q(v.x, v.y, v.z, 0f) * Q(-x, -y, -z, w)
        return V3(r.x, r.y, r.z)
    }

    companion object {
        fun axisAngle(axis: V3, deg: Float): Q {
            val a = axis.normalized()
            val h = deg * PI / 180.0 / 2.0
            val s = sin(h).toFloat()
            return Q(a.x * s, a.y * s, a.z * s, cos(h).toFloat())
        }
    }
}

/** World pose of a glTF node (position + rotation), captured once at load as the rest pose. */
data class WorldPose(val pos: V3, val quat: Q)

object CarConstants {
    // model units (1 unit ~ 100 m in the GLB); multiplied by the scene scale at runtime
    const val SEAT_TRAVEL = 0.0015f
    const val SEAT_HEIGHT = 0.0003f
    const val WHEEL_REACH = 0.0006f
    val SEAT_DIR = V3(0f, 0.0875f, 0.9962f).normalized()      // forward along the rail
    val WHEEL_DIR = V3(0f, 0.336f, -0.942f).normalized()      // toward the driver at zero tilt
    val AX_X = V3(1f, 0f, 0f)
    val AX_Y = V3(0f, 1f, 0f)
    val MIRROR_PITCH_AXIS_PASSENGER = V3(0.8435f, 0f, 0.5371f)
    val MIRROR_PITCH_AXIS_DRIVER = V3(0.8435f, 0f, -0.5371f)

    // Flip a sign if a mirror moves the wrong way (tune by eye in the app).
    const val YAW_SIGN_DRIVER = 1f
    const val YAW_SIGN_PASSENGER = 1f
    const val YAW_SIGN_INTERIOR = 1f
    const val PITCH_SIGN_DRIVER = 1f
    const val PITCH_SIGN_PASSENGER = 1f
    const val PITCH_SIGN_INTERIOR = 1f
}

object CarPose {
    const val RAILS = "Seat_Rails"
    const val BASE = "Seat_Base"
    const val BACKREST = "Seat_Backrest"
    const val WHEEL = "Steering_Wheel"
    const val MIRROR_DRIVER = "Mirror_Driver"          // left mirror (driver side = +X in the GLB)
    const val MIRROR_PASSENGER = "Mirror_Passenger"    // right mirror
    const val MIRROR_INTERIOR = "Mirror_Interior"      // node is "Mirror_Interior.001": prefix match

    /** Order matters: parents are written before children. */
    val NODE_KEYS = listOf(RAILS, BASE, BACKREST, WHEEL, MIRROR_DRIVER, MIRROR_PASSENGER, MIRROR_INTERIOR)

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
    private fun pct(v: Float) = (v / 100f).coerceIn(0f, 1f)

    /** -inMax..+inMax -> -negOut..+posOut (asymmetric, proportional). */
    private fun signedMap(v: Float, inMax: Float, negOut: Float, posOut: Float): Float {
        val n = (v / inMax).coerceIn(-1f, 1f)
        return if (n >= 0f) n * posOut else n * negOut
    }

    // preferences -> model ranges, all relative to the rest pose of the GLB
    fun seatSlide(s: SeatPreferences) = (pct(s.positionAvantArriere) - 0.5f) * CarConstants.SEAT_TRAVEL
    fun seatLift(s: SeatPreferences) = pct(s.hauteur) * CarConstants.SEAT_HEIGHT
    fun backrestTiltDeg(s: SeatPreferences) = lerp(15f, -45f, ((s.inclinaisonDossier - 90f) / 70f).coerceIn(0f, 1f))
    fun wheelTiltDeg(w: SteeringWheelPreferences) = lerp(-11f, 7f, pct(w.hauteur))
    fun wheelReach(w: SteeringWheelPreferences) = (pct(w.profondeur) - 0.5f) * CarConstants.WHEEL_REACH

    private fun sideMirror(m: MirrorPreferences) =
        signedMap(m.horizontal, 30f, 7f, 6f) to signedMap(m.vertical, 20f, 9f, 9f)

    private fun interiorMirror(m: MirrorPreferences) =
        signedMap(m.horizontal, 30f, 45f, 45f) to signedMap(m.vertical, 20f, 25f, 15f)

    /**
     * World targets for every animated node. Pivots are the node origins, so a rotation only changes the
     * world quaternion and a translation only the world position. [unitScale] = world scale of the model.
     */
    fun targets(rest: Map<String, WorldPose>, p: DriverPreferences, unitScale: Float): Map<String, WorldPose> {
        val k = unitScale
        val out = HashMap<String, WorldPose>()
        val slide = CarConstants.SEAT_DIR * (seatSlide(p.seat) * k)
        val lift = V3(0f, seatLift(p.seat) * k, 0f)

        rest[RAILS]?.let { out[RAILS] = WorldPose(it.pos + slide, it.quat) }
        rest[BASE]?.let { out[BASE] = WorldPose(it.pos + slide + lift, it.quat) }
        rest[BACKREST]?.let {
            out[BACKREST] = WorldPose(it.pos + slide + lift, Q.axisAngle(CarConstants.AX_X, backrestTiltDeg(p.seat)) * it.quat)
        }

        rest[WHEEL]?.let {
            val qTilt = Q.axisAngle(CarConstants.AX_X, wheelTiltDeg(p.steeringWheel))
            val reach = qTilt.rotate(CarConstants.WHEEL_DIR) * (wheelReach(p.steeringWheel) * k)
            out[WHEEL] = WorldPose(it.pos + reach, qTilt * it.quat)
        }

        fun mirror(key: String, m: MirrorPreferences, pitchAxis: V3, yawSign: Float, pitchSign: Float, interior: Boolean) {
            val r = rest[key] ?: return
            val (yaw, pitch) = if (interior) interiorMirror(m) else sideMirror(m)
            val qy = Q.axisAngle(CarConstants.AX_Y, yaw * yawSign)
            val qp = Q.axisAngle(pitchAxis, pitch * pitchSign)
            out[key] = WorldPose(r.pos, qp * qy * r.quat)
        }
        mirror(MIRROR_DRIVER, p.mirrors.gauche, CarConstants.MIRROR_PITCH_AXIS_DRIVER,
            CarConstants.YAW_SIGN_DRIVER, CarConstants.PITCH_SIGN_DRIVER, false)
        mirror(MIRROR_PASSENGER, p.mirrors.droit, CarConstants.MIRROR_PITCH_AXIS_PASSENGER,
            CarConstants.YAW_SIGN_PASSENGER, CarConstants.PITCH_SIGN_PASSENGER, false)
        mirror(MIRROR_INTERIOR, p.mirrors.interieur, CarConstants.AX_X,
            CarConstants.YAW_SIGN_INTERIOR, CarConstants.PITCH_SIGN_INTERIOR, true)
        return out
    }
}

private fun lerpF(a: Float, b: Float, t: Float) = a + (b - a) * t
private fun lerpM(a: MirrorPreferences, b: MirrorPreferences, t: Float) =
    MirrorPreferences(lerpF(a.horizontal, b.horizontal, t), lerpF(a.vertical, b.vertical, t))

/** Linear interpolation between two preference sets (used to animate the pose). */
fun lerpPreferences(a: DriverPreferences, b: DriverPreferences, t: Float) = DriverPreferences(
    seat = SeatPreferences(
        lerpF(a.seat.positionAvantArriere, b.seat.positionAvantArriere, t),
        lerpF(a.seat.hauteur, b.seat.hauteur, t),
        lerpF(a.seat.inclinaisonDossier, b.seat.inclinaisonDossier, t),
    ),
    steeringWheel = SteeringWheelPreferences(
        lerpF(a.steeringWheel.hauteur, b.steeringWheel.hauteur, t),
        lerpF(a.steeringWheel.profondeur, b.steeringWheel.profondeur, t),
    ),
    mirrors = MirrorsPreferences(
        lerpM(a.mirrors.gauche, b.mirrors.gauche, t),
        lerpM(a.mirrors.droit, b.mirrors.droit, t),
        lerpM(a.mirrors.interieur, b.mirrors.interieur, t),
    ),
    climate = ClimatePreferences(
        lerpF(a.climate.temperatureCelsius, b.climate.temperatureCelsius, t),
        lerpF(a.climate.vitesseVentilation, b.climate.vitesseVentilation, t),
    ),
)
