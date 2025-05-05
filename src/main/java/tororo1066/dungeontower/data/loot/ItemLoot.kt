package tororo1066.dungeontower.data.loot

import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import tororo1066.displaymonitorapi.actions.ActionResult
import tororo1066.displaymonitorapi.actions.IActionContext
import tororo1066.displaymonitorapi.configuration.IAdvancedConfigurationSection
import tororo1066.dungeontower.DungeonTower
import tororo1066.tororopluginapi.SJavaPlugin
import tororo1066.tororopluginapi.sItem.SItem

class ItemLoot: AbstractLootElement() {

    var item: ItemStack? = null
    var amount: Int = 1

    var announce: Boolean = false
    var removeFloorCount: Int = 0
    var removeOnExit: Boolean = false
    var removeOnDeath: Boolean = true

    override fun run(context: IActionContext): ActionResult {
        val parameters = context.configuration?.parameters ?: return ActionResult.noParameters("Parameters not found")
        val items = parameters["loot.items"] as? MutableList<ItemStack> ?: return ActionResult.noParameters("Loot items not found")
        val item = item ?: return ActionResult.noParameters("Item not found")
        val itemClone = item.clone()
        itemClone.amount = amount
        items.add(itemClone)
        parameters["loot.items"] = items

        return ActionResult.success()
    }

    override fun prepare(section: IAdvancedConfigurationSection) {
        val item = section.getStringItemStack("item")?.let {
            SItem(it)
        }

        amount = section.getInt("amount", 1)

        announce = section.getBoolean("announce", false)
        removeFloorCount = section.getInt("removeFloorCount", 0)
        removeOnExit = section.getBoolean("removeOnExit", false)
        removeOnDeath = section.getBoolean("removeOnDeath", true)

        item ?: return

        item.setCustomData(SJavaPlugin.plugin, DungeonTower.DUNGEON_LOOT_ITEM, PersistentDataType.INTEGER, 1)
        if (announce) {
            item.setCustomData(SJavaPlugin.plugin, DungeonTower.DUNGEON_LOOT_ANNOUNCE, PersistentDataType.INTEGER, 1)
        }
        if (removeFloorCount > 0) {
            item.setCustomData(SJavaPlugin.plugin, DungeonTower.DUNGEON_LOOT_REMOVE_FLOOR_COUNT, PersistentDataType.INTEGER, removeFloorCount)
        }
        if (removeOnExit) {
            item.setCustomData(SJavaPlugin.plugin, DungeonTower.DUNGEON_LOOT_REMOVE_ON_EXIT, PersistentDataType.INTEGER, 1)
        }
        if (removeOnDeath) {
            item.setCustomData(SJavaPlugin.plugin, DungeonTower.DUNGEON_LOOT_REMOVE_ON_DEATH, PersistentDataType.INTEGER, 1)
        }

        this.item = item.build()
    }
}