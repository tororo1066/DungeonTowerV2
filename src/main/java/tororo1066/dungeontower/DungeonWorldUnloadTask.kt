package tororo1066.dungeontower

import org.bukkit.Bukkit
import org.bukkit.scheduler.BukkitRunnable
import java.util.UUID

class DungeonWorldUnloadTask: BukkitRunnable() {

    companion object {
        val shouldUnloadWorlds = mutableSetOf<UUID>()
    }

    init {
        runTaskTimerAsynchronously(DungeonTower.plugin, 20L * 60 * 5, 20L * 60 * 5) // Every 5 minutes
    }

    override fun run() {
        val iterator = shouldUnloadWorlds.iterator()
        while (iterator.hasNext()) {
            val uuid = iterator.next()
            val world = Bukkit.getWorld(uuid)
            if (world != null) {
                EmptyWorldGenerator.deleteWorld(world).thenAccept { pair ->
                    val (result, error) = pair
                    if (result) {
                        iterator.remove()
                    } else {
                        DungeonTower.plugin.logger.warning("Failed to unload and delete world ${world.name}: $error")
                    }
                }
            }
        }
    }
}