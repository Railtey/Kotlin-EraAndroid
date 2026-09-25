package com.eraandroid.emuera.gameproc

import com.eraandroid.emuera.GlobalStatic
import com.eraandroid.emuera.Program
import com.eraandroid.emuera.config.Config
import com.eraandroid.emuera.config.DisplayWarningFlag
import com.eraandroid.emuera.gamedata.ParserMediator
import com.eraandroid.emuera.gamedata.expression.*
import com.eraandroid.emuera.gamedata.variable.VariableCode
import com.eraandroid.emuera.gamedata.variable.VariableNoArgTerm
import com.eraandroid.emuera.gamedata.variable.VariableTerm
import com.eraandroid.emuera.gameproc.function.*
import com.eraandroid.emuera.gameview.EmueraConsole
import com.eraandroid.emuera.sub.*
import java.util.ArrayDeque

class ErbLoader(private val output: EmueraConsole, private val exm: ExpressionMediator, private val parentProcess: Process) {
    private val ignoredFNFWarningFileList = ArrayList<String>()
    private var ignoredFNFWarningCount = 0
    private var enabledLineCount = 0
    private lateinit var labelDic: LabelDictionary
    private var noError = true

    fun loadErbFiles(erbDir: String, displayReport: Boolean, labelDictionary: LabelDictionary): Boolean {
        labelDic = labelDictionary
        labelDic.initialized = false
        val erbFiles = Config.getFiles(erbDir, "*.ERB")
        val isOnlyEvent = ArrayList<String>()
        noError = true
        try {
            labelDic.removeAll()
            for ((filename, file) in erbFiles) {
                if (displayReport) output.printSystemLine(filename + "読み込み中・・・")
                output.reportLoadProgress(filename)
                loadErb(file, filename, isOnlyEvent)
            }
            ParserMediator.flushWarningList()
            if (displayReport) output.printSystemLine("ユーザー定義関数のリストを構築中・・・")
            setLabelsArg()
            ParserMediator.flushWarningList()
            labelDic.initialized = true
            if (displayReport) output.printSystemLine("スクリプトの構文チェック中・・・")
            checkScript()
            ParserMediator.flushWarningList()
            if (displayReport) output.printSystemLine("ロード完了")
        } catch (e: Exception) {
            ParserMediator.flushWarningList()
            output.printError("予期しないエラーが発生しました:" + Program.ExeName)
            output.printError(e.javaClass.name + ":" + e.message)
            for (s in e.stackTrace.take(15)) output.printError(s.toString())
            return false
        } finally {
            parentProcess.scaningLine = null
        }
        return noError
    }

    fun loadErbs(path: List<String>, labelDictionary: LabelDictionary): Boolean {
        val isOnlyEvent = ArrayList<String>()
        noError = true
        labelDic = labelDictionary
        labelDic.initialized = false
        for (fpath in path) {
            val fname = if (fpath.startsWith(Program.ErbDir, ignoreCase = true) && !Program.AnalysisMode) fpath.substring(Program.ErbDir.length) else fpath
            if (Program.AnalysisMode) output.printSystemLine(fname + "読み込み中・・・")
            loadErb(fpath, fname, isOnlyEvent)
        }
        if (Program.AnalysisMode) output.newLine()
        ParserMediator.flushWarningList()
        setLabelsArg()
        ParserMediator.flushWarningList()
        labelDic.initialized = true
        checkScript()
        ParserMediator.flushWarningList()
        parentProcess.scaningLine = null
        return noError
    }

    private class PPState {
        private var skip = false
        private var done = false
        var disabled = false
        private val disabledStack = ArrayDeque<Boolean>()
        private val doneStack = ArrayDeque<Boolean>()
        private val ppMatch = ArrayDeque<String>()

        fun addKeyWord(token: String, token2: String?, position: ScriptPosition?) {
            val t2Empty = token2.isNullOrEmpty()
            when (token) {
                "SKIPSTART" -> run {
                    if (!t2Empty) { ParserMediator.warn(token + "に余分な引数があります", position, 1); return@run }
                    if (skip) { ParserMediator.warn("[SKIPSTART]が重複して使用されています", position, 1); return@run }
                    ppMatch.push("SKIPEND"); disabledStack.push(disabled); doneStack.push(done)
                    skip = true; disabled = true; done = false
                }
                "IF_DEBUG" -> run {
                    if (!t2Empty) { ParserMediator.warn(token + "に余分な引数があります", position, 1); return@run }
                    ppMatch.push("ELSEIF"); disabledStack.push(disabled); doneStack.push(done)
                    disabled = !Program.DebugMode; done = !disabled
                }
                "IF_NDEBUG" -> run {
                    if (!t2Empty) { ParserMediator.warn(token + "に余分な引数があります", position, 1); return@run }
                    ppMatch.push("ELSEIF"); disabledStack.push(disabled); doneStack.push(done)
                    disabled = Program.DebugMode; done = !disabled
                }
                "IF" -> run {
                    if (t2Empty) { ParserMediator.warn(token + "に引数がありません", position, 1); return@run }
                    ppMatch.push("ELSEIF"); disabledStack.push(disabled); doneStack.push(done)
                    disabled = GlobalStatic.IdentifierDictionary!!.getMacro(token2!!) == null; done = !disabled
                }
                "ELSEIF" -> run {
                    if (t2Empty) { ParserMediator.warn(token + "に引数がありません", position, 1); return@run }
                    if (ppMatch.isEmpty() || ppMatch.pop() != "ELSEIF") { ParserMediator.warn("不適切な[ELSEIF]です", position, 1); return@run }
                    ppMatch.push("ELSEIF")
                    disabled = done || GlobalStatic.IdentifierDictionary!!.getMacro(token2!!) == null
                    done = done or !disabled
                }
                "ELSE" -> run {
                    if (!t2Empty) { ParserMediator.warn(token + "に余分な引数があります", position, 1); return@run }
                    if (ppMatch.isEmpty() || ppMatch.pop() != "ELSEIF") { ParserMediator.warn("不適切な[ELSE]です", position, 1); return@run }
                    ppMatch.push("ENDIF")
                    disabled = done; done = true
                }
                "SKIPEND" -> run {
                    if (!t2Empty) { ParserMediator.warn(token + "に余分な引数があります", position, 1); return@run }
                    val match = if (ppMatch.isEmpty()) "" else ppMatch.pop()
                    if (match != "SKIPEND") { ParserMediator.warn("[SKIPSTART]と対応しない[SKIPEND]です", position, 1); return@run }
                    skip = false; disabled = disabledStack.pop(); done = doneStack.pop()
                }
                "ENDIF" -> run {
                    if (!t2Empty) { ParserMediator.warn(token + "に余分な引数があります", position, 1); return@run }
                    val match = if (ppMatch.isEmpty()) "" else ppMatch.pop()
                    if (match != "ENDIF" && match != "ELSEIF") { ParserMediator.warn("対応する[IF]のない[ENDIF]です", position, 1); return@run }
                    disabled = disabledStack.pop(); done = doneStack.pop()
                }
                else -> ParserMediator.warn("認識できないプリプロセッサです", position, 1)
            }
            if (skip) disabled = true
        }

