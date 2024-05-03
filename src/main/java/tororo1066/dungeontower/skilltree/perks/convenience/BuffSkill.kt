package tororo1066.dungeontower.skilltree.perks.convenience

import org.bukkit.entity.Player
import tororo1066.dungeontower.DungeonTower
import tororo1066.dungeontower.skilltree.AbstractPerk
import tororo1066.dungeontower.skilltree.PerkLocation
import tororo1066.dungeontower.skilltree.Skill

class BuffSkill: AbstractPerk("convenience", Skill.CONVENIENCE_MIDDLE_3, cost = 1,
    needPerks = listOf(listOf(JustGuard::class.java), listOf(OnceHeal::class.java)),
    blockedBy = listOf(HealSkill::class.java)
) {

    override fun getLocation(): PerkLocation {
        return PerkLocation(4..9, 15..18)
    }

    override fun getSkillName(): String {
        return "§5強化"
    }

    override fun getSkillDescription(): List<String> {
        return listOf(
            "§7自身の攻撃力、防御力を上げる魔法が使えるようになる"
        )
    }

    override fun onLearned(p: Player) {
        val wandItem = DungeonTower.magicAPI.controller.createWand("dungeon_sword_parts_4")?.item
        if (wandItem == null){
            p.sendMessage("§cエラー")
            return
        }
        p.inventory.addItem(wandItem)
    }

    override fun onUnlearned(p: Player) {
        DungeonTower.magicAPI.controller.createWand("dungeon_sword_parts_4")?.item?.let {
            p.inventory.removeItem(it)
            if (p.itemOnCursor == it) p.setItemOnCursor(null)
        }
    }
}