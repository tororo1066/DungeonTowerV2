package tororo1066.dungeontower.data

import com.sk89q.worldedit.WorldEdit
import com.sk89q.worldedit.bukkit.BukkitWorld
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard
import com.sk89q.worldedit.function.operation.ForwardExtentCopy
import com.sk89q.worldedit.function.operation.Operations
import com.sk89q.worldedit.math.BlockVector3
import com.sk89q.worldedit.regions.CuboidRegion
import com.sk89q.worldedit.session.ClipboardHolder
import com.sk89q.worldedit.world.block.BlockTypes
import io.lumine.mythic.bukkit.BukkitAdapter
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent
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
import org.bukkit.scheduler.BukkitTask
import tororo1066.dungeontower.DungeonTower
import tororo1066.tororopluginapi.sEvent.SEvent
import java.io.File
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.math.abs
import kotlin.math.min

class FloorData: Cloneable {

    enum class ClearTaskEnum {
        KILL_SPAWNER_MOBS,
        ENTER_COMMAND
    }

    class ClearTask(val type: ClearTaskEnum, var need: Int = 0,
                    var count: Int = 0, var clear: Boolean = false,
                    var scoreboardName: String = "", var clearScoreboardName: String = ""): Cloneable {

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

    var lastFloor = true

    val preventFloorStairs = ArrayList<Location>()
    val nextFloorStairs = ArrayList<Location>()//使わないかもしれない
    val spawners = ArrayList<BukkitTask>()

    val joinCommands = ArrayList<String>()

    val clearTask = ArrayList<ClearTask>()
    val spawnerClearTasks = HashMap<UUID,Boolean>()

    lateinit var yml: YamlConfiguration

    fun callFloor() {
        DungeonTower.createFloorNow = true
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

        dungeonStartLoc = Location(DungeonTower.dungeonWorld, DungeonTower.nowX.toDouble(), DungeonTower.y.toDouble(), 0.0)

        val region = CuboidRegion(
            BukkitWorld(DungeonTower.floorWorld),
            BlockVector3.at(lowX,lowY,lowZ),
            BlockVector3.at(highX,highY,highZ))
        val clipboard = BlockArrayClipboard(region)
        val forwardExtentCopy = ForwardExtentCopy(BukkitWorld(DungeonTower.floorWorld), region, clipboard, region.minimumPoint)
        Operations.complete(forwardExtentCopy)

        WorldEdit.getInstance().newEditSession(BukkitWorld(DungeonTower.dungeonWorld)).use {
            val operation = ClipboardHolder(clipboard)
                .createPaste(it)
                .to(BlockVector3.at(DungeonTower.nowX,DungeonTower.y,0))
                .ignoreAirBlocks(true)
                .build()
            Operations.complete(operation)
        }

        for ((indexX, x) in (lowX..highX).withIndex()){
            for ((indexY, y) in (lowY..highY).withIndex()){
                for ((indexZ, z) in (lowZ..highZ).withIndex()){
                    val block = DungeonTower.floorWorld.getBlockAt(x,y,z)
                    val placeLoc = dungeonStartLoc!!.clone().add(indexX.toDouble(),indexY.toDouble(),indexZ.toDouble())

                    when(block.type){

                        Material.OAK_SIGN->{
                            val data = DungeonTower.floorWorld.getBlockState(x,y,z) as Sign
                            when(data.getLine(0)){
                                "loot"->{
                                    val loot = (DungeonTower.lootData[data.getLine(1)]?:continue).clone()
                                    placeLoc.block.type = Material.CHEST
                                    val chest = placeLoc.block.state as Chest
                                    chest.customName(Component.text(loot.displayName))
                                    chest.setLock("§c§l${Random().nextDouble(10000.0)}")
                                    chest.update()
                                    loot.fillInventory(chest.inventory, Random(),
                                        LootContext.Builder(chest.location).build())
                                    val blockData = chest.blockData as Directional
                                    blockData.facing = (data.blockData as org.bukkit.block.data.type.Sign).rotation
                                    chest.blockData = blockData
                                }
                                "spawner"->{
                                    val spawner = (DungeonTower.spawnerData[data.getLine(1)]?:continue).clone()
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
                                        object : BukkitRunnable() {
                                            val sEvent = SEvent(DungeonTower.plugin)
                                            init {
                                                sEvent.register(EntityDeathEvent::class.java) { e ->
                                                    if (e.entity.persistentDataContainer[NamespacedKey(DungeonTower.plugin,"dmob"), PersistentDataType.STRING] != randUUID.toString())return@register
                                                    spawner.kill++
                                                    if (spawner.kill <= spawner.navigateKill){
                                                        val ksFind = clearTask.find { it.type == ClearTaskEnum.KILL_SPAWNER_MOBS }
                                                        if (ksFind != null) ksFind.count += 1
                                                    }
                                                    if (spawner.kill >= spawner.navigateKill){
                                                        spawnerClearTasks[randUUID] = true
                                                        if (spawnerClearTasks.values.none { !it }){
                                                            clearTask.filter { it.type == ClearTaskEnum.KILL_SPAWNER_MOBS }.forEach {
                                                                it.clear = true
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                            override fun cancel() {
                                                sEvent.unregisterAll()
                                                Bukkit.getScheduler().runTask(DungeonTower.plugin, Runnable {
                                                    DungeonTower.dungeonWorld.entities.filter { it.persistentDataContainer.get(
                                                        NamespacedKey(DungeonTower.plugin, "dmob"),
                                                        PersistentDataType.STRING) == randUUID.toString() }.forEach {
                                                        it.remove()
                                                    }
                                                })
                                                super.cancel()
                                            }

                                            override fun run() {
                                                if (spawner.count >= spawner.max)return
                                                if (locSave.getNearbyPlayers(spawner.activateRange.toDouble()).isEmpty())return
                                                val spawnLoc = locSave.clone().add(
                                                    (-spawner.radius..spawner.radius).random().toDouble(),
                                                    spawner.yOffSet,
                                                    (-spawner.radius..spawner.radius).random().toDouble()
                                                )
                                                val mob = spawner.mob?.spawn(BukkitAdapter.adapt(spawnLoc),spawner.level)
                                                locSave.world.playSound(locSave, Sound.BLOCK_END_PORTAL_FRAME_FILL, 1f, 1f)
                                                locSave.world.spawnParticle(Particle.FLAME, locSave, 15)
                                                mob?.entity?.dataContainer?.set(
                                                    NamespacedKey(DungeonTower.plugin,"dmob"),
                                                    PersistentDataType.STRING,
                                                    randUUID.toString())
                                                spawner.count++
                                                if (spawner.count >= spawner.max){
                                                    portal.setEye(false)
                                                    locSave.block.blockData = portal
                                                }

                                            }
                                        }.runTaskTimer(DungeonTower.plugin,spawner.coolTime.toLong(),spawner.coolTime.toLong())
                                    )
                                }

                            }

                        }
                        Material.WARPED_STAIRS->{
                            nextFloorStairs.add(placeLoc.clone().add(0.0,1.0,0.0).setDirection((block.blockData as Stairs).facing.direction))
                            lastFloor = false
                        }
                        Material.CRIMSON_STAIRS->{
                            preventFloorStairs.add(placeLoc.clone().add(0.0,1.0,0.0))
                        }

                        else->{}
                    }
                }
            }
        }

        DungeonTower.nowX += abs(highX - lowX) + DungeonTower.dungeonXSpace
        if (DungeonTower.xLimit <= DungeonTower.nowX){
            DungeonTower.nowX = 0
        }
        DungeonTower.createFloorNow = false
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
    }


    fun newInstance(): FloorData {
        val data = FloorData().apply {
            internalName = this@FloorData.internalName
            yml = this@FloorData.yml
            val start = yml.getString("startLoc")!!.split(",").map { it.toInt().toDouble() }
            startLoc = Location(DungeonTower.dungeonWorld,start[0],start[1],start[2])
            val end = yml.getString("endLoc")!!.split(",").map { it.toInt().toDouble() }
            endLoc = Location(DungeonTower.dungeonWorld,end[0],end[1],end[2])
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
                startLoc = Location(DungeonTower.dungeonWorld,start[0],start[1],start[2])
                val end = yml.getString("endLoc")!!.split(",").map { it.toInt().toDouble() }
                endLoc = Location(DungeonTower.dungeonWorld,end[0],end[1],end[2])
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
            }

            return Pair(data.internalName,data)
        }
    }

}