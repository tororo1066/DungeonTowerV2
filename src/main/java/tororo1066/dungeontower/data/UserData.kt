package tororo1066.dungeontower.data

import tororo1066.dungeontower.skilltree.AbstractPark
import tororo1066.dungeontower.skilltree.ActionType
import tororo1066.tororopluginapi.utils.toPlayer
import java.util.*
import kotlin.collections.HashMap

class UserData() {
    lateinit var uuid: UUID
    var mcid = ""
    var ip = ""
    //生きているかどうか
    var isAlive = true

    val parks = HashMap<String, AbstractPark>()

    constructor(uuid: UUID, mcid: String, ip: String) : this() {
        this.uuid = uuid
        this.mcid = mcid
        this.ip = ip
    }

    fun invokePark(actionType: ActionType){
        val p = uuid.toPlayer()?:return
        parks.values.forEach second@ { park ->
            park.onAction(p, actionType, this)
        }
    }
}