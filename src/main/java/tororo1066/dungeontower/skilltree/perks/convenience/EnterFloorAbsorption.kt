package tororo1066.dungeontower.skilltree.perks.convenience

import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import tororo1066.dungeontower.data.UserData
import tororo1066.dungeontower.skilltree.AbstractPerk
import tororo1066.dungeontower.skilltree.ActionType
import tororo1066.dungeontower.skilltree.PerkLocation
import tororo1066.dungeontower.skilltree.Skill

class EnterFloorAbsorption: AbstractPerk("convenience", Skill.CONVENIENCE_SMALL_CENTER, cost = 2) {

    override fun getLocation(): Map<String, PerkLocation> {
        return mapOf(
            "2560x1440" to PerkLocation(-2..1, 11..13),
            "1920x1080" to PerkLocation(-2..1, 15..18)
        )
    }

    override fun getSkillName(): String {
        return "§6吸収"
    }

    override fun getSkillDescription(): List<String> {
        return listOf(
            "§7フロアに入るたびに衝撃吸収が15秒間付く"
        )
    }

    override fun onAction(p: Player, action: ActionType, userData: UserData) {
        if (action == ActionType.ENTER_FLOOR) {
            p.addPotionEffect(PotionEffect(PotionEffectType.ABSORPTION, 300, 1))
        }
    }
}