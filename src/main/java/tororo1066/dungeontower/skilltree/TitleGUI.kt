package tororo1066.dungeontower.skilltree

import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.scheduler.BukkitRunnable
import tororo1066.dungeontower.DungeonTower
import java.time.Duration
import kotlin.math.abs


class TitleGUI(val p: Player): Listener {

    var initYaw = 0f
    var initPitch = 0f
    var yLocation = 0
    var xLocation = 0
    lateinit var runnable: BukkitRunnable

    fun show(){
        initYaw = p.location.yaw
        initPitch = 0f
        Bukkit.getPluginManager().registerEvents(this,DungeonTower.plugin)
        runnable = object : BukkitRunnable() {

            var lastYaw = 0f
            var yawOffset = 0f

            override fun run() {
                val pitch = p.location.pitch
                val pitchDiff = -(pitch - initPitch)
                yLocation = 9
                if (pitchDiff >= 0) {
                    yLocation += (pitchDiff / 10).toInt()
                } else {
                    yLocation -= (abs(pitchDiff) / 10).toInt()
                }

                val builder = StringBuilder().append('\uE000' + yLocation)

                val yaw = p.location.yaw
                if (lastYaw > 140 && yaw < -140) {
                    yawOffset += 360f
                } else if (lastYaw < -140 && yaw > 140) {
                    yawOffset -= 360f
                }

                val yawDiff = (yaw - initYaw + yawOffset) * 2

                var i = 0
                if (yawDiff >= 0) {
                    while (i < yawDiff / 4) {
                        builder.append("«")
                        i++
                    }
                } else {
                    while (i < abs(yawDiff.toDouble()) / 4) {
                        builder.append(" ")
                        i++
                    }
                }
                xLocation = if (yawDiff >= 0) i else -i
                if (yLocation in 15..16 && xLocation in 14..36){
                    val component = Component.text("    \uE032")
                        .font(Key.key("custom_ui"))
                        .color(TextColor.color(78, 92, 36))
                        .append(
                            Component.text("««««««検証文字列")
                                .font(Key.key("font8"))
                                .color(TextColor.color(255, 255, 255))
                        )
                    p.showTitle(Title.title(component
                        ,Component.text(""),Title.Times.times(Duration.ZERO, Duration.ofSeconds(1), Duration.ZERO)))
                    p.sendActionBar(Component.text(builder.toString()).font(Key.key("custom_ui")))
                } else {
                    val component = Component.text("\uE032")
                        .font(Key.key("custom_ui"))
                        .color(TextColor.color(78, 92, 36))
                        .append(
                            Component.text("««««««検証")
                                .font(Key.key("font8"))
                                .color(TextColor.color(255, 255, 255))
                        )
                    p.showTitle(Title.title(component
                        ,Component.text(""),Title.Times.times(Duration.ZERO, Duration.ofSeconds(1), Duration.ZERO)))
                    p.sendActionBar(Component.text(builder.toString()).font(Key.key("custom_ui")))
                }
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
    }
}