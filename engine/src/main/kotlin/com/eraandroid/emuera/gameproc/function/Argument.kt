package com.eraandroid.emuera.gameproc.function

import com.eraandroid.emuera.gamedata.expression.CaseExpression
import com.eraandroid.emuera.gamedata.expression.EType
import com.eraandroid.emuera.gamedata.expression.IOperandTerm
import com.eraandroid.emuera.gamedata.function.UserDefinedRefMethod
import com.eraandroid.emuera.gamedata.variable.ReferenceToken
import com.eraandroid.emuera.gamedata.variable.VariableTerm
import com.eraandroid.emuera.gamedata.variable.VariableToken
import com.eraandroid.emuera.gameproc.CalledFunction
import com.eraandroid.emuera.gameproc.UserDefinedFunctionArgument

typealias Terms = Array<IOperandTerm?>

abstract class Argument {
    @JvmField var isConst = false
    @JvmField var constStr: String? = null
    @JvmField var constInt: Long = 0
}

class ExpressionsArgument(val argumentTypeArray: Array<EType?>, val argumentArray: Terms) : Argument()
class VoidArgument : Argument()
class ErrorArgument(val errorMes: String) : Argument()
class ExpressionArgument(val term: IOperandTerm?) : Argument()
class ExpressionArrayArgument(termList: List<IOperandTerm?>) : Argument() { val termList: Terms = termList.toTypedArray() }
class SpPrintVArgument(val terms: Terms) : Argument()
class SpTimesArgument(val variableDest: VariableTerm, val doubleValue: Double) : Argument()
class SpBarArgument(value: IOperandTerm, max: IOperandTerm, length: IOperandTerm) : Argument() { val terms: Terms = arrayOf(value, max, length) }
class SpSwapCharaArgument(val x: IOperandTerm, val y: IOperandTerm?) : Argument()
class SpSwapVarArgument(val var1: VariableTerm, val var2: VariableTerm) : Argument()
class SpVarsizeArgument(val variableID: VariableToken) : Argument()
class SpSaveDataArgument(val target: IOperandTerm, val strExpression: IOperandTerm) : Argument()
class SpTInputsArgument(val time: IOperandTerm, val def: IOperandTerm, val disp: IOperandTerm?, val timeout: IOperandTerm?) : Argument()

enum class SortOrder { UNDEF, ASCENDING, DESENDING }

class SpSortcharaArgument(val sortKey: VariableTerm?, val sortOrder: SortOrder) : Argument()
class SpCallFArgment(val funcnameTerm: IOperandTerm, val subNames: Terms, val rowArgs: Terms) : Argument() {
    @JvmField var funcTerm: IOperandTerm? = null
}
class SpCallArgment(val funcnameTerm: IOperandTerm, val subNames: Terms, val rowArgs: Terms) : Argument() {
    @JvmField var udfArgument: UserDefinedFunctionArgument? = null
    @JvmField var callFunc: CalledFunction? = null
}
class SpForNextArgment(val cnt: VariableTerm, val start: IOperandTerm, val end: IOperandTerm, val step: IOperandTerm) : Argument()
class SpPowerArgument(val variableDest: VariableTerm, val x: IOperandTerm, val y: IOperandTerm) : Argument()
class CaseArgument(val caseExps: Array<CaseExpression>) : Argument()
class PrintDataArgument(val variable: VariableTerm?) : Argument()
class StrDataArgument(val variable: VariableTerm) : Argument()
class MethodArgument(val methodTerm: IOperandTerm) : Argument()
class BitArgument(val variableDest: VariableTerm, val term: Terms) : Argument()
class SpVarSetArgument(val variableDest: VariableTerm, val term: IOperandTerm?, val start: IOperandTerm?, val end: IOperandTerm?) : Argument()
class SpCVarSetArgument(val variableDest: VariableTerm, val index: IOperandTerm, val term: IOperandTerm?, val start: IOperandTerm?, val end: IOperandTerm?) : Argument()
class SpButtonArgument(val printStrTerm: IOperandTerm, val buttonWord: IOperandTerm) : Argument()
class SpColorArgument private constructor(val r: IOperandTerm?, val g: IOperandTerm?, val b: IOperandTerm?, val rgb: IOperandTerm?) : Argument() {
    constructor(r: IOperandTerm, g: IOperandTerm, b: IOperandTerm) : this(r, g, b, null)
    constructor(rgb: IOperandTerm) : this(null, null, null, rgb)
}
class SpSplitArgument(val targetStr: IOperandTerm, val split: IOperandTerm, val variable: VariableToken, val num: VariableTerm?) : Argument()
class SpHtmlSplitArgument(val targetStr: IOperandTerm, val variable: VariableToken, val num: VariableTerm?) : Argument()
class SpGetIntArgument(val varToken: VariableTerm) : Argument()
class SpArrayControlArgument(val varToken: VariableTerm, val num1: IOperandTerm, val num2: IOperandTerm) : Argument()
class SpArrayShiftArgument(val varToken: VariableTerm, val num1: IOperandTerm, val num2: IOperandTerm, val num3: IOperandTerm?, val num4: IOperandTerm?) : Argument()
class SpArraySortArgument(val varToken: VariableTerm, val order: SortOrder, val num1: IOperandTerm?, val num2: IOperandTerm?) : Argument()
class SpCopyArrayArgument(val varName1: IOperandTerm, val varName2: IOperandTerm) : Argument()
class SpSaveVarArgument(val term: IOperandTerm, val savMes: IOperandTerm, val varTokens: Array<VariableToken>) : Argument()

class RefArgument private constructor(
    val refMethodToken: UserDefinedRefMethod? = null,
    val srcRefMethodToken: UserDefinedRefMethod? = null,
    val srcCalledFunction: CalledFunction? = null,
    val refVarToken: ReferenceToken? = null,
    val srcVarToken: VariableToken? = null,
    val srcTerm: IOperandTerm? = null,
) : Argument() {
    companion object {
        fun method(udrm: UserDefinedRefMethod, src: UserDefinedRefMethod) = RefArgument(refMethodToken = udrm, srcRefMethodToken = src)
        fun method(udrm: UserDefinedRefMethod, src: CalledFunction?) = RefArgument(refMethodToken = udrm, srcCalledFunction = src)
        fun method(udrm: UserDefinedRefMethod, src: IOperandTerm) = RefArgument(refMethodToken = udrm, srcTerm = src)
        fun variable(vt: ReferenceToken, src: VariableToken) = RefArgument(refVarToken = vt, srcVarToken = src)
        fun variable(vt: ReferenceToken, src: IOperandTerm) = RefArgument(refVarToken = vt, srcTerm = src)
    }
}

class OneInputArgument(val term: IOperandTerm?, val flag: IOperandTerm?) : Argument()
class OneInputsArgument(val term: IOperandTerm?, val flag: IOperandTerm?) : Argument()

class SpSetArgument(val variableDest: VariableTerm, val term: IOperandTerm?) : Argument() {
    @JvmField var addConst = false
}
class SpSetArrayArgument private constructor(val variableDest: VariableTerm, val termList: Terms, val constIntList: LongArray?, val constStrList: Array<String?>?) : Argument() {
    constructor(v: VariableTerm, termList: Terms, constList: LongArray) : this(v, termList, constList, null)
    constructor(v: VariableTerm, termList: Terms, constList: Array<String?>) : this(v, termList, null, constList)
}
