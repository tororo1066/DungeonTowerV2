package tororo1066.dungeontower

import com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent
import net.kyori.adventure.text.Component
import org.bukkit.*
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.Inventory
import org.bukkit.persistence.PersistentDataType
import org.bukkit.scoreboard.DisplaySlot
import tororo1066.dungeontower.data.FloorData
import tororo1066.dungeontower.data.PartyData
import tororo1066.dungeontower.data.TowerData
import tororo1066.dungeontower.sql.DungeonTowerLogSQL
import tororo1066.dungeontower.sql.DungeonTowerPartyLogSQL
import tororo1066.tororopluginapi.SStr
import tororo1066.tororopluginapi.sEvent.SEvent
import tororo1066.tororopluginapi.utils.toPlayer
import java.util.*
import kotlin.collections.ArrayList

class DungeonTowerTask(val party: PartyData, val tower: TowerData): Thread() {

    class Lock{

        @Volatile
        private var isLock = false
        @Volatile
        private var hadLocked = false

        fun lock(){
            synchronized(this){
                if (hadLocked){
                    return
                }
                isLock = true
            }
            try {
                while (isLock){ sleep(1) }
            } catch (_: InterruptedException) {

            }
        }

        fun unlock(){
            synchronized(this){
                hadLocked = true
                isLock = false
            }
        }
    }

    private var nowFloorNum = 1
    lateinit var nowFloor: FloorData
    private val sEvent = SEvent(DungeonTower.plugin)
    private val nextFloorPlayers = ArrayList<UUID>()
    private lateinit var nowThread: Thread
    private var scoreboard = Bukkit.getScoreboardManager().newScoreboard

    private var end = false

    private fun runTask(unit: ()->Unit){
        val lock = Lock()

        Bukkit.getScheduler().runTask(DungeonTower.plugin, Runnable {
            unit.invoke()
            lock.unlock()
        })

        lock.lock()
    }

    private fun clearDungeonItems(inv: Inventory){
        inv.contents.forEach {
            if (it != null && it.itemMeta?.persistentDataContainer?.has(
                NamespacedKey(DungeonTower.plugin,"dlootitem"),
                PersistentDataType.STRING) == true){
                it.amount = 0
            }
        }
    }

    private fun dungeonItemToItem(inv: Inventory){
        inv.contents.forEach {
            if (it != null && it.itemMeta?.persistentDataContainer?.has(
                    NamespacedKey(DungeonTower.plugin, "dlootitem"),
                    PersistentDataType.STRING) == true){
                it.editMeta { meta -> meta.persistentDataContainer.remove(NamespacedKey(DungeonTower.plugin,"dlootitem")) }
            }
        }
    }

