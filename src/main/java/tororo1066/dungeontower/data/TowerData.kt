package tororo1066.dungeontower.data

import org.bukkit.GameRule
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import tororo1066.dungeontower.DungeonTower
import tororo1066.dungeontower.DungeonTower.Companion.sendPrefixMsg
import tororo1066.dungeontower.command.DungeonCommand
import tororo1066.dungeontower.dmonitor.workspace.TowerWorkspace
import tororo1066.dungeontower.task.DungeonTowerTask
import tororo1066.tororopluginapi.SStr
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
    var autoCreateParty = false
    //フロアたち keyは階層、Pairのfirstは確率
    val firstFloor = ArrayList<Pair<Int,FloorData>>()
    var challengeItem: ItemStack? = null
    var challengeScript: String? = null

    var entryScript: String? = null

    var playerLimit = -1

    var regionFlags = mutableMapOf<String, String>()

    val worldGameRules = mutableMapOf<GameRule<*>, Any>(
        GameRule.DO_DAYLIGHT_CYCLE to false,
        GameRule.DO_WEATHER_CYCLE to false,
        GameRule.DO_MOB_SPAWNING to false,
        GameRule.MOB_GRIEFING to false,
        GameRule.KEEP_INVENTORY to true,
    )

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
                val script = TowerWorkspace.actionConfigurations[entryScript]

                if (script == null) {
                    p.sendPrefixMsg(SStr("&c入場時に実行するスクリプトが見つかりません"))
                    DungeonCommand.entryCooldown.remove(p.uniqueId)
                    return@thenAcceptAsync
                }

                val context = DungeonTower.actionStorage.createActionContext(
                    DungeonTower.actionStorage.createPublicContext().apply {
                        parameters["entry.name"] = p.name
                        parameters["entry.uuid"] = p.uniqueId.toString()
                        parameters["entry.ip"] = p.address.address.hostAddress
                        parameters["party.uuid"] = partyData.partyUUID.toString()
                        workspace = TowerWorkspace
                    }
                ).apply {
                    target = p
                    location = p.location
                }
                script.run(context, true, null).join()

                val floorNum = (context.publicContext.parameters["entry.floor.num"] as? Int)
                val floorName = context.publicContext.parameters["entry.floor.name"] as? String
                val floorData = DungeonTower.floorData[floorName]?.newInstance()
                partyData.players.keys.forEach {
                    DungeonTower.playNow.add(it)
                }
                DungeonTower.util.runTask {
                    DungeonTowerTask(partyData, this, floorData to floorNum).start()
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
        }.exceptionally { ex ->
            p.sendPrefixMsg(SStr("&cタワーへの入場中にエラーが発生しました"))
            DungeonTower.plugin.logger.warning("Error while $internalName entry tower for ${p.name} (${p.uniqueId})")
            ex.printStackTrace()
            DungeonCommand.entryCooldown.remove(p.uniqueId)
            null
        }
    }

    fun canChallenge(p: Player, partyData: PartyData): CompletableFuture<Boolean> {
        if (playerLimit != -1 && DungeonTower.partiesData.count { it.value != null && it.value!!.currentTask?.tower?.internalName == internalName } >= playerLimit){
            p.sendPrefixMsg(SStr("&4最大並行プレイ人数の上限に達しています"))
            DungeonCommand.entryCooldown.remove(p.uniqueId)
            return CompletableFuture.completedFuture(false)
        }

        if (partyLimit != -1 && partyData.players.size > partyLimit){
            p.sendPrefixMsg(SStr("&4${partyLimit}人以下でしか入れません (現在:${partyData.players.size}人)"))
            DungeonCommand.entryCooldown.remove(p.uniqueId)
            return CompletableFuture.completedFuture(false)
        }

        var completableFuture = CompletableFuture.completedFuture(true)

        if (challengeScript != null){
            val script = TowerWorkspace.actionConfigurations[challengeScript]
                ?: throw NullPointerException("Challenge script $challengeScript not found in TowerWorkspace.")
            completableFuture = completableFuture.thenApplyAsync {
                val context = DungeonTower.actionStorage.createActionContext(
                    DungeonTower.actionStorage.createPublicContext().apply {
                        parameters["entry.name"] = p.name
                        parameters["entry.uuid"] = p.uniqueId.toString()
                        parameters["entry.ip"] = p.address.address.hostAddress
                        parameters["party.uuid"] = partyData.partyUUID.toString()
                        workspace = TowerWorkspace
                    }
                ).apply {
                    target = p
                    location = p.location
                }
                script.run(
                    context,
                    true,
                    null
                ).join()

                val allowed = context.publicContext.parameters["entry.allowed"] as? Boolean ?: true
                if (!allowed) {
                    p.sendPrefixMsg(SStr("§c挑戦するための条件を満たしていません！"))
                    DungeonCommand.entryCooldown.remove(p.uniqueId)
                    return@thenApplyAsync false
                } else {
                    DungeonCommand.entryCooldown.remove(p.uniqueId)
                    return@thenApplyAsync true
                }
            }

        }

        if (challengeItem != null){
            completableFuture = completableFuture.thenApplyAsync { bool ->
                if (!bool) return@thenApplyAsync false
                val filter = p.inventory.filter { it?.isSimilar(challengeItem) == true }
                if (filter.isEmpty() || filter.sumOf { it.amount } < challengeItem!!.amount){
                    p.sendPrefixMsg(SStr("§c挑戦するためのアイテムがありません！"))
                    DungeonCommand.entryCooldown.remove(p.uniqueId)
                    return@thenApplyAsync false
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

                DungeonCommand.entryCooldown.remove(p.uniqueId)
                return@thenApplyAsync true
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
                autoCreateParty = yml.getBoolean("autoCreateParty",false)
                yml.getStringList("firstFloor").forEach {
                    val split = it.split(",")
                    val floorData = (DungeonTower.floorData[split[1]]
                        ?: throw NullPointerException("Failed load FloorData to ${split[1]} in ${file.nameWithoutExtension}.")).newInstance()
                    firstFloor.add(Pair(split[0].toInt(), floorData))
                }

                challengeItem = yml.getItemStack("challengeItem")
                challengeScript = yml.getString("challengeScript")
                entryScript = yml.getString("entryScript")
                playerLimit = yml.getInt("playerLimit",-1)
                val section = yml.getConfigurationSection("worldGameRules")
                if (section != null) {
                    worldGameRules.clear()
                    section.getKeys(false).forEach {
                        val rule = GameRule.getByName(it) ?: return@forEach
                        worldGameRules[rule] = yml.get("worldGameRules.$it") ?: return@forEach
                    }
                }
                val flags = yml.getConfigurationSection("regionFlags")
                if (flags != null) {
                    regionFlags.clear()
                    flags.getKeys(false).forEach {
                        regionFlags[it] = yml.getString("regionFlags.$it") ?: return@forEach
                    }
                }
            }

            return Pair(data.internalName, data)
        }
    }
}