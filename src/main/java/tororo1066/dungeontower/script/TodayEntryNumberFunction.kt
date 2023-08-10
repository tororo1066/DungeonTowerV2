package tororo1066.dungeontower.script

import com.ezylang.evalex.Expression
import com.ezylang.evalex.data.EvaluationValue
import com.ezylang.evalex.functions.AbstractFunction
import com.ezylang.evalex.parser.Token
import org.bukkit.Bukkit
import tororo1066.dungeontower.sql.TowerLogDB
import tororo1066.tororopluginapi.script.ScriptFile

class TodayEntryNumberFunction: AbstractFunction() {

    override fun evaluate(
        expression: Expression,
        functionToken: Token,
        vararg parameterValues: EvaluationValue
    ): EvaluationValue {
        val log = TowerLogDB.selectTodayEntryCount()
        Bukkit.broadcastMessage(log.toString())
        return EvaluationValue(log)
    }

    companion object {
        fun registerFunction(){
            ScriptFile.configuration.functionDictionary.addFunction("DT_TODAY_ENTRY", TodayEntryNumberFunction())
        }
    }
}