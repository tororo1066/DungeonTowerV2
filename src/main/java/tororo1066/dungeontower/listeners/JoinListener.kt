package tororo1066.dungeontower.listeners

import org.bukkit.event.player.PlayerJoinEvent
import tororo1066.dungeontower.save.SaveDataDB
import tororo1066.tororopluginapi.annotation.SEventHandler

class JoinListener {

    @SEventHandler
    fun onJoin(e: PlayerJoinEvent) {
        SaveDataDB.create(e.player.uniqueId)
    }
}