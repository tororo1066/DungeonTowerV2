package tororo1066.dungeontower.weaponSystem

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import tororo1066.tororopluginapi.SJavaPlugin

data class Attribute(
    val name: String,
    val displayName: String,
    val icon: String,
    val activeIcon: String,
    val priority: Int = 0,
) {

    companion object {
        val elementAttributes = HashMap<String, Attribute>()
        val weaponAttributes = HashMap<String, Attribute>()

        fun fromYml(section: ConfigurationSection): Attribute {
            return Attribute(
                name = section.getString("name") ?: "unknown",
                displayName = section.getString("displayName") ?: "Unknown",
                icon = section.getString("icon") ?: "undefined",
                activeIcon = section.getString("activeIcon") ?: "undefined",
                priority = section.getInt("priority", 0)
            )
        }

        fun loadAttributes() {
            elementAttributes.clear()
            weaponAttributes.clear()

            val sConfig = SJavaPlugin.sConfig
            sConfig.mkdirs("CustomWeapons/attributes/element")
            sConfig.loadAllFiles("CustomWeapons/attributes/element").forEach { file ->
                if (file.extension != "yml") return@forEach
                val yml = YamlConfiguration.loadConfiguration(file)
                yml.getKeys(false).forEach { key ->
                    val section = yml.getConfigurationSection(key)
                    if (section != null) {
                        val attribute = fromYml(section)
                        elementAttributes[attribute.name] = attribute
                    }
                }
            }

            sConfig.mkdirs("CustomWeapons/attributes/weapon")
            sConfig.loadAllFiles("CustomWeapons/attributes/weapon").forEach { file ->
                if (file.extension != "yml") return@forEach
                val yml = YamlConfiguration.loadConfiguration(file)
                yml.getKeys(false).forEach { key ->
                    val section = yml.getConfigurationSection(key)
                    if (section != null) {
                        val attribute = fromYml(section)
                        weaponAttributes[attribute.name] = attribute
                    }
                }
            }
        }

        fun createElementAttributeString(enabledAttributes: List<Attribute>): String {
            return elementAttributes.values
                .sortedBy { it.priority }
                .joinToString(" ") { attribute ->
                    if (enabledAttributes.contains(attribute)) {
                        attribute.activeIcon
                    } else {
                        attribute.icon
                    }
                }.ifEmpty { "なし" }
        }

        fun createWeaponAttributeString(enabledAttributes: List<Attribute>): String {
            return weaponAttributes.values
                .sortedBy { it.priority }
                .joinToString(" ") { attribute ->
                    if (enabledAttributes.contains(attribute)) {
                        attribute.activeIcon
                    } else {
                        attribute.icon
                    }
                }.ifEmpty { "なし" }
        }
    }
}