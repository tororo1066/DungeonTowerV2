package tororo1066.dungeontower.data

import io.lumine.mythic.api.mobs.MythicMob
import io.lumine.mythic.bukkit.BukkitAdapter
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.persistence.PersistentDataType
import tororo1066.dungeontower.DungeonTower
import tororo1066.dungeontower.dmonitor.workspace.SpawnerWorkspace
import tororo1066.tororopluginapi.otherUtils.UsefulUtility
import java.io.File
import java.util.UUID
import kotlin.random.Random
import kotlin.random.nextInt

class SpawnerData: Cloneable {

    var internalName = ""
    val mobs = ArrayList<Pair<Int, MythicMob>>()
    var spawnScript: String? = null
    var count = 0
    var coolTime = 0
    var max = 0
    var radius = 0
    var yOffSet = 0.0
    var level = 1.0
    var activateRange = 0

    var kill = 0

    fun spawn(location: Location, spawnerUUID: UUID, towerData: TowerData, floorName: String, floorNum: Int) {
        val script = SpawnerWorkspace.spawnerScripts[spawnScript]

        if (script != null) {
            val context = DungeonTower.actionStorage.createActionContext(
                DungeonTower.actionStorage.createPublicContext().apply {
                    workspace = SpawnerWorkspace
                    parameters["yOffSet"] = yOffSet
                    parameters["radius"] = radius
                    parameters["level"] = level
                    parameters["tower.name"] = towerData.internalName
                    parameters["floor.name"] = floorName
                    parameters["floor.num"] = floorNum
                    parameters["spawner.uuid"] = spawnerUUID.toString()
                }
            ).apply {
                this.location = location
            }

            script.run(
                context,
                true,
                null
            )
            return
        }

        var mob: MythicMob? = null
        val random = Random.nextInt(1..1000000)
        var previousRandom = 0
        for (pair in mobs) {
            if (previousRandom < random && pair.first + previousRandom > random) {
                mob = pair.second
                break
            }
            previousRandom += pair.first
        }
        if (mob == null) return
        val activeMob = mob.spawn(BukkitAdapter.adapt(location), level)
        activeMob.entity.dataContainer.set(
            NamespacedKey(DungeonTower.plugin, DungeonTower.DUNGEON_MOB),
            PersistentDataType.STRING,
            spawnerUUID.toString()
        )
    }

    public override fun clone(): SpawnerData {
        return super.clone() as SpawnerData
    }

    companion object{
        fun loadFromYml(file: File): Pair<String, SpawnerData> {
            val yml = YamlConfiguration.loadConfiguration(file)
            val data = SpawnerData().apply {
                internalName = file.nameWithoutExtension
                yml.getConfigurationSection("mobs")?.let {
                    for (key in it.getKeys(false)){
                        DungeonTower.mythic.getMythicMob(key)?.let { mob ->
                            mobs.add(Pair(it.getInt(key),mob))
                        }
                    }
                }
                spawnScript = yml.getString("spawnScript")
                coolTime = yml.getInt("cooltime")
                max = yml.getInt("max")
                yOffSet = yml.getDouble("yOffSet")
                radius = yml.getInt("radius")
                level = yml.getDouble("level",1.0)
                activateRange = yml.getInt("activateRange")
            }

            return Pair(data.internalName, data)
        }
    }
}