        fun fileEnd(position: ScriptPosition?) {
            if (ppMatch.isNotEmpty()) {
                var match = ppMatch.pop()
                if (match == "ELSEIF") match = "ENDIF"
                ParserMediator.warn("[$match]がありません", position, 1)
            }
        }
    }

    private fun loadErb(filepath: String, filename: String, isOnlyEvent: MutableList<String>) {
        labelDic.addFilename(filename)
        val eReader = EraStreamReader(Config.UseRenameFile && ParserMediator.RenameDic != null)
        if (!eReader.open(filepath, filename)) {
            output.printError(eReader.fileName + "のオープンに失敗しました")
            return
        }
        try {
            val ppstate = PPState()
            var nextLine: LogicalLine?
            var lastLine: LogicalLine = NullLine()
            var lastLabelLine: FunctionLabelLine? = null
            var position: ScriptPosition?
            var funcCount = 0
            if (Program.AnalysisMode) output.printSystemLine("　")
            while (true) {
                val st = eReader.readEnabledLine(ppstate.disabled) ?: break
                position = ScriptPosition(eReader.fileName, eReader.lineNo)
                if (st.current == '[' && st.next != '[') {
                    st.shiftNext()
                    val token = LexicalAnalyzer.readSingleIdentifier(st)
                    LexicalAnalyzer.skipWhiteSpace(st)
                    val token2 = LexicalAnalyzer.readSingleIdentifier(st)
                    if (token.isEmpty() || st.current != ']') ParserMediator.warn("[]の使い方が不正です", position, 1)
                    ppstate.addKeyWord(token, token2, position)
                    st.shiftNext()
                    if (!st.eos) ParserMediator.warn("[$token]の後ろは無視されます。", position, 1)
                    continue
                }
                if (ppstate.disabled) continue
                if (st.current == '#') {
                    if (lastLine !is FunctionLabelLine) {
                        ParserMediator.warn("関数宣言の直後以外で#行が使われています", position, 1)
                        continue
                    }
                    if (!LogicalLineParser.parseSharpLine(lastLine, st, position, isOnlyEvent)) noError = false
                    continue
                }
                if (st.current == '$' || st.current == '@') {
                    val isFunction = st.current == '@'
                    nextLine = LogicalLineParser.parseLabelLine(st, position, output)
                    if (isFunction) {
                        val label = nextLine as FunctionLabelLine
                        lastLabelLine = label
                        if (label is InvalidLabelLine) {
                            noError = false
                            ParserMediator.warn(nextLine.errMes, position, 2)
                            labelDic.addInvalidLabel(label)
                        } else {
                            labelDic.addLabel(label)
                            if (!label.isEvent && (Config.WarnNormalFunctionOverloading || Program.AnalysisMode)) {
                                val seniorLabel = labelDic.getSameNameLabel(label)
                                if (seniorLabel != null) {
                                    ParserMediator.warn("関数@${label.labelName}は既に定義(${seniorLabel.position?.filename}の${seniorLabel.position?.lineNo}行目)されています", position, 1)
                                    funcCount = -1
                                }
                            }
                            funcCount++
                            if (Program.AnalysisMode && Config.PrintCPerLine > 0 && funcCount % Config.PrintCPerLine == 0) {
                                output.newLine()
                                output.printSystemLine("　")
                            }
                        }
                    } else if (nextLine is GotoLabelLine) {
                        nextLine.parentLabelLine = lastLabelLine
                        if (lastLabelLine != null && !labelDic.addLabelDollar(nextLine)) {
                            val pos = labelDic.getLabelDollar(nextLine.labelName, lastLabelLine)?.position
                            ParserMediator.warn("ラベル名\$${nextLine.labelName}は既に同じ関数内(${pos?.filename}の${pos?.lineNo}行目)で使用されています", position, 2)
                        }
                    }
                    if (nextLine is InvalidLine) {
                        noError = false
                        ParserMediator.warn(nextLine.errMes, position, 2)
                    }
                } else {
                    nextLine = LogicalLineParser.parseLine(st, position, output) ?: continue
                    if (nextLine is InvalidLine) {
                        noError = false
                        ParserMediator.warn(nextLine.errMes, position, 2)
                    }
                }
                if (lastLabelLine == null) ParserMediator.warn("関数が定義されるより前に行があります", position, 1)
                nextLine.parentLabelLine = lastLabelLine
                lastLine = addLine(nextLine, lastLine)
            }
            addLine(NullLine(), lastLine)
            ppstate.fileEnd(ScriptPosition(eReader.fileName, -1))
        } finally {
            eReader.close()
        }
    }

