package tororo1066.dungeontower.dmonitor

import tororo1066.displaymonitorapi.actions.ActionResult
import tororo1066.displaymonitorapi.actions.IAbstractAction
import tororo1066.displaymonitorapi.actions.IActionContext
import tororo1066.dungeontower.DungeonTower
import tororo1066.dungeontower.data.PartyData
import java.util.UUID
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

abstract class AbstractDungeonAction: IAbstractAction {

    override fun allowedAutoStop(): Boolean {
        return true
    }

    @OptIn(ExperimentalContracts::class)
    protected fun IActionContext.partyAction(unit: (PartyData) -> ActionResult): ActionResult {
        contract {
            callsInPlace(unit, InvocationKind.AT_MOST_ONCE)
        }
        val parameters = publicContext.parameters
        val uuid = UUID.fromString((parameters["party.uuid"] ?: return ActionResult.noParameters("Party UUID not found")) as String)
        val party = DungeonTower.partiesData.entries.find { it.value?.partyUUID == uuid }?.value ?: return ActionResult.noParameters("Party not found")
        return unit(party)
    }
}