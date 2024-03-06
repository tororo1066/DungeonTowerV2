package tororo1066.dungeontower.skilltree.parks.convenience

import org.bukkit.entity.Player
import tororo1066.dungeontower.DungeonTower
import tororo1066.dungeontower.skilltree.AbstractPark
import tororo1066.dungeontower.skilltree.ParkLocation
import tororo1066.dungeontower.skilltree.Skill

class BuffSkillUpgrade : AbstractPark("convenience", Skill.CONVENIENCE_SMALL_UP, cost = 1,
    needParks = listOf(listOf(BuffSkill::class.java))
) {

    override fun getLocation(): ParkLocation {
        return ParkLocation(10..13, 14..16)
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