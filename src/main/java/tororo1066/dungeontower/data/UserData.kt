package tororo1066.dungeontower.data

import java.util.*

class UserData() {
    lateinit var uuid: UUID
    var mcid = ""
    //生きているかどうか
    var isAlive = true

    constructor(uuid: UUID, mcid: String) : this() {
        this.uuid = uuid
        this.mcid = mcid
    }
}