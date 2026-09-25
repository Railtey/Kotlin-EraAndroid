package com.eraandroid.emuera.gameproc.function

import com.eraandroid.emuera.GlobalStatic
import com.eraandroid.emuera.Program
import com.eraandroid.emuera.config.Config
import com.eraandroid.emuera.gamedata.ParserMediator
import com.eraandroid.emuera.gamedata.expression.*
import com.eraandroid.emuera.gamedata.function.FunctionMethodTerm
import com.eraandroid.emuera.gamedata.function.UserDefinedRefMethod
import com.eraandroid.emuera.gamedata.variable.*
import com.eraandroid.emuera.gameproc.CalledFunction
import com.eraandroid.emuera.gameproc.InstructionLine
import com.eraandroid.emuera.sub.*

abstract class ArgumentBuilder {
    protected fun assignwarn(mes: String, line: InstructionLine, level: Int, isBackComp: Boolean) {
        val isError = level >= 2
        if (isError) { line.isError = true; line.errMes = mes }
        ParserMediator.warn(mes, line, level, isError, isBackComp)
    }

    protected fun warn(mesIn: String, line: InstructionLine, level: Int, isBackComp: Boolean) {
        val mes = line.function.name + "命令:" + mesIn
        val isError = level >= 2
        if (isError) { line.isError = true; line.errMes = mes }
        ParserMediator.warn(mes, line, level, isError, isBackComp)
    }

    /** null 要素 = チェックしない / EType.Void = 型不問 */
    protected var argumentTypeArray: Array<EType?> = arrayOf()
    protected var minArg = -1
    protected var argAny = false

    protected fun checkArgumentType(line: InstructionLine, exm: ExpressionMediator, arguments: Array<IOperandTerm?>?): Boolean {
        if (arguments == null) { warn("引数がありません", line, 2, false); return false }
        if (arguments.size < minArg || (arguments.size < argumentTypeArray.size && minArg < 0)) {
            warn("引数が足りません", line, 2, false); return false
        }
        var length = arguments.size
        if (arguments.size > argumentTypeArray.size && !argAny) {
            warn("引数が多すぎます", line, 1, false)
            length = argumentTypeArray.size
        }
        for (i in 0 until length) {
            val allowType: EType?
            if (!argAny && argumentTypeArray[i] == null) continue
            else if (argAny && i >= argumentTypeArray.size) allowType = argumentTypeArray[argumentTypeArray.size - 1]
            else allowType = argumentTypeArray[i]
            val a = arguments[i]
            if (a == null) {
                if (allowType == null) continue
                warn("第" + (i + 1) + "引数を認識できません", line, 2, false)
                return false
            }
            if (allowType != EType.Void && allowType != a.getOperandType()) {
                warn("第" + (i + 1) + "引数の型が正しくありません", line, 2, false)
                return false
            }
        }
        for (i in arguments.indices) arguments[i] = arguments[i]?.restructure(exm)
        return true
    }

    protected fun getChangeableVariable(terms: Array<IOperandTerm?>, i: Int, line: InstructionLine): VariableTerm? {
        val varTerm = terms[i - 1] as? VariableTerm
        if (varTerm == null) { warn("第" + i + "引数に変数以外を指定することはできません", line, 2, false); return null }
        if (varTerm.identifier.isConst) { warn("第" + i + "引数に変更できない変数を指定することはできません", line, 2, false); return null }
        return varTerm
    }

    protected fun popWords(line: InstructionLine): WordCollection =
        LexicalAnalyzer.analyse(line.popArgumentPrimitive(), LexEndWith.EoL, LexAnalyzeFlag.None)

    protected fun popTerms(line: InstructionLine): Array<IOperandTerm?> {
        val wc = LexicalAnalyzer.analyse(line.popArgumentPrimitive(), LexEndWith.EoL, LexAnalyzeFlag.None)
        return ExpressionParser.reduceArguments(wc, ArgsEndWith.EoL, false)
    }

    abstract fun createArgument(line: InstructionLine, exm: ExpressionMediator): Argument?

    protected fun zeroIndex(): Array<IOperandTerm?> = arrayOf(SingleTerm(0L))
    protected fun sysVar(name: String) = VariableTerm(GlobalStatic.VariableData!!.getSystemVariableToken(name), zeroIndex())
}

private val I = EType.Int64
private val S = EType.String
private val V = EType.Void

object ArgumentParser {
    private val argb = HashMap<FunctionArgType, ArgumentBuilder>()
    fun getArgumentBuilderDictionary(): Map<FunctionArgType, ArgumentBuilder> = argb
    fun getArgumentBuilder(key: FunctionArgType): ArgumentBuilder = argb.getValue(key)

    private val nargb = HashMap<String, ArgumentBuilder>()
    fun getNormalArgumentBuilder(argstr: String, minArgIn: Int): ArgumentBuilder {
        val minArg = if (minArgIn < 0) argstr.length else minArgIn
        val key = argstr + minArg
        nargb[key]?.let { return it }
        val types = Array<EType?>(argstr.length) {
            when (argstr[it]) { 'I' -> I; 'S' -> S; else -> throw ExeEE("異常な指定") }
        }
        val newarg = Expressions_ArgumentBuilder(types, minArg)
        nargb[key] = newarg
        return newarg
    }

    fun setArgumentTo(line: InstructionLine?): Boolean {
        if (line == null) return false
        if (line.argument != null) return true
        if (line.isError) return false
        if (!Program.DebugMode && line.function.isDebug()) { line.argument = null; return true }
        val errmes: String
        try {
            val ab = line.function.argBuilder
            val arg = if (ab != null) ab.createArgument(line, GlobalStatic.EMediator!!)
                else line.function.instruction!!.createArgument(line, GlobalStatic.EMediator!!)
            if (arg == null) {
                if (!line.isError) {
                    errmes = "命令の引数解析中に特定できないエラーが発生"
                } else return false
            } else {
                line.argument = arg
                return true
            }
        } catch (e: EmueraException) {
            line.isError = true
            line.errMes = e.message ?: ""
            ParserMediator.warn(line.errMes, line, 2, true, false)
            return false
        }
        line.isError = true
        line.errMes = errmes
        ParserMediator.warn(errmes, line, 2, true, false)
        return false
    }

