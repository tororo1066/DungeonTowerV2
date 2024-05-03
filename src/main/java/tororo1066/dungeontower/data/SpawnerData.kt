package tororo1066.dungeontower.data

import io.lumine.mythic.api.mobs.MythicMob
import org.bukkit.configuration.file.YamlConfiguration
import tororo1066.dungeontower.DungeonTower
import tororo1066.tororopluginapi.otherUtils.UsefulUtility
import tororo1066.tororopluginapi.script.ScriptFile
import java.io.File
import kotlin.random.Random
import kotlin.random.nextInt

class SpawnerData: Cloneable {

    var internalName = ""
    val mobs = ArrayList<Pair<Int, MythicMob>>()
    var spawnScript: String? = null
    var count = 0
    var coolTime = 0
    var max = 0
    var radius = 0
    var yOffSet = 0.0
    var level = 1.0
    var activateRange = 0

    var kill = 0
    var navigateKill = 0

    private var spawnScriptFile: ScriptFile? = null

    fun randomMob(towerData: TowerData, floorName: String, floorNum: Int): MythicMob? {
        if (spawnScript != null) {
            if (spawnScriptFile == null) {
                spawnScriptFile = ScriptFile(File(DungeonTower.plugin.dataFolder, spawnScript!!))
            }
            val scriptFile = spawnScriptFile!!
            scriptFile.publicVariables.apply {
                this["level"] = level
                this["towerName"] = towerData.internalName
                this["floorName"] = floorName
                this["floorNum"] = floorNum
            }
            val mob = UsefulUtility.sTry({
                (scriptFile.start() as String)
            }, {
                null
            })
            if (mob != null) {
                return DungeonTower.mythic.getMythicMob(mob)
            }
        }

        val random = Random.nextInt(1..1000000)
        var preventRandom = 0
        for (mob in mobs){
            if (preventRandom < random && mob.first + preventRandom > random){
                return mob.second
            }
            preventRandom += mob.first
        }

        return null
    }

    fun getLevel(towerData: TowerData, floorName: String, floorNum: Int): Double {
        val script = towerData.levelModifierScript?:return level
        val scriptFile = ScriptFile(File(DungeonTower.plugin.dataFolder, script))
        scriptFile.publicVariables["level"] = level
        scriptFile.publicVariables["towerName"] = towerData.internalName
        scriptFile.publicVariables["floorName"] = floorName
        scriptFile.publicVariables["floorNum"] = floorNum
        val level = UsefulUtility.sTry({
            (scriptFile.start() as Number).toDouble()
        }, {
            level
        })

        return level
    }

    public override fun clone(): SpawnerData {
        return super.clone() as SpawnerData
    }

    companion object{
        fun loadFromYml(file: File): Pair<String, SpawnerData> {
            val yml = YamlConfiguration.loadConfiguration(file)
            val data = SpawnerData().apply {
                internalName = file.nameWithoutExtension
                yml.getConfigurationSection("mobs")?.let {
                    for (key in it.getKeys(false)){
                        DungeonTower.mythic.getMythicMob(key)?.let { mob ->
                            mobs.add(Pair(it.getInt(key),mob))
                        }
                    }
                }
                spawnScript = yml.getString("spawnScript")
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