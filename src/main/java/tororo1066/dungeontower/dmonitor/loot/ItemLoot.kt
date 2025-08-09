package tororo1066.dungeontower.dmonitor.loot

import io.lumine.mythic.bukkit.MythicBukkit
import org.bukkit.Bukkit
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import tororo1066.displaymonitorapi.actions.ActionResult
import tororo1066.displaymonitorapi.actions.IActionContext
import tororo1066.displaymonitorapi.configuration.IAdvancedConfigurationSection
import tororo1066.dungeontower.DungeonTower
import tororo1066.tororopluginapi.SJavaPlugin
import tororo1066.tororopluginapi.sItem.SItem

class ItemLoot: AbstractLootElement() {

    enum class Type {
        MythicMobs,
        Magic,
        Vanilla
    }

    var itemString: String? = null
    var amount: Int = 1
    var type: Type = Type.Vanilla

    var announce: Boolean = false
    var removeFloorCount: Int = 0
    var removeOnExit: Boolean = false
    var removeOnDeath: Boolean = true

    override fun run(context: IActionContext): ActionResult {
        val parameters = context.publicContext.parameters
        val items = parameters["loot.items"] as? MutableList<ItemStack> ?: return ActionResult.noParameters("Loot items not found")
        val itemString = itemString ?: return ActionResult.noParameters("Item string not found")
        val item = when(type) {
            Type.MythicMobs -> MythicBukkit.inst().itemManager.getItemStack(itemString)
            Type.Magic -> DungeonTower.magicAPI.controller.getItem(itemString)?.itemStack
            Type.Vanilla -> Bukkit.getItemFactory().createItemStack(itemString)
        } ?: return ActionResult.noParameters("Item not found for string: $itemString")
        val sItem = SItem(item)

        sItem.setItemAmount(item.amount)

        sItem.setCustomData(SJavaPlugin.plugin, DungeonTower.DUNGEON_LOOT_ITEM, PersistentDataType.INTEGER, 1)
        if (announce) {
            sItem.setCustomData(SJavaPlugin.plugin, DungeonTower.DUNGEON_LOOT_ANNOUNCE, PersistentDataType.INTEGER, 1)
        }
        if (removeFloorCount > 0) {
            sItem.setCustomData(SJavaPlugin.plugin, DungeonTower.DUNGEON_LOOT_REMOVE_FLOOR_COUNT, PersistentDataType.INTEGER, removeFloorCount)
        }
        if (removeOnExit) {
            sItem.setCustomData(SJavaPlugin.plugin, DungeonTower.DUNGEON_LOOT_REMOVE_ON_EXIT, PersistentDataType.INTEGER, 1)
        }
        if (removeOnDeath) {
            sItem.setCustomData(SJavaPlugin.plugin, DungeonTower.DUNGEON_LOOT_REMOVE_ON_DEATH, PersistentDataType.INTEGER, 1)
        }

        items.add(sItem.build())
        parameters["loot.items"] = items

        return ActionResult.success()
    }

    override fun prepare(section: IAdvancedConfigurationSection) {
        itemString = section.getString("item") ?: return
        type = section.getEnum("type", Type::class.java, Type.Vanilla)

        amount = section.getInt("amount", 1)

        announce = section.getBoolean("announce", false)
        removeFloorCount = section.getInt("removeFloorCount", 0)
        removeOnExit = section.getBoolean("removeOnExit", false)
        removeOnDeath = section.getBoolean("removeOnDeath", true)

//        item ?: return
//
//        item.setCustomData(SJavaPlugin.plugin, DungeonTower.DUNGEON_LOOT_ITEM, PersistentDataType.INTEGER, 1)
//        if (announce) {
//            item.setCustomData(SJavaPlugin.plugin, DungeonTower.DUNGEON_LOOT_ANNOUNCE, PersistentDataType.INTEGER, 1)
//        }
//        if (removeFloorCount > 0) {
//            item.setCustomData(SJavaPlugin.plugin, DungeonTower.DUNGEON_LOOT_REMOVE_FLOOR_COUNT, PersistentDataType.INTEGER, removeFloorCount)
//        }
//        if (removeOnExit) {
//            item.setCustomData(SJavaPlugin.plugin, DungeonTower.DUNGEON_LOOT_REMOVE_ON_EXIT, PersistentDataType.INTEGER, 1)
//        }
//        if (removeOnDeath) {
//            item.setCustomData(SJavaPlugin.plugin, DungeonTower.DUNGEON_LOOT_REMOVE_ON_DEATH, PersistentDataType.INTEGER, 1)
//        }
//
//        this.item = item.build()
    }
}