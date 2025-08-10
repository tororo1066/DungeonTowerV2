package tororo1066.dungeontower.task

import com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent
import com.sk89q.worldedit.bukkit.BukkitWorld
import com.sk89q.worldguard.protection.regions.GlobalProtectedRegion
import net.kyori.adventure.text.Component
import org.bukkit.*
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerVelocityEvent
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scoreboard.Criteria
import org.bukkit.scoreboard.DisplaySlot
import tororo1066.dungeontower.DungeonTower
import tororo1066.dungeontower.EmptyWorldGenerator
import tororo1066.dungeontower.data.FloorData
import tororo1066.dungeontower.data.PartyData
import tororo1066.dungeontower.data.TowerData
import tororo1066.dungeontower.dmonitor.workspace.FloorWorkspace
import tororo1066.dungeontower.logging.TowerLogDB
import tororo1066.tororopluginapi.SJavaPlugin
import tororo1066.tororopluginapi.SStr
import tororo1066.tororopluginapi.utils.DateType
import tororo1066.tororopluginapi.utils.toJPNDateStr
import tororo1066.tororopluginapi.utils.toPlayer
import java.util.UUID

class DungeonTowerTask(
    party: PartyData,
    tower: TowerData,
    val firstFloor: Pair<FloorData?, Int?>? = null
): AbstractDungeonTask(party, tower) {

    var nowFloorNum = firstFloor?.second?:1
    lateinit var nowFloor: FloorData
    private val nextFloorPlayers = HashMap<FloorData, ArrayList<UUID>>()
    private val goalPlayers = ArrayList<UUID>()
    private val moveLockPlayers = ArrayList<UUID>()

    private var end = false
    private var unlockedChest = false
    private var createFloorNow = false

//    private val rootParent = party.parent

    lateinit var world: World

    var scoreboardTitle = tower.name
    val scoreboardFormats = HashMap<UUID, ArrayList<String>>() //プレイヤーごとにスコアボードを持つ

    val currentPlayerFloor = HashMap<UUID, FloorData>()
    val enteredFloors = HashMap<UUID, ArrayList<FloorData>>()
    val publicEnteredFloors = ArrayList<FloorData>()
    val exitedFloors = HashMap<UUID, ArrayList<FloorData>>()
    val publicExitedFloors = ArrayList<FloorData>()


    override fun onEnd() {
        party.players.keys.forEach { uuid ->
            moveLockPlayers.remove(uuid)
        }
//        nowFloor.removeFloor(world)
        end = true
        sEvent.unregisterAll()
        DungeonTower.regionContainer.get(BukkitWorld(world))?.removeRegion("__global__")
        val (unloaded, message) = EmptyWorldGenerator.deleteWorld(world, lobbyLocation)
        if (!unloaded) {
            SJavaPlugin.plugin.getLogger().warning("Failed to unload world ${world.name}: $message")
            DungeonWorldUnloadTask.shouldUnloadWorlds.add(world.name)
        }
        interrupt()
    }

    @Synchronized
    fun complete(message: SStr) {
        if (end) return
        end = true
        TowerLogDB.clearDungeon(party, tower.internalName)
        party.broadCast(message)
        party.players.keys.forEach {
            if (!party.alivePlayers.containsKey(it)){
                clearDungeonItems(it.toPlayer()?:return@forEach)
                return@forEach
            }
            dungeonItemToItem(it.toPlayer()?:return@forEach)
        }
        end(delay = 0)
    }

    fun fail(message: SStr, delay: Long = 0) {
        TowerLogDB.annihilationDungeon(party, tower.internalName)
        party.broadCast(message)
        end(delay)
    }

    private fun runTrigger(name: String, player: Player, floor: FloorData) {
        DungeonTower.actionStorage.trigger(
            FloorWorkspace, name, DungeonTower.actionStorage.createActionContext(
                DungeonTower.actionStorage.createPublicContext().apply {
                    workspace = FloorWorkspace
                    parameters.let {
                        it["party.uuid"] = party.partyUUID.toString()
                        it.putAll(floor.generateParameters())
                        it["floor.num"] = nowFloorNum
                    }
                }
            ).apply {
                target = player
            }
        ) { section ->
            section.getString("floor") == floor.internalName
        }
    }

    override fun run() {
        if (party.players.isEmpty()) return
        TowerLogDB.insertPartyData(party)
        TowerLogDB.enterDungeon(party, tower.internalName)
        party.currentTask = this

        party.broadCast(SStr("&c${tower.name}&aにテレポート中..."))
        nowFloor = firstFloor?.first?:tower.randomFloor()

        runTask {
            world = DungeonTower.worldGenerator.createEmptyWorld("dungeon")
            tower.worldGameRules.forEach { (rule, value) ->
                @Suppress("DEPRECATION") //他にやり方が思いつかないので一旦これで
                world.setGameRuleValue(rule.name, value.toString())
            }
            world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true) //仕様上必要

            val globalRegion = GlobalProtectedRegion("__global__")
            DungeonTower.sWorldGuard.setFlags(globalRegion, tower.regionFlags)
            val regions = DungeonTower.regionContainer.get(BukkitWorld(world))
            regions?.addRegion(globalRegion)
        }

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
                end(delay = 1)
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

            moveLockPlayers.remove(e.player.uniqueId)

            e.player.teleport(DungeonTower.lobbyLocation)

            if (party.alivePlayers.isEmpty()){
                party.broadCast(SStr("&c生きているプレイヤーが退出したため敗北扱いになりました"))
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

        sEvent.register(EntityDamageEvent::class.java){ e ->
            if (moveLockPlayers.contains(e.entity.uniqueId)){
                e.isCancelled = true
            }
        }

        sEvent.register(EntityDamageByEntityEvent::class.java){ e ->
            if (moveLockPlayers.contains(e.entity.uniqueId)){
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
                Material.WARPED_STAIRS -> {
                    val floor = getInFloor(nowFloor, e.player)?:return@register
                    if (!floor.finished.get()) {
                        if (floor.cancelStandOnStairs) {
                            e.isCancelled = true
                        }
                        return@register
                    }
                    if (nextFloorPlayers.containsKey(floor)){
                        nextFloorPlayers[floor]!!.add(e.player.uniqueId)
                    } else {
                        nextFloorPlayers[floor] = arrayListOf(e.player.uniqueId)
                    }

                    if (party.alivePlayers.size / 2 <= nextFloorPlayers[floor]!!.size && !createFloorNow
                        && floor.subFloors.isNotEmpty()){
                        createFloorNow = true
                        party.alivePlayers.keys.forEach {
                            moveLockPlayers.add(it)
                            val player = it.toPlayer()?:return@forEach
                            player.gameMode = GameMode.SPECTATOR
                            player.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, Int.MAX_VALUE, 0, false, false))
                        }
                        party.broadCast(SStr("&7転移中..."))

                        nowFloorNum++

                        nowFloor.removeFloor(world)

                        nowFloor = floor.randomSubFloor()
                        nowFloor.generateFloor(tower, world, nowFloorNum, party).thenAccept {
                            if (isInterrupted) return@thenAccept
                            DungeonTower.util.runTask {
                                nowFloor.activate()
                                party.smokeStan(60)
                                unlockedChest = false
                                party.teleport(nowFloor.previousFloorStairs.random().add(0.0,1.1,0.0))
                                callCommand(nowFloor)
                                party.alivePlayers.keys.forEach {
                                    moveLockPlayers.remove(it)
                                    val player = it.toPlayer()?:return@forEach
                                    player.gameMode = GameMode.SURVIVAL
                                    player.removePotionEffect(PotionEffectType.BLINDNESS)
                                    stepItems(player)
                                }
                                createFloorNow = false
                            }
                        }
                    }
                }
                Material.DIAMOND_BLOCK -> {
                    val floor = getInFloor(nowFloor, e.player)?:return@register
                    if (!floor.finished.get())return@register
                    goalPlayers.add(e.player.uniqueId)
                    if (party.alivePlayers.size / 2 < goalPlayers.size){
                        complete(SStr("&a&lクリア！"))
                    }
                }
                else->{}
            }
        }

        nowFloor.generateFloor(tower, world, nowFloorNum, party).join()
        scoreboardTitle = tower.name
        runTask {
            nowFloor.activate()

            party.smokeStan(60)
            party.teleport(nowFloor.previousFloorStairs.random().add(0.0,1.0,0.0))
        }
        callCommand(nowFloor)


        sleep(3000)

        while (!end){

            DungeonTower.util.runTask {
                if (!createFloorNow) {
                    nowFloor.time--
                }

                for (uuid in party.players.keys) {
                    val player = uuid.toPlayer()?:return@runTask

                    val currentFloor = getInFloor(nowFloor, player)
                    if (currentFloor != null){
                        if (currentPlayerFloor[uuid] != currentFloor) {
                            val previousFloor = currentPlayerFloor[uuid]

                            if (!publicEnteredFloors.contains(currentFloor)) {
                                publicEnteredFloors.add(currentFloor)
                                runTrigger("dungeon_first_enter_public", player, currentFloor)
                            }

                            if (!enteredFloors.computeIfAbsent(uuid) { arrayListOf() }.contains(currentFloor)) {
                                enteredFloors[uuid]!!.add(currentFloor)
                                runTrigger("dungeon_first_enter_private", player, currentFloor)
                            }
                            runTrigger("dungeon_enter", player, currentFloor)

                            //exit
                            if (previousFloor != null) {
                                if (!publicExitedFloors.contains(previousFloor)) {
                                    publicExitedFloors.add(previousFloor)
                                    runTrigger("dungeon_first_exit_public", player, previousFloor)
                                }

                                if (!exitedFloors.computeIfAbsent(uuid) { arrayListOf() }.contains(previousFloor)) {
                                    exitedFloors[uuid]!!.add(previousFloor)
                                    runTrigger("dungeon_first_exit_private", player, previousFloor)
                                }

                                runTrigger("dungeon_exit", player, previousFloor)
                            }
                        }
                        currentPlayerFloor[uuid] = currentFloor
                    } else {
                        currentPlayerFloor.remove(uuid)
                    }



                    val scoreboard = Bukkit.getScoreboardManager().newScoreboard
                    val obj = scoreboard.registerNewObjective("DungeonTower", Criteria.DUMMY, Component.text(scoreboardTitle))
                    obj.displaySlot = DisplaySlot.SIDEBAR

                    var scoreInt = -1

                    scoreboardFormats[uuid]?.forEach {
                        obj.getScore(it).score = scoreInt
                        scoreInt--
                    }

                    obj.getScore(" ").score = scoreInt
                    scoreInt--

                    obj.getScore("  §7残り時間" +
                            " §a${
                                if (nowFloor.time <= 0) "0秒" else nowFloor.time.toLong()
                                    .toJPNDateStr(DateType.SECOND,DateType.MINUTE,false)
                            }").score = scoreInt
                    scoreInt--

                    obj.getScore("  ").score = scoreInt
                    scoreInt--

                    party.alivePlayers.values.filter {
                        it.uuid.toPlayer() != null && it.uuid != uuid
                    }.sortedBy { it.uuid.toPlayer()?.health }
                        .take(4).forEach {
                            val health = "§c♥§a${it.uuid.toPlayer()!!.health.toInt()}"
                            val builder = StringBuilder()
                            repeat(16 - it.mcid.length) {
                                builder.append(" ")
                            }
                            obj.getScore("§7$builder${it.mcid} $health").score = scoreInt
                            scoreInt--
                        }

                    player.scoreboard = scoreboard
                }
            }

            if (nowFloor.time == 0){
//                runTask {
//                    party.players.keys.forEach { uuid ->
//                        moveLockPlayers.add(uuid)
//                        uuid.toPlayer()?.gameMode = GameMode.SPECTATOR
//                    }
//                }
                party.broadCast(SStr("&c&l時間切れ..."))
                TowerLogDB.timeOutDungeon(party, tower.internalName)
                end(1)
            }

            try {
                sleep(1000)
            } catch (_: InterruptedException){

            }
        }

    }

}