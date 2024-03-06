package tororo1066.dungeontower.skilltree.parks.convenience

import org.bukkit.entity.Player
import tororo1066.dungeontower.data.UserData
import tororo1066.dungeontower.skilltree.AbstractPark
import tororo1066.dungeontower.skilltree.ActionType
import tororo1066.dungeontower.skilltree.ParkLocation
import tororo1066.dungeontower.skilltree.Skill

class OnceHealAmountUp: AbstractPark("convenience", Skill.CONVENIENCE_SMALL_DOWN, cost = 1,
    needParks = listOf(listOf(OnceHeal::class.java))
) {

    override fun getLocation(): ParkLocation {
        return ParkLocation(8..10, 10..12)
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
            val park = userData.parks.values.first { it::class.java == OnceHeal::class.java } as OnceHeal
            park.healAmount = 12.0
        }
        if (action == ActionType.END_DUNGEON) {
            val park = userData.parks.values.first { it::class.java == OnceHeal::class.java } as OnceHeal
            park.healAmount = 10.0
        }
    }
}