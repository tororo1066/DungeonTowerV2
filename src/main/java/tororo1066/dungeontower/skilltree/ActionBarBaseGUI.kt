package tororo1066.dungeontower.skilltree

import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.scheduler.BukkitRunnable
import tororo1066.dungeontower.DungeonTower
import tororo1066.dungeontower.DungeonTower.Companion.sendPrefixMsg
import tororo1066.dungeontower.command.DungeonCommand
import tororo1066.dungeontower.data.TowerData
import tororo1066.dungeontower.save.SaveDataDB
import tororo1066.tororopluginapi.SStr
import java.time.Duration
import kotlin.math.abs

open class ActionBarBaseGUI(val p: Player, val towerData: TowerData, val menuChar: Char): Listener {

    init {
        p.sendPrefixMsg(SStr("&b&lシフト右クリックで閉じる"))
        SaveDataDB.load(p.uniqueId).thenAcceptAsync { data ->
            val thisData = data?.find { it.towerName == towerData.internalName }
            if (thisData == null) {
                SaveDataDB.save(p.uniqueId, towerData, perkPoints = towerData.defaultPerkPoints).thenAcceptAsync { result ->
                    if (!result){
                        p.sendMessage("§cエラーが発生しました")
                    }
                }.join()
            }
        }
    }

    var initYaw = 0f
    var initPitch = 0f
    var yLocation = 0
    var xLocation = 0
    lateinit var runnable: BukkitRunnable

    val items = LinkedHashMap<PerkLocation, AbstractPerk>()
    val largeItems = LinkedHashMap<PerkLocation, AbstractPerk>()
    val middleItems = LinkedHashMap<PerkLocation, AbstractPerk>()
    val smallItems = LinkedHashMap<PerkLocation, AbstractPerk>()

    var clickCoolDown = false
    var selectedPerk: AbstractPerk? = null

    var onCursor: ((Int, Int) -> Component)? = null
    private fun onClick(x: Int, y: Int) {
        if (!clickCoolDown) {
            val perk = items.entries.find { it.key.xLocation.contains(x) && it.key.yLocation.contains(y) }?.value
            if (perk != null) {
                clickCoolDown = true
                SaveDataDB.load(p.uniqueId).thenAcceptAsync { data ->
                    if (data == null) {
                        p.sendMessage("§cエラーが発生しました")
                        clickCoolDown = false
                        return@thenAcceptAsync
                    }
                    val saveData = data.find { it.towerName == towerData.internalName }
                    if (saveData == null) {
                        p.sendMessage("§cデータが存在しません")
                        clickCoolDown = false
                        return@thenAcceptAsync
                    }
                    if (selectedPerk != null && selectedPerk == perk) {
                        if (saveData.perks.any { it.value.contains(perk.javaClass.simpleName) }) {
                            p.sendMessage("§c既に解放されています")
                            clickCoolDown = false
                            return@thenAcceptAsync
                        }

                        if (saveData.perkPoints < perk.cost) {
                            p.sendMessage("§c解放に必要なポイントが足りません")
                            clickCoolDown = false
                            return@thenAcceptAsync
                        }

                        if (perk.blockedBy.any { saveData.perks.any { any -> any.value.contains(it.simpleName) } }) {
                            p.sendMessage("§c特定のスキルにブロックされています")
                            clickCoolDown = false
                            return@thenAcceptAsync
                        }

                        if (perk.needPerks.isNotEmpty() && !perk.needPerks.any { it.all { saveData.perks.any { any -> any.value.contains(it.simpleName) } } }) {
                            p.sendMessage("§c必要なスキルを習得していません")
                            clickCoolDown = false
                            return@thenAcceptAsync
                        }

                        val result = SaveDataDB.save(
                            p.uniqueId,
                            towerData,
                            perkPoints = saveData.perkPoints - perk.cost,
                            perks = saveData.perks.apply {
                                this[perk.category] = this[perk.category]?.apply {
                                    this.add(perk.javaClass.simpleName)
                                } ?: arrayListOf(perk.javaClass.simpleName)
                            }).get()
                        if (result) {
                            perk.onLearned(p)
                            p.sendMessage("${perk.getSkillName()}§aを習得しました")
                            selectedPerk = null
                        } else {
                            p.sendMessage("§cエラーが発生しました")
                        }
                        clickCoolDown = false
                    } else {
                        p.sendMessage("§c§l==============================")
                        p.sendMessage(perk.getSkillName())
                        perk.getSkillDescription().forEach {
                            p.sendMessage(it)
                        }
                        p.sendMessage("                                      ")
                        p.sendMessage("§9解放に必要なパーク")
                        perk.getNeedPerkNames().let {
                            if (it.isEmpty()) {
                                p.sendMessage("§aなし")
                            } else {
                                it.forEachIndexed { index, perkList ->
                                    if (index > 0) p.sendMessage("§7または")
                                    perkList.forEach { perkName ->
                                        p.sendMessage(perkName)
                                    }
                                }
                            }
                        }
                        p.sendMessage("                                      ")
                        p.sendMessage("§c解放してはいけないパーク")
                        perk.getBlockedPerkNames().let {
                            if (it.isEmpty()) {
                                p.sendMessage("§aなし")
                            } else {
                                it.forEach { perkName ->
                                    p.sendMessage(perkName)
                                }
                            }
                        }
                        p.sendMessage("                                      ")
                        p.sendMessage("§a§l解放に必要なポイント: ${perk.cost}")
                        p.sendMessage("                                      ")
                        p.sendMessage("§d§lもう一度クリックで解放")
                        p.sendMessage("§c§l==============================")
                        selectedPerk = perk
                        clickCoolDown = false
                    }
                }
            }
        }
    }
    var onClick: ((Int, Int) -> Unit)? = { x, y ->
        onClick(x, y)
    }

