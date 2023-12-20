package tororo1066.dungeontower.task

import com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent
import com.elmakers.mine.bukkit.api.event.PreCastEvent
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.event.entity.EntityResurrectEvent
import org.bukkit.event.entity.EntityToggleGlideEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerItemConsumeEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerVelocityEvent
import org.bukkit.scoreboard.Criteria
import org.bukkit.scoreboard.DisplaySlot
import tororo1066.dungeontower.DungeonTower
import tororo1066.dungeontower.data.FloorData
import tororo1066.dungeontower.data.PartyData
import tororo1066.dungeontower.data.TowerData
import tororo1066.dungeontower.sql.TowerLogDB
import tororo1066.tororopluginapi.SStr
import tororo1066.tororopluginapi.utils.DateType
import tororo1066.tororopluginapi.utils.toJPNDateStr
import tororo1066.tororopluginapi.utils.toPlayer
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class DungeonTowerTask(party: PartyData, tower: TowerData): AbstractDungeonTask(party, tower) {

    var nowFloorNum = 1
    lateinit var nowFloor: FloorData
    lateinit var nextFloor: FloorData
    private val nextFloorPlayers = HashMap<FloorData, ArrayList<UUID>>()
    private val goalPlayers = ArrayList<UUID>()
    private val moveLockPlayers = ArrayList<UUID>()
    private var scoreboard = Bukkit.getScoreboardManager().newScoreboard

    private var end = false
    private var unlockedChest = false
    private var createFloorNow = false

    override fun onEnd(){
        nowFloor.removeFloor()
        end = true
        sEvent.unregisterAll()
        scoreboard.getObjective("DungeonTower")?.displaySlot = null
        scoreboard.getObjective("DungeonTower")?.unregister()
        interrupt()
    }

    private fun generateNextFloor(){
        if (tower.isExistFloor(nowFloorNum + 1)){
            nextFloor = tower.randomFloor(nowFloorNum + 1)
            nextFloor.generateFloor()
        }
    }

    override fun run() {
        if (party.players.size == 0)return
        TowerLogDB.insertPartyData(party)
        TowerLogDB.enterDungeon(party, tower.internalName)
        party.nowTask = this
        party.broadCast(SStr("&c${tower.name}&aにテレポート中..."))
        nowFloor = tower.randomFloor(nowFloorNum)

        while (DungeonTower.createFloorNow){
            sleep(1)
        }
        runTask {
            nowFloor.generateFloor()
            nowFloor.activate()
        }
        runTask { party.smokeStan(60) }
        runTask { party.teleport(nowFloor.preventFloorStairs.random().add(0.0,1.0,0.0)) }
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
            nextFloorPlayers.entries.removeIf { it.value.contains(e.player.uniqueId) }
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
                TowerLogDB.annihilationDungeon(party, tower.internalName)
                end()
            }
        }
        sEvent.register(PlayerStopSpectatingEntityEvent::class.java){ e ->
            if (!party.players.containsKey(e.player.uniqueId))return@register
            e.isCancelled = true
        }
        sEvent.register(PlayerQuitEvent::class.java){ e ->
            if (!party.players.containsKey(e.player.uniqueId))return@register
            nextFloorPlayers.entries.removeIf { it.value.contains(e.player.uniqueId) }
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
                TowerLogDB.quitDisbandDungeon(party, tower.internalName)
                end(delay = 0)
                return@register
            }
        }
        sEvent.register(PlayerVelocityEvent::class.java){ e ->
            if (moveLockPlayers.contains(e.player.uniqueId)){
                e.isCancelled = true
            }
        }
        sEvent.register(PlayerMoveEvent::class.java){ e ->
            if (moveLockPlayers.contains(e.player.uniqueId)){
                e.isCancelled = true
                return@register
            }
            if (e.player.gameMode == GameMode.SPECTATOR)return@register
            if (!party.players.containsKey(e.player.uniqueId))return@register
            nextFloorPlayers.entries.removeIf { it.value.contains(e.player.uniqueId) }
            when(e.to.clone().subtract(0.0,1.0,0.0).block.type){
                Material.WARPED_STAIRS->{
                    val floor = getInFloor(nowFloor, e.player)?:return@register
                    if (!floor.checkClear())return@register
                    if (nextFloorPlayers.containsKey(floor)){
                        nextFloorPlayers[floor]!!.add(e.player.uniqueId)
                    } else {
                        nextFloorPlayers[floor] = arrayListOf(e.player.uniqueId)
                    }
                    if (party.alivePlayers.size / 2 < nextFloorPlayers[floor]!!.size && !createFloorNow
                        && floor.subFloors.isNotEmpty()){
                        createFloorNow = true
                        party.alivePlayers.keys.forEach {
                            moveLockPlayers.add(it)
                        }
                        party.broadCast(SStr("&7転移中..."))//메시지
                        Bukkit.getScheduler().runTaskAsynchronously(DungeonTower.plugin, Runnable {
                            nowFloorNum++
                            val preventFloor = nowFloor
                            nowFloor = floor.randomFloor()
                            while (DungeonTower.createFloorNow){
                                sleep(1)
                            }
                            Bukkit.getScheduler().runTask(DungeonTower.plugin, Runnable {
                                nowFloor.generateFloor()
                                nowFloor.activate()
                                party.smokeStan(60)
                                unlockedChest = false
                                party.teleport(nowFloor.preventFloorStairs.random().add(0.0,1.1,0.0))
                                callCommand(nowFloor)
                                preventFloor.removeFloor()
                                party.alivePlayers.keys.forEach {
                                    moveLockPlayers.remove(it)
                                }
                                createFloorNow = false
                            })
                        })
                    }
                }
                Material.DIAMOND_BLOCK->{
                    val floor = getInFloor(nowFloor, e.player)?:return@register
                    if (!floor.checkClear())return@register
                    goalPlayers.add(e.player.uniqueId)
                    if (party.alivePlayers.size / 2 < goalPlayers.size){
                        TowerLogDB.clearDungeon(party, tower.internalName)
                        party.broadCast(SStr("&a&lクリア！"))
                        party.players.keys.forEach {
                            if (!party.alivePlayers.containsKey(it)){
                                clearDungeonItems(it.toPlayer()?:return@forEach)
                                return@forEach
                            }
                            dungeonItemToItem(it.toPlayer()?:return@forEach)
                        }
                        end(delay = 0)
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
            if (!party.players.containsKey(e.mage?.entity?.uniqueId))return@register
            if (e.spell?.category?.name == "wing"){
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

                    obj.getScore(formatTask(nowFloor, it)).score = scoreInt
                    scoreInt--
                }

                nowFloor.parallelFloors.forEach { floor ->
                    floor.clearTask.forEach second@ { task ->
                        if (task.scoreboardName.isBlank()) return@second
                        obj.getScore(formatTask(floor, task)).score = scoreInt
                    }
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
                    .sortedBy { it.uuid.toPlayer()?.health }
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
                TowerLogDB.timeOutDungeon(party, tower.internalName)
                end()
            }

            try {
                sleep(1000)
            } catch (_: InterruptedException){

            }
        }

    }

}