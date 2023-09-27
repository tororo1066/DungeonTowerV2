package tororo1066.dungeontower.task

import com.google.common.io.ByteStreams
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.persistence.PersistentDataType
import tororo1066.dungeontower.DungeonTower
import tororo1066.dungeontower.data.FloorData
import tororo1066.dungeontower.data.PartyData
import tororo1066.dungeontower.data.TowerData
import tororo1066.tororopluginapi.SStr
import tororo1066.tororopluginapi.sEvent.SEvent

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

    protected fun clearDungeonItems(p: Player){
        p.inventory.contents?.forEach {
            if (it != null && it.itemMeta?.persistentDataContainer?.has(
                    NamespacedKey(DungeonTower.plugin,"dlootitem"),
                    PersistentDataType.STRING) == true){
                it.amount = 0
            }
        }
        val cursorItem = p.itemOnCursor
        if (!cursorItem.type.isAir && cursorItem.itemMeta.persistentDataContainer.has(
                NamespacedKey(DungeonTower.plugin,"dlootitem")
            )){
            cursorItem.amount = 0
        }
    }

    protected fun dungeonItemToItem(p: Player){
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
                    val out = ByteStreams.newDataOutput()
                    out.writeUTF("message")
                    out.writeUTF("${DungeonTower.prefix}§e§l${p.name}§dが§r${it.itemMeta.displayName}§dを手に入れた！")
                    DungeonTower.plugin.server.sendPluginMessage(DungeonTower.plugin, "tororo:dungeontower", out.toByteArray())
                    SStr("${DungeonTower.prefix}§e§l${p.name}§dが§r${it.itemMeta.displayName}§dを手に入れた！").broadcast()
                }
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

    protected open fun end(){

    }

    protected open fun onDeath(e: PlayerDeathEvent) {

    }

}