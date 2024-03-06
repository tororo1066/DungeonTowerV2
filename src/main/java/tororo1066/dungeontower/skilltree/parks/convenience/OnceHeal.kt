package tororo1066.dungeontower.skilltree.parks.convenience

import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import org.bukkit.inventory.meta.PotionMeta
import org.bukkit.persistence.PersistentDataType
import tororo1066.dungeontower.DungeonTower
import tororo1066.dungeontower.data.UserData
import tororo1066.dungeontower.skilltree.AbstractPark
import tororo1066.dungeontower.skilltree.ActionType
import tororo1066.dungeontower.skilltree.ParkLocation
import tororo1066.dungeontower.skilltree.Skill
import tororo1066.tororopluginapi.sItem.SInteractItemManager
import tororo1066.tororopluginapi.sItem.SItem

class OnceHeal: AbstractPark("convenience", Skill.CONVENIENCE_LARGE_2, cost = 1,
    blockedBy = listOf(JustGuard::class.java)
) {

    private val interactManager = SInteractItemManager(DungeonTower.plugin)
    var healAmount = 10.0

    override fun getLocation(): ParkLocation {
        return ParkLocation(0..6, 20..24)
    }

    override fun getSkillName(): String {
        return "§c§l治癒の風"
    }

    override fun getSkillDescription(): List<String> {
        return listOf(
            "§7フロアごとに1度だけ体力を回復できるアイテムを手に入れる",
            "§7既に持っている場合は手に入れられない",
            "§7回復量: §c${healAmount.toInt()}"
        )
    }

    override fun onAction(p: Player, action: ActionType, userData: UserData) {
        when(action) {
            ActionType.ENTER_FLOOR -> {
                if (p.itemOnCursor.itemMeta?.persistentDataContainer?.has(NamespacedKey(DungeonTower.plugin, "once_heal"), PersistentDataType.INTEGER) == true) return
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
                    e.item?.amount = 0
                    interactItem.delete()

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