package tororo1066.dungeontower

import com.elmakers.mine.bukkit.api.magic.MagicAPI
import io.lumine.mythic.bukkit.BukkitAPIHelper
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.command.CommandSender
import org.bukkit.event.EventPriority
import org.bukkit.event.player.PlayerQuitEvent
import tororo1066.displaymonitorapi.IDisplayMonitor
import tororo1066.displaymonitorapi.configuration.IActionConfiguration
import tororo1066.displaymonitorapi.storage.IActionStorage
import tororo1066.dungeontower.command.DungeonCommand
import tororo1066.dungeontower.data.*
import tororo1066.dungeontower.data.loot.ItemLoot
import tororo1066.dungeontower.data.loot.ShuffleLoot
import tororo1066.dungeontower.data.loot.SupplyLoot
import tororo1066.dungeontower.dmonitor.FinishTaskAction
import tororo1066.dungeontower.dmonitor.SetScoreboardLine
import tororo1066.dungeontower.dmonitor.TargetParty
import tororo1066.dungeontower.logging.TowerLogDB
import tororo1066.dungeontower.save.SaveDataDB
import tororo1066.dungeontower.script.ClearNumberFunction
import tororo1066.dungeontower.script.FloorScript
import tororo1066.dungeontower.script.TodayClearNumberFunction
import tororo1066.dungeontower.script.TodayEntryNumberFunction
import tororo1066.tororopluginapi.SInput
import tororo1066.tororopluginapi.SJavaPlugin
import tororo1066.tororopluginapi.SStr
import tororo1066.tororopluginapi.otherPlugin.SWorldGuardAPI
import tororo1066.tororopluginapi.otherUtils.UsefulUtility
import tororo1066.tororopluginapi.sEvent.SEvent
import tororo1066.tororopluginapi.utils.sendMessage
import tororo1066.tororopluginapi.world.EmptyWorldGenerator
import java.util.UUID

class DungeonTower: SJavaPlugin(UseOption.SConfig) {

    companion object{
        lateinit var plugin: DungeonTower
        var y = 1 //ダンジョンのyの高さ
        var lobbyLocation: Location = Location(null,0.0,0.0,0.0)
        lateinit var floorWorld: World

        val prefix = SStr("&b[&4Dungeon&cTower&b]&r")
        lateinit var mythic: BukkitAPIHelper //スポナーでmmのmobを湧かすために使用
        lateinit var magicAPI: MagicAPI //魔法API
        lateinit var sInput: SInput //入力マネージャー
        lateinit var util: UsefulUtility
        lateinit var worldGenerator: EmptyWorldGenerator
        lateinit var sWorldGuard: SWorldGuardAPI
        lateinit var actionStorage: IActionStorage

        val lootData = HashMap<String, LootData>() //宝箱のデータ
        val lootScripts = HashMap<String, IActionConfiguration>()
        val spawnerData = HashMap<String, SpawnerData>() //スポナーのデータ
        val floorData = HashMap<String, FloorData>() //フロアのデータ
        val towerData = HashMap<String, TowerData>() //塔のデータ
        val partiesData = HashMap<UUID, PartyData?>() //パーティのデータ PartyDataがnullじゃない人がリーダー
        val playNow = ArrayList<UUID>() //ダンジョンに挑戦中のプレイヤー

        const val DUNGEON_LOOT_ITEM = "dlootitem"
        const val DUNGEON_LOOT_ANNOUNCE = "dlootannounce"
        const val DUNGEON_LOOT_REMOVE_FLOOR_COUNT = "dlootremovefloor"
        const val DUNGEON_LOOT_REMOVE_ON_EXIT = "dlootremoveonexit"
        const val DUNGEON_LOOT_REMOVE_ON_DEATH = "dlootremoveondeath"

        fun CommandSender.sendPrefixMsg(str: SStr){
            this.sendMessage(prefix + str)
        }

        fun reloadDungeonConfig(){
            plugin.reloadConfig()
            y = plugin.config.getInt("y",1)
            Bukkit.getWorld(plugin.config.getString("floorWorld","world")!!)?.let { floorWorld = it }
            lobbyLocation = plugin.config.getLocation("lobbyLocation", Location(null,0.0,0.0,0.0))!!

            floorData.clear()
            lootData.clear()
            spawnerData.clear()
            towerData.clear()
            lootScripts.clear()

            sConfig.mkdirs("floors")
            sConfig.loadAllFiles("floors").forEach {
                val floor = FloorData.loadFromYml(it)
                floorData[floor.first] = floor.second
            }

            sConfig.mkdirs("loots")
            sConfig.loadAllFiles("loots").forEach {
                val loot = LootData.loadFromYml(it)
                lootData[loot.first] = loot.second
            }

            sConfig.mkdirs("spawners")
            sConfig.loadAllFiles("spawners").forEach {
                val spawner = SpawnerData.loadFromYml(it)
                spawnerData[spawner.first] = spawner.second
            }

            sConfig.mkdirs("towers")
            sConfig.loadAllFiles("towers").forEach {
                val tower = TowerData.loadFromYml(it)
                towerData[tower.first] = tower.second
            }

            sConfig.mkdirs("DisplayMonitorScripts/loot")
            sConfig.loadAllFiles("DisplayMonitorScripts/loot").forEach {
                val loots = actionStorage.getActionConfigurations(it)
                loots.forEach { loot ->
                    lootScripts[loot.key] = loot
                }
            }


            DungeonCommand()
            TowerLogDB()
            SaveDataDB
            FloorScript.load()
        }
    }

