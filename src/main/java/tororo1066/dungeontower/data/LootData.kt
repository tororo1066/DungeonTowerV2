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
import java.util.*

class LootData: LootTable, Cloneable {

    var includeName = ""
    val items = ArrayList<Triple<Int,IntRange, SItem>>()//確率、個数、ItemStack
    var rollAmount = 0
    var displayName = ""

    override fun populateLoot(random: Random, context: LootContext): MutableCollection<ItemStack> {

        val returnItems = ArrayList<ItemStack>()

        first@
        for (i in 0..rollAmount) {
            val randomNum = random.nextInt(999999) + 1
            var preventRandom = 0
            for (item in items){
                if (preventRandom < randomNum && item.first + preventRandom > randomNum){
                    val sumAmount = item.second.random()
                    val stackAmount = sumAmount / 64
                    val amount = sumAmount % 64
                    val dungeonItem = item.third.clone().setCustomData(
                        DungeonTower.plugin,"dlootitem",
                        PersistentDataType.STRING,"dlootitem")
                    (1..stackAmount).forEach { _ ->
                        returnItems.add(dungeonItem.clone().setItemAmount(64))
                    }
                    returnItems.add(dungeonItem.clone().setItemAmount(amount))
                    continue@first
                }
                preventRandom = item.first
            }
        }
        return returnItems
    }

    override fun fillInventory(inventory: Inventory, random: Random, context: LootContext) {
        var items = populateLoot(random, context).toMutableList()
        for (i in items.size until inventory.size){
            items.add(SItem(Material.BARRIER).setCustomData(DungeonTower.plugin,"dloot", PersistentDataType.STRING,"space"))
        }
        items = items.shuffled(random).toMutableList()
        for (item in items.withIndex()){
            if (item.value.itemMeta.persistentDataContainer.has(NamespacedKey(DungeonTower.plugin,"dloot"),
                    PersistentDataType.STRING)) items[item.index] = ItemStack(Material.AIR)
        }
        inventory.contents = items.toTypedArray()
    }

    override fun getKey(): NamespacedKey {
        return NamespacedKey(DungeonTower.plugin,includeName)
    }

    public override fun clone(): LootData {
        return super.clone() as LootData
    }

    companion object{
        @Suppress("UNCHECKED_CAST")
        fun loadFromYml(file: File): Pair<String, LootData> {
            val yml = YamlConfiguration.loadConfiguration(file)
            val data = LootData()
            data.includeName = file.nameWithoutExtension
            data.rollAmount = yml.getInt("roll")
            data.displayName = yml.getString("displayName")?:"DungeonTowerLootChest"
            val items = yml.getList("items") as List<ItemStack>
            val amounts = yml.getStringList("amounts").map { it.split("to")[0].toInt()..it.split("to")[1].toInt() }
            val chances = yml.getIntegerList("chances")
            for (i in items.indices){
                data.items.add(Triple(chances[i],amounts[i], SItem(items[i])))
            }

            return Pair(data.includeName, data)
        }
    }
}