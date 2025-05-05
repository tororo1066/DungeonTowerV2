package tororo1066.dungeontower.data.loot

import tororo1066.displaymonitorapi.actions.IAbstractAction

abstract class AbstractLootElement: IAbstractAction {

    override fun allowedAutoStop(): Boolean {
        return true
    }
}