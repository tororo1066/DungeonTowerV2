package tororo1066.dungeontower.data

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import tororo1066.dungeontower.DungeonTower
import tororo1066.dungeontower.DungeonTower.Companion.sendPrefixMsg
import tororo1066.tororopluginapi.SStr
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
                return floor.second.newInstance()
            }
            preventRandom = floor.first
        }
        throw NullPointerException("Couldn't find floor. Maybe sum percentage is not 1000000.")
    }

    fun canChallenge(p: Player, partyData: PartyData): Boolean {
        if (partyData.players.size > partyLimit){
            p.sendPrefixMsg(SStr("&4${partyLimit}人以下でしか入れません (現在:${partyData.players.size}人)"))
            return false
        }

        if (challengeItem != null){
            val filter = p.inventory.filter { it?.isSimilar(challengeItem) == true }
            if (filter.isEmpty() || filter.sumOf { it.amount } < challengeItem!!.amount){
                p.sendPrefixMsg(SStr("§c挑戦するためのアイテムがありません！"))
                return false
            }

            var amount = challengeItem!!.amount
            for (item in filter){
                if (item.amount < amount){
                    amount -= item.amount
                    item.amount = 0
                } else {
                    item.amount -= amount
                    break
                }
            }

        }


        return true
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
                        val floorData = (DungeonTower.floorData[split[1]]?:throw NullPointerException("Failed load FloorData to ${split[1]} in ${file.nameWithoutExtension}.")).newInstance()
                        floors[floorNum]!!.add(Pair(split[0].toInt(), floorData))
                    }
                }

                challengeItem = yml.getItemStack("challengeItem")
            }

            return Pair(data.internalName, data)
        }
    }
}