    private fun addLine(nextLine: LogicalLine, lastLine: LogicalLine): LogicalLine {
        enabledLineCount++
        lastLine.nextLine = nextLine
        return nextLine
    }

    private fun setLabelsArg() {
        for (label in labelDic.getAllLabels(false)) {
            try {
                if (label.argParsed) continue
                parentProcess.scaningLine = label
                parseLabel(label)
            } catch (exc: Exception) {
                var errmes = exc.message ?: ""
                if (exc !is EmueraException) errmes = exc.javaClass.name + ":" + errmes
                ParserMediator.warn("関数@${label.labelName} の引数のエラー:$errmes", label, 2, true, false)
                label.errMes = "ロード時に解析に失敗した関数が呼び出されました"
                label.isError = true
            } finally {
                parentProcess.scaningLine = null
            }
        }
        labelDic.sortLabels()
    }

    private fun parseLabel(label: FunctionLabelLine) {
        val wc = label.popRowArgs() ?: WordCollection()
        var args: Array<VariableTerm> = arrayOf()
        var defs: Array<SingleTerm?> = arrayOf()
        var maxArg = -1
        var maxArgs = -1
        label.argParsed = true
        if (label.isEvent) {
            if (!wc.eol) ParserMediator.warn("イベント関数@${label.labelName} に引数は設定できません", label, 2, true, false)
            label.arg = args
            label.def = defs
            label.argLength = -1
            label.argsLength = -1
            return
        }
        fun err(errMes: String) {
            ParserMediator.warn("関数@${label.labelName} の引数のエラー:$errMes", label, 2, true, false)
        }
        if (!wc.eol) {
            if (label.isSystem) ParserMediator.warn("システム関数@${label.labelName} に引数が設定されています", label, 1, false, false)
            var symbol = wc.current as? SymbolWord
            wc.shiftNext()
            if (symbol == null) { err("引数の書式が間違っています"); return }
            if (symbol.type == '[') {
                val subNamesRow = ExpressionParser.reduceArguments(wc, ArgsEndWith.RightBracket, false)
                if (subNamesRow.isEmpty()) { err("関数定義の[]内の引数は空にできません"); return }
                for (s in subNamesRow) {
                    if (s == null) { err("関数定義の引数は省略できません"); return }
                    if (s.restructure(exm) !is SingleTerm) { err("関数定義の[]内の引数は定数のみ指定できます"); return }
                }
                symbol = wc.current as? SymbolWord
                if (!wc.eol && symbol == null) { err("引数の書式が間違っています"); return }
                wc.shiftNext()
            }
            if (!wc.eol) {
                val argsRow = when (symbol?.type) {
                    ',' -> ExpressionParser.reduceArguments(wc, ArgsEndWith.EoL, true)
                    '(' -> ExpressionParser.reduceArguments(wc, ArgsEndWith.RightParenthesis, true)
                    else -> { err("引数の書式が間違っています"); return }
                }
                val length = argsRow.size / 2
                val argList = ArrayList<VariableTerm>(length)
                val defList = arrayOfNulls<SingleTerm>(length)
                for (i in 0 until length) {
                    var def: SingleTerm? = null
                    val vTerm = argsRow[i * 2]?.restructure(exm) as? VariableTerm
                    if (vTerm == null || vTerm.identifier.isConst) { err("関数定義の引数には代入可能な変数を指定してください"); return }
                    else if (!vTerm.identifier.isReference) {
                        if (vTerm is VariableNoArgTerm) { err("関数定義の参照型でない引数\"${vTerm.identifier.name}\"に添え字が指定されていません"); return }
                        if (!vTerm.isAllConst) { err("関数定義の引数の添え字には定数を指定してください"); return }
                    }
                    for (j in 0 until i) {
                        if (vTerm.checkSameTerm(argList[j]))
                            ParserMediator.warn("第" + KanaConv.toWide((i + 1).toString()) + "引数\"" + vTerm.getFullString() + "\"はすでに第" + KanaConv.toWide((j + 1).toString()) + "引数として宣言されています", label, 1, false, false)
                    }
                    if (vTerm.identifier.code == VariableCode.ARG) {
                        if (maxArg < vTerm.getEl1forArg + 1) maxArg = vTerm.getEl1forArg + 1
                    } else if (vTerm.identifier.code == VariableCode.ARGS) {
                        if (maxArgs < vTerm.getEl1forArg + 1) maxArgs = vTerm.getEl1forArg + 1
                    }
                    val canDef = vTerm.identifier.code == VariableCode.ARG || vTerm.identifier.code == VariableCode.ARGS || vTerm.identifier.isPrivate
                    val term = argsRow[i * 2 + 1]
                    if (term is NullTerm || term == null) {
                        if (canDef) def = if (vTerm.getOperandType() == EType.Int64) SingleTerm(0L) else SingleTerm("")
                    } else {
                        def = term.restructure(exm) as? SingleTerm
                        if (def == null) { err("引数の初期値には定数のみを指定できます"); return }
                        if (!canDef) { err("引数の初期値を定義できるのは\"ARG\"、\"ARGS\"またはプライベート変数のみです"); return }
                        else if (vTerm.identifier.isReference) { err("参照渡しの引数に初期値は定義できません"); return }
                        if (vTerm.getOperandType() != def.getOperandType()) { err("引数の型と初期値の型が一致していません"); return }
                    }
                    argList.add(vTerm)
                    defList[i] = def
                }
                args = argList.toTypedArray()
                defs = defList
            }
        }
        if (!wc.eol) { err("引数の書式が間違っています"); return }
        label.arg = args
        label.def = defs
        label.argLength = maxArg
        label.argsLength = maxArgs
    }

