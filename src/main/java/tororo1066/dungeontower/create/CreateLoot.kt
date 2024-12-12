package tororo1066.dungeontower.create

import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
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

class CreateLoot(val data: LootData, val isEdit: Boolean): LargeSInventory(data.internalName) {
    override fun renderMenu(p: Player): Boolean {
        val items = arrayListOf(
            createInputItem(
                SItem(Material.GRASS_BLOCK).setDisplayName("§a表示名を設定する")
                    .addLore("§d現在の値:§c${data.displayName}"), String::class.java
            ) { str, _ ->
                data.displayName = str
            },
            createInputItem(
                SItem(Material.CLOCK).setDisplayName("§a抽選回数を設定する").addLore("§d現在の値:§c${data.rollAmount}"),
                Int::class.java
            ) { int, _ ->
                data.rollAmount = int
            },
            SInventoryItem(Material.CHEST).setDisplayName("§a中身を設定する").setCanClick(false).setClickEvent { _ ->
                val inv = object : LargeSInventory(data.internalName) {
                    override fun renderMenu(p: Player): Boolean {
                        val items = arrayListOf<SInventoryItem>()

                        data.items.forEach {
                            val lootItemData = DungeonTower.lootItemData[it.third]?:return@forEach
                            items.add(SInventoryItem(lootItemData.itemStack)
                                .addLore(
                                    "§a確率: ${it.first}/1000000,§b個数: ${it.second.first}..${it.second.last}",
                                    "§6内部名: ${lootItemData.internalName}",
                                    "§cシフト左クリックで削除"
                                ).setCanClick(false).setClickEvent second@ { e ->
                                    if (e.click != ClickType.SHIFT_LEFT) return@second
                                    data.items.remove(it)
                                    allRenderMenu(p)
                                })
                        }

                        items.add(
                            createInputItem(
                                SItem(Material.EMERALD_BLOCK).setDisplayName("§a追加")
                                    .addLore("§a合計の確率: ${data.items.sumOf { it.first }}/1000000"),
                                String::class.java, "§dアイテムの内部名を入力してください", invOpenCancel = true, action = { str, _ ->
                                    val lootItemData = DungeonTower.lootItemData[str]
                                    if (lootItemData == null) {
                                        p.sendMessage("§cその内部名のアイテムは存在しません")
                                        open(p)
                                        return@createInputItem
                                    }

                                    DungeonTower.sInput.sendInputCUI(
                                        p, Int::class.java, "§d確率を設定してください(0~1000000)",
                                        action = { int ->
                                            DungeonTower.sInput.sendInputCUI(
                                                p, IntProgression::class.java, "§d個数を入力してください(<最低>..<最高>)",
                                                action = { intRange ->
                                                    data.items.add(Triple(int, intRange, lootItemData.internalName))
                                                    open(p)
                                                }
                                            )
                                        }
                                    )
                                }
                            )
                        )

                        setResourceItems(items)
                        return true
                    }
                }

                moveChildInventory(inv, p)
            }
        )

        if (isEdit) {
            items.add(
                SInventoryItem(Material.WRITABLE_BOOK).setDisplayName("§a上書き保存する").setCanClick(false).setClickEvent { _ ->
                    save(p)
                    p.closeInventory()
                }
            )
            items.add(
                SInventoryItem(Material.BARRIER).setDisplayName("§cシフト右クリックで削除する").setClickEvent {
                    if (it.click != ClickType.SHIFT_RIGHT) return@setClickEvent
                    DungeonTower.lootData.remove(data.internalName)
                    File(DungeonTower.plugin.dataFolder, "loots/${data.internalName}.yml").delete()
                    DungeonCommand()
                    p.sendMessage("§a削除しました")
                    p.closeInventory()
                }
            )
        } else {
            items.add(
                SInventoryItem(Material.WRITABLE_BOOK).setDisplayName("§a保存する").setCanClick(false).setClickEvent { _ ->
                    save(p)
                    p.closeInventory()
                }
            )
        }

        setResourceItems(items)
        return true
    }

    private fun save(p: Player) {
        val config = SJavaPlugin.sConfig.getConfig("loots/${data.internalName}")?: YamlConfiguration()
        config.set("roll",data.rollAmount)
        val items = ArrayList<String>()
        val chances = ArrayList<Int>()
        val amounts = ArrayList<String>()
        data.items.forEach { triple ->
            items.add(triple.third)
            chances.add(triple.first)
            amounts.add("${triple.second.first}to${triple.second.last}")
        }
        config.set("displayName",data.displayName)
        config.set("items",items)
        config.set("chances",chances)
        config.set("amounts",amounts)
        SJavaPlugin.sConfig.asyncSaveConfig(config,"loots/${data.internalName}").thenAccept {
            if (it){
                DungeonTower.lootData[data.internalName] = data
                DungeonCommand()
                p.sendPrefixMsg(SStr("&a保存に成功しました"))
            } else {
                p.sendPrefixMsg(SStr("&c保存に失敗しました"))
            }
        }
    }
}