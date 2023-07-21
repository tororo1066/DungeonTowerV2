package tororo1066.dungeontower.inventory

import org.bukkit.Material
import tororo1066.tororopluginapi.SJavaPlugin
import tororo1066.tororopluginapi.sInventory.SInventory
import tororo1066.tororopluginapi.sInventory.SInventoryItem

class RuleInventory: SInventory(SJavaPlugin.plugin,"説明",5) {

    override fun renderMenu(): Boolean {
        fillItem(SInventoryItem(Material.LIGHT_BLUE_STAINED_GLASS_PANE).setDisplayName(" ").setCanClick(false))

        setItem(10, SInventoryItem(Material.BOW).setDisplayName("§dどんな武器でも使える！")
            .addLore("§f銃や魔法武器を駆使して攻略しよう！")
            .setCanClick(false))

        setItem(12, SInventoryItem(Material.PLAYER_HEAD).setDisplayName("§6パーティを組んで挑戦しよう！")
            .addLore("§fパーティを組むことで有利に戦えるぞ！")
            .setCanClick(false))

        setItem(14, SInventoryItem(Material.SPAWNER).setDisplayName("§bタスクをクリアして次の階へ進もう！")
            .addLore("§f右側に表示されるタスクをこなすと上の階へ進むことができる！")
            .setCanClick(false))

        setItem(16, SInventoryItem(Material.CRIMSON_STAIRS).setDisplayName("§c次の階へ進む階段を見つけよう！")
            .addLore("§fこの階段の上に半分のプレイヤーが乗ると次の階へ進めるぞ！")
            .setCanClick(false))

        setItems(listOf(27,29,31,33), SInventoryItem(Material.RED_STAINED_GLASS_PANE).setDisplayName(" ")
            .setCanClick(false))

        setItem(28, SInventoryItem(Material.GOLDEN_APPLE).setDisplayName("§c§l使えないアイテム")
            .setCanClick(false))

        setItem(30, SInventoryItem(Material.ENCHANTED_GOLDEN_APPLE).setDisplayName("§c§l使えないアイテム")
            .setCanClick(false))

        setItem(32, SInventoryItem(Material.TOTEM_OF_UNDYING).setDisplayName("§c§l使えないアイテム")
            .setCanClick(false))


        return true
    }
}