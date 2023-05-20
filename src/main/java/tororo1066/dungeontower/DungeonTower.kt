package tororo1066.dungeontower

import io.lumine.mythic.bukkit.BukkitAPIHelper
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.command.CommandSender
import org.bukkit.event.EventPriority
import org.bukkit.event.player.PlayerQuitEvent
import tororo1066.dungeontower.command.DungeonCommand
import tororo1066.dungeontower.command.DungeonTaskCommand
import tororo1066.dungeontower.data.*
import tororo1066.tororopluginapi.SInput
import tororo1066.tororopluginapi.SJavaPlugin
import tororo1066.tororopluginapi.SStr
import tororo1066.tororopluginapi.sEvent.SEvent
import tororo1066.tororopluginapi.utils.sendMessage
import java.util.UUID

class DungeonTower: SJavaPlugin(UseOption.SConfig) {

    companion object{
        lateinit var plugin: DungeonTower
        var nowX = 0
        var xLimit = 10000000
        var y = 1
        var lobbyLocation: Location = Location(null,0.0,0.0,0.0)

        lateinit var dungeonWorld: World
        lateinit var floorWorld: World

        var createFloorNow = false

        val prefix = SStr("&b[&4Dungeon&cTower&b]&r")
        lateinit var mythic: BukkitAPIHelper
        lateinit var sInput: SInput

        val lootData = HashMap<String,LootData>()
        val spawnerData = HashMap<String,SpawnerData>()
        val floorData = HashMap<String,FloorData>()
        val towerData = HashMap<String,TowerData>()
        val partiesData = HashMap<UUID,PartyData?>()
        val playNow = ArrayList<UUID>()

        fun CommandSender.sendPrefixMsg(str: SStr){
            this.sendMessage(prefix + str)
        }

        fun reloadDungeonConfig(){
            plugin.reloadConfig()
            xLimit = plugin.config.getInt("xLimit",10000000)
            y = plugin.config.getInt("y",1)
            Bukkit.getWorld(plugin.config.getString("dungeonWorld","dungeon")!!)?.let { dungeonWorld = it }
            Bukkit.getWorld(plugin.config.getString("floorWorld","world")!!)?.let { floorWorld = it }
            lobbyLocation = plugin.config.getLocation("lobbyLocation", Location(null,0.0,0.0,0.0))!!
        }
    }

    override fun onStart() {
        plugin = this
        mythic = BukkitAPIHelper()
        sInput = SInput(this)
        reloadDungeonConfig()

        sConfig.loadAllFiles("floors").forEach {
            val floor = FloorData.loadFromYml(it)
            floorData[floor.first] = floor.second
        }

        sConfig.loadAllFiles("loots").forEach {
            val loot = LootData.loadFromYml(it)
            lootData[loot.first] = loot.second
        }

        sConfig.loadAllFiles("spawners").forEach {
            val spawner = SpawnerData.loadFromYml(it)
            spawnerData[spawner.first] = spawner.second
        }

        sConfig.loadAllFiles("towers").forEach {
            val tower = TowerData.loadFromYml(it)
            towerData[tower.first] = tower.second
        }
        val dungeonTaskCommand = DungeonTaskCommand()
        getCommand("dungeontask")?.setExecutor(dungeonTaskCommand)
        getCommand("dungeontask")?.tabCompleter = dungeonTaskCommand
        DungeonCommand()

        SEvent(this).register(PlayerQuitEvent::class.java,EventPriority.LOWEST) { e ->
            if (playNow.contains(e.player.uniqueId))return@register
            DungeonCommand.accepts.entries.removeIf { it.value.contains(e.player.uniqueId) }
            DungeonCommand.accepts.remove(e.player.uniqueId)
            partiesData.filter { it.value?.players?.containsKey(e.player.uniqueId) == true }.forEach {
                it.value!!.players.remove(e.player.uniqueId)
            }
            partiesData.remove(e.player.uniqueId)
        }
    }
}