    init {
        argb[FunctionArgType.METHOD] = METHOD_ArgumentBuilder()
        argb[FunctionArgType.VOID] = VOID_ArgumentBuilder()
        argb[FunctionArgType.INT_EXPRESSION] = INT_EXPRESSION_ArgumentBuilder(false)
        argb[FunctionArgType.INT_EXPRESSION_NULLABLE] = INT_EXPRESSION_ArgumentBuilder(true)
        argb[FunctionArgType.STR_EXPRESSION] = STR_EXPRESSION_ArgumentBuilder(false)
        argb[FunctionArgType.STR_EXPRESSION_NULLABLE] = STR_EXPRESSION_ArgumentBuilder(true)
        argb[FunctionArgType.STR] = STR_ArgumentBuilder(false)
        argb[FunctionArgType.STR_NULLABLE] = STR_ArgumentBuilder(true)
        argb[FunctionArgType.FORM_STR] = FORM_STR_ArgumentBuilder(false)
        argb[FunctionArgType.FORM_STR_NULLABLE] = FORM_STR_ArgumentBuilder(true)
        argb[FunctionArgType.SP_PRINTV] = SP_PRINTV_ArgumentBuilder()
        argb[FunctionArgType.SP_TIMES] = SP_TIMES_ArgumentBuilder()
        argb[FunctionArgType.SP_BAR] = SP_BAR_ArgumentBuilder()
        argb[FunctionArgType.SP_SET] = SP_SET_ArgumentBuilder()
        argb[FunctionArgType.SP_SETS] = SP_SET_ArgumentBuilder()
        argb[FunctionArgType.SP_SWAP] = SP_SWAP_ArgumentBuilder(false)
        argb[FunctionArgType.SP_VAR] = SP_VAR_ArgumentBuilder()
        argb[FunctionArgType.SP_SAVEDATA] = SP_SAVEDATA_ArgumentBuilder()
        argb[FunctionArgType.SP_TINPUT] = SP_TINPUT_ArgumentBuilder(I)
        argb[FunctionArgType.SP_TINPUTS] = SP_TINPUT_ArgumentBuilder(S)
        argb[FunctionArgType.SP_SORTCHARA] = SP_SORTCHARA_ArgumentBuilder()
        argb[FunctionArgType.SP_CALL] = SP_CALL_ArgumentBuilder(false, false)
        argb[FunctionArgType.SP_CALLF] = SP_CALL_ArgumentBuilder(true, false)
        argb[FunctionArgType.SP_CALLFORM] = SP_CALL_ArgumentBuilder(false, true)
        argb[FunctionArgType.SP_CALLFORMF] = SP_CALL_ArgumentBuilder(true, true)
        argb[FunctionArgType.SP_FOR_NEXT] = SP_FOR_NEXT_ArgumentBuilder()
        argb[FunctionArgType.SP_POWER] = SP_POWER_ArgumentBuilder()
        argb[FunctionArgType.SP_SWAPVAR] = SP_SWAPVAR_ArgumentBuilder()
        argb[FunctionArgType.EXPRESSION] = EXPRESSION_ArgumentBuilder(false)
        argb[FunctionArgType.EXPRESSION_NULLABLE] = EXPRESSION_ArgumentBuilder(true)
        argb[FunctionArgType.CASE] = CASE_ArgumentBuilder()
        argb[FunctionArgType.VAR_INT] = VAR_INT_ArgumentBuilder()
        argb[FunctionArgType.VAR_STR] = VAR_STR_ArgumentBuilder()
        argb[FunctionArgType.BIT_ARG] = BIT_ARG_ArgumentBuilder()
        argb[FunctionArgType.SP_VAR_SET] = SP_VAR_SET_ArgumentBuilder()
        argb[FunctionArgType.SP_BUTTON] = SP_BUTTON_ArgumentBuilder()
        argb[FunctionArgType.SP_COLOR] = SP_COLOR_ArgumentBuilder()
        argb[FunctionArgType.SP_SPLIT] = SP_SPLIT_ArgumentBuilder()
        argb[FunctionArgType.SP_GETINT] = SP_GETINT_ArgumentBuilder()
        argb[FunctionArgType.SP_CVAR_SET] = SP_CVAR_SET_ArgumentBuilder()
        argb[FunctionArgType.SP_CONTROL_ARRAY] = SP_CONTROL_ARRAY_ArgumentBuilder()
        argb[FunctionArgType.SP_SHIFT_ARRAY] = SP_SHIFT_ARRAY_ArgumentBuilder()
        argb[FunctionArgType.SP_SORTARRAY] = SP_SORT_ARRAY_ArgumentBuilder()
        argb[FunctionArgType.INT_ANY] = INT_ANY_ArgumentBuilder()
        argb[FunctionArgType.FORM_STR_ANY] = FORM_STR_ANY_ArgumentBuilder()
        argb[FunctionArgType.SP_COPYCHARA] = SP_SWAP_ArgumentBuilder(true)
        argb[FunctionArgType.SP_INPUT] = SP_INPUT_ArgumentBuilder()
        argb[FunctionArgType.SP_INPUTS] = SP_INPUTS_ArgumentBuilder()
        argb[FunctionArgType.SP_COPY_ARRAY] = SP_COPY_ARRAY_Arguments()
        argb[FunctionArgType.SP_SAVEVAR] = SP_SAVEVAR_ArgumentBuilder()
        argb[FunctionArgType.SP_SAVECHARA] = SP_SAVECHARA_ArgumentBuilder()
        argb[FunctionArgType.SP_REF] = SP_REF_ArgumentBuilder(false)
        argb[FunctionArgType.SP_REFBYNAME] = SP_REF_ArgumentBuilder(true)
        argb[FunctionArgType.SP_HTMLSPLIT] = SP_HTMLSPLIT_ArgumentBuilder()
    }
}

private class SP_PRINTV_ArgumentBuilder : ArgumentBuilder() {
    override fun createArgument(line: InstructionLine, exm: ExpressionMediator): Argument? {
        val wc = LexicalAnalyzer.analyse(line.popArgumentPrimitive(), LexEndWith.EoL, LexAnalyzeFlag.AnalyzePrintV)
        val args = ExpressionParser.reduceArguments(wc, ArgsEndWith.EoL, false)
        for (i in args.indices) {
            val a = args[i] ?: run { warn("引数を省略することはできません", line, 2, false); return null }
            args[i] = a.restructure(exm)
        }
        return SpPrintVArgument(args)
    }
}

private class SP_TIMES_ArgumentBuilder : ArgumentBuilder() {
    override fun createArgument(line: InstructionLine, exm: ExpressionMediator): Argument? {
        val st = line.popArgumentPrimitive()
        val wc = LexicalAnalyzer.analyse(st, LexEndWith.Comma, LexAnalyzeFlag.None)
        st.shiftNext()
        if (st.eos) { warn("引数が足りません", line, 2, false); return null }
        var d: Double
        try {
            LexicalAnalyzer.skipWhiteSpace(st)
            d = LexicalAnalyzer.readDouble(st)
            LexicalAnalyzer.skipWhiteSpace(st)
            if (!st.eos) warn("引数が多すぎます", line, 1, false)
        } catch (e: Exception) {
            warn("第２引数が実数値ではありません（常に0と解釈されます）", line, 1, false)
            d = 0.0
        }
        val term = ExpressionParser.reduceExpressionTerm(wc, TermEndWith.EoL) ?: run { warn("書式が間違っています", line, 2, false); return null }
        val varTerm = term.restructure(exm) as? VariableTerm ?: run { warn("第１引数に変数以外を指定することはできません", line, 2, false); return null }
        if (varTerm.isString) { warn("第１引数を文字列変数にすることはできません", line, 2, false); return null }
        if (varTerm.identifier.isConst) { warn("第１引数に変更できない変数を指定することはできません", line, 2, false); return null }
        return SpTimesArgument(varTerm, d)
    }
}

private class FORM_STR_ANY_ArgumentBuilder : ArgumentBuilder() {
    override fun createArgument(line: InstructionLine, exm: ExpressionMediator): Argument? {
        val st = line.popArgumentPrimitive()
        val termList = ArrayList<IOperandTerm?>()
        LexicalAnalyzer.skipHalfSpace(st)
        if (st.eos) {
            if (line.functionCode == FunctionCode.RETURNFORM) {
                termList.add(SingleTerm("0"))
                return ExpressionArrayArgument(termList).also { it.isConst = true; it.constInt = 0 }
            }
            warn("引数が設定されていません", line, 2, false)
            return null
        }
        while (true) {
            val sfwt = LexicalAnalyzer.analyseFormattedString(st, FormStrEndWith.Comma, false)
            termList.add(ExpressionParser.toStrFormTerm(sfwt).restructure(exm))
            st.shiftNext()
            if (st.eos) break
            LexicalAnalyzer.skipHalfSpace(st)
            if (st.eos) { warn("','の後ろに引数がありません。", line, 1, false); break }
        }
        return ExpressionArrayArgument(termList)
    }
}

private class VOID_ArgumentBuilder : ArgumentBuilder() {
    override fun createArgument(line: InstructionLine, exm: ExpressionMediator): Argument? {
        val st = line.popArgumentPrimitive()
        LexicalAnalyzer.skipWhiteSpace(st)
        if (!st.eos) warn("引数は不要です", line, 1, false)
        return VoidArgument()
    }
}