    var useCallForm = false

    private fun checkScript() {
        var usedLabelCount = 0
        var labelDepth = -1
        val labelList = labelDic.getAllLabels(true)
        while (true) {
            labelDepth++
            var countInDepth = 0
            for (label in labelList) {
                if (label.depth != labelDepth) continue
                usedLabelCount++
                countInDepth++
                checkFunctionWithCatch(label)
            }
            if (countInDepth == 0) break
        }
        labelDepth = -1
        val ignoredFNCWarningFileList = ArrayList<String>()
        var ignoredFNCWarningCount = 0
        val notCalledWarning = Config.FunctionNotCalledWarning
        val ignoreAll = notCalledWarning == DisplayWarningFlag.IGNORE || notCalledWarning == DisplayWarningFlag.LATER
        if (useCallForm) {
            if (Program.AnalysisMode) output.printSystemLine("CALLFORM系命令が使われたため、呼び出されない関数のチェックは行われません。")
            for (label in labelList) {
                if (label.depth != labelDepth) continue
                checkFunctionWithCatch(label)
            }
        } else {
            val ignoreUncalledFunction = Config.IgnoreUncalledFunction
            for (label in labelList) {
                if (label.depth != labelDepth) continue
                if (Program.AnalysisMode) checkFunctionWithCatch(label)
                var ignore = false
                if (notCalledWarning == DisplayWarningFlag.ONCE) {
                    val filename = label.position?.filename?.uppercase() ?: ""
                    if (filename.isNotEmpty()) {
                        if (ignoredFNCWarningFileList.contains(filename)) ignore = true
                        else ignoredFNCWarningFileList.add(filename)
                    }
                }
                if (ignoreAll || ignore) ignoredFNCWarningCount++
                else ParserMediator.warn("関数@${label.labelName}は定義されていますが一度も呼び出されません", label, 1, false, false)
                if (!ignoreUncalledFunction) checkFunctionWithCatch(label)
                else {
                    val nl = label.nextLine
                    if (nl != null && nl !is NullLine && nl !is FunctionLabelLine && !nl.isError) {
                        nl.isError = true
                        nl.errMes = "呼び出されないはずの関数が呼ばれた"
                    }
                }
            }
        }
        if (Program.AnalysisMode && (warningDic.isNotEmpty() || GlobalStatic.tempDic.isNotEmpty())) {
            output.printError("・定義が見つからなかった関数: 他のファイルで定義されている場合はこの警告は無視できます")
            if (warningDic.isNotEmpty()) {
                output.printError("　○一般関数:")
                for ((k, v) in warningDic) output.printError("　　$k: ${v}回")
            }
            if (GlobalStatic.tempDic.isNotEmpty()) {
                output.printError("　○文中関数:")
                for ((k, v) in GlobalStatic.tempDic) output.printError("　　$k: ${v}回")
            }
        } else {
            if (ignoredFNCWarningCount > 0 && Config.DisplayWarningLevel <= 1 && notCalledWarning != DisplayWarningFlag.IGNORE)
                output.printError("警告Lv1:定義された関数が一度も呼び出されていない事に関する警告を${ignoredFNCWarningCount}件無視しました")
            if (ignoredFNFWarningCount > 0 && Config.DisplayWarningLevel <= 2 && notCalledWarning != DisplayWarningFlag.IGNORE)
                output.printError("警告Lv2:定義されていない関数を呼び出した事に関する警告を${ignoredFNFWarningCount}件無視しました")
        }
        ParserMediator.flushWarningList()
        if (Config.DisplayReport) output.printError("非コメント行数:${enabledLineCount}, 全関数合計:${labelDic.count}, 被呼出関数合計:${usedLabelCount}")
        if (Config.AllowFunctionOverloading && Config.WarnFunctionOverloading) {
            val overloadedList = GlobalStatic.IdentifierDictionary!!.getOverloadedList(labelDic)
            if (overloadedList.isNotEmpty()) {
                output.newLine()
                output.printError("＊＊＊＊＊警告＊＊＊＊＊")
                for (funcname in overloadedList) output.printSystemLine("  システム関数\"$funcname\"がユーザー定義関数によって上書きされています")
                output.printSystemLine("  上記の関数を利用するスクリプトは意図通りに動かない可能性があります")
                output.newLine()
                output.printSystemLine("  ※この警告は該当する式中関数を利用しているEmuera専用スクリプト向けの警告です。")
                output.printSystemLine("  eramaker用のスクリプトの動作には影響しません。")
                output.printSystemLine("  今後この警告が不要ならばコンフィグの「システム関数が上書きされたとき警告を表示する」をOFFにして下さい。")
                output.printSystemLine("＊＊＊＊＊＊＊＊＊＊＊＊")
            }
        }
    }

