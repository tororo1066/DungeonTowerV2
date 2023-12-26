package tororo1066.dungeontower.script

import com.ezylang.evalex.Expression
import com.ezylang.evalex.data.EvaluationValue
import com.ezylang.evalex.functions.AbstractFunction
import com.ezylang.evalex.functions.FunctionParameter
import com.ezylang.evalex.parser.Token
import tororo1066.dungeontower.logging.TowerLogDB
import tororo1066.tororopluginapi.script.ScriptFile

@FunctionParameter(name = "uuid")
@FunctionParameter(name = "ip")
@FunctionParameter(name = "dungeon", isVarArg = true)
class TodayEntryNumberFunction: AbstractFunction() {

    override fun evaluate(
        expression: Expression,
        functionToken: Token,
        vararg parameterValues: EvaluationValue
    ): EvaluationValue {
        val dungeons = parameterValues.drop(2).map { it.stringValue }.let {
            it.ifEmpty { null }
        }
        val log = TowerLogDB.getTodayEntryCount(parameterValues[0].stringValue, parameterValues[1].stringValue, dungeons)
        return EvaluationValue(log)
    }

    companion object {
        fun registerFunction(){
            ScriptFile.configuration.functionDictionary
                .addFunction("DT_TODAY_ENTRY", TodayEntryNumberFunction())
        }
    }
}