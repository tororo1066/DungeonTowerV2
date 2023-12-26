package tororo1066.dungeontower.skilltree

import org.bukkit.entity.Player
import tororo1066.dungeontower.skilltree.parks.convenience.*

class ConvenienceGUI(p: Player): ActionBarBaseGUI(p, SkillMenu.CONVENIENCE.char) {

    init {
        registerItems(
            JustGuard(),
            OnceHeal()
        )
    }
}