package tororo1066.dungeontower.skilltree.perks.convenience

import org.bukkit.entity.Player
import tororo1066.dungeontower.data.UserData
import tororo1066.dungeontower.skilltree.AbstractPerk
import tororo1066.dungeontower.skilltree.ActionType
import tororo1066.dungeontower.skilltree.PerkLocation
import tororo1066.dungeontower.skilltree.Skill

class OnceHealAmountUp: AbstractPerk("convenience", Skill.CONVENIENCE_SMALL_DOWN, cost = 1,
    needPerks = listOf(listOf(OnceHeal::class.java))
) {

    override fun getLocation(): Map<String, PerkLocation> {
        return mapOf(
            "2560x1440" to PerkLocation(8..10, 10..12),
            "1920x1080" to PerkLocation(8..10, 14..17)
        )
    }

    override fun getSkillName(): String {
        return "§c癒しの風"
    }

    override fun getSkillDescription(): List<String> {
        return listOf(
            "§c§l治癒の風§7の回復量が2増える"
        )
    }

    override fun onAction(p: Player, action: ActionType, userData: UserData) {
        if (action == ActionType.ENTER_DUNGEON) {
            val perk = userData.perks.values.first { it::class.java == OnceHeal::class.java } as OnceHeal
            perk.healAmount = 12.0
        }
        if (action == ActionType.END_DUNGEON) {
            val perk = userData.perks.values.first { it::class.java == OnceHeal::class.java } as OnceHeal
            perk.healAmount = 10.0
        }
    }
}