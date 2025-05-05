package tororo1066.dungeontower.data

import org.bukkit.Location
import org.bukkit.configuration.file.YamlConfiguration
import tororo1066.dungeontower.DungeonTower
import java.io.File

class LootData: Cloneable {

    var internalName: String = ""
    var displayName: String = ""
    var displayMonitorScript: String? = null

    companion object {
        fun loadFromYml(file: File): Pair<String, LootData> {
            val yml = YamlConfiguration.loadConfiguration(file)
            val lootData = LootData().apply {
                internalName = file.nameWithoutExtension
                displayName = yml.getString("displayName") ?: "DungeonTowerLootChest"
                displayMonitorScript = yml.getString("displayMonitorScript")
            }
            return Pair(file.nameWithoutExtension, lootData)
        }
    }

    fun supplyLoot(location: Location) {
        val scriptName = displayMonitorScript ?: internalName
        val script = DungeonTower.lootScripts[scriptName] ?: return
        script.run(
            DungeonTower.actionStorage.createActionContext(
                DungeonTower.actionStorage.createPublicContext()
            ).apply {
                prepareParameters["chest.location.world"] = location.world.name
                prepareParameters["chest.location.x"] = location.blockX
                prepareParameters["chest.location.y"] = location.blockY
                prepareParameters["chest.location.z"] = location.blockZ
            },
            true,
            null
        )
    }

    public override fun clone(): LootData {
        return super.clone() as LootData
    }
}