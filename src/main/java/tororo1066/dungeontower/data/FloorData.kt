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
import org.bukkit.block.BlockFace
import org.bukkit.block.BlockState
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
import tororo1066.tororopluginapi.SDebug
import tororo1066.tororopluginapi.sEvent.SEvent
import tororo1066.tororopluginapi.script.ScriptFile
import tororo1066.tororopluginapi.utils.LocType
import tororo1066.tororopluginapi.utils.setYawL
import tororo1066.tororopluginapi.utils.toLocString
import java.io.File
import java.util.Random
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.math.*
import kotlin.random.nextInt
import kotlin.system.measureTimeMillis

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

    var uuid: UUID? = null

    var internalName = ""

    lateinit var startLoc: Location
    lateinit var endLoc: Location

    var initialTime = 300
    var time = 300

    var cancelStandOnStairs = true

    var dungeonStartLoc: Location? = null
    var dungeonEndLoc: Location? = null

    val previousFloorStairs = ArrayList<Location>()
    val nextFloorStairs = ArrayList<Location>()//使わないかもしれない
    val spawners = ArrayList<SpawnerRunnable>()

    val joinCommands = ArrayList<String>()

    val worldGameRules = mutableMapOf<GameRule<*>, Any>(
        GameRule.DO_DAYLIGHT_CYCLE to false,
        GameRule.DO_WEATHER_CYCLE to false,
        GameRule.DO_MOB_SPAWNING to false,
        GameRule.MOB_GRIEFING to false,
        GameRule.KEEP_INVENTORY to true,
    )

    val clearTask = ArrayList<ClearTask>()
    val spawnerClearTasks = HashMap<UUID,Boolean>()

    val parallelFloors = HashMap<String, FloorData>()
    var parallelFloor = false
    var parent: HashMap<String, Any?> = HashMap()
    var generateStep = 0
    var rotate = 0.0
    var parallelFloorOrigin: Location? = null
    lateinit var parentData: FloorData

    val subFloors = ArrayList<Pair<Int, String>>()

