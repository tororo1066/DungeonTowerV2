package tororo1066.dungeontower.skilltree.parks.convenience

import org.bukkit.entity.Player
import tororo1066.dungeontower.data.UserData
import tororo1066.dungeontower.skilltree.AbstractPark
import tororo1066.dungeontower.skilltree.ActionType
import tororo1066.dungeontower.skilltree.ParkLocation
import tororo1066.dungeontower.skilltree.Skill

class JustGuardDurationUp: AbstractPark("convenience", Skill.CONVENIENCE_SMALL_DOWN, cost = 1, needParks = listOf(listOf(JustGuard::class.java))) {

    override fun getLocation(): ParkLocation {
        return ParkLocation(-11..-9, 10..12)
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
            val park = userData.parks.values.first { it::class.java == JustGuard::class.java } as JustGuard
            park.duration = 4L
        }
        if (action == ActionType.END_DUNGEON) {
            val park = userData.parks.values.first { it::class.java == JustGuard::class.java } as JustGuard
            park.duration = 2L
        }
    }
}