package tororo1066.dungeontower.dmonitor.floor

import org.bukkit.util.Vector
import tororo1066.displaymonitorapi.actions.ActionResult
import tororo1066.displaymonitorapi.actions.IAbstractAction
import tororo1066.displaymonitorapi.actions.IActionContext
import tororo1066.displaymonitorapi.configuration.Execute
import tororo1066.displaymonitorapi.configuration.IAdvancedConfigurationSection
import tororo1066.dungeontower.DungeonTower
import java.util.UUID

class CheckConflictFloor: IAbstractAction {

    var allowConflictDistance: Vector = Vector(0.0, 0.0, 0.0)
    var floorName: String = ""
    var rotation: Double = .0
    var then: Execute = Execute.empty()
    var elseActions: Execute = Execute.empty()

    override fun allowedAutoStop(): Boolean {
        return true
    }

    @Suppress("UNCHECKED_CAST")
    override fun run(context: IActionContext): ActionResult {

        fun executeActions(conflict: Boolean): ActionResult {
            if (conflict) {
                then(context)
            } else {
                elseActions(context)
            }

            return ActionResult.success()
        }

        val location = context.location ?: return ActionResult.locationRequired()
        val floorData = DungeonTower.floorData[floorName]
            ?: return ActionResult.noParameters("Floor data not found for name: $floorName")
        val parameters = context.publicContext.parameters
        val uuid = UUID.fromString((parameters["uuid"] ?: return ActionResult.noParameters("No floor UUID found")) as String)
        val generatedFloors = CallFloor.floors[uuid] ?: return executeActions(false)
        val (dungeonStartLoc, dungeonEndLoc) = floorData.calculateLocation(location, rotation)

        for (floor in generatedFloors) {
            val (startX, startY, startZ) = floor["startLoc"] as? List<Int> ?: continue
            val (endX, endY, endZ) = floor["endLoc"] as? List<Int> ?: continue
            for (x in dungeonStartLoc.blockX - allowConflictDistance.blockX.. dungeonEndLoc.blockX-allowConflictDistance.blockX) {
                for (y in dungeonStartLoc.blockY - allowConflictDistance.blockY..dungeonEndLoc.blockY-allowConflictDistance.blockY) {
                    for (z in dungeonStartLoc.blockZ - allowConflictDistance.blockZ..dungeonEndLoc.blockZ-allowConflictDistance.blockZ) {
                        if (x in startX..endX && y in startY..endY && z in startZ..endZ) {
                            return executeActions(true)
                        }
                    }
                }
            }
        }

        return executeActions(false)
    }



    override fun prepare(configuration: IAdvancedConfigurationSection) {
        allowConflictDistance = configuration.getBukkitVector("allowConflictDistance", allowConflictDistance)
        floorName = configuration.getString("floorName", floorName) ?: floorName
        rotation = configuration.getDouble("rotation", rotation)
        then = configuration.getConfigExecute("then", then)
        elseActions = configuration.getConfigExecute("else", elseActions)
    }
}