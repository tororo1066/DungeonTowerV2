package tororo1066.dungeontower.data

import kotlinx.coroutines.future.await
import org.bukkit.GameRule
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import tororo1066.displaymonitorapi.actions.IActionContext
import tororo1066.dungeontower.DungeonTower
import tororo1066.dungeontower.DungeonTower.Companion.sendPrefixMsg
import tororo1066.dungeontower.command.DungeonCommand
import tororo1066.dungeontower.dmonitor.workspace.TowerWorkspace
import tororo1066.dungeontower.task.DungeonTowerTask
import tororo1066.tororopluginapi.SStr
import java.io.File
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

    private fun createActionContext(p: Player, partyData: PartyData): IActionContext {
        val context = DungeonTower.actionStorage.createActionContext(
            DungeonTower.actionStorage.createPublicContext().apply {
                parameters["entry.name"] = p.name
                parameters["entry.uuid"] = p.uniqueId.toString()
                parameters["entry.ip"] = p.address?.address?.hostAddress ?: "Unknown"
                parameters["party.uuid"] = partyData.partyUUID.toString()
                workspace = TowerWorkspace
            }
        ).apply {
            target = p
            location = p.location
        }

        return context
    }

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

    suspend fun entryTower(p: Player, partyData: PartyData) {
        DungeonCommand.entryCooldown.add(p.uniqueId)
        try {
            if (!canChallenge(p, partyData)) {
                DungeonCommand.entryCooldown.remove(p.uniqueId)
                return
            }
            if (entryScript != null){
                val script = TowerWorkspace.actionConfigurations[entryScript]
                    ?: throw NullPointerException("Entry script $entryScript not found in TowerWorkspace.")

                val context = createActionContext(p, partyData)

                script.run(context, true, null).await()

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
        } catch (ex: Exception) {
            p.sendPrefixMsg(SStr("&cタワーへの入場中にエラーが発生しました"))
            DungeonTower.plugin.logger.warning("Error while $internalName entry tower for ${p.name} (${p.uniqueId})")
            ex.printStackTrace()
        } finally {
            DungeonCommand.entryCooldown.remove(p.uniqueId)
        }
    }

    suspend fun canChallenge(p: Player, partyData: PartyData): Boolean {
        if (playerLimit != -1 && DungeonTower.partiesData.count { it.value != null && it.value!!.currentTask?.tower?.internalName == internalName } >= playerLimit){
            p.sendPrefixMsg(SStr("&4最大並行プレイ人数の上限に達しています"))
            return false
        }

        if (partyLimit != -1 && partyData.players.size > partyLimit){
            p.sendPrefixMsg(SStr("&4${partyLimit}人以下でしか入れません (現在:${partyData.players.size}人)"))
            return false
        }

        if (challengeScript != null){
            val script = TowerWorkspace.actionConfigurations[challengeScript]
                ?: throw NullPointerException("Challenge script $challengeScript not found in TowerWorkspace.")

            val context = createActionContext(p, partyData)

            script.run(
                context,
                true,
                null
            ).await()

            val allowed = context.publicContext.parameters["entry.allowed"] as? Boolean ?: true
            if (!allowed) {
                p.sendPrefixMsg(SStr("§c挑戦するための条件を満たしていません！"))
                return false
            }
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