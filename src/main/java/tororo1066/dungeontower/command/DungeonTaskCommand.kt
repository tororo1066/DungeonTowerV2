package tororo1066.dungeontower.command

import com.ezylang.evalex.Expression
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.command.BlockCommandSender
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import tororo1066.dungeontower.DungeonTower
import tororo1066.dungeontower.data.FloorData
import tororo1066.tororopluginapi.SDebug
import tororo1066.tororopluginapi.annotation.SCommandBody
import tororo1066.tororopluginapi.sCommand.SCommand
import tororo1066.tororopluginapi.sCommand.SCommandArg
import tororo1066.tororopluginapi.sCommand.SCommandArgType
import tororo1066.tororopluginapi.utils.toPlayer
import java.util.UUID

class DungeonTaskCommand: SCommand("dungeontask") {

    @SCommandBody
    val execute = command().addArg(
        SCommandArg("execute")
    ).addArg(
        SCommandArg(SCommandArgType.STRING)
            .addAlias("Nearest{radius}")
    ).addArg(
        SCommandArg(SCommandArgType.STRING)
            .addAlias("command")
    ).noLimit(true).setNormalFunction { sender, _, _, args ->
        SDebug.broadcastDebug("DungeonTask", "Prepare to execute command: ${args.joinToString(" ")}")
        val player: Player
        val regex = Regex("Nearest\\{([0-9]+)}")
        if (regex.matches(args[1])) {
            val radius = regex.find(args[1])!!.groupValues[1].toInt()
            val location = when (sender) {
                is BlockCommandSender -> sender.block.location
                is Entity -> sender.location
                else -> return@setNormalFunction
            }
            player = location.getNearbyPlayers(radius.toDouble())
                .filter {
                    DungeonTower.partiesData.containsKey(it.uniqueId)
                            && it.gameMode != GameMode.SPECTATOR
                }
                .minByOrNull { it.location.distance(location) } ?:return@setNormalFunction
        } else {
            player = args[1].toPlayer()?:return@setNormalFunction
        }
//        val data = DungeonTower.partiesData.entries
//            .find { it.value?.players?.containsKey(player.uniqueId) == false }
//            ?.value?:return@setNormalFunction
        val data = DungeonTower.partiesData[player.uniqueId] ?: kotlin.run {
            DungeonTower.partiesData.entries.find { it.value?.players?.containsKey(player.uniqueId) == true }?.value
        } ?: return@setNormalFunction
        val nowTask = data.nowTask?:return@setNormalFunction

        //command example: /dungeontask execute Nearest{10} say Expression(10+%nowFloor%)
        // /dungeontask execute Nearest{10} say Expression("aaa"+"bbb")

        var command = args.toList().subList(2, args.size).joinToString(" ")
            .replace("%partyUUID%", data.partyUUID.toString())
            .replace("%nowFloor%", nowTask.nowFloor.toString())
            .replace("%world%", nowTask.world.name)
            .replace("%start.x%", nowTask.nowFloor.dungeonStartLoc?.blockX.toString())
            .replace("%start.y%", nowTask.nowFloor.dungeonStartLoc?.blockY.toString())
            .replace("%start.z%", nowTask.nowFloor.dungeonStartLoc?.blockZ.toString())
            .replace("%end.x%", nowTask.nowFloor.dungeonEndLoc?.blockX.toString())
            .replace("%end.y%", nowTask.nowFloor.dungeonEndLoc?.blockY.toString())
            .replace("%end.z%", nowTask.nowFloor.dungeonEndLoc?.blockZ.toString())

        val expressionRegex = Regex("Expression\\(([^)]+)\\)")
        val matches = expressionRegex.findAll(command)
        matches.forEach {
            val expression = Expression(it.groupValues[1])
            command = command.replace(it.value, expression.evaluate().value.toString())
        }

        SDebug.broadcastDebug("DungeonTask", "Execute command: $command")
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)
    }

    @SCommandBody
    val countCommandTask = command().addArg(
        SCommandArg("countCommandTask")
    ).addArg(
        SCommandArg(SCommandArgType.STRING)
            .addAlias("partyUUID")
    ).addArg(
        SCommandArg(SCommandArgType.INT)
            .addAlias("count")
    ).setNormalFunction { _, _, _, args ->
        val partyUUID = UUID.fromString(args[1])
        val count = args[2].toInt()
        val data = DungeonTower.partiesData.entries.find { it.value?.partyUUID == partyUUID }?.value?:return@setNormalFunction
        val nowTask = data.nowTask?:return@setNormalFunction
        val find = nowTask.nowFloor.clearTask.find { it.type == FloorData.ClearTaskEnum.ENTER_COMMAND }?:return@setNormalFunction
        if (find.clear)return@setNormalFunction
        find.count += count
        if (find.count >= find.need){
            find.clear = true
        }
    }

    @SCommandBody
    val specificLobbyLocation = command().addArg(
        SCommandArg("specificLobbyLocation")
    ).addArg(
        SCommandArg(SCommandArgType.STRING)
            .addAlias("partyUUID")
    ).addArg(
        SCommandArg(SCommandArgType.WORLD)
            .addAlias("world")
    ).addArg(
        SCommandArg(SCommandArgType.DOUBLE)
            .addAlias("x")
    ).addArg(
        SCommandArg(SCommandArgType.DOUBLE)
            .addAlias("y")
    ).addArg(
        SCommandArg(SCommandArgType.DOUBLE)
            .addAlias("z")
    ).addArg(
        SCommandArg(SCommandArgType.DOUBLE)
            .addAlias("yaw")
    ).addArg(
        SCommandArg(SCommandArgType.DOUBLE)
            .addAlias("pitch")
    ).setNormalFunction { _, _, _, args ->
        val partyUUID = UUID.fromString(args[1])
        val data = DungeonTower.partiesData.entries.find { it.value?.partyUUID == partyUUID }?.value?:return@setNormalFunction
        val world = Bukkit.getWorld(args[2])?:return@setNormalFunction
        val x = args[3].toDouble()
        val y = args[4].toDouble()
        val z = args[5].toDouble()
        val yaw = args[6].toFloat()
        val pitch = args[7].toFloat()
        data.nowTask?.lobbyLocation = Location(world, x, y, z, yaw, pitch)
        SDebug.broadcastDebug("DungeonTask", "Set lobby location of party $partyUUID to $world $x $y $z $yaw $pitch")
    }
}