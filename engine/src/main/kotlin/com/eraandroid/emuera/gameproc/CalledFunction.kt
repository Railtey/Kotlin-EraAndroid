package com.eraandroid.emuera.gameproc

import com.eraandroid.emuera.GlobalStatic
import com.eraandroid.emuera.config.Config
import com.eraandroid.emuera.config.ConfigCode
import com.eraandroid.emuera.gamedata.expression.EType
import com.eraandroid.emuera.gamedata.expression.ExpressionMediator
import com.eraandroid.emuera.gamedata.expression.IOperandTerm
import com.eraandroid.emuera.gamedata.function.FunctionMethod
import com.eraandroid.emuera.gamedata.function.FunctionMethodCreator
import com.eraandroid.emuera.gamedata.function.FunctionMethodTerm
import com.eraandroid.emuera.gamedata.variable.ReferenceToken
import com.eraandroid.emuera.gamedata.variable.VariableTerm
import com.eraandroid.emuera.sub.CodeEE

class UserDefinedFunctionArgument(val arguments: Array<IOperandTerm?>, destArgs: Array<VariableTerm>) {
    val transporterInt = LongArray(arguments.size)
    val transporterStr = arrayOfNulls<String>(arguments.size)
    val transporterRef = arrayOfNulls<Any>(arguments.size)
    val isRef = BooleanArray(arguments.size) { destArgs[it].identifier.isReference }

    fun setTransporter(exm: ExpressionMediator) {
        for (i in arguments.indices) {
            val a = arguments[i] ?: continue
            if (isRef[i]) {
                val vTerm = a as VariableTerm
                if (vTerm.identifier.isCharacterData) {
                    val charaNo = vTerm.getElementInt(0, exm)
                    if (charaNo < 0 || charaNo >= GlobalStatic.VariableData!!.characterList.size)
                        throw CodeEE("キャラクタ配列変数${vTerm.identifier.name}の第１引数(${charaNo})はキャラ登録番号の範囲外です")
                    transporterRef[i] = vTerm.identifier.getArrayChara(charaNo.toInt())
                } else transporterRef[i] = vTerm.identifier.getArray()
            } else if (a.getOperandType() == EType.Int64) transporterInt[i] = a.getIntValue(exm)
            else transporterStr[i] = a.getStrValue(exm)
        }
    }

    fun restructure(exm: ExpressionMediator): UserDefinedFunctionArgument {
        for (i in arguments.indices) {
            val a = arguments[i] ?: continue
            if (isRef[i]) a.restructure(exm) else arguments[i] = a.restructure(exm)
        }
        return this
    }
}

class CalledFunction private constructor(val functionName: String) {
    private var eventLabelList: Array<MutableList<FunctionLabelLine>>? = null
    var currentLabel: FunctionLabelLine? = null
        private set
    lateinit var topLabel: FunctionLabelLine
        private set
    private var counter = -1
    private var group = 0
    var returnAddress: LogicalLine? = null
        private set
    var isJump = false
    var finished = false
        private set
    var isEvent = false
        private set
    val hasSingleFlag: Boolean get() = currentLabel?.isSingle ?: false

