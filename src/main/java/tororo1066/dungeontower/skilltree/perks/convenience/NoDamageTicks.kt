package tororo1066.dungeontower.skilltree.perks.convenience

import org.bukkit.entity.Player
import org.bukkit.event.EventPriority
import org.bukkit.event.entity.EntityDamageByEntityEvent
import tororo1066.dungeontower.data.UserData
import tororo1066.dungeontower.skilltree.AbstractPerk
import tororo1066.dungeontower.skilltree.PerkLocation
import tororo1066.dungeontower.skilltree.Skill

class NoDamageTicks: AbstractPerk("convenience", Skill.CONVENIENCE_MIDDLE_2, cost = 2,
    needPerks = listOf(listOf(JustGuard::class.java), listOf(OnceHeal::class.java))
) {

    override fun getLocation(): Map<String, PerkLocation> {
        return mapOf(
            "2560x1440" to PerkLocation(-2..2, 15..18),
            "1920x1080" to PerkLocation(-2..2, 19..22)
        )
    }

    override fun getSkillName(): String {
        return "§c§l抑制"
    }

    override fun getSkillDescription(): List<String> {
        return listOf(
            "§7無敵時間が0.05秒伸びる"
        )
    }

    override fun registerPerk(p: Player, userData: UserData) {
        sEvent.register(EntityDamageByEntityEvent::class.java, EventPriority.LOWEST) { e ->
            if (e.isCancelled) return@register
            if (e.entity.uniqueId == p.uniqueId) {
                (e.entity as Player).noDamageTicks = 2
            }
        }
    }
}