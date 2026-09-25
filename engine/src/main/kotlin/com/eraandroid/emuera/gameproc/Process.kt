package com.eraandroid.emuera.gameproc

import com.eraandroid.emuera.GlobalStatic
import com.eraandroid.emuera.IdentifierDictionary
import com.eraandroid.emuera.Program
import com.eraandroid.emuera.config.Config
import com.eraandroid.emuera.config.ConfigCode
import com.eraandroid.emuera.config.ConfigData
import com.eraandroid.emuera.config.EraColor
import com.eraandroid.emuera.content.AppContents
import com.eraandroid.emuera.gamedata.ConstantData
import com.eraandroid.emuera.gamedata.GameBase
import com.eraandroid.emuera.gamedata.ParserMediator
import com.eraandroid.emuera.gamedata.StrForm
import com.eraandroid.emuera.gamedata.expression.*
import com.eraandroid.emuera.gamedata.function.SuperUserDefinedMethodTerm
import com.eraandroid.emuera.gamedata.variable.*
import com.eraandroid.emuera.gameproc.function.*
import com.eraandroid.emuera.gameview.DisplayLineAlignment
import com.eraandroid.emuera.gameview.EmueraConsole
import com.eraandroid.emuera.gameview.FontStyle
import com.eraandroid.emuera.sub.*
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class Process(private val console: EmueraConsole) {
    val getCurrentLine: LogicalLine? get() = state.currentLine
    lateinit var labelDictionary: LabelDictionary
        private set
    lateinit var vEvaluator: VariableEvaluator
        private set
    private lateinit var exm: ExpressionMediator
    private lateinit var gamebase: GameBase
    private lateinit var idDic: IdentifierDictionary
    private lateinit var state: ProcessState
    private lateinit var originalState: ProcessState
    private var noError = false
    private var initialiing = false
    val inInitializeing: Boolean get() = initialiing

    fun initialize(): Boolean {
        LexicalAnalyzer.useMacro = false
        state = ProcessState(console)
        originalState = state
        initialiing = true
        try {
            ParserMediator.initialize(console)
            if (ParserMediator.hasWarning) ParserMediator.flushWarningList()
            if (!AppContents.loadContents()) {
                ParserMediator.flushWarningList()
                console.printSystemLine("リソースフォルダ読み込み中に異常が発見されたため処理を終了します")
                return false
            }
            ParserMediator.flushWarningList()
            if (Config.UseReplaceFile && !Program.AnalysisMode) {
                val rep = FileUtil.resolve(Program.CsvDir + "_Replace.csv")
                if (rep.isFile) {
                    if (Config.DisplayReport) console.printSystemLine("_Replace.csv読み込み中・・・")
                    ConfigData.instance.loadReplaceFile(rep.path)
                    if (ParserMediator.hasWarning) ParserMediator.flushWarningList()
                }
            }
            Config.setReplace(ConfigData.instance)
            console.setStBar(Config.DrawLineString)
            if (Config.UseRenameFile) {
                val ren = FileUtil.resolve(Program.CsvDir + "_Rename.csv")
                if (ren.isFile) {
                    if (Config.DisplayReport || Program.AnalysisMode) console.printSystemLine("_Rename.csv読み込み中・・・")
                    ParserMediator.loadEraExRenameFile(ren.path)
                } else console.printError("csv\\_Rename.csvが見つかりません")
            }
            if (!Config.DisplayReport) {
                console.printSingleLine(Config.LoadLabel)
                console.refreshStrings(true)
            }
            gamebase = GameBase()
            if (!gamebase.loadGameBaseCsv(Program.CsvDir + "GAMEBASE.CSV")) {
                ParserMediator.flushWarningList()
                console.printSystemLine("GAMEBASE.CSVの読み込み中に問題が発生したため処理を終了しました")
                return false
            }
            console.setWindowTitle(gamebase.ScriptWindowTitle)
            GlobalStatic.GameBaseData = gamebase
            val constant = ConstantData()
            constant.loadData(Program.CsvDir, console, Config.DisplayReport)
            GlobalStatic.ConstantData = constant
            trainName = constant.getCsvNameList(VariableCode.TRAINNAME)
            vEvaluator = VariableEvaluator(gamebase, constant)
            GlobalStatic.VEvaluator = vEvaluator
            idDic = IdentifierDictionary(vEvaluator.variableData)
            GlobalStatic.IdentifierDictionary = idDic
            StrForm.initialize()
            VariableParser.initialize()
            exm = ExpressionMediator(this, vEvaluator, console)
            GlobalStatic.EMediator = exm
            labelDictionary = LabelDictionary()
            GlobalStatic.LabelDictionary = labelDictionary
            val hLoader = HeaderFileLoader(console, idDic, this)
            LexicalAnalyzer.useMacro = false
            if (!hLoader.loadHeaderFiles(Program.ErbDir, Config.DisplayReport)) {
                ParserMediator.flushWarningList()
                console.printSystemLine("ERHの読み込み中にエラーが発生したため処理を終了しました")
                return false
            }
            LexicalAnalyzer.useMacro = idDic.useMacro()
            val loader = ErbLoader(console, exm, this)
            noError = if (Program.AnalysisMode) loader.loadErbs(Program.AnalysisFiles!!, labelDictionary)
            else loader.loadErbFiles(Program.ErbDir, Config.DisplayReport, labelDictionary)
            initSystemProcess()
            initialiing = false
        } catch (e: Throwable) {
            handleException(e, null, true)
            console.printSystemLine("初期化中に致命的なエラーが発生したため処理を終了しました")
            return false
        }
        state.begin(BeginType.TITLE)
        return true
    }

    fun reloadErb() {
        saveCurrentState(false)
        state.systemState = SystemStateCode.System_Reloaderb
        ErbLoader(console, exm, this).loadErbFiles(Program.ErbDir, false, labelDictionary)
        console.readAnyKey()
    }

    fun reloadPartialErb(path: List<String>) {
        saveCurrentState(false)
        state.systemState = SystemStateCode.System_Reloaderb
        ErbLoader(console, exm, this).loadErbs(path, labelDictionary)
        console.readAnyKey()
    }

    fun setCommnds(count: Long) {
        coms = ArrayList(count.toInt())
        isCTrain = true
        val selectcom = vEvaluator.SELECTCOM_ARRAY
        if (count >= selectcom.size) throw CodeEE("CALLTRAIN命令の引数の値がSELECTCOMの要素数を超えています")
        for (i in 0 until count.toInt()) coms.add(selectcom[i + 1])
    }

    fun clearCommands(): Boolean {
        coms.clear()
        this.count = 0
        isCTrain = false
        skipPrint = true
        return callFunction("CALLTRAINEND", false, false)
    }

    fun inputResult5(r0: Int, r1: Int, r2: Int, r3: Int, r4: Int) {
        val result = vEvaluator.RESULT_ARRAY
        result[0] = r0.toLong(); result[1] = r1.toLong(); result[2] = r2.toLong(); result[3] = r3.toLong(); result[4] = r4.toLong()
    }

    fun inputInteger(i: Long) { vEvaluator.RESULT = i }
    fun inputSystemInteger(i: Long) { systemResult = i }
    fun inputString(s: String) { vEvaluator.RESULTS = s }

    private var startTime = 0L

    fun doScript() {
        startTime = System.currentTimeMillis()
        state.lineCount = 0
        var systemProcRunning = true
        try {
            while (true) {
                methodStack = 0
                systemProcRunning = true
                while (state.scriptEnd && console.isRunning) runSystemProc()
                if (!console.isRunning) break
                systemProcRunning = false
                runScriptProc()
            }
        } catch (ec: Throwable) {
            var currentLine = state.errorLine
            if (currentLine is NullLine) currentLine = null
            if (systemProcRunning) handleExceptionInSystemProc(ec, currentLine, true)
            else handleException(ec, currentLine, true)
        }
    }

    fun beginTitle() {
        vEvaluator.resetData()
        state = originalState
        state.begin(BeginType.TITLE)
    }

    fun updateCheckInfiniteLoopState() {
        startTime = System.currentTimeMillis()
        state.lineCount = 0
    }

    private fun checkInfiniteLoop() {
        val time = System.currentTimeMillis() - startTime
        if (time < Config.InfiniteLoopAlertTime) return
        val currentLine = state.currentLine
        if (currentLine == null || currentLine is NullLine) return
        val text = "現在、${currentLine.position?.filename}の${currentLine.position?.lineNo}行目を実行中です。\n最後の入力から${time}ミリ秒経過し${state.lineCount}行が実行されました。\n処理を中断し強制終了しますか？"
        if (console.confirmInfiniteLoop(text)) throw CodeEE("無限ループの疑いにより強制終了が選択されました")
        state.lineCount = 0
        startTime = System.currentTimeMillis()
    }

    private var methodStack = 0

    fun getValue(udmt: SuperUserDefinedMethodTerm): SingleTerm? {
        methodStack++
        if (methodStack > 100) throw CodeEE("関数の呼び出しスタックが溢れました(無限に再帰呼び出しされていませんか？)")
        val tempCurrent = state.currentMin
        state.currentMin = state.functionCount
        val call = udmt.call
        call.updateRetAddress(state.currentLine)
        try {
            state.intoFunction(call, udmt.argument, exm)
            runScriptProc()
            return state.methodReturnValue
        } finally {
            if (call.topLabel.hasPrivDynamicVar) call.topLabel.exit()
            state.currentMin = tempCurrent
            methodStack--
        }
    }

    fun clearMethodStack() { methodStack = 0 }
    fun methodStack(): Int = methodStack

    fun getRunningPosition(): ScriptPosition? = state.errorLine?.position

    var scaningLine: LogicalLine? = null
    fun getScaningLine(): LogicalLine? = scaningLine ?: state.errorLine

    private fun handleExceptionInSystemProc(exc: Throwable, current: LogicalLine?, playSound: Boolean) {
        console.throwError(playSound)
        when (exc) {
            is CodeEE -> { console.printError("関数の終端でエラーが発生しました:" + Program.ExeName); console.printError(exc.message ?: "") }
            is ExeEE -> { console.printError("関数の終端でEmueraのエラーが発生しました:" + Program.ExeName); console.printError(exc.message ?: "") }
            else -> {
                console.printError("関数の終端で予期しないエラーが発生しました:" + Program.ExeName)
                console.printError(exc.javaClass.name + ":" + exc.message)
                for (s in exc.stackTrace.take(30)) console.printError(s.toString())
            }
        }
    }

    private fun handleException(exc: Throwable, current: LogicalLine?, playSound: Boolean) {
        console.throwError(playSound)
        var position: ScriptPosition? = null
        if (exc is EmueraException && exc.position != null) position = exc.position
        else if (current?.position != null) position = current.position
        var posString = ""
        if (position != null) posString = if (position.lineNo >= 0) position.filename + "の" + position.lineNo + "行目で" else position.filename + "で"
        when (exc) {
            is CodeEE -> {
                if (position != null) {
                    if (current is InstructionLine && current.functionCode == FunctionCode.THROW) {
                        console.printErrorButton(posString + "THROWが発生しました", position)
                        printRawLine(position)
                        console.printError("THROW内容：" + exc.message)
                    } else {
                        console.printErrorButton(posString + "エラーが発生しました:" + Program.ExeName, position)
                        printRawLine(position)
                        console.printError("エラー内容：" + exc.message)
                    }
                    val pl = current?.parentLabelLine
                    if (pl != null) console.printError("現在の関数：@" + pl.labelName + "（" + pl.position?.filename + "の" + pl.position?.lineNo + "行目）")
                    console.printError("関数呼び出しスタック：")
                    var depth = 0
                    while (true) {
                        val parent = try { state.getReturnAddressSequensial(depth++) } catch (e: IndexOutOfBoundsException) { null } ?: break
                        val pp = parent.position
                        if (pp != null) console.printErrorButton("↑" + pp.filename + "の" + pp.lineNo + "行目（関数@" + parent.parentLabelLine?.labelName + "内）", pp)
                    }
                } else {
                    console.printError(posString + "エラーが発生しました:" + Program.ExeName)
                    console.printError(exc.message ?: "")
                }
            }
            is ExeEE -> {
                console.printError(posString + "Emueraのエラーが発生しました:" + Program.ExeName)
                console.printError(exc.message ?: "")
            }
            else -> {
                console.printError(posString + "予期しないエラーが発生しました:" + Program.ExeName)
                console.printError(exc.javaClass.name + ":" + exc.message)
                for (s in exc.stackTrace.take(30)) console.printError(s.toString())
            }
        }
    }

    fun printRawLine(position: ScriptPosition) {
        val str = getRawTextFormFilewithLine(position)
        if (str != "") console.printError(str)
    }

    fun getRawTextFormFilewithLine(position: ScriptPosition): String {
        val fn = position.filename
        if (fn.length < 4) return ""
        val dir = when (fn.substring(fn.length - 4).lowercase()) {
            ".erb", ".erh" -> Program.ErbDir
            ".csv" -> Program.CsvDir
            else -> return ""
        }
        val f = FileUtil.resolve(dir + fn)
        if (!f.isFile || position.lineNo <= 0) return ""
        return try { TextFileReader.readAllLines(f).getOrNull(position.lineNo - 1) ?: "" } catch (e: Exception) { "" }
    }

    // ===================== Process.SystemProc =====================
    private var trainName: Array<String?> = arrayOf()
    private val systemProcessDictionary = HashMap<Int, () -> Unit>()

    private fun initSystemProcess() {
        comAble = IntArray(trainName.size)
        val d = systemProcessDictionary
        d[SystemStateCode.Title_Begin] = ::beginTitleProc
        d[SystemStateCode.Openning] = ::endOpenning
        d[SystemStateCode.Train_Begin] = ::beginTrain
        d[SystemStateCode.Train_CallEventTrain] = ::endCallEventTrain
        d[SystemStateCode.Train_CallShowStatus] = ::endCallShowStatus
        d[SystemStateCode.Train_CallComAbleXX] = ::endCallComAbleXX
        d[SystemStateCode.Train_CallShowUserCom] = ::endCallShowUserCom
        d[SystemStateCode.Train_WaitInput] = ::trainWaitInput
        d[SystemStateCode.Train_CallEventCom] = ::endEventCom
        d[SystemStateCode.Train_CallComXX] = ::endCallComXX
        d[SystemStateCode.Train_CallSourceCheck] = ::endCallSourceCheck
        d[SystemStateCode.Train_CallEventComEnd] = ::endCallEventComEnd
        d[SystemStateCode.Train_DoTrain] = ::doTrain
        d[SystemStateCode.AfterTrain_Begin] = ::beginAfterTrain
        d[SystemStateCode.Ablup_Begin] = ::beginAblup
        d[SystemStateCode.Ablup_CallShowJuel] = ::endCallShowJuel
        d[SystemStateCode.Ablup_CallShowAblupSelect] = ::endCallShowAblupSelect
        d[SystemStateCode.Ablup_WaitInput] = ::ablupWaitInput
        d[SystemStateCode.Ablup_CallAblupXX] = ::endCallAblupXX
        d[SystemStateCode.Turnend_Begin] = ::beginTurnend
        d[SystemStateCode.Shop_Begin] = ::beginShop
        d[SystemStateCode.Shop_CallEventShop] = ::endCallEventShop
        d[SystemStateCode.Shop_CallShowShop] = ::endCallShowShop
        d[SystemStateCode.Shop_WaitInput] = ::shopWaitInput
        d[SystemStateCode.Shop_CallEventBuy] = ::endCallEventBuy
        d[SystemStateCode.SaveGame_Begin] = ::beginSaveGame
        d[SystemStateCode.SaveGame_WaitInput] = ::saveGameWaitInput
        d[SystemStateCode.SaveGame_WaitInputOverwrite] = ::saveGameWaitInputOverwrite
        d[SystemStateCode.SaveGame_CallSaveInfo] = ::endCallSaveInfo
        d[SystemStateCode.LoadGame_Begin] = ::beginLoadGame
        d[SystemStateCode.LoadGame_WaitInput] = ::loadGameWaitInput
        d[SystemStateCode.LoadGameOpenning_Begin] = ::beginLoadGameOpening
        d[SystemStateCode.LoadGameOpenning_WaitInput] = ::loadGameWaitInput
        d[SystemStateCode.AutoSave_CallSaveInfo] = ::endAutoSaveCallSaveInfo
        d[SystemStateCode.AutoSave_CallUniqueAutosave] = ::endAutoSave
        d[SystemStateCode.LoadData_DataLoaded] = ::beginDataLoaded
        d[SystemStateCode.LoadData_CallSystemLoad] = ::endSystemLoad
        d[SystemStateCode.LoadData_CallEventLoad] = ::endEventLoad
        d[SystemStateCode.Openning_TitleLoadgame] = ::endTitleLoadgame
        d[SystemStateCode.System_Reloaderb] = ::endReloaderb
        d[SystemStateCode.First_Begin] = ::beginFirst
        d[SystemStateCode.Normal] = ::endNormal
    }

    private var systemResult: Long = 0
    private var lastCalledComable = -1
    private var lastAddCom = -1
    private var comAble = IntArray(0)

    private fun runSystemProc() {
        val p = systemProcessDictionary[state.systemState] ?: throw ExeEE("未定義のシステム状態: " + state.systemState)
        p()
    }

    private fun setWait() = console.readAnyKey()

    private fun setWaitInput() {
        val req = InputRequest()
        req.inputType = InputType.IntValue
        req.isSystemInput = true
        console.waitInput(req)
    }

    private fun callFunction(functionName: String, force: Boolean, isEvent: Boolean): Boolean {
        val call = if (isEvent) CalledFunction.callEventFunction(this, functionName, null) else CalledFunction.callFunction(this, functionName, null)
        if (call == null) {
            if (!force) return false
            throw CodeEE("関数\"@$functionName\"が見つかりません")
        }
        state.intoFunction(call, null, null)
        return true
    }

    private fun beginTitleProc() {
        if (isCTrain) if (clearCommands()) return
        skipPrint = false
        console.resetStyle()
        deleteAllPrevState()
        if (Program.AnalysisMode) {
            console.printSystemLine("ファイル解析終了：Analysis.logに出力します")
            console.outputLog(Program.ExeDir + "Analysis.log")
            console.noOutputLog = true
            console.printSystemLine("エンターキーもしくはクリックで終了します")
            console.throwTitleError(false)
            return
        }
        if (!noError && !Config.CompatiErrorLine) {
            console.printSystemLine("ERBコードに解釈不可能な行があるためEmueraを終了します")
            console.printSystemLine("※互換性オプション「" + Config.getConfigName(ConfigCode.CompatiErrorLine) + "」により強制的に動作させることができます")
            console.printSystemLine("emuera.logにログを出力します")
            console.outputLog(Program.ExeDir + "emuera.log")
            console.noOutputLog = true
            console.printSystemLine("エンターキーもしくはクリックで終了します")
            console.throwTitleError(true)
            return
        }
        if (callFunction("SYSTEM_TITLE", false, false)) {
            state.systemState = SystemStateCode.Normal
            return
        }
        console.printBar()
        console.newLine()
        console.alignment = DisplayLineAlignment.CENTER
        console.printSingleLine(gamebase.ScriptTitle)
        if (gamebase.ScriptVersion != 0L) console.printSingleLine(gamebase.ScriptVersionText)
        console.printSingleLine(gamebase.ScriptAutherName)
        console.printSingleLine("(" + gamebase.ScriptYear + ")")
        console.newLine()
        console.printSingleLine(gamebase.ScriptDetail)
        console.alignment = DisplayLineAlignment.LEFT
        console.printBar()
        console.newLine()
        console.printSingleLine("[0] " + Config.TitleMenuString0)
        console.printSingleLine("[1] " + Config.TitleMenuString1)
        openingInput()
    }

    private fun openingInput() {
        setWaitInput()
        state.systemState = SystemStateCode.Openning
    }

    private fun endOpenning() {
        if (systemResult == 0L) {
            vEvaluator.resetData()
            vEvaluator.addCharacterFromCsvNo(0)
            if (gamebase.DefaultCharacter > 0) vEvaluator.addCharacterFromCsvNo(gamebase.DefaultCharacter)
            console.printBar()
            console.newLine()
            beginFirst()
        } else if (systemResult == 1L) {
            if (callFunction("TITLE_LOADGAME", false, false)) state.systemState = SystemStateCode.Openning_TitleLoadgame
            else beginLoadGameOpening()
        } else {
            console.deleteLine(1)
            console.printTemporaryLine("無効な値です")
            console.updatedGeneration = true
            openingInput()
        }
    }

    private fun beginFirst() {
        state.systemState = SystemStateCode.Normal
        if (isCTrain) if (clearCommands()) return
        skipPrint = false
        callFunction("EVENTFIRST", true, true)
    }

    private fun endTitleLoadgame() = beginTitleProc()

    private fun beginTrain() {
        vEvaluator.updateInBeginTrain()
        state.systemState = SystemStateCode.Train_CallEventTrain
        if (!callFunction("EVENTTRAIN", false, true)) endCallEventTrain()
    }

    private var coms: MutableList<Long> = ArrayList()
    private var isCTrain = false
    private var count = 0
    var skipPrint = false

    private fun endCallEventTrain() {
        if (vEvaluator.NEXTCOM >= 0) {
            state.systemState = SystemStateCode.Train_CallEventCom
            vEvaluator.SELECTCOM = vEvaluator.NEXTCOM
            vEvaluator.NEXTCOM = 0
            callEventCom()
        } else {
            if (isCTrain) skipPrint = true
            callFunction("SHOW_STATUS", true, false)
            state.systemState = SystemStateCode.Train_CallShowStatus
        }
    }

    private fun endCallShowStatus() {
        state.systemState = SystemStateCode.Train_CallComAbleXX
        lastCalledComable = -1
        lastAddCom = -1
        printComCount = 0
        comAble.fill(-1)
        endCallComAbleXX()
    }

    private fun getTrainComString(trainCode: Int, comNo: Int): String = trainName[trainCode] + "[" + comNo.toString().padStart(3, ' ') + "]"

    private var printComCount = 0

    private fun printCom() {
        if (!isCTrain) {
            console.printC(getTrainComString(lastCalledComable, lastAddCom), true)
            printComCount++
            if (Config.PrintCPerLine > 0 && printComCount % Config.PrintCPerLine == 0) console.printFlush(false)
        }
    }

    private fun endCallComAbleXX() {
        if (lastCalledComable >= 0 && trainName[lastCalledComable] != null) {
            lastAddCom++
            if (vEvaluator.RESULT != 0L) {
                comAble[lastAddCom] = lastCalledComable
                printCom()
                console.refreshStrings(false)
            }
        }
        while (++lastCalledComable < trainName.size) {
            if (trainName[lastCalledComable] == null) continue
            if (!callFunction("COM_ABLE$lastCalledComable", false, false)) {
                lastAddCom++
                if (Config.ComAbleDefault == 0) continue
                comAble[lastAddCom] = lastCalledComable
                printCom()
                continue
            }
            console.refreshStrings(false)
            return
        }
        if (lastCalledComable >= trainName.size) {
            state.systemState = SystemStateCode.Train_CallShowUserCom
            console.printFlush(false)
            console.refreshStrings(false)
            callFunction("SHOW_USERCOM", true, false)
        }
    }

    private fun endCallShowUserCom() {
        if (skipPrint) skipPrint = false
        vEvaluator.updateAfterShowUsercom()
        if (!isCTrain) {
            setWaitInput()
            state.systemState = SystemStateCode.Train_WaitInput
        } else if (count < coms.size) {
            systemResult = coms[count]
            count++
            trainWaitInput()
        }
    }

    private fun trainWaitInput() {
        var selectCom = -1
        if (!isCTrain) {
            if (systemResult >= 0 && systemResult < comAble.size) selectCom = comAble[systemResult.toInt()]
        } else {
            for (c in comAble) if (c.toLong() == systemResult) selectCom = systemResult.toInt()
            console.printSingleLine("＜コマンド連続実行：${count}/${coms.size}＞")
        }
        if (selectCom >= 0) {
            vEvaluator.SELECTCOM = selectCom.toLong()
            callEventCom()
        } else {
            if (isCTrain) console.printSingleLine("コマンドを実行できませんでした")
            vEvaluator.RESULT = systemResult
            state.systemState = SystemStateCode.Train_CallEventComEnd
            callFunction("USERCOM", true, false)
        }
    }

    private var doTrainSelectCom: Long = -1

    private fun doTrain() {
        vEvaluator.updateAfterShowUsercom()
        vEvaluator.SELECTCOM = doTrainSelectCom
        callEventCom()
    }

    private fun callEventCom() {
        vEvaluator.updateAfterInputCom()
        state.systemState = SystemStateCode.Train_CallEventCom
        if (!callFunction("EVENTCOM", false, true)) endEventCom()
    }

    private fun endEventCom() {
        state.systemState = SystemStateCode.Train_CallComXX
        callFunction("COM" + vEvaluator.SELECTCOM, true, false)
    }

    private fun endCallComXX() {
        if (vEvaluator.RESULT == 0L) endCallEventComEnd()
        else {
            state.systemState = SystemStateCode.Train_CallSourceCheck
            callFunction("SOURCE_CHECK", true, false)
        }
    }

    private fun endCallSourceCheck() {
        vEvaluator.updateAfterSourceCheck()
        state.systemState = SystemStateCode.Train_CallEventComEnd
        needWaitToEventComEnd = true
        if (!callFunction("EVENTCOMEND", false, true)) endCallEventComEnd()
    }

    var needWaitToEventComEnd = false
    private var needCheck = true

    private fun endCallEventComEnd() {
        if (console.lastLineIsTemporary && !isCTrain && needCheck) {
            if (console.lastLineIsEmpty) {
                console.deleteLine(2)
                console.printTemporaryLine("無効な値です")
            }
            console.updatedGeneration = true
            endCallShowUserCom()
        } else {
            if (isCTrain && count == coms.size) {
                isCTrain = false
                skipPrint = false
                coms.clear()
                count = 0
                if (callFunction("CALLTRAINEND", false, false)) {
                    needCheck = false
                    return
                }
            }
            needCheck = true
            if (needWaitToEventComEnd) setWait()
            needWaitToEventComEnd = false
            endCallEventTrain()
        }
    }

    private fun beginAfterTrain() {
        if (isCTrain) if (clearCommands()) return
        skipPrint = false
        state.systemState = SystemStateCode.Normal
        callFunction("EVENTEND", true, true)
    }

    private fun beginAblup() {
        if (isCTrain) if (clearCommands()) return
        skipPrint = false
        state.systemState = SystemStateCode.Ablup_CallShowJuel
        callFunction("SHOW_JUEL", true, false)
    }

    private fun endCallShowJuel() {
        state.systemState = SystemStateCode.Ablup_CallShowAblupSelect
        callFunction("SHOW_ABLUP_SELECT", true, false)
    }

    private fun endCallShowAblupSelect() {
        setWaitInput()
        state.systemState = SystemStateCode.Ablup_WaitInput
    }

    private fun ablupWaitInput() {
        if (systemResult in 0..99) {
            state.systemState = SystemStateCode.Ablup_CallAblupXX
            if (!callFunction("ABLUP$systemResult", false, false)) {
                console.deleteLine(1)
                console.printTemporaryLine("無効な値です")
                console.updatedGeneration = true
                endCallShowAblupSelect()
            }
        } else {
            vEvaluator.RESULT = systemResult
            state.systemState = SystemStateCode.Ablup_CallAblupXX
            callFunction("USERABLUP", true, false)
        }
    }

    private fun endCallAblupXX() {
        if (console.lastLineIsTemporary) {
            if (console.lastLineIsEmpty) {
                console.deleteLine(2)
                console.printTemporaryLine("無効な値です")
            }
            console.updatedGeneration = true
            endCallShowAblupSelect()
        } else beginAblup()
    }

    private fun beginTurnend() {
        if (isCTrain) if (clearCommands()) return
        skipPrint = false
        callFunction("EVENTTURNEND", true, true)
        state.systemState = SystemStateCode.Normal
    }

    private fun beginShop() {
        if (isCTrain) if (clearCommands()) return
        skipPrint = false
        state.systemState = SystemStateCode.Shop_CallEventShop
        if (!callFunction("EVENTSHOP", false, true)) endCallEventShop()
    }

    private fun endCallEventShop() {
        saveTarget = -1
        if (Config.AutoSave && state.calledWhenNormal) beginAutoSave()
        else {
            state.systemState = SystemStateCode.AutoSave_Skipped
            endAutoSaveCallSaveInfo()
        }
    }

    private fun nowText(): String = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")) + " "

    private fun beginAutoSave() {
        if (callFunction("SYSTEM_AUTOSAVE", false, false)) {
            state.systemState = SystemStateCode.AutoSave_CallUniqueAutosave
            return
        }
        saveTarget = AutoSaveIndex
        vEvaluator.SAVEDATA_TEXT = nowText()
        state.systemState = SystemStateCode.AutoSave_CallSaveInfo
        if (!callFunction("SAVEINFO", false, false)) endAutoSaveCallSaveInfo()
    }

    private fun endAutoSaveCallSaveInfo() {
        if (saveTarget == AutoSaveIndex) {
            if (!vEvaluator.saveTo(saveTarget, vEvaluator.SAVEDATA_TEXT ?: "")) {
                console.printError("オートセーブ中に予期しないエラーが発生しました")
                console.printError("オートセーブをスキップします")
                console.readAnyKey()
            }
        }
        endAutoSave()
    }

    private fun endAutoSave() {
        if (state.isBegun) { state.begin(); return }
        state.systemState = SystemStateCode.Shop_CallShowShop
        callFunction("SHOW_SHOP", true, false)
    }

    private fun endCallShowShop() {
        setWaitInput()
        state.systemState = SystemStateCode.Shop_WaitInput
    }

    private fun shopWaitInput() {
        if (systemResult >= 0 && systemResult < Config.MaxShopItem) {
            if (vEvaluator.itemSales(systemResult)) {
                if (vEvaluator.buyItem(systemResult)) {
                    state.systemState = SystemStateCode.Shop_CallEventBuy
                    if (!callFunction("EVENTBUY", false, true)) endCallEventBuy()
                    return
                } else {
                    console.deleteLine(1)
                    console.printTemporaryLine("お金が足りません。")
                }
            } else {
                console.deleteLine(1)
                console.printTemporaryLine("売っていません。")
            }
            endCallShowShop()
        } else {
            vEvaluator.RESULT = systemResult
            callFunction("USERSHOP", true, false)
            state.systemState = SystemStateCode.Shop_CallEventBuy
        }
    }

    private fun endCallEventBuy() {
        if (console.lastLineIsTemporary) {
            if (console.lastLineIsEmpty) {
                console.deleteLine(2)
                console.printTemporaryLine("無効な値です")
            }
            console.updatedGeneration = true
            endCallShowShop()
        } else endAutoSave()
    }

    private fun beginDataLoaded() {
        state.systemState = SystemStateCode.LoadData_CallSystemLoad
        if (!callFunction("SYSTEM_LOADEND", false, false)) endSystemLoad()
    }

    private fun endSystemLoad() {
        state.systemState = SystemStateCode.LoadData_CallEventLoad
        if (!callFunction("EVENTLOAD", false, true)) endAutoSave()
    }

    private fun endEventLoad() = endAutoSave()

    private fun beginSaveGame() {
        console.printSingleLine("何番にセーブしますか？")
        state.systemState = SystemStateCode.SaveGame_Begin
        printSaveDataText()
    }

    private fun beginLoadGame() {
        console.printSingleLine("何番をロードしますか？")
        state.systemState = SystemStateCode.LoadGame_Begin
        printSaveDataText()
    }

    private fun beginLoadGameOpening() {
        console.printSingleLine("何番をロードしますか？")
        state.systemState = SystemStateCode.LoadGameOpenning_Begin
        printSaveDataText()
    }

    private var dataIsAvailable = BooleanArray(21)
    private var isFirstTime = true
    private var page = 0

    private fun p2(i: Int) = i.toString().padStart(2, ' ')

    private fun printSaveDataText() {
        if (isFirstTime) {
            isFirstTime = false
            dataIsAvailable = BooleanArray(Config.SaveDataNos + 1)
        }
        for (i in 0 until page) {
            console.printFlush(false)
            console.print("[${p2(i * 20)}] セーブデータ${p2(i * 20)}～${p2(i * 20 + 19)}を表示")
        }
        for (i in 0 until 20) {
            val dataNo = page * 20 + i
            if (dataNo == dataIsAvailable.size - 1) break
            dataIsAvailable[dataNo] = false
            console.printFlush(false)
            console.print("[${p2(dataNo)}] ")
            if (!writeSavedataTextFrom(dataNo)) continue
            dataIsAvailable[dataNo] = true
        }
        for (i in page until (dataIsAvailable.size - 2) / 20) {
            console.printFlush(false)
            console.print("[${p2((i + 1) * 20)}] セーブデータ${p2((i + 1) * 20)}～${p2((i + 1) * 20 + 19)}を表示")
        }
        dataIsAvailable[dataIsAvailable.size - 1] = false
        if (state.systemState != SystemStateCode.SaveGame_Begin) {
            console.printFlush(false)
            console.print("[${p2(AutoSaveIndex)}] ")
            if (writeSavedataTextFrom(AutoSaveIndex)) dataIsAvailable[dataIsAvailable.size - 1] = true
        }
        console.refreshStrings(false)
        console.printSingleLine("[100] 戻る")
        setWaitInput()
        state.systemState = when (state.systemState) {
            SystemStateCode.SaveGame_Begin -> SystemStateCode.SaveGame_WaitInput
            SystemStateCode.LoadGame_Begin -> SystemStateCode.LoadGame_WaitInput
            else -> SystemStateCode.LoadGameOpenning_WaitInput
        }
    }

    private var saveTarget = -1

    private fun invalidInput() {
        console.deleteLine(1)
        console.printTemporaryLine("無効な値です")
        console.updatedGeneration = true
        setWaitInput()
    }

    private fun saveGameWaitInput() {
        if (systemResult == 100L) { loadPrevState(); return }
        else if ((systemResult.toInt() / 20) != page && systemResult != AutoSaveIndex.toLong() && systemResult >= 0 && systemResult < dataIsAvailable.size - 1) {
            page = systemResult.toInt() / 20
            state.systemState = SystemStateCode.SaveGame_Begin
            printSaveDataText()
            return
        }
        val available: Boolean
        if (systemResult >= 0 && systemResult < dataIsAvailable.size - 1) available = dataIsAvailable[systemResult.toInt()]
        else { invalidInput(); return }
        saveTarget = systemResult.toInt()
        if (available) {
            console.printSingleLine("既にデータが存在します。上書きしますか？")
            console.printC("[0] はい", false)
            console.printC("[1] いいえ", false)
            setWaitInput()
            state.systemState = SystemStateCode.SaveGame_WaitInputOverwrite
            return
        }
        systemResult = 0
        saveGameWaitInputOverwrite()
    }

    private fun saveGameWaitInputOverwrite() {
        if (systemResult == 1L) { beginSaveGame(); return }
        else if (systemResult != 0L) { invalidInput(); return }
        vEvaluator.SAVEDATA_TEXT = nowText()
        state.systemState = SystemStateCode.SaveGame_CallSaveInfo
        if (!callFunction("SAVEINFO", false, false)) endCallSaveInfo()
    }

    private fun endCallSaveInfo() {
        if (!vEvaluator.saveTo(saveTarget, vEvaluator.SAVEDATA_TEXT ?: "")) {
            console.printError("セーブ中に予期しないエラーが発生しました")
            console.readAnyKey()
        }
        loadPrevState()
    }

    private fun loadGameWaitInput() {
        if (systemResult == 100L) {
            if (state.systemState == SystemStateCode.LoadGameOpenning_WaitInput) { beginTitleProc(); return }
            loadPrevState()
            return
        } else if ((systemResult.toInt() / 20) != page && systemResult != AutoSaveIndex.toLong() && systemResult >= 0 && systemResult < dataIsAvailable.size - 1) {
            page = systemResult.toInt() / 20
            state.systemState = if (state.systemState == SystemStateCode.LoadGameOpenning_WaitInput) SystemStateCode.LoadGameOpenning_Begin else SystemStateCode.LoadGame_Begin
            printSaveDataText()
            return
        }
        val available: Boolean = when {
            systemResult >= 0 && systemResult < dataIsAvailable.size - 1 -> dataIsAvailable[systemResult.toInt()]
            systemResult == AutoSaveIndex.toLong() -> dataIsAvailable[dataIsAvailable.size - 1]
            else -> { invalidInput(); return }
        }
        if (!available) {
            console.printSingleLine(systemResult.toString())
            console.printError("データがありません")
            if (state.systemState == SystemStateCode.LoadGameOpenning_WaitInput) { beginLoadGameOpening(); return }
            beginLoadGame()
            return
        }
        if (!vEvaluator.loadFrom(systemResult.toInt())) throw ExeEE("ファイルのロード中に予期しないエラーが発生しました")
        deletePrevState()
        beginDataLoaded()
    }

    private fun endNormal(): Unit = throw CodeEE("予期しないスクリプト終端です")

    private fun endReloaderb() {
        loadPrevState()
        console.reloadErbFinished()
    }

    private fun writeSavedataTextFrom(saveIndex: Int): Boolean {
        val result = vEvaluator.checkData(saveIndex, EraSaveFileType.Normal)
        console.print(result.dataMes)
        console.newLine()
        return result.state == EraDataState.OK
    }

    // ===================== Process.ScriptProc =====================
    private fun runScriptProc() {
        while (true) {
            state.shiftNextLine()
            if (Config.InfiniteLoopAlertTime > 0 && state.lineCount % 10000 == 0) checkInfiniteLoop()
            val line = state.currentLine!!
            if (line.isError) throw CodeEE(line.errMes)
            else if (line is InstructionLine) {
                val func = line
                if (!Program.DebugMode && func.function.isDebug()) continue
                if (func.argument == null) {
                    ArgumentParser.setArgumentTo(func)
                    if (func.isError) throw CodeEE(func.errMes)
                }
                if (skipPrint && func.function.isPrint()) {
                    if (userDefinedSkip && func.function.isInput()) {
                        console.printError("表示スキップ中にデフォルト値を持たないINPUTに遭遇しました")
                        console.printError("INPUTに必要な処理をNOSKIP～ENDNOSKIPで囲むか、SKIPDISP 0～SKIPDISP 1で囲ってください")
                        throw CodeEE("無限ループに入る可能性が高いため実行を終了します")
                    }
                    continue
                }
                val inst = func.function.instruction
                if (inst != null) inst.doInstruction(exm, func, state)
                else if (func.function.isFlowContorol()) doFlowControlFunction(func)
                else doNormalFunction(func)
            } else if (line is NullLine || line is FunctionLabelLine) {
                if (!state.isFunctionMethod) vEvaluator.RESULT = 0
                state.`return`(0)
            } else if (line is GotoLabelLine) continue
            else if (line is InvalidLine) {
                if (line.errMes.isEmpty()) throw CodeEE("読込に失敗した行が実行されました。エラーの詳細は読込時の警告を参照してください。")
                else throw CodeEE(line.errMes)
            }
            if (!console.isRunning || state.scriptEnd) return
        }
    }

    fun doDebugNormalFunction(func: InstructionLine, munchkin: Boolean) {
        val inst = func.function.instruction
        if (inst != null) inst.doInstruction(exm, func, state) else doNormalFunction(func)
        if (munchkin) vEvaluator.iamaMunchkin()
    }

    private fun colorOf(r: Long, g: Long, b: Long) = EraColor.fromArgb(r.toInt(), g.toInt(), b.toInt())

    private fun readColorArg(colorArg: SpColorArgument, useConst: Boolean): EraColor {
        if (useConst && colorArg.isConst) {
            val rgb = colorArg.constInt
            return colorOf((rgb and 0xFF0000) shr 16, (rgb and 0x00FF00) shr 8, rgb and 0x0000FF)
        }
        if (colorArg.rgb != null) {
            val rgb = colorArg.rgb.getIntValue(exm)
            return colorOf((rgb and 0xFF0000) shr 16, (rgb and 0x00FF00) shr 8, rgb and 0x0000FF)
        }
        val r = colorArg.r!!.getIntValue(exm)
        val g = colorArg.g!!.getIntValue(exm)
        val b = colorArg.b!!.getIntValue(exm)
        if (r < 0 || g < 0 || b < 0) throw CodeEE("SETCOLORの引数に0未満の値が指定されました")
        if (r > 255 || g > 255 || b > 255) throw CodeEE("SETCOLORの引数に255を超える値が指定されました")
        return colorOf(r, g, b)
    }

    private fun colorByName(colorName: String): EraColor {
        val c = NamedColors.fromName(colorName)
        if (c == null) {
            if (colorName.equals("transparent", ignoreCase = true)) throw CodeEE("無色透明(Transparent)は色として指定できません")
            throw CodeEE("指定された色名\"$colorName\"は無効な色名です")
        }
        return EraColor(c)
    }

    private fun doNormalFunction(func: InstructionLine) {
        var iValue: Long
        var str: String?
        val arg = func.argument!!
        when (func.functionCode) {
            FunctionCode.PRINTBUTTON -> {
                if (skipPrint) return
                exm.console.useUserStyle = true
                exm.console.useSetColorStyle = true
                val bArg = arg as SpButtonArgument
                str = bArg.printStrTerm.getStrValue(exm).replace("\n", "")
                if (bArg.buttonWord.getOperandType() == EType.Int64) exm.console.printButton(str, bArg.buttonWord.getIntValue(exm))
                else exm.console.printButton(str, bArg.buttonWord.getStrValue(exm))
            }
            FunctionCode.PRINTBUTTONC, FunctionCode.PRINTBUTTONLC -> {
                if (skipPrint) return
                exm.console.useUserStyle = true
                exm.console.useSetColorStyle = true
                val bArg = arg as SpButtonArgument
                str = bArg.printStrTerm.getStrValue(exm).replace("\n", "")
                val isRight = func.functionCode == FunctionCode.PRINTBUTTONC
                if (bArg.buttonWord.getOperandType() == EType.Int64) exm.console.printButtonC(str, bArg.buttonWord.getIntValue(exm), isRight)
                else exm.console.printButtonC(str, bArg.buttonWord.getStrValue(exm), isRight)
            }
            FunctionCode.PRINTPLAIN, FunctionCode.PRINTPLAINFORM -> {
                if (skipPrint) return
                exm.console.useUserStyle = true
                exm.console.useSetColorStyle = true
                exm.console.printPlain((arg as ExpressionArgument).term!!.getStrValue(exm))
            }
            FunctionCode.DRAWLINE -> {
                if (skipPrint) return
                exm.console.printBar()
                exm.console.newLine()
            }
            FunctionCode.DRAWLINEFORM -> {
                if (skipPrint) return
                exm.console.printCustomBar((arg as ExpressionArgument).term!!.getStrValue(exm), false)
                exm.console.newLine()
            }
            FunctionCode.PRINT_ABL, FunctionCode.PRINT_TALENT, FunctionCode.PRINT_MARK, FunctionCode.PRINT_EXP -> {
                if (skipPrint) return
                val target = (arg as ExpressionArgument).term!!.getIntValue(exm)
                exm.console.print(vEvaluator.getCharacterDataString(target, func.functionCode))
                exm.console.newLine()
            }
            FunctionCode.PRINT_PALAM -> {
                if (skipPrint) return
                val target = (arg as ExpressionArgument).term!!.getIntValue(exm)
                var count = 0
                for (i in 0 until 100) {
                    val printStr = vEvaluator.getCharacterParamString(target, i)
                    if (printStr != null) {
                        exm.console.printC(printStr, true)
                        count++
                        if (Config.PrintCPerLine > 0 && count % Config.PrintCPerLine == 0) exm.console.printFlush(false)
                    }
                }
                exm.console.printFlush(false)
                exm.console.refreshStrings(false)
            }
            FunctionCode.PRINT_ITEM -> {
                if (skipPrint) return
                exm.console.print(vEvaluator.getHavingItemsString())
                exm.console.newLine()
            }
            FunctionCode.PRINT_SHOPITEM -> {
                if (skipPrint) return
                var length = minOf(vEvaluator.ITEMSALES.size, vEvaluator.ITEMNAME.size)
                if (length > vEvaluator.ITEMPRICE.size) length = vEvaluator.ITEMPRICE.size
                var count = 0
                for (i in 0 until length) {
                    if (vEvaluator.itemSales(i.toLong())) {
                        val printStr = vEvaluator.ITEMNAME[i] ?: ""
                        val price = vEvaluator.ITEMPRICE[i]
                        if (Config.MoneyFirst) exm.console.printC("[$i] $printStr(${Config.MoneyLabel}$price)", false)
                        else exm.console.printC("[$i] $printStr($price${Config.MoneyLabel})", false)
                        count++
                        if (Config.PrintCPerLine > 0 && count % Config.PrintCPerLine == 0) exm.console.printFlush(false)
                    }
                }
                exm.console.printFlush(false)
                exm.console.refreshStrings(false)
            }
            FunctionCode.UPCHECK -> vEvaluator.updateInUpcheck(exm.console, skipPrint)
            FunctionCode.CUPCHECK -> vEvaluator.cUpdateInUpcheck(exm.console, (arg as ExpressionArgument).term!!.getIntValue(exm), skipPrint)
            FunctionCode.DELALLCHARA -> vEvaluator.delAllCharacter()
            FunctionCode.PICKUPCHARA -> {
                val a = arg as ExpressionArrayArgument
                val noList = LongArray(a.termList.size)
                val charaNum = vEvaluator.CHARANUM
                for (i in a.termList.indices) {
                    val t = a.termList[i]!!
                    noList[i] = t.getIntValue(exm)
                    val code = (t as? VariableTerm)?.identifier?.code
                    if (code != VariableCode.MASTER && code != VariableCode.ASSI && code != VariableCode.TARGET)
                        if (noList[i] < 0 || noList[i] >= charaNum)
                            throw CodeEE("命令PICKUPCHARAの第${i + 1}引数にキャラリストの範囲外の値(${noList[i]})が与えられました")
                }
                vEvaluator.pickUpChara(noList)
            }
            FunctionCode.ADDDEFCHARA -> {
                if (func.parentLabelLine != null && func.parentLabelLine!!.labelName != "SYSTEM_TITLE")
                    throw CodeEE("@SYSTEM_TITLE以外でこの命令を使うことはできません")
                vEvaluator.addCharacterFromCsvNo(0)
                if (GlobalStatic.GameBaseData!!.DefaultCharacter > 0) vEvaluator.addCharacterFromCsvNo(GlobalStatic.GameBaseData!!.DefaultCharacter)
            }
            FunctionCode.PUTFORM -> {
                str = (arg as ExpressionArgument).term!!.getStrValue(exm)
                vEvaluator.SAVEDATA_TEXT = (vEvaluator.SAVEDATA_TEXT ?: "") + str
            }
            FunctionCode.QUIT -> exm.console.quit()
            FunctionCode.VARSIZE -> vEvaluator.varSize((arg as SpVarsizeArgument).variableID)
            FunctionCode.SAVEDATA -> {
                val a = arg as SpSaveDataArgument
                val target = a.target.getIntValue(exm)
                if (target < 0) throw CodeEE("SAVEDATAの引数に負の値(${target})が指定されました")
                else if (target > Int.MAX_VALUE) throw CodeEE("SAVEDATAの引数(${target})が大きすぎます")
                val savemes = a.strExpression.getStrValue(exm)
                if (savemes.contains("\n")) throw CodeEE("SAVEDATAのセーブテキストに改行文字が与えられました（セーブデータが破損するため改行文字は使えません）")
                if (!vEvaluator.saveTo(target.toInt(), savemes)) console.printError("SAVEDATA命令によるセーブ中に予期しないエラーが発生しました")
            }
            FunctionCode.POWER -> {
                val a = arg as SpPowerArgument
                val pow = Math.pow(a.x.getIntValue(exm).toDouble(), a.y.getIntValue(exm).toDouble())
                if (pow.isNaN()) throw CodeEE("累乗結果が非数値です")
                else if (pow.isInfinite()) throw CodeEE("累乗結果が無限大です")
                else if (pow >= Long.MAX_VALUE.toDouble() || pow <= Long.MIN_VALUE.toDouble()) throw CodeEE("累乗結果(${pow})が64ビット符号付き整数の範囲外です")
                a.variableDest.setValue(pow.toLong(), exm)
            }
            FunctionCode.SWAP -> {
                val a = arg as SpSwapVarArgument
                val v1 = a.var1.getFixedVariableTerm(exm)
                val v2 = a.var2.getFixedVariableTerm(exm)
                if (v1.getOperandType() != v2.getOperandType()) throw CodeEE("入れ替える変数の型が異なります")
                if (v1.getOperandType() == EType.Int64) {
                    val temp = v1.getIntValue(exm)
                    v1.setValue(v2.getIntValue(exm), exm)
                    v2.setValue(temp, exm)
                } else if (a.var1.getOperandType() == EType.String) {
                    val temps = v1.getStrValue(exm)
                    v1.setValue(v2.getStrValue(exm), exm)
                    v2.setValue(temps, exm)
                } else throw CodeEE("不明な変数型です")
            }
            FunctionCode.GETTIME -> {
                val n = LocalDateTime.now()
                var date = n.year.toLong()
                date = date * 100 + n.monthValue
                date = date * 100 + n.dayOfMonth
                date = date * 100 + n.hour
                date = date * 100 + n.minute
                date = date * 100 + n.second
                date = date * 1000 + n.nano / 1000000
                vEvaluator.RESULT = date
                vEvaluator.RESULTS = n.format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"))
            }
            FunctionCode.SETCOLOR -> exm.console.setStringStyle(readColorArg(arg as SpColorArgument, false))
            FunctionCode.SETCOLORBYNAME -> exm.console.setStringStyle(colorByName(arg.constStr!!))
            FunctionCode.SETBGCOLOR -> exm.console.setBgColor(readColorArg(arg as SpColorArgument, true))
            FunctionCode.SETBGCOLORBYNAME -> exm.console.setBgColor(colorByName(arg.constStr!!))
            FunctionCode.FONTSTYLE -> {
                var fs = FontStyle.Regular
                iValue = if (arg.isConst) arg.constInt else (arg as ExpressionArgument).term!!.getIntValue(exm)
                if ((iValue and 1) != 0L) fs = fs or FontStyle.Bold
                if ((iValue and 2) != 0L) fs = fs or FontStyle.Italic
                if ((iValue and 4) != 0L) fs = fs or FontStyle.Strikeout
                if ((iValue and 8) != 0L) fs = fs or FontStyle.Underline
                exm.console.setStringStyle(fs)
            }
            FunctionCode.SETFONT -> {
                str = if (arg.isConst) arg.constStr else (arg as ExpressionArgument).term!!.getStrValue(exm)
                exm.console.setFont(str ?: "")
            }
            FunctionCode.ALIGNMENT -> {
                str = arg.constStr!!
                exm.console.alignment = when {
                    str.equals("LEFT", Config.ICVariable) -> DisplayLineAlignment.LEFT
                    str.equals("CENTER", Config.ICVariable) -> DisplayLineAlignment.CENTER
                    str.equals("RIGHT", Config.ICVariable) -> DisplayLineAlignment.RIGHT
                    else -> throw CodeEE("ALIGNMENTのキーワード\"$str\"は未定義です")
                }
            }
            FunctionCode.REDRAW -> exm.console.setRedraw(if (arg.isConst) arg.constInt else (arg as ExpressionArgument).term!!.getIntValue(exm))
            FunctionCode.RESET_STAIN -> vEvaluator.setDefaultStain(if (arg.isConst) arg.constInt else (arg as ExpressionArgument).term!!.getIntValue(exm))
            FunctionCode.SPLIT -> {
                val a = arg as SpSplitArgument
                val target = a.targetStr.getStrValue(exm)
                val sep = a.split.getStrValue(exm)
                var retStr: Array<String?> = if (sep.isEmpty()) arrayOf(target) else target.split(sep).toTypedArray()
                a.num!!.setValue(retStr.size.toLong(), exm)
                val len0 = a.variable.getLength(0)
                if (retStr.size > len0) retStr = retStr.copyOf(len0)
                a.variable.setValues(retStr, longArrayOf(0, 0, 0))
            }
            FunctionCode.PRINTCPERLINE -> (arg as SpGetIntArgument).varToken.setValue(Config.PrintCPerLine.toLong(), exm)
            FunctionCode.SAVENOS -> (arg as SpGetIntArgument).varToken.setValue(Config.SaveDataNos.toLong(), exm)
            FunctionCode.FORCEKANA -> exm.forceKana(if (arg.isConst) arg.constInt else (arg as ExpressionArgument).term!!.getIntValue(exm))
            FunctionCode.SKIPDISP -> {
                iValue = if (arg.isConst) arg.constInt else (arg as ExpressionArgument).term!!.getIntValue(exm)
                skipPrint = iValue != 0L
                userDefinedSkip = iValue != 0L
                vEvaluator.RESULT = if (skipPrint) 1L else 0L
            }
            FunctionCode.NOSKIP -> {
                if (func.jumpTo == null) throw CodeEE("対応するENDNOSKIPのないNOSKIPです")
                saveSkip = skipPrint
                if (skipPrint) skipPrint = false
            }
            FunctionCode.ENDNOSKIP -> {
                if (func.jumpTo == null) throw CodeEE("対応するNOSKIPのないENDNOSKIPです")
                if (saveSkip) skipPrint = true
            }
            FunctionCode.OUTPUTLOG -> exm.console.outputLog(null)
            FunctionCode.ARRAYSHIFT -> {
                val a = arg as SpArrayShiftArgument
                if (!a.varToken.identifier.isArray1D) throw CodeEE("ARRAYSHIFTは1次元配列および配列型キャラクタ変数のみに対応しています")
                val dest = a.varToken.getFixedVariableTerm(exm)
                val shift = a.num1.getIntValue(exm).toInt()
                if (shift == 0) return
                val start = a.num3!!.getIntValue(exm).toInt()
                if (start < 0) throw CodeEE("ARRAYSHIFTの第４引数が負の値(${start})です")
                val num: Int
                if (a.num4 != null) {
                    num = a.num4.getIntValue(exm).toInt()
                    if (num < 0) throw CodeEE("ARRAYSHIFTの第５引数が負の値(${num})です")
                    if (num == 0) return
                } else num = -1
                if (dest.identifier.isInteger) vEvaluator.shiftArray(dest, shift, a.num2.getIntValue(exm), start, num)
                else vEvaluator.shiftArray(dest, shift, a.num2.getStrValue(exm), start, num)
            }
            FunctionCode.ARRAYREMOVE -> {
                val a = arg as SpArrayControlArgument
                if (!a.varToken.identifier.isArray1D) throw CodeEE("ARRAYREMOVEは1次元配列および配列型キャラクタ変数のみに対応しています")
                val p = a.varToken.getFixedVariableTerm(exm)
                val start = a.num1.getIntValue(exm).toInt()
                val num = a.num2.getIntValue(exm).toInt()
                if (start < 0) throw CodeEE("ARRAYREMOVEの第２引数が負の値(${start})です")
                if (num < 0) throw CodeEE("ARRAYREMOVEの第３引数が負の値(${start})です")
                if (num == 0) return
                vEvaluator.removeArray(p, start, num)
            }
            FunctionCode.ARRAYSORT -> {
                val a = arg as SpArraySortArgument
                if (!a.varToken.identifier.isArray1D) throw CodeEE("ARRAYRESORTは1次元配列および配列型キャラクタ変数のみに対応しています")
                val p = a.varToken.getFixedVariableTerm(exm)
                val start = a.num1!!.getIntValue(exm).toInt()
                if (start < 0) throw CodeEE("ARRAYSORTの第３引数が負の値(${start})です")
                val num: Int
                if (a.num2 != null) {
                    num = a.num2.getIntValue(exm).toInt()
                    if (num < 0) throw CodeEE("ARRAYSORTの第４引数が負の値(${start})です")
                    if (num == 0) return
                } else num = -1
                vEvaluator.sortArray(p, a.order, start, num)
            }
            FunctionCode.ARRAYCOPY -> {
                val a = arg as SpCopyArrayArgument
                val n1 = a.varName1
                val n2 = a.varName2
                val v0: VariableToken
                val v1: VariableToken
                if (n1 !is SingleTerm || n2 !is SingleTerm) {
                    val name0 = n1.getStrValue(exm)
                    val name1 = n2.getStrValue(exm)
                    v0 = idDic.getVariableToken(name0, null, true) ?: throw CodeEE("ARRAYCOPY命令の第１引数(${name0})が有効な変数名ではありません")
                    if (!v0.isArray1D && !v0.isArray2D && !v0.isArray3D) throw CodeEE("ARRAYCOPY命令の第１引数\"${name0}\"は配列変数ではありません")
                    if (v0.isCharacterData) throw CodeEE("ARRAYCOPY命令の第１引数\"${name0}\"はキャラクタ変数です（対応していません）")
                    v1 = idDic.getVariableToken(name1, null, true) ?: throw CodeEE("ARRAYCOPY命令の第２引数(${name0})が有効な変数名ではありません")
                    if (!v1.isArray1D && !v1.isArray2D && !v1.isArray3D) throw CodeEE("ARRAYCOPY命令の第２引数\"${name1}\"は配列変数ではありません")
                    if (v1.isCharacterData) throw CodeEE("ARRAYCOPY命令の第２引数\"${name1}\"はキャラクタ変数です（対応していません）")
                    if (v1.isConst) throw CodeEE("ARRAYCOPY命令の第２引数\"${name1}\"は値を変更できない変数です")
                    if ((v0.isArray1D && !v1.isArray1D) || (v0.isArray2D && !v1.isArray2D) || (v0.isArray3D && !v1.isArray3D)) throw CodeEE("ARRAYCOPY命令の２つの配列変数の次元数が一致していません")
                    if ((v0.isInteger && v1.isString) || (v0.isString && v1.isInteger)) throw CodeEE("ARRAYCOPY命令の２つの配列変数の型が一致していません")
                } else {
                    v0 = idDic.getVariableToken(n1.str, null, true)!!
                    v1 = idDic.getVariableToken(n2.str, null, true)!!
                    if ((v0.isInteger && v1.isString) || (v0.isString && v1.isInteger)) throw CodeEE("ARRAYCOPY命令の２つの配列変数の型が一致していません")
                }
                vEvaluator.copyArray(v0, v1)
            }
            FunctionCode.ENCODETOUNI -> {
                val target = (arg as ExpressionArgument).term!!.getStrValue(exm)
                val length = vEvaluator.RESULT_ARRAY.size
                if (target.length > length - 1) throw CodeEE("ENCODETOUNIの引数が長すぎます（現在${target.length}文字。最大${length - 1}文字まで）")
                val ary = IntArray(target.length) {
                    if (Character.isLowSurrogate(target[it])) throw CodeEE("ENCODETOUNI:無効なサロゲートペアです")
                    target.codePointAt(it)
                }
                vEvaluator.setEncodingResult(ary)
            }
            FunctionCode.ASSERT -> if ((arg as ExpressionArgument).term!!.getIntValue(exm) == 0L) throw CodeEE("ASSERT文の引数が0です")
            FunctionCode.THROW -> throw CodeEE((arg as ExpressionArgument).term!!.getStrValue(exm))
            FunctionCode.CLEARTEXTBOX -> console.clearTextBox()
            FunctionCode.STRDATA -> {
                val dataList = func.dataList!!
                if (dataList.isEmpty()) { state.jumpTo(func.jumpTo); return }
                val choice = exm.vEvaluator.getNextRand(dataList.size.toLong()).toInt()
                val iList = dataList[choice]
                val sb = StringBuilder()
                var i = 0
                for (selectedLine in iList) {
                    state.currentLine = selectedLine
                    if (selectedLine.argument == null) ArgumentParser.setArgumentTo(selectedLine)
                    sb.append((selectedLine.argument as ExpressionArgument).term!!.getStrValue(exm))
                    if (++i < iList.size) sb.append("\n")
                }
                (arg as StrDataArgument).variable.setValue(sb.toString(), exm)
                state.jumpTo(func.jumpTo)
            }
            else -> {}
        }
    }

    private var saveSkip = false
    private var userDefinedSkip = false

    private fun doFlowControlFunction(func: InstructionLine): Boolean {
        val arg = func.argument
        when (func.functionCode) {
            FunctionCode.LOADDATA -> {
                val target = (arg as ExpressionArgument).term!!.getIntValue(exm)
                if (target < 0) throw CodeEE("LOADDATAの引数に負の値(${target})が指定されました")
                else if (target > Int.MAX_VALUE) throw CodeEE("LOADDATAの引数(${target})が大きすぎます")
                val result = vEvaluator.checkData(target.toInt(), EraSaveFileType.Normal)
                if (result.state != EraDataState.OK) throw CodeEE("不正なデータをロードしようとしました")
                if (!vEvaluator.loadFrom(target.toInt())) throw ExeEE("ファイルのロード中に予期しないエラーが発生しました")
                state.clearFunctionList()
                state.systemState = SystemStateCode.LoadData_DataLoaded
                return false
            }
            FunctionCode.TRYCALLLIST, FunctionCode.TRYJUMPLIST -> {
                for (iLine in func.callList!!) {
                    val cfa = iLine.argument as SpCallArgment
                    var funcName = cfa.funcnameTerm.getStrValue(exm)
                    if (Config.ICFunction) funcName = funcName.uppercase()
                    val callto = CalledFunction.callFunction(this, funcName, func.jumpTo) ?: continue
                    callto.isJump = func.function.isJump()
                    val errMes = arrayOfNulls<String>(1)
                    val args = callto.convertArg(cfa.rowArgs, errMes) ?: throw CodeEE(errMes[0] ?: "")
                    state.intoFunction(callto, args, exm)
                    return true
                }
                state.jumpTo(func.jumpTo)
            }
            FunctionCode.TRYGOTOLIST -> {
                var jumpto: LogicalLine? = null
                for (iLine in func.callList!!) {
                    if (iLine.argument == null) ArgumentParser.setArgumentTo(iLine)
                    var funcName = (iLine.argument as SpCallArgment).funcnameTerm.getStrValue(exm)
                    if (Config.ICVariable) funcName = funcName.uppercase()
                    jumpto = state.currentCalled!!.callLabel(this, funcName)
                    if (jumpto != null) break
                }
                state.jumpTo(jumpto ?: func.jumpTo)
            }
            FunctionCode.CALLTRAIN -> {
                setCommnds((arg as ExpressionArgument).term!!.getIntValue(exm))
                return false
            }
            FunctionCode.STOPCALLTRAIN -> {
                if (isCTrain) {
                    clearCommands()
                    skipPrint = false
                }
                return false
            }
            FunctionCode.DOTRAIN -> {
                when (state.systemState) {
                    SystemStateCode.Train_CallEventTrain, SystemStateCode.Train_CallShowStatus,
                    SystemStateCode.Train_CallShowUserCom, SystemStateCode.Train_CallEventComEnd -> {}
                    else -> {
                        exm.console.printSystemLine(state.systemState.toString())
                        throw CodeEE("DOTRAIN命令をこの位置で実行することはできません")
                    }
                }
                coms.clear()
                isCTrain = false
                this.count = 0
                val train = (arg as ExpressionArgument).term!!.getIntValue(exm)
                if (train < 0) throw CodeEE("DOTRAIN命令に0未満の値が渡されました")
                if (train >= trainName.size) throw CodeEE("DOTRAIN命令にTRAINNAMEの配列数以上の値が渡されました")
                doTrainSelectCom = train
                state.systemState = SystemStateCode.Train_DoTrain
                return false
            }
            else -> {}
        }
        return true
    }

    private val prevStateList = ArrayList<ProcessState>()

    fun saveCurrentState(@Suppress("UNUSED_PARAMETER") single: Boolean) {
        prevStateList.add(state)
        state = state.clone()
    }

    fun loadPrevState() {
        state.clearFunctionList()
        state = prevStateList[prevStateList.size - 1]
        deletePrevState()
    }

    private fun deletePrevState() {
        if (prevStateList.isEmpty()) return
        prevStateList.removeAt(prevStateList.size - 1)
    }

    private fun deleteAllPrevState() {
        for (s in prevStateList) s.clearFunctionList()
        prevStateList.clear()
    }

    val getCurrentState: ProcessState get() = state

    companion object {
        private const val AutoSaveIndex = 99
    }
}
