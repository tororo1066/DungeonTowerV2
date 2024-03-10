package tororo1066.dungeontower.data

import com.sk89q.worldedit.WorldEdit
import com.sk89q.worldedit.bukkit.BukkitWorld
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard
import com.sk89q.worldedit.function.operation.ForwardExtentCopy
import com.sk89q.worldedit.function.operation.Operations
import com.sk89q.worldedit.math.BlockVector3
import com.sk89q.worldedit.math.transform.AffineTransform
import com.sk89q.worldedit.regions.CuboidRegion
import com.sk89q.worldedit.session.ClipboardHolder
import com.sk89q.worldedit.world.block.BlockTypes
import io.lumine.mythic.bukkit.BukkitAPIHelper
import io.lumine.mythic.bukkit.BukkitAdapter
import net.kyori.adventure.text.Component
import org.bukkit.*
import org.bukkit.block.Chest
import org.bukkit.block.Sign
import org.bukkit.block.data.Directional
import org.bukkit.block.data.type.EndPortalFrame
import org.bukkit.block.data.type.Stairs
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.loot.LootContext
import org.bukkit.persistence.PersistentDataType
import org.bukkit.scheduler.BukkitRunnable
import tororo1066.dungeontower.DungeonTower
import tororo1066.dungeontower.save.SaveDataDB
import tororo1066.dungeontower.script.FloorScript
import tororo1066.tororopluginapi.sEvent.SEvent
import java.io.File
import java.util.Random
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.random.nextInt

class FloorData: Cloneable {

    enum class ClearTaskEnum {
        KILL_SPAWNER_MOBS,
        ENTER_COMMAND
    }

    enum class ClearCondition(val displayName: String) {
        CLEAR_PARENT("親フロアをクリア"),
        CLEAR_PARENT_AND_SELF("親フロアと自身をクリア"),
        CLEAR_PARALLEL("並列フロアをクリア"),
        CLEAR_PARALLEL_AND_SELF("並列フロアと自身をクリア"),
        CLEAR_ALL("全てクリア")
    }

    class ClearTask(
        val type: ClearTaskEnum, var need: Int = 0,
        var count: Int = 0, var clear: Boolean = false,
        var scoreboardName: String = "", var clearScoreboardName: String = "",
        var condition: ClearCondition = ClearCondition.CLEAR_ALL
    ): Cloneable {

        public override fun clone(): ClearTask {
            return super.clone() as ClearTask
        }
    }

    var internalName = ""

    lateinit var startLoc: Location
    lateinit var endLoc: Location

    var initialTime = 300
    var time = 300

    var dungeonStartLoc: Location? = null
    var dungeonEndLoc: Location? = null

    val preventFloorStairs = ArrayList<Location>()
    val nextFloorStairs = ArrayList<Location>()//使わないかもしれない
    val spawners = ArrayList<SpawnerRunnable>()

    val joinCommands = ArrayList<String>()

    val clearTask = ArrayList<ClearTask>()
    val spawnerClearTasks = HashMap<UUID,Boolean>()

    val parallelFloors = HashMap<String, FloorData>()
    var parallelFloor = false
    var rotate = 0.0
    var parallelFloorOrigin: Location? = null
    lateinit var parent: FloorData

    val subFloors = ArrayList<Pair<Int, String>>()

    val waves = HashMap<Int,ArrayList<Pair<Int, WaveData>>>()

    lateinit var yml: YamlConfiguration

    var generated = false

    var shouldUseSaveData = false
    var loadedSaveData = false

    fun checkClear(): Boolean {
        ClearTaskEnum.values().forEach {
            if (!checkClear(it))return false
        }
        return true
    }

