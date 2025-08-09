package tororo1066.dungeontower.dmonitor

import tororo1066.displaymonitorapi.actions.ActionResult
import tororo1066.displaymonitorapi.actions.IActionContext
import tororo1066.displaymonitorapi.configuration.Execute
import tororo1066.displaymonitorapi.configuration.IAdvancedConfigurationSection
import tororo1066.tororopluginapi.utils.toPlayer

class TargetParty: AbstractDungeonAction() {

    var actions: Execute = Execute.empty()
    var onlyParent = false

    override fun run(context: IActionContext): ActionResult {
        return context.partyAction { party ->
            val players = if (onlyParent) {
                listOfNotNull(party.parent.toPlayer())
            } else {
                party.players.keys.mapNotNull { it.toPlayer() }
            }
            players.forEach { player ->
                if (context.publicContext.stop) {
                    return@partyAction ActionResult.success()
                }
                val cloneContext = context.cloneWithRandomUUID().apply {
                    target = player
                }
                actions(cloneContext)
                if (cloneContext.stop) {
                    return@partyAction ActionResult.success()
                }
            }
            ActionResult.success()
        }
    }

    override fun prepare(section: IAdvancedConfigurationSection) {
        actions = section.getConfigExecute("actions", actions)
        onlyParent = section.getBoolean("onlyParent", false)
    }
}