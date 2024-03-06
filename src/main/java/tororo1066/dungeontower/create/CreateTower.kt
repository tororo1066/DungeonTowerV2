package tororo1066.dungeontower.create

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
import tororo1066.tororopluginapi.defaultMenus.SingleItemInventory
import tororo1066.tororopluginapi.sInventory.SInventoryItem
import tororo1066.tororopluginapi.sItem.SItem
import java.io.File
import java.util.function.Consumer

class CreateTower(val data: TowerData, val isEdit: Boolean): LargeSInventory(SJavaPlugin.plugin, data.internalName) {

    override fun renderMenu(p: Player): Boolean {
        val items = arrayListOf(
            createInputItem(SItem(Material.GRASS_BLOCK).setDisplayName("§a名前を設定する")
                .addLore("§d現在の値:§c${data.name}"),SStr::class.java) { str, _ ->
                data.name = str.toString()
            },
            createInputItem(SItem(Material.EMERALD_BLOCK).setDisplayName("§a最大人数を設定する")
                .addLore("§d現在の値:§c${data.partyLimit}"),Int::class.java) { int, _ ->
                data.partyLimit = int
            },
            SInventoryItem(Material.GOLD_INGOT).setDisplayName("§a挑戦に必要なアイテムを設定する")
                .addLore("§d現在の値:${if (data.challengeItem?.itemMeta?.displayName == "")
                    data.challengeItem?.type?.name else data.challengeItem?.itemMeta?.displayName}")
                .setCanClick(false)
                .setClickEvent {
                    val inv = SingleItemInventory(DungeonTower.plugin, "アイテムの設定")
                        .apply {
                            data.challengeItem?.let { nowItem = it }
                            onConfirm = Consumer {
                                data.challengeItem = it
                                p.closeInventory()
                            }
                        }

                    moveChildInventory(inv,p)
                },
            createInputItem(SItem(Material.GLOWSTONE).setDisplayName("§aデフォルトのパークポイントを設定する")
                .addLore("§d現在の値:${data.defaultParkPoints}"),Int::class.java) { int, _ ->
                data.defaultParkPoints = int
            },
            createInputItem(SItem(Material.REDSTONE_BLOCK).setDisplayName("§a挑戦出来るか確認するスクリプトを設定する")
                .addLore("§d現在の値:${data.challengeScript}"),String::class.java) { str, _ ->
                val file = File(DungeonTower.plugin.dataFolder,str)
                if (!file.exists()){
                    p.sendPrefixMsg(SStr("&cファイルが存在しません"))
                    return@createInputItem
                }
                data.challengeScript = file.toRelativeString(DungeonTower.plugin.dataFolder)
            },
            createInputItem(SItem(Material.LAPIS_BLOCK).setDisplayName("§a入場時に実行するスクリプトを設定する")
                .addLore("§d現在の値:${data.entryScript}"),String::class.java) { str, _ ->
                val file = File(DungeonTower.plugin.dataFolder,str)
                if (!file.exists()){
                    p.sendPrefixMsg(SStr("&cファイルが存在しません"))
                    return@createInputItem
                }
                data.entryScript = file.toRelativeString(DungeonTower.plugin.dataFolder)
            },
            SInventoryItem(Material.COMMAND_BLOCK).setDisplayName("§a最初のフロアの設定").setCanClick(false).setClickEvent { e ->
                val inv = object : LargeSInventory(DungeonTower.plugin, "最初のフロアの設定") {
                    override fun renderMenu(p: Player): Boolean {
                        val items = arrayListOf<SInventoryItem>()
                        data.firstFloor.forEach {
                            val (chance, floorData) = it
                            items.add(SInventoryItem(Material.REDSTONE_BLOCK).setDisplayName("§d${chance}/1000000")
                                .addLore("§6フロア:${floorData.internalName}")
                                .setCanClick(false).setClickEvent second@ { e ->
                                    if (e.click != ClickType.SHIFT_LEFT)return@second
                                    data.firstFloor.remove(it)
                                    allRenderMenu(p)
                                })
                        }
                        items.add(createInputItem(SItem(Material.EMERALD_BLOCK).setDisplayName("§a追加")
                            .addLore("§a合計の確率:${data.firstFloor.sumOf { sum -> sum.first }}/1000000"),String::class.java,"§dフロア名を入れてください", invOpenCancel = true) { str, _ ->
                            val floor = DungeonTower.floorData[str]
                            if (floor == null){
                                p.sendPrefixMsg(SStr("&cフロアが存在しません"))
                                open(p)
                                return@createInputItem
                            }
                            DungeonTower.sInput.sendInputCUI(p,Int::class.java,"§d確率を入れてください") { chance ->
                                data.firstFloor.add(Pair(chance,floor.newInstance()))
                                open(p)
                            }
                        })
                        setResourceItems(items)
                        return true
                    }
                }
                moveChildInventory(inv,e.whoClicked as Player)
            }
        )

        if (isEdit){
            items.add(SInventoryItem(Material.WRITABLE_BOOK)
                .setDisplayName("§a上書き保存").setCanClick(false).setClickEvent {
                    save(p)
                    p.closeInventory()
            })
            items.add(SInventoryItem(Material.BARRIER)
                .setDisplayName("§cシフト右クリックで削除する").setClickEvent {
                    if (it.click != ClickType.SHIFT_RIGHT)return@setClickEvent
                    File(SJavaPlugin.plugin.dataFolder.path + "/towers/${data.internalName}.yml").delete()
                    DungeonTower.towerData.remove(data.internalName)
                    DungeonCommand()
                    p.sendPrefixMsg(SStr("&a削除しました"))
                    p.closeInventory()
            })
        } else {
            items.add(SInventoryItem(Material.WRITABLE_BOOK).setDisplayName("§a保存")
                .setCanClick(false).setClickEvent {
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
        config.set("defaultParkPoints",data.defaultParkPoints)
        config.set("challengeItem",data.challengeItem)
        config.set("challengeScript",data.challengeScript)
        config.set("entryScript",data.entryScript)

        config.set("firstFloor",data.firstFloor.map { "${it.first},${it.second.internalName}" })

        if (SJavaPlugin.sConfig.saveConfig(config,"towers/${data.internalName}")){
            DungeonTower.towerData[data.internalName] = data
            DungeonCommand()
            p.sendPrefixMsg(SStr("&a保存に成功しました"))
        } else {
            p.sendPrefixMsg(SStr("&c保存に失敗しました"))
        }
    }
}