private class STR_ArgumentBuilder(private val nullable: Boolean) : ArgumentBuilder() {
    override fun createArgument(line: InstructionLine, exm: ExpressionMediator): Argument? {
        val st = line.popArgumentPrimitive()
        val rowStr: String
        if (st.eos) {
            if (!nullable) { warn("引数が設定されていません", line, 2, false); return null }
            rowStr = ""
        } else rowStr = st.substring()
        if (line.functionCode == FunctionCode.SETCOLORBYNAME || line.functionCode == FunctionCode.SETBGCOLORBYNAME) {
            if (NamedColors.fromName(rowStr) == null) {
                if (rowStr.equals("transparent", ignoreCase = true)) throw CodeEE("無色透明(Transparent)は色として指定できません")
                throw CodeEE("指定された色名\"$rowStr\"は無効な色名です")
            }
        }
        return ExpressionArgument(SingleTerm(rowStr)).also { it.constStr = rowStr; it.isConst = true }
    }
}

private class FORM_STR_ArgumentBuilder(private val nullable: Boolean) : ArgumentBuilder() {
    override fun createArgument(line: InstructionLine, exm: ExpressionMediator): Argument? {
        val st = line.popArgumentPrimitive()
        if (st.eos) {
            if (!nullable) { warn("引数が設定されていません", line, 2, false); return null }
            return ExpressionArgument(SingleTerm("")).also { it.constStr = ""; it.isConst = true }
        }
        val sfwt = LexicalAnalyzer.analyseFormattedString(st, FormStrEndWith.EoL, false)
        val term = ExpressionParser.toStrFormTerm(sfwt).restructure(exm)
        val ret = ExpressionArgument(term)
        if (term is SingleTerm) { ret.constStr = term.getStrValue(exm); ret.isConst = true }
        return ret
    }
}

private class SP_VAR_ArgumentBuilder : ArgumentBuilder() {
    override fun createArgument(line: InstructionLine, exm: ExpressionMediator): Argument? {
        val st = line.popArgumentPrimitive()
        val iw = LexicalAnalyzer.readSingleIdentifierWord(st) ?: run { warn("第１引数を読み取ることができません", line, 2, false); return null }
        val id = GlobalStatic.IdentifierDictionary!!.getVariableToken(iw.code, null, true)
            ?: run { warn("第１引数に変数以外を指定することはできません", line, 2, false); return null }
        if ((!id.isArray1D && !id.isArray2D && !id.isArray3D) || id.code == VariableCode.RAND) {
            warn("第１引数に配列でない変数を指定することはできません", line, 2, false); return null
        }
        LexicalAnalyzer.skipWhiteSpace(st)
        if (!st.eos) warn("引数の後に余分な文字があります", line, 1, false)
        return SpVarsizeArgument(id)
    }
}

private fun isFwdBack(w: Word): Boolean = w is IdentifierWord && (w.code.equals("FORWARD", Config.ICVariable) || w.code.equals("BACK", Config.ICVariable))
private fun isBack(w: Word): Boolean = (w as IdentifierWord).code.equals("BACK", Config.ICVariable)

private class SP_SORTCHARA_ArgumentBuilder : ArgumentBuilder() {
    override fun createArgument(line: InstructionLine, exm: ExpressionMediator): Argument? {
        var varTerm: VariableTerm = sysVar("NO")
        var order = SortOrder.ASCENDING
        val wc = popWords(line)
        if (wc.eol) return SpSortcharaArgument(varTerm, order)
        if (isFwdBack(wc.current)) {
            if (isBack(wc.current)) order = SortOrder.DESENDING
            wc.shiftNext()
            if (!wc.eol) warn("引数が多すぎます", line, 1, false)
        } else {
            val term = ExpressionParser.reduceExpressionTerm(wc, TermEndWith.Comma) ?: run { warn("書式が間違っています", line, 2, false); return null }
            varTerm = term.restructure(exm) as? VariableTerm ?: run { warn("第１引数に変数以外を指定することはできません", line, 2, false); return null }
            if (!varTerm.identifier.isCharacterData) { warn("第１引数はキャラクタ変数でなければなりません", line, 2, false); return null }
            wc.shiftNext()
            if (!wc.eol) {
                if (isFwdBack(wc.current)) {
                    if (isBack(wc.current)) order = SortOrder.DESENDING
                    wc.shiftNext()
                    if (!wc.eol) warn("引数が多すぎます", line, 1, false)
                } else { warn("書式が間違っています", line, 2, false); return null }
            }
        }
        return SpSortcharaArgument(varTerm, order)
    }
}

private class SP_SORT_ARRAY_ArgumentBuilder : ArgumentBuilder() {
    override fun createArgument(line: InstructionLine, exm: ExpressionMediator): Argument? {
        var order = SortOrder.ASCENDING
        val wc = popWords(line)
        var term3: IOperandTerm? = SingleTerm(0L)
        var term4: IOperandTerm? = null
        if (wc.eol) { warn("書式が間違っています", line, 2, false); return null }
        val term = ExpressionParser.reduceExpressionTerm(wc, TermEndWith.Comma) ?: run { warn("書式が間違っています", line, 2, false); return null }
        val varTerm = term.restructure(exm) as? VariableTerm ?: run { warn("第１引数に変数以外を指定することはできません", line, 2, false); return null }
        if (varTerm.identifier.isConst) { warn("第１引数が変更できない変数です", line, 2, false); return null }
        if (!varTerm.identifier.isArray1D) { warn("第１引数に１次元配列もしくは配列型キャラクタ変数以外を指定することはできません", line, 2, false); return null }
        wc.shiftNext()
        val id = wc.current as? IdentifierWord
        if (id != null && isFwdBack(id)) {
            if (isBack(id)) order = SortOrder.DESENDING
            wc.shiftNext()
        } else if (id != null) { warn("第２引数にソート方法指定子（FORWARD or BACK）以外が指定されています", line, 2, false); return null }
        if (id != null) {
            wc.shiftNext()
            if (!wc.eol) {
                term3 = ExpressionParser.reduceExpressionTerm(wc, TermEndWith.Comma) ?: run { warn("第３引数が解釈出来ません", line, 2, false); return null }
                if (!term3!!.isInteger) { warn("第３引数が数値ではありません", line, 2, false); return null }
                wc.shiftNext()
                if (!wc.eol) {
                    term4 = ExpressionParser.reduceExpressionTerm(wc, TermEndWith.Comma) ?: run { warn("第４引数が解釈出来ません", line, 2, false); return null }
                    if (!term4!!.isInteger) { warn("第４引数が数値ではありません", line, 2, false); return null }
                    wc.shiftNext()
                    if (!wc.eol) warn("引数が多すぎます", line, 1, false)
                }
            }
        }
        return SpArraySortArgument(varTerm, order, term3, term4)
    }
}

