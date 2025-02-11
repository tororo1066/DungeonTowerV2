package tororo1066.dungeontower.dmonitor

import tororo1066.displaymonitor.actions.AbstractAction
import tororo1066.displaymonitor.actions.ActionContext
import tororo1066.displaymonitor.actions.ActionResult
import tororo1066.displaymonitor.configuration.AdvancedConfigurationSection
import tororo1066.displaymonitor.elements.Execute
import tororo1066.dungeontower.DungeonTower
import tororo1066.tororopluginapi.utils.toPlayer
import java.util.UUID

class TargetParty: AbstractAction() {

    var actions: Execute = Execute {}

    override fun run(context: ActionContext): ActionResult {
        val parameters = context.configuration?.parameters ?: return ActionResult.noParameters("Parameters not found")
        val uuid = UUID.fromString((parameters["party.uuid"] ?: return ActionResult.noParameters("Party UUID not found")) as String)
        val party = DungeonTower.partiesData.entries.find { it.value?.partyUUID == uuid }?.value ?: return ActionResult.noParameters("Party not found")
        party.players.keys.forEach {
            if (context.publicContext.stop) {
                return ActionResult.success()
            }
            val p = it.toPlayer() ?: return@forEach
            val cloneContext = context.cloneWithRandomUUID().apply {
                target = p
            }
            actions(cloneContext)
            if (cloneContext.stop) {
                return ActionResult.success()
            }
        }

        return ActionResult.success()
    }

    override fun prepare(section: AdvancedConfigurationSection) {
        actions = section.getConfigExecute("actions", actions)!!
    }
}