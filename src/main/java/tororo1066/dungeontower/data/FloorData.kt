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
import tororo1066.tororopluginapi.SDebug
import tororo1066.tororopluginapi.sEvent.SEvent
import tororo1066.tororopluginapi.utils.LocType
import tororo1066.tororopluginapi.utils.toLocString
import java.io.File
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.math.*
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

    val parallelFloors = ArrayList<FloorData>()
    var parallelFloor = false
    var parallelFloorOrigin: Location? = null
    lateinit var parent: FloorData

    val subFloors = ArrayList<Pair<Int, FloorData>>()

    val waves = HashMap<Int,ArrayList<Pair<Int, WaveData>>>()

    lateinit var yml: YamlConfiguration

    var generated = false

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
                return find.clear && parallelFloors.none { !it.checkClear(task) } && if (parallelFloor) parent.checkClear(task) else true
            }
            ClearCondition.CLEAR_PARENT -> {
                return !parallelFloor || parent.checkClear(task)
            }
            ClearCondition.CLEAR_PARENT_AND_SELF -> {
                return find.clear && (!parallelFloor || parent.checkClear(task))
            }
            ClearCondition.CLEAR_PARALLEL -> {
                return parallelFloors.none { !it.checkClear(task) }
            }
            ClearCondition.CLEAR_PARALLEL_AND_SELF -> {
                return find.clear && parallelFloors.none { !it.checkClear(task) }
            }
        }
    }

    fun randomFloor(): FloorData {
        val random = kotlin.random.Random.nextInt(1..1000000)
        var preventRandom = 0
        for (floor in subFloors){
            if (preventRandom < random && floor.first + preventRandom > random){
                return floor.second.newInstance()
            }
            preventRandom = floor.first
        }
        throw NullPointerException("Couldn't find floor. Maybe sum percentage is not 1000000.")
    }

    inner class SpawnerRunnable(val data: SpawnerData, val location: Location, val uuid: UUID) : BukkitRunnable() {

        val sEvent = SEvent(DungeonTower.plugin)

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
        parallelFloors.forEach {
            list.addAll(it.getAllTask())
        }
        return list
    }

    private fun Location.toBlockVector3(): BlockVector3 {
        return BlockVector3.at(blockX,blockY,blockZ)
    }

    fun generateFloor(location: Location, direction: Double) {

        preventFloorStairs.clear()
        nextFloorStairs.clear()
        spawnerClearTasks.clear()
        spawners.clear()
        time = initialTime
        val lowX = min(startLoc.blockX, endLoc.blockX)
        val lowY = min(startLoc.blockY, endLoc.blockY)
        val lowZ = min(startLoc.blockZ, endLoc.blockZ)
        val highX = if (lowX == startLoc.blockX) endLoc.blockX else startLoc.blockX
        val highY = if (lowY == startLoc.blockY) endLoc.blockY else startLoc.blockY
        val highZ = if (lowZ == startLoc.blockZ) endLoc.blockZ else startLoc.blockZ

        val modifiedDirection = Math.toRadians(if (direction < 0) direction + 360 else direction)

        val dungeonStartLoc = location.clone()
        val dungeonEndLoc = location.clone().add(
            (highX- lowX) * cos(modifiedDirection) - (highZ - lowZ) * sin(modifiedDirection),
            (highY - lowY).toDouble(),
            (highX- lowX) * sin(modifiedDirection) + (highZ - lowZ) * cos(modifiedDirection)
        )
//        Bukkit.broadcastMessage("dungeonStartLoc: ${dungeonStartLoc.toLocString(LocType.WORLD_BLOCK_COMMA)}")
//        Bukkit.broadcastMessage("dungeonEndLoc: ${dungeonEndLoc.toLocString(LocType.WORLD_BLOCK_COMMA)}")

//        if (dungeonStartLoc.blockX > dungeonEndLoc.blockX){
//            val temp = dungeonStartLoc.blockX
//            dungeonStartLoc.add((dungeonStartLoc.blockX - dungeonEndLoc.blockX).toDouble(),0.0,0.0)
//            dungeonEndLoc.add((temp - dungeonEndLoc.blockX).toDouble(),0.0,0.0)
//        }
//
//        if (dungeonStartLoc.blockZ > dungeonEndLoc.blockZ){
//            val temp = dungeonStartLoc.blockZ
//            dungeonStartLoc.add(0.0,0.0,(dungeonStartLoc.blockZ - dungeonEndLoc.blockZ).toDouble())
//            dungeonEndLoc.add(0.0,0.0,(temp - dungeonEndLoc.blockZ).toDouble())
//        }

        val buildLocation = dungeonStartLoc.clone()
        val originLocation = (parallelFloorOrigin?:startLoc).clone()


//        if (parallelFloor && parallelFloorOrigin != null){
//            val parallelFloorOrigin = parallelFloorOrigin!!.clone()
//            val blockX = parallelFloorOrigin.blockX
//            val blockZ = parallelFloorOrigin.blockZ
//            dungeonStartLoc.subtract(
//                (blockX - startLoc.blockX).toDouble(),
//                0.0,
//                (blockZ - startLoc.blockZ).toDouble()
//            )
//            dungeonEndLoc.subtract(
//                (blockX - startLoc.blockX).toDouble(),
//                0.0,
//                (blockZ - startLoc.blockZ).toDouble()
//            )
//            SDebug.broadcastDebug(1, "dungeonStartLoc: ${dungeonStartLoc.toLocString(LocType.WORLD_BLOCK_COMMA)}, dungeonEndLoc: ${dungeonEndLoc.toLocString(LocType.WORLD_BLOCK_COMMA)}")
//        }

        this.dungeonStartLoc = dungeonStartLoc
        this.dungeonEndLoc = dungeonEndLoc
        Bukkit.broadcastMessage("modifiedDungeonStartLoc: ${dungeonStartLoc.toLocString(LocType.WORLD_BLOCK_COMMA)}")
        Bukkit.broadcastMessage("modifiedDungeonEndLoc: ${dungeonEndLoc.toLocString(LocType.WORLD_BLOCK_COMMA)}")

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

        if (parallelFloor) {
            DungeonTower.nowX += abs(dungeonEndLoc.blockX - dungeonStartLoc.blockX)
            if (DungeonTower.xLimit <= DungeonTower.nowX) {
                DungeonTower.nowX = 0
            }
        } else {
            DungeonTower.nowX += abs(dungeonEndLoc.blockX - dungeonStartLoc.blockX) + DungeonTower.dungeonXSpace
            if (DungeonTower.xLimit <= DungeonTower.nowX) {
                DungeonTower.nowX = 0
            }
        }


        WorldEdit.getInstance().newEditSession(BukkitWorld(DungeonTower.dungeonWorld)).use {
            val operation = ClipboardHolder(clipboard)
                .apply {
                    transform = transform.combine(AffineTransform()
                        .rotateY(direction))
                }
                .createPaste(it)
                .to(buildLocation.toBlockVector3())
                .ignoreAirBlocks(false)
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

                    if (parallelFloor){
                        placeLoc.block.type = Material.EMERALD_BLOCK
                    }
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
                                    val floor = (DungeonTower.floorData[data.getLine(1)] ?: continue).newInstance()
                                    val chance = data.getLine(2).toIntOrNull() ?: 1000000
                                    if (Random().nextInt(1000000) > chance) continue
                                    val rotate = data.getLine(3).toDoubleOrNull() ?: 0.0
                                    floor.parent = this
                                    floor.parallelFloor = true
                                    parallelFloors.add(floor)
                                    floor.generateFloor(placeLoc, rotate)
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

    fun generateFloor() {
        generateFloor(
            Location(
                DungeonTower.dungeonWorld,
                DungeonTower.nowX.toDouble(),
                DungeonTower.y.toDouble(),
                0.0
            ), 0.0
        )
    }

    fun activate() {
        spawners.forEach {
            it.runTaskTimer(DungeonTower.plugin, 1, 1)
        }
        return
    }

    fun unlockChest(){
        val lowX = min(startLoc.blockX, endLoc.blockX)
        val lowY = min(startLoc.blockY, endLoc.blockY)
        val lowZ = min(startLoc.blockZ, endLoc.blockZ)
        val highX = if (lowX == startLoc.blockX) endLoc.blockX else startLoc.blockX
        val highY = if (lowY == startLoc.blockY) endLoc.blockY else startLoc.blockY
        val highZ = if (lowZ == startLoc.blockZ) endLoc.blockZ else startLoc.blockZ

        for ((indexX, _) in (lowX..highX).withIndex()){
            for ((indexY, _) in (lowY..highY).withIndex()){
                for ((indexZ, _) in (lowZ..highZ).withIndex()){
                    val placeLoc = dungeonStartLoc!!.clone().add(indexX.toDouble(),indexY.toDouble(),indexZ.toDouble())

                    val chest = placeLoc.block.state as? Chest?:continue
                    chest.setLock(null)
                    chest.update()
                }
            }
        }
    }

    fun removeFloor(){
        DungeonTower.dungeonWorld.entities.filter {
            spawnerClearTasks.containsKey(
                UUID.fromString(it.persistentDataContainer.get(
                    NamespacedKey(DungeonTower.plugin, "dmob"),
                    PersistentDataType.STRING)?:return@filter false)
            )
        }.forEach {
            it.remove()
        }
        spawners.forEach {
            it.cancel()
        }
        spawners.clear()

        val lowX = min(startLoc.blockX, endLoc.blockX)
        val lowY = min(startLoc.blockY, endLoc.blockY)
        val lowZ = min(startLoc.blockZ, endLoc.blockZ)
        val highX = if (lowX == startLoc.blockX) endLoc.blockX else startLoc.blockX
        val highY = if (lowY == startLoc.blockY) endLoc.blockY else startLoc.blockY
        val highZ = if (lowZ == startLoc.blockZ) endLoc.blockZ else startLoc.blockZ

        WorldEdit.getInstance().newEditSession(BukkitWorld(DungeonTower.dungeonWorld)).use {
            val x = dungeonStartLoc!!.blockX
            val y = dungeonStartLoc!!.blockY
            val z = dungeonStartLoc!!.blockZ
            it.setBlocks(CuboidRegion(BukkitWorld(DungeonTower.dungeonWorld),
                BlockVector3.at(x,y,z),
                BlockVector3.at(x+highX-lowX,y+highY-lowY,z+highZ-lowZ)), BlockTypes.AIR!!.defaultState
            )
        }

        parallelFloors.forEach {
            it.removeFloor()
        }
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
                val floorData = (DungeonTower.floorData[split[1]]?:throw NullPointerException("Failed load FloorData to ${split[1]} in ${internalName}.")).newInstance()
                subFloors.add(Pair(split[0].toInt(),floorData))
            }
        }

        return data
    }

    companion object {
        fun loadFromYml(file: File): Pair<String,FloorData> {
            val yml = YamlConfiguration.loadConfiguration(file)
            val data = FloorData().apply {
                internalName = file.nameWithoutExtension
                this.yml = yml
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
                    val floorData = (DungeonTower.floorData[split[1]]?:throw NullPointerException("Failed load FloorData to ${split[1]} in ${file.nameWithoutExtension}.")).newInstance()
                    subFloors.add(Pair(split[0].toInt(),floorData))
                }
            }

            return Pair(data.internalName,data)
        }
    }

}