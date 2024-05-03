package tororo1066.dungeontower.task

import com.google.common.io.ByteStreams
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.scoreboard.DisplaySlot
import tororo1066.dungeontower.DungeonTower
import tororo1066.dungeontower.data.FloorData
import tororo1066.dungeontower.data.PartyData
import tororo1066.dungeontower.data.TowerData
import tororo1066.dungeontower.skilltree.ActionType
import tororo1066.tororopluginapi.sEvent.SEvent
import tororo1066.tororopluginapi.sItem.SItem
import tororo1066.tororopluginapi.utils.toPlayer

abstract class AbstractDungeonTask(val party: PartyData, val tower: TowerData): Thread() {

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
                while (isLock){
                    sleep(1)
                }
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

    val sEvent = SEvent(DungeonTower.plugin)


    protected fun runTask(unit: ()->Unit){
        val lock = Lock()

        Bukkit.getScheduler().runTask(DungeonTower.plugin, Runnable {
            unit.invoke()
            lock.unlock()
        })

        lock.lock()
    }

    protected fun asyncRunTask(unit: ()->Unit) {
        Bukkit.getScheduler().runTaskAsynchronously(DungeonTower.plugin, Runnable {
            unit.invoke()
        })
    }

    protected fun clearDungeonItems(p: Player) {
        fun clearDungeonItem(itemStack: ItemStack?) {
            if (itemStack == null) return
            if (itemStack.type.isAir || !itemStack.hasItemMeta()) return
            val sItem = SItem(itemStack)
            if (sItem.getCustomData(DungeonTower.plugin, "dlootitem", PersistentDataType.INTEGER) != null) {
                if (sItem.getCustomData(DungeonTower.plugin, "dlootremoveondeath", PersistentDataType.INTEGER) != null) {
                    itemStack.amount = 0
                } else {
                    itemStack.editMeta { meta -> meta.persistentDataContainer.remove(NamespacedKey(DungeonTower.plugin,"dlootitem")) }
                }
            }

        }
        p.inventory.forEach {
            clearDungeonItem(it)
        }
        val cursorItem = p.itemOnCursor
        clearDungeonItem(cursorItem)
    }

    protected fun stepItems(p: Player){
        fun stepItem(itemStack: ItemStack?) {
            if (itemStack == null) return
            if (itemStack.type.isAir || !itemStack.hasItemMeta()) return
            val sItem = SItem(itemStack)
            if (sItem.getCustomData(DungeonTower.plugin, "dlootremovefloor", PersistentDataType.INTEGER) != null) {
                val data = sItem.getCustomData(DungeonTower.plugin, "dlootremovefloor", PersistentDataType.INTEGER)?:return
                if (data - 1 <= 0) {
                    itemStack.amount = 0
                } else {
                    itemStack.editMeta { meta -> meta.persistentDataContainer.set(
                        NamespacedKey(DungeonTower.plugin,"dlootremovefloor"),
                        PersistentDataType.INTEGER,data - 1) }
                }
            }
        }
        p.inventory.forEach {
            stepItem(it)
        }
        val cursorItem = p.itemOnCursor
        stepItem(cursorItem)
    }

    protected fun dungeonItemToItem(p: Player){
        fun dungeonItemToItem(itemStack: ItemStack?) {
            if (itemStack == null) return
            if (itemStack.type.isAir || !itemStack.hasItemMeta()) return
            val sItem = SItem(itemStack)
            if (sItem.getCustomData(DungeonTower.plugin, "dlootremoveonexit", PersistentDataType.INTEGER) != null) {
                itemStack.amount = 0
                return
            }
            if (sItem.getCustomData(DungeonTower.plugin, "dlootitem", PersistentDataType.INTEGER) != null) {
                itemStack.editMeta { meta -> meta.persistentDataContainer.remove(NamespacedKey(DungeonTower.plugin,"dlootitem")) }
                if (sItem.getCustomData(DungeonTower.plugin, "dlootannounce", PersistentDataType.INTEGER) != null){
                    itemStack.editMeta { meta -> meta.persistentDataContainer.remove(NamespacedKey(DungeonTower.plugin,"dlootannounce")) }
                    val out = ByteStreams.newDataOutput()
                    out.writeUTF("Message")
                    out.writeUTF("ALL")
                    out.writeUTF("${DungeonTower.prefix}§e§l${p.name}§dが§r${itemStack.itemMeta.displayName}§dを手に入れた！")
                    DungeonTower.plugin.server.sendPluginMessage(DungeonTower.plugin, "BungeeCord", out.toByteArray())
                }
            }
        }
        p.inventory.forEach {
            dungeonItemToItem(it)
        }
        val cursorItem = p.itemOnCursor
        dungeonItemToItem(cursorItem)
    }

    protected fun callCommand(floor: FloorData){
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

    protected fun formatTask(floor: FloorData, task: FloorData.ClearTask): String {
        val replace = " " + (if (task.clear) task.clearScoreboardName else task.scoreboardName)
            .replace("&", "§")
            .replace("<and>", "&")
            .replace("<spawnerNavigateNeed>", floor.getAllTask()
                .filter { fil -> fil.type == FloorData.ClearTaskEnum.KILL_SPAWNER_MOBS }
                .sumOf { map -> map.need }.toString()
            )
            .replace("<spawnerNavigateCount>", floor.getAllTask()
                .filter { fil -> fil.type == FloorData.ClearTaskEnum.KILL_SPAWNER_MOBS }
                .sumOf { map -> map.count }.toString()
            )
            .replace("<gimmickNeed>", floor.getAllTask()
                .find { find -> find.type == FloorData.ClearTaskEnum.ENTER_COMMAND }
                ?.need.toString())
            .replace("<gimmickCount>", floor.getAllTask()
                .find { find -> find.type == FloorData.ClearTaskEnum.ENTER_COMMAND }
                ?.count.toString())
        return replace
    }

    protected fun end(delay: Long = 60) {
        Bukkit.getScheduler().runTaskLater(DungeonTower.plugin, Runnable {
            party.players.forEach { (uuid, data) ->
                data.invokePerk(ActionType.END_DUNGEON)
                val p = uuid.toPlayer()
                p?.let {
                    clearDungeonItems(it)
                }
                if (p?.gameMode == GameMode.SPECTATOR){
                    p.spectatorTarget = null
                    p.gameMode = GameMode.SURVIVAL
                }
                p?.teleport(DungeonTower.lobbyLocation)
                p?.scoreboard?.clearSlot(DisplaySlot.SIDEBAR)
                DungeonTower.partiesData.remove(uuid)
                DungeonTower.playNow.remove(uuid)
            }
            onEnd()
        },delay)
    }

    protected fun getInFloor(mainFloor: FloorData, p: Player): FloorData? {
        fun check(floor: FloorData): Boolean {
            val startLoc = floor.dungeonStartLoc?:return false
            val endLoc = floor.dungeonEndLoc?:return false
            val x = startLoc.blockX..endLoc.blockX
            val y = startLoc.blockY..endLoc.blockY
            val z = startLoc.blockZ..endLoc.blockZ
            val world = startLoc.world
            return world.name == p.world.name && x.contains(p.location.blockX) && y.contains(p.location.blockY) && z.contains(p.location.blockZ)
        }

        mainFloor.parallelFloors.values.forEach { parallel ->
            getInFloor(parallel,p)?.let { return it }
            if (check(parallel)){
                return parallel
            }
        }

        if (check(mainFloor)){
            return mainFloor
        }

        return null
    }

    protected open fun onEnd(){

    }

}