    fun checkClear(task: ClearTaskEnum): Boolean {
        val find = clearTask.find { it.type == task }?:return true
        when(find.condition){
            ClearCondition.CLEAR_ALL -> {
                return find.clear && parallelFloors.none { !it.value.checkClear(task) } && if (parallelFloor) parent.checkClear(task) else true
            }
            ClearCondition.CLEAR_PARENT -> {
                return !parallelFloor || parent.checkClear(task)
            }
            ClearCondition.CLEAR_PARENT_AND_SELF -> {
                return find.clear && (!parallelFloor || parent.checkClear(task))
            }
            ClearCondition.CLEAR_PARALLEL -> {
                return parallelFloors.none { !it.value.checkClear(task) }
            }
            ClearCondition.CLEAR_PARALLEL_AND_SELF -> {
                return find.clear && parallelFloors.none { !it.value.checkClear(task) }
            }
        }
    }

    fun randomSubFloor(): FloorData {
        val random = kotlin.random.Random.nextInt(1..1000000)
        var preventRandom = 0
        for (floor in subFloors){
            if (preventRandom < random && floor.first + preventRandom > random){
                return DungeonTower.floorData[floor.second]!!.newInstance()
            }
            preventRandom = floor.first
        }
        throw NullPointerException("Couldn't find floor. Maybe sum percentage is not 1000000.")
    }

    inner class SpawnerRunnable(val data: SpawnerData, val location: Location, val uuid: UUID) : BukkitRunnable() {

        private val sEvent = SEvent(DungeonTower.plugin)

        init {
            sEvent.register(EntityDeathEvent::class.java) { e ->
                if (e.entity.persistentDataContainer[NamespacedKey(DungeonTower.plugin,"dmob"), PersistentDataType.STRING] != uuid.toString())return@register
                data.kill++
                if (data.kill <= data.navigateKill){
                    val ksFind = clearTask.find { it.type == ClearTaskEnum.KILL_SPAWNER_MOBS }
                    if (ksFind != null) ksFind.count += 1
                }
                if (data.kill >= data.navigateKill){
                    spawnerClearTasks[uuid] = true
                    if (spawnerClearTasks.values.none { !it }){
                        clearTask.filter { it.type == ClearTaskEnum.KILL_SPAWNER_MOBS }.forEach {
                            it.clear = true
                        }
                    }
                }
            }
        }

        var coolTime = data.coolTime
        override fun run() {
            if (data.count >= data.max){
                cancel()
                return
            }
            coolTime--
            if (coolTime > 0)return
            if (location.getNearbyPlayers(data.activateRange.toDouble())
                    .none { it.gameMode == GameMode.SURVIVAL || it.gameMode == GameMode.ADVENTURE })return
            val spawnLoc = location.clone().add(
                (-data.radius..data.radius).random().toDouble(),
                data.yOffSet,
                (-data.radius..data.radius).random().toDouble()
            )
            val mob = data.randomMob().spawn(BukkitAdapter.adapt(spawnLoc),data.level)
            location.world.playSound(location, Sound.BLOCK_END_PORTAL_FRAME_FILL, 1f, 1f)
            location.world.spawnParticle(Particle.FLAME, location, 15)
            mob?.entity?.dataContainer?.set(
                NamespacedKey(DungeonTower.plugin,"dmob"),
                PersistentDataType.STRING,
                uuid.toString())
            data.count++
            if (data.count >= data.max){
                val portal = location.block.blockData as EndPortalFrame
                portal.setEye(false)
                location.block.blockData = portal
            }
        }

        override fun cancel() {
            Bukkit.getScheduler().runTask(DungeonTower.plugin, Runnable {
                DungeonTower.dungeonWorld.entities.filter { it.persistentDataContainer.get(
                    NamespacedKey(DungeonTower.plugin, "dmob"),
                    PersistentDataType.STRING) == uuid.toString() }.forEach {
                    it.remove()
                }
            })
            super.cancel()
        }
    }

    fun getAllTask(): ArrayList<ClearTask> {
        val list = ArrayList<ClearTask>()
        list.addAll(clearTask)
        parallelFloors.values.forEach {
            list.addAll(it.getAllTask())
        }
        return list
    }

    private fun Location.toBlockVector3(): BlockVector3 {
        return BlockVector3.at(blockX,blockY,blockZ)
    }

