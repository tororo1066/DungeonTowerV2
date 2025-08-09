package tororo1066.dungeontower.dmonitor.loot

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.block.Container
import org.bukkit.inventory.ItemStack
import tororo1066.displaymonitorapi.actions.ActionResult
import tororo1066.displaymonitorapi.actions.IActionContext
import tororo1066.displaymonitorapi.configuration.Execute
import tororo1066.displaymonitorapi.configuration.IAdvancedConfigurationSection
import tororo1066.tororopluginapi.SJavaPlugin

class SupplyLoot: AbstractLootElement() {

    var actions: Execute = Execute.empty()

    override fun run(context: IActionContext): ActionResult {
        val parameters = context.publicContext.parameters
        val chestLocation = context.location ?: return ActionResult.locationRequired()

        parameters["loot.items"] = mutableListOf<ItemStack>()
        actions(context)
        val items = parameters["loot.items"] as? List<ItemStack> ?: return ActionResult.noParameters("Loot items not found")
        if (items.isEmpty() || items.size > 27) {
            return ActionResult.noParameters("Loot items not found or too many items (${items.size})")
        }
        Bukkit.getScheduler().runTask(SJavaPlugin.plugin, Runnable {
            val chest = chestLocation.block.state as? Container ?: return@Runnable
            val inventory = chest.snapshotInventory
            inventory.clear()
            for (i in items.indices) {
                val item = items[i]
                if (inventory.size > i) {
                    inventory.setItem(i, item)
                }
            }
            chest.update(true, false)
        })

        return ActionResult.success()
    }

    override fun prepare(section: IAdvancedConfigurationSection) {
        actions = section.getConfigExecute("actions") ?: actions
    }
}