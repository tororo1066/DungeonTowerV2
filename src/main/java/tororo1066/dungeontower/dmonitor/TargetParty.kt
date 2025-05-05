package tororo1066.dungeontower.dmonitor

import tororo1066.displaymonitorapi.actions.ActionResult
import tororo1066.displaymonitorapi.actions.IAbstractAction
import tororo1066.displaymonitorapi.actions.IActionContext
import tororo1066.displaymonitorapi.configuration.Execute
import tororo1066.displaymonitorapi.configuration.IAdvancedConfigurationSection
import tororo1066.dungeontower.DungeonTower
import tororo1066.tororopluginapi.utils.toPlayer
import java.util.UUID

class TargetParty: IAbstractAction {

    var actions: Execute = Execute.empty()

    override fun allowedAutoStop(): Boolean {
        return true
    }

    override fun run(context: IActionContext): ActionResult {
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

    override fun prepare(section: IAdvancedConfigurationSection) {
        actions = section.getConfigExecute("actions", actions)
    }
}