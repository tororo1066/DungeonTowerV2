package tororo1066.dungeontower.listeners

import org.bukkit.*
import org.bukkit.Particle.DustOptions
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.persistence.PersistentDataType
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.BoundingBox
import tororo1066.dungeontower.DungeonTower
import tororo1066.dungeontower.DungeonTower.Companion.sendPrefixMsg
import tororo1066.tororopluginapi.SStr
import tororo1066.tororopluginapi.SStr.Companion.toSStr
import tororo1066.tororopluginapi.annotation.SEventHandler
import tororo1066.tororopluginapi.utils.LocType
import tororo1066.tororopluginapi.utils.toLocString
import java.util.UUID

class WandListener {

    @SEventHandler
    fun interact(e: PlayerInteractEvent){
        if (!e.hasItem())return
        if (e.hand == EquipmentSlot.OFF_HAND)return
        val item = e.item!!
        if (!item.itemMeta.persistentDataContainer.has(
                NamespacedKey(DungeonTower.plugin,"wand"),
                PersistentDataType.INTEGER))return
        e.isCancelled = true

        if (e.action.isRightClick && e.hasBlock()){
            val locStr = e.clickedBlock!!.location.toLocString(LocType.BLOCK_COMMA)
            if (item.itemMeta.lore.isNullOrEmpty()){
                val meta = item.itemMeta
                meta.persistentDataContainer.set(
                    NamespacedKey(DungeonTower.plugin,"firstloc"),
                    PersistentDataType.STRING,locStr)
                val lore = meta.lore()?: mutableListOf()
                lore.add(SStr("§d始点.${locStr}").toPaperComponent())
                meta.lore(lore)
                item.itemMeta = meta
                e.player.sendPrefixMsg(SStr("&a始点を${locStr}にしたよ～"))
            } else {
                if (item.itemMeta.lore?.size == 1){
                    val meta = item.itemMeta
                    meta.persistentDataContainer.set(
                        NamespacedKey(DungeonTower.plugin,"secondloc"),
                        PersistentDataType.STRING,locStr)
                    val lore = meta.lore()?: mutableListOf()
                    lore.add(SStr("§d終点.${locStr}").toPaperComponent())
                    meta.lore(lore)
                    item.itemMeta = meta
                    e.player.sendPrefixMsg(SStr("&a終点を${locStr}にしたよ～"))
                }
            }
        }
        if (e.action.isLeftClick){
            val meta = item.itemMeta
            meta.lore(null)
            meta.persistentDataContainer.remove(NamespacedKey(DungeonTower.plugin,"firstloc"))
            meta.persistentDataContainer.remove(NamespacedKey(DungeonTower.plugin,"secondloc"))
            item.itemMeta = meta
            e.player.sendPrefixMsg(SStr("&aけしたよ"))
        }
    }
}