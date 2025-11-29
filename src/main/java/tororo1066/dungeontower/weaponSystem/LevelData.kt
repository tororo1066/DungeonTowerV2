package tororo1066.dungeontower.weaponSystem

import com.elmakers.mine.bukkit.api.magic.Mage
import com.elmakers.mine.bukkit.api.spell.Spell
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.inventory.ItemStack
import tororo1066.dungeontower.DungeonTower

/*
yml
range: 1-100
element_attributes:
  - fire
  - ice
weapon_attributes:
  - damage
  - speed
  - critical
upgrade_item: custom_item_name
on_upgrade: custom_spell_name
 */
class LevelData {
    var levelRange: IntRange = 0..0
    var elementAttributes: List<Attribute> = emptyList()
    var weaponAttributes: List<Attribute> = emptyList()
    var levelScale = 1.0
    var upgradeItem: String? = null
    var onUpgrade: String? = null

    fun getUpgradeItem(): ItemStack? {
        return DungeonTower.magicAPI.controller.getItem(upgradeItem ?: return null)?.itemStack
    }

    fun getOnUpgradeSpell(mage: Mage): Spell? {
        return DungeonTower.magicAPI.controller.getSpellTemplate(onUpgrade ?: return null)?.createMageSpell(mage)
    }

    companion object {
        fun fromYml(section: ConfigurationSection): LevelData {
            val levelData = LevelData()
            levelData.levelRange = section.getString("range")?.split("..")?.let {
                if (it.size == 2) {
                    it[0].toInt()..it[1].toInt()
                } else {
                    0..0 // Default range if parsing fails
                }
            } ?: 0..0
            levelData.elementAttributes = section.getStringList("element_attributes").mapNotNull { Attribute.elementAttributes[it] }
            levelData.weaponAttributes = section.getStringList("weapon_attributes").mapNotNull { Attribute.weaponAttributes[it] }

            levelData.levelScale = section.getDouble("scale", 1.0)

            levelData.upgradeItem = section.getString("upgrade_item")

            levelData.onUpgrade = section.getString("on_upgrade")

            return levelData
        }
    }
}