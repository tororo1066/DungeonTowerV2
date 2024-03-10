package tororo1066.dungeontower.script

import com.ezylang.evalex.Expression
import com.ezylang.evalex.parser.Token
import org.bukkit.Bukkit
import tororo1066.dungeontower.DungeonTower
import tororo1066.tororopluginapi.otherUtils.UsefulUtility
import tororo1066.tororopluginapi.script.ScriptFile
import java.io.File

object FloorScript {

    val scripts = mutableMapOf<String, ScriptFile>()

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
        script: String?,
        rotate: Double,
        willGenerateFloors: List<String>,
        noGenerateFloors: List<String>,
        generate: Boolean
    ): Pair<String, Double> {
        script ?: return floorName to rotate
        val scriptFile = scripts[script] ?: return floorName to rotate
        scriptFile.debug = true

        scriptFile.publicVariables.run {
            put("towerName", towerName)
            put("floorName", floorName)
            put("rotate", rotate)
            put("willGenerateFloors", willGenerateFloors)
            put("noGenerateFloors", noGenerateFloors)
            put("generate", generate)
        }
        val result = scriptFile.start()
        if (result is String) {
            val split = result.split(",")
            return split[0] to (split.getOrNull(1)?.toDouble() ?: rotate)
        }
        if (result is Boolean) return if (result) floorName to rotate else "" to rotate
        return floorName to rotate
    }
}