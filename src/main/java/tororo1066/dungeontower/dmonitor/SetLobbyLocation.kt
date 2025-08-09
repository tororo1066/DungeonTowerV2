package tororo1066.dungeontower.dmonitor

import tororo1066.displaymonitorapi.actions.ActionResult
import tororo1066.displaymonitorapi.actions.IAbstractAction
import tororo1066.displaymonitorapi.actions.IActionContext
import tororo1066.displaymonitorapi.configuration.IAdvancedConfigurationSection
import tororo1066.dungeontower.DungeonTower
import java.util.UUID

class SetLobbyLocation: AbstractDungeonAction() {

    override fun run(context: IActionContext): ActionResult {
        return context.partyAction { party ->
            val currentTask = party.currentTask ?: return@partyAction ActionResult.noParameters("Current Task not found")
            val location = context.location ?: return@partyAction ActionResult.locationRequired()
            currentTask.lobbyLocation = location
            ActionResult.success()
        }
    }

    override fun prepare(configuration: IAdvancedConfigurationSection) {
    }
}