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
            createInputItem(SItem(Material.PLAYER_HEAD).setDisplayName("§aMythicMobを設定する").addLore("§d現在の値:§c${data.mob?.internalName}"),String::class.java){ str, _ ->
                data.mob = DungeonTower.mythic.getMythicMob(str)?:return@createInputItem
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
            items.add(SInventoryItem(Material.BARRIER).setDisplayName("§cシフト右クリックで削除する").setClickEvent {
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
        config.set("mob",data.mob?.internalName)
        config.set("cooltime",data.coolTime)
        config.set("max",data.max)
        config.set("radius",data.radius)
        config.set("level",data.level)
        config.set("activateRange",data.activateRange)
        config.set("navigate",data.navigateKill)
        if (SJavaPlugin.sConfig.saveConfig(config,"spawners/${data.internalName}")){
            DungeonTower.spawnerData[data.internalName] = data
            DungeonCommand()
            p.sendPrefixMsg(SStr("&a保存に成功しました"))
        } else {
            p.sendPrefixMsg(SStr("&c保存に失敗しました"))
        }
    }
}