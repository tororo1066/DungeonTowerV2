package tororo1066.dungeontower.weaponSystem.magic

import com.elmakers.mine.bukkit.action.CompoundAction
import com.elmakers.mine.bukkit.api.action.CastContext
import com.elmakers.mine.bukkit.api.spell.SpellResult
import com.elmakers.mine.bukkit.wand.Wand
import tororo1066.dungeontower.weaponSystem.CustomWeapon

class AppendDungeonLore: CompoundAction() {

    override fun start(context: CastContext): SpellResult {
        val wand = context.wand as? Wand ?: return SpellResult.NO_TARGET
        val customWeapon = CustomWeapon.getWeapon(wand) ?: return SpellResult.NO_TARGET
        val lore = wand.getStringList("lore")?.toMutableList() ?: mutableListOf()
        lore.addAll(customWeapon.getWeaponLore(lineBreak = true))
        wand.setProperty("lore", lore)
        return SpellResult.CAST
    }
}