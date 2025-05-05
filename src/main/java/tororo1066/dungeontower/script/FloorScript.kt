package tororo1066.dungeontower.script

import com.ezylang.evalex.Expression
import com.ezylang.evalex.data.EvaluationValue
import com.ezylang.evalex.functions.AbstractFunction
import com.ezylang.evalex.functions.FunctionParameter
import com.ezylang.evalex.parser.Token
import org.bukkit.Bukkit
import org.bukkit.Location
import tororo1066.dungeontower.DungeonTower
import tororo1066.tororopluginapi.SDebug
import tororo1066.tororopluginapi.otherUtils.UsefulUtility
import tororo1066.tororopluginapi.script.ScriptFile
import java.io.File
import java.util.UUID

object FloorScript {

    val scripts = mutableMapOf<String, ScriptFile>()
    val floors = mutableMapOf<UUID, MutableList<Map<String, Any?>>>()

    init {
        ScriptFile.functions["dt_conflict"] = { ConflictFunction(it) }
    }

    fun load() {
        scripts.clear()
        val scriptFolder = File(DungeonTower.plugin.dataFolder, "scripts")
        if (!scriptFolder.exists()) scriptFolder.mkdirs()
        val files = scriptFolder.listFiles() ?: return
        for (file in files) {
            if (file.extension != "tororo") continue
            val scriptFile = ScriptFile(file)
            scripts[file.nameWithoutExtension] = scriptFile
        }
    }

    fun getLoadPriority(script: String): Int {
        val scriptFile = scripts[script] ?: return 0

        return UsefulUtility.sTry({
            Expression("loadPriority()", scriptFile.configuration).evaluate()?.numberValue?.toInt() ?: 0
        }, {
            0
        })
    }

    fun getLabelName(script: String): String? {
        val scriptFile = scripts[script] ?: return null
        return UsefulUtility.sTry({
            Expression("label()", scriptFile.configuration).evaluate()?.stringValue
        }, {
            null
        })
    }

    fun generateSubFloorScript(
        towerName: String,
        floorName: String,
        floorNum: Int,
        uuid: UUID,
        location: Location,
        parent: Map<String, Any?>,
        script: String?,
        baseRotate: Double,
        rotate: Double,
        signRotate: Double,
        generateStep: Int,
        willGenerateFloors: List<String>,
        noGenerateFloors: List<String>,
        generate: Boolean,
        worldInstanceId: Int
    ): Pair<String, Double> {
        script ?: return floorName to rotate
        val scriptFile = scripts[script] ?: return floorName to rotate

        scriptFile.publicVariables.run {
            put("towerName", towerName)
            put("floorName", floorName)
            put("floorNum", floorNum)
            put("location", listOf(location.blockX, location.blockY, location.blockZ))
            put("baseRotate", baseRotate)
            put("rotate", rotate)
            put("signRotate", signRotate)
            put("generateStep", generateStep)
            put("willGenerateFloors", willGenerateFloors)
            put("noGenerateFloors", noGenerateFloors)
            put("generate", generate)
            put("parent", parent.ifEmpty { null })
            put("uuid", uuid.toString())
            put("all", floors[uuid]?: listOf<Map<String, Any?>>())
            put("worldInstanceId", worldInstanceId)
        }
        try {
            val result = scriptFile.start()
            if (result is String) {
                val split = result.split(",")
                return split[0] to (split.getOrNull(1)?.toDouble() ?: rotate)
            }
            if (result is Boolean) return if (result) floorName to rotate else "" to rotate
            return floorName to rotate
        } catch (e: Exception) {
            e.printStackTrace()
            return floorName to rotate
        }
    }

    @FunctionParameter(name = "floorName")
    @FunctionParameter(name = "allowConflictDistanceX")
    @FunctionParameter(name = "allowConflictDistanceY")
    @FunctionParameter(name = "allowConflictDistanceZ")
    @FunctionParameter(name = "worldInstanceId")
    class ConflictFunction(private val scriptFile: ScriptFile): AbstractFunction() {
        @Suppress("UNCHECKED_CAST")
        override fun evaluate(
            expression: Expression,
            functionToken: Token,
            vararg parameterValues: EvaluationValue
        ): EvaluationValue {
            val variable = parameterValues[0].stringValue
            val allowConflictDistanceX = parameterValues[1].numberValue.toInt()
            val allowConflictDistanceY = parameterValues[2].numberValue.toInt()
            val allowConflictDistanceZ = parameterValues[3].numberValue.toInt()
            val worldInstanceId = parameterValues[4].numberValue.toInt()
            val world = Bukkit.getWorld("${DungeonTower.plugin.name.lowercase()}_dungeon_${worldInstanceId}")
            val split = variable.split(",")
            val floorName = split[0]
            val rotate = (split.getOrNull(1)?.toDouble()?:0.0) + (scriptFile.publicVariables["baseRotate"] as? Double?:0.0)
            val floorData = DungeonTower.floorData[floorName]?.newInstance()?:return EvaluationValue(true, expression.configuration)
            val (floorLocX, floorLocY, floorLocZ) = scriptFile.publicVariables["location"] as? List<Int>?:return EvaluationValue(false, expression.configuration)
            SDebug.broadcastDebug("FloorScript", "FloorName: $floorName, Rotate: $rotate, Location: $floorLocX, $floorLocY, $floorLocZ")
            val (dungeonStartLoc, dungeonEndLoc) = floorData.calculateLocation(Location(
                world,
                floorLocX.toDouble(),
                floorLocY.toDouble(),
                floorLocZ.toDouble()
            ), rotate)
            val uuidString = scriptFile.publicVariables["uuid"] as? String?:return EvaluationValue(false, expression.configuration)
            val uuid = UUID.fromString(uuidString)
            val values = floors[uuid]?:return EvaluationValue(false, expression.configuration)
            values.forEach { value ->
                val (startX, startY, startZ) = value["startLoc"] as? List<Int>?:return EvaluationValue(false, expression.configuration)
                val (endX, endY, endZ) = value["endLoc"] as? List<Int>?:return EvaluationValue(false, expression.configuration)
                for (x in dungeonStartLoc.blockX-allowConflictDistanceX..dungeonEndLoc.blockX-allowConflictDistanceX) {
                    for (y in dungeonStartLoc.blockY-allowConflictDistanceY..dungeonEndLoc.blockY-allowConflictDistanceY) {
                        for (z in dungeonStartLoc.blockZ-allowConflictDistanceZ..dungeonEndLoc.blockZ-allowConflictDistanceZ) {
                            if (x in startX..endX && y in startY..endY && z in startZ..endZ) {
                                SDebug.broadcastDebug("FloorScript", "Conflict!")
                                return EvaluationValue(true, expression.configuration)
                            }
                        }
                    }
                }
            }

            return EvaluationValue(false, expression.configuration)
        }
    }
}