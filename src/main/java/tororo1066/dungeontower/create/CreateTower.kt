package tororo1066.dungeondata.create

import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import tororo1066.dungeontower.DungeonTower
import tororo1066.dungeontower.DungeonTower.Companion.sendPrefixMsg
import tororo1066.dungeontower.command.DungeonCommand
import tororo1066.dungeontower.data.TowerData
import tororo1066.tororopluginapi.SJavaPlugin
import tororo1066.tororopluginapi.SStr
import tororo1066.tororopluginapi.defaultMenus.LargeSInventory
import tororo1066.tororopluginapi.sInventory.SInventoryItem
import tororo1066.tororopluginapi.sItem.SItem
import java.io.File

class CreateTower(val data: TowerData, val isEdit: Boolean): LargeSInventory(SJavaPlugin.plugin, data.internalName) {

    override fun renderMenu(p: Player): Boolean {
        val items = arrayListOf(
            createInputItem(SItem(Material.GRASS_BLOCK).setDisplayName("§a名前を設定する").addLore("§d現在の値:§c${data.name}"),String::class.java) { str, _ ->
                data.name = str
            },
            createInputItem(SItem(Material.EMERALD_BLOCK).setDisplayName("§a最大人数を設定する").addLore("§d現在の値:§c${data.partyLimit}"),Int::class.java) { int, _ ->
                data.partyLimit = int
            },
            SInventoryItem(Material.COMMAND_BLOCK).setDisplayName("§aフロアの設定").setCanClick(false).setClickEvent { e ->
                val inv = object : LargeSInventory(DungeonTower.plugin, "フロアの設定") {
                    override fun renderMenu(p: Player): Boolean {
                        val items = arrayListOf<SInventoryItem>()
                        data.floors.keys.forEach { int ->
                            items.add(SInventoryItem(Material.REDSTONE_BLOCK).setDisplayName("${int}f").setCanClick(false).setClickEvent {
                                val inv = object : LargeSInventory(DungeonTower.plugin, "${int}f") {
                                    override fun renderMenu(p: Player): Boolean {
                                        val newItems = arrayListOf<SInventoryItem>()
                                        data.floors[int]!!.forEach { pair ->
                                            newItems.add(SInventoryItem(Material.REDSTONE_BLOCK).setDisplayName("§d確率:${pair.first}/1000000§f,§6フロア:${pair.second.internalName}").addLore("§cシフト左クリックで削除").setCanClick(false).setClickEvent second@ { e ->
                                                if (e.click != ClickType.SHIFT_LEFT)return@second
                                                data.floors[int]!!.remove(pair)
                                                allRenderMenu(p)

                                            })
                                        }

                                        newItems.add(createInputItem(SItem(Material.EMERALD_BLOCK).setDisplayName("§a追加").addLore("§a合計の確率:${data.floors[int]!!.sumOf { sum -> sum.first }}/1000000"),String::class.java,"§dフロア名を入れてください",true) { str, _ ->
                                            val floor = DungeonTower.floorData[str]
                                            if (floor == null){
                                                p.sendPrefixMsg(SStr("&cフロアが存在しません"))
                                                open(p)
                                                return@createInputItem
                                            }
                                            DungeonTower.sInput.sendInputCUI(p,Int::class.java,"§d確率を入れてください") { chance ->
                                                data.floors[int]!!.add(Pair(chance,floor.clone()))
                                                open(p)
                                            }
                                        })
                                        setResourceItems(newItems)
                                        return true
                                    }
                                }
                                moveChildInventory(inv,e.whoClicked as Player)
                            })
                        }
                        items.add(SInventoryItem(Material.EMERALD_BLOCK).setDisplayName("§a追加").setCanClick(false).setClickEvent {
                            data.floors[(data.floors.keys.maxOrNull()?:0)+1] = arrayListOf()
                            allRenderMenu(p)
                        })
                        setResourceItems(items)
                        return true
                    }
                }
                moveChildInventory(inv,e.whoClicked as Player)
            }
        )

        if (isEdit){
            items.add(SInventoryItem(Material.WRITABLE_BOOK).setDisplayName("§a上書き保存").setCanClick(false).setClickEvent {
                save(p)
                p.closeInventory()
            })
            items.add(SInventoryItem(Material.BARRIER).setDisplayName("§cシフト右クリックで削除する").setClickEvent {
                if (it.click != ClickType.SHIFT_RIGHT)return@setClickEvent
                File(SJavaPlugin.plugin.dataFolder.path + "/towers/${data.internalName}.yml").delete()
                DungeonTower.towerData.remove(data.internalName)
                DungeonCommand()
                p.sendPrefixMsg(SStr("&a削除しました"))
                p.closeInventory()
            })
        } else {
            items.add(SInventoryItem(Material.WRITABLE_BOOK).setDisplayName("§a保存").setCanClick(false).setClickEvent {
                save(p)
                p.closeInventory()
            })
        }

        setResourceItems(items)
        return true
    }

    private fun save(p: Player){
        val config = SJavaPlugin.sConfig.getConfig("towers/${data.internalName}")?:YamlConfiguration()
        config.set("name",data.name)
        config.set("partyLimit",data.partyLimit)

        data.floors.forEach { (floorNum, array) ->
            val list = ArrayList<String>()
            array.forEach {
                list.add("${it.first},${it.second.internalName}")
            }
            config.set("floors.${floorNum}f",list)
        }
        if (SJavaPlugin.sConfig.saveConfig(config,"towers/${data.internalName}")){
            DungeonTower.towerData[data.internalName] = data
            DungeonCommand()
            p.sendPrefixMsg(SStr("&a保存に成功しました"))
        } else {
            p.sendPrefixMsg(SStr("&c保存に失敗しました"))
        }
    }
}