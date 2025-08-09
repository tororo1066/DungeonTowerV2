package tororo1066.dungeontower.dmonitor.tower

import tororo1066.displaymonitorapi.actions.ActionResult
import tororo1066.displaymonitorapi.actions.IAbstractAction
import tororo1066.displaymonitorapi.actions.IActionContext
import tororo1066.displaymonitorapi.configuration.IAdvancedConfigurationSection
import kotlin.math.max

class SelectFloor: IAbstractAction {

    var floorName = ""
    var floorNum = 1

    override fun allowedAutoStop(): Boolean {
        return true
    }

    override fun run(context: IActionContext): ActionResult {
        val parameters = context.publicContext.parameters
        parameters["entry.floor.name"] = floorName
        parameters["entry.floor.num"] = floorNum

        return ActionResult.success()
    }

    override fun prepare(configuration: IAdvancedConfigurationSection) {
        floorName = configuration.getString("floorName", "")!!
        floorNum = max(1, configuration.getInt("floorNum", 1))
    }
}