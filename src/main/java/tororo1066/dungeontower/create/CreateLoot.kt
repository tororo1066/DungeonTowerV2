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
                    .addLore("§d現在の値:§c${data.displayName}"), SStr::class.java
            ) { str, _ ->
                data.displayName = str.toString()
            },
            createInputItem(
                SItem(Material.CHEST).setDisplayName("§d抽選のスクリプトを設定する")
                    .addLore("§d現在の値:§c${data.displayMonitorScript}"), String::class.java
            ) { str, _ ->
                data.displayMonitorScript = str
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
                SInventoryItem(Material.BARRIER).setDisplayName("§cシフト右クリックで削除する").setCanClick(false).setClickEvent {
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
        config.set("displayName",data.displayName)
        config.set("displayMonitorScript",data.displayMonitorScript)
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