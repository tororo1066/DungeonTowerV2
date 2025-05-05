package tororo1066.dungeontower.command

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.block.Sign
import org.bukkit.command.CommandSender
import org.bukkit.persistence.PersistentDataType
import tororo1066.dungeontower.DungeonTower
import tororo1066.dungeontower.DungeonTower.Companion.sendPrefixMsg
import tororo1066.dungeontower.create.*
import tororo1066.dungeontower.data.*
import tororo1066.dungeontower.save.SaveDataDB
import tororo1066.dungeontower.skilltree.AbstractPerk
import tororo1066.dungeontower.skilltree.ActionBarBaseGUI
import tororo1066.dungeontower.skilltree.ConvenienceGUI
import tororo1066.tororopluginapi.SDebug
import tororo1066.tororopluginapi.SStr
import tororo1066.tororopluginapi.annotation.SCommandBody
import tororo1066.tororopluginapi.otherClass.StrExcludeFileIllegalCharacter
import tororo1066.tororopluginapi.sCommand.SCommand
import tororo1066.tororopluginapi.sCommand.SCommandArg
import tororo1066.tororopluginapi.sCommand.SCommandArgType
import tororo1066.tororopluginapi.sItem.SItem
import tororo1066.tororopluginapi.utils.sendMessage
import tororo1066.tororopluginapi.utils.toPlayer
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.math.min

