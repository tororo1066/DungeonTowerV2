package tororo1066.dungeontower.logging

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
                    "name" to SDBVariable(SDBVariable.Text),
                    "ip" to SDBVariable(SDBVariable.Text)
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

    companion object {

        lateinit var database: SDatabase
        fun insertPartyData(partyData: PartyData) {
            if (database.isMongo){
                database.insert("tower_log",
                    mapOf(
                        "party_uuid" to partyData.partyUUID.toString(),
                        "users" to partyData.players.map {
                            mapOf("uuid" to it.key.toString(), "name" to it.value.mcid, "ip" to it.value.ip)
                        },
                        "actions" to listOf<Document>()
                    ))
            } else {
                database.insert("party_log",
                    mapOf(
                        "party_uuid" to partyData.partyUUID.toString(),
                        "uuid" to partyData.players.keys.joinToString("\r\n") { it.toString() },
                        "name" to partyData.players.values.joinToString("\r\n") { it.mcid },
                        "ip" to partyData.players.values.joinToString("\r\n") { it.ip }
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
                        "party_uuid" to partyData.partyUUID.toString(),
                        "action" to action,
                        "value" to value,
                        "date" to Date().toSQLVariable(SDBVariable.DateTime)
                    ))
            }
        }

        fun getSubAccounts(uuid: UUID): List<UUID> {
            return if (database.isMongo) {
                val ips = database.asyncSelect("tower_log", SDBCondition().equal("users.uuid", uuid))
                    .get().flatMap { it.getList<Document>("users") }
                    .map { it.getString("ip") }.distinct()
                database.asyncSelect("tower_log", SDBCondition().include("users.ip", ips))
                    .get().flatMap { it.getList<Document>("users") }
                    .map { UUID.fromString(it.getString("uuid")) }.distinct()
            } else {
                val ips = database.asyncSelect("party_log", SDBCondition().like("uuid", uuid.toString()))
                    .get().flatMap { it.getString("ip").split("\r\n") }.distinct()
                database.asyncSelect("party_log", SDBCondition().include("ip", ips))
                    .get().flatMap { it.getString("uuid").split("\r\n") }
                    .map { UUID.fromString(it) }.distinct()
            }
        }

        fun getCount(uuid: String, ip: String, dungeons: List<String>? = null, time: Date?, action: String = "CLEAR_DUNGEON"): Int {

            return if (database.isMongo){
                val condition = SDBCondition().equal("actions.action", action)
                    .and(SDBCondition().equal("users.ip",ip).or(SDBCondition().equal("users.uuid",uuid)))
                if (time != null) {
                    condition.and(SDBCondition().orHigher("actions.date", time))
                }
                if (dungeons != null) {
                    condition.and(SDBCondition().include("actions.value", dungeons))
                }
                database.asyncSelect("tower_log", condition).get().count()
            } else {
                val condition = SDBCondition().equal("action", action)
                    .and(SDBCondition().like("ip", ip).or(SDBCondition().like("uuid", uuid)))
                if (time != null) {
                    condition.and(SDBCondition().orHigher("date", time.toSQLVariable(SDBVariable.DateTime)))
                }
                if (dungeons != null) {
                    condition.and(SDBCondition().include("value", dungeons))
                }
                database.asyncSelect(
                    "tower_log join party_log on tower_log.party_uuid = party_log.party_uuid",
                    condition
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