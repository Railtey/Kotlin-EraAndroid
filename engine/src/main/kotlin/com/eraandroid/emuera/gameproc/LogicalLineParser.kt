package com.eraandroid.emuera.gameproc

import com.eraandroid.emuera.GlobalStatic
import com.eraandroid.emuera.IdentifierDictionary
import com.eraandroid.emuera.Program
import com.eraandroid.emuera.config.Config
import com.eraandroid.emuera.config.ConfigCode
import com.eraandroid.emuera.gamedata.ParserMediator
import com.eraandroid.emuera.gamedata.expression.*
import com.eraandroid.emuera.gameproc.function.FunctionIdentifier
import com.eraandroid.emuera.gameview.EmueraConsole
import com.eraandroid.emuera.sub.*

object LogicalLineParser {
    fun parseSharpLine(label: FunctionLabelLine, st: StringStream, position: ScriptPosition?, onlyLabel: MutableList<String>): Boolean {
        st.shiftNext()
        var token = LexicalAnalyzer.readSingleIdentifier(st)
        if (Config.ICFunction) token = token.uppercase()
        if (token !in setOf("SINGLE", "LATER", "PRI", "ONLY", "FUNCTION", "FUNCTIONS", "LOCALSIZE", "LOCALSSIZE", "DIM", "DIMS")) {
            ParserMediator.warn("解釈できない#行です", position, 1)
            return false
        }
        try {
            val wc = LexicalAnalyzer.analyse(st, LexEndWith.EoL, LexAnalyzeFlag.AllowAssignment)
            when (token) {
                "SINGLE" -> when {
                    label.isMethod -> ParserMediator.warn("式中関数では#SINGLEは機能しません", position, 1)
                    !label.isEvent -> ParserMediator.warn("イベント関数以外では#SINGLEは機能しません", position, 1)
                    label.isSingle -> ParserMediator.warn("#SINGLEが重複して使われています", position, 1)
                    label.isOnly -> ParserMediator.warn("#ONLYが指定されたイベント関数では#SINGLEは機能しません", position, 1)
                    else -> label.isSingle = true
                }
                "LATER" -> when {
                    label.isMethod -> ParserMediator.warn("式中関数では#LATERは機能しません", position, 1)
                    !label.isEvent -> ParserMediator.warn("イベント関数以外では#LATERは機能しません", position, 1)
                    label.isLater -> ParserMediator.warn("#LATERが重複して使われています", position, 1)
                    label.isOnly -> ParserMediator.warn("#ONLYが指定されたイベント関数では#LATERは機能しません", position, 1)
                    else -> {
                        if (label.isPri) ParserMediator.warn("#PRIと#LATERが重複して使われています(この関数は2度呼ばれます)", position, 1)
                        label.isLater = true
                    }
                }
                "PRI" -> when {
                    label.isMethod -> ParserMediator.warn("式中関数では#PRIは機能しません", position, 1)
                    !label.isEvent -> ParserMediator.warn("イベント関数以外では#PRIは機能しません", position, 1)
                    label.isPri -> ParserMediator.warn("#PRIが重複して使われています", position, 1)
                    label.isOnly -> ParserMediator.warn("#ONLYが指定されたイベント関数では#PRIは機能しません", position, 1)
                    else -> {
                        if (label.isLater) ParserMediator.warn("#PRIと#LATERが重複して使われています(この関数は2度呼ばれます)", position, 1)
                        label.isPri = true
                    }
                }
                "ONLY" -> when {
                    label.isMethod -> ParserMediator.warn("式中関数では#ONLYは機能しません", position, 1)
                    !label.isEvent -> ParserMediator.warn("イベント関数以外では#ONLYは機能しません", position, 1)
                    label.isOnly -> ParserMediator.warn("#ONLYが重複して使われています", position, 1)
                    else -> {
                        if (onlyLabel.contains(label.labelName))
                            ParserMediator.warn("このイベント関数\"@${label.labelName}\"にはすでに#ONLYが宣言されています（この関数は実行されません）", position, 1)
                        onlyLabel.add(label.labelName)
                        label.isOnly = true
                        if (label.isPri) { ParserMediator.warn("このイベント関数には#PRIが宣言されていますが無視されます", position, 1); label.isPri = false }
                        if (label.isLater) { ParserMediator.warn("このイベント関数には#LATERが宣言されていますが無視されます", position, 1); label.isLater = false }
                        if (label.isSingle) { ParserMediator.warn("このイベント関数には#SINGLEが宣言されていますが無視されます", position, 1); label.isSingle = false }
                    }
                }
                "FUNCTION", "FUNCTIONS" -> run {
                    if (label.labelName.isNotEmpty() && label.labelName[0].isDigit()) {
                        ParserMediator.warn("#${token}属性は関数名が数字で始まる関数には指定できません", position, 1)
                        label.isError = true
                        label.errMes = "関数名が数字で始まっています"
                        return@run
                    }
                    if (label.isMethod) {
                        if ((label.methodType == EType.Int64 && token == "FUNCTION") || (label.methodType == EType.String && token == "FUNCTIONS")) {
                            ParserMediator.warn("関数${label.labelName}にはすでに#${token}が宣言されています(この行は無視されます)", position, 1)
                            return false
                        }
                        if (label.methodType == EType.Int64 && token == "FUNCTIONS") ParserMediator.warn("関数${label.labelName}にはすでに#FUNCTIONが宣言されています", position, 2)
                        else if (label.methodType == EType.String && token == "FUNCTION") ParserMediator.warn("関数${label.labelName}にはすでに#FUNCTIONSが宣言されています", position, 2)
                        return false
                    }
                    if (label.depth == 0) {
                        ParserMediator.warn("システム関数に#${token}が指定されています", position, 2)
                        return false
                    }
                    label.isMethod = true
                    label.depth = 0
                    label.methodType = if (token == "FUNCTIONS") EType.String else EType.Int64
                    if (label.isPri) { ParserMediator.warn("式中関数では#PRIは機能しません", position, 1); label.isPri = false }
                    if (label.isLater) { ParserMediator.warn("式中関数では#LATERは機能しません", position, 1); label.isLater = false }
                    if (label.isSingle) { ParserMediator.warn("式中関数では#SINGLEは機能しません", position, 1); label.isSingle = false }
                    if (label.isOnly) { ParserMediator.warn("式中関数では#ONLYは機能しません", position, 1); label.isOnly = false }
                }
                "LOCALSIZE", "LOCALSSIZE" -> run {
                    if (wc.eol) { ParserMediator.warn("#${token}の後に有効な数値が指定されていません", position, 2); return@run }
                    if (label.isEvent) {
                        ParserMediator.warn("イベント関数では#${token}による${token.substring(0, token.length - 4)}のサイズ指定は無視されます", position, 1)
                        return@run
                    }
                    val a = ExpressionParser.reduceIntegerTerm(wc, TermEndWith.EoL)
                    val sizeTerm = a.restructure(GlobalStatic.EMediator!!) as? SingleTerm
                    if (sizeTerm == null || sizeTerm.getOperandType() != EType.Int64) {
                        ParserMediator.warn("#${token}の後に有効な定数式が指定されていません", position, 2); return@run
                    }
                    if (sizeTerm.int <= 0) { ParserMediator.warn("#${token}に0以下の値(${sizeTerm.int})が与えられました。設定は無視されます", position, 1); return@run }
                    if (sizeTerm.int >= Int.MAX_VALUE) { ParserMediator.warn("#${token}に大きすぎる値(${sizeTerm.int})が与えられました。設定は無視されます", position, 1); return@run }
                    val size = sizeTerm.int.toInt()
                    val dic = GlobalStatic.IdentifierDictionary!!
                    if (token == "LOCALSIZE") {
                        if (dic.getLocalIsForbid("LOCAL")) { ParserMediator.warn("#${token}が指定されていますが変数LOCALは使用禁止されています", position, 2); return@run }
                        if (label.localLength > 0) ParserMediator.warn("この関数にはすでに#LOCALSIZEが定義されています。（以前の定義は無視されます）", position, 1)
                        label.localLength = size
                    } else {
                        if (dic.getLocalIsForbid("LOCALS")) { ParserMediator.warn("#${token}が指定されていますが変数LOCALSは使用禁止されています", position, 2); return@run }
                        if (label.localsLength > 0) ParserMediator.warn("この関数にはすでに#LOCALSSIZEが定義されています。（以前の定義は無視されます）", position, 1)
                        label.localsLength = size
                    }
                }
                "DIM", "DIMS" -> {
                    val data = UserDefinedVariableData.create(wc, token == "DIMS", true, position)
                    if (!label.addPrivateVariable(data)) {
                        ParserMediator.warn("変数名${data.Name}は既に使用されています", position, 2)
                        return false
                    }
                }
            }
            if (!wc.eol) ParserMediator.warn("#の識別子の後に余分な文字があります", position, 1)
        } catch (e: Exception) {
            ParserMediator.warn(e.message ?: e.toString(), position, 2)
            return false
        }
        return true
    }