@Suppress("unused")
class DungeonCommand: SCommand(
    "dungeon",
    prefix = DungeonTower.prefix.toString(),
    perm = "dungeon.user",
    alias = listOf("tower", "dt")
) {

    companion object {
        val accepts = HashMap<UUID,ArrayList<UUID>>()
        val entryCooldown = ArrayList<UUID>()
        val givingPerkPoint = ArrayList<UUID>()
        val perkOpeningPlayers = HashMap<UUID, ActionBarBaseGUI>()

        fun showHelp(sender: CommandSender, label: String){
            sender.sendMessage(SStr("&c==================== &bDungeonTower &c===================="))
            sender.sendMessage(SStr("&7/${label} entry <ダンジョン名> &dダンジョンに挑戦する"))
            sender.sendMessage(SStr("&7/${label} party &dパーティのヘルプを表示する"))
            if (sender.hasPermission("dungeon.op")){
                sender.sendMessage(SStr("&7/${label} setLobby &d立っているところをロビーにする"))
                sender.sendMessage(SStr("&7/${label} wand &dフロアを作成する杖を入手する(右で選択、左でクリア)"))
                sender.sendMessage(SStr("&7/${label} create &dダンジョンを作成するヘルプを表示する"))
                sender.sendMessage(SStr("&7/${label} edit &dダンジョンを編集するヘルプを表示する"))
            }
            sender.sendMessage(SStr("&c==================== &bDungeonTower &c===================="))
        }

        fun CommandSender.checkIllegal(string: String): Boolean {
            if (StrExcludeFileIllegalCharacter(string).nullableString == null){
                this.sendPrefixMsg(SStr("&c${string}は使用できません"))
                return false
            }
            return true
        }
    }

    init {
        SDebug.debugType.add("FloorScript")
        SDebug.debugType.add("UpFloor")
        setCommandNoFoundEvent {
            showHelp(it.sender,it.label)
        }
        SDebug.debugType.add("DungeonTask")
        registerDebugCommand("dungeon.op")
    }

    @SCommandBody
    val help = command().setNormalExecutor {
        showHelp(it.sender,it.label)
    }

    @SCommandBody("dungeon.user.perk")
    val showPerkMenu = command().addArg(SCommandArg("showPerk"))
        .addArg(SCommandArg(DungeonTower.towerData.keys).addAlias("ダンジョン名"))
        .addArg(SCommandArg(listOf("2560x1440", "1920x1080")))
        .setPlayerExecutor {
            if (DungeonTower.playNow.contains(it.sender.uniqueId) || entryCooldown.contains(it.sender.uniqueId)){
                it.sender.sendPrefixMsg(SStr("&cダンジョンに参加しています"))
                return@setPlayerExecutor
            }
            if (perkOpeningPlayers.containsKey(it.sender.uniqueId)){
                perkOpeningPlayers[it.sender.uniqueId]!!.stop()
            }
            val tower = DungeonTower.towerData[it.args[1]]!!
            val gui = ConvenienceGUI(it.sender, tower, it.args[2])
            gui.show()
        }

    @SCommandBody("dungeon.user.perk")
    val hidePerkMenu = command().addArg(SCommandArg("hidePerk"))
        .setPlayerExecutor {
            if (perkOpeningPlayers.containsKey(it.sender.uniqueId)){
                perkOpeningPlayers[it.sender.uniqueId]!!.stop()
            }
        }

    @SCommandBody("dungeon.user.perk.forget")
    val forgetPerk = command().addArg(SCommandArg("forgetPerk")).addArg(SCommandArg(DungeonTower.towerData.keys))
        .setPlayerExecutor {
            if (DungeonTower.playNow.contains(it.sender.uniqueId)){
                it.sender.sendPrefixMsg(SStr("&cダンジョンに参加しています"))
                return@setPlayerExecutor
            }
            if (givingPerkPoint.contains(it.sender.uniqueId)){
                it.sender.sendPrefixMsg(SStr("&c少し待ってから実行してください"))
                return@setPlayerExecutor
            }
            givingPerkPoint.add(it.sender.uniqueId)
            val tower = DungeonTower.towerData[it.args[1]]!!
            SaveDataDB.load(it.sender.uniqueId).thenAcceptAsync { list ->
                val data = list.find { find -> find.towerName == it.args[1] }
                if (data == null){
                    it.sender.sendPrefixMsg(SStr("&cパークが存在しません"))
                    givingPerkPoint.remove(it.sender.uniqueId)
                    return@thenAcceptAsync
                }
                data.perks.flatMap { entry -> entry.value.map { map -> AbstractPerk.getPerk(entry.key, map) } }.forEach {
                    perk -> perk.onUnlearned(it.sender)
                }
                val points = data.perks.flatMap { entry -> entry.value.map { map -> AbstractPerk.getPerk(entry.key, map).cost } }.sum() + data.perkPoints
                SaveDataDB.save(it.sender.uniqueId, tower, perkPoints = points, perks = hashMapOf()).thenAcceptAsync { save ->
                    if (save){
                        it.sender.sendPrefixMsg(SStr("&aパークを忘れました"))
                    } else {
                        it.sender.sendPrefixMsg(SStr("&cエラーが発生しました"))
                    }
                    givingPerkPoint.remove(it.sender.uniqueId)
                }
            }
        }

    @SCommandBody("dungeon.op")
    val givePerkPoint = command().addArg(SCommandArg("givePerkPoint"))
        .addArg(SCommandArg(SCommandArgType.ONLINE_PLAYER).addAlias("プレイヤー名"))
        .addArg(SCommandArg(DungeonTower.towerData.keys).addAlias("ダンジョン名"))
        .addArg(SCommandArg(SCommandArgType.INT).addAlias("ポイント"))
        .setPlayerExecutor {
            val p = it.args[1].toPlayer()!!
            if (givingPerkPoint.contains(p.uniqueId)){
                p.sendPrefixMsg(SStr("&c少し待ってから実行してください"))
                return@setPlayerExecutor
            }
            givingPerkPoint.add(p.uniqueId)
            val tower = DungeonTower.towerData[it.args[2]]!!
            var increasePoints = it.args[3].toInt()
            SaveDataDB.load(p.uniqueId).thenAcceptAsync { list ->
                val data = list.find { find -> find.towerName == it.args[2] }
                val inlinePerkPoints = data?.perks?.flatMap { entry -> entry.value.map { map -> AbstractPerk.getPerk(entry.key, map).cost } }?.sum() ?: 0
                increasePoints = min(increasePoints, tower.perkLimit - inlinePerkPoints - (data?.perkPoints ?: 0))
                val newPoints = if (data == null) {
                    min(tower.defaultPerkPoints + increasePoints, tower.perkLimit)
                } else {
                    min(data.perkPoints + increasePoints, tower.perkLimit - inlinePerkPoints)
                }
                val save = SaveDataDB.save(p.uniqueId, tower, perkPoints = newPoints).get()
                if (save){
                    p.sendPrefixMsg(SStr("${tower.name}&r&aでのパークポイントが${increasePoints}増えました"))
                } else {
                    p.sendPrefixMsg(SStr("&cエラーが発生しました"))
                }
                givingPerkPoint.remove(p.uniqueId)
                return@thenAcceptAsync
            }
        }

    @SCommandBody
    val entryTower = command().addArg(SCommandArg("entry")).addArg(SCommandArg(DungeonTower.towerData.keys)).setPlayerExecutor {
        if (entryCooldown.contains(it.sender.uniqueId)){
            it.sender.sendPrefixMsg(SStr("&c少し待ってから実行してください"))
            return@setPlayerExecutor
        }
        if (DungeonTower.playNow.contains(it.sender.uniqueId)){
            it.sender.sendPrefixMsg(SStr("&cダンジョンに参加しています"))
            return@setPlayerExecutor
        }
        if (!DungeonTower.partiesData.containsKey(it.sender.uniqueId)){
            it.sender.sendPrefixMsg(SStr("&cパーティに参加していません"))
            return@setPlayerExecutor
        }
        if (DungeonTower.partiesData[it.sender.uniqueId] == null){
            it.sender.sendPrefixMsg(SStr("&cパーティリーダーのみが実行できます"))
            return@setPlayerExecutor
        }

        val tower = DungeonTower.towerData[it.args[1]]!!
        val partyData = DungeonTower.partiesData[it.sender.uniqueId]!!

        tower.entryTower(it.sender,partyData)
    }

    @SCommandBody
    val partyHelp = command().addArg(SCommandArg("party")).setNormalExecutor {
        it.sender.sendMessage(SStr("&c================== &bDungeonTower &c=================="))
        it.sender.sendMessage(SStr("&7/${it.label} party create &dパーティを作る"))
        it.sender.sendMessage(SStr("&7/${it.label} party join <プレイヤー名> &dパーティに参加申請を送る"))
        it.sender.sendMessage(SStr("&7/${it.label} party accept <プレイヤー名> &d参加申請を承認する"))
        it.sender.sendMessage(SStr("&7/${it.label} party leave &dパーティから脱退する"))
        it.sender.sendMessage(SStr("&c================== &bDungeonTower &c=================="))
    }

    @SCommandBody
    val createParty = command().addArg(SCommandArg("party")).addArg(SCommandArg("create"))
        .setPlayerExecutor {
            if (DungeonTower.playNow.contains(it.sender.uniqueId)){
                it.sender.sendPrefixMsg(SStr("&cダンジョンに参加しています"))
                return@setPlayerExecutor
            }
            if (DungeonTower.partiesData.containsKey(it.sender.uniqueId)){
                it.sender.sendPrefixMsg(SStr("&c既にパーティーに参加しています"))
                return@setPlayerExecutor
            }
            val party = PartyData()
            party.parent = it.sender.uniqueId
            party.players[it.sender.uniqueId] = UserData(it.sender.uniqueId,it.sender.name,it.sender.address.address.hostAddress)
            DungeonTower.partiesData[it.sender.uniqueId] = party
            it.sender.sendPrefixMsg(SStr("&aパーティーを作成しました！"))
        }

    @SCommandBody
    val joinParty = command().addArg(SCommandArg("party")).addArg(SCommandArg("join"))
        .addArg(SCommandArg(SCommandArgType.ONLINE_PLAYER).addAlias("プレイヤー名")).setPlayerExecutor {
            if (DungeonTower.playNow.contains(it.sender.uniqueId)){
                it.sender.sendPrefixMsg(SStr("&cダンジョンに参加しています"))
                return@setPlayerExecutor
            }
            if (DungeonTower.partiesData.containsKey(it.sender.uniqueId)){
                it.sender.sendPrefixMsg(SStr("&c既にパーティーに参加しています"))
                return@setPlayerExecutor
            }
            val p = it.args[2].toPlayer()!!

            if (DungeonTower.partiesData[p.uniqueId] == null){
                it.sender.sendPrefixMsg(SStr("&cパーティーが存在しません"))
                return@setPlayerExecutor
            }

            if (!accepts.containsKey(p.uniqueId)) accepts[p.uniqueId] = arrayListOf()
            accepts[p.uniqueId]!!.add(it.sender.uniqueId)

            it.sender.sendPrefixMsg(SStr("&a${p.name}に参加申請を送りました"))
            p.sendPrefixMsg(SStr("&a${it.sender.name}が参加申請を送りました"))
            p.sendPrefixMsg(SStr("&a&l[承認するにはここをクリック！]").commandText("/dungeon party accept ${it.sender.name}"))
        }

    @SCommandBody
    val acceptParty = command().addArg(SCommandArg("party")).addArg(SCommandArg("accept"))
        .addArg(SCommandArg(SCommandArgType.ONLINE_PLAYER).addAlias("プレイヤー名")).setPlayerExecutor {
            if (DungeonTower.playNow.contains(it.sender.uniqueId)){
                it.sender.sendPrefixMsg(SStr("&cダンジョンに参加しています"))
                return@setPlayerExecutor
            }
            if (!DungeonTower.partiesData.containsKey(it.sender.uniqueId)){
                it.sender.sendPrefixMsg(SStr("&cパーティーが存在しません"))
                return@setPlayerExecutor
            }
            if (DungeonTower.partiesData[it.sender.uniqueId] == null){
                it.sender.sendPrefixMsg(SStr("&cパーティーリーダーのみ実行できます"))
                return@setPlayerExecutor
            }
            val p = it.args[2].toPlayer()!!

            if (!accepts.containsKey(it.sender.uniqueId)){
                it.sender.sendPrefixMsg(SStr("&c参加申請がありません"))
                return@setPlayerExecutor
            }
            if (!accepts[it.sender.uniqueId]!!.contains(p.uniqueId)){
                it.sender.sendPrefixMsg(SStr("&c参加申請がありません"))
                return@setPlayerExecutor
            }
            accepts[it.sender.uniqueId]!!.remove(p.uniqueId)
            val party = DungeonTower.partiesData[it.sender.uniqueId]!!
            party.players[p.uniqueId] = UserData(p.uniqueId,p.name,p.address.address.hostAddress)
            it.sender.sendPrefixMsg(SStr("&a${p.name}をパーティーに追加しました"))
            party.broadCast(SStr("&a${p.name}がパーティーに参加しました"))
        }

    @SCommandBody
    val leaveParty = command().addArg(SCommandArg("party")).addArg(SCommandArg("leave")).setPlayerExecutor {
        if (DungeonTower.playNow.contains(it.sender.uniqueId)){
            it.sender.sendPrefixMsg(SStr("&cダンジョンに参加しています"))
            return@setPlayerExecutor
        }
        if (!DungeonTower.partiesData.containsKey(it.sender.uniqueId)){
            it.sender.sendPrefixMsg(SStr("&cパーティーに参加していません"))
            return@setPlayerExecutor
        }
        val data = DungeonTower.partiesData[it.sender.uniqueId]
        if (data == null){
            DungeonTower.partiesData.values.filterNotNull()
                .filter { filter -> filter.players.containsKey(it.sender.uniqueId) }.forEach { party ->
                party.broadCast(SStr("&a${it.sender.name}がパーティーから抜けました"))
                party.players.remove(it.sender.uniqueId)
                DungeonTower.partiesData.remove(it.sender.uniqueId)
            }
        } else {
            data.broadCast(SStr("&a${it.sender.name}がパーティーを解散しました"))
            data.players.keys.forEach { uuid ->
                DungeonTower.partiesData.remove(uuid)
            }
        }
    }

    @SCommandBody
    val listParty = command().addArg(SCommandArg("party")).addArg(SCommandArg("list")).setPlayerExecutor {
        if (!DungeonTower.partiesData.containsKey(it.sender.uniqueId)){
            it.sender.sendPrefixMsg(SStr("&cパーティーに参加していません"))
            return@setPlayerExecutor
        }

        val data = DungeonTower.partiesData[it.sender.uniqueId]
        if (data == null){
            DungeonTower.partiesData.values.filterNotNull()
                .filter { filter -> filter.players.containsKey(it.sender.uniqueId) }.forEach { party ->
                    party.players.values.forEach { userData ->
                        it.sender.sendMessage("§d${if (party.parent == userData.uuid) "パーティリーダー " else ""}§a${userData.mcid}")
                    }
                }
        } else {
            data.players.values.forEach { userData ->
                it.sender.sendMessage("§d${if (data.parent == userData.uuid) "パーティリーダー " else ""}§a${userData.mcid}")
            }
        }
    }

    @SCommandBody("dungeon.op")
    val partyList = command().addArg(SCommandArg("parties")).setNormalExecutor {
        DungeonTower.partiesData.values.filterNotNull().forEach { party ->
            party.players.values.forEach { userData ->
                it.sender.sendMessage(SStr("§d${if (party.parent == userData.uuid) "パーティリーダー " else ""}§a${userData.mcid}")
                    .commandText("/minecraft:tp ${userData.mcid}")
                    .hoverText("§aクリックでテレポート"))
            }
            it.sender.sendMessage("§dプレイ中: ${party.nowTask != null}")
            if (party.nowTask != null){
                it.sender.sendMessage("§dダンジョン: §r${party.nowTask!!.tower.name}")
                it.sender.sendMessage("§dフロア: §r${party.nowTask!!.nowFloor.internalName}§7(${party.nowTask!!.nowFloorNum}階)")
            }
            it.sender.sendMessage("§6====================")
        }
    }

    @SCommandBody("dungeon.op")
    val setLobby = command().addArg(SCommandArg("setLobby")).setPlayerExecutor {
        DungeonTower.lobbyLocation = it.sender.location
        DungeonTower.plugin.config.set("lobbyLocation",it.sender.location)
        DungeonTower.plugin.saveConfig()
        it.sender.sendPrefixMsg(SStr("&aロビーを設定しました"))
    }

    @SCommandBody("dungeon.op")
    val giveWand = command().addArg(SCommandArg("wand")).setPlayerExecutor {
        val item = SItem(Material.STICK).setDisplayName("§a範囲を指定するわんど...みたいな").setCustomData(
            DungeonTower.plugin,"wand",
            PersistentDataType.INTEGER,1)
        it.sender.inventory.setItemInMainHand(item.build())
        it.sender.sendPrefixMsg(SStr("&aプレゼント"))
    }

    @SCommandBody("dungeon.op")
    val reload = command().addArg(SCommandArg("reload")).setNormalExecutor {
        DungeonTower.reloadDungeonConfig()
        it.sender.sendPrefixMsg(SStr("&aリロードしました"))
    }

    @SCommandBody("dungeon.op")
    val createHelp = command().addArg(SCommandArg("create")).setNormalExecutor {
        it.sender.sendMessage(SStr("&c================== &bDungeonTower &c=================="))
        it.sender.sendMessage(SStr("&7/${it.label} create floor <内部名> &dフロアを作成する"))
        it.sender.sendMessage(SStr("&7/${it.label} create dungeon <内部名> &dダンジョンを作成する"))
        it.sender.sendMessage(SStr("&7/${it.label} create spawner <内部名> &dボスを作成する"))
        it.sender.sendMessage(SStr("&7/${it.label} create loot <内部名> &dルートチェストを作成する"))
        it.sender.sendMessage(SStr("&c================== &bDungeonTower &c=================="))
    }

    @SCommandBody("dungeon.op")
    val editHelp = command().addArg(SCommandArg("edit")).setNormalExecutor {
        it.sender.sendMessage(SStr("&c================== &bDungeonTower &c=================="))
        it.sender.sendMessage(SStr("&7/${it.label} edit floor <内部名> &dフロアを編集する"))
        it.sender.sendMessage(SStr("&7/${it.label} edit dungeon <内部名> &dダンジョンを編集する"))
        it.sender.sendMessage(SStr("&7/${it.label} edit spawner <内部名> &dボスを編集する"))
        it.sender.sendMessage(SStr("&7/${it.label} edit loot <内部名> &dルートチェストを編集する"))
        it.sender.sendMessage(SStr("&c================== &bDungeonTower &c=================="))
    }

    @SCommandBody("dungeon.op")
    val createFloor = command().addArg(SCommandArg("create")).addArg(SCommandArg("floor"))
        .addArg(SCommandArg(SCommandArgType.STRING).addAlias("内部名"))
        .setPlayerExecutor {
            if (!it.sender.checkIllegal(it.args[2]))return@setPlayerExecutor
            if (DungeonTower.floorData.containsKey(it.args[2])){
                it.sender.sendPrefixMsg(SStr("&c既に存在してるよ！"))
                return@setPlayerExecutor
            }
            val meta = it.sender.inventory.itemInMainHand.itemMeta
            val firstLoc =
                meta?.persistentDataContainer?.get(NamespacedKey(DungeonTower.plugin,"firstloc"), PersistentDataType.STRING)
            val secondLoc =
                meta?.persistentDataContainer?.get(NamespacedKey(DungeonTower.plugin,"secondloc"), PersistentDataType.STRING)

            if (firstLoc == null || secondLoc == null){
                it.sender.sendPrefixMsg(SStr("&c範囲指定してね！"))
                return@setPlayerExecutor
            }
            val startLoc = firstLoc.split(",").map { map -> map.toDouble() }
            val endLoc = secondLoc.split(",").map { map -> map.toDouble() }

            val data = FloorData()
            data.internalName = it.args[2]
            data.startLoc = Location(DungeonTower.floorWorld,startLoc[0],startLoc[1],startLoc[2])
            data.endLoc = Location(DungeonTower.floorWorld,endLoc[0],endLoc[1],endLoc[2])
            CreateFloor(data,false).open(it.sender)
        }

    @SCommandBody("dungeon.op")
    val editFloor = command().addArg(SCommandArg("edit")).addArg(SCommandArg("floor"))
        .addArg(SCommandArg(DungeonTower.floorData.keys))
        .setPlayerExecutor {
            if (!it.sender.checkIllegal(it.args[2]))return@setPlayerExecutor
            if (!DungeonTower.floorData.containsKey(it.args[2])){
                it.sender.sendPrefixMsg(SStr("&c存在しないよ！"))
                return@setPlayerExecutor
            }
            val meta = it.sender.inventory.itemInMainHand.itemMeta
            val firstLoc =
                meta?.persistentDataContainer?.get(NamespacedKey(DungeonTower.plugin,"firstloc"), PersistentDataType.STRING)
            val secondLoc =
                meta?.persistentDataContainer?.get(NamespacedKey(DungeonTower.plugin,"secondloc"), PersistentDataType.STRING)

            if (firstLoc == null || secondLoc == null){
                CreateFloor(DungeonTower.floorData[it.args[2]]!!.newInstance(),true).open(it.sender)
                return@setPlayerExecutor
            }
            val startLoc = firstLoc.split(",").map { map -> map.toDouble() }
            val endLoc = secondLoc.split(",").map { map -> map.toDouble() }

            if (DungeonTower.floorData.containsKey(it.args[2])){
                val data = DungeonTower.floorData[it.args[2]]!!.newInstance()
                data.startLoc = Location(DungeonTower.floorWorld,startLoc[0],startLoc[1],startLoc[2])
                data.endLoc = Location(DungeonTower.floorWorld,endLoc[0],endLoc[1],endLoc[2])
                CreateFloor(data,true).open(it.sender)
                return@setPlayerExecutor
            }
        }

    @SCommandBody("dungeon.op")
    val copyFloor = command().addArg(SCommandArg("copy")).addArg(SCommandArg("floor"))
        .addArg(SCommandArg(DungeonTower.floorData.keys).addAlias("コピー元")).addArg(SCommandArg(SCommandArgType.STRING).addAlias("内部名"))
        .setPlayerExecutor {
            val meta = it.sender.inventory.itemInMainHand.itemMeta
            val firstLoc =
                meta?.persistentDataContainer?.get(NamespacedKey(DungeonTower.plugin,"firstloc"), PersistentDataType.STRING)
            val secondLoc =
                meta?.persistentDataContainer?.get(NamespacedKey(DungeonTower.plugin,"secondloc"), PersistentDataType.STRING)

            val copy = DungeonTower.floorData[it.args[2]]!!.newInstance()
            copy.internalName = it.args[3]
            if (firstLoc == null || secondLoc == null){
                CreateFloor(copy,false).open(it.sender)
                return@setPlayerExecutor
            }
            val startLoc = firstLoc.split(",").map { map -> map.toDouble() }
            val endLoc = secondLoc.split(",").map { map -> map.toDouble() }

            copy.startLoc = Location(DungeonTower.floorWorld,startLoc[0],startLoc[1],startLoc[2])
            copy.endLoc = Location(DungeonTower.floorWorld,endLoc[0],endLoc[1],endLoc[2])
            CreateFloor(copy,false).open(it.sender)
            return@setPlayerExecutor
        }

    @SCommandBody("dungeon.op")
    val createLoot = command().addArg(SCommandArg("create")).addArg(SCommandArg("loot"))
        .addArg(SCommandArg(SCommandArgType.STRING).addAlias("内部名"))
        .setPlayerExecutor {
            if (!it.sender.checkIllegal(it.args[2]))return@setPlayerExecutor
            if (DungeonTower.lootData.containsKey(it.args[2])){
                it.sender.sendPrefixMsg(SStr("&c既に存在してるよ！"))
                return@setPlayerExecutor
            }

            val data = LootData()
            data.internalName = it.args[2]
            CreateLoot(data,false).open(it.sender)
        }

    @SCommandBody("dungeon.op")
    val editLoot = command().addArg(SCommandArg("edit")).addArg(SCommandArg("loot"))
        .addArg(SCommandArg(DungeonTower.lootData.keys))
        .setPlayerExecutor {
            if (!it.sender.checkIllegal(it.args[2]))return@setPlayerExecutor
            if (!DungeonTower.lootData.containsKey(it.args[2])){
                it.sender.sendPrefixMsg(SStr("&c存在しないよ！"))
                return@setPlayerExecutor
            }
            CreateLoot(DungeonTower.lootData[it.args[2]]!!.clone(),true).open(it.sender)
        }

    @SCommandBody("dungeon.op")
    val copyLoot = command().addArg(SCommandArg("copy")).addArg(SCommandArg("loot"))
        .addArg(SCommandArg(DungeonTower.lootData.keys).addAlias("コピー元")).addArg(SCommandArg(SCommandArgType.STRING).addAlias("内部名"))
        .setPlayerExecutor {
            val copy = DungeonTower.lootData[it.args[2]]!!.clone()
            copy.internalName = it.args[3]
            CreateLoot(copy,false).open(it.sender)
        }

    @SCommandBody("dungeon.op")
    val createSpawner = command().addArg(SCommandArg("create")).addArg(SCommandArg("spawner"))
        .addArg(SCommandArg(SCommandArgType.STRING).addAlias("内部名"))
        .setPlayerExecutor {
            if (!it.sender.checkIllegal(it.args[2]))return@setPlayerExecutor
            if (DungeonTower.spawnerData.containsKey(it.args[2])){
                it.sender.sendPrefixMsg(SStr("&c既に存在してるよ！"))
                return@setPlayerExecutor
            }

            val data = SpawnerData()
            data.internalName = it.args[2]
            CreateSpawner(data,false).open(it.sender)
        }

    @SCommandBody("dungeon.op")
    val editSpawner = command().addArg(SCommandArg("edit")).addArg(SCommandArg("spawner"))
        .addArg(SCommandArg(DungeonTower.spawnerData.keys))
        .setPlayerExecutor {
            if (!it.sender.checkIllegal(it.args[2]))return@setPlayerExecutor
            if (!DungeonTower.spawnerData.containsKey(it.args[2])){
                it.sender.sendPrefixMsg(SStr("&c存在しないよ！"))
                return@setPlayerExecutor
            }
            CreateSpawner(DungeonTower.spawnerData[it.args[2]]!!.clone(),true).open(it.sender)
        }

    @SCommandBody("dungeon.op")
    val copySpawner = command().addArg(SCommandArg("copy")).addArg(SCommandArg("spawner"))
        .addArg(SCommandArg(DungeonTower.spawnerData.keys).addAlias("コピー元")).addArg(SCommandArg(SCommandArgType.STRING).addAlias("内部名"))
        .setPlayerExecutor {
            val copy = DungeonTower.spawnerData[it.args[2]]!!.clone()
            copy.internalName = it.args[3]
            CreateSpawner(copy,false).open(it.sender)
        }

    @SCommandBody("dungeon.op")
    val createTower = command().addArg(SCommandArg("create")).addArg(SCommandArg("tower"))
        .addArg(SCommandArg(SCommandArgType.STRING).addAlias("内部名"))
        .setPlayerExecutor {
            if (!it.sender.checkIllegal(it.args[2]))return@setPlayerExecutor
            if (DungeonTower.towerData.containsKey(it.args[2])){
                it.sender.sendPrefixMsg(SStr("&c既に存在してるよ！"))
                return@setPlayerExecutor
            }

            val data = TowerData()
            data.internalName = it.args[2]
            CreateTower(data,false).open(it.sender)
        }

    @SCommandBody("dungeon.op")
    val editTower = command().addArg(SCommandArg("edit")).addArg(SCommandArg("tower"))
        .addArg(SCommandArg(DungeonTower.towerData.keys))
        .setPlayerExecutor {
            if (!it.sender.checkIllegal(it.args[2]))return@setPlayerExecutor
            if (!DungeonTower.towerData.containsKey(it.args[2])){
                it.sender.sendPrefixMsg(SStr("&c存在しないよ！"))
                return@setPlayerExecutor
            }
            CreateTower(DungeonTower.towerData[it.args[2]]!!.clone(),true).open(it.sender)
        }

    @SCommandBody("dungeon.op")
    val modifySpawnerSign = command().addArg(SCommandArg("sign"))
        .addArg(SCommandArg("spawner"))
        .addArg(SCommandArg(DungeonTower.spawnerData.keys))
        .setPlayerExecutor {
            val hit = it.sender.rayTraceBlocks(4.0)?.hitBlock?:return@setPlayerExecutor
            val state = hit.state as? Sign?:return@setPlayerExecutor
            state.setLine(0,"spawner")
            state.setLine(1,it.args[2])
            state.update(true)
        }

    @SCommandBody("dungeon.op")
    val modifyLootSign = command().addArg(SCommandArg("sign"))
        .addArg(SCommandArg("loot"))
        .addArg(SCommandArg(DungeonTower.lootData.keys))
        .setPlayerExecutor {
            val hit = it.sender.rayTraceBlocks(4.0)?.hitBlock?:return@setPlayerExecutor
            val state = hit.state as? Sign?:return@setPlayerExecutor
            state.setLine(0,"loot")
            state.setLine(1,it.args[2])
            state.update(true)
        }

    @SCommandBody("dungeon.op")
    val saveFakeData = command()
        .addArg(SCommandArg("saveFakeData"))
        .addArg(SCommandArg(DungeonTower.towerData.keys))
        .addArg(SCommandArg(SCommandArgType.INT).addAlias("フロア数"))
        .addArg(SCommandArg(SCommandArgType.STRING).addAlias("内部名"))
        .setPlayerExecutor {
            CompletableFuture.runAsync {
                val towerData = DungeonTower.towerData[it.args[1]]!!
                val floorData = DungeonTower.floorData[it.args[3]]!!
                for (i in 1..it.args[2].toInt()){
                    try {
                        SaveDataDB.save(it.sender.uniqueId,towerData,floorData,i).join()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }.thenRun {
                it.sender.sendPrefixMsg(SStr("&a保存しました"))
            }
        }
}