package tororo1066.dungeontower.task

import com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent
import net.kyori.adventure.text.Component
import org.bukkit.*
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerVelocityEvent
import org.bukkit.scoreboard.Criteria
import org.bukkit.scoreboard.DisplaySlot
import tororo1066.dungeontower.DungeonTower
import tororo1066.dungeontower.command.DungeonCommand
import tororo1066.dungeontower.data.FloorData
import tororo1066.dungeontower.data.PartyData
import tororo1066.dungeontower.data.TowerData
import tororo1066.dungeontower.logging.TowerLogDB
import tororo1066.dungeontower.skilltree.ActionType
import tororo1066.tororopluginapi.SDebug.Companion.sendDebug
import tororo1066.tororopluginapi.SStr
import tororo1066.tororopluginapi.utils.DateType
import tororo1066.tororopluginapi.utils.toJPNDateStr
import tororo1066.tororopluginapi.utils.toPlayer
import tororo1066.tororopluginapi.world.EmptyWorldGenerator
import java.util.UUID

class DungeonTowerTask(party: PartyData, tower: TowerData, val firstFloor: Pair<FloorData, Int>? = null): AbstractDungeonTask(party, tower) {

    var nowFloorNum = firstFloor?.second?:1
    lateinit var nowFloor: FloorData
    private val nextFloorPlayers = HashMap<FloorData, ArrayList<UUID>>()
    private val goalPlayers = ArrayList<UUID>()
    private val moveLockPlayers = ArrayList<UUID>()

    private var end = false
    private var unlockedChest = false
    private var createFloorNow = false

    private val rootParent = party.parent

    private var floorDisplay = tower.name

    lateinit var world: World

    val scoreboardFormats = HashMap<UUID, ArrayList<String>>() //プレイヤーごとにスコアボードを持つ

    val currentPlayerFloor = HashMap<UUID, FloorData>()
    val enteredFloors = HashMap<UUID, ArrayList<FloorData>>()
    val publicEnteredFloors = ArrayList<FloorData>()
    val exitedFloors = HashMap<UUID, ArrayList<FloorData>>()
    val publicExitedFloors = ArrayList<FloorData>()

    override fun onEnd(){
        nowFloor.removeFloor(world)
        end = true
        sEvent.unregisterAll()
        EmptyWorldGenerator.deleteWorld(world)
        interrupt()
    }

    private fun complete(message: SStr) {
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

    private fun fail(message: SStr) {
        TowerLogDB.annihilationDungeon(party, tower.internalName)
        party.broadCast(message)
        end()
    }

    override fun run() {
        if (party.players.size == 0) return
        party.players.keys.forEach {
            DungeonCommand.perkOpeningPlayers[it]?.stop()
            DungeonCommand.perkOpeningPlayers.remove(it)
        }
        TowerLogDB.insertPartyData(party)
        TowerLogDB.enterDungeon(party, tower.internalName)
        party.nowTask = this

        party.broadCast(SStr("&c${tower.name}&aにテレポート中..."))

        nowFloor = firstFloor?.first?:tower.randomFloor()

        runTask {
            world = DungeonTower.worldGenerator.createEmptyWorld("dungeon")
            tower.worldGameRules.forEach { (rule, value) ->
                @Suppress("DEPRECATION") //他にやり方が思いつかないので一旦これで
                world.setGameRuleValue(rule.name, value.toString())
            }
            world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true) //仕様上必要

            DungeonTower.sWorldGuard.getRegion(
                world, "__global__"
            )?.let {
                DungeonTower.sWorldGuard.setFlags(it, tower.regionFlags)
            }
        }

        nowFloor.generateFloor(tower, world, nowFloorNum, rootParent, party).join()
        floorDisplay = nowFloor.getDisplayName(tower, nowFloorNum)

        party.loadPerk(tower.internalName).join()
//        party.players.keys.forEach {
//            clearDungeonItems(it.toPlayer()?:return@forEach)
//        }
        runTask {
            nowFloor.activate()

            party.smokeStan(60)
            party.teleport(nowFloor.previousFloorStairs.random().add(0.0,1.0,0.0))
            party.registerPerk()
            party.invokePerk(ActionType.ENTER_DUNGEON)
            party.invokePerk(ActionType.ENTER_FLOOR)
        }
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
            data.invokePerk(ActionType.DIE)
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
            val data = party.players[e.player.uniqueId]!!
            data.invokePerk(ActionType.DIE)
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
                    e.player.sendDebug("UpFloor", floor.internalName)
                    if (!floor.finished.get()) {
                        if (floor.cancelStandOnStairs) {
                            e.isCancelled = true
                        }
                        return@register
                    }
                    e.player.sendDebug("UpFloor", "Clear")
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
                        }
                        party.broadCast(SStr("&7転移中..."))

