package tororo1066.dungeontower.sql

import tororo1066.dungeontower.data.PartyData
import tororo1066.tororopluginapi.SJavaPlugin
import tororo1066.tororopluginapi.mysql.ultimate.USQLTable
import tororo1066.tororopluginapi.mysql.ultimate.USQLVariable

@Suppress("UNUSED")
class DungeonTowerPartyLogSQL: USQLTable("party_log",SJavaPlugin.mysql) {

    companion object {
        val id = USQLVariable(USQLVariable.Int, nullable = false, index = USQLVariable.Index.PRIMARY)
        val party_uuid = USQLVariable(USQLVariable.VarChar, length = 36, nullable = false, index = USQLVariable.Index.KEY)
        val uuid = USQLVariable(USQLVariable.Text)
        val name = USQLVariable(USQLVariable.Text)

        lateinit var sql: DungeonTowerPartyLogSQL

        fun insert(partyData: PartyData){
            sql.callBackInsert(partyData.partyUUID,
                partyData.players.keys.joinToString(","),
                partyData.players.map { it.value.mcid }.joinToString(","))
        }
    }

    init {
        sql = this
    }
}