package tororo1066.dungeontower

import net.kyori.adventure.text.Component
import net.kyori.adventure.util.TriState
import org.bukkit.*
import org.bukkit.generator.ChunkGenerator
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger

class EmptyWorldGenerator {

    val id = AtomicInteger(0)

    fun createEmptyWorld(name: String): World {
        val worldName = "${DungeonTower.plugin.name.lowercase()}_${name}_${id.getAndIncrement()}"
        if (Bukkit.getWorld(worldName) != null) {
            throw IllegalArgumentException("World with name $worldName already exists.")
        }

        val creator = WorldCreator(worldName)
            .keepSpawnLoaded(TriState.TRUE)
            .generateStructures(false)
            .generator(EmptyWorldGenerator)
            .environment(World.Environment.NORMAL)
            .type(WorldType.FLAT)

        val world = Bukkit.createWorld(creator)
            ?: throw IllegalStateException("Failed to create world with name $worldName.")

        world.isAutoSave = false

        DungeonTower.worlds.add(world.uid)

        return world
    }

    companion object {
        fun deleteWorld(world: World, spawnLocation: Location? = null): CompletableFuture<Result> {
            if (Bukkit.getWorld(world.name) == null) {
                return CompletableFuture.completedFuture(Result(false, "World ${world.name} does not exist."))
            }

            world.players.forEach { player ->
                if (spawnLocation != null) {
                    player.teleport(spawnLocation)
                } else {
                    player.kick(Component.text("The world ${world.name} has been deleted."))
                }
            }

//        MythicBukkit.inst().mobManager.getActiveMobs { it.entity.world.name == world.name }.forEach { mob ->
//            val opt = MythicBukkit.inst().mobManager.getActiveMob(mob.uniqueId)
//            if (opt.isPresent) {
//                val activeMob = opt.get()
//                activeMob.setDead()
//                activeMob.setDespawned()
//                activeMob.setUnloaded()
//            }
//            mob.remove()
//        }

            world.entities.forEach { entity ->
                entity.remove()
            }

            world.loadedChunks.forEach { chunk ->
                chunk.unload(false)
            }

//            if (!Bukkit.unloadWorld(world, false)) {
//                return Result(false, "Failed to unload world ${world.name}.")
//            }
//
//            Bukkit.getScheduler().runTaskLaterAsynchronously(DungeonTower.plugin, Runnable {
//                val worldFile = world.worldFolder
//                if (worldFile.exists()) {
//                    worldFile.deleteRecursively()
//                }
//            }, 100L)
//
//            return Result(true, "World ${world.name} has been deleted successfully.")

            return CompletableFuture.supplyAsync {
                Thread.sleep(10000L) // Wait for the unload to complete
                var lock = true
                var success = false
                Bukkit.getScheduler().runTask(DungeonTower.plugin, Runnable {
                    success = Bukkit.unloadWorld(world, false)
                    lock = false
                })

                while (lock) {
                    Thread.sleep(100L)
                }

                if (!success) {
                    return@supplyAsync Result(false, "Failed to unload world ${world.name}.")
                }

                val worldFile = world.worldFolder
                if (worldFile.exists()) {
                    worldFile.deleteRecursively()
                }

                Result(true, "World ${world.name} has been deleted successfully.")
            }
        }
    }

    class Result(val success: Boolean, val message: String) {
        operator fun component1() = success
        operator fun component2() = message
    }

    private object EmptyWorldGenerator: ChunkGenerator()
}