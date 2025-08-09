package tororo1066.dungeontower.dmonitor

import tororo1066.displaymonitorapi.actions.ActionResult
import tororo1066.displaymonitorapi.actions.IActionContext
import tororo1066.displaymonitorapi.configuration.IAdvancedConfigurationSection

class SetScoreboardLine: AbstractDungeonAction() {

    var title: String? = null
    val addLines = mutableListOf<String>()
    val removeLines = mutableListOf<Regex>()
    val setLines = mutableMapOf<Int,String>()

    override fun run(context: IActionContext): ActionResult {
        return context.partyAction { party ->
            val nowTask = party.currentTask ?: return@partyAction ActionResult.noParameters("Current Task not found")
            val target = context.target ?: return@partyAction ActionResult.targetRequired()
            title?.let {
                nowTask.scoreboardTitle = it
            }
            val scoreboardFormats = nowTask.scoreboardFormats.computeIfAbsent(target.uniqueId) { arrayListOf() }
            removeLines.forEach {
                scoreboardFormats.removeIf { line -> it.matches(line) }
            }
            addLines.forEach {
                scoreboardFormats.add(it)
            }
            setLines.forEach { (index, line) ->
                if (index < scoreboardFormats.size) {
                    scoreboardFormats[index] = line
                } else {
                    while (scoreboardFormats.size < index) {
                        scoreboardFormats.add(" ")
                    }
                    scoreboardFormats.add(line)
                }
            }
            ActionResult.success()
        }
    }

    override fun prepare(section: IAdvancedConfigurationSection) {
        title = section.getString("title")
        addLines.addAll(section.getStringList("addLines"))
        removeLines.addAll(section.getStringList("removeLines").map { it.toRegex() })
        section.getConfigurationSection("setLines")?.let { setSection ->
            setSection.getKeys(false).forEach { key ->
                setLines[key.toInt()] = setSection.getString(key,"")!!
            }
        }
    }
}