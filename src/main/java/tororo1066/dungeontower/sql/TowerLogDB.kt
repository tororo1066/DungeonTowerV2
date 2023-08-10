package tororo1066.dungeontower.sql

import com.mongodb.client.model.Updates
import org.bson.Document
import tororo1066.dungeontower.DungeonTower
import tororo1066.dungeontower.data.PartyData
import tororo1066.tororopluginapi.database.SDBCondition
import tororo1066.tororopluginapi.database.SDBVariable
import tororo1066.tororopluginapi.database.SDatabase
import tororo1066.tororopluginapi.database.SDatabase.Companion.toSQLVariable
import java.util.*

class TowerLogDB {

    init {
        database = SDatabase.newInstance(DungeonTower.plugin)
        if (!database.isMongo){
            database.createTable("party_log",
                mapOf(
                    "id" to SDBVariable(SDBVariable.Int, autoIncrement = true),
                    "party_uuid" to SDBVariable(SDBVariable.VarChar, length = 36, nullable = false, index = SDBVariable.Index.UNIQUE),
                    "uuid" to SDBVariable(SDBVariable.Text),
                    "name" to SDBVariable(SDBVariable.Text)
                ))
        }

        database.createTable("tower_log",
            mapOf(
                "id" to SDBVariable(SDBVariable.Int, autoIncrement = true),
                "party_uuid" to SDBVariable(SDBVariable.VarChar, length = 36,false,SDBVariable.Index.KEY),
                "action" to SDBVariable(SDBVariable.VarChar, length = 50),
                "value" to SDBVariable(SDBVariable.Text),
                "date" to SDBVariable(SDBVariable.DateTime)
            ))
    }

    companion object{

        lateinit var database: SDatabase
        fun insertPartyData(partyData: PartyData){
            if (database.isMongo){
                database.backGroundInsert("tower_log",
                    mapOf(
                        "party_uuid" to partyData.partyUUID.toString(),
                        "users" to partyData.players.map {
                            mapOf("uuid" to it.key.toString(), "name" to it.value.mcid)
                        },
                        "actions" to listOf<Document>()
                    ))
            } else {
                database.backGroundInsert("party_log",
                    mapOf(
                        "party_uuid" to partyData.partyUUID,
                        "uuid" to partyData.players.keys.joinToString("\r\n"),
                        "name" to partyData.players.map { it.value.mcid }.joinToString("\r\n")
                    ))
            }
        }

        fun anyAction(partyData: PartyData, action: String, value: String){
            if (database.isMongo){
                database.backGroundUpdate("tower_log",
                    Updates.push("actions",
                        Document(mapOf("action" to action, "value" to value, "date" to Date()))
                    ), SDBCondition().equal("party_uuid", partyData.partyUUID.toString())
                )
            } else {
                database.backGroundInsert("tower_log",
                    mapOf(
                        "party_uuid" to partyData.partyUUID,
                        "action" to action,
                        "value" to value,
                        "date" to "now()"
                    ))
            }
        }

        fun selectTodayEntryCount(): Int {
            val time = Calendar.getInstance().apply {
                set(Calendar.HOUR, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.time
            return if (database.isMongo){
                database.asyncSelect("tower_log",
                    SDBCondition().equal("action", "ENTER_DUNGEON")
                        .and().lessThan("actions.date", time)
                ).get().count()
            } else {
                database.asyncSelect("tower_log",
                    SDBCondition().equal("action", "ENTER_DUNGEON")
                        .and().lessThan("date", time.toSQLVariable(SDBVariable.DateTime))
                ).get().count()
            }
        }

        fun enterDungeon(partyData: PartyData, towerName: String){
            anyAction(partyData, "ENTER_DUNGEON", towerName)
        }

        fun clearDungeon(partyData: PartyData, towerName: String){
            anyAction(partyData, "CLEAR_DUNGEON", towerName)
        }

        fun annihilationDungeon(partyData: PartyData, towerName: String){
            anyAction(partyData, "ANNIHILATION_DUNGEON", towerName)
        }

        fun timeOutDungeon(partyData: PartyData, towerName: String){
            anyAction(partyData, "TIMEOUT_DUNGEON", towerName)
        }

        fun quitDisbandDungeon(partyData: PartyData, towerName: String){
            anyAction(partyData, "QUIT_DISBAND_DUNGEON", towerName)
        }
    }


}