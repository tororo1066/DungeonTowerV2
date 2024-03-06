package tororo1066.dungeontower.skilltree.parks.convenience

import org.bukkit.entity.Player
import org.bukkit.event.EventPriority
import org.bukkit.event.entity.EntityDamageByEntityEvent
import tororo1066.dungeontower.data.UserData
import tororo1066.dungeontower.skilltree.AbstractPark
import tororo1066.dungeontower.skilltree.ParkLocation
import tororo1066.dungeontower.skilltree.Skill

class NoDamageTicks: AbstractPark("convenience", Skill.CONVENIENCE_MIDDLE_2, cost = 1,
    needParks = listOf(listOf(JustGuard::class.java), listOf(OnceHeal::class.java))
) {

    override fun getLocation(): ParkLocation {
        return ParkLocation(-2..2, 15..18)
    }

    override fun getSkillName(): String {
        return "§c§l抑制"
    }

    override fun getSkillDescription(): List<String> {
        return listOf(
            "§7無敵時間が0.05秒伸びる"
        )
    }

    override fun registerPark(p: Player, userData: UserData) {
        sEvent.register(EntityDamageByEntityEvent::class.java, EventPriority.LOWEST) { e ->
            if (e.isCancelled) return@register
            if (e.entity.uniqueId == p.uniqueId) {
                (e.entity as Player).noDamageTicks = 2
            }
        }
    }
}