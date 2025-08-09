package tororo1066.dungeontower.dmonitor.floor

import tororo1066.displaymonitorapi.actions.ActionResult
import tororo1066.displaymonitorapi.actions.IAbstractAction
import tororo1066.displaymonitorapi.actions.IActionContext
import tororo1066.displaymonitorapi.configuration.IAdvancedConfigurationSection
import tororo1066.dungeontower.DungeonTower
import java.util.UUID

class CallFloor: IAbstractAction {

    private var floorName: String = ""
    private var rotation: Int = 0

    companion object {
        val floors = mutableMapOf<UUID, MutableList<Map<String, Any?>>>()
    }

    override fun allowedAutoStop(): Boolean {
        return true
    }

    override fun run(context: IActionContext): ActionResult {
        val parameters = context.publicContext.parameters
        if (!DungeonTower.floorData.containsKey(floorName)) {
            return ActionResult.noParameters("Floor data not found for name: $floorName")
        }

        parameters["call.floor.name"] = floorName
        parameters["call.rotation"] = rotation

        return ActionResult.success()
    }

    override fun prepare(configuration: IAdvancedConfigurationSection) {
        floorName = configuration.getString("floorName", floorName)!!
        rotation = configuration.getInt("rotation", rotation)
    }
}