    val warningDic = LinkedHashMap<String, Long>()

    private fun printFunctionNotFoundWarning(str: String, line: LogicalLine, level: Int, isError: Boolean) {
        if (Program.AnalysisMode) {
            warningDic[str] = (warningDic[str] ?: 0L) + 1
            return
        }
        if (isError) { line.isError = true; line.errMes = str }
        if (level < Config.DisplayWarningLevel) return
        var ignore = false
        when (Config.FunctionNotFoundWarning) {
            DisplayWarningFlag.IGNORE -> ignore = true
            DisplayWarningFlag.DISPLAY -> ignore = false
            DisplayWarningFlag.ONCE -> {
                val filename = line.position?.filename?.uppercase() ?: ""
                if (filename.isNotEmpty()) {
                    if (ignoredFNFWarningFileList.contains(filename)) ignore = true
                    else ignoredFNFWarningFileList.add(filename)
                }
            }
            else -> {}
        }
        if (ignore && !Program.AnalysisMode) { ignoredFNFWarningCount++; return }
        ParserMediator.warn(str, line, level, isError, false)
    }

    private fun checkFunctionWithCatch(label: FunctionLabelLine) {
        try {
            setArgument(label)
            nestCheck(label)
            setJumpTo(label)
        } catch (exc: Exception) {
            val errmes = if (exc is EmueraException) exc.message else exc.javaClass.name + ":" + exc.message
            ParserMediator.warn("@${label.labelName} の解析中にエラー:$errmes", label, 2, true, false,
                if (exc !is EmueraException) exc.stackTrace.take(15).joinToString("\n") else null)
            label.errMes = "ロード時に解析に失敗した関数が呼び出されました"
        } finally {
            parentProcess.scaningLine = null
        }
    }

    private fun setArgument(label: FunctionLabelLine) {
        var nextLine: LogicalLine? = label
        val inMethod = label.isMethod
        while (true) {
            nextLine = nextLine!!.nextLine
            parentProcess.scaningLine = nextLine
            if (nextLine !is InstructionLine) {
                if (nextLine == null || nextLine is NullLine || nextLine is FunctionLabelLine) break
                continue
            }
            val func = nextLine
            if (inMethod && !func.function.isMethodSafe()) {
                ParserMediator.warn(func.function.name + "命令は#FUNCTION中で使うことはできません", nextLine, 2, true, false)
                continue
            }
            if (Config.NeedReduceArgumentOnLoad || Program.AnalysisMode || func.function.isForceSetArg()) ArgumentParser.setArgumentTo(func)
        }
    }

    private val dataCodes = setOf(FunctionCode.PRINTDATA, FunctionCode.PRINTDATAL, FunctionCode.PRINTDATAW, FunctionCode.PRINTDATAD,
        FunctionCode.PRINTDATADL, FunctionCode.PRINTDATADW, FunctionCode.PRINTDATAK, FunctionCode.PRINTDATAKL, FunctionCode.PRINTDATAKW,
        FunctionCode.STRDATA, FunctionCode.DATALIST, FunctionCode.TRYCALLLIST, FunctionCode.TRYJUMPLIST, FunctionCode.TRYGOTOLIST)
    private val tryListCodes = setOf(FunctionCode.TRYCALLLIST, FunctionCode.TRYJUMPLIST, FunctionCode.TRYGOTOLIST)
    private val trycCodes = setOf(FunctionCode.TRYCGOTO, FunctionCode.TRYCCALL, FunctionCode.TRYCJUMP, FunctionCode.TRYCGOTOFORM, FunctionCode.TRYCCALLFORM, FunctionCode.TRYCJUMPFORM)

