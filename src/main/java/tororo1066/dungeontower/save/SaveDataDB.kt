package tororo1066.dungeontower.save

import com.google.gson.Gson
import com.mongodb.client.model.Updates
import org.bson.conversions.Bson
import tororo1066.dungeontower.DungeonTower
import tororo1066.dungeontower.data.FloorData
import tororo1066.dungeontower.data.TowerData
import tororo1066.tororopluginapi.database.SDBCondition
import tororo1066.tororopluginapi.database.SDBResultSet
import tororo1066.tororopluginapi.database.SDBVariable
import tororo1066.tororopluginapi.database.SDatabase
import java.util.UUID
import java.util.concurrent.CompletableFuture

object SaveDataDB {

    class SaveData(
        val uuid: UUID,
        val towerName: String,
        val floors: HashMap<Int, ArrayList<Map<String, Any>>>,
        val parkPoints: Int,
        val parks: HashMap<String, ArrayList<String>>,
        )

    val sDatabase = SDatabase.newInstance(DungeonTower.plugin)
    val cache = HashMap<UUID, ArrayList<SaveData>>()

    init {
        sDatabase.backGroundCreateTable("save_data", mapOf(
//            "id" to SDBVariable(SDBVariable.Int, autoIncrement = true),
//            "uuid" to SDBVariable(SDBVariable.VarChar, 36),
//            "tower" to SDBVariable(SDBVariable.Text),
//            "floor" to SDBVariable(SDBVariable.Text)
        ))

        /*
        mongoDB
        {
            "uuid": "uuid",
            "data": {
                "<tower_name>": {
                    "floors": {
                        "<floor_num>": [
                            {
                                "internalName": "<floor_internal_name>",
                                etc...
                            }
                        ]
                    }
                    "parkPoints": 0
                    "parks": {
                        "<category>": ["<park_name>", "<park_name>"]
                    }
                    "connectTower": "<tower_name>" <- nullable
                }
            }
        }
         */
    }

    fun create(uuid: UUID): CompletableFuture<Boolean> {
        if (sDatabase.isMongo) {
            return sDatabase.asyncSelect("save_data", SDBCondition().equal("uuid", uuid.toString())).thenApplyAsync {
                if (it.isEmpty()) {
                    sDatabase.insert("save_data", mapOf(
                        "uuid" to uuid.toString()
                    ))
                } else {
                    return@thenApplyAsync false
                }
            }
        } else {
            return CompletableFuture()
        }
    }