    fun convertArg(srcArgs: Array<IOperandTerm?>, errMes: Array<String?>): UserDefinedFunctionArgument? {
        errMes[0] = null
        if (topLabel.isError) { errMes[0] = topLabel.errMes; return null }
        val func = topLabel
        val convertedArg = arrayOfNulls<IOperandTerm>(func.arg.size)
        if (convertedArg.size < srcArgs.size) {
            errMes[0] = "引数の数が関数\"@${func.labelName}\"に設定された数を超えています"
            return null
        }
        for (i in func.arg.indices) {
            var term = if (i < srcArgs.size) srcArgs[i] else null
            val destArg = func.arg[i]
            if (destArg.identifier.isReference) {
                if (term == null) { errMes[0] = "\"@${func.labelName}\"の${i + 1}番目の引数は参照渡しのため省略できません"; return null }
                val vTerm = term as? VariableTerm
                if (vTerm == null || vTerm.identifier.dimension == 0) {
                    errMes[0] = "\"@${func.labelName}\"の${i + 1}番目の引数は参照渡しのための配列変数でなければなりません"; return null
                }
                val em = arrayOf("")
                if (!(destArg.identifier as ReferenceToken).matchType(vTerm.identifier, false, em)) {
                    errMes[0] = "\"@${func.labelName}\"の${i + 1}番目の引数:" + em[0]; return null
                }
            } else if (term == null) {
                term = func.def.getOrNull(i)
                if (term == null && !Config.CompatiFuncArgOptional) {
                    errMes[0] = "\"@${func.labelName}\"の${i + 1}番目の引数は省略できません(この警告は互換性オプション「" + Config.getConfigName(ConfigCode.CompatiFuncArgOptional) + "」により無視できます)"
                    return null
                }
            } else if (term.getOperandType() != destArg.getOperandType()) {
                if (term.getOperandType() == EType.String) {
                    errMes[0] = "\"@${func.labelName}\"の${i + 1}番目の引数を文字列型から整数型に変換できません"; return null
                }
                if (!Config.CompatiFuncArgAutoConvert) {
                    errMes[0] = "\"@${func.labelName}\"の${i + 1}番目の引数を整数型から文字列型に変換できません(この警告は互換性オプション「" + Config.getConfigName(ConfigCode.CompatiFuncArgAutoConvert) + "」により無視できます)"
                    return null
                }
                val m = tostrMethod ?: FunctionMethodCreator.getMethodList().getValue("TOSTR").also { tostrMethod = it }
                term = FunctionMethodTerm(m, arrayOf(term))
            }
            convertedArg[i] = term
        }
        return UserDefinedFunctionArgument(convertedArg, func.arg)
    }

    fun callLabel(parent: Process, label: String): LogicalLine? = parent.labelDictionary.getLabelDollar(label, currentLabel!!)

    fun updateRetAddress(line: LogicalLine?) { returnAddress = line }

    fun clone(): CalledFunction {
        val c = CalledFunction(functionName)
        c.eventLabelList = eventLabelList
        c.currentLabel = currentLabel
        c.topLabel = topLabel
        c.group = group
        c.isEvent = isEvent
        c.counter = counter
        c.returnAddress = returnAddress
        return c
    }

    fun shiftNext() {
        val list = eventLabelList!!
        while (true) {
            counter++
            if (list[group].size > counter) { currentLabel = list[group][counter]; return }
            group++
            counter = -1
            if (group >= 4) { currentLabel = null; return }
        }
    }

    fun shiftNextGroup() {
        counter = -1
        group++
        if (group >= 4) { currentLabel = null; return }
        shiftNext()
    }

    fun finishEvent() {
        group = 4
        counter = -1
        currentLabel = null
    }

    val isOnly: Boolean get() = currentLabel!!.isOnly

    companion object {
        private var tostrMethod: FunctionMethod? = null

        fun callEventFunction(parent: Process, label: String, retAddress: LogicalLine?): CalledFunction? {
            val called = CalledFunction(label)
            called.finished = false
            called.eventLabelList = parent.labelDictionary.getEventLabels(label)
            if (called.eventLabelList == null) {
                val line = parent.labelDictionary.getNonEventLabel(label)
                if (line != null)
                    throw CodeEE("イベント関数でない関数@${label}(${line.position?.filename}:${line.position?.lineNo}行目)に対しEVENT呼び出しが行われました")
                return null
            }
            called.counter = -1
            called.group = 0
            called.shiftNext()
            called.topLabel = called.currentLabel!!
            called.returnAddress = retAddress
            called.isEvent = true
            return called
        }

        fun callFunction(parent: Process, label: String, retAddress: LogicalLine?): CalledFunction? {
            val called = CalledFunction(label)
            called.finished = false
            val labelline = parent.labelDictionary.getNonEventLabel(label)
            if (labelline == null) {
                if (parent.labelDictionary.getEventLabels(label) != null)
                    throw CodeEE("イベント関数@${label}に対し通常のCALLが行われました(このエラーは互換性オプション「" + Config.getConfigName(ConfigCode.CompatiCallEvent) + "」により無視できます)")
                return null
            } else if (labelline.isMethod) {
                throw CodeEE("#FUCNTION(S)が定義された関数@${labelline.labelName}(${labelline.position?.filename}:${labelline.position?.lineNo}行目)に対し通常のCALLが行われました")
            }
            called.topLabel = labelline
            called.currentLabel = labelline
            called.returnAddress = retAddress
            called.isEvent = false
            return called
        }

        fun createCalledFunctionMethod(labelline: FunctionLabelLine, label: String): CalledFunction {
            val called = CalledFunction(label)
            called.topLabel = labelline
            called.currentLabel = labelline
            called.returnAddress = null
            called.isEvent = false
            return called
        }
    }
}
