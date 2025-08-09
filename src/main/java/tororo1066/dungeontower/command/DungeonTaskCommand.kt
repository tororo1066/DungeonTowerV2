package tororo1066.dungeontower.command

import org.bukkit.Bukkit
import tororo1066.commandapi.argumentType.EntityArg
import tororo1066.commandapi.argumentType.StringArg
import tororo1066.dungeontower.DungeonTower
import tororo1066.tororopluginapi.annotation.SCommandV2Body
import tororo1066.tororopluginapi.sCommand.v2.SCommandV2

class DungeonTaskCommand : SCommandV2("dtask") {

    init {
        root.setPermission("dtask.use")
    }

    @SCommandV2Body
    val execute = command {
        literal("execute") {
            argument("player", EntityArg(singleTarget = true, playersOnly = true)) {
                argument("command", StringArg(StringArg.StringType.GREEDY_PHRASE)) {
                    setFunctionExecutor { sender, _, args ->
                        val player = args.getEntities("player").first()
                        val command = args.getArgument("command", String::class.java)
                        val party =
                            DungeonTower.partiesData.values.find { it != null && it.players.containsKey(player.uniqueId) }
                                ?: return@setFunctionExecutor

                        val replaced = command
                            .replace("%party_uuid%", party.partyUUID.toString())

                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), replaced)
                    }
                }
            }
        }
    }
}