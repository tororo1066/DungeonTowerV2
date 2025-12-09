package tororo1066.dungeontower.dmonitor

import tororo1066.displaymonitorapi.actions.ActionResult
import tororo1066.displaymonitorapi.actions.IActionContext
import tororo1066.displaymonitorapi.configuration.Execute
import tororo1066.displaymonitorapi.configuration.IAdvancedConfigurationSection

class InDungeon: AbstractDungeonAction() {

    var actions: Execute = Execute.empty()
    var failActions: Execute = Execute.empty()

    override fun run(context: IActionContext): ActionResult {
//        return context.partyAction { party ->
//            if (party.currentTask != null) {
//                actions(context)
//                ActionResult.success()
//            } else {
//                failActions(context)
//                ActionResult.success()
//            }
//        }
        if (context.getParty() != null) {
            actions(context)
            return ActionResult.success()
        } else {
            failActions(context)
            return ActionResult.success()
        }
    }

    override fun prepare(configuration: IAdvancedConfigurationSection) {
        actions = configuration.getConfigExecute("then", actions)
        failActions = configuration.getConfigExecute("else", failActions)
    }
}