private class SP_CALL_ArgumentBuilder(private val callf: Boolean, private val form: Boolean) : ArgumentBuilder() {
    override fun createArgument(line: InstructionLine, exm: ExpressionMediator): Argument? {
        val st = line.popArgumentPrimitive()
        val funcname: IOperandTerm = if (form) {
            val sfw = LexicalAnalyzer.analyseFormattedString(st, FormStrEndWith.LeftParenthesis_Bracket_Comma_Semicolon, true)
            ExpressionParser.toStrFormTerm(sfw).restructure(exm)
        } else {
            SingleTerm(LexicalAnalyzer.readString(st, StrEndWith.LeftParenthesis_Bracket_Comma_Semicolon).trim(' ', '\t'))
        }
        val cur = st.current
        val wc = LexicalAnalyzer.analyse(st, LexEndWith.EoL, LexAnalyzeFlag.None)
        wc.shiftNext()
        var subNames: Array<IOperandTerm?>? = null
        var args: Array<IOperandTerm?>? = null
        if (cur == '[') {
            subNames = ExpressionParser.reduceArguments(wc, ArgsEndWith.RightBracket, false)
            if (!wc.eol) {
                if (wc.current.type != '(') wc.shiftNext()
                args = ExpressionParser.reduceArguments(wc, ArgsEndWith.RightParenthesis, false)
            }
        }
        if (cur == '(' || cur == ',') {
            args = if (cur == '(') ExpressionParser.reduceArguments(wc, ArgsEndWith.RightParenthesis, false)
            else ExpressionParser.reduceArguments(wc, ArgsEndWith.EoL, false)
            if (!wc.eol) { warn("書式が間違っています", line, 2, false); return null }
        }
        val sn = subNames ?: arrayOf()
        val ag = args ?: arrayOf()
        for (i in sn.indices) sn[i] = sn[i]?.restructure(exm)
        for (i in ag.indices) ag[i] = ag[i]?.restructure(exm)
        val ret: Argument = if (callf) SpCallFArgment(funcname, sn, ag) else SpCallArgment(funcname, sn, ag)
        if (funcname is SingleTerm) {
            ret.isConst = true
            ret.constStr = funcname.str
            if (ret.constStr == "") { warn("関数名が指定されていません", line, 2, false); return null }
        }
        return ret
    }
}

private class CASE_ArgumentBuilder : ArgumentBuilder() {
    override fun createArgument(line: InstructionLine, exm: ExpressionMediator): Argument? {
        val wc = popWords(line)
        val args = ExpressionParser.reduceCaseExpressions(wc)
        if (!wc.eol || args.isEmpty()) { warn("書式が間違っています", line, 2, false); return null }
        for (a in args) a.reduce(exm)
        return CaseArgument(args)
    }
}

private class SP_SET_ArgumentBuilder : ArgumentBuilder() {
    override fun createArgument(line: InstructionLine, exm: ExpressionMediator): Argument? {
        val destWc = line.popAssignmentDestStr()
        val destTerms = ExpressionParser.reduceArguments(destWc, ArgsEndWith.EoL, false)
        if (destTerms.isEmpty() || destTerms[0] == null) { assignwarn("代入文の左辺の読み取りに失敗しました", line, 2, false); return null }
        if (destTerms.size != 1) { assignwarn("代入文の左辺に余分な','があります", line, 2, false); return null }
        val varTerm = destTerms[0] as? VariableTerm ?: run { assignwarn("代入文の左辺に変数以外を指定することはできません", line, 2, false); return null }
        if (varTerm.identifier.isConst) { assignwarn("代入文の左辺に変更できない変数を指定することはできません", line, 2, false); return null }
        varTerm.restructure(exm)
        val st = line.popArgumentPrimitive()
        val op = line.assignOperator!!
        var src: IOperandTerm
        if (varTerm.isInteger) {
            if (op == OperatorCode.AssignmentStr) { assignwarn("整数型の代入に演算子" + OperatorManager.toOperatorString(op) + "は使用できません", line, 2, false); return null }
            if (op == OperatorCode.Increment || op == OperatorCode.Decrement) {
                LexicalAnalyzer.skipWhiteSpace(st)
                if (!st.eos) {
                    if (op == OperatorCode.Increment) assignwarn("インクリメント行でインクリメント以外の処理が定義されています", line, 2, false)
                    else assignwarn("デクリメント行でデクリメント以外の処理が定義されています", line, 2, false)
                    return null
                }
                return SpSetArgument(varTerm, null).also { it.isConst = true; it.constInt = if (op == OperatorCode.Increment) 1 else -1; it.addConst = true }
            }
            val srcWc = LexicalAnalyzer.analyse(st, LexEndWith.EoL, LexAnalyzeFlag.None)
            val srcTerms = ExpressionParser.reduceArguments(srcWc, ArgsEndWith.EoL, false)
            if (srcTerms.isEmpty() || srcTerms[0] == null) { assignwarn("代入文の右辺の読み取りに失敗しました", line, 2, false); return null }
            if (srcTerms.size != 1) {
                if (op != OperatorCode.Assignment) { assignwarn("複合代入演算では右辺に複数の値を含めることはできません", line, 2, false); return null }
                var allConst = true
                val constValues = LongArray(srcTerms.size)
                for (i in srcTerms.indices) {
                    val t = srcTerms[i] ?: run { assignwarn("代入式の右辺の値は省略できません", line, 2, false); return null }
                    if (!t.isInteger) { assignwarn("数値型変数に文字列は代入できません", line, 2, false); return null }
                    val r = t.restructure(exm)
                    srcTerms[i] = r
                    if (allConst && r is SingleTerm) constValues[i] = r.int else allConst = false
                }
                return SpSetArrayArgument(varTerm, srcTerms, constValues).also { it.isConst = allConst }
            }
            if (!srcTerms[0]!!.isInteger) { assignwarn("数値型変数に文字列は代入できません", line, 2, false); return null }
            src = srcTerms[0]!!.restructure(exm)
            if (op == OperatorCode.Assignment) {
                val ret = SpSetArgument(varTerm, src)
                if (src is SingleTerm) { ret.isConst = true; ret.addConst = false; ret.constInt = src.int }
                return ret
            }
            if ((op == OperatorCode.Plus || op == OperatorCode.Minus) && src is SingleTerm) {
                val v = src.int
                return SpSetArgument(varTerm, null).also { it.isConst = true; it.constInt = if (op == OperatorCode.Plus) v else -v; it.addConst = true }
            }
            src = OperatorMethodManager.reduceBinaryTerm(op, varTerm, src)
            return SpSetArgument(varTerm, src)
        } else {
            if (op == OperatorCode.Assignment) {
                if (Config.SystemIgnoreStringSet) { assignwarn("文字列代入は禁止されています（'=を用いるかコンフィグオプションを変えてください)", line, 2, false); return null }
                LexicalAnalyzer.skipHalfSpace(st)
                val sfwt = LexicalAnalyzer.analyseFormattedString(st, FormStrEndWith.EoL, true)
                src = ExpressionParser.toStrFormTerm(sfwt).restructure(exm)
                val ret = SpSetArgument(varTerm, src)
                if (src is SingleTerm) { ret.isConst = true; ret.addConst = false; ret.constStr = src.str }
                return ret
            } else if (op == OperatorCode.Mult || op == OperatorCode.Plus || op == OperatorCode.AssignmentStr) {
                val srcWc = LexicalAnalyzer.analyse(st, LexEndWith.EoL, LexAnalyzeFlag.None)
                val srcTerms = ExpressionParser.reduceArguments(srcWc, ArgsEndWith.EoL, false)
                if (srcTerms.isEmpty() || srcTerms[0] == null) { assignwarn("代入文の右辺の読み取りに失敗しました", line, 2, false); return null }
                if (op == OperatorCode.AssignmentStr) {
                    if (srcTerms.size == 1) {
                        if (srcTerms[0]!!.isInteger) { assignwarn("文字列変数に数値型は代入できません", line, 2, false); return null }
                        src = srcTerms[0]!!.restructure(exm)
                        val ret = SpSetArgument(varTerm, src)
                        if (src is SingleTerm) { ret.isConst = true; ret.addConst = false; ret.constStr = src.str }
                        return ret
                    }
                    var allConst = true
                    val constValues = arrayOfNulls<String>(srcTerms.size)
                    for (i in srcTerms.indices) {
                        val t = srcTerms[i] ?: run { assignwarn("代入式の右辺の値は省略できません", line, 2, false); return null }
                        if (t.isInteger) { assignwarn("文字列変数に数値型は代入できません", line, 2, false); return null }
                        val r = t.restructure(exm)
                        srcTerms[i] = r
                        if (allConst && r is SingleTerm) constValues[i] = r.str else allConst = false
                    }
                    return SpSetArrayArgument(varTerm, srcTerms, constValues).also { it.isConst = allConst }
                }
                if (srcTerms.size != 1) { assignwarn("代入文の右辺に余分な','があります", line, 2, false); return null }
                src = srcTerms[0]!!.restructure(exm)
                src = OperatorMethodManager.reduceBinaryTerm(op, varTerm, src)
                return SpSetArgument(varTerm, src)
            }
            assignwarn("代入式に使用できない演算子が使われました", line, 2, false)
            return null
        }
    }
}

