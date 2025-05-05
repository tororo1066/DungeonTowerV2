package tororo1066.dungeontower.create

import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import tororo1066.dungeontower.DungeonTower
import tororo1066.dungeontower.DungeonTower.Companion.sendPrefixMsg
import tororo1066.dungeontower.command.DungeonCommand
import tororo1066.dungeontower.data.SpawnerData
import tororo1066.tororopluginapi.SJavaPlugin
import tororo1066.tororopluginapi.SStr
import tororo1066.tororopluginapi.defaultMenus.LargeSInventory
import tororo1066.tororopluginapi.sInventory.SInventoryItem
import tororo1066.tororopluginapi.sItem.SItem
import java.io.File

class CreateSpawner(val data: SpawnerData, val isEdit: Boolean): LargeSInventory(SJavaPlugin.plugin, data.internalName){

    override fun renderMenu(p: Player): Boolean {
        val items = arrayListOf(
            SInventoryItem(Material.PLAYER_HEAD).setDisplayName("§aMythicMobを設定する").setCanClick(false).setClickEvent {
                val inv = object : LargeSInventory("MythicMobを選択する") {
                    override fun renderMenu(p: Player): Boolean {
                        val items = arrayListOf<SInventoryItem>()
                        data.mobs.forEach {
                            items.add(SInventoryItem(Material.REDSTONE_BLOCK)
                                .setDisplayName("§d確率:${it.first}/1000000§f,§6モブ:${it.second.internalName}")
                                .addLore("§cシフト左クリックで削除")
                                .setCanClick(false)
                                .setClickEvent { e ->
                                    if (e.click == ClickType.SHIFT_LEFT){
                                        data.mobs.remove(it)
                                        renderMenu(p)
                                    }
                                })
                        }

                        items.add(createInputItem(SItem(Material.EMERALD_BLOCK).setDisplayName("§a追加"), String::class.java,
                            "§dMythicMobを選択してください", invOpenCancel = true){ str, _ ->
                            val mob = DungeonTower.mythic.getMythicMob(str)
                            if (mob == null){
                                p.sendPrefixMsg(SStr("§cそのMythicMobは存在しません！"))
                                return@createInputItem
                            }
                            DungeonTower.sInput.sendInputCUI(p, Int::class.java, "§d確率を指定してください",
                                action = { int ->
                                    data.mobs.add(Pair(int, mob))
                                    open(p)
                                }
                            )
                        })
                        setResourceItems(items)
                        return true
                    }
                }
                moveChildInventory(inv, p)
            },
            createNullableInputItem(
                SItem(Material.WRITABLE_BOOK).setDisplayName("§aスポーンスクリプトを設定する")
                    .addLore("§d現在の値:${data.spawnScript}")
                    .addLore("§cこれが有効な場合←は無視されます"), String::class.java
            ) { str, _ ->
                if (str == null) {
                    data.spawnScript = null
                    return@createNullableInputItem
                }
                val file = File(SJavaPlugin.plugin.dataFolder, str)
                if (!file.exists()) {
                    p.sendPrefixMsg(SStr("&cファイルが存在しません"))
                    return@createNullableInputItem
                }
                data.spawnScript = file.toRelativeString(SJavaPlugin.plugin.dataFolder)
            },
            createInputItem(SItem(Material.CLOCK).setDisplayName("§aCoolTime(tick)を設定する").addLore("§d現在の値:§c${data.coolTime}"),Int::class.java){ int, _ ->
                data.coolTime = int
            },
            createInputItem(SItem(Material.REDSTONE_BLOCK).setDisplayName("§a湧く量を設定する").addLore("§d現在の値:§c${data.max}"),Int::class.java){ int, _ ->
                data.max = int
            },
            createInputItem(SItem(Material.DIAMOND_BLOCK).setDisplayName("§a湧く半径を設定する").addLore("§d現在の値:§c${data.radius}"),Int::class.java){ int, _ ->
                data.radius = int
            },
            createInputItem(SItem(Material.SCAFFOLDING).setDisplayName("§a湧くときの高さのオフセットを設定する").addLore("§d現在の値:§c${data.yOffSet}"),Double::class.java){ double, _ ->
                data.yOffSet = double
            },
            createInputItem(SItem(Material.EXPERIENCE_BOTTLE).setDisplayName("§aMythicMobのlevelを設定する").addLore("§d現在の値:§c${data.level}"),Double::class.java){ double, _ ->
                data.level = double
            },
            createInputItem(SItem(Material.BARRIER).setDisplayName("§a稼働する半径を設定する").addLore("§d現在の値:§c${data.activateRange}"),Int::class.java){ int, _ ->
                data.activateRange = int
            },
            createInputItem(SItem(Material.DIAMOND_SWORD).setDisplayName("§aクリア条件を達成するために倒す数を設定する").addLore("§d現在の値:§c${data.navigateKill}"),Int::class.java){ int, _ ->
                data.navigateKill = int
            }
        )

        if(isEdit){
            items.add(SInventoryItem(Material.WRITABLE_BOOK).setDisplayName("§a上書き保存する").setCanClick(false).setClickEvent {
                save(p)
                p.closeInventory()
            })
            items.add(SInventoryItem(Material.BARRIER).setDisplayName("§cシフト右クリックで削除する").setCanClick(false).setClickEvent {
                if (it.click != ClickType.SHIFT_RIGHT)return@setClickEvent
                File(SJavaPlugin.plugin.dataFolder.path + "/spawners/${data.internalName}.yml").delete()
                DungeonTower.spawnerData.remove(data.internalName)
                DungeonCommand()
                p.sendPrefixMsg(SStr("&a削除しました"))
                p.closeInventory()
            })
        } else {
            items.add(SInventoryItem(Material.WRITABLE_BOOK).setDisplayName("§a保存する").setCanClick(false).setClickEvent {
                save(p)
                p.closeInventory()
            })
        }

        setResourceItems(items)
        return true
    }

    private fun save(p: Player){
        val config = SJavaPlugin.sConfig.getConfig("spawners/${data.internalName}")?:YamlConfiguration()
        data.mobs.forEach {
            config.set("mobs.${it.second.internalName}",it.first)
        }
        config.set("spawnScript",data.spawnScript)
        config.set("cooltime",data.coolTime)
        config.set("max",data.max)
        config.set("radius",data.radius)
        config.set("yOffSet",data.yOffSet)
        config.set("level",data.level)
        config.set("activateRange",data.activateRange)
        config.set("navigate",data.navigateKill)
        SJavaPlugin.sConfig.asyncSaveConfig(config,"spawners/${data.internalName}").thenAccept {
            if (it){
                DungeonTower.spawnerData[data.internalName] = data
                DungeonCommand()
                p.sendPrefixMsg(SStr("&a保存に成功しました"))
            } else {
                p.sendPrefixMsg(SStr("&c保存に失敗しました"))
            }
        }
    }
}