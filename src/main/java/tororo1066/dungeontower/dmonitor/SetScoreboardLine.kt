package tororo1066.dungeontower.dmonitor

import tororo1066.displaymonitor.actions.AbstractAction
import tororo1066.displaymonitor.actions.ActionContext
import tororo1066.displaymonitor.actions.ActionResult
import tororo1066.displaymonitor.configuration.AdvancedConfigurationSection
import tororo1066.dungeontower.DungeonTower
import java.util.UUID

class SetScoreboardLine: AbstractAction() {

    val addLines = mutableListOf<String>()
    val removeLines = mutableListOf<Regex>()
    val setLines = mutableMapOf<Int,String>()

    override fun run(context: ActionContext): ActionResult {
        val parameters = context.configuration?.parameters ?: return ActionResult.noParameters("Parameters not found")
        val uuid = UUID.fromString((parameters["party.uuid"] ?: return ActionResult.noParameters("Party UUID not found")) as String)
        val party = DungeonTower.partiesData.entries.find { it.value?.partyUUID == uuid }?.value ?: return ActionResult.noParameters("Party not found")
        val nowTask = party.nowTask ?: return ActionResult.noParameters("Current Task not found")
        val target = context.target ?: return ActionResult.targetRequired()
        val scoreboardFormats = nowTask.scoreboardFormats.computeIfAbsent(target.uniqueId) { arrayListOf() }
        removeLines.forEach {
            scoreboardFormats.removeIf { line -> it.matches(line) }
        }
        addLines.forEach {
            scoreboardFormats.add(it)
        }
        setLines.forEach { (index, line) ->
            if (index < scoreboardFormats.size){
                scoreboardFormats[index] = line
            } else {
                while (scoreboardFormats.size < index){
                    scoreboardFormats.add(" ")
                }
                scoreboardFormats.add(line)
            }
        }

        return ActionResult.success()
    }

    override fun prepare(section: AdvancedConfigurationSection) {
        addLines.addAll(section.getStringList("addLines"))
        removeLines.addAll(section.getStringList("removeLines").map { it.toRegex() })
        section.getConfigurationSection("setLines")?.let { setSection ->
            setSection.getKeys(false).forEach { key ->
                setLines[key.toInt()] = setSection.getString(key,"")!!
            }
        }
    }
}