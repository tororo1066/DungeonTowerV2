package tororo1066.dungeontower.dmonitor.tower

import tororo1066.displaymonitorapi.actions.ActionResult
import tororo1066.displaymonitorapi.actions.IAbstractAction
import tororo1066.displaymonitorapi.actions.IActionContext
import tororo1066.displaymonitorapi.configuration.IAdvancedConfigurationSection

class AllowEntry: IAbstractAction {

    var allowed = true

    override fun allowedAutoStop(): Boolean {
        return true
    }

    override fun run(context: IActionContext): ActionResult {
        val parameters = context.publicContext.parameters
        parameters["entry.allowed"] = allowed
        return ActionResult.success()
    }

    override fun prepare(configuration: IAdvancedConfigurationSection) {
        allowed = configuration.getBoolean("allowed", true)
    }
}