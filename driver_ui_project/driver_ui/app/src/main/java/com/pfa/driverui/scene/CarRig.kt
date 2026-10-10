package com.pfa.driverui.scene

import com.pfa.driverui.model.DriverPreferences
import dev.romainguy.kotlin.math.Quaternion
import io.github.sceneview.math.Position
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.Node

/**
 * Finds the animated glTF nodes of the car (by name) and moves them in WORLD space.
 * Create it only AFTER the ModelNode has its final scale/position (scaleToUnits, centerOrigin): the rest pose
 * is captured in the constructor.
 */
class CarRig(private val model: ModelNode) {

    private val nodes: Map<String, Node> = run {
        val named = model.nodes.mapNotNull { n -> (n as? ModelNode.ChildNode)?.name?.let { it to n } }
        CarPose.NODE_KEYS.mapNotNull { key ->
            val node = named.firstOrNull { it.first == key }?.second
                ?: named.firstOrNull { it.first.startsWith(key) }?.second
            node?.let { key to it }
        }.toMap()
    }

    private val rest: Map<String, WorldPose> = nodes.mapValues { (_, n) ->
        val p = n.worldPosition
        val q = n.worldQuaternion
        WorldPose(V3(p.x, p.y, p.z), Q(q.x, q.y, q.z, q.w))
    }

    /** World scale of the model (scaleToUnits factor). Model units * unitScale = world units. */
    val unitScale: Float get() = model.scale.x

    val missingNodes: List<String> get() = CarPose.NODE_KEYS.filter { it !in nodes }

    /** glTF-frame point -> world position (the model root has no rotation). */
    fun toWorld(p: V3): Position {
        val o = model.worldPosition
        val k = unitScale
        return Position(o.x + p.x * k, o.y + p.y * k, o.z + p.z * k)
    }

    fun apply(prefs: DriverPreferences) {
        val targets = CarPose.targets(rest, prefs, unitScale)
        for (key in CarPose.NODE_KEYS) {
            val node = nodes[key] ?: continue
            val w = targets[key] ?: continue
            node.worldPosition = Position(w.pos.x, w.pos.y, w.pos.z)
            node.worldQuaternion = Quaternion(w.quat.x, w.quat.y, w.quat.z, w.quat.w)
        }
    }
}
