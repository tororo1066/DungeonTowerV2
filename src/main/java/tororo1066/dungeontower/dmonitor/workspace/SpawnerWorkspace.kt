package tororo1066.dungeontower.dmonitor.workspace

import tororo1066.displaymonitorapi.configuration.IActionConfiguration
import tororo1066.displaymonitorapi.workspace.IAbstractWorkspace
import tororo1066.dungeontower.DungeonTower
import tororo1066.tororopluginapi.SJavaPlugin

object SpawnerWorkspace: IAbstractWorkspace {

    val spawnerScripts = HashMap<String, IActionConfiguration>()

    fun load() {
        spawnerScripts.clear()
        val sConfig = SJavaPlugin.sConfig
        sConfig.mkdirs("DisplayMonitorScripts/spawner")
        sConfig.loadAllFiles("DisplayMonitorScripts/spawner").forEach { file ->
            val spawners = DungeonTower.actionStorage.getActionConfigurations(file)
            spawners.forEach { spawner ->
                spawnerScripts[spawner.key] = spawner
            }
        }
    }

    override fun getName(): String {
        return "DungeonTower_Spawner"
    }

    override fun getActionConfigurations(): Map<String, IActionConfiguration> {
        return spawnerScripts
    }
}