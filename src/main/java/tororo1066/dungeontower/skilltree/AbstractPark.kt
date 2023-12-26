package tororo1066.dungeontower.skilltree

import org.bukkit.entity.Player
import tororo1066.dungeontower.DungeonTower
import tororo1066.tororopluginapi.sEvent.SEvent

abstract class AbstractPark(val category: String, val texture: Skill) {

    val sEvent = SEvent(DungeonTower.plugin)

    abstract fun getLocation(): ParkLocation

    abstract fun getSkillName(): String

    abstract fun getSkillDescription(): List<String>

    open fun registerSkill(p: Player) {}

    open fun onAction(p: Player, action: ActionType) {}

}