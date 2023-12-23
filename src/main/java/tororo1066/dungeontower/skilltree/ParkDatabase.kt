package tororo1066.dungeontower.skilltree

import com.mongodb.client.model.Updates
import tororo1066.dungeontower.DungeonTower
import tororo1066.tororopluginapi.database.SDBCondition
import tororo1066.tororopluginapi.database.SDBVariable
import tororo1066.tororopluginapi.database.SDatabase
import tororo1066.tororopluginapi.otherUtils.UsefulUtility
import java.util.UUID
import java.util.concurrent.CompletableFuture

class ParkDatabase {

    val cache = HashMap<UUID, HashMap<String, Class<out AbstractPark>>>()
    val sDatabase = SDatabase.newInstance(DungeonTower.plugin)

    init {
        sDatabase.backGroundCreateTable("park", mapOf(
            "id" to SDBVariable(SDBVariable.Int, autoIncrement = true),
            "uuid" to SDBVariable(SDBVariable.VarChar, 36),
            "category" to SDBVariable(SDBVariable.VarChar, 100),
            "park" to SDBVariable(SDBVariable.Text)
        ))
    }

    fun loadAsync(uuid: UUID): CompletableFuture<HashMap<String, Class<out AbstractPark>>> {
        if (cache.containsKey(uuid)) return CompletableFuture.completedFuture(cache[uuid]!!)
        val asyncResult = sDatabase.asyncSelect("park", SDBCondition().equal("uuid", uuid.toString()))
        val map = HashMap<String, Class<out AbstractPark>>()
        return asyncResult.thenApplyAsync { result ->
            if (sDatabase.isMongo) {
                val data = result.firstOrNull()?: return@thenApplyAsync map
                val parks = data.getDeepResult("parks")
                parks.result.keys.forEach {
                    val list = parks.getList<String>(it)
                    list.forEach second@ { park ->
                        val parkClass = UsefulUtility.sTry({
                            Class.forName("tororo1066.dungeontower.skilltree.parks.${it}.${park}").asSubclass(AbstractPark::class.java)
                        }, { null })?: return@second
                        map[parkClass.simpleName] = parkClass
                    }
                }

            } else {
                for (row in result){
                    val park = UsefulUtility.sTry({
                        Class.forName("tororo1066.dungeontower.skilltree.parks.${row.getString("category")}.${row.getString("park")}").asSubclass(AbstractPark::class.java)
                    }, { null })?: continue
                    map[park.simpleName] = park
                }
            }
            cache[uuid] = map
            map
        }
    }

    fun addAsync(uuid: UUID, park: AbstractPark): CompletableFuture<Boolean> {
        fun addCache() {
            if (cache.containsKey(uuid)) {
                cache[uuid]!![park.javaClass.simpleName] = park.javaClass
            } else {
                cache[uuid] = hashMapOf(park.javaClass.simpleName to park.javaClass)
            }
        }
        if (sDatabase.isMongo) {
            val asyncResult = sDatabase.asyncSelect("park", SDBCondition().equal("uuid", uuid.toString()))
            return asyncResult.thenApplyAsync { result ->
                if (result.firstOrNull() != null) {
                    return@thenApplyAsync sDatabase.update("park", Updates.push("parks.${park.category}", park.javaClass.simpleName), SDBCondition().equal("uuid", uuid.toString()))
                } else {
                    if (sDatabase.insert("park", mapOf(
                            "uuid" to uuid.toString(),
                            "parks" to mapOf(park.category to listOf(park.javaClass.simpleName))
                        ))) {
                        addCache()
                        return@thenApplyAsync true
                    } else {
                        return@thenApplyAsync false
                    }
                }
            }
        } else {
            return sDatabase.asyncInsert("park", mapOf(
                "uuid" to uuid.toString(),
                "category" to park.category,
                "park" to park.javaClass.simpleName
            )).thenApplyAsync { result ->
                if (result) {
                    addCache()
                }
                result
            }
        }
    }

    fun removeAsync(uuid: UUID, park: AbstractPark): CompletableFuture<Boolean> {
        if (sDatabase.isMongo) {
            val asyncResult = sDatabase.asyncSelect("park", SDBCondition().equal("uuid", uuid.toString()))
            return asyncResult.thenApplyAsync { result ->
                if (result.firstOrNull() != null) {
                    if (sDatabase.update("park", Updates.pull("parks.${park.category}", park.javaClass.simpleName), SDBCondition().equal("uuid", uuid.toString()))) {
                        cache.remove(uuid)
                        return@thenApplyAsync true
                    } else {
                        return@thenApplyAsync false
                    }
                } else {
                    return@thenApplyAsync false
                }
            }
        } else {
            return sDatabase.asyncDelete("park", SDBCondition().equal("uuid", uuid.toString()).and(SDBCondition().equal("category", park.category)).and(SDBCondition().equal("park", park.javaClass.simpleName)))
                .thenApplyAsync { result ->
                    if (result) cache.remove(uuid)
                    result
                }
        }
    }

}