//    val waves = HashMap<Int,ArrayList<Pair<Int, WaveData>>>()

    lateinit var yml: YamlConfiguration

    var generated = false

    var shouldUseSaveData = false
    var loadedSaveData = false

    val dataBlocks = HashMap<Location, BlockState>()

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
                return find.clear && parallelFloors.none { !it.value.checkClear(task) } && if (parallelFloor) parentData.checkClear(task) else true
            }
            ClearCondition.CLEAR_PARENT -> {
                return !parallelFloor || parentData.checkClear(task)
            }
            ClearCondition.CLEAR_PARENT_AND_SELF -> {
                return find.clear && (!parallelFloor || parentData.checkClear(task))
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

    fun getDisplayName(towerData: TowerData, floorNum: Int): String {
        val script = towerData.floorDisplayScript ?: return towerData.name
        val scriptFile = ScriptFile(File(DungeonTower.plugin.dataFolder, script))
        scriptFile.publicVariables.run {
            put("towerName", towerData.name)
            put("floorName", internalName)
            put("floorNum", floorNum)
        }

        val result = scriptFile.start() as? String
        return result ?: towerData.name
    }

    inner class SpawnerRunnable(val data: SpawnerData, val location: Location, val uuid: UUID, val towerData: TowerData, val floorName: String, val floorNum: Int) : BukkitRunnable() {

        private val sEvent = SEvent(DungeonTower.plugin)
        private val facing = when((location.block.blockData as EndPortalFrame).facing){
            BlockFace.NORTH -> 0.0
            BlockFace.EAST -> 270.0
            BlockFace.SOUTH -> 180.0
            BlockFace.WEST -> 90.0
            else -> 0.0
        }

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

        private var coolTime = data.coolTime
        override fun run() {
            if (data.count >= data.max){
                cancel()
                return
            }
            coolTime--
            if (coolTime > 0)return
            coolTime = data.coolTime
            if (location.getNearbyPlayers(data.activateRange.toDouble())
                    .none { it.gameMode == GameMode.SURVIVAL || it.gameMode == GameMode.ADVENTURE })return
            val spawnLoc = location.clone().add(
                (-data.radius..data.radius).random().toDouble(),
                data.yOffSet,
                (-data.radius..data.radius).random().toDouble()
            ).setYawL(facing.toFloat())
            val mob = data.randomMob(towerData, floorName, floorNum)?.spawn(BukkitAdapter.adapt(spawnLoc),data.getLevel(towerData, floorName, floorNum))
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

        fun stop() {
            Bukkit.getScheduler().runTask(DungeonTower.plugin, Runnable {
                location.world.entities.filter { it.persistentDataContainer.get(
                    NamespacedKey(DungeonTower.plugin, "dmob"),
                    PersistentDataType.STRING) == uuid.toString() }.forEach {
                    it.remove()
                }
            })
            sEvent.unregisterAll()
            cancel()
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

    fun calculateLocation(location: Location, direction: Double): Pair<Location, Location> {
        val (lowX, lowY, lowZ, highX, highY, highZ) = getPoints()

        var modifiedDirection = if (direction < 0) direction + 360 else {
            direction
        }

        modifiedDirection = -Math.toRadians(modifiedDirection)
        val originLocation = (parallelFloorOrigin?:startLoc).clone()

        val originX = originLocation.blockX
        val originY = originLocation.blockY
        val originZ = originLocation.blockZ

        val dungeonStartLoc = location.clone().add(
            round((lowX - originX) * cos(modifiedDirection) - (lowZ - originZ) * sin(modifiedDirection)),
            (lowY - originY).toDouble(),
            round((lowZ - originZ) * cos(modifiedDirection) + (lowX - originX) * sin(modifiedDirection))
        )
        val dungeonEndLoc = location.clone().add(
            round((highX - originX) * cos(modifiedDirection) - (highZ - originZ) * sin(modifiedDirection)),
            (highY - originY).toDouble(),
            round((highZ - originZ) * cos(modifiedDirection) + (highX - originX) * sin(modifiedDirection))
        )

        if (dungeonStartLoc.blockX > dungeonEndLoc.blockX){
            val temp = dungeonStartLoc.blockX
            dungeonStartLoc.add((dungeonEndLoc.blockX - dungeonStartLoc.blockX).toDouble(),0.0,0.0)
            dungeonEndLoc.add((temp - dungeonEndLoc.blockX).toDouble(),0.0,0.0)
        }

        if (dungeonStartLoc.blockZ > dungeonEndLoc.blockZ){
            val temp = dungeonStartLoc.blockZ
            dungeonStartLoc.add(0.0,0.0,(dungeonEndLoc.blockZ - dungeonStartLoc.blockZ).toDouble())
            dungeonEndLoc.add(0.0,0.0,(temp - dungeonEndLoc.blockZ).toDouble())
        }

        return Pair(dungeonStartLoc, dungeonEndLoc)
    }

    data class ParallelFloorData(
        val floorName: String,
        val label: String,
        val script: String?,
        val rotate: Double,
        val signRotate: Double,
        val location: Location,
        var generate: Boolean
    )

    @Suppress("DEPRECATION")
    private fun generateFloor(towerData: TowerData, floorNum: Int, location: Location, direction: Double) {
        SDebug.broadcastDebug(3, "GeneratingFloor $internalName (step: $generateStep) in ${location.blockX},${location.blockY},${location.blockZ} with direction $direction")

        previousFloorStairs.clear()
        nextFloorStairs.clear()
        spawnerClearTasks.clear()
        spawners.clear()
        time = initialTime

        val (lowX, lowY, lowZ, highX, highY, highZ) = getPoints()

        val originLocation = (parallelFloorOrigin ?: startLoc).clone()
        SDebug.broadcastDebug(5, "parallelFloorOrigin: ${originLocation.blockX},${originLocation.blockY},${originLocation.blockZ}")

        val (dungeonStartLoc, dungeonEndLoc) = calculateLocation(location, direction)

        SDebug.broadcastDebug(5, "dungeonStartLoc: ${dungeonStartLoc.blockX},${dungeonStartLoc.blockY},${dungeonStartLoc.blockZ}")
        SDebug.broadcastDebug(5, "dungeonEndLoc: ${dungeonEndLoc.blockX},${dungeonEndLoc.blockY},${dungeonEndLoc.blockZ}")

        val buildLocation = location.clone()

        this.dungeonStartLoc = dungeonStartLoc
        this.dungeonEndLoc = dungeonEndLoc

        val measure = measureTimeMillis {
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


            WorldEdit.getInstance().newEditSession(BukkitWorld(location.world)).use {
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
        }

        SDebug.broadcastDebug(4, "generateFloor: $measure ms")

        val modifiedDirection = -Math.toRadians(
            if (direction < 0) direction + 360 else {
                direction
            }
        )

        dataBlocks.forEach { (loc, state) ->
            val block = state.block
            val x = loc.blockX
            val y = loc.blockY
            val z = loc.blockZ

            val placeLoc = location.clone().add(
                round((x - originLocation.blockX) * cos(modifiedDirection) - (z - originLocation.blockZ) * sin(modifiedDirection)),
                (y - originLocation.blockY).toDouble(),
                round((z - originLocation.blockZ) * cos(modifiedDirection) + (x - originLocation.blockX) * sin(modifiedDirection))
            )

            when (block.type) {

                Material.OAK_SIGN -> {
                    SDebug.broadcastDebug(6, "placeLoc: ${placeLoc.blockX},${placeLoc.blockY},${placeLoc.blockZ}")
                    val data = state as Sign
                    when (data.getLine(0)) {
                        "loot" -> {
                            val loot = (DungeonTower.lootData[data.getLine(1)] ?: return@forEach).clone()
                            SDebug.broadcastDebug(2, "Generating Loot Chest in ${placeLoc.blockX},${placeLoc.blockY},${placeLoc.blockZ} (${x},${y},${z})")
                            DungeonTower.util.runTask {
                                placeLoc.block.type = Material.CHEST
                                val chest = placeLoc.block.state as Chest
                                (chest.blockData as Directional).facing = (data.blockData as org.bukkit.block.data.type.Sign).rotation
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
                        }
                        "spawner" -> {
                            val spawner =
                                (DungeonTower.spawnerData[data.getLine(1)] ?: return@forEach).clone()
                            val locSave = placeLoc.clone()
                            DungeonTower.util.runTask {
                                locSave.block.type = Material.END_PORTAL_FRAME
                                val portal = locSave.block.blockData as EndPortalFrame
                                portal.facing = (data.blockData as org.bukkit.block.data.type.Sign).rotation
                                portal.setEye(true)

                                locSave.block.blockData = portal
                                val randUUID = UUID.randomUUID()
                                spawnerClearTasks[randUUID] = spawner.navigateKill <= 0
                                val find = clearTask.find { it.type == ClearTaskEnum.KILL_SPAWNER_MOBS }
                                if (find != null) find.need += spawner.navigateKill
                                spawners.add(
                                    SpawnerRunnable(spawner, locSave, randUUID, towerData, internalName, floorNum)
                                )
                            }
                        }
                        "floor" -> {
                            SDebug.broadcastDebug(5, "Generating Parallel Floor in ${(placeLoc).toLocString(LocType.BLOCK_COMMA)} (${x},${y},${z})")
                            val script = data.getLine(3).ifBlank { null }
                            val label = script?.let { let -> FloorScript.getLabelName(let) } ?: "${x},${y},${z}"
                            if (parallelFloors.containsKey(label)) {
                                val floor = parallelFloors[label]!!
                                floor.generateFloor(towerData, floorNum, placeLoc, floor.rotate)
                            }

                            DungeonTower.util.runTask {
                                placeLoc.block.type = Material.AIR
                            }
                        }
                        else -> {}
                    }
                }

                Material.WARPED_STAIRS -> {
                    nextFloorStairs.add(placeLoc.clone().add(0.0, 1.0, 0.0))
                }

                Material.CRIMSON_STAIRS -> {
                    previousFloorStairs.add(
                        placeLoc.clone().add(0.0, 1.0, 0.0).setDirection((block.blockData as Stairs).facing.direction)
                    )
                }

                else -> {}
            }
        }

        generated = true
    }

    @Suppress("DEPRECATION")
    fun calculateDistance(towerData: TowerData, location: Location, direction: Double, floorNum: Int): Int {
        DungeonTower.util.threadRunTask {
            loadDataBlocks()
        }
        if (uuid == null) uuid = UUID.randomUUID()

        val originLocation = (parallelFloorOrigin ?: startLoc).clone()

        val (dungeonStartLoc, dungeonEndLoc) = calculateLocation(location, direction)

        val modifiedDirection = -Math.toRadians(
            if (direction < 0) direction + 360 else {
                direction
            }
        )

        var distance = dungeonEndLoc.blockX - dungeonStartLoc.blockX

        val loadedParallelFloors = ArrayList<ParallelFloorData>()

        dataBlocks.forEach { (loc, state) ->
            val block = state.block
            if (block.type != Material.OAK_SIGN) return@forEach
            val data = state as Sign
            val placeLoc = location.clone().add(
                round((loc.blockX - originLocation.blockX) * cos(modifiedDirection) - (loc.blockZ - originLocation.blockZ) * sin(modifiedDirection)),
                (loc.blockY - originLocation.blockY).toDouble(),
                round((loc.blockZ - originLocation.blockZ) * cos(modifiedDirection) + (loc.blockX - originLocation.blockX) * sin(modifiedDirection))
            )
            val x = loc.blockX
            val y = loc.blockY
            val z = loc.blockZ

            when (data.getLine(0)) {
                "floor" -> {
                    if (loadedSaveData) {
                        val script = data.getLine(3).ifBlank { null }
                        val label = script?.let { let -> FloorScript.getLabelName(let) } ?: "${x},${y},${z}"
                        if (parallelFloors.containsKey(label)) {
                            val floor = parallelFloors[label]!!
                            floor.parentData = this
                            floor.parallelFloor = true
                            distance += floor.calculateDistance(towerData, placeLoc, 0.0, floorNum)
                            SDebug.broadcastDebug(1, "Loaded Parallel Floor in ${(placeLoc).toLocString(LocType.BLOCK_COMMA)} (${x},${y},${z})")
                            return@forEach
                        }
                    }
                    val floor = data.getLine(1)
                    val split = data.getLine(2).split(",")
                    val chance = split.getOrNull(0)?.toIntOrNull() ?: 1000000
                    val rotate = split.getOrNull(1)?.toDoubleOrNull() ?: 0.0
                    val script = data.getLine(3).ifBlank { null }
                    val label = script?.let { let -> FloorScript.getLabelName(let) } ?: "${x},${y},${z}"
                    val signRotation = (block.blockData as org.bukkit.block.data.type.Sign).rotation
                    val signRotate = when(signRotation){
                        BlockFace.NORTH -> 0.0
                        BlockFace.EAST -> 270.0
                        BlockFace.SOUTH -> 180.0
                        BlockFace.WEST -> 90.0
                        else -> 0.0
                    }

                    loadedParallelFloors.add(
                        ParallelFloorData(
                            floor,
                            label,
                            script,
                            rotate,
                            signRotate,
                            placeLoc,
                            Random().nextInt(1000000) < chance
                        )
                    )
                }
                else -> {}
            }
        }

        loadedParallelFloors.sortedByDescending { it.script?.let { let -> FloorScript.getLoadPriority(let) } }.forEach { data ->
            val (floorName, rotate) = FloorScript.generateSubFloorScript(
                towerData.internalName,
                data.floorName,
                floorNum,
                uuid!!,
                data.location,
                parent,
                data.script,
                rotate,
                data.rotate,
                data.signRotate,
                generateStep,
                loadedParallelFloors.filter { it.generate }.map { it.label },
                loadedParallelFloors.filter { !it.generate }.map { it.label },
                data.generate,
                location.world.name.split("_").last().toInt()
            )

            if (floorName.isBlank()) return@forEach
            val floorData = DungeonTower.floorData[floorName]?.newInstance() ?: return@forEach
            floorData.parallelFloor = true
            floorData.parentData = this
            floorData.rotate = rotate + this.rotate
            floorData.generateStep = generateStep + 1
            floorData.uuid = this.uuid
            floorData.parent = HashMap(mapOf(
                "floorName" to this.internalName,
                "script" to data.script,
                "rotate" to this.rotate,
                "parent" to if (parent.isEmpty()) null else parent,
                "generateStep" to generateStep,
                "startLoc" to listOf(dungeonStartLoc.blockX, dungeonStartLoc.blockY, dungeonStartLoc.blockZ),
                "endLoc" to listOf(dungeonEndLoc.blockX, dungeonEndLoc.blockY, dungeonEndLoc.blockZ),
                "parallelFloorOrigin" to listOf(originLocation.blockX, originLocation.blockY, originLocation.blockZ),
                "location" to listOf(location.blockX, location.blockY, location.blockZ),
            ))
            parallelFloors[data.label] = floorData
            FloorScript.floors.getOrPut(uuid!!) { mutableListOf() }.add(floorData.parent)

            distance += floorData.calculateDistance(towerData, data.location, floorData.rotate, floorNum)
        }

        return distance
    }

    fun generateFloor(towerData: TowerData, world: World, floorNum: Int, playerUUID: UUID): CompletableFuture<Void> {
        return CompletableFuture.runAsync {
            var save = false
            if (shouldUseSaveData) {
                if (loadedSaveData) {
                    save = true
                } else {
                    SaveDataDB.load(playerUUID).get().find { it.towerName == towerData.internalName }?.let {
                        val floorData = it.floors[floorNum]?.find { floor ->
                            floor["internalName"] == internalName
                        }
                        if (floorData != null) {
                            if (loadData(floorData)) {
                                SDebug.broadcastDebug(1, "Using SaveData for $internalName")
                                save = true
                            }
                        }
                    }
                }
            }
            val location = Location(
                world,
                0.0,
                DungeonTower.y.toDouble(),
                0.0
            )
            calculateDistance(towerData, location, 0.0, floorNum)
            generateFloor(towerData, floorNum, location, 0.0)
            if (save) {
                SaveDataDB.save(
                    playerUUID,
                    towerData,
                    floor = this,
                    floorNum = floorNum
                )
            }
        }
    }

    fun activate() {
        spawners.forEach {
            it.runTaskTimer(DungeonTower.plugin, 1, 1)
        }
        parallelFloors.forEach {
            it.value.activate()
        }
        if (clearTask.none { !it.clear }) {
            unlockChest()
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

    fun killMobs(world: World) {
        val helper = BukkitAPIHelper()
        world.entities.filter {
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
    }

    fun removeFloor(world: World){
        spawners.forEach {
            it.stop()
        }
        spawners.clear()

        SDebug.broadcastDebug(5, "dungeonStartLoc: ${dungeonStartLoc?.toLocString(LocType.BLOCK_COMMA)}")
        SDebug.broadcastDebug(5, "dungeonEndLoc: ${dungeonEndLoc?.toLocString(LocType.BLOCK_COMMA)}")

        WorldEdit.getInstance().newEditSession(BukkitWorld(world)).use {
            val x = dungeonStartLoc!!.blockX
            val y = dungeonStartLoc!!.blockY
            val z = dungeonStartLoc!!.blockZ
            val endX = dungeonEndLoc!!.blockX
            val endY = dungeonEndLoc!!.blockY
            val endZ = dungeonEndLoc!!.blockZ
            it.setBlocks(CuboidRegion(BukkitWorld(world),
                BlockVector3.at(x,y,z),
                BlockVector3.at(endX, endY, endZ)) as Set<BlockVector3>, BlockTypes.AIR!!.defaultState
            )
        }

        parallelFloors.forEach {
            it.value.removeFloor(world)
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
                    SDebug.broadcastDebug(1, "Loading Parallel Floor $key")
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
            uuid = null
            internalName = this@FloorData.internalName
            yml = this@FloorData.yml
            val start = yml.getString("startLoc")!!.split(",").map { it.toInt().toDouble() }
            startLoc = Location(DungeonTower.floorWorld,start[0],start[1],start[2])
            val end = yml.getString("endLoc")!!.split(",").map { it.toInt().toDouble() }
            endLoc = Location(DungeonTower.floorWorld,end[0],end[1],end[2])
            initialTime = yml.getInt("time",300)
            time = initialTime
            cancelStandOnStairs = yml.getBoolean("cancelStandOnStairs",true)
            joinCommands.addAll(yml.getStringList("joinCommands"))
            val section = yml.getConfigurationSection("worldGameRules")
            if (section != null) {
                worldGameRules.clear()
                section.getKeys(false).forEach {
                    val rule = GameRule.getByName(it) ?: return@forEach
                    worldGameRules[rule] = yml.get("worldGameRules.$it") ?: return@forEach
                }
            }
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

    private fun loadDataBlocks() {
        val (lowX, lowY, lowZ, highX, highY, highZ) = getPoints()

        for (x in (lowX..highX)) {
            for (y in (lowY..highY)) {
                for (z in (lowZ..highZ)) {
                    val block = DungeonTower.floorWorld.getBlockAt(x, y, z)

                    if (block.type in arrayOf(Material.OAK_SIGN, Material.WARPED_STAIRS, Material.CRIMSON_STAIRS)) {
                        dataBlocks[Location(DungeonTower.floorWorld, x.toDouble(), y.toDouble(), z.toDouble())] = block.state
                    }
                }
            }
        }

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