    fun parseLine(str: String, console: EmueraConsole?): LogicalLine? = parseLine(StringStream(str), ScriptPosition(), console)

    fun parseLabelLine(stream: StringStream, position: ScriptPosition?, console: EmueraConsole?): LogicalLine {
        val isFunction = stream.current == '@'
        var labelName = ""
        var errMes: String
        run {
            try {
                stream.shiftNext()
                val wc = LexicalAnalyzer.analyse(stream, LexEndWith.EoL, LexAnalyzeFlag.AllowAssignment)
                val first = wc.current
                if (wc.eol || first !is IdentifierWord) { errMes = "関数名が不正であるか存在しません"; return@run }
                labelName = first.code
                wc.shiftNext()
                if (Config.ICVariable) labelName = labelName.uppercase()
                val err = GlobalStatic.IdentifierDictionary!!.checkUserLabelName(isFunction, labelName)
                if (err != null) {
                    if (err.second >= 2) { errMes = err.first; return@run }
                    ParserMediator.warn(err.first, position, err.second)
                }
                if (!isFunction) {
                    if (!wc.eol) ParserMediator.warn("\$で始まるラベルに引数が設定されています", position, 1)
                    return GotoLabelLine(position, labelName)
                }
                if (Program.AnalysisMode) console?.printC("@$labelName", false)
                val funclabelLine = FunctionLabelLine(position, labelName, wc)
                if (IdentifierDictionary.isEventLabelName(labelName)) {
                    funclabelLine.isEvent = true
                    funclabelLine.isSystem = true
                    funclabelLine.depth = 0
                } else if (IdentifierDictionary.isSystemLabelName(labelName)) {
                    funclabelLine.isSystem = true
                    funclabelLine.depth = 0
                }
                return funclabelLine
            } catch (e: CodeEE) {
                errMes = e.message ?: ""
            }
        }
        if (isFunction) {
            if (labelName.isEmpty()) labelName = "<Error>"
            return InvalidLabelLine(position, labelName, errMes)
        }
        return InvalidLine(position, errMes)
    }

