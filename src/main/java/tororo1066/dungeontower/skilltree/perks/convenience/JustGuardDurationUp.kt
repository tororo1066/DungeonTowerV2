package tororo1066.dungeontower.skilltree.perks.convenience

import org.bukkit.entity.Player
import tororo1066.dungeontower.data.UserData
import tororo1066.dungeontower.skilltree.AbstractPerk
import tororo1066.dungeontower.skilltree.ActionType
import tororo1066.dungeontower.skilltree.PerkLocation
import tororo1066.dungeontower.skilltree.Skill

class JustGuardDurationUp: AbstractPerk("convenience", Skill.CONVENIENCE_SMALL_DOWN, cost = 1, needPerks = listOf(listOf(JustGuard::class.java))) {

    override fun getLocation(): Map<String, PerkLocation> {
        return mapOf(
            "2560x1440" to PerkLocation(-11..-9, 10..12),
            "1920x1080" to PerkLocation(-11..-9, 14..17)
        )
    }

    override fun getSkillName(): String {
        return "§6猶予"
    }

    override fun getSkillDescription(): List<String> {
        return listOf(
            "§6§lジャストガード§7の猶予が0.1秒増える"
        )
    }

    override fun onAction(p: Player, action: ActionType, userData: UserData) {
        if (action == ActionType.ENTER_DUNGEON) {
            val perk = userData.perks.values.first { it::class.java == JustGuard::class.java } as JustGuard
            perk.duration = 4L
        }
        if (action == ActionType.END_DUNGEON) {
            val perk = userData.perks.values.first { it::class.java == JustGuard::class.java } as JustGuard
            perk.duration = 2L
        }
    }
}