private class METHOD_ArgumentBuilder : ArgumentBuilder() {
    override fun createArgument(line: InstructionLine, exm: ExpressionMediator): Argument? {
        val args = popTerms(line)
        val method = line.function.method!!
        val errmes = method.checkArgumentType(line.function.name, args)
        if (errmes != null) throw CodeEE(errmes)
        return MethodArgument(FunctionMethodTerm(method, args).restructure(exm))
    }
}

private class SP_INPUTS_ArgumentBuilder : ArgumentBuilder() {
    init { argumentTypeArray = arrayOf(S); minArg = 0 }
    override fun createArgument(line: InstructionLine, exm: ExpressionMediator): Argument? {
        val st = line.popArgumentPrimitive()
        if (st.eos) return ExpressionArgument(null)
        val sfwt = LexicalAnalyzer.analyseFormattedString(st, FormStrEndWith.EoL, false)
        if (!st.eos) warn("引数が多すぎます", line, 1, false)
        val term = ExpressionParser.toStrFormTerm(sfwt).restructure(exm)
        val ret = ExpressionArgument(term)
        if (term is SingleTerm) {
            ret.constStr = term.getStrValue(exm)
            if (line.functionCode == FunctionCode.ONEINPUTS) {
                if (ret.constStr.isNullOrEmpty()) {
                    warn("引数が空文字列なため、引数は無視されます", line, 1, false)
                    return ExpressionArgument(null)
                } else if (ret.constStr!!.length > 1) {
                    warn("ONEINPUTSの引数に２文字以上の文字列が渡されています（２文字目以降は無視されます）", line, 1, false)
                    ret.constStr = ret.constStr!!.substring(0, 1)
                }
            }
            ret.isConst = true
        }
        return ret
    }
}

private class INT_EXPRESSION_ArgumentBuilder(private val nullable: Boolean) : ArgumentBuilder() {
    init { argumentTypeArray = arrayOf(I); minArg = 0 }
    override fun createArgument(line: InstructionLine, exm: ExpressionMediator): Argument? {
        val terms = popTerms(line)
        if (!checkArgumentType(line, exm, terms)) return null
        val term: IOperandTerm
        if (terms.isEmpty()) {
            term = SingleTerm(0L)
            if (!nullable) {
                if (line.function.isExtended()) warn("省略できない引数が省略されています。Emueraは0を補います", line, 1, false)
                else warn("省略できない引数が省略されています。Emueraは0を補いますがeramakerの動作は不定です", line, 1, false)
            }
        } else term = terms[0] ?: SingleTerm(0L)
        if (line.functionCode == FunctionCode.REPEAT) {
            if (term is SingleTerm && term.int <= 0L) warn("0回以下のREPEATです。(eramakerではエラーになります)", line, 0, true)
            val repCount = sysVar("COUNT")
            repCount.restructure(exm)
            return SpForNextArgment(repCount, SingleTerm(0L), term, SingleTerm(1L))
        }
        val ret = ExpressionArgument(term)
        if (term is SingleTerm) {
            val i = term.int
            ret.constInt = i
            ret.isConst = true
            if (line.functionCode == FunctionCode.CLEARLINE) {
                if (i <= 0L) warn("引数に0以下の値が渡されています(この行は何もしません)", line, 1, false)
            } else if (line.functionCode == FunctionCode.FONTSTYLE) {
                if (i < 0L) warn("引数に負の値が渡されています(結果は不定です)", line, 1, false)
            }
        }
        return ret
    }
}

private class INT_ANY_ArgumentBuilder : ArgumentBuilder() {
    init { argumentTypeArray = arrayOf(I); minArg = 0; argAny = true }
    override fun createArgument(line: InstructionLine, exm: ExpressionMediator): Argument? {
        val terms = popTerms(line)
        if (!checkArgumentType(line, exm, terms)) return null
        if (terms.isEmpty()) {
            if (line.functionCode == FunctionCode.RETURN) {
                return ExpressionArrayArgument(listOf(SingleTerm(0L))).also { it.isConst = true; it.constInt = 0 }
            }
            warn("引数が設定されていません", line, 2, false)
            return null
        }
        val ret = ExpressionArrayArgument(terms.toList())
        if (terms.size == 1) {
            val t0 = terms[0]
            if (t0 is SingleTerm) {
                ret.isConst = true
                ret.constInt = t0.int
                return ret
            } else if (line.functionCode == FunctionCode.RETURN) {
                if (t0 is VariableTerm) warn("RETURNの引数に変数が渡されています(eramaker：常に0を返します)", line, 0, true)
                else warn("RETURNの引数に数式が渡されています(eramaker：Emueraとは異なる値を返します)", line, 0, true)
            }
        } else {
            warn(line.function.name + "の引数に複数の値が与えられています(eramaker：非対応です)", line, 0, true)
        }
        return ret
    }
}

private class STR_EXPRESSION_ArgumentBuilder(nullable: Boolean) : ArgumentBuilder() {
    init { argumentTypeArray = arrayOf(S); if (nullable) minArg = 0 }
    override fun createArgument(line: InstructionLine, exm: ExpressionMediator): Argument? {
        val terms = popTerms(line)
        if (!checkArgumentType(line, exm, terms)) return null
        if (terms.isEmpty()) return ExpressionArgument(SingleTerm("")).also { it.constStr = ""; it.isConst = true }
        return ExpressionArgument(terms[0])
    }
}

private class EXPRESSION_ArgumentBuilder(nullable: Boolean) : ArgumentBuilder() {
    init { argumentTypeArray = arrayOf(V); if (nullable) minArg = 0 }
    override fun createArgument(line: InstructionLine, exm: ExpressionMediator): Argument? {
        val terms = popTerms(line)
        if (!checkArgumentType(line, exm, terms)) return null
        if (terms.isEmpty()) return ExpressionArgument(null).also { it.constStr = ""; it.constInt = 0; it.isConst = true }
        return ExpressionArgument(terms[0])
    }
}

private class SP_BAR_ArgumentBuilder : ArgumentBuilder() {
    init { argumentTypeArray = arrayOf(I, I, I) }
    override fun createArgument(line: InstructionLine, exm: ExpressionMediator): Argument? {
        val terms = popTerms(line)
        if (!checkArgumentType(line, exm, terms)) return null
        return SpBarArgument(terms[0]!!, terms[1]!!, terms[2]!!)
    }
}

private class SP_SWAP_ArgumentBuilder(nullable: Boolean) : ArgumentBuilder() {
    init { argumentTypeArray = arrayOf(I, I); if (nullable) minArg = 1 }
    override fun createArgument(line: InstructionLine, exm: ExpressionMediator): Argument? {
        val terms = popTerms(line)
        if (!checkArgumentType(line, exm, terms)) return null
        return SpSwapCharaArgument(terms[0]!!, if (terms.size > 1) terms[1] else null)
    }
}

