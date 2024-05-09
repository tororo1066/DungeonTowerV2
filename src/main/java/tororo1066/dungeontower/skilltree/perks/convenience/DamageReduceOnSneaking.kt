package tororo1066.dungeontower.skilltree.perks.convenience

import org.bukkit.entity.Player
import org.bukkit.event.EventPriority
import org.bukkit.event.entity.EntityDamageByEntityEvent
import tororo1066.dungeontower.data.UserData
import tororo1066.dungeontower.skilltree.AbstractPerk
import tororo1066.dungeontower.skilltree.PerkLocation
import tororo1066.dungeontower.skilltree.Skill

class DamageReduceOnSneaking: AbstractPerk("convenience", Skill.CONVENIENCE_SMALL_CENTER, cost = 2) {

    override fun getLocation(): Map<String, PerkLocation> {
        return mapOf(
            "2560x1440" to PerkLocation(-7..-4, 11..13),
            "1920x1080" to PerkLocation(-7..-4, 15..18)
        )
    }

    override fun getSkillName(): String {
        return "§9耐久"
    }

    override fun getSkillDescription(): List<String> {
        return listOf(
            "スニーク中に受けるダメージが10%減少する"
        )
    }

    override fun registerPerk(p: Player, userData: UserData) {
        sEvent.register(EntityDamageByEntityEvent::class.java, EventPriority.LOWEST) { e ->
            if (e.entity.uniqueId != p.uniqueId) return@register
            if (!p.isSneaking) return@register
            e.damage *= 0.9
        }
    }
}