    override fun onStart() {
        plugin = this
        mythic = BukkitAPIHelper()
        magicAPI = Bukkit.getPluginManager().getPlugin("Magic") as MagicAPI
        sInput = SInput(this)
        util = UsefulUtility(this)
        worldGenerator = EmptyWorldGenerator()
        sWorldGuard = SWorldGuardAPI()
        actionStorage = IDisplayMonitor.DisplayMonitorInstance.getInstance().actionStorage
        reloadDungeonConfig()
        DungeonCommand()
        TowerLogDB()
        TodayClearNumberFunction.registerFunction()
        ClearNumberFunction.registerFunction()
        TodayEntryNumberFunction.registerFunction()
        FloorScript.load()

        actionStorage.registerAction("FinishTask", FinishTaskAction::class.java)
        actionStorage.registerAction("SetScoreboardLine", SetScoreboardLine::class.java)
        actionStorage.registerAction("TargetParty", TargetParty::class.java)

        actionStorage.registerAction(SupplyLoot::class.java)
        actionStorage.registerAction(ItemLoot::class.java)
        actionStorage.registerAction(ShuffleLoot::class.java)

        SEvent(this).register(PlayerQuitEvent::class.java, EventPriority.LOWEST) { e ->
            if (playNow.contains(e.player.uniqueId))return@register
            DungeonCommand.accepts.entries.removeIf { it.value.contains(e.player.uniqueId) }
            DungeonCommand.accepts.remove(e.player.uniqueId)
            partiesData.filter { it.value?.players?.containsKey(e.player.uniqueId) == true }.forEach {
                it.value!!.players.remove(e.player.uniqueId)
            }
            partiesData.remove(e.player.uniqueId)
            DungeonCommand.entryCooldown.remove(e.player.uniqueId)
        }

        server.messenger.registerOutgoingPluginChannel(this,"BungeeCord")

        Bukkit.getWorlds().forEach {
            if (it.name.startsWith("${name.lowercase()}_dungeon_")) {
                Bukkit.unloadWorld(it, false)
            }
        }

        Bukkit.getWorldContainer().listFiles()?.forEach {
            if (it.name.startsWith("${name.lowercase()}_dungeon_")) {
                it.deleteRecursively()
            }
        }
    }

    override fun onEnd() {

    }
}