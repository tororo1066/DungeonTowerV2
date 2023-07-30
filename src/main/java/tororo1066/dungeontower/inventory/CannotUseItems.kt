package tororo1066.dungeontower.inventory

import org.bukkit.Material
import tororo1066.tororopluginapi.defaultMenus.LargeSInventory
import tororo1066.tororopluginapi.sInventory.SInventoryItem

class CannotUseItems: LargeSInventory("§c§l使えないアイテム") {

    override fun renderMenu(): Boolean {
        val items = arrayListOf(
            SInventoryItem(Material.ELYTRA).setCanClick(false),
            SInventoryItem(Material.GOLDEN_APPLE).setCanClick(false),
            SInventoryItem(Material.ENCHANTED_GOLDEN_APPLE).setCanClick(false),
            SInventoryItem(Material.TOTEM_OF_UNDYING).setCanClick(false),
            SInventoryItem(Material.IRON_SHOVEL).setDisplayName("§b§lウィング類")
                .setCustomModelData(39).setCanClick(false)
        )

        setResourceItems(items)
        return true
    }
}