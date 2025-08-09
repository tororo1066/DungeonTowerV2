package tororo1066.dungeontower.task

import org.bukkit.Bukkit
import tororo1066.dungeontower.EmptyWorldGenerator

class DungeonWorldUnloadTask: Runnable {

    companion object {
        val shouldUnloadWorlds = ArrayList<String>()
    }

    override fun run() {
        if (shouldUnloadWorlds.isEmpty()) return

        shouldUnloadWorlds.removeAll { worldName ->
            val world = Bukkit.getWorld(worldName) ?: return@removeAll true
            EmptyWorldGenerator.deleteWorld(world).success
        }
    }
}