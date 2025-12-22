package tororo1066.dungeontower.weaponSystem

import com.elmakers.mine.bukkit.magic.Mage
import com.elmakers.mine.bukkit.wand.Wand
import org.bukkit.Bukkit
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import tororo1066.dungeontower.DungeonTower

class CustomWeapon private constructor(val wand: Wand) {

    var level: Int = 1
        private set
    var currentMaxLevel: Int = 1
        private set

    val levelData = HashMap<IntRange, LevelData>()
    val maxLevel: Int
        get() = levelData.keys.maxOfOrNull { it.last } ?: 1

    var onInitialize: String? = null
        private set


    companion object {
        fun getWeapon(wand: Wand): CustomWeapon? {
            return if (wand.getBoolean("custom_weapon")) {
                CustomWeapon(wand)
            } else {
                null
            }
        }

        fun getOrCreateWeapon(wand: Wand): CustomWeapon? {
            if (canCreateWeapon(wand)) {
                return CustomWeapon(wand)
            }
            return null
        }

        private fun canCreateWeapon(wand: Wand): Boolean {
            val template = wand.template ?: return false
            val section = template.getConfigurationSection("custom_weapon")
            return section != null
        }
    }

    init {
        val template = wand.template
        template?.getSectionList("custom_weapon.levels")?.forEach { section ->
            val levelData = LevelData.fromYml(section)
            this.levelData[levelData.levelRange] = levelData
        }

        val isCreated = wand.getBoolean("custom_weapon")
        if (!isCreated) {

            val section = YamlConfiguration()
            section.set("custom_weapon", true)
            section.set("level", 1)
            section.set("current_max_level", 1)

            val lore = wand.getStringList("lore")?.toMutableList() ?: mutableListOf()
            lore.addAll(getWeaponLore(lineBreak = true))

            section.set("lore", lore)

            wand.upgrade(section)
        } else {
            level = wand.getInt("level")
            currentMaxLevel = wand.getInt("current_max_level")
        }

    }

    fun setLevel(level: Int, p: Player) {
        this.level = level
        val mage = DungeonTower.magicAPI.controller.getMage(p) as Mage
        wand.mage = mage
        wand.configure("level", level)
        val initializeSpell = DungeonTower.magicAPI.controller.getSpellTemplate(onInitialize)
            ?.createMageSpell(mage)
        initializeSpell?.cast()
        val currentLevelData = levelData.entries.firstOrNull { it.key.contains(level) }?.value
        if (currentLevelData != null) {
            val onUpgradeSpell = currentLevelData.getOnUpgradeSpell(mage)
            onUpgradeSpell?.cast()
        }

        updateLore()

        wand.mage = null
    }

    fun getCurrentLevelData(): LevelData? {
        return levelData.entries.firstOrNull { it.key.contains(level) }?.value
    }

    fun getNextLevelData(): LevelData? {
        return levelData.entries.firstOrNull { it.key.contains(level + 1) }?.value
    }

    fun updateLore() {
        val lore = wand.getStringList("lore")?.toMutableList() ?: mutableListOf()
        val weaponLore = getWeaponLore(lineBreak = false)
        if (lore.size >= 3) {
            lore[lore.size - 3] = weaponLore[0]
            lore[lore.size - 2] = weaponLore[1]
            lore[lore.size - 1] = weaponLore[2]
        } else {
            lore.addAll(weaponLore)
        }
        wand.configure("lore", lore)
    }

    fun getWeaponLore(lineBreak: Boolean): List<String> {
        val currentLevelData = getCurrentLevelData()

        val loreList = mutableListOf<String>()
        if (lineBreak) {
            loreList.add("")
        }

        if (currentLevelData != null) {
            loreList.addAll(
                listOf(
                    "§aレベル: §d$level",
                    "§7属性: ${Attribute.createElementAttributeString(currentLevelData.elementAttributes)}",
                    "§7武器属性: ${Attribute.createWeaponAttributeString(currentLevelData.weaponAttributes)}"
                )
            )
        } else {
            loreList.addAll(
                listOf(
                    "§aレベル: §d$level",
                    "§7属性: なし",
                    "§7武器属性: なし"
                )
            )
        }

        return loreList
    }
}