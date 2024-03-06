package tororo1066.dungeontower.skilltree

import org.bukkit.entity.Player
import tororo1066.dungeontower.DungeonTower
import tororo1066.dungeontower.data.UserData
import tororo1066.tororopluginapi.sEvent.SEvent

abstract class AbstractPark(
    val category: String,
    val texture: Skill,
    val cost: Int = 0,
    val needParks: List<List<Class<out AbstractPark>>> = listOf(),
    val blockedBy: List<Class<out AbstractPark>> = listOf()
) : Cloneable {

    val sEvent = SEvent(DungeonTower.plugin)

    abstract fun getLocation(): ParkLocation

    abstract fun getSkillName(): String

    abstract fun getSkillDescription(): List<String>

    open fun onLearned(p: Player) {}

    open fun onUnlearned(p: Player) {}

    open fun registerPark(p: Player, userData: UserData) {}

    open fun onAction(p: Player, action: ActionType, userData: UserData) {}

    fun getNeedParkNames(): List<List<String>> {
        return needParks.map { map -> map.map { it.getConstructor().newInstance().getSkillName() }  }
    }

    fun getBlockedParkNames(): List<String> {
        return blockedBy.map { it.getConstructor().newInstance().getSkillName() }
    }

    public override fun clone(): AbstractPark {
        return super.clone() as AbstractPark
    }

}