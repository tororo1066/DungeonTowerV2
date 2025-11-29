package tororo1066.dungeontower.dmonitor.floor

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.ItemDisplay
import org.bukkit.inventory.ItemStack
import org.bukkit.util.Transformation
import org.bukkit.util.Vector
import org.joml.Quaternionf
import org.joml.Vector3f
import tororo1066.displaymonitorapi.actions.ActionResult
import tororo1066.displaymonitorapi.actions.IAbstractAction
import tororo1066.displaymonitorapi.actions.IActionContext
import tororo1066.displaymonitorapi.configuration.IAdvancedConfigurationSection
import tororo1066.dungeontower.DungeonTower
import java.util.UUID

class FilterNotConflictFloor: IAbstractAction {

    var allowConflictDistance: Vector = Vector(0.0, 0.0, 0.0)
    var floors: List<String> = emptyList()
    var baseRotation: Double = 0.0
    var listName: String? = null

    override fun allowedAutoStop(): Boolean {
        return true
    }

    @Suppress("UNCHECKED_CAST")
    override fun run(context: IActionContext): ActionResult {

        fun setAndReturn(reason: String): ActionResult {
            val list = floors.mapNotNull { floor ->
                val split = floor.split(",")
                if (split.size < 2) return@mapNotNull null
                val floorName = split[0]
                val rotation = split.getOrNull(1)?.toDoubleOrNull() ?: 0.0
                DungeonTower.floorData[floorName]?.let {
                    hashMapOf("floorName" to floorName, "rotation" to rotation)
                }
            }
            context.publicContext.parameters[listName ?: "noConflictFloors"] = list
            return ActionResult.success(reason)
        }

        val location = context.location ?: return ActionResult.locationRequired()
        val parameters = context.publicContext.parameters
        val uuid = UUID.fromString(parameters["uuid"] as? String ?: return setAndReturn("UUID not found in parameters."))
        val generatedFloors = CallFloor.floors[uuid] ?: return setAndReturn("No generated floors found for UUID: $uuid")

        val noConflictFloors = ArrayList<HashMap<String, Any>>()
        val calculateFloors = floors.mapNotNull { floor ->
            val split = floor.split(",")
            if (split.size < 2) return@mapNotNull null
            val floorName = split[0]
            val rotation = ((split.getOrNull(1)?.toDoubleOrNull() ?: 0.0) + baseRotation) % 360.0
            val floorData = DungeonTower.floorData[floorName]?.newInstance() ?: return@mapNotNull null
            val (dungeonStartLoc, dungeonEndLoc) = floorData.calculateLocation(location, rotation)
            //debug
//            Bukkit.getScheduler().runTask(DungeonTower.plugin, Runnable {
//                val center = Location(
//                    location.world,
//                    (dungeonStartLoc.blockX + dungeonEndLoc.blockX) / 2.0,
//                    (dungeonStartLoc.blockY + dungeonEndLoc.blockY) / 2.0,
//                    (dungeonStartLoc.blockZ + dungeonEndLoc.blockZ) / 2.0
//                )
//                location.world.spawn(center, ItemDisplay::class.java) { itemDisplay ->
//                    itemDisplay.itemStack = ItemStack(Material.EMERALD_BLOCK)
//                    itemDisplay.transformation = Transformation(
//                        Vector3f(),
//                        Quaternionf(),
//                        Vector3f(dungeonEndLoc.blockX - dungeonStartLoc.blockX.toFloat(), dungeonEndLoc.blockY - dungeonStartLoc.blockY.toFloat(), dungeonEndLoc.blockZ - dungeonStartLoc.blockZ.toFloat() ),
//                        Quaternionf()
//                    )
//                }
//            })
            //debug end
            Triple(Pair(floorName, rotation), dungeonStartLoc, dungeonEndLoc)
        }

        for (calculateFloor in calculateFloors) {
            val (floorData, dungeonStartLoc, dungeonEndLoc) = calculateFloor
            val (floorName, rotation) = floorData
            var conflicted = false

            root@ for (generatedFloor in generatedFloors) {
                val (startX, startY, startZ) = generatedFloor["startLoc"] as? List<Int> ?: continue
                val (endX, endY, endZ) = generatedFloor["endLoc"] as? List<Int> ?: continue

                for (x in dungeonStartLoc.blockX + allowConflictDistance.blockX..dungeonEndLoc.blockX - allowConflictDistance.blockX) {
                    for (y in dungeonStartLoc.blockY + allowConflictDistance.blockY..dungeonEndLoc.blockY - allowConflictDistance.blockY) {
                        for (z in dungeonStartLoc.blockZ + allowConflictDistance.blockZ..dungeonEndLoc.blockZ - allowConflictDistance.blockZ) {
                            if (x in startX..endX && y in startY..endY && z in startZ..endZ) {
                                conflicted = true
                                break@root
                            }
                        }
                    }
                }
            }

            if (!conflicted) {
                noConflictFloors.add(hashMapOf("floorName" to floorName, "rotation" to rotation))
            }
        }

        parameters[listName ?: "noConflictFloors"] = noConflictFloors

        return ActionResult.success()
    }

    override fun prepare(configuration: IAdvancedConfigurationSection) {
        allowConflictDistance = configuration.getBukkitVector("allowConflictDistance", Vector(0.0, 0.0, 0.0))
        floors = configuration.getStringList("floors")
        baseRotation = configuration.getDouble("baseRotation", 0.0)
        listName = configuration.getString("listName")
    }
}