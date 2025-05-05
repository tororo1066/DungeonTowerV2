package tororo1066.dungeontower.dmonitor

import tororo1066.displaymonitorapi.actions.ActionResult
import tororo1066.displaymonitorapi.actions.IAbstractAction
import tororo1066.displaymonitorapi.actions.IActionContext
import tororo1066.displaymonitorapi.configuration.IAdvancedConfigurationSection
import tororo1066.dungeontower.DungeonTower
import java.util.UUID

class FinishTaskAction: IAbstractAction {

    override fun allowedAutoStop(): Boolean {
        return true
    }

    override fun run(context: IActionContext): ActionResult {
        val parameters = context.configuration?.parameters ?: return ActionResult.noParameters("Parameters not found")
        val uuid = UUID.fromString((parameters["party.uuid"] ?: return ActionResult.noParameters("Party UUID not found")) as String)
        val party = DungeonTower.partiesData.entries.find { it.value?.partyUUID == uuid }?.value ?: return ActionResult.noParameters("Party not found")
        val nowFloor = party.nowTask?.nowFloor ?: return ActionResult.noParameters("Current Task not found")
//        nowFloor.finished.set(true)
//        DungeonTower.util.runTask {
//            nowFloor.unlockChest()
//        }
        if (nowFloor.finished.compareAndSet(false, true)) {
            DungeonTower.util.runTask {
                nowFloor.unlockChest()
            }
        }
        return ActionResult.success()
    }

    override fun prepare(section: IAdvancedConfigurationSection) {
    }
}