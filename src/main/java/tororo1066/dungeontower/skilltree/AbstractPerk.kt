package tororo1066.dungeontower.skilltree

import org.bukkit.entity.Player
import tororo1066.dungeontower.DungeonTower
import tororo1066.dungeontower.data.UserData
import tororo1066.tororopluginapi.sEvent.SEvent

abstract class AbstractPerk(
    val category: String,
    val texture: Skill,
    val cost: Int = 0,
    val needPerks: List<List<Class<out AbstractPerk>>> = listOf(),
    val blockedBy: List<Class<out AbstractPerk>> = listOf()
) : Cloneable {

    val sEvent = SEvent(DungeonTower.plugin)

    abstract fun getLocation(): Map<String, PerkLocation>

    abstract fun getSkillName(): String

    abstract fun getSkillDescription(): List<String>

    open fun onLearned(p: Player) {}

    open fun onUnlearned(p: Player) {}

    open fun registerPerk(p: Player, userData: UserData) {}

    open fun onAction(p: Player, action: ActionType, userData: UserData) {}

    fun getNeedPerkNames(): List<List<String>> {
        return needPerks.map { map -> map.map { it.getConstructor().newInstance().getSkillName() }  }
    }

    fun getBlockedPerkNames(): List<String> {
        return blockedBy.map { it.getConstructor().newInstance().getSkillName() }
    }

    public override fun clone(): AbstractPerk {
        return super.clone() as AbstractPerk
    }

    companion object {
        fun getPerk(category: String, name: String): AbstractPerk {
            val clazz = Class.forName("tororo1066.dungeontower.skilltree.perks.$category.$name")
            return clazz.getConstructor().newInstance() as AbstractPerk
        }
    }
}