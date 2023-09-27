package tororo1066.dungeontower.data

import io.lumine.mythic.api.mobs.MythicMob
import org.bukkit.configuration.file.YamlConfiguration
import tororo1066.dungeontower.DungeonTower
import java.io.File
import kotlin.random.Random
import kotlin.random.nextInt

class SpawnerData: Cloneable {

    var internalName = ""
    val mobs = ArrayList<Pair<Int, MythicMob>>()
    var count = 0
    var coolTime = 0
    var max = 0
    var radius = 0
    var yOffSet = 0.0
    var level = 1.0
    var activateRange = 0

    var kill = 0
    var navigateKill = 0

    fun randomMob(): MythicMob {
        val random = Random.nextInt(1..1000000)
        var preventRandom = 0
        for (mob in mobs){
            if (preventRandom < random && mob.first + preventRandom > random){
                return mob.second
            }
            preventRandom = mob.first
        }
        throw NullPointerException("Couldn't find mob. Maybe sum percentage is not 1000000.")
    }

    public override fun clone(): SpawnerData {
        return super.clone() as SpawnerData
    }

    companion object{
        fun loadFromYml(file: File): Pair<String, SpawnerData> {
            val yml = YamlConfiguration.loadConfiguration(file)
            val data = SpawnerData().apply {
                internalName = file.nameWithoutExtension
                DungeonTower.mythic.getMythicMob(yml.getString("mob",""))?.let { mobs.add(Pair(1000000,it)) }
                yml.getConfigurationSection("mobs")?.let {
                    for (key in it.getKeys(false)){
                        DungeonTower.mythic.getMythicMob(key)?.let { mob ->
                            mobs.add(Pair(it.getInt(key),mob))
                        }
                    }
                }
                coolTime = yml.getInt("cooltime")
                max = yml.getInt("max")
                yOffSet = yml.getDouble("yOffSet")
                radius = yml.getInt("radius")
                level = yml.getDouble("level",1.0)
                activateRange = yml.getInt("activateRange")
                navigateKill = yml.getInt("navigate")
            }

            return Pair(data.internalName, data)
        }
    }
}