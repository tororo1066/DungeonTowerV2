package tororo1066.dungeontower.dmonitor.workspace

import tororo1066.displaymonitorapi.configuration.IActionConfiguration
import tororo1066.displaymonitorapi.workspace.IAbstractWorkspace
import tororo1066.dungeontower.DungeonTower
import tororo1066.tororopluginapi.SJavaPlugin

object LootWorkspace: IAbstractWorkspace {

    val lootScripts = HashMap<String, IActionConfiguration>()

    fun load() {
        lootScripts.clear()
        val sConfig = SJavaPlugin.sConfig
        sConfig.mkdirs("DisplayMonitorScripts/loot")
        sConfig.loadAllFiles("DisplayMonitorScripts/loot").forEach { file ->
            val loots = DungeonTower.actionStorage.getActionConfigurations(file)
            loots.forEach { loot ->
                lootScripts[loot.key] = loot
            }
        }
    }

    override fun getName(): String {
        return "DungeonTower_Loot"
    }

    override fun getActionConfigurations(): Map<String, IActionConfiguration> {
        return lootScripts
    }
}