    private fun nestCheck(label: FunctionLabelLine) {
        var nextLine: LogicalLine? = label
        var tempLineList = ArrayList<InstructionLine>()
        val nestStack = ArrayDeque<InstructionLine>()
        val selectcaseStack = ArrayDeque<InstructionLine>()
        var pairLine: InstructionLine? = null
        fun w(mes: String, l: LogicalLine, level: Int, isError: Boolean) = ParserMediator.warn(mes, l, level, isError, false)
        while (true) {
            nextLine = nextLine!!.nextLine
            parentProcess.scaningLine = nextLine
            if (nextLine == null || nextLine is NullLine || nextLine is FunctionLabelLine) break
            if (nextLine !is InstructionLine) {
                if (nextLine is GotoLabelLine) {
                    val cur = nestStack.peek()
                    if (cur != null && cur.functionCode in dataCodes) w(cur.function.name + "構文中に\$ラベルを定義することはできません", nextLine, 2, true)
                }
                continue
            }
            val func = nextLine
            val baseFunc = nestStack.peek()
            if (baseFunc != null) {
                if (baseFunc.function.isPrintData() || baseFunc.functionCode == FunctionCode.STRDATA) {
                    if (func.functionCode != FunctionCode.DATA && func.functionCode != FunctionCode.DATAFORM && func.functionCode != FunctionCode.DATALIST
                        && func.functionCode != FunctionCode.ENDLIST && func.functionCode != FunctionCode.ENDDATA) {
                        w(baseFunc.function.name + "構文に使用できない命令'" + func.function.name + "'が含まれています", func, 2, true); continue
                    }
                } else if (baseFunc.functionCode == FunctionCode.DATALIST) {
                    if (func.functionCode != FunctionCode.DATA && func.functionCode != FunctionCode.DATAFORM && func.functionCode != FunctionCode.ENDLIST) {
                        w("DATALIST構文に使用できない命令'" + func.function.name + "'が含まれています", func, 2, true); continue
                    }
                } else if (baseFunc.functionCode in tryListCodes) {
                    if (func.functionCode != FunctionCode.FUNC && func.functionCode != FunctionCode.ENDFUNC) {
                        w(baseFunc.function.name + "構文に使用できない命令'" + func.function.name + "'が含まれています", func, 2, true); continue
                    }
                } else if (baseFunc.functionCode == FunctionCode.SELECTCASE) {
                    if (baseFunc.ifCaseList!!.isEmpty() && func.functionCode != FunctionCode.CASE && func.functionCode != FunctionCode.CASEELSE && func.functionCode != FunctionCode.ENDSELECT) {
                        w("SELECTCASE構文の分岐の外に命令'" + func.function.name + "'が含まれています", func, 2, true); continue
                    }
                }
            }
            when (func.functionCode) {
                FunctionCode.REPEAT -> {
                    for (iLine in nestStack) {
                        if (iLine.functionCode == FunctionCode.REPEAT) w("REPEAT文が入れ子にされています（無限ループの恐れがあります）", func, 1, false)
                        else if (iLine.functionCode == FunctionCode.FOR) {
                            val cnt = (iLine.argument as? SpForNextArgment)?.cnt
                            if (cnt != null && cnt.identifier.name == "COUNT" && cnt.isAllConst && cnt.getEl1forArg == 0)
                                w("カウンタ変数にCOUNT:0を用いたFOR文の中でREPEATが呼び出されています", func, 1, false)
                        }
                    }
                    if (!func.isError) nestStack.push(func)
                }
                FunctionCode.IF -> {
                    nestStack.push(func)
                    func.ifCaseList = arrayListOf(func)
                }
                FunctionCode.SELECTCASE -> {
                    nestStack.push(func)
                    func.ifCaseList = ArrayList()
                    selectcaseStack.push(func)
                }
                FunctionCode.FOR -> {
                    if (func.argument == null) ArgumentParser.setArgumentTo(func)
                    val cnt = (func.argument as? SpForNextArgment)?.cnt
                    if (cnt != null && cnt.identifier.name == "COUNT") {
                        for (iLine in nestStack) {
                            if (iLine.functionCode == FunctionCode.REPEAT && cnt.isAllConst && cnt.getEl1forArg == 0)
                                w("REPEAT文の中でカウンタ変数にCOUNT:0を用いたFORが使われています（無限ループの恐れがあります）", func, 1, false)
                            else if (iLine.functionCode == FunctionCode.FOR) {
                                val destCnt = (iLine.argument as? SpForNextArgment)?.cnt
                                if (destCnt != null && destCnt.identifier.name == "COUNT" && cnt.isAllConst && destCnt.isAllConst && destCnt.getEl1forArg == cnt.getEl1forArg)
                                    w("カウンタ変数にCOUNT:${cnt.getEl1forArg}を用いたFOR文が入れ子にされています（無限ループの恐れがあります）", func, 1, false)
                            }
                        }
                    }
                    if (!func.isError) nestStack.push(func)
                }
                FunctionCode.WHILE, FunctionCode.TRYCGOTO, FunctionCode.TRYCJUMP, FunctionCode.TRYCCALL, FunctionCode.TRYCGOTOFORM,
                FunctionCode.TRYCJUMPFORM, FunctionCode.TRYCCALLFORM, FunctionCode.DO -> nestStack.push(func)
                FunctionCode.BREAK, FunctionCode.CONTINUE -> {
                    for (l in nestStack) {
                        if (l.functionCode == FunctionCode.REPEAT || l.functionCode == FunctionCode.FOR || l.functionCode == FunctionCode.WHILE || l.functionCode == FunctionCode.DO) {
                            pairLine = l; break
                        }
                    }
                    if (pairLine == null) w("REPEAT, FOR, WHILE, DOの中以外で" + func.function.name + "文が使われました", func, 2, true)
                    else func.jumpTo = pairLine
                }
                FunctionCode.ELSEIF, FunctionCode.ELSE -> {
                    val ifLine = nestStack.peek()
                    if (ifLine == null || ifLine.functionCode != FunctionCode.IF) w("IF～ENDIFの外で" + func.function.name + "文が使われました", func, 2, true)
                    else {
                        val list = ifLine.ifCaseList!!
                        if (list[list.size - 1].functionCode == FunctionCode.ELSE) w("ELSE文より後で" + func.function.name + "文が使われました", func, 1, false)
                        list.add(func)
                    }
                }
                FunctionCode.ENDIF -> {
                    val ifLine = nestStack.peek()
                    if (ifLine == null || ifLine.functionCode != FunctionCode.IF) w("対応するIFの無いENDIF文です", func, 2, true)
                    else {
                        for (l in ifLine.ifCaseList!!) l.jumpTo = func
                        nestStack.pop()
                    }
                }
                FunctionCode.CASE, FunctionCode.CASEELSE -> run {
                    var selectLine = nestStack.peek()
                    if (selectLine == null || (selectLine.functionCode != FunctionCode.SELECTCASE && selectcaseStack.isEmpty())) {
                        w("SELECTCASE～ENDSELECTの外で" + func.function.name + "文が使われました", func, 2, true); return@run
                    } else if (selectLine.functionCode != FunctionCode.SELECTCASE && selectcaseStack.isNotEmpty()) {
                        do {
                            w(selectLine!!.function.name + "文に対応する" + FunctionIdentifier.getMatchFunction(selectLine.functionCode) + "がない状態で" + func.function.name + "文に到達しました", func, 2, true)
                            nestStack.pop()
                            selectLine = nestStack.peek()
                        } while (selectLine != null && selectLine.functionCode != FunctionCode.SELECTCASE)
                        return@run
                    }
                    val list = selectLine.ifCaseList!!
                    if (list.isNotEmpty() && list[list.size - 1].functionCode == FunctionCode.CASEELSE) w("CASEELSE文より後で" + func.function.name + "文が使われました", func, 1, false)
                    list.add(func)
                }
                FunctionCode.ENDSELECT -> run {
                    var selectLine = nestStack.peek()
                    if (selectLine == null || (selectLine.functionCode != FunctionCode.SELECTCASE && selectcaseStack.isEmpty())) {
                        w("対応するSELECTCASEの無いENDSELECT文です", func, 2, true); return@run
                    } else if (selectLine.functionCode != FunctionCode.SELECTCASE && selectcaseStack.isNotEmpty()) {
                        do {
                            w(selectLine!!.function.name + "文に対応する" + FunctionIdentifier.getMatchFunction(selectLine.functionCode) + "がない状態で" + func.function.name + "文に到達しました", func, 2, true)
                            nestStack.pop()
                            selectLine = nestStack.peek()
                        } while (selectLine != null && selectLine.functionCode != FunctionCode.SELECTCASE)
                        selectcaseStack.pop()
                        nestStack.pop()
                        return@run
                    }
                    nestStack.pop()
                    selectcaseStack.pop()
                    selectLine.jumpTo = func
                    if (selectLine.isError) return@run
                    val term = (selectLine.argument as? ExpressionArgument)?.term
                    if (term == null) { w("SELECTCASEの引数がありません", selectLine, 2, true); return@run }
                    for (caseLine in selectLine.ifCaseList!!) {
                        caseLine.jumpTo = func
                        if (caseLine.isError) continue
                        if (caseLine.functionCode == FunctionCode.CASEELSE) continue
                        val caseExps = (caseLine.argument as CaseArgument).caseExps
                        if (caseExps.isEmpty()) w("CASEの引数がありません", caseLine, 2, true)
                        for (exp in caseExps) if (exp.getOperandType() != term.getOperandType()) w("CASEの引数の型がSELECTCASEと一致しません", caseLine, 2, true)
                    }
                }
                FunctionCode.REND, FunctionCode.NEXT, FunctionCode.WEND, FunctionCode.LOOP -> {
                    val parentFunc = FunctionIdentifier.getParentFunc(func.functionCode)
                    if (nestStack.isEmpty() || nestStack.peek().functionCode != parentFunc) {
                        w("対応する" + parentFunc.name + "の無い" + func.function.name + "文です", func, 2, true)
                    } else {
                        val p = nestStack.pop()
                        pairLine = p
                        func.jumpTo = p
                        p.jumpTo = func
                    }
                }
                FunctionCode.CATCH -> {
                    val p = nestStack.peek()
                    if (p == null || p.functionCode !in trycCodes) w("対応するTRYC系命令がありません", func, 2, true)
                    else {
                        nestStack.pop()
                        pairLine = p
                        p.jumpToEndCatch = func
                        nestStack.push(func)
                    }
                }
                FunctionCode.ENDCATCH -> {
                    if (nestStack.isEmpty() || nestStack.peek().functionCode != FunctionCode.CATCH) w("対応するCATCHのないENDCATCHです", func, 2, true)
                    else {
                        val p = nestStack.pop()
                        pairLine = p
                        p.jumpToEndCatch = func
                    }
                }
                FunctionCode.PRINTDATA, FunctionCode.PRINTDATAL, FunctionCode.PRINTDATAW, FunctionCode.PRINTDATAD, FunctionCode.PRINTDATADL,
                FunctionCode.PRINTDATADW, FunctionCode.PRINTDATAK, FunctionCode.PRINTDATAKL, FunctionCode.PRINTDATAKW -> {
                    for (iLine in nestStack) {
                        if (iLine.function.isPrintData()) { w("PRINTDATA系命令が入れ子にされています", func, 2, true); break }
                        if (iLine.functionCode == FunctionCode.STRDATA) { w("PRINTDATA系命令の中にSTRDATA系命令が含まれています", func, 2, true); break }
                    }
                    if (!func.isError) {
                        func.dataList = ArrayList()
                        nestStack.push(func)
                    }
                }
                FunctionCode.STRDATA -> {
                    for (iLine in nestStack) {
                        if (iLine.functionCode == FunctionCode.STRDATA) { w("STRDATA命令が入れ子にされています", func, 2, true); break }
                        if (iLine.function.isPrintData()) { w("STRDATA系命令の中にPRINTDATA系命令が含まれています", func, 2, true); break }
                    }
                    if (!func.isError) {
                        func.dataList = ArrayList()
                        nestStack.push(func)
                    }
                }
                FunctionCode.DATALIST -> {
                    val pline = nestStack.peek()
                    if (pline == null || (!pline.function.isPrintData() && pline.functionCode != FunctionCode.STRDATA)) w("対応するPRINTDATA系命令のないDATALISTです", func, 2, true)
                    else {
                        tempLineList = ArrayList()
                        nestStack.push(func)
                    }
                }
                FunctionCode.ENDLIST -> {
                    if (nestStack.isEmpty() || nestStack.peek().functionCode != FunctionCode.DATALIST) w("対応するDATALISTのないENDLISTです", func, 2, true)
                    else {
                        if (tempLineList.isEmpty()) w("DATALIST命令に表示データが与えられていません（このDATALISTは空文字列を表示します）", func, 1, false)
                        nestStack.pop()
                        nestStack.peek().dataList!!.add(tempLineList)
                    }
                }
                FunctionCode.DATA, FunctionCode.DATAFORM -> {
                    val pdata = nestStack.peek()
                    if (pdata == null || (!pdata.function.isPrintData() && pdata.functionCode != FunctionCode.DATALIST && pdata.functionCode != FunctionCode.STRDATA))
                        w("対応するPRINTDATA系命令のない" + func.function.name + "です", func, 2, true)
                    else if (pdata.functionCode != FunctionCode.DATALIST) pdata.dataList!!.add(arrayListOf(func))
                    else tempLineList.add(func)
                }
                FunctionCode.ENDDATA -> {
                    val pline = nestStack.peek()
                    if (pline == null || (!pline.function.isPrintData() && pline.functionCode != FunctionCode.STRDATA))
                        w("対応するPRINTDATA系命令もしくはSTRDATAのない" + func.function.name + "です", func, 2, true)
                    else {
                        if (pline.functionCode == FunctionCode.DATALIST) w("DATALISTが閉じられていません", func, 2, true)
                        if (pline.dataList!!.isEmpty()) w(pline.function.name + "命令に表示データがありません（この命令は無視されます）", func, 1, false)
                        pline.jumpTo = func
                        nestStack.pop()
                    }
                }
                FunctionCode.TRYCALLLIST, FunctionCode.TRYJUMPLIST, FunctionCode.TRYGOTOLIST -> {
                    for (iLine in nestStack) {
                        if (iLine.functionCode in tryListCodes) { w("TRYCALLLIST系命令が入れ子にされています", func, 2, true); break }
                    }
                    if (!func.isError) {
                        func.callList = ArrayList()
                        nestStack.push(func)
                    }
                }
                FunctionCode.FUNC -> run {
                    val pFunc = nestStack.peek()
                    if (pFunc == null || pFunc.functionCode !in tryListCodes) { w("対応するTRYCALLLIST系命令のない" + func.function.name + "です", func, 2, true); return@run }
                    if (func.argument == null) { w("TRYCALLLIST系命令中に無効な" + func.function.name + "が存在します", pFunc, 2, true); return@run }
                    if (pFunc.functionCode == FunctionCode.TRYGOTOLIST) {
                        val a = func.argument as SpCallArgment
                        if (a.subNames.isNotEmpty()) { w("TRYGOTOLISTの呼び出し対象に[～～]が設定されています", func, 2, true); return@run }
                        if (a.rowArgs.isNotEmpty()) { w("TRYGOTOLISTの呼び出し対象に引数が設定されています", func, 2, true); return@run }
                    }
                    pFunc.callList!!.add(func)
                }
                FunctionCode.ENDFUNC -> {
                    val pf = nestStack.peek()
                    if (pf == null || pf.functionCode !in tryListCodes) w("対応するTRYCALLLIST系命令のない" + func.function.name + "です", func, 2, true)
                    else {
                        pf.jumpTo = func
                        nestStack.pop()
                    }
                }
                FunctionCode.NOSKIP -> {
                    for (iLine in nestStack) if (iLine.functionCode == FunctionCode.NOSKIP) { w("NOSKIP系命令が入れ子にされています", func, 2, true); break }
                    if (!func.isError) nestStack.push(func)
                }
                FunctionCode.ENDNOSKIP -> {
                    val pfunc = nestStack.peek()
                    if (pfunc == null || pfunc.functionCode != FunctionCode.NOSKIP) w("対応するNOSKIP系命令のない" + func.function.name + "です", func, 2, true)
                    else {
                        pfunc.jumpTo = func
                        func.jumpTo = pfunc
                        nestStack.pop()
                    }
                }
                else -> {}
            }
        }
        while (nestStack.isNotEmpty()) {
            val func = nestStack.pop()
            w(func.function.name + "に対応する" + FunctionIdentifier.getMatchFunction(func.functionCode) + "が見つかりません", func, 2, true)
        }
        selectcaseStack.clear()
    }

