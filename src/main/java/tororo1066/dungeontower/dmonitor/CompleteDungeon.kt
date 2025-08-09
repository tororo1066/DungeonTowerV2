package tororo1066.dungeontower.dmonitor

import org.bukkit.Bukkit
import tororo1066.displaymonitorapi.actions.ActionResult
import tororo1066.displaymonitorapi.actions.IActionContext
import tororo1066.displaymonitorapi.configuration.IAdvancedConfigurationSection
import tororo1066.tororopluginapi.SJavaPlugin
import tororo1066.tororopluginapi.SStr

class CompleteDungeon: AbstractDungeonAction() {

    var message = ""

    override fun run(context: IActionContext): ActionResult {
        return context.partyAction { party ->
            val currentTask = party.currentTask ?: return@partyAction ActionResult.noParameters("Current Task not found")
            var lock = true
            Bukkit.getScheduler().runTask(SJavaPlugin.plugin, Runnable {
                currentTask.complete(SStr(message))
                lock = false
            })
            while (lock) {
                Thread.sleep(50)
            }
            return@partyAction ActionResult.success()
        }
    }

    override fun prepare(configuration: IAdvancedConfigurationSection) {
        message = configuration.getString("message") ?: "§a§lクリア！"
    }
}