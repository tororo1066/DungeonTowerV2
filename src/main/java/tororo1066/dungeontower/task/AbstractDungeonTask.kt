package tororo1066.dungeontower.task

import com.google.common.io.ByteStreams
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryType
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import tororo1066.dungeontower.DungeonTower
import tororo1066.dungeontower.data.FloorData
import tororo1066.dungeontower.data.PartyData
import tororo1066.dungeontower.data.TowerData
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
    var lobbyLocation = DungeonTower.lobbyLocation


    protected fun runTask(unit: ()->Unit){
        val lock = Lock()

        Bukkit.getScheduler().runTask(DungeonTower.plugin, Runnable {
            unit.invoke()
            lock.unlock()
        })

        lock.lock()
    }

    protected fun clearDungeonItems(p: Player) {
        fun clearDungeonItem(itemStack: ItemStack?) {
            if (itemStack == null) return
            if (itemStack.type.isAir || !itemStack.hasItemMeta()) return
            val sItem = SItem(itemStack)
            if (sItem.getCustomData(DungeonTower.plugin, DungeonTower.DUNGEON_LOOT_ITEM, PersistentDataType.INTEGER) != null) {
                if (sItem.getCustomData(DungeonTower.plugin, DungeonTower.DUNGEON_LOOT_REMOVE_ON_DEATH, PersistentDataType.INTEGER) != null) {
                    itemStack.amount = 0
                } else {
                    itemStack.editMeta { meta ->
                        meta.persistentDataContainer.remove(NamespacedKey(DungeonTower.plugin, DungeonTower.DUNGEON_LOOT_ITEM))
                    }
                }
            }

        }
        val cursorItem = p.itemOnCursor
        clearDungeonItem(cursorItem)
        p.inventory.forEach {
            clearDungeonItem(it)
        }
        if (p.openInventory.type == InventoryType.PLAYER) {
            val top = p.openInventory.topInventory
            top.forEach {
                clearDungeonItem(it)
            }
        }
    }

    protected fun stepItems(p: Player){
        fun stepItem(itemStack: ItemStack?) {
            if (itemStack == null) return
            if (itemStack.type.isAir || !itemStack.hasItemMeta()) return
            val sItem = SItem(itemStack)
            if (sItem.getCustomData(DungeonTower.plugin, DungeonTower.DUNGEON_LOOT_REMOVE_FLOOR_COUNT, PersistentDataType.INTEGER) != null) {
                val data = sItem.getCustomData(DungeonTower.plugin, DungeonTower.DUNGEON_LOOT_REMOVE_FLOOR_COUNT, PersistentDataType.INTEGER)?:return
                if (data - 1 <= 0) {
                    itemStack.amount = 0
                } else {
                    itemStack.editMeta { meta -> meta.persistentDataContainer.set(
                        NamespacedKey(DungeonTower.plugin, DungeonTower.DUNGEON_LOOT_REMOVE_FLOOR_COUNT),
                        PersistentDataType.INTEGER,data - 1) }
                }
            }
        }
        val cursorItem = p.itemOnCursor
        stepItem(cursorItem)
        p.inventory.forEach {
            stepItem(it)
        }
        if (p.openInventory.type == InventoryType.PLAYER) {
            val top = p.openInventory.topInventory
            top.forEach {
                stepItem(it)
            }
        }
    }

    protected fun dungeonItemToItem(p: Player){
        fun dungeonItemToItem(itemStack: ItemStack?) {
            if (itemStack == null) return
            if (itemStack.type.isAir || !itemStack.hasItemMeta()) return
            val sItem = SItem(itemStack)
            if (sItem.getCustomData(DungeonTower.plugin, DungeonTower.DUNGEON_LOOT_REMOVE_ON_EXIT, PersistentDataType.INTEGER) != null) {
                itemStack.amount = 0
                return
            }
            if (sItem.getCustomData(DungeonTower.plugin, DungeonTower.DUNGEON_LOOT_ITEM, PersistentDataType.INTEGER) != null) {
                itemStack.editMeta { meta ->
                    meta.persistentDataContainer.remove(NamespacedKey(DungeonTower.plugin, DungeonTower.DUNGEON_LOOT_ITEM))
                }
                if (sItem.getCustomData(DungeonTower.plugin, DungeonTower.DUNGEON_LOOT_ANNOUNCE, PersistentDataType.INTEGER) != null){
                    itemStack.editMeta { meta ->
                        meta.persistentDataContainer.remove(NamespacedKey(DungeonTower.plugin, DungeonTower.DUNGEON_LOOT_ANNOUNCE))
                    }
                    val out = ByteStreams.newDataOutput()
                    out.writeUTF("Message")
                    out.writeUTF("ALL")
                    out.writeUTF("${DungeonTower.prefix}§e§l${p.name}§dが§r${itemStack.itemMeta.displayName}§dを手に入れた！")
                    DungeonTower.plugin.server.sendPluginMessage(DungeonTower.plugin, "BungeeCord", out.toByteArray())
                }
            }
        }
        val cursorItem = p.itemOnCursor
        dungeonItemToItem(cursorItem)
        p.inventory.forEach {
            dungeonItemToItem(it)
        }
        if (p.openInventory.type == InventoryType.PLAYER) {
            val top = p.openInventory.topInventory
            top.forEach {
                dungeonItemToItem(it)
            }
        }
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

    protected fun end(delay: Long = 60) {
        val function = {
            party.players.forEach { (uuid, _) ->
                val p = uuid.toPlayer()
                p?.let {
                    clearDungeonItems(it)
                }
                if (p?.gameMode == GameMode.SPECTATOR){
                    p.spectatorTarget = null
                    p.gameMode = GameMode.SURVIVAL
                }
                p?.teleport(lobbyLocation)
                p?.scoreboard = Bukkit.getScoreboardManager().newScoreboard
                DungeonTower.partiesData.remove(uuid)
                DungeonTower.playNow.remove(uuid)
            }
            party.currentTask = null
            onEnd()
        }
        Bukkit.getScheduler().runTaskLater(DungeonTower.plugin, Runnable {
            function.invoke()
        }, delay)
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