    protected fun registerItems(vararg items: AbstractPerk){
        for (item in items){
            val location = item.getLocation()
            this.items[location] = item
            when(item.texture.type) {
                Type.LARGE -> largeItems[location] = item
                Type.MIDDLE -> middleItems[location] = item
                Type.SMALL -> smallItems[location] = item
            }
        }
    }

    fun show(){
        DungeonCommand.perkOpeningPlayers[p.uniqueId] = this
        initYaw = p.location.yaw
        initPitch = 0f
        Bukkit.getPluginManager().registerEvents(this, DungeonTower.plugin)
        runnable = object : BukkitRunnable() {

            var lastYaw = 0f
            var yawOffset = 0f

            override fun run() {
                val pitch = p.location.pitch
                val pitchDiff = -(pitch - initPitch)
                yLocation = 18
                if (pitchDiff >= 0) {
                    yLocation += (pitchDiff / 5).toInt()
                } else {
                    yLocation -= (abs(pitchDiff) / 5).toInt()
                }

                val builder = StringBuilder().append('\uE000' + yLocation)

                val yaw = p.location.yaw
                if (lastYaw > 140 && yaw < -140) {
                    yawOffset += 360f
                } else if (lastYaw < -140 && yaw > 140) {
                    yawOffset -= 360f
                }

                val yawDiff = ((yaw - initYaw + yawOffset) * 2).toInt()

                var i = 0
                if (yawDiff >= 0) {
                    while (i < yawDiff / 4) {
                        builder.append("«")
                        i++
                    }
                } else {
                    while (i < abs(yawDiff) / 4) {
                        builder.append(" ")
                        i++
                    }
                }
                xLocation = if (yawDiff >= 0) i else -i
                val component = text("                 ¡¢${menuChar}")
                    .font(Key.key("custom_ui"))
                    .color(TextColor.color(78, 92, 36))
                    .append(
                        //                                                                                       小(上)        小(下)     中(1)           小(中)         大(1)                                       中(2)              小(中)   大(2)                                             小(中)         中(3)        小(下)        小(上)
                        text("««««««««««««««««««««««««««««««««««««««««««««««««««««««««««««««««««««««««««»»${smallItems.getCharForIndex(0)}«««««¬${smallItems.getCharForIndex(1)}«««¬${middleItems.getCharForIndex(0)}«««««««««»¬${smallItems.getCharForIndex(2)}«««««««»${largeItems.getCharForIndex(0)}««««««${middleItems.getCharForIndex(1)}««««««««««««¬¬${smallItems.getCharForIndex(3)}««${largeItems.getCharForIndex(1)}«««««««««««»¬${smallItems.getCharForIndex(4)}««««««»¬${middleItems.getCharForIndex(2)}«««««««¬${smallItems.getCharForIndex(5)}«««««»${smallItems.getCharForIndex(6)}                                   ")
                            .font(Key.key("custom_ui"))
                            .color(TextColor.color(255, 255, 255))
                    )
                p.showTitle(
                    Title.title(text(builder.toString()).font(Key.key("custom_ui"))
                        , onCursor?.invoke(xLocation, yLocation)?: text(""), Title.Times.times(Duration.ZERO, Duration.ofSeconds(1), Duration.ZERO)))
                p.sendActionBar(component)

                lastYaw = yaw
            }
        }

        runnable.runTaskTimer(DungeonTower.plugin,0,1)
    }

    fun stop(){
        DungeonCommand.perkOpeningPlayers.remove(p.uniqueId)
        runnable.cancel()
        HandlerList.unregisterAll(this)
    }

    private fun HashMap<PerkLocation, AbstractPerk>.getCharForIndex(index: Int): Char {
        return this.toList()[index].second.texture.char
    }



    @EventHandler
    fun onInteract(e: PlayerInteractEvent){
        if (e.useInteractedBlock() == Event.Result.DEFAULT)return
        if (e.hand != EquipmentSlot.HAND)return
        if (e.player != p)return
        if (e.player.isSneaking && e.action.isRightClick) {
            stop()
            return
        }
        onClick?.invoke(xLocation, yLocation)
    }
}