    fun save(
        uuid: UUID,
        tower: TowerData,
        floor: FloorData? = null,
        floorNum: Int? = null,
        parkPoints: Int? = null,
        parks: HashMap<String, ArrayList<String>>? = null
    ): CompletableFuture<Boolean> {
        if (sDatabase.isMongo) {
            return sDatabase.asyncSelect("save_data", SDBCondition().equal("uuid", uuid.toString())).thenApplyAsync {
                if (it.isEmpty()) {
                    return@thenApplyAsync false
                } else {
                    try {
                        val singleData = it.first().getNullableDeepResult("data")
                        val condition = SDBCondition().equal("uuid", uuid.toString())
                        val updates = arrayListOf<Bson>()

                        if (floor != null && floorNum != null) {
                            singleData?.result?.keys?.forEach { towerName ->
                                if (towerName == tower.internalName) {
                                    val floors = singleData.getDeepResult(towerName).getDeepResult("floors")
                                    floors.result.keys.forEach { floorNum ->
                                        floors.getList<SDBResultSet>(floorNum).forEach { floorResultSet ->
                                            if (floorResultSet.getString("internalName") == floor.internalName) {
                                                return@thenApplyAsync false
                                            }
                                        }
                                    }
                                }
                            }
                            updates.add(Updates.push("data.${tower.internalName}.floors.${floorNum}", floor.toMap()))
                        }

                        if (parkPoints != null) {
                            updates.add(Updates.set("data.${tower.internalName}.parkPoints", parkPoints))
                        }

                        if (parks != null) {
                            updates.add(Updates.set("data.${tower.internalName}.parks", parks))
                        }

                        if (sDatabase.update("save_data", Updates.combine(
                                updates
                            ), condition)) {

                            val saveDataMap = hashMapOf<Int, ArrayList<Map<String, Any>>>()

                            singleData?.result?.keys?.find { towerName ->
                                towerName == tower.internalName
                            }?.let { towerName ->
                                singleData.getDeepResult(towerName).getNullableDeepResult("floors")?.let { floors ->
                                    floors.result.keys.forEach { floorNum ->
                                        val list = ArrayList<Map<String, Any>>()
                                        floors.getList<SDBResultSet>(floorNum).forEach { floorResultSet ->
                                            val map = HashMap<String, Any>()
                                            floorResultSet.result.forEach second@ { (key, value) ->
                                                map[key] = value?:return@second
                                            }
                                            list.add(map)
                                        }
                                        saveDataMap[floorNum.toInt()] = list
                                    }
                                }

                            }

                            if (floor != null && floorNum != null) {
                                if (saveDataMap.containsKey(floorNum)) {
                                    saveDataMap[floorNum]!!.add(floor.toMap())
                                } else {
                                    saveDataMap[floorNum] = arrayListOf(floor.toMap())
                                }
                            }

                            val saveData = SaveData(uuid, tower.internalName, saveDataMap, parkPoints?:0, parks?: hashMapOf())
                            if (cache.containsKey(uuid)) {
                                cache[uuid]!!.indexOfFirst { indexOfFirst -> indexOfFirst.towerName == tower.internalName }.let { index ->
                                    if (index != -1) {
                                        cache[uuid]!![index] = saveData
                                    } else {
                                        cache[uuid]!!.add(saveData)
                                    }
                                }
                            } else {
                                cache[uuid] = arrayListOf(saveData)
                            }
                            return@thenApplyAsync true
                        } else {
                            return@thenApplyAsync false
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        return@thenApplyAsync false
                    }
                }
            }
        } else {
            return CompletableFuture()
        }
    }

    fun load(uuid: UUID): CompletableFuture<ArrayList<SaveData>> {
        if (cache.containsKey(uuid)){
            return CompletableFuture.completedFuture(cache[uuid])
        }
        if (sDatabase.isMongo){
            return sDatabase.asyncSelect("save_data", SDBCondition().equal("uuid", uuid.toString())).thenApplyAsync { result ->
                if (result.isEmpty()) {
                    return@thenApplyAsync arrayListOf<SaveData>()
                } else {
                    val saveData = arrayListOf<SaveData>()
                    result.forEach {
                        val data = it.getNullableDeepResult("data")?:return@forEach
                        data.result.keys.forEach second@ { towerName ->
                            val towerData = data.getDeepResult(towerName)
                            val floors = hashMapOf<Int, ArrayList<Map<String, Any>>>()
                            val floorResults = towerData.getNullableDeepResult("floors")
                            floorResults?.result?.keys?.forEach { floorNum ->
                                val list = ArrayList<Map<String, Any>>()
                                floorResults.getList<SDBResultSet>(floorNum).forEach { floorResultSet ->
                                    val map = HashMap<String, Any>()
                                    floorResultSet.result.forEach third@ { (key, value) ->
                                        map[key] = value?:return@third
                                    }
                                    list.add(map)
                                }
                                floors[floorNum.toInt()] = list
                            }

                            val parkPoints = towerData.getNullableInt("parkPoints")?:0
                            val parks = hashMapOf<String, ArrayList<String>>()
                            towerData.getNullableDeepResult("parks")?.result?.keys?.forEach { category ->
                                parks[category] = ArrayList(towerData.getDeepResult("parks").getList<String>(category))
                            }
                            saveData.add(SaveData(uuid, towerName, floors, parkPoints, parks))
                        }
                    }
                    cache[uuid] = saveData
                    return@thenApplyAsync saveData
                }
            }
        } else {
            return CompletableFuture()
        }
    }
}