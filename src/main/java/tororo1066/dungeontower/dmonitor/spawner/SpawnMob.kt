package tororo1066.dungeontower.dmonitor.spawner

import io.lumine.mythic.bukkit.BukkitAdapter
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.persistence.PersistentDataType
import tororo1066.displaymonitorapi.actions.ActionResult
import tororo1066.displaymonitorapi.actions.IAbstractAction
import tororo1066.displaymonitorapi.actions.IActionContext
import tororo1066.displaymonitorapi.configuration.IAdvancedConfigurationSection
import tororo1066.dungeontower.DungeonTower

class SpawnMob: IAbstractAction {

    var mobName = ""
    var level = .0
    var effect = true

    override fun allowedAutoStop(): Boolean {
        return true
    }

    override fun run(context: IActionContext): ActionResult {
        val parameters = context.publicContext.parameters
        val location = context.location ?: return ActionResult.locationRequired()
        val uuid = parameters["spawner.uuid"] as? String
            ?: return ActionResult.noParameters("Spawner UUID not found")
        if (mobName.isEmpty()) {
            return ActionResult.noParameters("Mob name is empty")
        }
        val mythicMob = DungeonTower.mythic.getMythicMob(mobName)
            ?: return ActionResult.noParameters("Mythic mob not found: $mobName")
        Bukkit.getScheduler().runTask(DungeonTower.plugin, Runnable {
            val entity = mythicMob.spawn(BukkitAdapter.adapt(location), level)

            entity.entity.dataContainer.set(
                NamespacedKey(DungeonTower.plugin, DungeonTower.DUNGEON_MOB),
                PersistentDataType.STRING, uuid
            )

            if (effect) {
                location.world.playSound(location, Sound.BLOCK_END_PORTAL_FRAME_FILL, 1f, 1f)
                location.world.spawnParticle(Particle.FLAME, location, 15)
            }
        })

        return ActionResult.success()
    }

    override fun prepare(configuration: IAdvancedConfigurationSection) {
        mobName = configuration.getString("mob", "")!!
        level = configuration.getDouble("level", 0.0)
        effect = configuration.getBoolean("effect", true)
    }
}