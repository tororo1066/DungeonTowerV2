package tororo1066.dungeontower

import com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent
import com.elmakers.mine.bukkit.api.event.PreCastEvent
import com.google.common.io.ByteStreams
import net.kyori.adventure.text.Component
import org.bukkit.*
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityResurrectEvent
import org.bukkit.event.entity.EntityToggleGlideEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerItemConsumeEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.Inventory
import org.bukkit.persistence.PersistentDataType
import org.bukkit.scoreboard.Criteria
import org.bukkit.scoreboard.DisplaySlot
import tororo1066.dungeontower.data.FloorData
import tororo1066.dungeontower.data.PartyData
import tororo1066.dungeontower.data.TowerData
import tororo1066.dungeontower.sql.DungeonTowerLogSQL
import tororo1066.dungeontower.sql.DungeonTowerPartyLogSQL
import tororo1066.tororopluginapi.SStr
import tororo1066.tororopluginapi.sEvent.SEvent
import tororo1066.tororopluginapi.utils.DateType
import tororo1066.tororopluginapi.utils.toJPNDateStr
import tororo1066.tororopluginapi.utils.toPlayer
import java.util.*
import java.util.function.Consumer
import kotlin.collections.ArrayList

class DungeonTowerTask(val party: PartyData, val tower: TowerData): Thread() {

    class Lock {

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
    private val moveLockPlayers = ArrayList<UUID>()
    private lateinit var nowThread: Thread
    private var scoreboard = Bukkit.getScoreboardManager().newScoreboard

    private var end = false
    private var unlockedChest = false

    private fun runTask(unit: ()->Unit){
        val lock = Lock()

        Bukkit.getScheduler().runTask(DungeonTower.plugin, Runnable {
            unit.invoke()
            lock.unlock()
        })

        lock.lock()
    }

    private fun clearDungeonItems(p: Player){
        val cursorItem = p.itemOnCursor
        if (!cursorItem.type.isAir && cursorItem.itemMeta.persistentDataContainer.has(
            NamespacedKey(DungeonTower.plugin,"dlootitem")
        )){
            cursorItem.amount = 0
        }
        p.inventory.contents?.forEach {
            if (it != null && it.itemMeta?.persistentDataContainer?.has(
                NamespacedKey(DungeonTower.plugin,"dlootitem"),
                PersistentDataType.STRING) == true){
                it.amount = 0
            }
        }
    }

