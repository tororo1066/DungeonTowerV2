package tororo1066.dungeontower.create

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

class CreateFloor(val data: FloorData, val isEdit: Boolean): LargeSInventory(SJavaPlugin.plugin, data.internalName) {

    val tasks = HashMap<FloorData.ClearTaskEnum,FloorData.ClearTask>(FloorData.ClearTaskEnum.values().associateBy({it},{ FloorData.ClearTask(it)}))

    init {
        data.clearTask.forEach {
            tasks[it.type] = it
        }
    }

    override fun renderMenu(p: Player): Boolean {
        fun SInventory.settingScoreboard(taskEnum: FloorData.ClearTaskEnum): SInventoryItem {
            val task = tasks[taskEnum]!!
            return createInputItem(SItem(Material.REDSTONE_BLOCK).setDisplayName("§aスコアボードの表示を設定する")
                .addLore("§d現在の値: ${task.scoreboardName}")
                .addLore("§e<spawnerNavigateNeed> §7モブを倒さないといけない数")
                .addLore("§e<spawnerNavigateCount> §7ゲーム中のモブを倒した数")
                .addLore("§e<gimmickNeed> §7dtaskを達成しないといけない数")
                .addLore("§e<gimmickCount> §7ゲーム中にdtaskを実行した回数"),String::class.java,"/<文字>") { str, _ ->

                task.scoreboardName = str
                val dataTask = data.clearTask.find { it.type == taskEnum }
                if (dataTask != null){
                    dataTask.scoreboardName = str
                }
            }
        }

        fun SInventory.settingClearScoreboard(taskEnum: FloorData.ClearTaskEnum): SInventoryItem {
            val task = tasks[taskEnum]!!
            return createInputItem(SItem(Material.LAPIS_BLOCK).setDisplayName("§aタスク達成後のスコアボードの表示を設定する")
                .addLore("§d現在の値: ${task.clearScoreboardName}")
                .addLore("§e<spawnerNavigateNeed> §7モブを倒さないといけない数")
                .addLore("§e<spawnerNavigateCount> §7ゲーム中のモブを倒した数")
                .addLore("§e<gimmickNeed> §7dtaskを達成しないといけない数")
                .addLore("§e<gimmickCount> §7ゲーム中にdtaskを実行した回数"),String::class.java,"/<文字>") { str, _ ->

                task.clearScoreboardName = str
                val dataTask = data.clearTask.find { it.type == taskEnum }
                if (dataTask != null){
                    dataTask.clearScoreboardName = str
                }
            }
        }

        val items = arrayListOf(
            createInputItem(SItem(Material.CLOCK).setDisplayName("§a制限時間を設定する")
                .addLore("§d現在の値: ${data.time}秒"),Int::class.java) { int, _ ->
                    data.time = int
            },
            SInventoryItem(Material.REDSTONE_BLOCK).setDisplayName("§aクリアに必要なタスクを設定する")
                .addLore("§d現在の値: ${data.clearTask.map { it.type.name }}").setCanClick(false)
                .setClickEvent {
                    val settingInv = object : LargeSInventory(SJavaPlugin.plugin, "タスクを設定する") {
                        override fun renderMenu(p: Player): Boolean {
                            val commandClearTask = tasks[FloorData.ClearTaskEnum.ENTER_COMMAND]!!
                            val items = arrayListOf(
                                SInventoryItem(Material.PLAYER_HEAD).setDisplayName("§aモブを倒す")
                                    .addLore("§d現在の値: ${if (!data.clearTask.none 
                                        { it.type == FloorData.ClearTaskEnum.KILL_SPAWNER_MOBS })
                                        "§f§l[§a§l有効§f§l]" else "§f§l[§c§l無効§f§l]"}")
                                    .setCanClick(false).setClickEvent {
                                        val settingInv = object : LargeSInventory(SJavaPlugin.plugin, "設定") {
                                            override fun renderMenu(p: Player): Boolean {
                                                val items = arrayListOf(
                                                    SInventoryItem(Material.EMERALD_BLOCK).setDisplayName("§a有効切り替え")
                                                        .addLore("§d現在の値: ${if (!data.clearTask.none
                                                            { it.type == FloorData.ClearTaskEnum.KILL_SPAWNER_MOBS }) "§f§l[§a§l有効§f§l]" else "§f§l[§c§l無効§f§l]"}")
                                                        .setCanClick(false).setClickEvent {
                                                            if (data.clearTask.none { it.type == FloorData.ClearTaskEnum.KILL_SPAWNER_MOBS }){
                                                                data.clearTask.add(tasks[FloorData.ClearTaskEnum.KILL_SPAWNER_MOBS]!!)
                                                            } else {
                                                                data.clearTask.removeIf { it.type == FloorData.ClearTaskEnum.KILL_SPAWNER_MOBS }
                                                            }
                                                            allRenderMenu(p)
                                                        },
                                                    settingScoreboard(FloorData.ClearTaskEnum.KILL_SPAWNER_MOBS),
                                                    settingClearScoreboard(FloorData.ClearTaskEnum.KILL_SPAWNER_MOBS)
                                                )
                                                setResourceItems(items)
                                                return true
                                            }
                                        }
                                        moveChildInventory(settingInv,p)
                                    },
                                SInventoryItem(Material.COMMAND_BLOCK).setDisplayName("§aコマンドを実行する")
                                    .addLore("§d現在の値: ${if (!data.clearTask.none 
                                        { it.type == FloorData.ClearTaskEnum.ENTER_COMMAND }) "§f§l[§a§l有効§f§l]" else "§f§l[§c§l無効§f§l]"}")
                                    .setCanClick(false).setClickEvent {
                                        val settingInv = object : LargeSInventory(SJavaPlugin.plugin, "設定") {

                                            override fun renderMenu(p: Player): Boolean {

                                                val items = arrayListOf(
                                                    SInventoryItem(Material.EMERALD_BLOCK).setDisplayName("§a有効切り替え")
                                                        .addLore("§d現在の値: ${if (!data.clearTask.none 
                                                            { it.type == FloorData.ClearTaskEnum.ENTER_COMMAND }) "§f§l[§a§l有効§f§l]" else "§f§l[§c§l無効§f§l]"}")
                                                        .setCanClick(false).setClickEvent {
                                                            if (data.clearTask.none { it.type == FloorData.ClearTaskEnum.ENTER_COMMAND }){
                                                                data.clearTask.add(commandClearTask)
                                                            } else {
                                                                data.clearTask.removeIf { it.type == FloorData.ClearTaskEnum.ENTER_COMMAND }
                                                            }
                                                            allRenderMenu(p)
                                                        },
                                                    createInputItem(SItem(Material.DIAMOND_BLOCK).setDisplayName("§a実行回数を設定する")
                                                        .addLore("§d現在の値: ${commandClearTask.need}")
                                                        .addLore("§e/dtask PlayerInRadius{<半径>}か/dtask <プレイヤー名>でカウントされる"),Int::class.java,"/<実行回数>") { int, _ ->
                                                        commandClearTask.need = int
                                                        val commandTask = data.clearTask.find { it.type == FloorData.ClearTaskEnum.ENTER_COMMAND }
                                                        if (commandTask != null){
                                                            commandTask.need = int
                                                        }
                                                    },
                                                    settingScoreboard(FloorData.ClearTaskEnum.ENTER_COMMAND),
                                                    settingClearScoreboard(FloorData.ClearTaskEnum.ENTER_COMMAND)
                                                )

                                                setResourceItems(items)
                                                return true
                                            }
                                        }
                                        moveChildInventory(settingInv,p)
                                    }
                            )

                            setResourceItems(items)
                            return true
                        }
                    }

                    moveChildInventory(settingInv,p)

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

        if (data.startLoc.blockX >= data.endLoc.blockX){
            val startLoc = data.startLoc.clone()
            val endLoc = data.endLoc.clone()
            data.startLoc = endLoc
            data.endLoc = startLoc
        }

        val config = SJavaPlugin.sConfig.getConfig("floors/${data.internalName}")?: YamlConfiguration()
        config.set("startLoc",data.startLoc.toLocString(LocType.BLOCK_COMMA))
        config.set("endLoc",data.endLoc.toLocString(LocType.BLOCK_COMMA))
        config.set("time",data.time)

        val clearTasks = ArrayList<String>()
        data.clearTask.forEach {
            when(it.type){
                FloorData.ClearTaskEnum.KILL_SPAWNER_MOBS->{
                    clearTasks.add("${it.type.name},${it.scoreboardName},${it.clearScoreboardName}")
                }
                FloorData.ClearTaskEnum.ENTER_COMMAND->{
                    clearTasks.add("${it.type.name},${it.need},${it.scoreboardName},${it.clearScoreboardName}")
                }
            }
        }
        config.set("clearTasks",clearTasks)
        config.set("joinCommands",data.joinCommands)

        if (SJavaPlugin.sConfig.saveConfig(config,"floors/${data.internalName}")){
            data.yml = config
            DungeonTower.floorData[data.internalName] = data
            DungeonCommand()
            p.sendPrefixMsg(SStr("&a保存に成功しました"))
        } else {
            p.sendPrefixMsg(SStr("&c保存に失敗しました"))
        }
    }



}
