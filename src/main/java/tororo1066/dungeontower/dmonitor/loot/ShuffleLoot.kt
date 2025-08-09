package tororo1066.dungeontower.dmonitor.loot

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import tororo1066.displaymonitorapi.actions.ActionResult
import tororo1066.displaymonitorapi.actions.IActionContext
import tororo1066.displaymonitorapi.configuration.IAdvancedConfigurationSection

class ShuffleLoot: AbstractLootElement() {

    var fillEmpty: Boolean = false

    override fun run(context: IActionContext): ActionResult {
        val parameters = context.publicContext.parameters
        val items = parameters["loot.items"] as? MutableList<ItemStack> ?: return ActionResult.noParameters("Loot items not found")
        if (fillEmpty) {
            (0 until 27 - items.size).forEach { _ ->
                items.add(ItemStack(Material.AIR))
            }
        }
        items.shuffle()
        parameters["loot.items"] = items
        return ActionResult.success()
    }

    override fun prepare(configuration: IAdvancedConfigurationSection) {
        fillEmpty = configuration.getBoolean("fillEmpty", false)
    }
}