    fun parseLine(stream: StringStream, position: ScriptPosition?, console: EmueraConsole?): LogicalLine? {
        LexicalAnalyzer.skipWhiteSpace(stream)
        if (stream.eos) return null
        try {
            if (stream.current == '+' || stream.current == '-') {
                val op = stream.current
                val wc = LexicalAnalyzer.analyse(stream, LexEndWith.EoL, LexAnalyzeFlag.None)
                val opWT = wc.current as? OperatorWord
                if (opWT == null || (opWT.code != OperatorCode.Increment && opWT.code != OperatorCode.Decrement)) {
                    return InvalidLine(position, if (op == '+') "行が'+'から始まっていますが、インクリメントではありません" else "行が'-'から始まっていますが、デクリメントではありません")
                }
                wc.shiftNext()
                return InstructionLine(position, FunctionIdentifier.SETFunction, opWT.code, wc, null)
            }
            val idWT = LexicalAnalyzer.readFirstIdentifierWord(stream)
            val func = GlobalStatic.IdentifierDictionary!!.getFunctionIdentifier(idWT.code)
            if (func != null) {
                if (stream.eos) return InstructionLine(position, func, stream)
                val c = stream.current
                if (c != ';' && c != ' ' && c != '\t' && (!Config.SystemAllowFullSpace || c != '　')) {
                    return InvalidLine(position, if (c == '　') "命令で行が始まっていますが、命令の直後に半角スペース・タブ以外の文字が来ています(この警告はシステムオプション「" + Config.getConfigName(ConfigCode.SystemAllowFullSpace) + "」により無視できます)"
                        else "命令で行が始まっていますが、命令の直後に半角スペース・タブ以外の文字が来ています")
                }
                stream.shiftNext()
                return InstructionLine(position, func, stream)
            }
            LexicalAnalyzer.skipWhiteSpace(stream)
            if (stream.eos) return InvalidLine(position, "解釈できない行です")
            stream.seekBegin(0)
            val wc1 = LexicalAnalyzer.analyse(stream, LexEndWith.Operator, LexAnalyzeFlag.None)
            var assignOP: OperatorCode
            try {
                assignOP = LexicalAnalyzer.readAssignmentOperator(stream)
            } catch (e: CodeEE) {
                return InvalidLine(position, "解釈できない行です")
            }
            if (assignOP == OperatorCode.Equal) {
                if (console != null) ParserMediator.warn("代入演算子に\"==\"が使われています", position, 0)
                assignOP = OperatorCode.Assignment
            }
            return InstructionLine(position, FunctionIdentifier.SETFunction, assignOP, wc1, stream)
        } catch (e: CodeEE) {
            return InvalidLine(position, e.message ?: "")
        }
    }
}
