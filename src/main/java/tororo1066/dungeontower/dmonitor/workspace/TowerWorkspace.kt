package tororo1066.dungeontower.dmonitor.workspace

import tororo1066.displaymonitorapi.configuration.IActionConfiguration
import tororo1066.displaymonitorapi.workspace.IAbstractWorkspace
import tororo1066.dungeontower.DungeonTower
import tororo1066.tororopluginapi.SJavaPlugin

object TowerWorkspace: IAbstractWorkspace {

    val towerScripts = HashMap<String, IActionConfiguration>()

    fun load() {
        towerScripts.clear()
        val sConfig = SJavaPlugin.sConfig
        sConfig.mkdirs("DisplayMonitorScripts/tower")
        sConfig.loadAllFiles("DisplayMonitorScripts/tower").forEach { file ->
            val floors = DungeonTower.actionStorage.getActionConfigurations(file)
            floors.forEach { floor ->
                towerScripts[floor.key] = floor
            }
        }
    }

    override fun getName(): String {
        return "DungeonTower_Tower"
    }

    override fun getActionConfigurations(): Map<String, IActionConfiguration> {
        return towerScripts
    }
}