private class SP_SAVEDATA_ArgumentBuilder : ArgumentBuilder() {
    init { argumentTypeArray = arrayOf(I, S) }
    override fun createArgument(line: InstructionLine, exm: ExpressionMediator): Argument? {
        val terms = popTerms(line)
        if (!checkArgumentType(line, exm, terms)) return null
        return SpSaveDataArgument(terms[0]!!, terms[1]!!)
    }
}

private class SP_TINPUT_ArgumentBuilder(defType: EType) : ArgumentBuilder() {
    init { argumentTypeArray = arrayOf(I, defType, I, S); minArg = 2 }
    override fun createArgument(line: InstructionLine, exm: ExpressionMediator): Argument? {
        val terms = popTerms(line)
        if (!checkArgumentType(line, exm, terms)) return null
        return SpTInputsArgument(terms[0]!!, terms[1]!!, if (terms.size > 2) terms[2] else null, if (terms.size > 3) terms[3] else null)
    }
}

private class SP_FOR_NEXT_ArgumentBuilder : ArgumentBuilder() {
    init { argumentTypeArray = arrayOf(I, null, I, I); minArg = 3 }
    override fun createArgument(line: InstructionLine, exm: ExpressionMediator): Argument? {
        val terms = popTerms(line)
        if (!checkArgumentType(line, exm, terms)) return null
        val varTerm = getChangeableVariable(terms, 1, line) ?: return null
        if (varTerm.identifier.isCharacterData) { warn("第1引数にキャラクタ変数を指定することはできません", line, 2, false); return null }
        val start = terms[1] ?: SingleTerm(0L)
        val end = terms[2]!!
        val step = if (terms.size > 3 && terms[3] != null) terms[3]!! else SingleTerm(1L)
        if (!start.isInteger) { warn("第2引数の型が違います", line, 2, false); return null }
        return SpForNextArgment(varTerm, start, end, step)
    }
}

private class SP_POWER_ArgumentBuilder : ArgumentBuilder() {
    init { argumentTypeArray = arrayOf(I, I, I) }
    override fun createArgument(line: InstructionLine, exm: ExpressionMediator): Argument? {
        val terms = popTerms(line)
        if (!checkArgumentType(line, exm, terms)) return null
        val varTerm = getChangeableVariable(terms, 1, line) ?: return null
        return SpPowerArgument(varTerm, terms[1]!!, terms[2]!!)
    }
}

private class SP_SWAPVAR_ArgumentBuilder : ArgumentBuilder() {
    init { argumentTypeArray = arrayOf(V, V) }
    override fun createArgument(line: InstructionLine, exm: ExpressionMediator): Argument? {
        val terms = popTerms(line)
        if (!checkArgumentType(line, exm, terms)) return null
        val x = getChangeableVariable(terms, 1, line) ?: return null
        val y = getChangeableVariable(terms, 2, line) ?: return null
        if (x.getOperandType() != y.getOperandType()) { warn("引数の型が異なります", line, 2, false); return null }
        return SpSwapVarArgument(x, y)
    }
}

private class VAR_INT_ArgumentBuilder : ArgumentBuilder() {
    init { argumentTypeArray = arrayOf(I); minArg = 0 }
    override fun createArgument(line: InstructionLine, exm: ExpressionMediator): Argument? {
        val terms = popTerms(line)
        if (terms.isEmpty()) return PrintDataArgument(null)
        if (!checkArgumentType(line, exm, terms)) return null
        val varTerm = getChangeableVariable(terms, 1, line) ?: return null
        return PrintDataArgument(varTerm)
    }
}

private class VAR_STR_ArgumentBuilder : ArgumentBuilder() {
    init { argumentTypeArray = arrayOf(S); minArg = 0 }
    override fun createArgument(line: InstructionLine, exm: ExpressionMediator): Argument? {
        val terms = popTerms(line)
        if (terms.isEmpty()) return StrDataArgument(sysVar("RESULTS"))
        if (!checkArgumentType(line, exm, terms)) return null
        val x = getChangeableVariable(terms, 1, line) ?: return null
        return StrDataArgument(x)
    }
}

private class BIT_ARG_ArgumentBuilder : ArgumentBuilder() {
    init { argumentTypeArray = arrayOf(I, I); minArg = 2; argAny = true }
    override fun createArgument(line: InstructionLine, exm: ExpressionMediator): Argument? {
        val terms = popTerms(line)
        if (!checkArgumentType(line, exm, terms)) return null
        val varTerm = getChangeableVariable(terms, 1, line) ?: return null
        val termList = terms.drop(1)
        val ret = BitArgument(varTerm, termList.toTypedArray())
        for (i in termList.indices) {
            val t = termList[i]
            if (t is SingleTerm) {
                val bit = t.int
                if (bit < 0 || bit > 63) {
                    warn("第" + KanaConv.toWide((i + 2).toString()) + "引数(" + bit + ")が範囲(０～６３)を超えています", line, 2, false)
                    return null
                }
            }
        }
        return ret
    }
}

private class SP_VAR_SET_ArgumentBuilder : ArgumentBuilder() {
    init { argumentTypeArray = arrayOf(V, V, I, I); minArg = 1 }
    override fun createArgument(line: InstructionLine, exm: ExpressionMediator): Argument? {
        val terms = popTerms(line)
        if (!checkArgumentType(line, exm, terms)) return null
        val varTerm = getChangeableVariable(terms, 1, line) ?: return null
        if (varTerm.identifier.isConst) { warn("値を変更できない変数" + varTerm.identifier.name + "が指定されました", line, 2, false); return null }
        val term: IOperandTerm = if (terms.size > 1 && terms[1] != null) terms[1]!! else if (varTerm.isString) SingleTerm("") else SingleTerm(0L)
        if (varTerm is VariableNoArgTerm) {
            if (terms.size > 2) { warn("対象となる変数" + varTerm.identifier.name + "の要素を省略する場合には第3引数以降を設定できません", line, 2, false); return null }
            return SpVarSetArgument(FixedVariableTerm(varTerm.identifier), term, null, null)
        }
        val term3 = if (terms.size > 2) terms[2] else null
        val term4 = if (terms.size > 3) terms[3] else null
        if (terms.size >= 3 && !varTerm.identifier.isArray1D) warn("第３引数以降は1次元配列以外では無視されます", line, 1, false)
        if (term.getOperandType() != varTerm.getOperandType()) { warn("２つの引数の型が一致していません", line, 2, false); return null }
        return SpVarSetArgument(varTerm, term, term3, term4)
    }
}

private class SP_CVAR_SET_ArgumentBuilder : ArgumentBuilder() {
    init { argumentTypeArray = arrayOf(V, V, V, I, I); minArg = 1 }
    override fun createArgument(line: InstructionLine, exm: ExpressionMediator): Argument? {
        val terms = popTerms(line)
        if (!checkArgumentType(line, exm, terms)) return null
        val varTerm = getChangeableVariable(terms, 1, line) ?: return null
        if (!varTerm.identifier.isCharacterData) { warn("第１引数にキャラクタ変数以外の変数を指定することはできません", line, 2, false); return null }
        if (varTerm.identifier.isArray2D) { warn("第１引数に二次元配列の変数を指定することはできません", line, 2, false); return null }
        val index: IOperandTerm = if (terms.size > 1 && terms[1] != null) terms[1]!! else SingleTerm(0L)
        val term: IOperandTerm = if (terms.size > 2 && terms[2] != null) terms[2]!! else if (varTerm.isString) SingleTerm("") else SingleTerm(0L)
        val term4 = if (terms.size > 3) terms[3] else null
        val term5 = if (terms.size > 4) terms[4] else null
        if (index is SingleTerm && index.getOperandType() == S && varTerm.identifier.isArray1D) {
            if (!GlobalStatic.ConstantData!!.isDefined(varTerm.identifier.code, index.str)) {
                warn("文字列" + index.str + "は変数" + varTerm.identifier.name + "の要素ではありません", line, 2, false); return null
            }
        }
        if (terms.size > 3 && !varTerm.identifier.isArray1D) warn("第４引数以降は1次元配列以外では無視されます", line, 1, false)
        if (term.getOperandType() != varTerm.getOperandType()) { warn("２つの引数の型が一致していません", line, 2, false); return null }
        return SpCVarSetArgument(varTerm, index, term, term4, term5)
    }
}

