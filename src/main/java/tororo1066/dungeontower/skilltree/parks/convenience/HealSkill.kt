package tororo1066.dungeontower.skilltree.parks.convenience

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import tororo1066.dungeontower.DungeonTower
import tororo1066.dungeontower.skilltree.AbstractPark
import tororo1066.dungeontower.skilltree.ParkLocation
import tororo1066.dungeontower.skilltree.Skill
import kotlin.math.cos

class HealSkill: AbstractPark("convenience", Skill.CONVENIENCE_MIDDLE_1, cost = 1,
    needParks = listOf(listOf(JustGuard::class.java), listOf(OnceHeal::class.java)),
    blockedBy = listOf(BuffSkill::class.java)
) {

    override fun getLocation(): ParkLocation {
        return ParkLocation(-9..-4, 15..18)
    }

    override fun getSkillName(): String {
        return "§a再生"
    }

    override fun getSkillDescription(): List<String> {
        return listOf(
            "§7体力が回復する魔法が使えるようになる"
        )
    }

    override fun onLearned(p: Player) {
        val wandItem = DungeonTower.magicAPI.controller.createWand("dungeon_sword_parts_3")?.item
        if (wandItem == null){
            p.sendMessage("§cエラー")
            return
        }
        p.inventory.addItem(wandItem)
    }

    override fun onUnlearned(p: Player) {
        DungeonTower.magicAPI.controller.createWand("dungeon_sword_parts_3")?.item?.let {
            p.inventory.removeItem(it)
            if (p.itemOnCursor == it) p.setItemOnCursor(null)
        }
    }

}