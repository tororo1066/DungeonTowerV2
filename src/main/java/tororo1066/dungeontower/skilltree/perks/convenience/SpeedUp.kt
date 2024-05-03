package tororo1066.dungeontower.skilltree.perks.convenience

import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.entity.Player
import tororo1066.dungeontower.data.UserData
import tororo1066.dungeontower.skilltree.AbstractPerk
import tororo1066.dungeontower.skilltree.ActionType
import tororo1066.dungeontower.skilltree.PerkLocation
import tororo1066.dungeontower.skilltree.Skill

class SpeedUp: AbstractPerk("convenience", Skill.CONVENIENCE_SMALL_CENTER, cost = 1) {

    override fun getLocation(): PerkLocation {
        return PerkLocation(3..6, 11..13)
    }

    override fun getSkillName(): String {
        return "§b加速"
    }

    override fun getSkillDescription(): List<String> {
        return listOf(
            "§7移動速度が10%上昇する"
        )
    }

    override fun registerPerk(p: Player, userData: UserData) {
        p.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED)?.addModifier(
            AttributeModifier(
                "speed_up",
                0.1,
                AttributeModifier.Operation.ADD_SCALAR
            )
        )
    }

    override fun onAction(p: Player, action: ActionType, userData: UserData) {
        if (action == ActionType.END_DUNGEON || action == ActionType.DIE) {
            val attribute = p.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED)
            attribute?.modifiers?.firstOrNull { it.name == "speed_up" }?.let {
                attribute.removeModifier(it)
            }
        }
    }

}