package tororo1066.dungeontower.create

import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import tororo1066.dungeontower.DungeonTower
import tororo1066.dungeontower.DungeonTower.Companion.sendPrefixMsg
import tororo1066.dungeontower.command.DungeonCommand
import tororo1066.dungeontower.data.LootItemData
import tororo1066.tororopluginapi.SJavaPlugin
import tororo1066.tororopluginapi.SStr
import tororo1066.tororopluginapi.defaultMenus.LargeSInventory
import tororo1066.tororopluginapi.defaultMenus.SingleItemInventory
import tororo1066.tororopluginapi.sInventory.SInventoryItem
import tororo1066.tororopluginapi.sItem.SItem
import java.io.File
import java.util.function.Consumer

class CreateLootItem(
    val data: LootItemData, val isEdit: Boolean
) : LargeSInventory(SJavaPlugin.plugin, data.internalName) {

    override fun renderMenu(p: Player): Boolean {
        val items = arrayListOf(
            SInventoryItem(Material.GRASS_BLOCK).setDisplayName("§aアイテムを設定する").setCanClick(false)
                .setClickEvent { _ ->
                    val inv = object : SingleItemInventory(SJavaPlugin.plugin, "§aアイテムを設定する") {
                        init {
                            nowItem = data.itemStack.build()
                            onConfirm = Consumer { item ->
                                data.itemStack = SItem(item)
                                p.closeInventory()
                            }
                        }
                    }

                    moveChildInventory(inv, p)
                },
            SInventoryItem(Material.COMMAND_BLOCK).setDisplayName("§a全鯖にアイテムを手に入れたことを通知する")
                .addLore("§d現在の値: ${if (data.announce) "§f§l[§a§l有効§f§l]" else "§f§l[§c§l無効§f§l]"}")
                .setCanClick(false).setClickEvent { _ ->
                    data.announce = !data.announce
                    allRenderMenu(p)
                },
            createInputItem(
                SItem(Material.WARPED_STAIRS).setDisplayName("§a何フロア移動したら消えるか設定する(0で無効化)")
                    .addLore("§d現在の値: §c${data.removeFloorCount}"), Int::class.java
            ) { int, _ ->
                data.removeFloorCount = int
            },
            SInventoryItem(Material.BARRIER).setDisplayName("§aダンジョンを出たときに消す")
                .addLore("§d現在の値: ${if (data.removeOnExit) "§f§l[§a§l有効§f§l]" else "§f§l[§c§l無効§f§l]"}")
                .setCanClick(false).setClickEvent { _ ->
                    data.removeOnExit = !data.removeOnExit
                    allRenderMenu(p)
                },
            SInventoryItem(Material.BONE).setDisplayName("§a死んだときに消す")
                .addLore("§d現在の値: ${if (data.removeOnDeath) "§f§l[§a§l有効§f§l]" else "§f§l[§c§l無効§f§l]"}")
                .setCanClick(false).setClickEvent { _ ->
                    data.removeOnDeath = !data.removeOnDeath
                    allRenderMenu(p)
                }
        )

        if (isEdit) {
            items.add(
                SInventoryItem(Material.WRITABLE_BOOK).setDisplayName("§a上書き保存する").setCanClick(false)
                    .setClickEvent { _ ->
                        save(p)
                        p.closeInventory()
                    })
            items.add(SInventoryItem(Material.BARRIER).setDisplayName("§cシフト右クリックで削除する").setClickEvent {
                if (it.click != ClickType.SHIFT_RIGHT) return@setClickEvent
                File(SJavaPlugin.plugin.dataFolder.path + "/lootItems/${data.internalName}.yml").delete()
                DungeonTower.lootItemData.remove(data.internalName)
                DungeonCommand()
                p.sendPrefixMsg(SStr("&a削除しました"))
                p.closeInventory()
            })
        } else {
            items.add(
                SInventoryItem(Material.WRITABLE_BOOK).setDisplayName("§a保存する").setCanClick(false)
                    .setClickEvent { _ ->
                        save(p)
                        p.closeInventory()
                    })
        }

        setResourceItems(items)
        return true
    }

    private fun save(p: Player) {
        val config = SJavaPlugin.sConfig.getConfig("lootItems/${data.internalName}") ?: YamlConfiguration()
        config.set("itemStack", data.itemStack.build())
        config.set("announce", data.announce)
        config.set("removeFloorCount", data.removeFloorCount)
        config.set("removeOnExit", data.removeOnExit)
        config.set("removeOnDeath", data.removeOnDeath)
        SJavaPlugin.sConfig.asyncSaveConfig(config, "lootItems/${data.internalName}").thenAccept {
            if (it) {
                DungeonTower.lootItemData[data.internalName] = data
                DungeonCommand()
                p.sendPrefixMsg(SStr("&a保存に成功しました"))
            } else {
                p.sendPrefixMsg(SStr("&c保存に失敗しました"))
            }
        }
    }
}