package tororo1066.dungeontower.skilltree.perks.convenience

import org.bukkit.entity.Player
import tororo1066.dungeontower.DungeonTower
import tororo1066.dungeontower.skilltree.AbstractPerk
import tororo1066.dungeontower.skilltree.PerkLocation
import tororo1066.dungeontower.skilltree.Skill

class HealSkill: AbstractPerk("convenience", Skill.CONVENIENCE_MIDDLE_1, cost = 2,
    needPerks = listOf(listOf(JustGuard::class.java), listOf(OnceHeal::class.java)),
    blockedBy = listOf(BuffSkill::class.java)
) {

    override fun getLocation(): Map<String, PerkLocation> {
        return mapOf(
            "2560x1440" to PerkLocation(-9..-4, 15..18),
            "1920x1080" to PerkLocation(-9..-4, 19..22)
        )
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