package com.pfa.driverui.scene

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import java.util.Locale

/**
 * Camera shots and tap-marker positions that were tuned with the debug screen. They override the defaults written
 * in [CarView] and are saved on the device, so no rebuild is needed while tuning. Once happy, copy the numbers
 * ([exportKotlin]) into CameraPresets.kt.
 */
object CarTuning {
    private const val PREFS = "car_tuning"

    val shots = mutableStateMapOf<CarView, CamShot>()
    val anchors = mutableStateMapOf<CarView, V3>()

    fun shot(view: CarView): CamShot = shots[view] ?: view.shot
    fun anchor(view: CarView): V3 = anchors[view] ?: view.anchor

    private fun f(v: Float) = String.format(Locale.US, "%.5f", v)

    private fun parse(text: String?, count: Int): List<Float>? {
        val parts = text?.split(",")?.mapNotNull { it.trim().toFloatOrNull() } ?: return null
        return if (parts.size == count) parts else null
    }

    fun load(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        for (v in CarView.entries) {
            parse(p.getString("shot_${v.name}", null), 7)?.let {
                shots[v] = CamShot(V3(it[0], it[1], it[2]), V3(it[3], it[4], it[5]), it[6])
            }
            parse(p.getString("anchor_${v.name}", null), 3)?.let { anchors[v] = V3(it[0], it[1], it[2]) }
        }
    }

    fun saveShot(context: Context, view: CarView, shot: CamShot) {
        shots[view] = shot
        val text = listOf(shot.pos.x, shot.pos.y, shot.pos.z, shot.target.x, shot.target.y, shot.target.z, shot.fovDeg)
            .joinToString(",") { f(it) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("shot_${view.name}", text).apply()
    }

    fun resetShot(context: Context, view: CarView) {
        shots.remove(view)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove("shot_${view.name}").apply()
    }

    fun saveAnchors(context: Context, values: Map<CarView, V3>) {
        val edit = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        for ((view, a) in values) {
            anchors[view] = a
            edit.putString("anchor_${view.name}", listOf(a.x, a.y, a.z).joinToString(",") { f(it) })
        }
        edit.apply()
    }

    fun resetAnchors(context: Context) {
        anchors.clear()
        val edit = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        for (v in CarView.entries) edit.remove("anchor_${v.name}")
        edit.apply()
    }

    /** Text to paste into the CarView enum (CameraPresets.kt): the saved values for every view. */
    fun exportKotlin(): String = buildString {
        for (v in CarView.entries) {
            val s = shot(v)
            val a = anchor(v)
            append("    ${v.name}(\"${v.label}\", ")
            append("CamShot(V3(${f(s.pos.x)}f, ${f(s.pos.y)}f, ${f(s.pos.z)}f), ")
            append("V3(${f(s.target.x)}f, ${f(s.target.y)}f, ${f(s.target.z)}f), ${s.fovDeg.toInt()}f), ")
            append("V3(${f(a.x)}f, ${f(a.y)}f, ${f(a.z)}f)),\n")
        }
    }
}
