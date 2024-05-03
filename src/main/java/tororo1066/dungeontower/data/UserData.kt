package tororo1066.dungeontower.data

import tororo1066.dungeontower.save.SaveDataDB
import tororo1066.dungeontower.skilltree.AbstractPerk
import tororo1066.dungeontower.skilltree.ActionType
import tororo1066.tororopluginapi.utils.toPlayer
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.collections.HashMap

class UserData() {
    lateinit var uuid: UUID
    var mcid = ""
    var ip = ""
    //生きているかどうか
    var isAlive = true

    val perks = HashMap<String, AbstractPerk>()

    constructor(uuid: UUID, mcid: String, ip: String) : this() {
        this.uuid = uuid
        this.mcid = mcid
        this.ip = ip
    }

    fun loadPerk(towerName: String): CompletableFuture<Void> {
        return SaveDataDB.load(uuid).thenAccept { list ->
            val data = list.find { it.towerName == towerName }?:return@thenAccept
            data.perks.forEach { (category, perkElements) ->
                perkElements.forEach { perkName ->
                    val perk = AbstractPerk.getPerk(category, perkName)
                    perks[perkName] = perk
                }
            }
        }
    }

    fun invokePerk(actionType: ActionType){
        val p = uuid.toPlayer()?:return
        perks.values.forEach second@ { perk ->
            perk.onAction(p, actionType, this)
        }
    }
}