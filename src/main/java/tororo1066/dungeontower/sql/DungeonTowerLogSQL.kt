package tororo1066.dungeontower.sql

import tororo1066.dungeontower.data.PartyData
import tororo1066.tororopluginapi.SJavaPlugin
import tororo1066.tororopluginapi.mysql.ultimate.USQLTable
import tororo1066.tororopluginapi.mysql.ultimate.USQLVariable

@Suppress("UNUSED")
class DungeonTowerLogSQL: USQLTable("dungeon_tower_log", SJavaPlugin.mysql) {

    companion object {
        val id = USQLVariable(USQLVariable.Int, autoIncrement = true)
        val party_uuid = USQLVariable(USQLVariable.VarChar, length = 36,false,USQLVariable.Index.KEY)
        val action = USQLVariable(USQLVariable.VarChar, length = 50)
        val value = USQLVariable(USQLVariable.Text)
        val date = USQLVariable(USQLVariable.DateTime)

        lateinit var sql: DungeonTowerLogSQL

        fun enterDungeon(partyData: PartyData, towerName: String){
            sql.callBackInsert(partyData.partyUUID, "ENTER_DUNGEON", towerName, "now()")
        }

        fun clearDungeon(partyData: PartyData, towerName: String){
            sql.callBackInsert(partyData.partyUUID, "CLEAR_DUNGEON", towerName, "now()")
        }

        fun annihilationDungeon(partyData: PartyData, towerName: String){
            sql.callBackInsert(partyData.partyUUID, "ANNIHILATION_DUNGEON", towerName, "now()")
        }

        fun timeOutDungeon(partyData: PartyData, towerName: String){
            sql.callBackInsert(partyData.partyUUID, "TIMEOUT_DUNGEON", towerName, "now()")
        }

        fun quitDisbandDungeon(partyData: PartyData, towerName: String){
            sql.callBackInsert(partyData.partyUUID, "QUIT_DISBAND_DUNGEON", towerName, "now()")
        }
    }

    init {
        sql = this
    }
}