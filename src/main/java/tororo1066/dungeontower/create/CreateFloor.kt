package tororo1066.dungeontower.create

import org.bukkit.GameRule
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import tororo1066.dungeontower.DungeonTower
import tororo1066.dungeontower.DungeonTower.Companion.sendPrefixMsg
import tororo1066.dungeontower.command.DungeonCommand
import tororo1066.dungeontower.data.FloorData
import tororo1066.tororopluginapi.SJavaPlugin
import tororo1066.tororopluginapi.SStr
import tororo1066.tororopluginapi.defaultMenus.LargeSInventory
import tororo1066.tororopluginapi.sInventory.SInventory
import tororo1066.tororopluginapi.sInventory.SInventoryItem
import tororo1066.tororopluginapi.sItem.SItem
import tororo1066.tororopluginapi.utils.LocType
import tororo1066.tororopluginapi.utils.toLocString
import java.io.File
import kotlin.math.max
import kotlin.math.min

class CreateFloor(val data: FloorData, val isEdit: Boolean): LargeSInventory(SJavaPlugin.plugin, data.internalName) {


    override fun renderMenu(p: Player): Boolean {

        val items = arrayListOf(
            createInputItem(SItem(Material.CLOCK).setDisplayName("§a制限時間を設定する")
                .addLore("§d現在の値: ${data.time}秒"),Int::class.java) { int, _ ->
                    data.time = int
                    data.initialTime = int
            },
            SInventoryItem(Material.COMMAND_BLOCK).setDisplayName("§a参加時のコマンドの実行")
                .addLore("§d現在の値: ${data.joinCommands}")
                .setCanClick(false).setClickEvent {

                    val settingInv = object : LargeSInventory(SJavaPlugin.plugin, "参加時のコマンドの実行"){

                        override fun renderMenu(p: Player): Boolean {
                            val items = arrayListOf<SInventoryItem>()

                            data.joinCommands.forEach {
                                items.add(SInventoryItem(Material.COMMAND_BLOCK).setDisplayName("§a${it}")
                                    .addLore("§cシフト左クリックで削除")
                                    .setCanClick(false).setClickEvent second@ { e ->
                                        if (e.click != ClickType.SHIFT_LEFT)return@second
                                        data.joinCommands.remove(it)
                                        allRenderMenu(p)
                                    })
                            }

                            items.add(createInputItem(SItem(Material.EMERALD_BLOCK).setDisplayName("§aコマンドを追加する")
                                .addLore("§d<name> ->プレイヤーの名前")
                                .addLore("§d<uuid> ->プレイヤーのUUID"),String::class.java,"/<コマンド>") { str, _ ->
                                data.joinCommands.add(str)
                                allRenderMenu(p)
                            })

                            setResourceItems(items)
                            return true
                        }
                    }

                    moveChildInventory(settingInv,p)
                },
            createNullableInputItem(SItem(Material.OAK_PLANKS).setDisplayName("§6始点を変更する")
                .addLore("§d現在の値: ${data.parallelFloorOrigin?.toLocString(LocType.BLOCK_COMMA)}"),Location::class.java,"/<座標>") { location, _ ->
                   data.parallelFloorOrigin = location
            },
            SInventoryItem(Material.GRASS_BLOCK).setDisplayName("§aサブフロアを設定する")
                .setCanClick(false).setClickEvent {
                    val settingInv = object : LargeSInventory(SJavaPlugin.plugin, "サブフロアを設定する"){

                        override fun renderMenu(p: Player): Boolean {
                            val items = arrayListOf<SInventoryItem>()

                            data.subFloors.forEach {
                                items.add(SInventoryItem(Material.REDSTONE_BLOCK)
                                    .setDisplayName("§d確率:${it.first}/1000000§f,§6フロア:${it.second}")
                                    .addLore("§cシフト左クリックで削除")
                                    .setCanClick(false).setClickEvent second@ { e ->
                                        if (e.click != ClickType.SHIFT_LEFT)return@second
                                        data.subFloors.remove(it)
                                        allRenderMenu(p)
                                    })
                            }

                            items.add(createInputItem(SItem(Material.EMERALD_BLOCK).setDisplayName("§a追加")
                                .addLore("§a合計の確率:${data.subFloors.sumOf { sum -> sum.first }}/1000000"),
                                String::class.java,
                                "§dフロア名を入れてください",
                                invOpenCancel = true
                            ) { str, _ ->
                                if (!DungeonTower.floorData.containsKey(str)) {
                                    p.sendPrefixMsg(SStr("&cフロアが存在しません"))
                                    open(p)
                                    return@createInputItem
                                }
                                DungeonTower.sInput.sendInputCUI(
                                    p,
                                    Int::class.java,
                                    "§d確率を入れてください",
                                    action = { chance ->
                                        data.subFloors.add(Pair(chance, str))
                                        open(p)
                                    })
                            })

                            setResourceItems(items)
                            return true
                        }
                    }

                    moveChildInventory(settingInv,p)
                },
            SInventoryItem(Material.REPEATING_COMMAND_BLOCK).setDisplayName("§aセーブデータを使用する")
                .addLore("§d現在の値: ${data.shouldUseSaveData}").setCanClick(false)
                .setClickEvent {
                    data.shouldUseSaveData = !data.shouldUseSaveData
                    allRenderMenu(p)
                },
            SInventoryItem(Material.WARPED_STAIRS).setDisplayName("§aタスクをクリアしていない時に階段に上るのを禁止する")
                .addLore("§d現在の値: ${data.cancelStandOnStairs}").setCanClick(false)
                .setClickEvent {
                    data.cancelStandOnStairs = !data.cancelStandOnStairs
                    allRenderMenu(p)
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
            items.add(SInventoryItem(Material.WRITABLE_BOOK).setDisplayName("§a上書き保存する").setClickEvent {
                save(p)
                p.closeInventory()
            })
            items.add(SInventoryItem(Material.BARRIER).setDisplayName("§cシフト右クリックで削除する").setClickEvent {
                if (it.click != ClickType.SHIFT_RIGHT)return@setClickEvent

                File(SJavaPlugin.plugin.dataFolder.path + "/floors/${data.internalName}.yml").delete()
                DungeonTower.floorData.remove(data.internalName)

                DungeonCommand()
                p.sendPrefixMsg(SStr("&a削除しました"))
                p.closeInventory()
            })
        } else {
            items.add(SInventoryItem(Material.WRITABLE_BOOK).setDisplayName("§a保存する").setClickEvent {
                save(p)
                p.closeInventory()
            })
        }

        setResourceItems(items)
        return true
    }

    private fun save(p: Player){

        val minX = min(data.startLoc.x,data.endLoc.x)
        val minY = min(data.startLoc.y,data.endLoc.y)
        val minZ = min(data.startLoc.z,data.endLoc.z)
        val maxX = max(data.startLoc.x,data.endLoc.x)
        val maxY = max(data.startLoc.y,data.endLoc.y)
        val maxZ = max(data.startLoc.z,data.endLoc.z)

        data.startLoc.set(minX,minY,minZ)
        data.endLoc.set(maxX,maxY,maxZ)

        val config = SJavaPlugin.sConfig.getConfig("floors/${data.internalName}")?: YamlConfiguration()
        config.set("startLoc",data.startLoc.toLocString(LocType.BLOCK_COMMA))
        config.set("endLoc",data.endLoc.toLocString(LocType.BLOCK_COMMA))
        config.set("time",data.time)
        config.set("joinCommands",data.joinCommands)
        config.set("parallelFloorOrigin",data.parallelFloorOrigin?.toLocString(LocType.BLOCK_COMMA))
        config.set("subFloors",data.subFloors.map { "${it.first},${it.second}" })
        config.set("shouldUseSaveData",data.shouldUseSaveData)
        config.set("cancelStandOnStairs",data.cancelStandOnStairs)

        SJavaPlugin.sConfig.asyncSaveConfig(config,"floors/${data.internalName}").thenAccept {
            if (it){
                data.yml = config
                DungeonTower.floorData[data.internalName] = data
                DungeonCommand()
                p.sendPrefixMsg(SStr("&a保存に成功しました"))
            } else {
                p.sendPrefixMsg(SStr("&c保存に失敗しました"))
            }
        }
    }



}