    data class Points(val lowX: Int, val lowY: Int, val lowZ: Int, val highX: Int, val highY: Int, val highZ: Int)

    private fun getPoints(): Points {
        val lowX = min(startLoc.blockX, endLoc.blockX)
        val lowY = min(startLoc.blockY, endLoc.blockY)
        val lowZ = min(startLoc.blockZ, endLoc.blockZ)
        val highX = if (lowX == startLoc.blockX) endLoc.blockX else startLoc.blockX
        val highY = if (lowY == startLoc.blockY) endLoc.blockY else startLoc.blockY
        val highZ = if (lowZ == startLoc.blockZ) endLoc.blockZ else startLoc.blockZ
        return Points(lowX, lowY, lowZ, highX, highY, highZ)
    }

    private fun calculateLocation(location: Location, direction: Double): Pair<Location, Location> {
        val (lowX, lowY, lowZ, highX, highY, highZ) = getPoints()

        val modifiedDirection = Math.toRadians(if (direction < 0) direction + 360 else direction)

        val originLocation = (parallelFloorOrigin?:startLoc).clone()

        val originX = originLocation.blockX
        val originY = originLocation.blockY
        val originZ = originLocation.blockZ

        val dungeonStartLoc = location.clone().add(
            (lowX - originX) * cos(modifiedDirection) + (lowZ - originZ) * sin(modifiedDirection),
            (lowY - originY).toDouble(),
            (lowZ - originZ) * cos(modifiedDirection) - (lowX - originX) * sin(modifiedDirection)
        )
        val dungeonEndLoc = location.clone().add(
            (highX - originX) * cos(modifiedDirection) + (highZ - originZ) * sin(modifiedDirection),
            (highY - originY).toDouble(),
            (highZ - originZ) * cos(modifiedDirection) - (highX - originX) * sin(modifiedDirection)
        )

        if (dungeonStartLoc.blockX > dungeonEndLoc.blockX){
            val temp = dungeonStartLoc.blockX
            dungeonStartLoc.add((dungeonStartLoc.blockX - dungeonEndLoc.blockX).toDouble(),0.0,0.0)
            dungeonEndLoc.add((temp - dungeonEndLoc.blockX).toDouble(),0.0,0.0)
        }

        if (dungeonStartLoc.blockZ > dungeonEndLoc.blockZ){
            val temp = dungeonStartLoc.blockZ
            dungeonStartLoc.add(0.0,0.0,(dungeonStartLoc.blockZ - dungeonEndLoc.blockZ).toDouble())
            dungeonEndLoc.add(0.0,0.0,(temp - dungeonEndLoc.blockZ).toDouble())
        }

        return Pair(dungeonStartLoc, dungeonEndLoc)
    }

    private fun noLoadGenerateFloor() {
        val location = Location(
            DungeonTower.dungeonWorld,
            DungeonTower.nowX.toDouble(),
            DungeonTower.y.toDouble(),
            0.0
        )
        DungeonTower.nowX += calculateDistance(location, 0.0) + 1 + DungeonTower.dungeonXSpace
        val newLocation = Location(
            DungeonTower.dungeonWorld,
            DungeonTower.nowX.toDouble(),
            DungeonTower.y.toDouble(),
            0.0
        )
        noLoadGenerateFloor(newLocation, 0.0)
    }