                        nowFloorNum++
                        val previousFloor = nowFloor
                        nowFloor = floor.randomSubFloor()
                        nowFloor.generateFloor(tower, world, nowFloorNum, rootParent, party).thenAccept {
                            floorDisplay = nowFloor.getDisplayName(tower, nowFloorNum)
                            DungeonTower.util.runTask {
                                previousFloor.killMobs(world)
                                nowFloor.activate()
                                party.smokeStan(60)
                                unlockedChest = false
                                party.teleport(nowFloor.previousFloorStairs.random().add(0.0,1.1,0.0))
                                callCommand(nowFloor)
                                previousFloor.removeFloor(world)
                                party.alivePlayers.keys.forEach {
                                    moveLockPlayers.remove(it)
                                    stepItems(it.toPlayer()?:return@forEach)
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
//                        TowerLogDB.clearDungeon(party, tower.internalName)
//                        party.broadCast(SStr("&a&lクリア！"))
//                        party.players.keys.forEach {
//                            if (!party.alivePlayers.containsKey(it)){
//                                clearDungeonItems(it.toPlayer()?:return@forEach)
//                                return@forEach
//                            }
//                            dungeonItemToItem(it.toPlayer()?:return@forEach)
//                        }
//                        end(delay = 0)
                        complete(SStr("&a&lクリア！"))
                    }
                }
                else->{}
            }
        }


        sleep(3000)

        while (!end){

            DungeonTower.util.runTask {
                nowFloor.time--

                for (uuid in party.players.keys) {
                    val player = uuid.toPlayer()?:return@runTask

                    val currentFloor = getInFloor(nowFloor, player)
                    if (currentFloor != null){
                        if (currentPlayerFloor[uuid] != currentFloor) {
                            val previousFloor = currentPlayerFloor[uuid]

                            if (!publicEnteredFloors.contains(currentFloor)) {
                                publicEnteredFloors.add(currentFloor)
                                DungeonTower.actionStorage.trigger("dungeon_first_enter_public", DungeonTower.actionStorage.createActionContext(
                                    DungeonTower.actionStorage.createPublicContext()
                                ).apply {
                                    this.target = player
                                    this.prepareParameters.let {
                                        it["party.uuid"] = party.partyUUID.toString()
                                        it.putAll(currentFloor.generateParameters())
                                    }
                                }) { section ->
                                    section.getString("floor") == currentFloor.internalName
                                }
                            }

                            if (!enteredFloors.computeIfAbsent(uuid) { arrayListOf() }.contains(currentFloor)) {
                                enteredFloors[uuid]!!.add(currentFloor)
                                DungeonTower.actionStorage.trigger("dungeon_first_enter_private", DungeonTower.actionStorage.createActionContext(
                                    DungeonTower.actionStorage.createPublicContext()
                                ).apply {
                                    this.target = player
                                    this.prepareParameters.let {
                                        it["party.uuid"] = party.partyUUID.toString()
                                        it.putAll(currentFloor.generateParameters())
                                    }
                                }) { section ->
                                    section.getString("floor") == currentFloor.internalName
                                }
                            }

                            DungeonTower.actionStorage.trigger("dungeon_enter", DungeonTower.actionStorage.createActionContext(
                                DungeonTower.actionStorage.createPublicContext()
                            ).apply {
                                this.target = player
                                this.prepareParameters.let {
                                    it["party.uuid"] = party.partyUUID.toString()
                                    it.putAll(currentFloor.generateParameters())
                                }
                            }) { section ->
                                section.getString("floor") == currentFloor.internalName
                            }

                            //exit
                            if (previousFloor != null) {
                                if (!publicExitedFloors.contains(previousFloor)) {
                                    publicExitedFloors.add(previousFloor)
                                    DungeonTower.actionStorage.trigger("dungeon_first_exit_public", DungeonTower.actionStorage.createActionContext(
                                        DungeonTower.actionStorage.createPublicContext()
                                    ).apply {
                                        this.target = player
                                        this.prepareParameters.let {
                                            it["party.uuid"] = party.partyUUID.toString()
                                            it.putAll(previousFloor.generateParameters())
                                        }
                                    }) { section ->
                                        section.getString("floor") == previousFloor.internalName
                                    }
                                }

                                if (!exitedFloors.computeIfAbsent(uuid) { arrayListOf() }.contains(previousFloor)) {
                                    exitedFloors[uuid]!!.add(previousFloor)
                                    DungeonTower.actionStorage.trigger("dungeon_first_exit_private", DungeonTower.actionStorage.createActionContext(
                                        DungeonTower.actionStorage.createPublicContext()
                                    ).apply {
                                        this.target = player
                                        this.prepareParameters.let {
                                            it["party.uuid"] = party.partyUUID.toString()
                                            it.putAll(previousFloor.generateParameters())
                                        }
                                    }) { section ->
                                        section.getString("floor") == previousFloor.internalName
                                    }
                                }

                                DungeonTower.actionStorage.trigger("dungeon_exit", DungeonTower.actionStorage.createActionContext(
                                    DungeonTower.actionStorage.createPublicContext()
                                ).apply {
                                    this.target = player
                                    this.prepareParameters.let {
                                        it["party.uuid"] = party.partyUUID.toString()
                                        it.putAll(previousFloor.generateParameters())
                                    }
                                }) { section ->
                                    section.getString("floor") == previousFloor.internalName
                                }
                            }
                        }
                        currentPlayerFloor[uuid] = currentFloor
                    } else {
                        currentPlayerFloor.remove(uuid)
                    }



                    val scoreboard = Bukkit.getScoreboardManager().newScoreboard
                    val obj = scoreboard.registerNewObjective("DungeonTower", Criteria.DUMMY, Component.text(floorDisplay))
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
                            for (i in 1..16 - it.mcid.length) {
                                builder.append(" ")
                            }
                            obj.getScore("§7$builder${it.mcid} $health").score = scoreInt
                            scoreInt--
                        }

                    player.scoreboard = scoreboard
                }
            }

//            if (nowFloor.clearTask.none { !it.clear }){
//                if (!unlockedChest){
//                    unlockedChest = true
//                    Bukkit.getScheduler().runTask(DungeonTower.plugin, Runnable {
//                        nowFloor.unlockChest()
//                    })
//                }
//            }

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