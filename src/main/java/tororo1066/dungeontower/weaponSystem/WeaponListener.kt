package tororo1066.dungeontower.weaponSystem

import com.elmakers.mine.bukkit.api.event.PreCastEvent
import com.elmakers.mine.bukkit.api.event.WandActivatedEvent
import com.elmakers.mine.bukkit.wand.Wand
import tororo1066.dungeontower.DungeonTower
import tororo1066.tororopluginapi.annotation.SEventHandler

class WeaponListener {

    @SEventHandler
    fun onCast(e: PreCastEvent) {
        if (!DungeonTower.customWeaponEnabled) return
        val currentCast = e.spell.currentCast
        val wand = currentCast.wand as? Wand ?: return
        val customWeapon = CustomWeapon.getWeapon(wand) ?: return
        currentCast.variables.set("custom_weapon_level", customWeapon.level)
    }

    @SEventHandler
    fun onActivate(e: WandActivatedEvent) {
        if (!DungeonTower.customWeaponEnabled || !DungeonTower.autoCreateCustomWeapon) return
        val wand = e.wand as? Wand ?: return
        CustomWeapon.getOrCreateWeapon(wand)
    }
}