package tororo1066.dungeontower.data

import io.lumine.mythic.api.mobs.MythicMob
import org.bukkit.configuration.file.YamlConfiguration
import tororo1066.dungeontower.DungeonTower
import java.io.File

class SpawnerData: Cloneable {

    var includeName = ""
    var mob: MythicMob? = null
    var count = 0
    var coolTime = 0
    var max = 0
    var radius = 0
    var level = 0.0
    var activateRange = 0

    var kill = 0
    var navigateKill = 0

    public override fun clone(): SpawnerData {
        return super.clone() as SpawnerData
    }

    companion object{
        fun loadFromYml(file: File): Pair<String, SpawnerData> {
            val yml = YamlConfiguration.loadConfiguration(file)
            val data = SpawnerData()
            data.includeName = file.nameWithoutExtension
            data.mob = DungeonTower.mythic.getMythicMob(yml.getString("mob"))?:null
            data.coolTime = yml.getInt("cooltime")
            data.max = yml.getInt("max")
            data.radius = yml.getInt("radius")
            data.level = yml.getDouble("level")
            data.activateRange = yml.getInt("activateRange")
            data.navigateKill = yml.getInt("navigate")
            return Pair(data.includeName, data)
        }
    }
}