    private fun noLoadGenerateFloor(location: Location, direction: Double) {
        preventFloorStairs.clear()
        nextFloorStairs.clear()
        spawnerClearTasks.clear()
        spawners.clear()
        time = initialTime
        val (lowX, lowY, lowZ, highX, highY, highZ) = getPoints()

        val originLocation = (parallelFloorOrigin ?: startLoc).clone()

        val (dungeonStartLoc, dungeonEndLoc) = calculateLocation(location, direction)

        val buildLocation = location.clone()

        this.dungeonStartLoc = dungeonStartLoc
        this.dungeonEndLoc = dungeonEndLoc

        val region = CuboidRegion(
            BukkitWorld(DungeonTower.floorWorld),
            BlockVector3.at(lowX, lowY, lowZ),
            BlockVector3.at(highX, highY, highZ)
        )
        val clipboard = BlockArrayClipboard(region)
        clipboard.origin = originLocation.toBlockVector3()
        val forwardExtentCopy =
            ForwardExtentCopy(BukkitWorld(DungeonTower.floorWorld), region, clipboard, region.minimumPoint)
        Operations.complete(forwardExtentCopy)


        WorldEdit.getInstance().newEditSession(BukkitWorld(DungeonTower.dungeonWorld)).use {
            val operation = ClipboardHolder(clipboard)
                .apply {
                    transform = transform.combine(AffineTransform()
                        .rotateY(direction))
                }
                .createPaste(it)
                .to(buildLocation.toBlockVector3())
                .ignoreAirBlocks(true)
                .build()
            Operations.complete(operation)
        }

        val xSign = sign(dungeonEndLoc.blockX.toDouble() - dungeonStartLoc.blockX.toDouble())
        val ySign = sign(dungeonEndLoc.blockY.toDouble() - dungeonStartLoc.blockY.toDouble())
        val zSign = sign(dungeonEndLoc.blockZ.toDouble() - dungeonStartLoc.blockZ.toDouble())


        for ((indexX, x) in (lowX..highX).withIndex()) {
            for ((indexY, y) in (lowY..highY).withIndex()) {
                for ((indexZ, z) in (lowZ..highZ).withIndex()) {
                    val block = DungeonTower.floorWorld.getBlockAt(x, y, z)

                    val placeLoc =
                        dungeonStartLoc.clone().add(indexX.toDouble() * xSign, indexY.toDouble() * ySign, indexZ.toDouble() * zSign)

                    when (block.type) {

                        Material.OAK_SIGN -> {
                            val data = DungeonTower.floorWorld.getBlockState(x, y, z) as Sign
                            when (data.getLine(0)) {
                                "loot" -> {
                                    val loot = (DungeonTower.lootData[data.getLine(1)] ?: continue).clone()
                                    placeLoc.block.type = Material.CHEST
                                    val chest = placeLoc.block.state as Chest
                                    chest.customName(Component.text(loot.displayName))
                                    chest.setLock("§c§l${Random().nextDouble(10000.0)}")
                                    chest.update()
                                    loot.fillInventory(
                                        chest.inventory, Random(),
                                        LootContext.Builder(chest.location).build()
                                    )
                                    val blockData = chest.blockData as Directional
                                    blockData.facing = (data.blockData as org.bukkit.block.data.type.Sign).rotation
                                    chest.blockData = blockData
                                }
                                "spawner" -> {
                                    val spawner =
                                        (DungeonTower.spawnerData[data.getLine(1)] ?: continue).clone()
                                    val locSave = placeLoc.clone()
                                    locSave.block.type = Material.END_PORTAL_FRAME
                                    val portal = locSave.block.blockData as EndPortalFrame
                                    portal.setEye(true)

                                    locSave.block.blockData = portal
                                    val randUUID = UUID.randomUUID()
                                    spawnerClearTasks[randUUID] = spawner.navigateKill <= 0
                                    val find = clearTask.find { it.type == ClearTaskEnum.KILL_SPAWNER_MOBS }
                                    if (find != null) find.need += spawner.navigateKill
                                    spawners.add(
                                        SpawnerRunnable(spawner, locSave, randUUID)
                                    )
                                }
                                "floor" -> {
                                    val script = data.getLine(3).ifBlank { null }
                                    val label = script?.let { let -> FloorScript.getLabelName(let) } ?: "${x},${y},${z}"
                                    if (parallelFloors.containsKey(label)) {
                                        val floor = parallelFloors[label]!!
                                        floor.parent = this
                                        floor.parallelFloor = true
                                        floor.noLoadGenerateFloor(placeLoc, floor.rotate)
                                    }
                                }
                                else -> {}
                            }
                        }

                        Material.WARPED_STAIRS -> {
                            nextFloorStairs.add(placeLoc.clone().add(0.0, 1.0, 0.0))
                        }

                        Material.CRIMSON_STAIRS -> {
                            preventFloorStairs.add(
                                placeLoc.clone().add(0.0, 1.0, 0.0).setDirection((block.blockData as Stairs).facing.direction)
                            )
                        }

                        else -> {}
                    }
                }
            }
        }
        generated = true
    }

