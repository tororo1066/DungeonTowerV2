package tororo1066.dungeontower.data.loot

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
        val parameters = context.configuration?.parameters ?: return ActionResult.noParameters("Parameters not found")

        val chestLocationWorld = parameters["chest.location.world"] as? String ?: return ActionResult.noParameters("Chest location world not found")
        val chestLocationX = parameters["chest.location.x"] as? Int ?: return ActionResult.noParameters("Chest location x not found")
        val chestLocationY = parameters["chest.location.y"] as? Int ?: return ActionResult.noParameters("Chest location y not found")
        val chestLocationZ = parameters["chest.location.z"] as? Int ?: return ActionResult.noParameters("Chest location z not found")
        val world = Bukkit.getWorld(chestLocationWorld) ?: return ActionResult.noParameters("Chest location world not found")
        val chestLocation =
            Location(world, chestLocationX.toDouble(), chestLocationY.toDouble(), chestLocationZ.toDouble())

        parameters["loot.items"] = mutableListOf<ItemStack>()
        actions(context)
        val items = parameters["loot.items"] as? List<ItemStack> ?: return ActionResult.noParameters("Loot items not found")
        if (items.isEmpty() || items.size > 27) {
            return ActionResult.noParameters("Loot items not found or too many items (${items.size})")
        }
        Bukkit.getScheduler().runTask(SJavaPlugin.plugin, Runnable {
            val chest = world.getBlockAt(chestLocation).state as? Container ?: return@Runnable
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