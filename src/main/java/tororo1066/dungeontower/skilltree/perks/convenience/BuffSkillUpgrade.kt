package tororo1066.dungeontower.skilltree.perks.convenience

import org.bukkit.entity.Player
import tororo1066.dungeontower.DungeonTower
import tororo1066.dungeontower.skilltree.AbstractPerk
import tororo1066.dungeontower.skilltree.PerkLocation
import tororo1066.dungeontower.skilltree.Skill

class BuffSkillUpgrade : AbstractPerk(
    "convenience", Skill.CONVENIENCE_SMALL_UP, cost = 1,
    needPerks = listOf(listOf(BuffSkill::class.java))
) {

    override fun getLocation(): Map<String, PerkLocation> {
        return mapOf(
            "2560x1440" to PerkLocation(10..13, 14..16),
            "1920x1080" to PerkLocation(14..17, 18..21)
        )
    }

    override fun getSkillName(): String {
        return "§5§l強化"
    }

    override fun getSkillDescription(): List<String> {
        return listOf(
            "§5強化§7の効果時間が延びる"
        )
    }

    override fun onLearned(p: Player) {
        val wandItem = DungeonTower.magicAPI.controller.createWand("dungeon_sword_upgrade_4")?.item
        if (wandItem == null){
            p.sendMessage("§cエラー")
            return
        }
        p.inventory.addItem(wandItem)
    }

    override fun onUnlearned(p: Player) {
        DungeonTower.magicAPI.controller.createWand("dungeon_sword_upgrade_4")?.item?.let {
            p.inventory.removeItem(it)
            if (p.itemOnCursor == it) p.setItemOnCursor(null)
        }
    }
}