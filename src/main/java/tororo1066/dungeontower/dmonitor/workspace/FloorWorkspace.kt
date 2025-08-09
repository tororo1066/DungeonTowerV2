package tororo1066.dungeontower.dmonitor.workspace

import tororo1066.displaymonitorapi.configuration.IActionConfiguration
import tororo1066.displaymonitorapi.workspace.IAbstractWorkspace
import tororo1066.dungeontower.DungeonTower
import tororo1066.tororopluginapi.SJavaPlugin

object FloorWorkspace: IAbstractWorkspace {

    val floorScripts = HashMap<String, IActionConfiguration>()

    fun load() {
        floorScripts.clear()
        val sConfig = SJavaPlugin.sConfig
        sConfig.mkdirs("DisplayMonitorScripts/floor")
        sConfig.loadAllFiles("DisplayMonitorScripts/floor").forEach { file ->
            val floors = DungeonTower.actionStorage.getActionConfigurations(file)
            floors.forEach { floor ->
                floorScripts[floor.key] = floor
            }
        }
    }

    override fun getName(): String {
        return "DungeonTower_Floor"
    }

    override fun getActionConfigurations(): Map<String, IActionConfiguration> {
        return floorScripts
    }
}