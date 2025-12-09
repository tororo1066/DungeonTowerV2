package tororo1066.dungeontower.dmonitor

import tororo1066.displaymonitorapi.actions.ActionResult
import tororo1066.displaymonitorapi.actions.IActionContext
import tororo1066.displaymonitorapi.configuration.IAdvancedConfigurationSection

class SetCurrentTime: AbstractDungeonAction() {

    var time = 0

    override fun run(context: IActionContext): ActionResult {
        return context.partyAction { party ->
            val nowTask = party.currentTask ?: return@partyAction ActionResult.noParameters("Current Task not found")
            nowTask.nowFloor.time = time
            ActionResult.success()
        }
    }

    override fun prepare(configuration: IAdvancedConfigurationSection) {
        time = configuration.getInt("time",0)
    }
}