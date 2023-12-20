package tororo1066.dungeontower.skilltree.parks.convenience

import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.player.PlayerToggleSneakEvent
import tororo1066.dungeontower.DungeonTower
import tororo1066.dungeontower.skilltree.AbstractPark
import tororo1066.dungeontower.skilltree.LargeSkill
import tororo1066.dungeontower.skilltree.ParkLocation

class JustGuard: AbstractPark("convenience", LargeSkill.CONVENIENCE_1.char) {

    override fun getLocation(): ParkLocation {
        return ParkLocation(-7..-2, 20..24)
    }

    override fun getSkillName(): String {
        return "§6§lJust Guard"
    }

    override fun getSkillDescription(): List<String> {
        return listOf(
            "§f攻撃を受けた時にしゃがむと",
            "§fダメージを無効化できる",
            "§fクールタイム: 3秒"
        )
    }

    override fun registerSkill(p: Player) {
        var blocking = false
        var cooldown = false
        sEvent.register(PlayerToggleSneakEvent::class.java) { e ->
            if (cooldown) return@register
            if (e.player.uniqueId != p.uniqueId) return@register
            if (e.isSneaking){
                blocking = true
                Bukkit.getScheduler().runTaskLater(DungeonTower.plugin, Runnable {
                    blocking = false
                }, 2)
                Bukkit.getScheduler().runTaskLater(DungeonTower.plugin, Runnable {
                    cooldown = false
                }, 60)
            }
        }

        sEvent.register(EntityDamageByEntityEvent::class.java) { e ->
            if (e.entity.uniqueId != p.uniqueId) return@register
            if (!blocking) return@register
            e.damage = 0.0
            (e.entity as Player).playSound(e.entity.location, Sound.BLOCK_ANVIL_PLACE, 0.5f, 2f)
        }
    }
}