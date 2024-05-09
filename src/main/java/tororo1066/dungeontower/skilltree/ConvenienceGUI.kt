package tororo1066.dungeontower.skilltree

import org.bukkit.entity.Player
import tororo1066.dungeontower.data.TowerData
import tororo1066.dungeontower.skilltree.perks.convenience.*

class ConvenienceGUI(p: Player, towerData: TowerData, resolution: String): ActionBarBaseGUI(p, towerData, SkillMenu.CONVENIENCE.char, resolution) {

    init {
        registerItems(
            JustGuard(),
            OnceHeal(),
            HealSkill(),
            NoDamageTicks(),
            BuffSkill(),
            HealSkillUpgrade(),
            JustGuardDurationUp(),
            DamageReduceOnSneaking(),
            EnterFloorAbsorption(),
            SpeedUp(),
            OnceHealAmountUp(),
            BuffSkillUpgrade()
        )
    }
}