    data class ParallelFloorData(
        val floorName: String,
        val label: String,
        val script: String?,
        val rotate: Double,
        val location: Location,
        val generate: Boolean
    )

    private fun generateFloor(towerData: TowerData, location: Location, direction: Double) {

        preventFloorStairs.clear()
        nextFloorStairs.clear()
        spawnerClearTasks.clear()
        spawners.clear()
        parallelFloors.clear()
        time = initialTime

        val loadedParallelFloors = ArrayList<ParallelFloorData>()

        val (lowX, lowY, lowZ, highX, highY, highZ) = getPoints()

        val originLocation = (parallelFloorOrigin ?: startLoc).clone()

        val (dungeonStartLoc, dungeonEndLoc) = calculateLocation(location, direction)

        val buildLocation = location.clone()

        this.dungeonStartLoc = dungeonStartLoc
        this.dungeonEndLoc = dungeonEndLoc

        val region = CuboidRegion(
            BukkitWorld(DungeonTower.floorWorld),
            BlockVector3.at(lowX, lowY, lowZ),
            BlockVector3.at(highX, highY, highZ)
        )
        val clipboard = BlockArrayClipboard(region)
        clipboard.origin = originLocation.toBlockVector3()
        val forwardExtentCopy =
            ForwardExtentCopy(BukkitWorld(DungeonTower.floorWorld), region, clipboard, region.minimumPoint)
        Operations.complete(forwardExtentCopy)


        WorldEdit.getInstance().newEditSession(BukkitWorld(DungeonTower.dungeonWorld)).use {
            val operation = ClipboardHolder(clipboard)
                .apply {
                    transform = transform.combine(AffineTransform()
                        .rotateY(direction))
                }
                .createPaste(it)
                .to(buildLocation.toBlockVector3())
                .ignoreAirBlocks(true)
                .build()
            Operations.complete(operation)
        }

        val xSign = sign(dungeonEndLoc.blockX.toDouble() - dungeonStartLoc.blockX.toDouble())
        val ySign = sign(dungeonEndLoc.blockY.toDouble() - dungeonStartLoc.blockY.toDouble())
        val zSign = sign(dungeonEndLoc.blockZ.toDouble() - dungeonStartLoc.blockZ.toDouble())


        for ((indexX, x) in (lowX..highX).withIndex()) {
            for ((indexY, y) in (lowY..highY).withIndex()) {
                for ((indexZ, z) in (lowZ..highZ).withIndex()) {
                    val block = DungeonTower.floorWorld.getBlockAt(x, y, z)

                    val placeLoc =
                        dungeonStartLoc.clone().add(indexX.toDouble() * xSign, indexY.toDouble() * ySign, indexZ.toDouble() * zSign)

                    when (block.type) {

                        Material.OAK_SIGN -> {
                            val data = DungeonTower.floorWorld.getBlockState(x, y, z) as Sign
                            when (data.getLine(0)) {
                                "loot" -> {
                                    val loot = (DungeonTower.lootData[data.getLine(1)] ?: continue).clone()
                                    placeLoc.block.type = Material.CHEST
                                    val chest = placeLoc.block.state as Chest
                                    chest.customName(Component.text(loot.displayName))
                                    chest.setLock("§c§l${Random().nextDouble(10000.0)}")
                                    chest.update()
                                    loot.fillInventory(
                                        chest.inventory, Random(),
                                        LootContext.Builder(chest.location).build()
                                    )
                                    val blockData = chest.blockData as Directional
                                    blockData.facing = (data.blockData as org.bukkit.block.data.type.Sign).rotation
                                    chest.blockData = blockData
                                }
                                "spawner" -> {
                                    val spawner =
                                        (DungeonTower.spawnerData[data.getLine(1)] ?: continue).clone()
                                    val locSave = placeLoc.clone()
                                    locSave.block.type = Material.END_PORTAL_FRAME
                                    val portal = locSave.block.blockData as EndPortalFrame
                                    portal.setEye(true)

                                    locSave.block.blockData = portal
                                    val randUUID = UUID.randomUUID()
                                    spawnerClearTasks[randUUID] = spawner.navigateKill <= 0
                                    val find = clearTask.find { it.type == ClearTaskEnum.KILL_SPAWNER_MOBS }
                                    if (find != null) find.need += spawner.navigateKill
                                    spawners.add(
                                        SpawnerRunnable(spawner, locSave, randUUID)
                                    )
                                }
                                "floor" -> {
                                    val floor = data.getLine(1)
                                    val split = data.getLine(2).split(",")
                                    val chance = split.getOrNull(0)?.toIntOrNull() ?: 1000000
                                    val rotate = split.getOrNull(1)?.toDoubleOrNull() ?: 0.0
                                    val script = data.getLine(3).ifBlank { null }
                                    val label = script?.let { let -> FloorScript.getLabelName(let) } ?: "${x},${y},${z}"
                                    loadedParallelFloors.add(
                                        ParallelFloorData(
                                            floor,
                                            label,
                                            script,
                                            rotate,
                                            placeLoc,
                                            Random().nextInt(1000000) < chance
                                        )
                                    )

                                    placeLoc.block.type = Material.AIR
                                }
                                else -> {}
                            }
                        }

                        Material.WARPED_STAIRS -> {
                            nextFloorStairs.add(placeLoc.clone().add(0.0, 1.0, 0.0))
                        }

                        Material.CRIMSON_STAIRS -> {
                            preventFloorStairs.add(
                                placeLoc.clone().add(0.0, 1.0, 0.0).setDirection((block.blockData as Stairs).facing.direction)
                            )
                        }

                        else -> {}
                    }
                }
            }
        }

        loadedParallelFloors.sortedByDescending { it.script?.let { let -> FloorScript.getLoadPriority(let) } }.forEach { data ->
            val (floorName, rotate) = FloorScript.generateSubFloorScript(
                towerData.internalName,
                data.floorName,
                data.script,
                data.rotate,
                loadedParallelFloors.filter { it.generate }.map { it.label },
                loadedParallelFloors.filter { !it.generate }.map { it.label },
                data.generate
            )

            if (floorName.isBlank()) return@forEach
            val floorData = DungeonTower.floorData[floorName]?.newInstance() ?: return@forEach
            floorData.parallelFloor = true
            floorData.parent = this
            floorData.rotate = rotate
            parallelFloors[data.label] = floorData
            floorData.generateFloor(towerData, data.location, rotate)
        }

        generated = true
    }

