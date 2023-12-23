package tororo1066.dungeontower.skilltree

import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.scheduler.BukkitRunnable
import tororo1066.dungeontower.DungeonTower
import tororo1066.dungeontower.skilltree.parks.convenience.JustGuard
import java.time.Duration
import kotlin.math.abs

open class ActionBarBaseGUI(val p: Player): Listener {

    var initYaw = 0f
    var initPitch = 0f
    var yLocation = 0
    var xLocation = 0
    lateinit var runnable: BukkitRunnable

    val items = HashMap<ParkLocation, AbstractPark>()

    var onCursor: ((Int, Int) -> Component)? = { x, y ->
        val park = items.entries.find { it.key.xLocation.contains(x) && it.key.yLocation.contains(y) }?.value
        if (park != null){
            val builder = StringBuilder()
            for (i in 1..abs(xLocation)) {
                if (xLocation > 0) {
                    builder.append(" ")
                } else {
                    builder.append("«")
                }
            }
            builder.append(park.getSkillName())
            val text = text(builder.toString()).font(Key.key("font_plus_6"))
            text
        } else {
            text("")
        }
    }
    var onClick: ((Int, Int) -> Unit)? = { x, y ->
        val park = items.entries.find { it.key.xLocation.contains(x) && it.key.yLocation.contains(y) }?.value
        park?.registerSkill(p)
    }

    private fun registerItems(vararg items: AbstractPark){
        for (item in items){
            val location = item.getLocation()
            this.items[location] = item
        }
    }

    fun show(){
        registerItems(JustGuard())
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
                val component = text("                 ¡¢\uE028")
                    .font(Key.key("custom_ui_save"))
                    .color(TextColor.color(78, 92, 36))
                    .append(
                        //                                                                                        小(上)        小(下)     中(1)           小(中)         大(1)        中(2)              小(中)   大(2)            小(中)         中(3)        小(下)        小(上)
                        text("««««««««««««««««««««««««««««««««««««««««««««««««««««««««««««««««««««««««««»»¬\uE02E«««««»¬\uE030«««»\uE02B«««««««««»\uE02F«««««««»\uE029««««««\uE02C««««««««««««¬\uE02F««\uE02A««««««««««««\uE02F«««««««\uE02D««««««»\uE030«««««»¬\uE02E                                   ")
                            .font(Key.key("custom_ui_save"))
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



    @EventHandler
    fun onInteract(e: PlayerInteractEvent){
        if (e.player != p)return
        e.player.sendMessage("x:$xLocation y:$yLocation")
        onClick?.invoke(xLocation, yLocation)
    }
}