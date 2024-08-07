package tororo1066.dungeontower

import com.elmakers.mine.bukkit.api.magic.MagicAPI
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
import tororo1066.dungeontower.script.TodayClearNumberFunction
import tororo1066.dungeontower.logging.TowerLogDB
import tororo1066.dungeontower.save.SaveDataDB
import tororo1066.dungeontower.script.ClearNumberFunction
import tororo1066.dungeontower.script.FloorScript
import tororo1066.tororopluginapi.SInput
import tororo1066.tororopluginapi.SJavaPlugin
import tororo1066.tororopluginapi.SStr
import tororo1066.tororopluginapi.otherUtils.UsefulUtility
import tororo1066.tororopluginapi.sEvent.SEvent
import tororo1066.tororopluginapi.utils.sendMessage
import java.util.UUID

class DungeonTower: SJavaPlugin(UseOption.SConfig) {

    companion object{
        lateinit var plugin: DungeonTower
        var nowX = 0 //ダンジョンができるごとにこの値はダンジョンのx分増える
        var dungeonXSpace = 5 //ダンジョンごとの間隔 近すぎると弊害があるときには空けるべき
        var xLimit = 10000 //nowXがこの数字を上回ったときにnowXを0にする 無駄すぎるチャンク生成を控える
        var y = 1 //ダンジョンのyの高さ
        var lobbyLocation: Location = Location(null,0.0,0.0,0.0)

        lateinit var dungeonWorld: World
        lateinit var floorWorld: World

        val prefix = SStr("&b[&4Dungeon&cTower&b]&r")
        lateinit var mythic: BukkitAPIHelper //スポナーでmmのmobを湧かすために使用
        lateinit var magicAPI: MagicAPI //魔法API
        lateinit var sInput: SInput //入力マネージャー
        lateinit var util: UsefulUtility

        val lootItemData = HashMap<String,LootItemData>() //アイテムのデータ
        val lootData = HashMap<String,LootData>() //宝箱のデータ
        val spawnerData = HashMap<String,SpawnerData>() //スポナーのデータ
        val floorData = HashMap<String,FloorData>() //フロアのデータ
        val towerData = HashMap<String,TowerData>() //塔のデータ
        val partiesData = HashMap<UUID,PartyData?>() //パーティのデータ PartyDataがnullじゃない人がリーダー
        val playNow = ArrayList<UUID>() //ダンジョンに挑戦中のプレイヤー

        var joiningNow = false

        fun CommandSender.sendPrefixMsg(str: SStr){
            this.sendMessage(prefix + str)
        }

        fun reloadDungeonConfig(){
            plugin.reloadConfig()
            xLimit = plugin.config.getInt("xLimit",10000)
            y = plugin.config.getInt("y",1)
            Bukkit.getWorld(plugin.config.getString("dungeonWorld","dungeon")!!)?.let { dungeonWorld = it }
            Bukkit.getWorld(plugin.config.getString("floorWorld","world")!!)?.let { floorWorld = it }
            lobbyLocation = plugin.config.getLocation("lobbyLocation", Location(null,0.0,0.0,0.0))!!
            dungeonXSpace = plugin.config.getInt("dungeonXSpace",5)

            floorData.clear()
            lootData.clear()
            spawnerData.clear()
            towerData.clear()

            sConfig.mkdirs("floors")
            sConfig.loadAllFiles("floors").forEach {
                val floor = FloorData.loadFromYml(it)
                floorData[floor.first] = floor.second
            }

            sConfig.mkdirs("lootItems")
            sConfig.loadAllFiles("lootItems").forEach {
                val lootItem = LootItemData.loadFromYml(it)
                lootItemData[lootItem.first] = lootItem.second
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
        reloadDungeonConfig()
        val dungeonTaskCommand = DungeonTaskCommand()
        getCommand("dungeontask")?.setExecutor(dungeonTaskCommand)
        getCommand("dungeontask")?.tabCompleter = dungeonTaskCommand
        DungeonCommand()
        TowerLogDB()
        TodayClearNumberFunction.registerFunction()
        ClearNumberFunction.registerFunction()
        FloorScript.load()

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
    }
}