    fun calculateDistance(location: Location, direction: Double): Int {
        val (lowX, lowY, lowZ, highX, highY, highZ) = getPoints()

        val (dungeonStartLoc, dungeonEndLoc) = calculateLocation(location, direction)

        val xSign = sign(dungeonEndLoc.blockX.toDouble() - dungeonStartLoc.blockX.toDouble())
        val ySign = sign(dungeonEndLoc.blockY.toDouble() - dungeonStartLoc.blockY.toDouble())
        val zSign = sign(dungeonEndLoc.blockZ.toDouble() - dungeonStartLoc.blockZ.toDouble())

        var distance = dungeonEndLoc.blockX - dungeonStartLoc.blockX

        for ((indexX, x) in (lowX..highX).withIndex()) {
            for ((indexY, y) in (lowY..highY).withIndex()) {
                for ((indexZ, z) in (lowZ..highZ).withIndex()) {
                    val block = DungeonTower.floorWorld.getBlockAt(x, y, z)

                    val placeLoc =
                        dungeonStartLoc.clone()
                            .add(indexX.toDouble() * xSign, indexY.toDouble() * ySign, indexZ.toDouble() * zSign)

                    when (block.type) {

                        Material.OAK_SIGN -> {
                            val data = DungeonTower.floorWorld.getBlockState(x, y, z) as Sign
                            when (data.getLine(0)) {
                                "floor" -> {
                                    val floor = (DungeonTower.floorData[data.getLine(1)] ?: continue).newInstance()
                                    val rotate = data.getLine(3).split(",").getOrNull(1)?.toDoubleOrNull() ?: 0.0
                                    distance += floor.calculateDistance(placeLoc, rotate)
                                }
                                else -> {}
                            }
                        }
                        else -> {}
                    }
                }
            }
        }

        return distance
    }

