package tororo1066.dungeontower.skilltree.perks.convenience

import org.bukkit.Bukkit
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.scheduler.BukkitTask
import tororo1066.dungeontower.DungeonTower
import tororo1066.dungeontower.data.UserData
import tororo1066.dungeontower.skilltree.AbstractPerk
import tororo1066.dungeontower.skilltree.Skill
import tororo1066.dungeontower.skilltree.PerkLocation

class JustGuard: AbstractPerk("convenience", Skill.CONVENIENCE_LARGE_1, cost = 1,
    blockedBy = listOf(OnceHeal::class.java)
) {

    var cooltime = 60L
    var duration = 2L

    private var _task: BukkitTask? = null

    override fun getLocation(): PerkLocation {
        return PerkLocation(-7..-2, 20..24)
    }

    override fun getSkillName(): String {
        return "§6§lジャストガード"
    }

    override fun getSkillDescription(): List<String> {
        return listOf(
            "§7しゃがむと一瞬無敵になる",
            "§7クールタイム: 3秒"
        )
    }

    override fun registerPerk(p: Player, userData: UserData) {
        var blocking = false
        var cooldown = false
        sEvent.register(PlayerToggleSneakEvent::class.java) { e ->
            if (cooldown) return@register
            if (e.player.uniqueId != p.uniqueId) return@register
            if (e.isSneaking){
                blocking = true
                Bukkit.getScheduler().runTaskLater(DungeonTower.plugin, Runnable {
                    blocking = false
                }, duration)
                _task = Bukkit.getScheduler().runTaskLater(DungeonTower.plugin, Runnable {
                    cooldown = false
                }, cooltime)
            }
        }

        sEvent.register(EntityDamageByEntityEvent::class.java) { e ->
            if (e.entity.uniqueId != p.uniqueId) return@register
            if (!blocking) return@register
            e.damage = 0.0
            val player = e.entity as Player
            player.playSound(e.entity.location, Sound.BLOCK_ANVIL_PLACE, 0.5f, 2f)
            player.spawnParticle(Particle.TOTEM, e.entity.location, 10, 0.5, 0.5, 0.5, 0.1)
            _task?.cancel()
            cooldown = false
        }
    }
}