package tororo1066.dungeontower.create

import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import tororo1066.dungeontower.DungeonTower
import tororo1066.dungeontower.DungeonTower.Companion.sendPrefixMsg
import tororo1066.dungeontower.command.DungeonCommand
import tororo1066.dungeontower.data.LootData
import tororo1066.tororopluginapi.SJavaPlugin
import tororo1066.tororopluginapi.SStr
import tororo1066.tororopluginapi.defaultMenus.LargeSInventory
import tororo1066.tororopluginapi.sInventory.SInventoryItem
import tororo1066.tororopluginapi.sItem.SItem
import java.io.File

class CreateLoot(val data: LootData, val isEdit: Boolean): LargeSInventory(SJavaPlugin.plugin, data.internalName) {

    override fun renderMenu(p: Player): Boolean {
        val items = arrayListOf(
            createInputItem(SItem(Material.GRASS_BLOCK).setDisplayName("§a表示名を設定する")
                .addLore("§d現在の値:§c${data.displayName}"),String::class.java) { str, _ ->
                data.displayName = str
            },
            createInputItem(SItem(Material.CLOCK).setDisplayName("§a抽選回数を設定する").addLore("§d現在の値:§c${data.rollAmount}"),Int::class.java){ int, _ ->
                data.rollAmount = int
            },
            SInventoryItem(Material.CHEST).setDisplayName("§a中身を設定する").setCanClick(false).setClickEvent { _ ->
                val inv = object : LargeSInventory(DungeonTower.plugin,"§a中身を設定する") {
                    override fun renderMenu(p: Player): Boolean {
                        val items = arrayListOf<SInventoryItem>()
                        data.items.forEach {
                            items.add(SInventoryItem(it.third)
                                .addLore("§a確率:${it.first}/1000000,§b個数:${it.second.first}..${it.second.last}",
                                    "§6持ち帰れたときのアナウンス:${it.third.getCustomData(DungeonTower.plugin,"dannouncementitem",
                                        PersistentDataType.INTEGER) != null}",
                                    "§cシフト左クリックで削除").setCanClick(false).setClickEvent second@ { e ->
                                if (e.click != ClickType.SHIFT_LEFT)return@second
                                data.items.remove(it)
                                allRenderMenu(p)
                            })
                        }
                        items.add(createInputItem(SItem(Material.EMERALD_BLOCK).setDisplayName("§a追加")
                            .addLore("§a合計の確率:${data.items.sumOf { it.first }}/1000000"),
                            Int::class.java,"§d確率を設定してください(手に登録するアイテムを持ってください)",true) { int, _ ->
                            val item = p.inventory.itemInMainHand
                            if (item.type.isAir){
                                p.sendPrefixMsg(SStr("§c手にアイテムを持ってください！"))
                                open(p)
                                return@createInputItem
                            }

                            DungeonTower.sInput.sendInputCUI(p,IntProgression::class.java,"§d個数を入力してください(<最低>..<最高>)") { intRange ->
                                DungeonTower.sInput.sendInputCUI(p,Boolean::class.java,
                                    "§d持ち帰れたときにアナウンスするか決めてください(true/false)") { bool ->
                                    val sItem = SItem(item.clone()).apply {
                                        if (bool){
                                            setCustomData(DungeonTower.plugin,"dannouncementitem",
                                                PersistentDataType.INTEGER,1)
                                        }
                                    }
                                    data.items.add(Triple(int,intRange, sItem))
                                    open(p)
                                }
                            }
                        })
                        setResourceItems(items)
                        return true
                    }
                }
                moveChildInventory(inv, p)
            }
        )

        if (isEdit){
            items.add(SInventoryItem(Material.WRITABLE_BOOK).setDisplayName("§a上書き保存する").setCanClick(false).setClickEvent { _ ->
                save(p)
                p.closeInventory()
            })
            items.add(SInventoryItem(Material.BARRIER).setDisplayName("§cシフト右クリックで削除する").setClickEvent {
                if (it.click != ClickType.SHIFT_RIGHT)return@setClickEvent
                File(SJavaPlugin.plugin.dataFolder.path + "/loots/${data.internalName}.yml").delete()
                DungeonTower.lootData.remove(data.internalName)
                DungeonCommand()
                p.sendPrefixMsg(SStr("&a削除しました"))
                p.closeInventory()
            })
        } else {
            items.add(SInventoryItem(Material.WRITABLE_BOOK).setDisplayName("§a保存する").setCanClick(false).setClickEvent { _ ->
                save(p)
                p.closeInventory()
            })
        }

        setResourceItems(items)
        return true
    }

    private fun save(p: Player){
        val config = SJavaPlugin.sConfig.getConfig("loots/${data.internalName}")?: YamlConfiguration()
        config.set("roll",data.rollAmount)
        val itemStacks = ArrayList<ItemStack>()
        val chances = ArrayList<Int>()
        val amounts = ArrayList<String>()
        data.items.forEach { triple ->
            itemStacks.add(ItemStack(triple.third))
            chances.add(triple.first)
            amounts.add("${triple.second.first}to${triple.second.last}")
        }
        config.set("displayName",data.displayName)
        config.set("items",itemStacks)
        config.set("chances",chances)
        config.set("amounts",amounts)
        if (SJavaPlugin.sConfig.saveConfig(config,"loots/${data.internalName}")){
            DungeonTower.lootData[data.internalName] = data
            DungeonCommand()
            p.sendPrefixMsg(SStr("&a保存に成功しました"))
        } else {
            p.sendPrefixMsg(SStr("&c保存に失敗しました"))
        }
    }
}