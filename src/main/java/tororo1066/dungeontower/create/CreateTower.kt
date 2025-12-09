package tororo1066.dungeontower.create

import org.bukkit.GameRule
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
            createNullableInputItem(SItem(Material.REDSTONE_BLOCK).setDisplayName("§a挑戦出来るか確認するスクリプトを設定する")
                .addLore("§d現在の値:${data.challengeScript}"),String::class.java) { str, _ ->
                data.challengeScript = str
            },
            createNullableInputItem(SItem(Material.LAPIS_BLOCK).setDisplayName("§a入場時に実行するスクリプトを設定する")
                .addLore("§d現在の値:${data.entryScript}"),String::class.java) { str, _ ->
                data.entryScript = str
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
                            DungeonTower.sInput.sendInputCUI(p,Int::class.java,"§d確率を入れてください",
                                action = { chance ->
                                    data.firstFloor.add(Pair(chance,floor.newInstance()))
                                    open(p)
                                },
                                onCancel = {
                                    open(p)
                                }
                            )
                        })
                        setResourceItems(items)
                        return true
                    }
                }
                moveChildInventory(inv,e.whoClicked as Player)
            },
            createInputItem(SItem(Material.PINK_WOOL).setDisplayName("§a並列最大人数を設定する")
                .addLore("§d現在の値:${data.playerLimit}"),Int::class.java) { int, _ ->
                data.playerLimit = int
            },
            SInventoryItem(Material.OAK_BOAT).setDisplayName("§eパーティが存在しない時に自動で作成する")
                .addLore("§d現在の値:${data.autoCreateParty}").setCanClick(false).setClickEvent { e ->
                    data.autoCreateParty = !data.autoCreateParty
                    allRenderMenu(e.whoClicked as Player)
                },
            SInventoryItem(Material.DARK_OAK_SIGN).setDisplayName("§bワールドのゲームルールを設定する")
                .also {
                    if (data.worldGameRules.isNotEmpty()) {
                        it.addLore("§d現在の値:")
                    }
                    data.worldGameRules.forEach { (gameRule, value) ->
                        it.addLore("§d${gameRule.name} $value")
                    }
                }
                .setCanClick(false)
                .setClickEvent {
                    val settingInv = object : LargeSInventory(SJavaPlugin.plugin, "ワールドのゲームルールを設定する"){

                        override fun renderMenu(p: Player): Boolean {
                            val items = arrayListOf<SInventoryItem>()

                            data.worldGameRules.forEach { (gameRule, value) ->
                                items.add(SInventoryItem(Material.REDSTONE_BLOCK)
                                    .setDisplayName("§d${gameRule.name}§f,§6${value}")
                                    .addLore("§cシフト左クリックで削除")
                                    .setCanClick(false).setClickEvent second@ { e ->
                                        if (e.click != ClickType.SHIFT_LEFT)return@second
                                        data.worldGameRules.remove(gameRule)
                                        allRenderMenu(p)
                                    })
                            }

                            items.add(createInputItem(SItem(Material.EMERALD_BLOCK).setDisplayName("§a追加"),
                                String::class.java,
                                "§dゲームルール名を入れてください",
                                invOpenCancel = true
                            ) { str, _ ->
                                val gameRule = GameRule.getByName(str)
                                if (gameRule == null) {
                                    p.sendPrefixMsg(SStr("&cゲームルールが存在しません"))
                                    open(p)
                                    return@createInputItem
                                }
                                DungeonTower.sInput.sendInputCUI(
                                    p,
                                    gameRule.type,
                                    "§d値を入れてください(${gameRule.name})",
                                    action = { value ->
                                        data.worldGameRules[gameRule] = value
                                        open(p)
                                    },
                                    onCancel = {
                                        open(p)
                                    })
                            })

                            setResourceItems(items)
                            return true
                        }
                    }

                    moveChildInventory(settingInv,p)
                },
            SInventoryItem(Material.LAPIS_BLOCK).setDisplayName("§eWorldGuardのフラグを設定する")
                .setCanClick(false)
                .setClickEvent {
                    val inv = object : LargeSInventory(SJavaPlugin.plugin, "WorldGuardのフラグを設定する") {
                        override fun renderMenu(p: Player): Boolean {
                            val items = arrayListOf<SInventoryItem>()
                            data.regionFlags.forEach { (flag, value) ->
                                items.add(SInventoryItem(Material.REDSTONE_BLOCK)
                                    .setDisplayName("§d${flag}§f,§6${value}")
                                    .addLore("§cシフト左クリックで削除")
                                    .setCanClick(false).setClickEvent second@ { e ->
                                        if (e.click != ClickType.SHIFT_LEFT)return@second
                                        data.regionFlags.remove(flag)
                                        allRenderMenu(p)
                                    })
                            }

                            items.add(createInputItem(SItem(Material.EMERALD_BLOCK).setDisplayName("§a追加"),
                                String::class.java,
                                "§dフラグ名を入れてください",
                                invOpenCancel = true
                            ) { str, _ ->
                                DungeonTower.sInput.sendInputCUI(
                                    p,
                                    String::class.java,
                                    "§d値を入れてください",
                                    action = { value ->
                                        data.regionFlags[str] = value
                                        open(p)
                                    },
                                    onCancel = {
                                        open(p)
                                    })
                            })

                            setResourceItems(items)
                            return true
                        }
                    }

                    moveChildInventory(inv,p)
                }
        )

        if (isEdit){
            items.add(SInventoryItem(Material.WRITABLE_BOOK)
                .setDisplayName("§a上書き保存").setCanClick(false).setClickEvent {
                    save(p)
                    p.closeInventory()
            })
            items.add(SInventoryItem(Material.BARRIER)
                .setDisplayName("§cシフト右クリックで削除する").setCanClick(false).setClickEvent {
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
        config.set("autoCreateParty",data.autoCreateParty)
        config.set("challengeItem",data.challengeItem)
        config.set("challengeScript",data.challengeScript)
        config.set("entryScript",data.entryScript)
        config.set("playerLimit",data.playerLimit)
        config.set("firstFloor",data.firstFloor.map { "${it.first},${it.second.internalName}" })
        config.set("worldGameRules",data.worldGameRules.mapKeys { it.key.name })
        config.set("regionFlags",data.regionFlags)

        SJavaPlugin.sConfig.asyncSaveConfig(config,"towers/${data.internalName}").thenAccept {
            if (it){
                DungeonTower.towerData[data.internalName] = data
                DungeonCommand()
                p.sendPrefixMsg(SStr("&a保存に成功しました"))
            } else {
                p.sendPrefixMsg(SStr("&c保存に失敗しました"))
            }
        }
    }
}