    private fun callCommand(floor: FloorData){
        Bukkit.getScheduler().runTask(DungeonTower.plugin, Runnable {
            party.players.keys.forEach {
                val p = Bukkit.getPlayer(it)?:return@forEach
                floor.joinCommands.forEach { cmd ->
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                        cmd.replace("<name>",p.name)
                            .replace("<uuid>",p.uniqueId.toString()))
                }
            }
        })
    }

    private fun end(){
        nowFloor.removeFloor()
        end = true
        sEvent.unregisterAll()
        scoreboard.getObjective("DungeonTower")?.unregister()
        nowThread.interrupt()
    }

    override fun run() {
        if (party.players.size == 0)return
        DungeonTowerPartyLogSQL.insert(party)
        DungeonTowerLogSQL.enterDungeon(party, tower.internalName)
        nowThread = this
        party.nowTask = this
        party.broadCast(SStr("&c${tower.name}&aにテレポート中..."))
        nowFloor = tower.randomFloor(nowFloorNum)

        while (DungeonTower.createFloorNow){
            sleep(1)
        }
        runTask { nowFloor.callFloor() }
        runTask { party.smokeStan(60) }
        runTask { party.teleport(nowFloor.preventFloorStairs.first().add(0.0,1.0,0.0)) }
        callCommand(nowFloor)
        //eventでthreadをlockするの使わないで
        sEvent.register(PlayerDeathEvent::class.java){ e ->
            if (!party.players.containsKey(e.player.uniqueId))return@register
            val data = party.players[e.player.uniqueId]!!
            if (!data.isAlive)return@register
            nextFloorPlayers.remove(e.player.uniqueId)
            clearDungeonItems(e.player.inventory)
            data.isAlive = false
            party.broadCast(SStr("&c&l${e.player.name}が死亡した..."))
            e.player.spigot().respawn()
            for (p in party.players) {
                if (p.value.isAlive){
                    e.player.gameMode = GameMode.SPECTATOR
                    e.player.spectatorTarget = p.key.toPlayer()?:continue
                    break
                }
            }

            if (party.alivePlayers.isEmpty()){
                party.broadCast(SStr("&c&l全滅してしまった..."))
                DungeonTowerLogSQL.annihilationDungeon(party, tower.internalName)
                Bukkit.getScheduler().runTaskLater(DungeonTower.plugin, Runnable {
                    party.teleport(DungeonTower.lobbyLocation)
                    party.players.keys.forEach { uuid ->
                        if (uuid.toPlayer()?.gameMode == GameMode.SPECTATOR){
                            uuid.toPlayer()?.gameMode = GameMode.SURVIVAL
                        }
                        DungeonTower.partiesData.remove(uuid)
                        DungeonTower.playNow.remove(uuid)
                    }
                    end()
                },60)
            }
        }
        sEvent.register(PlayerStopSpectatingEntityEvent::class.java){ e ->
            if (!party.players.containsKey(e.player.uniqueId))return@register
            e.isCancelled = true
        }
        sEvent.register(PlayerQuitEvent::class.java){ e ->
            if (!party.players.containsKey(e.player.uniqueId))return@register
            nextFloorPlayers.remove(e.player.uniqueId)
            clearDungeonItems(e.player.inventory)
            if (party.parent == e.player.uniqueId){
                val randomParent = party.players.entries.filter { it.key != e.player.uniqueId }.randomOrNull()?.key
                if (randomParent != null){
                    party.parent = randomParent
                }
            }
            if (e.player.gameMode == GameMode.SPECTATOR){
                e.player.gameMode = GameMode.SURVIVAL
            }
            party.players.remove(e.player.uniqueId)
            DungeonTower.partiesData.remove(e.player.uniqueId)

            DungeonTower.playNow.remove(e.player.uniqueId)

            e.player.teleport(DungeonTower.lobbyLocation)

            if (party.alivePlayers.isEmpty()){
                DungeonTowerLogSQL.quitDisbandDungeon(party, tower.internalName)
                end()
                return@register
            }
        }
        sEvent.register(PlayerMoveEvent::class.java){ e ->
            if (e.player.gameMode == GameMode.SPECTATOR)return@register
            if (!party.players.containsKey(e.player.uniqueId))return@register
            nextFloorPlayers.remove(e.player.uniqueId)
            when(e.to.clone().subtract(0.0,1.0,0.0).block.type){
                Material.WARPED_STAIRS->{
                    if (nowFloor.clearTask.any { !it.clear })return@register
                    nextFloorPlayers.add(e.player.uniqueId)
                    if (party.alivePlayers.size / 2 < nextFloorPlayers.size){
                        party.smokeStan(60)
                        nowFloorNum++
                        val preventFloor = nowFloor
                        nowFloor = tower.randomFloor(nowFloorNum)
                        while (DungeonTower.createFloorNow){
                            sleep(1)
                        }
                        nowFloor.callFloor()
                        party.teleport(nowFloor.preventFloorStairs.first().add(0.0,1.0,0.0))
                        callCommand(nowFloor)
                        preventFloor.removeFloor()
                    }
                }
                Material.DIAMOND_BLOCK->{
                    if (nowFloor.clearTask.any { !it.clear })return@register
                    if (!nowFloor.lastFloor)return@register//どこでもクリアできるっていうのも面白いかもしれない
                    nextFloorPlayers.add(e.player.uniqueId)
                    if (party.alivePlayers.size / 2 < nextFloorPlayers.size){
                        DungeonTowerLogSQL.clearDungeon(party, tower.internalName)
                        party.broadCast(SStr("&a&lクリア！"))
                        party.alivePlayers.keys.forEach {
                            dungeonItemToItem(it.toPlayer()?.inventory?:return@forEach)
                        }
                        party.teleport(DungeonTower.lobbyLocation)
                        party.players.keys.forEach { uuid ->
                            if (uuid.toPlayer()?.gameMode == GameMode.SPECTATOR){
                                uuid.toPlayer()?.gameMode = GameMode.SURVIVAL
                            }
                            DungeonTower.partiesData.remove(uuid)
                            DungeonTower.playNow.remove(uuid)
                        }
                        end()
                    }
                }
                else->{}
            }
        }


        sleep(3000)

        while (!end){
            if (nowFloor.clearTask.none { !it.clear }){
                party.actionBar(SStr("&a&l道が開いた！§d§l(${nextFloorPlayers.size}/${party.alivePlayers.size/2})"))
            } else {
                party.actionBar(SStr("&c&l何かをしないといけない..."))
            }

            scoreboard = Bukkit.getScoreboardManager().newScoreboard

            val obj = scoreboard.registerNewObjective("DungeonTower","Dummy", Component.text(tower.internalName))
            obj.displaySlot = DisplaySlot.SIDEBAR

            obj.getScore("§6タスク").score = 0
            var scoreInt = 1
            nowFloor.clearTask.forEach {
                val replace = it.scoreBoardName
                    .replace("<spawnerNavigateNeed>", nowFloor.clearTask
                        .filter { fil -> fil.type == FloorData.ClearTaskEnum.KILL_SPAWNER_MOBS }
                        .sumOf { map -> map.need }.toString())
                    .replace("<spawnerNavigateCount>",nowFloor.clearTask
                        .filter { fil -> fil.type == FloorData.ClearTaskEnum.KILL_SPAWNER_MOBS }
                        .sumOf { map -> map.count }.toString())
                    .replace("<gimmickNeed>",nowFloor.clearTask
                        .find { find -> find.type == FloorData.ClearTaskEnum.ENTER_COMMAND }
                        ?.need.toString())
                    .replace("<gimmickCount>",nowFloor.clearTask
                        .find { find -> find.type == FloorData.ClearTaskEnum.ENTER_COMMAND }
                        ?.count.toString())

                obj.getScore(replace).score = scoreInt
                scoreInt++
            }

            party.scoreboard(scoreboard)

            try {
                sleep(1000)
            } catch (_: InterruptedException){

            }
        }



    }


}