    fun generateFloor(towerData: TowerData, floorNum: Int, uuid: UUID): CompletableFuture<Void> {
        return CompletableFuture.runAsync {
            if (shouldUseSaveData) {
                if (loadedSaveData) {
                    DungeonTower.util.threadRunTask {
                        noLoadGenerateFloor()
                    }
                    return@runAsync
                } else {
                    SaveDataDB.load(uuid).get().find { it.towerName == towerData.internalName }?.let {
                        val floorData = it.floors[floorNum]?.find { floor ->
                            floor["internalName"] == internalName
                        }
                        if (floorData != null) {
                            if (loadData(floorData)) {
                                DungeonTower.util.threadRunTask {
                                    noLoadGenerateFloor()
                                }
                                return@runAsync
                            }
                        }
                    }
                }
            }
            val location = Location(
                DungeonTower.dungeonWorld,
                DungeonTower.nowX.toDouble(),
                DungeonTower.y.toDouble(),
                0.0
            )
            DungeonTower.util.threadRunTask {
                DungeonTower.nowX += calculateDistance(location, 0.0) + 1 + DungeonTower.dungeonXSpace
            }
            val newLocation = Location(
                DungeonTower.dungeonWorld,
                DungeonTower.nowX.toDouble(),
                DungeonTower.y.toDouble(),
                0.0
            )
            DungeonTower.util.threadRunTask {
                generateFloor(towerData, newLocation, 0.0)
            }
            SaveDataDB.save(
                uuid,
                towerData,
                floor = this,
                floorNum = floorNum
            )
        }
    }

    fun activate() {
        spawners.forEach {
            it.runTaskTimer(DungeonTower.plugin, 1, 1)
        }
        parallelFloors.forEach {
            it.value.activate()
        }
        return
    }

    fun unlockChest(){
        val (lowX, lowY, lowZ, highX, highY, highZ) = getPoints()

        val dungeonStartLoc = dungeonStartLoc?:return
        val dungeonEndLoc = dungeonEndLoc?:return

        val xSign = sign(dungeonEndLoc.blockX.toDouble() - dungeonStartLoc.blockX.toDouble())
        val ySign = sign(dungeonEndLoc.blockY.toDouble() - dungeonStartLoc.blockY.toDouble())
        val zSign = sign(dungeonEndLoc.blockZ.toDouble() - dungeonStartLoc.blockZ.toDouble())

        for ((indexX, _) in (lowX..highX).withIndex()){
            for ((indexY, _) in (lowY..highY).withIndex()){
                for ((indexZ, _) in (lowZ..highZ).withIndex()){
                    val placeLoc = dungeonStartLoc.clone().add(indexX.toDouble() * xSign, indexY.toDouble() * ySign, indexZ.toDouble() * zSign)

                    val chest = placeLoc.block.state as? Chest?:continue
                    chest.setLock(null)
                    chest.update()
                }
            }
        }
    }