    private fun dungeonItemToItem(p: Player){
        val inv = p.inventory
        inv.contents?.forEach {
            if (it != null && it.itemMeta?.persistentDataContainer?.has(
                    NamespacedKey(DungeonTower.plugin, "dlootitem"),
                    PersistentDataType.STRING) == true){
                it.editMeta { meta -> meta.persistentDataContainer.remove(NamespacedKey(DungeonTower.plugin,"dlootitem")) }
                if (it.itemMeta.persistentDataContainer.has(
                    NamespacedKey(DungeonTower.plugin, "dannouncementitem"),
                    PersistentDataType.INTEGER
                )){
                    it.editMeta { meta -> meta.persistentDataContainer.remove(NamespacedKey(DungeonTower.plugin, "dannouncementitem")) }
                    if (DungeonTower.plugin.server.messenger.isReservedChannel("tororo:dungeontower")){
                        val out = ByteStreams.newDataOutput()
                        out.writeUTF("message")
                        out.writeUTF("${DungeonTower.prefix}§e§l${p.name}§dが§r${it.itemMeta.displayName}§dを手に入れた！")
                        DungeonTower.plugin.server.sendPluginMessage(DungeonTower.plugin, "tororo:dungeontower", out.toByteArray())
                    } else {
                        SStr("${DungeonTower.prefix}§e§l${p.name}§dが§r${it.itemMeta.displayName}§dを手に入れた！").broadcast()
                    }
                }
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
        scoreboard.getObjective("DungeonTower")?.displaySlot = null
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
            if (e.isCancelled)return@register
            if (moveLockPlayers.contains(e.player.uniqueId)){
                e.isCancelled = true
                return@register
            }
            if (!party.players.containsKey(e.player.uniqueId))return@register
            val data = party.players[e.player.uniqueId]!!
            if (!data.isAlive)return@register
            nextFloorPlayers.remove(e.player.uniqueId)
            clearDungeonItems(e.player)
            data.isAlive = false
            party.broadCast(SStr("&c&l${e.player.name}が死亡した..."))
            e.player.spigot().respawn()
            for (p in party.players) {
                val player = p.key.toPlayer()?:return@register
                if (player.gameMode == GameMode.SPECTATOR && player.spectatorTarget == e.player){
                    for (otherP in party.players){
                        if (otherP.value.isAlive){
                            player.spectatorTarget = otherP.key.toPlayer()?:continue
                            break
                        }
                    }
                }
                if (p.value.isAlive){
                    e.player.gameMode = GameMode.SPECTATOR
                    e.player.spectatorTarget = p.key.toPlayer()?:continue
                    break
                }
            }

            if (party.alivePlayers.isEmpty()){
                party.broadCast(SStr("&c&l全滅してしまった..."))
                party.players.keys.forEach { uuid ->
                    moveLockPlayers.add(uuid)
                }
                DungeonTowerLogSQL.annihilationDungeon(party, tower.internalName)
                Bukkit.getScheduler().runTaskLater(DungeonTower.plugin, Runnable {
                    party.teleport(DungeonTower.lobbyLocation)
                    party.players.keys.forEach { uuid ->
                        if (uuid.toPlayer()?.gameMode == GameMode.SPECTATOR){
                            uuid.toPlayer()?.spectatorTarget = null
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
            clearDungeonItems(e.player)
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
                party.broadCast(SStr("&c生きているプレイヤーが退出したため負けになりました"))
                party.teleport(DungeonTower.lobbyLocation)
                party.players.keys.forEach { uuid ->
                    if (uuid.toPlayer()?.gameMode == GameMode.SPECTATOR){
                        uuid.toPlayer()?.spectatorTarget = null
                        uuid.toPlayer()?.gameMode = GameMode.SURVIVAL
                    }
                    DungeonTower.partiesData.remove(uuid)
                    DungeonTower.playNow.remove(uuid)
                }
                DungeonTowerLogSQL.quitDisbandDungeon(party, tower.internalName)
                end()
                return@register
            }
        }
        sEvent.register(PlayerMoveEvent::class.java){ e ->
            if (moveLockPlayers.contains(e.player.uniqueId)){
                e.isCancelled = true
                return@register
            }
            if (e.player.gameMode == GameMode.SPECTATOR)return@register
            if (!party.players.containsKey(e.player.uniqueId))return@register
            nextFloorPlayers.remove(e.player.uniqueId)
            when(e.to.clone().subtract(0.0,1.0,0.0).block.type){
                Material.WARPED_STAIRS->{
                    if (nowFloor.clearTask.any { !it.clear })return@register
                    nextFloorPlayers.add(e.player.uniqueId)
                    if (party.alivePlayers.size / 2 < nextFloorPlayers.size){
                        nowFloorNum++
                        val preventFloor = nowFloor
                        nowFloor = tower.randomFloor(nowFloorNum)
                        while (DungeonTower.createFloorNow){
                            sleep(1)
                        }
                        party.smokeStan(60)
                        nowFloor.callFloor()
                        unlockedChest = false
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
                            dungeonItemToItem(it.toPlayer()?:return@forEach)
                        }
                        party.teleport(DungeonTower.lobbyLocation)
                        party.players.keys.forEach { uuid ->
                            if (uuid.toPlayer()?.gameMode == GameMode.SPECTATOR){
                                uuid.toPlayer()?.spectatorTarget = null
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

        sEvent.register(PlayerItemConsumeEvent::class.java) { e ->
            if (!party.players.containsKey(e.player.uniqueId))return@register
            if (e.item.type == Material.GOLDEN_APPLE || e.item.type == Material.ENCHANTED_GOLDEN_APPLE){
                e.isCancelled = true
            }
        }

        sEvent.register(EntityResurrectEvent::class.java) { e ->
            if (!party.players.containsKey(e.entity.uniqueId))return@register
            if (e.hand == null || e.isCancelled)return@register
            e.isCancelled = true
        }

        sEvent.register(PreCastEvent::class.java) { e ->
            if (!party.players.containsKey(e.mage.entity.uniqueId))return@register
            if (e.spell.category.name == "wing"){
                e.mage.sendMessage("§cwingは使えません")
                e.isCancelled = true
            }
        }

        sEvent.register(EntityToggleGlideEvent::class.java) { e ->
            if (!party.players.containsKey(e.entity.uniqueId))return@register
            if (e.isGliding && !e.isCancelled){
                e.entity.sendMessage("§cエリトラは使えません")
                e.isCancelled = true
            }
        }


        sleep(3000)

        while (!end){
            DungeonTower.util.runTask {
                nowFloor.time--
                scoreboard = Bukkit.getScoreboardManager().newScoreboard

                val obj = scoreboard.registerNewObjective("DungeonTower", Criteria.DUMMY, Component.text(tower.name))
                obj.displaySlot = DisplaySlot.SIDEBAR

                obj.getScore("§6タスク").score = 0
                var scoreInt = -1
                nowFloor.clearTask.forEach {
                    val replace = " " + (if (it.clear) it.clearScoreboardName else it.scoreboardName)
                        .replace("&", "§")
                        .replace("<and>", "&")
                        .replace("<spawnerNavigateNeed>", nowFloor.clearTask
                            .filter { fil -> fil.type == FloorData.ClearTaskEnum.KILL_SPAWNER_MOBS }
                            .sumOf { map -> map.need }.toString()
                        )
                        .replace("<spawnerNavigateCount>", nowFloor.clearTask
                            .filter { fil -> fil.type == FloorData.ClearTaskEnum.KILL_SPAWNER_MOBS }
                            .sumOf { map -> map.count }.toString()
                        )
                        .replace("<gimmickNeed>", nowFloor.clearTask
                            .find { find -> find.type == FloorData.ClearTaskEnum.ENTER_COMMAND }
                            ?.need.toString())
                        .replace("<gimmickCount>", nowFloor.clearTask
                            .find { find -> find.type == FloorData.ClearTaskEnum.ENTER_COMMAND }
                            ?.count.toString())

                    obj.getScore(replace).score = scoreInt
                    scoreInt--
                }

                obj.getScore(" ").score = scoreInt
                scoreInt--

                obj.getScore("  §7残り時間" +
                        " §a${
                            if (nowFloor.time < 0) "0秒" else nowFloor.time.toLong()
                                .toJPNDateStr(DateType.SECOND,DateType.MINUTE,false)
                        }").score = scoreInt
                scoreInt--

                obj.getScore("  ").score = scoreInt
                scoreInt--

                party.alivePlayers.values.filter { it.uuid.toPlayer() != null }
                    .take(4).forEach {
                        val health = "§c♥§a${it.uuid.toPlayer()!!.health.toInt()}"
                        val builder = StringBuilder()
                        for (i in 1..16 - it.mcid.length) {
                            builder.append(" ")
                        }
                        obj.getScore("§7$builder${it.mcid} $health").score = scoreInt
                        scoreInt--
                    }

                party.scoreboard(scoreboard)
            }

            if (nowFloor.clearTask.none { !it.clear }){
                if (!unlockedChest){
                    unlockedChest = true
                    Bukkit.getScheduler().runTask(DungeonTower.plugin, Runnable {
                        nowFloor.unlockChest()
                    })
                }
            }

            if (nowFloor.time == 0){
                runTask {
                    party.players.keys.forEach { uuid ->
                        moveLockPlayers.add(uuid)
                        uuid.toPlayer()?.gameMode = GameMode.SPECTATOR
                    }
                }
                party.broadCast(SStr("&c&l時間切れ..."))
                DungeonTowerLogSQL.timeOutDungeon(party, tower.internalName)
                Bukkit.getScheduler().runTaskLater(DungeonTower.plugin, Runnable {
                    party.teleport(DungeonTower.lobbyLocation)
                    party.players.keys.forEach { uuid ->
                        if (uuid.toPlayer()?.gameMode == GameMode.SPECTATOR){
                            uuid.toPlayer()?.spectatorTarget = null
                            uuid.toPlayer()?.gameMode = GameMode.SURVIVAL
                        }
                        DungeonTower.partiesData.remove(uuid)
                        DungeonTower.playNow.remove(uuid)
                    }
                    end()
                },60)
            }

            try {
                sleep(1000)
            } catch (_: InterruptedException){

            }
        }

    }

}