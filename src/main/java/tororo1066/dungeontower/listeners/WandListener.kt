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

//    val tasks = HashMap<UUID, BukkitTask>()
//
//    @SEventHandler
//    fun onHeld(e: PlayerItemHeldEvent) {
//        val p = e.player
//        val item = p.inventory.getItem(e.newSlot)
//        if (item?.itemMeta?.persistentDataContainer?.has(
//                NamespacedKey(DungeonTower.plugin,"wand"),
//                PersistentDataType.INTEGER) == false
//        ) {
//            val task = tasks[p.uniqueId]
//            if (task != null) {
//                task.cancel()
//                tasks.remove(p.uniqueId)
//            }
//            return
//        }
//
//        val task = tasks[p.uniqueId]
//        if (task != null) {
//            task.cancel()
//            tasks.remove(p.uniqueId)
//        }
//
//        tasks[p.uniqueId] = Bukkit.getScheduler().runTaskTimerAsynchronously(DungeonTower.plugin, Runnable {
//            val box = BoundingBox.of(
//                p.location.clone().add(-5.0, -5.0, -5.0),
//                p.location.clone().add(5.0, 5.0, 5.0)
//            )
//            val nearFloors = DungeonTower.floorData.values.filter {
//                box.contains(it.startLoc.toVector()) || box.contains(it.endLoc.toVector())
//            }
//
//            nearFloors.forEach {
//                showVertexParticle(p, it.startLoc)
//                showVertexParticle(p, it.endLoc)
//            }
//        }, 0, 1)
//
//    }
//
//    private fun showVertexParticle(p: Player, loc: Location) {
//        //render box particle
//        drawLine(p, loc.clone().add(-0.5, -0.5, -0.5), loc.clone().add(0.5, -0.5, -0.5))
//        drawLine(p, loc.clone().add(-0.5, -0.5, -0.5), loc.clone().add(-0.5, 0.5, -0.5))
//        drawLine(p, loc.clone().add(-0.5, -0.5, -0.5), loc.clone().add(-0.5, -0.5, 0.5))
//        drawLine(p, loc.clone().add(0.5, 0.5, 0.5), loc.clone().add(-0.5, 0.5, 0.5))
//        drawLine(p, loc.clone().add(0.5, 0.5, 0.5), loc.clone().add(0.5, -0.5, 0.5))
//        drawLine(p, loc.clone().add(0.5, 0.5, 0.5), loc.clone().add(0.5, 0.5, -0.5))
//        drawLine(p, loc.clone().add(-0.5, 0.5, -0.5), loc.clone().add(0.5, 0.5, -0.5))
//        drawLine(p, loc.clone().add(-0.5, 0.5, -0.5), loc.clone().add(-0.5, -0.5, -0.5))
//        drawLine(p, loc.clone().add(-0.5, 0.5, 0.5), loc.clone().add(-0.5, -0.5, 0.5))
//        drawLine(p, loc.clone().add(0.5, -0.5, 0.5), loc.clone().add(-0.5, -0.5, 0.5))
//        drawLine(p, loc.clone().add(0.5, -0.5, 0.5), loc.clone().add(0.5, -0.5, -0.5))
//        drawLine(p, loc.clone().add(0.5, -0.5, -0.5), loc.clone().add(0.5, 0.5, -0.5))
//    }
//
//    private fun drawLine(p: Player, start: Location, end: Location) {
//        val distance = start.distance(end)
//        val direction = end.toVector().subtract(start.toVector()).normalize()
//        val particles = distance.toInt()
//        val space = distance / particles
//        for (i in 0 until particles) {
//            val loc = start.clone().add(direction.clone().multiply(space * i))
//            p.spawnParticle(Particle.REDSTONE, loc, 1, 0.0, 0.0, 0.0, DustOptions(Color.RED, 1.0f))
//        }
//    }
}