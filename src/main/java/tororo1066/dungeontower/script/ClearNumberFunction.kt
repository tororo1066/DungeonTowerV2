package tororo1066.dungeontower.script

import com.ezylang.evalex.Expression
import com.ezylang.evalex.data.EvaluationValue
import com.ezylang.evalex.functions.AbstractFunction
import com.ezylang.evalex.functions.FunctionParameter
import com.ezylang.evalex.parser.Token
import tororo1066.dungeontower.logging.TowerLogDB
import tororo1066.tororopluginapi.script.ScriptFile
import java.util.Calendar

@FunctionParameter(name = "uuid")
@FunctionParameter(name = "ip")
@FunctionParameter(name = "dungeon", isVarArg = true)
class ClearNumberFunction: AbstractFunction() {
    override fun evaluate(
        expression: Expression,
        functionToken: Token,
        vararg parameterValues: EvaluationValue
    ): EvaluationValue {
        val dungeons = parameterValues.drop(2).map { it.stringValue }.let {
            it.ifEmpty { null }
        }
        val log = TowerLogDB.getCount(
            parameterValues[0].stringValue,
            parameterValues[1].stringValue,
            dungeons,
            null
        )
        return EvaluationValue(log, expression.configuration)
    }

    companion object {
        fun registerFunction(){
            ScriptFile.functions["dt_clear"] = { ClearNumberFunction() }
        }
    }
}