    fun removeFloor(){
        val helper = BukkitAPIHelper()
        DungeonTower.dungeonWorld.entities.filter {
            spawnerClearTasks.containsKey(
                UUID.fromString(it.persistentDataContainer.get(
                    NamespacedKey(DungeonTower.plugin, "dmob"),
                    PersistentDataType.STRING)?:return@filter false)
            )
        }.forEach {
            helper.getMythicMobInstance(it).let { mob ->
                mob.children.forEach { child ->
                    child.remove()
                }
                mob.remove()
            }
        }
        spawners.forEach {
            it.cancel()
        }
        spawners.clear()

        WorldEdit.getInstance().newEditSession(BukkitWorld(DungeonTower.dungeonWorld)).use {
            val x = dungeonStartLoc!!.blockX
            val y = dungeonStartLoc!!.blockY
            val z = dungeonStartLoc!!.blockZ
            val endX = dungeonEndLoc!!.blockX
            val endY = dungeonEndLoc!!.blockY
            val endZ = dungeonEndLoc!!.blockZ
            it.setBlocks(CuboidRegion(BukkitWorld(DungeonTower.dungeonWorld),
                BlockVector3.at(x,y,z),
                BlockVector3.at(endX, endY, endZ)), BlockTypes.AIR!!.defaultState
            )
        }

        parallelFloors.forEach {
            it.value.removeFloor()
        }
    }

    fun toMap(): Map<String,Any> {
        val map = HashMap<String,Any>()
        map["parallelFloors"] = parallelFloors.entries.associate { it.key to it.value.toMap() }
        map["internalName"] = internalName
        map["rotate"] = rotate
        return map
    }

    @Suppress("UNCHECKED_CAST")
    fun loadData(map: Map<String,Any>): Boolean {
        if (internalName != map["internalName"])return false
        map["parallelFloors"]?.let {
            (it as Map<String,Any>).forEach { (key, value) ->
                value as Map<String,Any>
                parallelFloors[key] = DungeonTower.floorData[value["internalName"].toString()]!!.newInstance().apply {
                    loadData(value)
                }
            }
        }
        rotate = map["rotate"] as Double
        loadedSaveData = true
        return true
    }


    fun newInstance(): FloorData {
        val data = FloorData().apply {
            internalName = this@FloorData.internalName
            yml = this@FloorData.yml
            val start = yml.getString("startLoc")!!.split(",").map { it.toInt().toDouble() }
            startLoc = Location(DungeonTower.floorWorld,start[0],start[1],start[2])
            val end = yml.getString("endLoc")!!.split(",").map { it.toInt().toDouble() }
            endLoc = Location(DungeonTower.floorWorld,end[0],end[1],end[2])
            initialTime = yml.getInt("time",300)
            time = initialTime
            joinCommands.addAll(yml.getStringList("joinCommands"))
            yml.getStringList("clearTasks").forEach {
                val split = it.split(",")
                val taskEnum = ClearTaskEnum.valueOf(split[0].uppercase())
                val task = ClearTask(taskEnum)
                if (taskEnum == ClearTaskEnum.ENTER_COMMAND){
                    task.need = split[1].toInt()
                }
                task.scoreboardName = split.reversed()[1]
                task.clearScoreboardName = split.last()
                clearTask.add(task)
            }
            yml.getString("parallelFloorOrigin")?.let {
                val split = it.split(",")
                parallelFloorOrigin = Location(DungeonTower.floorWorld,split[0].toDouble(),split[1].toDouble(),split[2].toDouble())
            }
            yml.getStringList("subFloors").forEach {
                val split = it.split(",")
                subFloors.add(Pair(split[0].toInt(),split[1]))
            }
            shouldUseSaveData = yml.getBoolean("shouldUseSaveData",false)
        }

        return data
    }

    companion object {

        fun loadFromYml(file: File): Pair<String,FloorData> {
            val yml = YamlConfiguration.loadConfiguration(file)
            val data = FloorData().apply {
                internalName = file.nameWithoutExtension
                this.yml = yml
                newInstance()
            }

            return Pair(data.internalName,data)
        }
    }

}