package tororo1066.dungeontower.skilltree.parks.convenience

import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.entity.Player
import tororo1066.dungeontower.data.UserData
import tororo1066.dungeontower.skilltree.AbstractPark
import tororo1066.dungeontower.skilltree.ActionType
import tororo1066.dungeontower.skilltree.ParkLocation
import tororo1066.dungeontower.skilltree.Skill

class SpeedUp: AbstractPark("convenience", Skill.CONVENIENCE_SMALL_CENTER, cost = 1) {

    override fun getLocation(): ParkLocation {
        return ParkLocation(3..6, 11..13)
    }

    override fun getSkillName(): String {
        return "§b加速"
    }

    override fun getSkillDescription(): List<String> {
        return listOf(
            "§7移動速度が10%上昇する"
        )
    }

    override fun registerPark(p: Player, userData: UserData) {
        p.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED)?.addModifier(
            AttributeModifier(
                "speed_up",
                0.1,
                AttributeModifier.Operation.ADD_SCALAR
            )
        )
    }

    override fun onAction(p: Player, action: ActionType, userData: UserData) {
        if (action == ActionType.END_DUNGEON) {
            val attribute = p.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED)
            attribute?.modifiers?.firstOrNull { it.name == "speed_up" }?.let {
                attribute.removeModifier(it)
            }
        }
    }

}