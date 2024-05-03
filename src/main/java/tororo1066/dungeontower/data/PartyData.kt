package tororo1066.dungeontower.data

import org.bukkit.Location
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scoreboard.Scoreboard
import tororo1066.dungeontower.DungeonTower
import tororo1066.dungeontower.skilltree.ActionType
import tororo1066.dungeontower.task.DungeonTowerTask
import tororo1066.tororopluginapi.SStr
import tororo1066.tororopluginapi.utils.sendMessage
import tororo1066.tororopluginapi.utils.toPlayer
import java.util.UUID
import java.util.concurrent.CompletableFuture

class PartyData: Cloneable {
    val players = HashMap<UUID,UserData>()
    lateinit var parent: UUID
    var nowTask: DungeonTowerTask? = null
    val partyUUID: UUID = UUID.randomUUID()
    val alivePlayers: HashMap<UUID,UserData> get() {
        return HashMap(players.filter { it.value.isAlive })
    }

    fun broadCast(str: SStr){
        players.keys.forEach {
            it.toPlayer()?.sendMessage(DungeonTower.prefix + str)
        }
    }

    fun smokeStan(tick: Int){
        players.keys.forEach {
            val p = it.toPlayer()?:return@forEach
            p.addPotionEffect(PotionEffect(PotionEffectType.SLOW,tick,10,false,false,false))
            p.addPotionEffect(PotionEffect(PotionEffectType.JUMP,tick,200,false,false,false))
            p.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS,tick,1,false,false,false))
        }
    }

    fun teleport(loc: Location){
        players.keys.forEach {
            it.toPlayer()?.teleport(loc)
        }
    }

    fun actionBar(str: SStr){
        players.keys.forEach {
            it.toPlayer()?.sendActionBar(str.toPaperComponent())
        }
    }

    fun scoreboard(scoreboard: Scoreboard){
        players.keys.forEach {
            it.toPlayer()?.scoreboard = scoreboard
        }
    }

    fun loadPerk(towerName: String): CompletableFuture<Void> {
        return CompletableFuture.allOf(*players.values.map { it.loadPerk(towerName) }.toTypedArray())
    }

    fun registerPerk(){
        players.values.forEach {
            it.perks.values.forEach second@ { perk ->
                perk.registerPerk(it.uuid.toPlayer()?:return@forEach, it)
            }
        }
    }

    fun invokePerk(actionType: ActionType){
        players.values.forEach {
            it.invokePerk(actionType)
        }
    }

    public override fun clone(): PartyData {
        return super.clone() as PartyData
    }
}