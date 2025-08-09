package tororo1066.dungeontower.dmonitor

import tororo1066.displaymonitorapi.actions.ActionResult
import tororo1066.displaymonitorapi.actions.IActionContext
import tororo1066.displaymonitorapi.configuration.IAdvancedConfigurationSection
import tororo1066.dungeontower.logging.TowerLogDB

class GetSubAccounts: AbstractDungeonAction() {

    var listName = "subAccounts"

    override fun run(context: IActionContext): ActionResult {
        val target = context.target ?: return ActionResult.targetRequired()
        val subAccounts = TowerLogDB.getSubAccounts(target.uniqueId)
        context.publicContext.parameters[listName] = subAccounts.map { it.toString() }
        return ActionResult.success()
    }

    override fun prepare(configuration: IAdvancedConfigurationSection) {
        listName = configuration.getString("listName", listName) ?: listName
    }
}