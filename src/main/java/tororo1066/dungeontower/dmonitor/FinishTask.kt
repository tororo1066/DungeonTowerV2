package tororo1066.dungeontower.dmonitor

import tororo1066.displaymonitorapi.actions.ActionResult
import tororo1066.displaymonitorapi.actions.IActionContext
import tororo1066.displaymonitorapi.configuration.IAdvancedConfigurationSection
import tororo1066.dungeontower.DungeonTower

class FinishTask: AbstractDungeonAction() {

    override fun run(context: IActionContext): ActionResult {
        return context.partyAction { party ->
            val nowFloor = party.currentTask?.nowFloor ?: return@partyAction ActionResult.noParameters("Current Task not found")
            if (nowFloor.finished.compareAndSet(false, true)) {
                DungeonTower.util.runTask {
                    nowFloor.unlockChest()
                }
            }
            ActionResult.success()
        }
    }

    override fun prepare(section: IAdvancedConfigurationSection) {
    }
}