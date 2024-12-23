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

        fun SInventory.settingClearCondition(taskEnum: FloorData.ClearTaskEnum): SInventoryItem {
            val task = tasks[taskEnum]!!
            return SInventoryItem(Material.DIAMOND_BLOCK).setDisplayName("§aタスク達成条件を設定する")
                .addLore("§d現在の値: ${task.condition.displayName}")
                .setCanClick(false).setClickEvent {
                    val settingInv = object : LargeSInventory(SJavaPlugin.plugin, "設定") {
                        override fun renderMenu(p: Player): Boolean {
                            val items = arrayListOf<SInventoryItem>()
                            FloorData.ClearCondition.values().forEach { condition ->
                                items.add(SInventoryItem(Material.EMERALD_BLOCK).setDisplayName("§a${condition.displayName}")
                                    .addLore("§d現在の値: ${if (task.condition == condition) "§f§l[§a§l有効§f§l]" else "§f§l[§c§l無効§f§l]"}")
                                    .setCanClick(false).setClickEvent {
                                        task.condition = condition
                                        val dataTask = data.clearTask.find { it.type == taskEnum }
                                        if (dataTask != null){
                                            dataTask.condition = condition
                                        }
                                        p.closeInventory()
                                    })
                            }
                            setResourceItems(items)
                            return true
                        }
                    }

                    moveChildInventory(settingInv,p)
                }
        }

        val items = arrayListOf(
            createInputItem(SItem(Material.CLOCK).setDisplayName("§a制限時間を設定する")
                .addLore("§d現在の値: ${data.time}秒"),Int::class.java) { int, _ ->
                    data.time = int
                    data.initialTime = int
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
                                                    settingClearScoreboard(FloorData.ClearTaskEnum.KILL_SPAWNER_MOBS),
                                                    settingClearCondition(FloorData.ClearTaskEnum.KILL_SPAWNER_MOBS)
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
                                                        .addLore("§e/dtask <partyUUID> countCommandTask <カウント数>でカウントされる"),Int::class.java,"/<実行回数>") { int, _ ->
                                                        commandClearTask.need = int
                                                        val commandTask = data.clearTask.find { it.type == FloorData.ClearTaskEnum.ENTER_COMMAND }
                                                        if (commandTask != null){
                                                            commandTask.need = int
                                                        }
                                                    },
                                                    settingScoreboard(FloorData.ClearTaskEnum.ENTER_COMMAND),
                                                    settingClearScoreboard(FloorData.ClearTaskEnum.ENTER_COMMAND),
                                                    settingClearCondition(FloorData.ClearTaskEnum.ENTER_COMMAND)
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
            SInventoryItem(Material.DARK_OAK_SIGN).setDisplayName("§bワールドのゲームルールを設定する")
                .also {
                    if (data.worldGameRules.isNotEmpty()) {
                        it.addLore("§d現在の値:")
                    }
                    data.worldGameRules.forEach { (gameRule, value) ->
                        it.addLore("§d$gameRule $value")
                    }
                }
                .setCanClick(false)
                .setClickEvent {
                    val settingInv = object : LargeSInventory(SJavaPlugin.plugin, "ワールドのゲームルールを設定する"){

                        override fun renderMenu(p: Player): Boolean {
                            val items = arrayListOf<SInventoryItem>()

                            data.worldGameRules.forEach { (gameRule, value) ->
                                items.add(SInventoryItem(Material.REDSTONE_BLOCK)
                                    .setDisplayName("§d${gameRule}§f,§6${value}")
                                    .addLore("§cシフト左クリックで削除")
                                    .setCanClick(false).setClickEvent second@ { e ->
                                        if (e.click != ClickType.SHIFT_LEFT)return@second
                                        data.worldGameRules.remove(gameRule)
                                        allRenderMenu(p)
                                    })
                            }

                            items.add(createInputItem(SItem(Material.EMERALD_BLOCK).setDisplayName("§a追加")
                                .addLore("§a合計の確率:${data.subFloors.sumOf { sum -> sum.first }}/1000000"),
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
                                    "§d値を入れてください(${gameRule.type.simpleName})",
                                    action = { value ->
                                        data.worldGameRules[gameRule] = value
                                        open(p)
                                    })
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
        config.set("parallelFloorOrigin",data.parallelFloorOrigin?.toLocString(LocType.BLOCK_COMMA))
        config.set("subFloors",data.subFloors.map { "${it.first},${it.second}" })
        config.set("shouldUseSaveData",data.shouldUseSaveData)
        config.set("cancelStandOnStairs",data.cancelStandOnStairs)
        config.set("worldGameRules",data.worldGameRules.mapKeys { it.key.name })

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
