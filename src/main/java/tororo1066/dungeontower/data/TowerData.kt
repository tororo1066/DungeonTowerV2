package tororo1066.dungeontower.data

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Server
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import tororo1066.dungeontower.DungeonTower
import tororo1066.dungeontower.DungeonTower.Companion.sendPrefixMsg
import tororo1066.dungeontower.command.DungeonCommand
import tororo1066.dungeontower.save.SaveDataDB
import tororo1066.dungeontower.task.DungeonTowerTask
import tororo1066.tororopluginapi.SStr
import tororo1066.tororopluginapi.script.ScriptFile
import java.io.File
import java.util.concurrent.CompletableFuture
import kotlin.random.Random
import kotlin.random.nextInt

class TowerData: Cloneable {

    var internalName = ""
    //名前
    var name = ""
    //挑戦可能な最大人数
    var partyLimit = -1
    //フロアたち keyは階層、Pairのfirstは確率
    val firstFloor = ArrayList<Pair<Int,FloorData>>()
    var challengeItem: ItemStack? = null
    var challengeScript: String? = null

    var entryScript: String? = null

    var defaultParkPoints = 0

    fun randomFloor(): FloorData {
        val random = Random.nextInt(1..1000000)
        var preventRandom = 0
        for (floor in firstFloor){
            if (preventRandom < random && floor.first + preventRandom > random){
                return floor.second.newInstance()
            }
            preventRandom = floor.first
        }
        throw NullPointerException("Couldn't find floor. Maybe sum percentage is not 1000000.")
    }

    fun entryTower(p: Player, partyData: PartyData): CompletableFuture<Void> {
        DungeonCommand.entryCooldown.add(p.uniqueId)
        return canChallenge(p, partyData).thenAcceptAsync { bool ->
            if (!bool) return@thenAcceptAsync
            if (entryScript != null){
                val scriptFile = ScriptFile(File(DungeonTower.plugin.dataFolder, "$entryScript"))
                scriptFile.debug = true
                scriptFile.publicVariables["name"] = p.name
                scriptFile.publicVariables["uuid"] = p.uniqueId.toString()
                scriptFile.publicVariables["ip"] = p.address.address.hostAddress
                val data = SaveDataDB.load(p.uniqueId).get()
                val saveData = data.find { it.towerName == internalName }
                saveData?.let {
                    scriptFile.publicVariables["floors"] = it.floors.mapKeys { map -> map.key.toString() }.plus("size" to it.floors.size)
                } ?: run {
                    scriptFile.publicVariables["floors"] = mapOf("size" to 0)
                }

                val result = scriptFile.start()
                if (result !is String) {
                    p.sendPrefixMsg(SStr("&4エラー。 運営に報告してください"))
                    Bukkit.broadcast(Component.text(
                        "${DungeonTower.prefix}§4§l[ERROR] §r§c${scriptFile.file.name}の戻り値がStringではありません"
                    ), Server.BROADCAST_CHANNEL_ADMINISTRATIVE)
                    DungeonCommand.entryCooldown.remove(p.uniqueId)
                    return@thenAcceptAsync
                }

                val split = result.split(",")
                val floorNum = split[0].toInt()
                val floorName = split.getOrNull(1)

                val floorData = if (floorName != null){
                    DungeonTower.floorData[floorName]
                } else {
                    saveData?.let {
                        if (floorNum < it.floors.size){
                            DungeonTower.floorData[it.floors[floorNum]?.lastOrNull()?.get("internalName")]
                        } else {
                            null
                        }
                    }
                }?.newInstance()?.apply {
                    saveData?.let {
                        it.floors[floorNum]?.find { find -> find["internalName"] == internalName }?.let { data ->
                            loadData(data)
                        }
                    }
                }
                partyData.players.keys.forEach {
                    DungeonTower.playNow.add(it)
                }
                val floor = floorData?.let { it to floorNum }
                DungeonTower.util.runTask {
                    DungeonTowerTask(partyData, this, floor).start()
                }

            } else {
                partyData.players.keys.forEach {
                    DungeonTower.playNow.add(it)
                }
                DungeonTower.util.runTask {
                    DungeonTowerTask(partyData, this).start()
                }
            }

            DungeonCommand.entryCooldown.remove(p.uniqueId)
        }
    }

    fun canChallenge(p: Player, partyData: PartyData): CompletableFuture<Boolean> {
        if (partyData.players.size > partyLimit){
            p.sendPrefixMsg(SStr("&4${partyLimit}人以下でしか入れません (現在:${partyData.players.size}人)"))
            return CompletableFuture.completedFuture(false)
        }

        val completableFuture = CompletableFuture.completedFuture(true)

        if (challengeScript != null){
            val scriptFile = ScriptFile(File(DungeonTower.plugin.dataFolder, "$challengeScript"))
            scriptFile.debug = true
            scriptFile.publicVariables["name"] = p.name
            scriptFile.publicVariables["uuid"] = p.uniqueId.toString()
            scriptFile.publicVariables["ip"] = p.address.address.hostAddress
            completableFuture.completeAsync {
                val result = scriptFile.start()
                if (result is Boolean){
                    if (!result){
                        p.sendPrefixMsg(SStr("§c挑戦するための条件を満たしていません！"))
                        return@completeAsync false
                    } else {
                        return@completeAsync true
                    }
                } else {
                    p.sendPrefixMsg(SStr("&4エラー。 運営に報告してください"))
                    Bukkit.broadcast(Component.text(
                        "${DungeonTower.prefix}§4§l[ERROR] §r§c${scriptFile.file.name}の戻り値がBooleanではありません"
                    ), Server.BROADCAST_CHANNEL_ADMINISTRATIVE)
                    return@completeAsync false
                }
            }

        }

        if (challengeItem != null){
            completableFuture.completeAsync {
                val filter = p.inventory.filter { it?.isSimilar(challengeItem) == true }
                if (filter.isEmpty() || filter.sumOf { it.amount } < challengeItem!!.amount){
                    p.sendPrefixMsg(SStr("§c挑戦するためのアイテムがありません！"))
                    return@completeAsync false
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

                return@completeAsync true
            }
        }

        return completableFuture
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
                yml.getStringList("firstFloor").forEach {
                    val split = it.split(",")
                    val floorData = (DungeonTower.floorData[split[1]]
                        ?: throw NullPointerException("Failed load FloorData to ${split[1]} in ${file.nameWithoutExtension}.")).newInstance()
                    firstFloor.add(Pair(split[0].toInt(), floorData))
                }

                challengeItem = yml.getItemStack("challengeItem")
                challengeScript = yml.getString("challengeScript")
                defaultParkPoints = yml.getInt("defaultParkPoints",0)
            }

            return Pair(data.internalName, data)
        }
    }
}