private class SP_BUTTON_ArgumentBuilder : ArgumentBuilder() {
    init { argumentTypeArray = arrayOf(S, V) }
    override fun createArgument(line: InstructionLine, exm: ExpressionMediator): Argument? {
        val terms = popTerms(line)
        if (!checkArgumentType(line, exm, terms)) return null
        return SpButtonArgument(terms[0]!!, terms[1]!!)
    }
}

private class SP_COLOR_ArgumentBuilder : ArgumentBuilder() {
    init { argumentTypeArray = arrayOf(I, I, I); minArg = 1 }
    override fun createArgument(line: InstructionLine, exm: ExpressionMediator): Argument? {
        val terms = popTerms(line)
        if (!checkArgumentType(line, exm, terms)) return null
        if (terms.size == 2) { warn("SETCOLORの引数の数が不正です(SETCOLORの引数は1個もしくは3個です)", line, 2, false); return null }
        val arg: SpColorArgument
        if (terms.size == 1) {
            arg = SpColorArgument(terms[0]!!)
            if (terms[0] is SingleTerm) { arg.constInt = terms[0]!!.getIntValue(exm); arg.isConst = true }
        } else {
            arg = SpColorArgument(terms[0]!!, terms[1]!!, terms[2]!!)
            if (terms[0] is SingleTerm && terms[1] is SingleTerm && terms[2] is SingleTerm) {
                arg.constInt = (terms[0]!!.getIntValue(exm) shl 16) + (terms[1]!!.getIntValue(exm) shl 8) + terms[2]!!.getIntValue(exm)
                arg.isConst = true
            }
        }
        return arg
    }
}

private class SP_SPLIT_ArgumentBuilder : ArgumentBuilder() {
    init { argumentTypeArray = arrayOf(S, S, S, I); minArg = 3 }
    override fun createArgument(line: InstructionLine, exm: ExpressionMediator): Argument? {
        val terms = popTerms(line)
        if (!checkArgumentType(line, exm, terms)) return null
        val x = getChangeableVariable(terms, 3, line) ?: return null
        if (!x.identifier.isArray1D && !x.identifier.isArray2D && !x.identifier.isArray3D) { warn("第３引数は配列変数でなければなりません", line, 2, false); return null }
        val term = if (terms.size >= 4) getChangeableVariable(terms, 4, line) else sysVar("RESULT")
        return SpSplitArgument(terms[0]!!, terms[1]!!, x.identifier, term)
    }
}

private class SP_HTMLSPLIT_ArgumentBuilder : ArgumentBuilder() {
    init { argumentTypeArray = arrayOf(S, S, I); minArg = 1 }
    override fun createArgument(line: InstructionLine, exm: ExpressionMediator): Argument? {
        val terms = popTerms(line)
        if (!checkArgumentType(line, exm, terms)) return null
        var destVarTerm: VariableTerm? = null
        var term: VariableTerm? = null
        if (terms.size >= 2) destVarTerm = getChangeableVariable(terms, 2, line)
        val destVar = destVarTerm?.identifier ?: GlobalStatic.VariableData!!.getSystemVariableToken("RESULTS")
        if (!destVar.isArray1D || destVar.isCharacterData) { warn("第２引数は非キャラ型の1次元配列変数でなければなりません", line, 2, false); return null }
        if (terms.size >= 3) term = getChangeableVariable(terms, 3, line)
        if (term == null) term = sysVar("RESULT")
        return SpHtmlSplitArgument(terms[0]!!, destVar, term)
    }
}

private class SP_GETINT_ArgumentBuilder : ArgumentBuilder() {
    init { argumentTypeArray = arrayOf(I); minArg = 0 }
    override fun createArgument(line: InstructionLine, exm: ExpressionMediator): Argument? {
        val terms = popTerms(line)
        if (terms.isEmpty()) return SpGetIntArgument(sysVar("RESULT"))
        if (!checkArgumentType(line, exm, terms)) return null
        val x = getChangeableVariable(terms, 1, line) ?: return null
        return SpGetIntArgument(x)
    }
}

private class SP_CONTROL_ARRAY_ArgumentBuilder : ArgumentBuilder() {
    init { argumentTypeArray = arrayOf(V, I, I) }
    override fun createArgument(line: InstructionLine, exm: ExpressionMediator): Argument? {
        val terms = popTerms(line)
        if (!checkArgumentType(line, exm, terms)) return null
        val x = getChangeableVariable(terms, 1, line) ?: return null
        return SpArrayControlArgument(x, terms[1]!!, terms[2]!!)
    }
}

private class SP_SHIFT_ARRAY_ArgumentBuilder : ArgumentBuilder() {
    init { argumentTypeArray = arrayOf(V, I, V, I, I); minArg = 3 }
    override fun createArgument(line: InstructionLine, exm: ExpressionMediator): Argument? {
        val terms = popTerms(line)
        if (!checkArgumentType(line, exm, terms)) return null
        val x = getChangeableVariable(terms, 1, line) ?: return null
        if (!x.identifier.isArray1D) { warn("第１引数に１次元配列もしくは配列型キャラクタ変数以外を指定することはできません", line, 2, false); return null }
        if (line.functionCode == FunctionCode.ARRAYSHIFT) {
            if (terms[0]!!.getOperandType() != terms[2]!!.getOperandType()) { warn("第１引数と第３引数の型が違います", line, 2, false); return null }
        }
        val term4 = if (terms.size >= 4) terms[3] else SingleTerm(0L)
        val term5 = if (terms.size >= 5) terms[4] else null
        return SpArrayShiftArgument(x, terms[1]!!, terms[2]!!, term4, term5)
    }
}

private class SP_SAVEVAR_ArgumentBuilder : ArgumentBuilder() {
    init { argumentTypeArray = arrayOf(S, S, V); argAny = true; minArg = 3 }
    override fun createArgument(line: InstructionLine, exm: ExpressionMediator): Argument? {
        val terms = popTerms(line)
        if (!checkArgumentType(line, exm, terms)) return null
        val varTokens = ArrayList<VariableToken>()
        for (i in 2 until terms.size) {
            if (terms[i] == null) { warn("第" + (i + 1) + "引数を省略できません", line, 2, false); return null }
            val vTerm = getChangeableVariable(terms, i + 1, line) ?: return null
            val vToken = vTerm.identifier
            if (vToken.isCharacterData) { warn("キャラクタ変数" + vToken.name + "はセーブできません(キャラクタ変数のSAVEにはSAVECHARAを使用します)", line, 2, false); return null }
            if (vToken.isPrivate) { warn("プライベート変数" + vToken.name + "はセーブできません", line, 2, false); return null }
            if (vToken.isLocal) { warn("ローカル変数" + vToken.name + "はセーブできません", line, 2, false); return null }
            if (vToken.isConst) { warn("値を変更できない変数はセーブできません", line, 2, false); return null }
            if (vToken.isCalc) { warn("疑似変数はセーブできません", line, 2, false); return null }
            if (vToken.isReference) { warn("参照型変数はセーブできません", line, 2, false); return null }
            varTokens.add(vToken)
        }
        for (i in varTokens.indices) for (j in i + 1 until varTokens.size)
            if (varTokens[i] === varTokens[j]) { warn("変数" + varTokens[i].name + "を二度以上保存しようとしています", line, 1, false); return null }
        return SpSaveVarArgument(terms[0]!!, terms[1]!!, varTokens.toTypedArray())
    }
}

