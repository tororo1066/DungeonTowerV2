package tororo1066.dungeontower.data

import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.loot.LootContext
import org.bukkit.loot.LootTable
import org.bukkit.persistence.PersistentDataType
import tororo1066.dungeontower.DungeonTower
import tororo1066.tororopluginapi.sItem.SItem
import java.io.File
import java.util.Random

class LootData: LootTable, Cloneable {

    var internalName = ""
    var items = ArrayList<Triple<Int, IntProgression, String>>()
    var rollAmount = 0
    var displayName = ""

    companion object {
        fun loadFromYml(file: File): Pair<String, LootData> {
            val yml = YamlConfiguration.loadConfiguration(file)
            val lootData = LootData().apply {
                internalName = file.nameWithoutExtension
                displayName = yml.getString("displayName")?:"DungeonTowerLootChest"
                rollAmount = yml.getInt("roll")
                val configItems = yml.getStringList("items")
                val amounts = yml.getStringList("amounts").map { it.split("to")[0].toInt()..it.split("to")[1].toInt() }
                val chances = yml.getIntegerList("chances")
                for (i in configItems.indices){
                    items.add(Triple(chances[i], amounts[i], (DungeonTower.lootItemData[configItems[i]]?:continue).internalName))
                }
            }
            return Pair(file.nameWithoutExtension, lootData)
        }
    }

    override fun populateLoot(random: Random?, context: LootContext): MutableCollection<ItemStack> {
        val returnItems = ArrayList<ItemStack>()
        first@
        for (i in 0..rollAmount) {
            val randomNum = (random?: Random()).nextInt(999999) + 1
            var preventRandom = 0
            for (item in items){
                if (preventRandom < randomNum && item.first + preventRandom > randomNum){
                    val sumAmount = item.second.toList().random()
                    val stackAmount = sumAmount / 64
                    val amount = sumAmount % 64
                    val lootItemData = DungeonTower.lootItemData[item.third]?:continue
                    val dungeonItem = lootItemData.itemStack.clone().apply {
                        setCustomData(
                            DungeonTower.plugin,"dlootitem",
                            PersistentDataType.INTEGER,0
                        )
                        if (lootItemData.announce) {
                            setCustomData(
                                DungeonTower.plugin,"dlootannounce",
                                PersistentDataType.INTEGER,0
                            )
                        }
                        if (lootItemData.removeFloorCount > 0) {
                            setCustomData(
                                DungeonTower.plugin,"dlootremovefloor",
                                PersistentDataType.INTEGER,lootItemData.removeFloorCount
                            )
                        }
                        if (lootItemData.removeOnExit) {
                            setCustomData(
                                DungeonTower.plugin,"dlootremoveonexit",
                                PersistentDataType.INTEGER,0
                            )
                        }
                        if (lootItemData.removeOnDeath) {
                            setCustomData(
                                DungeonTower.plugin,"dlootremoveondeath",
                                PersistentDataType.INTEGER,0
                            )
                        }
                    }
                    (1..stackAmount).forEach { _ ->
                        returnItems.add(dungeonItem.clone().setItemAmount(64).build())
                    }
                    returnItems.add(dungeonItem.clone().setItemAmount(amount).build())
                    continue@first
                }
                preventRandom += item.first
            }
        }
        return returnItems
    }

    override fun fillInventory(inventory: Inventory, random: Random?, context: LootContext) {
        var items = populateLoot(random, context).toMutableList()
        for (i in items.size until inventory.size){
            items.add(SItem(Material.BARRIER).setCustomData(DungeonTower.plugin,"dloot", PersistentDataType.STRING,"space").build())
        }
        items = items.shuffled(random?:Random()).toMutableList()
        for (item in items.withIndex()){
            if (item.value.itemMeta.persistentDataContainer.has(
                    NamespacedKey(DungeonTower.plugin,"dloot"),
                    PersistentDataType.STRING)) items[item.index] = ItemStack(Material.AIR)
        }
        inventory.contents = items.toTypedArray()
    }

    override fun getKey(): NamespacedKey {
        return NamespacedKey(DungeonTower.plugin,internalName)
    }

    public override fun clone(): LootData {
        return (super.clone() as LootData).apply {
            items = ArrayList(items.map { Triple(it.first, it.second, it.third) })
        }
    }
}