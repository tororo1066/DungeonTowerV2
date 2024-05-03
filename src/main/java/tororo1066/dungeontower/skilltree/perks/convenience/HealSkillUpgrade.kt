package tororo1066.dungeontower.skilltree.perks.convenience

import org.bukkit.entity.Player
import tororo1066.dungeontower.DungeonTower
import tororo1066.dungeontower.skilltree.AbstractPerk
import tororo1066.dungeontower.skilltree.PerkLocation
import tororo1066.dungeontower.skilltree.Skill

class HealSkillUpgrade: AbstractPerk("convenience", Skill.CONVENIENCE_SMALL_UP, cost = 1,
    needPerks = listOf(listOf(HealSkill::class.java))
) {

    override fun getLocation(): PerkLocation {
        return PerkLocation(-14..-11, 14..16)
    }

    override fun getSkillName(): String {
        return "§a§l再生"
    }

    override fun getSkillDescription(): List<String> {
        return listOf(
            "§a再生§7の回復量が1増える"
        )
    }

    override fun onLearned(p: Player) {
        val wandItem = DungeonTower.magicAPI.controller.createWand("dungeon_sword_upgrade_3")?.item
        if (wandItem == null){
            p.sendMessage("§cエラー")
            return
        }
        p.inventory.addItem(wandItem)
    }

    override fun onUnlearned(p: Player) {
        DungeonTower.magicAPI.controller.createWand("dungeon_sword_upgrade_3")?.item?.let {
            p.inventory.removeItem(it)
            if (p.itemOnCursor == it) p.setItemOnCursor(null)
        }
    }
}