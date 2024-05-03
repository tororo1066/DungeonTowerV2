package tororo1066.dungeontower.data

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.inventory.ItemStack
import tororo1066.tororopluginapi.sItem.SItem
import tororo1066.tororopluginapi.sItem.SItem.Companion.toSItem
import java.io.File

class LootItemData(): Cloneable {
    var internalName: String = ""
    lateinit var itemStack: SItem
    var announce: Boolean = false
    var removeFloorCount: Int = 0
    var removeOnExit: Boolean = false
    var removeOnDeath: Boolean = true

    constructor(
        internalName: String,
        itemStack: SItem,
        announce: Boolean = false,
        removeFloorCount: Int = 0,
        removeOnExit: Boolean = false,
        removeOnDeath: Boolean = true
    ) : this() {
        this.internalName = internalName
        this.itemStack = itemStack
        this.announce = announce
        this.removeFloorCount = removeFloorCount
        this.removeOnExit = removeOnExit
        this.removeOnDeath = removeOnDeath
    }

    companion object {
        fun loadFromYml(file: File): Pair<String, LootItemData> {
            val yml = YamlConfiguration.loadConfiguration(file)
            val itemStack = yml.getItemStack("itemStack")!!.toSItem()
            val announce = yml.getBoolean("announce")
            val removeFloorCount = yml.getInt("removeFloorCount")
            val removeOnExit = yml.getBoolean("removeOnExit")
            val removeOnDeath = yml.getBoolean("removeOnDeath", true)
            return Pair(file.nameWithoutExtension, LootItemData(
                file.nameWithoutExtension,
                itemStack,
                announce,
                removeFloorCount,
                removeOnExit,
                removeOnDeath
            ))
        }
    }

    public override fun clone(): LootItemData {
        return LootItemData(
            internalName,
            itemStack.clone(),
            announce,
            removeFloorCount,
            removeOnExit,
            removeOnDeath
        )
    }
}