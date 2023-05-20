package tororo1066.dungeontower

import com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent
import org.bukkit.*
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.Inventory
import org.bukkit.persistence.PersistentDataType
import tororo1066.dungeontower.data.FloorData
import tororo1066.dungeontower.data.PartyData
import tororo1066.dungeontower.data.TowerData
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

    var nowFloorNum = 1
    lateinit var nowFloor: FloorData
    private val sEvent = SEvent(DungeonTower.plugin)
    private val nextFloorPlayers = ArrayList<UUID>()
    lateinit var nowThread: Thread

    var end = false

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

    override fun run() {
        if (party.players.size == 0)return
        nowThread = this
        party.nowTask = this
        party.broadCast(SStr("&c${tower.name}&aにテレポート中..."))
        runTask { party.smokeStan(60) }
        nowFloor = tower.randomFloor(nowFloorNum)

        while (DungeonTower.createFloorNow){
            sleep(1)
        }
        runTask { nowFloor.callFloor() }
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
            for (p in party.players) {
                if (p.value.isAlive){
                    e.player.gameMode = GameMode.SPECTATOR
                    e.player.spectatorTarget = p.key.toPlayer()?:continue
                    break
                }
            }

            if (party.alivePlayers.isEmpty()){
                party.broadCast(SStr("&c&l全滅してしまった..."))
                Bukkit.getScheduler().runTaskLater(DungeonTower.plugin, Runnable {
                    party.teleport(DungeonTower.lobbyLocation)
                    party.players.keys.forEach { uuid ->
                        if (uuid.toPlayer()?.gameMode == GameMode.SPECTATOR){
                            uuid.toPlayer()?.gameMode = GameMode.SURVIVAL
                        }
                        DungeonTower.partiesData.remove(uuid)
                        DungeonTower.playNow.remove(uuid)
                    }
                    nowFloor.removeFloor()
                    end = true
                    sEvent.unregisterAll()
                    nowThread.interrupt()
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

            if (party.players.isEmpty()){
                nowFloor.removeFloor()
                end = true
                sEvent.unregisterAll()
                nowThread.interrupt()
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
                        party.broadCast(SStr("&a&lクリア！"))
                        party.alivePlayers.keys.forEach {
                            dungeonItemToItem(it.toPlayer()?.inventory?:return@forEach)
                        }
                        end = true
                        sEvent.unregisterAll()
                        party.teleport(DungeonTower.lobbyLocation)
                        party.players.keys.forEach { uuid ->
                            if (uuid.toPlayer()?.gameMode == GameMode.SPECTATOR){
                                uuid.toPlayer()?.gameMode = GameMode.SURVIVAL
                            }
                            DungeonTower.partiesData.remove(uuid)
                            DungeonTower.playNow.remove(uuid)
                        }
                        nowFloor.removeFloor()
                        nowThread.interrupt()
                    }
                }
                else->{}
            }
        }


        sleep(3000)

        while (!end){
            if (nowFloor.clearTask.none { !it.clear }){
                party.actionBar(SStr("&a&l道が開いた！"))
            } else {
                party.actionBar(SStr("&c&l何かをしないといけない..."))
            }

            sleep(200)
        }



    }


}