private class SP_SAVECHARA_ArgumentBuilder : ArgumentBuilder() {
    init { argumentTypeArray = arrayOf(S, S, I); minArg = 3; argAny = true }
    override fun createArgument(line: InstructionLine, exm: ExpressionMediator): Argument? {
        val terms = popTerms(line)
        if (!checkArgumentType(line, exm, terms)) return null
        val termList = terms.toList()
        val ret = ExpressionArrayArgument(termList)
        for (i in 2 until termList.size) {
            val ti = termList[i] as? SingleTerm ?: continue
            val iValue = ti.int
            if (iValue < 0) { warn("キャラ登録番号は正の値でなければなりません", line, 2, false); return null }
            if (iValue > Int.MAX_VALUE) { warn("キャラ登録番号が32bit符号付整数の上限を超えています", line, 2, false); return null }
            for (j in i + 1 until termList.size) {
                val tj = termList[j] as? SingleTerm ?: continue
                if (iValue == tj.int) { warn("キャラ登録番号${iValue}を二度以上保存しようとしています", line, 1, false); return null }
            }
        }
        return ret
    }
}

private class SP_REF_ArgumentBuilder(private val byname: Boolean) : ArgumentBuilder() {
    init { argumentTypeArray = arrayOf(V, V); minArg = 2 }
    override fun createArgument(line: InstructionLine, exm: ExpressionMediator): Argument? {
        val wc = popWords(line)
        val id = wc.current as? IdentifierWord
        wc.shiftNext()
        if (id == null || wc.current.type != ',') { warn("書式が間違っています", line, 2, false); return null }
        wc.shiftNext()
        var name: IOperandTerm? = null
        var srcCode: String? = null
        if (byname) {
            name = ExpressionParser.reduceExpressionTerm(wc, TermEndWith.EoL)
            if (name == null || name.isInteger || !wc.eol) { warn("書式が間違っています", line, 2, false); return null }
            name = name.restructure(exm)
            if (name is SingleTerm) srcCode = name.getStrValue(exm)
        } else {
            val id2 = wc.current as? IdentifierWord
            wc.shiftNext()
            if (id2 == null || !wc.eol) { warn("書式が間違っています", line, 2, false); return null }
            srcCode = id2.code
        }
        val dic = GlobalStatic.IdentifierDictionary!!
        val refm = dic.getRefMethod(id.code)
        var refVar: ReferenceToken? = null
        if (refm == null) {
            val token = dic.getVariableToken(id.code, null, true)
            if (token == null || !token.isReference) { warn("第一引数は関数参照か参照型変数でなければなりません", line, 2, false); return null }
            refVar = token as ReferenceToken
        }
        if (refm != null) {
            if (srcCode == null) return RefArgument.method(refm, name!!)
            val srcRef = dic.getRefMethod(srcCode)
            if (srcRef != null) return RefArgument.method(refm, srcRef)
            val label = GlobalStatic.LabelDictionary!!.getNonEventLabel(srcCode) ?: run { warn("式中関数${srcCode}が見つかりません", line, 2, false); return null }
            if (!label.isMethod) { warn("#FUNCTION(S)属性を持たない関数${srcCode}は参照できません", line, 2, false); return null }
            return RefArgument.method(refm, CalledFunction.createCalledFunctionMethod(label, label.labelName))
        } else {
            if (srcCode == null) return RefArgument.variable(refVar!!, name!!)
            val srcVar = dic.getVariableToken(srcCode, null, true) ?: run { warn("変数${srcCode}が見つかりません", line, 2, false); return null }
            return RefArgument.variable(refVar!!, srcVar)
        }
    }
}

private class SP_INPUT_ArgumentBuilder : ArgumentBuilder() {
    init { argumentTypeArray = arrayOf(I); minArg = 0 }
    override fun createArgument(line: InstructionLine, exm: ExpressionMediator): Argument? {
        val terms = popTerms(line)
        if (!checkArgumentType(line, exm, terms)) return null
        if (terms.isEmpty()) return ExpressionArgument(null)
        val term = terms[0]
        val ret = ExpressionArgument(term)
        if (term is SingleTerm) {
            var i = term.int
            if (line.functionCode == FunctionCode.ONEINPUT) {
                if (i < 0) {
                    warn("ONEINPUTの引数にONEINPUTが受け取れない負の数数が指定されています（引数を無効とします）", line, 1, false)
                    return ExpressionArgument(null)
                } else if (i > 9) {
                    warn("ONEINPUTの引数にONEINPUTが受け取れない2桁以上の数数が指定されています（最初の桁を引数と見なします）", line, 1, false)
                    i = i.toString().substring(0, 1).toLong()
                }
            }
            ret.constInt = i
            ret.isConst = true
        }
        return ret
    }
}

private class SP_COPY_ARRAY_Arguments : ArgumentBuilder() {
    init { argumentTypeArray = arrayOf(S, S); minArg = 2 }
    override fun createArgument(line: InstructionLine, exm: ExpressionMediator): Argument? {
        val terms = popTerms(line)
        if (!checkArgumentType(line, exm, terms)) return null
        val dic = GlobalStatic.IdentifierDictionary!!
        var v0: VariableToken? = null
        var v1: VariableToken? = null
        val t0 = terms[0]
        if (t0 is SingleTerm) {
            v0 = dic.getVariableToken(t0.str, null, true) ?: run { warn("ARRAYCOPY命令の第１引数\"${t0.str}\"は変数名として存在しません", line, 2, false); return null }
            if (!v0.isArray1D && !v0.isArray2D && !v0.isArray3D) { warn("ARRAYCOPY命令の第１引数\"${t0.str}\"は配列変数ではありません", line, 2, false); return null }
            if (v0.isCharacterData) { warn("ARRAYCOPY命令の第１引数\"${t0.str}\"はキャラクタ変数です（対応していません）", line, 2, false); return null }
        }
        val t1 = terms[1]
        if (t1 is SingleTerm) {
            v1 = dic.getVariableToken(t1.str, null, true) ?: run { warn("ARRAYCOPY命令の第２引数\"${t1.str}\"は変数名として存在しません", line, 2, false); return null }
            if (!v1.isArray1D && !v1.isArray2D && !v1.isArray3D) warn("ARRAYCOPY命令の第２引数\"${t1.str}\"は配列変数ではありません", line, 2, false)
            if (v1.isCharacterData) { warn("ARRAYCOPY命令の第２引数\"${t1.str}\"はキャラクタ変数です（対応していません）", line, 2, false); return null }
            if (v1.isConst) { warn("ARRAYCOPY命令の第２引数\"${t1.str}\"は値を変更できない変数です", line, 2, false); return null }
        }
        if (v0 != null && v1 != null) {
            if ((v0.isArray1D && !v1.isArray1D) || (v0.isArray2D && !v1.isArray2D) || (v0.isArray3D && !v1.isArray3D)) { warn("ARRAYCOPY命令の2つの引数の次元が異なります", line, 2, false); return null }
            if ((v0.isInteger && v1.isString) || (v0.isString && v1.isInteger)) { warn("ARRAYCOPY命令の２つの配列変数の型が一致していません", line, 2, false); return null }
        }
        return SpCopyArrayArgument(terms[0]!!, terms[1]!!)
    }
}

private class Expressions_ArgumentBuilder(types: Array<EType?>, minArgs: Int = -1) : ArgumentBuilder() {
    init { argumentTypeArray = types; minArg = minArgs }
    override fun createArgument(line: InstructionLine, exm: ExpressionMediator): Argument? {
        val terms = popTerms(line)
        if (!checkArgumentType(line, exm, terms)) return null
        return ExpressionsArgument(argumentTypeArray, terms)
    }
}
