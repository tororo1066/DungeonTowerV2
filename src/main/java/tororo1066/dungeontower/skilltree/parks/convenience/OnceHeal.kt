package tororo1066.dungeontower.skilltree.parks.convenience

import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import org.bukkit.inventory.meta.PotionMeta
import org.bukkit.persistence.PersistentDataType
import tororo1066.dungeontower.DungeonTower
import tororo1066.dungeontower.skilltree.AbstractPark
import tororo1066.dungeontower.skilltree.ActionType
import tororo1066.dungeontower.skilltree.ParkLocation
import tororo1066.dungeontower.skilltree.Skill
import tororo1066.tororopluginapi.sItem.SInteractItemManager
import tororo1066.tororopluginapi.sItem.SItem

class OnceHeal: AbstractPark("convenience", Skill.CONVENIENCE_LARGE_2) {

    val interactManager = SInteractItemManager(DungeonTower.plugin)
    val healAmount = 5.0

    override fun getLocation(): ParkLocation {
        return ParkLocation(0..6, 25..29)
    }

    override fun getSkillName(): String {
        return "§c§l治癒の風"
    }

    override fun getSkillDescription(): List<String> {
        return listOf(

        )
    }

    override fun onAction(p: Player, action: ActionType) {
        when(action) {
            ActionType.ENTER_FLOOR -> {
                if (p.inventory.any { it?.itemMeta?.persistentDataContainer?.has(NamespacedKey(DungeonTower.plugin, "once_heal"), PersistentDataType.INTEGER) == true }) return
                val item = SItem(Material.SPLASH_POTION)
                    .setDisplayName("§c§l治癒の風")
                    .addLore("§9体力を回復する")
                    .setCustomData(DungeonTower.plugin, "once_heal", PersistentDataType.INTEGER, 1)
                item.editMeta(PotionMeta::class.java) { meta ->
                    meta.color = Color.RED
                }
                val interactItem = interactManager.createSInteractItem(item, true).setInteractEvent { e, interactItem ->
                    val player = e.player
                    if (player.health == player.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.value) {
                        player.sendMessage("§c使う意味がないようだ...")
                        return@setInteractEvent true
                    }
                    player.health += healAmount
                    player.sendMessage("§c体力が回復した...")
                    interactItem.amount -= 1
                    return@setInteractEvent true
                }

                p.inventory.addItem(interactItem)
            }

            ActionType.END_DUNGEON -> {
                p.inventory.filter {
                    it?.itemMeta?.persistentDataContainer?.has(NamespacedKey(DungeonTower.plugin, "once_heal"), PersistentDataType.INTEGER) == true
                }.forEach {
                    it.amount = 0
                }

                if (p.itemOnCursor.itemMeta?.persistentDataContainer?.has(NamespacedKey(DungeonTower.plugin, "once_heal"), PersistentDataType.INTEGER) == true){
                    p.itemOnCursor.amount = 0
                }

                interactManager.unregister()
            }

            else -> {}
        }
    }
}