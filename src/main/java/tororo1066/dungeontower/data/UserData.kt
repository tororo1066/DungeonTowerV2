package tororo1066.dungeontower.data

import java.util.UUID

class UserData() {
    lateinit var uuid: UUID
    var mcid = ""
    var ip = ""
    //生きているかどうか
    var isAlive = true

    constructor(uuid: UUID, mcid: String, ip: String) : this() {
        this.uuid = uuid
        this.mcid = mcid
        this.ip = ip
    }
}