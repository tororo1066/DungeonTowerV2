package tororo1066.dungeontower.data

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.inventory.ItemStack
import tororo1066.dungeontower.DungeonTower
import java.io.File
import kotlin.random.Random
import kotlin.random.nextInt

class TowerData: Cloneable {

    var internalName = ""
    //名前
    var name = ""
    //挑戦可能な最大人数 -1でなし
    var partyLimit = -1
    //フロアたち keyは階層、Pairのfirstは確率
    val floors = HashMap<Int,ArrayList<Pair<Int,FloorData>>>()
    var challengeItem: ItemStack? = null

    fun randomFloor(floorNum: Int): FloorData {
        val random = Random.nextInt(1..1000000)
        var preventRandom = 0
        for (floor in floors[floorNum]!!){
            if (preventRandom < random && floor.first + preventRandom > random){
                return floor.second.clone()
            }
            preventRandom = floor.first
        }
        throw NullPointerException("Couldn't find floor  Maybe sum percentage is not 1000000.")
    }

    public override fun clone(): TowerData {
        return super.clone() as TowerData
    }

    companion object{
        fun loadFromYml(file: File): Pair<String, TowerData> {
            val yml = YamlConfiguration.loadConfiguration(file)
            val data = TowerData().apply {
                internalName = file.nameWithoutExtension
                name = yml.getString("name","null")!!
                partyLimit = yml.getInt("partyLimit",-1)

                val configFloors = yml.getConfigurationSection("floors")!!
                for (key in configFloors.getKeys(false)){
                    val floorNum = key.substring(0,1).toInt()
                    floors[floorNum] = arrayListOf()
                    configFloors.getStringList(key).forEach {
                        val split = it.split(",")
                        val floorData = (DungeonTower.floorData[split[1]]?:throw NullPointerException("Failed load FloorData to ${split[1]} in ${file.nameWithoutExtension}.")).clone()
                        floors[floorNum]!!.add(Pair(split[0].toInt(), floorData))
                    }
                }

                challengeItem = yml.getItemStack("challengeItem")
            }

            return Pair(data.internalName, data)
        }
    }
}