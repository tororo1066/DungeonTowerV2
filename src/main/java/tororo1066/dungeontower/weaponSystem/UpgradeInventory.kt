package tororo1066.dungeontower.weaponSystem

import com.elmakers.mine.bukkit.magic.Mage
import com.elmakers.mine.bukkit.wand.Wand
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.PlayerInventory
import tororo1066.dungeontower.DungeonTower
import tororo1066.tororopluginapi.SJavaPlugin
import tororo1066.tororopluginapi.sInventory.SInventory
import tororo1066.tororopluginapi.sInventory.SInventoryItem
import tororo1066.tororopluginapi.utils.returnItem

class UpgradeInventory: SInventory(SJavaPlugin.plugin, "§c武器の強化", 3) {

    var selectedWeapon: CustomWeapon? = null
    var selectedUpgradeItem: ItemStack? = null

    init {
        setOnClick {
            it.isCancelled = true


            val item = it.currentItem ?: return@setOnClick


            val clickedInventory = it.clickedInventory ?: return@setOnClick
            val player = it.whoClicked as? Player ?: return@setOnClick
            if (clickedInventory is PlayerInventory) {
                selectedWeapon?.let { weapon ->
                    val levelData = weapon.getNextLevelData() ?: return@setOnClick
                    if (item.isSimilar(levelData.getUpgradeItem())) {
                        val cloned = item.clone().apply { amount = 1 }
                        item.amount -= 1
                        selectedUpgradeItem?.let { upgradeItem ->
                            player.returnItem(upgradeItem)
                        }
                        selectedUpgradeItem = cloned
                        setItem(15, SInventoryItem(cloned).setCanClick(false))
                    } else {
                        val newWeapon = getWeapon(item)
                        if (newWeapon == null) {
                            player.sendMessage("§cこのアイテムで武器を強化することはできません")
                            return@setOnClick
                        }
                        val cloned = item.clone().apply { amount = 1 }
                        newWeapon.wand.item = cloned
                        item.amount -= 1
                        weapon.wand.item?.let { itemStack -> player.returnItem(itemStack) }
                        selectedWeapon = newWeapon
                        setItem(11, SInventoryItem(cloned).setCanClick(false))
                    }
                } ?: run {
                    val weapon = getWeapon(item) ?: return@setOnClick
                    val cloned = item.clone().apply { amount = 1 }
                    weapon.wand.item = cloned
                    item.amount -= 1
                    selectedWeapon = weapon
                    setItem(11, SInventoryItem(cloned).setCanClick(false))
                    setItem(13, SInventoryItem(Material.GRAY_STAINED_GLASS_PANE)
                        .setDisplayName("§f")
                        .setCanClick(false))
                    setItem(15, SInventoryItem(Material.BARRIER)
                        .setDisplayName("§a強化アイテムをクリックで選択")
                        .setCanClick(false))
                }
            } else {
                when (it.slot) {
                    11 -> {
                        selectedWeapon?.let { weapon ->
                            val itemStack = weapon.wand.item ?: return@setOnClick
                            player.returnItem(itemStack)
                            selectedWeapon = null
                            selectedUpgradeItem?.let { upgradeItem ->
                                player.returnItem(upgradeItem)
                                selectedUpgradeItem = null
                            }
                            setItems(
                                listOf(11, 15),
                                SInventoryItem(Material.GRAY_STAINED_GLASS_PANE)
                                    .setDisplayName("§f")
                                    .setCanClick(false)
                            )

                            setItem(
                                13,
                                SInventoryItem(Material.BARRIER)
                                    .setDisplayName("§a武器をクリックで選択")
                                    .setCanClick(false)
                            )
                        }
                    }
                    15 -> {
                        selectedUpgradeItem?.let { upgradeItem ->
                            player.returnItem(upgradeItem)
                            selectedUpgradeItem = null
                            setItem(
                                15,
                                SInventoryItem(Material.BARRIER)
                                    .setDisplayName("§a強化アイテムをクリックで選択")
                                    .setCanClick(false)
                            )
                        }
                    }
                }
            }
        }

        setOnClose {
            val player = it.player as? Player ?: return@setOnClose
            selectedWeapon?.wand?.item?.let { itemStack ->
                player.returnItem(itemStack)
            }
            selectedUpgradeItem?.let { upgradeItem ->
                player.returnItem(upgradeItem)
            }

            selectedWeapon = null
            selectedUpgradeItem = null
        }
    }

    private fun getWeapon(item: ItemStack): CustomWeapon? {
        val wand = DungeonTower.magicAPI.controller.getIfWand(item) ?: return null
        return CustomWeapon.getWeapon(wand as Wand)
    }

    override fun renderMenu(p: Player): Boolean {
        setItems(
            0..26,
            SInventoryItem(Material.GRAY_STAINED_GLASS_PANE)
                .setDisplayName("§f")
                .setCanClick(false)
        )

        setItem(
            13,
            SInventoryItem(Material.BARRIER)
                .setDisplayName("§a武器をクリックで選択")
                .setCanClick(false)
        )

        setItem(
            22,
            SInventoryItem(Material.LIME_STAINED_GLASS_PANE)
                .setDisplayName("§a強化")
                .setCanClick(false)
                .setClickEvent { _ ->
                    val weapon = selectedWeapon
                    val upgradeItem = selectedUpgradeItem

                    if (weapon == null) {
                        p.sendMessage("§c武器が選択されていません")
                        return@setClickEvent
                    }
                    if (upgradeItem == null) {
                        p.sendMessage("§c強化アイテムを選択してください")
                        return@setClickEvent
                    }

                    val levelData = weapon.getNextLevelData()
                    if (levelData == null) {
                        p.sendMessage("§cこれ以上強化できません")
                        return@setClickEvent
                    }
                    if (!upgradeItem.isSimilar(levelData.getUpgradeItem())) {
                        p.sendMessage("§cこのアイテムでは強化できません")
                        return@setClickEvent
                    }

                    val mage = DungeonTower.magicAPI.controller.getMage(p) as? Mage
                    if (mage == null) {
                        p.sendMessage("§c内部エラー 運営に報告してください")
                        return@setClickEvent
                    }
                    weapon.setLevel(weapon.level + 1, p)
                    val item = weapon.wand.item
                    if (item == null) {
                        p.sendMessage("§c内部エラー 運営に報告してください")
                        return@setClickEvent
                    }

                    selectedWeapon = null
                    selectedUpgradeItem = null

                    setItems(
                        listOf(11, 15),
                        SInventoryItem(Material.GRAY_STAINED_GLASS_PANE)
                            .setDisplayName("§f")
                            .setCanClick(false)
                    )

                    setItem(
                        13,
                        SInventoryItem(Material.BARRIER)
                            .setDisplayName("§a武器をクリックで選択")
                            .setCanClick(false)
                    )

                    p.returnItem(item)
                    p.sendMessage("§a武器を強化しました！")
                    p.playSound(p.location, Sound.BLOCK_ANVIL_USE, 1f, 1f)
                }
        )

        return true
    }
}