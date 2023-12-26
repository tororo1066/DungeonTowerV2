package tororo1066.dungeontower.save

import com.mongodb.client.model.Updates
import tororo1066.dungeontower.DungeonTower
import tororo1066.tororopluginapi.database.SDBCondition
import tororo1066.tororopluginapi.database.SDBVariable
import tororo1066.tororopluginapi.database.SDatabase
import java.util.UUID
import java.util.concurrent.CompletableFuture

class SaveData {

    companion object {
        val sDatabase = SDatabase.newInstance(DungeonTower.plugin)

        init {
            sDatabase.backGroundCreateTable("save_data", mapOf(
                "id" to SDBVariable(SDBVariable.Int, autoIncrement = true),
                "uuid" to SDBVariable(SDBVariable.VarChar, 36),
                "tower" to SDBVariable(SDBVariable.Text),
                "floor" to SDBVariable(SDBVariable.Text)
            ))
        }

        fun save(uuid: UUID, tower: String, floor: String): CompletableFuture<Boolean> {
            if (sDatabase.isMongo){
                return sDatabase.asyncSelect("save_data", SDBCondition().equal("uuid", uuid.toString())).thenApplyAsync { result ->
                    if (result.isEmpty()) {
                        return@thenApplyAsync sDatabase.insert(
                            "save_data", mapOf(
                                "uuid" to uuid.toString(),
                                "data" to mapOf(
                                    tower to listOf(floor)
                                )
                            )
                        )
                    } else {
                        return@thenApplyAsync sDatabase.update(
                            "save_data", Updates.push("data.${tower}", floor), SDBCondition().equal("uuid", uuid.toString())
                        )
                    }

                }
            } else {
                return sDatabase.asyncInsert("save_data", mapOf(
                    "uuid" to uuid.toString(),
                    "tower" to tower,
                    "floor" to floor
                ))
            }
        }

        fun load(uuid: UUID): CompletableFuture<Map<String, List<String>>> {
            if (sDatabase.isMongo){
                return sDatabase.asyncSelect("save_data", SDBCondition().equal("uuid", uuid.toString())).thenApplyAsync { result ->
                    if (result.isEmpty()) {
                        return@thenApplyAsync mapOf<String, List<String>>()
                    } else {
                        val data = result.first().getDeepResult("data")
                        return@thenApplyAsync data.result.mapValues { data.getList<String>(it.key) }
                    }
                }
            } else {
                return sDatabase.asyncSelect("save_data", SDBCondition().equal("uuid", uuid.toString())).thenApplyAsync { result ->
                    if (result.isEmpty()) {
                        return@thenApplyAsync mapOf<String, List<String>>()
                    } else {
                        return@thenApplyAsync result.associate { it.getString("tower") to listOf(it.getString("floor")) }
                    }
                }
            }
        }
    }
}