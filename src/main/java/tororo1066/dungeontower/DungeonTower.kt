package tororo1066.dungeontower

import com.elmakers.mine.bukkit.action.ActionFactory
import com.elmakers.mine.bukkit.api.magic.MagicAPI
import com.github.shynixn.mccoroutine.bukkit.asyncDispatcher
import com.sk89q.worldguard.WorldGuard
import com.sk89q.worldguard.protection.regions.RegionContainer
import io.lumine.mythic.bukkit.BukkitAPIHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.World
import org.bukkit.command.CommandSender
import org.bukkit.event.EventPriority
import org.bukkit.event.player.PlayerQuitEvent
import tororo1066.displaymonitorapi.IDisplayMonitor
import tororo1066.displaymonitorapi.storage.IActionStorage
import tororo1066.dungeontower.command.DungeonCommand
import tororo1066.dungeontower.command.DungeonTaskCommand
import tororo1066.dungeontower.data.*
import tororo1066.dungeontower.dmonitor.*
import tororo1066.dungeontower.dmonitor.floor.CallFloor
import tororo1066.dungeontower.dmonitor.floor.CheckConflictFloor
import tororo1066.dungeontower.dmonitor.floor.FilterNotConflictFloor
import tororo1066.dungeontower.dmonitor.loot.ItemLoot
import tororo1066.dungeontower.dmonitor.loot.ShuffleLoot
import tororo1066.dungeontower.dmonitor.loot.SupplyLoot
import tororo1066.dungeontower.dmonitor.spawner.SpawnMob
import tororo1066.dungeontower.dmonitor.tower.AllowEntry
import tororo1066.dungeontower.dmonitor.tower.SelectFloor
import tororo1066.dungeontower.dmonitor.workspace.FloorWorkspace
import tororo1066.dungeontower.dmonitor.workspace.LootWorkspace
import tororo1066.dungeontower.dmonitor.workspace.SpawnerWorkspace
import tororo1066.dungeontower.dmonitor.workspace.TowerWorkspace
import tororo1066.dungeontower.logging.TowerLogDB
import tororo1066.dungeontower.weaponSystem.Attribute
import tororo1066.dungeontower.weaponSystem.magic.AppendDungeonLore
import tororo1066.tororopluginapi.SInput
import tororo1066.tororopluginapi.SJavaPlugin
import tororo1066.tororopluginapi.SStr
import tororo1066.tororopluginapi.otherPlugin.SWorldGuardAPI
import tororo1066.tororopluginapi.otherUtils.UsefulUtility
import tororo1066.tororopluginapi.sEvent.SEvent
import tororo1066.tororopluginapi.utils.sendMessage
import java.util.UUID

class DungeonTower: SJavaPlugin(UseOption.SConfig) {

