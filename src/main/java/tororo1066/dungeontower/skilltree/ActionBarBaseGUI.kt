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
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.scheduler.BukkitRunnable
import tororo1066.dungeontower.DungeonTower
import tororo1066.dungeontower.data.TowerData
import tororo1066.dungeontower.save.SaveDataDB
import tororo1066.tororopluginapi.sEvent.SEvent
import java.time.Duration
import kotlin.math.abs

open class ActionBarBaseGUI(val p: Player, val towerData: TowerData, val menuChar: Char): Listener {

    init {
        SaveDataDB.load(p.uniqueId).thenAcceptAsync { data ->
            val thisData = data?.find { it.towerName == towerData.internalName }
            if (thisData == null) {
                SaveDataDB.save(p.uniqueId, towerData, parkPoints = towerData.defaultParkPoints).thenAcceptAsync { result ->
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

    val items = LinkedHashMap<ParkLocation, AbstractPark>()
    val largeItems = LinkedHashMap<ParkLocation, AbstractPark>()
    val middleItems = LinkedHashMap<ParkLocation, AbstractPark>()
    val smallItems = LinkedHashMap<ParkLocation, AbstractPark>()

    var clickCoolDown = false
    var selectedPark: AbstractPark? = null

    var onCursor: ((Int, Int) -> Component)? = null
//    var onCursor: ((Int, Int) -> Component)? = { x, y ->
//        val park = items.entries.find { it.key.xLocation.contains(x) && it.key.yLocation.contains(y) }?.value
//        if (park != null){
//            val builder = StringBuilder()
//            for (i in 1..abs(xLocation)) {
//                if (xLocation > 0) {
//                    builder.append("  ")
//                } else {
//                    builder.append("««")
//                }
//            }
//            builder.append("クリックで詳細を見る")
//            val text = text(builder.toString()).font(Key.key("font_plus_${abs(yLocation).toInt() - abs(yLocation).toInt() % 4}"))
//            text
//        } else {
//            text("")
//        }
//    }
    fun onClick(x: Int, y: Int) {
        if (!clickCoolDown) {
            val park = items.entries.find { it.key.xLocation.contains(x) && it.key.yLocation.contains(y) }?.value
            if (park != null) {
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
                    if (selectedPark != null && selectedPark == park) {
                        if (saveData.parks.any { it.value.contains(park.javaClass.simpleName) }) {
                            p.sendMessage("§c既に解放されています")
                            clickCoolDown = false
                            return@thenAcceptAsync
                        }

                        if (saveData.parkPoints < park.cost) {
                            p.sendMessage("§c解放に必要なポイントが足りません")
                            clickCoolDown = false
                            return@thenAcceptAsync
                        }

                        if (park.blockedBy.any { saveData.parks.any { any -> any.value.contains(it.simpleName) } }) {
                            p.sendMessage("§c特定のスキルにブロックされています")
                            clickCoolDown = false
                            return@thenAcceptAsync
                        }

                        if (park.needParks.isNotEmpty() && !park.needParks.any { it.all { saveData.parks.any { any -> any.value.contains(it.simpleName) } } }) {
                            p.sendMessage("§c必要なスキルを習得していません")
                            clickCoolDown = false
                            return@thenAcceptAsync
                        }

                        val result = SaveDataDB.save(
                            p.uniqueId,
                            towerData,
                            parkPoints = saveData.parkPoints - park.cost,
                            parks = saveData.parks.apply {
                                this[park.category] = this[park.category]?.apply {
                                    this.add(park.javaClass.simpleName)
                                } ?: arrayListOf(park.javaClass.simpleName)
                            }).get()
                        if (result) {
                            park.onLearned(p)
                            p.sendMessage("${park.getSkillName()}§aを習得しました")
                            selectedPark = null
                        } else {
                            p.sendMessage("§cエラーが発生しました")
                        }
                        clickCoolDown = false
                    } else {
                        p.sendMessage("§c§l==============================")
                        p.sendMessage(park.getSkillName())
                        park.getSkillDescription().forEach {
                            p.sendMessage(it)
                        }
                        p.sendMessage("                                      ")
                        p.sendMessage("§9解放に必要なパーク")
                        park.getNeedParkNames().let {
                            if (it.isEmpty()) {
                                p.sendMessage("§aなし")
                            } else {
                                it.forEachIndexed { index, parkList ->
                                    if (index > 0) p.sendMessage("§7または")
                                    parkList.forEach { parkName ->
                                        p.sendMessage(parkName)
                                    }
                                }
                            }
                        }
                        p.sendMessage("                                      ")
                        p.sendMessage("§c解放してはいけないパーク")
                        park.getBlockedParkNames().let {
                            if (it.isEmpty()) {
                                p.sendMessage("§aなし")
                            } else {
                                it.forEach { parkName ->
                                    p.sendMessage(parkName)
                                }
                            }
                        }
                        p.sendMessage("                                      ")
                        p.sendMessage("§a§l解放に必要なポイント: ${park.cost}")
                        p.sendMessage("                                      ")
                        p.sendMessage("§d§lもう一度クリックで解放")
                        p.sendMessage("§c§l==============================")
                        selectedPark = park
                        clickCoolDown = false
                    }
                }
            }
        }
    }
    var onClick: ((Int, Int) -> Unit)? = { x, y ->
        onClick(x, y)
    }

    protected fun registerItems(vararg items: AbstractPark){
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
                    Title.title(text(builder.toString()).font(Key.key("custom_ui_save"))
                        , onCursor?.invoke(xLocation, yLocation)?: text(""), Title.Times.times(Duration.ZERO, Duration.ofSeconds(1), Duration.ZERO)))
                p.sendActionBar(component)

                lastYaw = yaw
            }
        }

        runnable.runTaskTimer(DungeonTower.plugin,0,1)
    }

    fun stop(){
        runnable.cancel()
        HandlerList.unregisterAll(this)
    }

    private fun HashMap<ParkLocation, AbstractPark>.getCharForIndex(index: Int): Char {
        return this.toList()[index].second.texture.char
    }



    @EventHandler
    fun onInteract(e: PlayerInteractEvent){
        if (e.useInteractedBlock() == Event.Result.DENY)return
        if (e.hand != EquipmentSlot.HAND)return
        if (e.player != p)return
        onClick?.invoke(xLocation, yLocation)
    }
}