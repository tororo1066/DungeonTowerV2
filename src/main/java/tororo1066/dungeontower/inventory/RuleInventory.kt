package tororo1066.dungeontower.inventory

import org.bukkit.Material
import org.bukkit.entity.Player
import tororo1066.tororopluginapi.SJavaPlugin
import tororo1066.tororopluginapi.sInventory.SInventory
import tororo1066.tororopluginapi.sInventory.SInventoryItem

class RuleInventory: SInventory(SJavaPlugin.plugin,"説明",5) {

    init {
        setOnClick { it.isCancelled = true }
    }

    override fun renderMenu(): Boolean {
        fillItem(SInventoryItem(Material.BLUE_STAINED_GLASS_PANE).setDisplayName(" ").setCanClick(false))

        setItem(10, SInventoryItem(Material.BOW).setDisplayName("§dどんな武器でも使える！")
            .addLore("§f銃や魔法武器を駆使して攻略しよう！")
            .setCanClick(false))

        setItem(12, SInventoryItem(Material.PLAYER_HEAD).setDisplayName("§6パーティを組んで挑戦しよう！")
            .addLore("§fパーティを組むことで有利に戦えるぞ！")
            .addLore("§d/dungeon party")
            .setCanClick(false))

        setItem(14, SInventoryItem(Material.SPAWNER).setDisplayName("§bタスクをクリアして次の階へ進もう！")
            .addLore("§f右側に表示されるタスクをこなすと上の階へ進むことができる！")
            .setCanClick(false))

        setItem(16, SInventoryItem(Material.WARPED_STAIRS).setDisplayName("§c次の階へ進む階段を見つけよう！")
            .addLore("§fこの階段の上に半分のプレイヤーが乗ると次の階へ進めるぞ！")
            .setCanClick(false))

        setItem(28, SInventoryItem(Material.CHEST).setDisplayName("§eアイテムを集めよう！")
            .addLore("§fモブを倒しきったらチェストを開けよう！")
            .addLore("§fクリアすると持ち帰ることができる！"))

        setItems(32..34, SInventoryItem(Material.RED_STAINED_GLASS_PANE).setDisplayName("§c§l使えないアイテム(クリックで移動)")
            .setCanClick(false)
            .setClickEvent {
                moveChildInventory(CannotUseItems(), it.whoClicked as Player)
            })


        return true
    }
}