    companion object{
        lateinit var plugin: DungeonTower
        var y = 1 //ダンジョンのyの高さ
        var lobbyLocation: Location = Location(null,0.0,0.0,0.0)
        lateinit var floorWorld: World
        var customWeaponEnabled = false
        var autoCreateCustomWeapon = false
        var worldUsage = WorldUsage.REUSE

        val prefix = SStr("&b[&4Dungeon&cTower&b]&r")
        lateinit var mythic: BukkitAPIHelper //スポナーでmmのmobを湧かすために使用
        lateinit var magicAPI: MagicAPI //魔法API
        lateinit var sInput: SInput //入力マネージャー
        lateinit var util: UsefulUtility
        lateinit var worldGenerator: EmptyWorldGenerator
        lateinit var sWorldGuard: SWorldGuardAPI
        lateinit var regionContainer: RegionContainer
        lateinit var actionStorage: IActionStorage

        val lootData = HashMap<String, LootData>() //宝箱のデータ
        val spawnerData = HashMap<String, SpawnerData>() //スポナーのデータ
        val floorData = HashMap<String, FloorData>() //フロアのデータ
        val towerData = HashMap<String, TowerData>() //塔のデータ

        val partiesData = HashMap<UUID, PartyData?>() //パーティのデータ PartyDataがnullじゃない人がリーダー
        val playNow = ArrayList<UUID>() //ダンジョンに挑戦中のプレイヤー

        val worlds = ArrayList<UUID>() //ダンジョンのワールドのUUID

        val DUNGEON_LOOT_CHEST by lazy {
            NamespacedKey(plugin, "dungeon_loot_chest")
        }
        const val DUNGEON_LOOT_ITEM = "dlootitem"
        const val DUNGEON_LOOT_ANNOUNCE = "dlootannounce"
        const val DUNGEON_LOOT_REMOVE_FLOOR_COUNT = "dlootremovefloor"
        const val DUNGEON_LOOT_REMOVE_ON_EXIT = "dlootremoveonexit"
        const val DUNGEON_LOOT_REMOVE_ON_DEATH = "dlootremoveondeath"

        const val DUNGEON_MOB = "dmob"

        val scope by lazy {
            CoroutineScope(SupervisorJob() + plugin.asyncDispatcher)
        }

        fun CommandSender.sendPrefixMsg(str: SStr){
            this.sendMessage(prefix + str)
        }

        fun reloadDungeonConfig(){
            plugin.reloadConfig()
            y = plugin.config.getInt("y",1)
            customWeaponEnabled = plugin.config.getBoolean("customWeaponEnabled")
            autoCreateCustomWeapon = plugin.config.getBoolean("autoCreateCustomWeapon", false)
            worldUsage = WorldUsage.valueOf(plugin.config.getString("worldUsage","REUSE")!!.uppercase())

            Bukkit.getWorld(plugin.config.getString("floorWorld","world")!!)?.let { floorWorld = it }
            lobbyLocation = plugin.config.getLocation("lobbyLocation", Location(null,0.0,0.0,0.0))!!

            floorData.clear()
            lootData.clear()
            spawnerData.clear()
            towerData.clear()

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

            FloorWorkspace.load()
            LootWorkspace.load()
            SpawnerWorkspace.load()
            TowerWorkspace.load()

            Attribute.loadAttributes()

            DungeonCommand()
            TowerLogDB.reload()
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
        regionContainer = WorldGuard.getInstance().platform.regionContainer
        actionStorage = IDisplayMonitor.DisplayMonitorInstance.getInstance().actionStorage
        reloadDungeonConfig()
        DungeonCommand()
        DungeonTaskCommand()
        TowerLogDB

        actionStorage.registerAction(GetSubAccounts::class.java)
        actionStorage.registerAction(FinishTask::class.java)
        actionStorage.registerAction(SetScoreboardLine::class.java)
        actionStorage.registerAction(TargetParty::class.java)
        actionStorage.registerAction(InDungeon::class.java)
        actionStorage.registerAction(SetLobbyLocation::class.java)
        actionStorage.registerAction(CompleteDungeon::class.java)
        actionStorage.registerAction(SetCurrentTime::class.java)

        actionStorage.registerAction(SupplyLoot::class.java)
        actionStorage.registerAction(ItemLoot::class.java)
        actionStorage.registerAction(ShuffleLoot::class.java)

        actionStorage.registerAction(SpawnMob::class.java)

        actionStorage.registerAction(CallFloor::class.java)
        actionStorage.registerAction(CheckConflictFloor::class.java)
        actionStorage.registerAction(FilterNotConflictFloor::class.java)

        actionStorage.registerAction(AllowEntry::class.java)
        actionStorage.registerAction(SelectFloor::class.java)


        val workspaceStorage = IDisplayMonitor.DisplayMonitorInstance.getInstance().workspaceStorage
        workspaceStorage.registerWorkspace(FloorWorkspace)
        workspaceStorage.registerWorkspace(LootWorkspace)
        workspaceStorage.registerWorkspace(SpawnerWorkspace)
        workspaceStorage.registerWorkspace(TowerWorkspace)

        ActionFactory.registerActionClass("AppendDungeonLore", AppendDungeonLore::class.java)

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

        DungeonWorldUnloadTask()
    }

    override fun onEnd() {
        TowerLogDB.database.close()
        scope.cancel()
    }
}