    private fun setJumpTo(label: FunctionLabelLine) {
        var nextLine: LogicalLine? = label
        var depth = label.depth
        if (depth < 0) depth = -2
        val useCF = BooleanArray(1)
        while (true) {
            nextLine = nextLine!!.nextLine
            if (nextLine !is InstructionLine) {
                if (nextLine == null || nextLine is NullLine || nextLine is FunctionLabelLine) break
                continue
            }
            val func = nextLine
            if (func.isError) continue
            parentProcess.scaningLine = func
            val inst = func.function.instruction
            if (inst != null) {
                val notFound = arrayOfNulls<String>(1)
                useCF[0] = useCallForm
                try {
                    inst.setJumpTo(useCF, func, depth, notFound)
                } catch (e: CodeEE) {
                    ParserMediator.warn(e.message ?: "", func, 2, true, false)
                    continue
                } finally {
                    useCallForm = useCF[0]
                }
                val nf = notFound[0]
                if (nf != null) {
                    if (!Program.AnalysisMode) printFunctionNotFoundWarning("指定された関数名\"@$nf\"は存在しません", func, 2, true)
                    else printFunctionNotFoundWarning(nf, func, 2, true)
                }
                continue
            }
            if (func.functionCode == FunctionCode.TRYCALLLIST || func.functionCode == FunctionCode.TRYJUMPLIST) useCallForm = true
        }
    }
}
