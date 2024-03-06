package tororo1066.dungeontower.skilltree

import org.bukkit.entity.Player
import tororo1066.dungeontower.data.TowerData
import tororo1066.dungeontower.skilltree.parks.convenience.*

class ConvenienceGUI(p: Player, towerData: TowerData): ActionBarBaseGUI(p, towerData, SkillMenu.CONVENIENCE.char) {

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