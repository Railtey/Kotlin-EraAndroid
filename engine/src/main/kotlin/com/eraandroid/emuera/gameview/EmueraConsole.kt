package com.eraandroid.emuera.gameview

import com.eraandroid.emuera.GlobalStatic
import com.eraandroid.emuera.Program
import com.eraandroid.emuera.config.Config
import com.eraandroid.emuera.config.EraColor
import com.eraandroid.emuera.content.ASprite
import com.eraandroid.emuera.content.AppContents
import com.eraandroid.emuera.content.GraphicsImage
import com.eraandroid.emuera.content.SpriteG
import com.eraandroid.emuera.gamedata.expression.EType
import com.eraandroid.emuera.gamedata.expression.ExpressionParser
import com.eraandroid.emuera.gamedata.expression.TermEndWith
import com.eraandroid.emuera.gameproc.*
import com.eraandroid.emuera.gameproc.function.ArgumentParser
import com.eraandroid.emuera.gameproc.function.FunctionCode
import com.eraandroid.emuera.platform.EPoint
import com.eraandroid.emuera.platform.ERect
import com.eraandroid.emuera.platform.Platform
import com.eraandroid.emuera.sub.*
import java.io.File

enum class ConsoleState { Initializing, Quit, Error, Running, WaitInput, Sleep }

/**
 * Emuera のコンソール。PRINT 系の出力を ConsoleDisplayLine のリストとして保持し、入力待ちを管理する。
 * すべてのメソッドはエンジンスレッドから呼ぶこと。UI は [snapshot] で表示内容を取得する。
 */
class EmueraConsole(val host: ConsoleHost) {
    private val lock = Any()
    private var state = ConsoleState.Initializing
    private val msPerFrame: Long = if (Config.FPS > 0) 1000L / Config.FPS else 1000L / 60
    private val displayLineList = ArrayList<ConsoleDisplayLine>()
    private val printBuffer = PrintStringBuffer(this)
    private val stringMeasure = StringMeasure()
    private lateinit var emuera: Process


    // ===================== CBG =====================
    class ClientBackGroundImage(val zdepth: Int) : Comparable<ClientBackGroundImage> {
        var img: ASprite? = null
        var imgB: ASprite? = null
        var x = 0
        var y = 0
        var isButton = false
        var buttonValue = 0
        var tooltipString: String? = null
        override fun compareTo(other: ClientBackGroundImage): Int = -zdepth.compareTo(other.zdepth)
    }

    private val cbgList = ArrayList<ClientBackGroundImage>()
    private var cbgButtonMap: GraphicsImage? = null
    var selectingCBGButtonInt = -1
        private set
    private var lastSelectingCBGButtonInt = -1

    fun cbgClear() {
        synchronized(lock) {
            for (c in cbgList) if (c.img != null && c.img!!.name.isEmpty()) c.img!!.dispose()
            cbgList.clear()
            cbgClearBMap()
            cbgList.add(ClientBackGroundImage(0))
        }
    }

    fun cbgClearRange(zmin: Int, zmax: Int) {
        if (zmin > zmax) return
        synchronized(lock) {
            cbgList.removeAll { c ->
                val rm = !(c.zdepth < zmin || c.zdepth > zmax || c.zdepth == 0)
                if (rm && c.img != null && c.img!!.name.isEmpty()) c.img!!.dispose()
                rm
            }
        }
    }

    fun cbgClearButton() {
        synchronized(lock) {
            cbgList.removeAll { c ->
                if (c.isButton && c.img != null && c.img!!.name.isEmpty()) c.img!!.dispose()
                c.isButton
            }
            cbgClearBMap()
        }
    }

    fun cbgClearBMap() {
        cbgButtonMap = null
        selectingCBGButtonInt = -1
        lastSelectingCBGButtonInt = -1
    }

    fun cbgSetGraphics(gra: GraphicsImage?, x: Int, y: Int, zdepth: Int): Boolean {
        if (gra == null || !gra.isCreated) return false
        return cbgSetImage(SpriteG("", gra, ERect(0, 0, gra.width, gra.height)), x, y, zdepth)
    }

    fun cbgSetImage(image: ASprite?, x: Int, y: Int, zdepth: Int): Boolean {
        if (image == null || !image.isCreated) return false
        if (zdepth == 0) throw IllegalArgumentException()
        val cbg = ClientBackGroundImage(zdepth)
        cbg.img = image; cbg.x = x; cbg.y = y
        synchronized(lock) { cbgList.add(cbg); cbgList.sort() }
        return true
    }

    fun cbgSetButtonMap(gra: GraphicsImage?): Boolean {
        if (gra == null || !gra.isCreated) return false
        if (cbgButtonMap === gra) return false
        cbgButtonMap = gra
        selectingCBGButtonInt = -1
        lastSelectingCBGButtonInt = -1
        return true
    }

    fun cbgSetButtonImage(buttonValue: Int, imageN: ASprite?, imageB: ASprite?, x: Int, y: Int, zdepth: Int, tooltip: String?): Boolean {
        if (zdepth == 0) throw IllegalArgumentException()
        val cbg = ClientBackGroundImage(zdepth)
        cbg.img = imageN; cbg.imgB = imageB; cbg.x = x; cbg.y = y
        cbg.isButton = true; cbg.buttonValue = buttonValue; cbg.tooltipString = tooltip
        synchronized(lock) { cbgList.add(cbg); cbgList.sort() }
        return true
    }

    val clientWidth: Int get() = host.clientWidth
    val clientHeight: Int get() = host.clientHeight

    // ===================== 状態 =====================
    val enabled: Boolean get() = true
    val isActive: Boolean get() = host.isActive
    val isRunning: Boolean get() = state == ConsoleState.Initializing || state == ConsoleState.Running || runERBFromMemory
    val isInProcess: Boolean get() = state == ConsoleState.Initializing || state == ConsoleState.Sleep || inProcess || state == ConsoleState.Running || runERBFromMemory
    val isError: Boolean get() = state == ConsoleState.Error
    val isWaitingEnterKey: Boolean
        get() {
            if (state == ConsoleState.Quit || state == ConsoleState.Error) return true
            if (state == ConsoleState.WaitInput) return inputReq!!.inputType == InputType.AnyKey || inputReq!!.inputType == InputType.EnterKey
            return false
        }
    val isWaitAnyKey: Boolean get() = state == ConsoleState.WaitInput && inputReq!!.inputType == InputType.AnyKey
    val isWaintingOnePhrase: Boolean get() = state == ConsoleState.WaitInput && inputReq!!.oneInput
    val isRunningTimer: Boolean get() = state == ConsoleState.WaitInput && inputReq!!.timelimit > 0 && !isTimeOut
    val isWaitingPrimitive: Boolean get() = state == ConsoleState.WaitInput && inputReq!!.inputType == InputType.PrimitiveMouseKey
    /** 入力待ちで値 (数値/文字列) を求めているか */
    val isWaitingValue: Boolean get() = state == ConsoleState.WaitInput && inputReq!!.needValue
    val consoleState: ConsoleState get() = state
    val currentInputRequest: InputRequest? get() = if (state == ConsoleState.WaitInput) inputReq else null

    val selectedString: String?
        get() {
            val sb = selectingButton ?: return null
            if (state == ConsoleState.Error) return sb.inputs
            if (state != ConsoleState.WaitInput) return null
            if (inputReq!!.inputType == InputType.IntValue && sb.isInteger) return sb.input.toString()
            if (inputReq!!.inputType == InputType.StrValue) return sb.inputs
            return null
        }

    fun initialize() {
        GlobalStatic.Console = this
        emuera = Process(this)
        GlobalStatic.Process = emuera
        clearDisplay()
        if (!emuera.initialize()) {
            state = ConsoleState.Error
            outputLog(null)
            printFlush(false)
            refreshStrings(true)
            return
        }
        callEmueraProgram("")
        refreshStrings(true)
    }

    fun quit() { state = ConsoleState.Quit }

    fun throwTitleError(error: Boolean) {
        state = ConsoleState.Error
        notToTitle = true
        byError = error
    }

    fun throwError(@Suppress("UNUSED_PARAMETER") playSound: Boolean) {
        forceUpdateGeneration()
        useUserStyle = false
        printFlush(false)
        refreshStrings(false)
        state = ConsoleState.Error
    }

    var notToTitle = false
    var byError = false

    // ===================== button =====================
    private var lastButtonIsInput = true
    var updatedGeneration = false
    private var lastButtonGeneration: Long = 0
    var newButtonGeneration: Long = 0
        private set

    fun updateGeneration() { lastButtonGeneration = newButtonGeneration; updatedGeneration = true }
    fun forceUpdateGeneration() { newButtonGeneration++; lastButtonGeneration = newButtonGeneration; updatedGeneration = true }
    private var lastInputLine: LogicalLine? = null

    private fun newGeneration() {
        val req = inputReq
        if (state != ConsoleState.WaitInput || req == null || !req.needValue) return
        if (!updatedGeneration && emuera.getCurrentLine !== lastInputLine) lastButtonGeneration = newButtonGeneration
        else updatedGeneration = false
        lastInputLine = emuera.getCurrentLine
        if (req.inputType == InputType.IntValue) {
            if (lastButtonGeneration == newButtonGeneration) newButtonGeneration++
            else if (!lastButtonIsInput) lastButtonGeneration = newButtonGeneration
            lastButtonIsInput = true
        }
        if (req.inputType == InputType.StrValue) {
            if (lastButtonGeneration == newButtonGeneration) newButtonGeneration++
            else if (lastButtonIsInput) lastButtonGeneration = newButtonGeneration
            lastButtonIsInput = false
        }
    }

    var selectingButton: ConsoleButtonString? = null
        private set
    private var lastSelectingButton: ConsoleButtonString? = null
    fun buttonIsSelected(button: ConsoleButtonString): Boolean = selectingButton === button
    var pointingString: ConsoleButtonString? = null
        private set

    /** このボタンが現在選択可能か (世代・入力種別を満たすか) */
    fun canSelect(button: ConsoleButtonString?): Boolean {
        if (button == null || !button.isButton) return false
        if (state == ConsoleState.Error) return true
        if (!(state == ConsoleState.WaitInput && inputReq!!.needValue)) return false
        if (button.generation != lastButtonGeneration) return false
        if (inputReq!!.inputType == InputType.IntValue && !button.isInteger) return false
        return true
    }

    // ===================== Input & Timer =====================
    private var inputReq: InputRequest? = null

    fun await(time: Int) {
        if (state != ConsoleState.Running) { quit(); return }
        refreshStrings(true)
        state = ConsoleState.Sleep
        emuera.updateCheckInfiniteLoopState()
        if (time > 0) Thread.sleep(time.toLong()) else Thread.sleep(1)
        state = ConsoleState.Running
    }

    fun waitInput(req: InputRequest) {
        state = ConsoleState.WaitInput
        inputReq = req
        if (req.timelimit > 0) presetTimer()
    }

    fun readAnyKey(anykey: Boolean = false, stopMesskip: Boolean = false) {
        val req = InputRequest()
        req.inputType = if (!anykey) InputType.EnterKey else InputType.AnyKey
        req.stopMesskip = stopMesskip
        inputReq = req
        state = ConsoleState.WaitInput
        emuera.needWaitToEventComEnd = false
    }

    private var redrawTimerInterval = 0L
    private var redrawTimerNext = 0L

    fun setRedrawTimer(tickcountIn: Int) {
        if (tickcountIn <= 0) { redrawTimerInterval = 0; return }
        redrawTimerInterval = maxOf(10, tickcountIn).toLong()
        redrawTimerNext = System.currentTimeMillis() + redrawTimerInterval
    }

    /** アニメーション用の再描画間隔 (0 なら無効) */
    val redrawInterval: Long get() = redrawTimerInterval

    private var timerEnabled = false
    private var timerID: Long = -1
    private var timerStartTime: Long = 0
    private var timerNextDisplayTime: Long = 0
    private var timerEndTime: Long = 0
    var isTimeOut = false
        private set
    private var needSettimer = false

    private fun presetTimer() {
        needSettimer = true
        if (inputReq!!.displayTime) {
            val start = inputReq!!.timelimit / 100
            printSingleLine("残り " + (start.toDouble() / 10.0).toString().removeSuffix(".0"))
        }
    }

    private fun setTimer() {
        isTimeOut = false
        timerID = inputReq!!.id
        timerEnabled = true
        timerStartTime = System.currentTimeMillis()
        timerEndTime = timerStartTime + inputReq!!.timelimit
        timerNextDisplayTime = timerStartTime + 100
    }

    /** UI から定期的 (50ms 程度) にエンジンスレッド上で呼ぶ。タイマー入力を処理する */
    fun tick() {
        if (needSettimer) {
            needSettimer = false
            setTimer()
        }
        if (!timerEnabled) return
        val req = inputReq
        if (state != ConsoleState.WaitInput || req == null || req.timelimit <= 0 || timerID != req.id) {
            timerEnabled = false
            return
        }
        val curtime = System.currentTimeMillis()
        if (curtime >= timerEndTime) { endTimer(); return }
        if (req.displayTime && curtime >= timerNextDisplayTime) {
            timerNextDisplayTime = curtime + 100
            val time = (timerEndTime - curtime) / 100
            changeLastLine("残り " + (time.toDouble() / 10.0).toString().removeSuffix(".0"))
            refreshStrings(true)
        }
    }

    private fun stopTimer() { timerEnabled = false }

    private fun endTimer() {
        stopTimer()
        isTimeOut = true
        if (isWaitingPrimitive) { inputMouseKey(4, 0, 0, 0, 0); return }
        val req = inputReq!!
        if (req.displayTime) changeLastLine(req.timeUpMes ?: "")
        else if (req.timeUpMes != null) printSingleLine(req.timeUpMes!!)
        callEmueraProgram("")
        refreshStrings(true)
    }

    fun forceStopTimer() { timerEnabled = false }

    // ===================== Call =====================
    private fun callEmueraProgram(str: String?) {
        if (str != null) {
            if (!doInputToEmueraProgram(str)) return
            if (state == ConsoleState.Error) return
        }
        state = ConsoleState.Running
        emuera.doScript()
        if (state == ConsoleState.Running) {
            state = ConsoleState.Error
            printError("emueraのエラー：プログラムの状態を特定できません")
        }
        if (state == ConsoleState.Error && !noOutputLog) outputLog(Program.ExeDir + "emuera.log")
        printFlush(false)
        newGeneration()
    }

    private fun doInputToEmueraProgram(strIn: String): Boolean {
        var str: String? = strIn
        if (state == ConsoleState.WaitInput) {
            val req = inputReq!!
            when (req.inputType) {
                InputType.IntValue -> {
                    val inputValue: Long
                    if (str.isNullOrEmpty() && req.hasDefValue && !isRunningTimer) {
                        inputValue = req.defIntValue
                        str = inputValue.toString()
                    } else inputValue = str?.trim()?.toLongOrNull() ?: return false
                    if (req.isSystemInput) emuera.inputSystemInteger(inputValue) else emuera.inputInteger(inputValue)
                }
                InputType.StrValue -> {
                    if (str.isNullOrEmpty() && req.hasDefValue && !isRunningTimer) str = req.defStrValue
                    if (str == null) str = ""
                    emuera.inputString(str)
                }
                else -> {}
            }
            stopTimer()
        }
        print(str ?: "")
        printFlush(false)
        return true
    }

    // ===================== 入力系 =====================
    var mesSkip = false
    private var inProcess = false
    @Volatile var killMacro = false

    fun inputMouseKey(type: Int, result1: Int, result2: Int, result3: Int, result4: Int) {
        emuera.inputResult5(type, result1, result2, result3, result4)
        inProcess = true
        try {
            callEmueraProgram(null)
        } finally {
            inProcess = false
        }
        refreshStrings(true)
    }

    /** INPUTMOUSEKEY 待ち中のタップ (座標は画面左下原点ではなく Emuera 準拠: y は負値) */
    fun mouseDown(x: Int, yFromBottom: Int, button: Int) {
        if (!isWaitingPrimitive) return
        var buttonNum = -1
        val map = cbgButtonMap
        if (map != null && map.isCreated) {
            val mx = x
            val my = yFromBottom + map.height
            if (mx >= 0 && my >= 0 && mx < map.width && my < map.height) {
                val c = map.gGetColor(mx, my)
                if ((c ushr 24) == 255) buttonNum = c and 0xFFFFFF
            }
        }
        inputMouseKey(1, button, x, yFromBottom, buttonNum)
    }

    fun pressPrimitiveKey(keycode: Int, keydata: Int) {
        if (isWaitingPrimitive) inputMouseKey(3, keycode, keydata, 0, 0)
    }

    fun pressEnterKey(keySkip: Boolean, strIn: String?, changedByMouse: Boolean) {
        var str = strIn ?: ""
        mesSkip = keySkip
        when (state) {
            ConsoleState.Running, ConsoleState.Initializing -> return
            ConsoleState.Quit -> { host.closeGame(); return }
            ConsoleState.Error -> {
                if (str == ErrorButtonsText) return
                host.closeGame()
                return
            }
            else -> {}
        }
        val req = inputReq ?: return
        killMacro = false
        try {
            val text: List<String>
            if (changedByMouse) text = listOf(str)
            else {
                if (str.startsWith("@") && str.length > 1 && !req.oneInput) {
                    doSystemCommand(str)
                    return
                }
                if (req.inputType == InputType.Void) return
                if (timerEnabled && (req.inputType == InputType.AnyKey || req.inputType == InputType.EnterKey)) stopTimer()
                if (str.contains("(")) str = parseInput(StringStream(str), false)
                text = str.split("\\n", "\r\n", "\n", "\r")
            }
            inProcess = true
            var i = 0
            while (i < text.size) {
                var inputs = text[i]
                if (inputs.indexOf("\\e") >= 0) {
                    inputs = inputs.replace("\\e", "")
                    mesSkip = true
                }
                val cur = inputReq!!
                if (cur.oneInput && (!Config.AllowLongInputByMouse || !changedByMouse) && inputs.length > 1) inputs = inputs.substring(0, 1)
                if (cur.inputType == InputType.Void) {
                    i--
                    inputs = ""
                }
                callEmueraProgram(inputs)
                refreshStrings(false)
                while (mesSkip && state == ConsoleState.WaitInput) {
                    if (inputReq!!.needValue) break
                    if (inputReq!!.stopMesskip) break
                    callEmueraProgram("")
                    refreshStrings(false)
                }
                mesSkip = false
                if (state != ConsoleState.WaitInput) break
                if (killMacro) break
                i++
            }
        } finally {
            inProcess = false
        }
        refreshStrings(true)
    }

    /** ボタンをタップしたとき (UI から) */
    fun clickButton(button: ConsoleButtonString?) {
        if (button != null && canSelect(button)) {
            selectingButton = button
            pressEnterKey(false, selectedString, true)
        } else pressEnterKey(false, "", true)
    }

    /** CBG ボタンをタップ */
    fun clickCBGButton(value: Int) {
        if (state == ConsoleState.WaitInput && inputReq!!.needValue) pressEnterKey(false, value.toString(), true)
    }

    private fun parseInput(st: StringStream, isNest: Boolean): String {
        val sb = StringBuilder(20)
        val num = StringBuilder(20)
        var hasRet = false
        while (!st.eos && (!isNest || st.current != ')')) {
            if (st.current == '(') {
                st.shiftNext()
                val tstr = parseInput(st, true)
                if (!st.eos) {
                    st.shiftNext()
                    if (st.current == '*') {
                        st.shiftNext()
                        while (st.current.isDigit()) { num.append(st.current); st.shiftNext() }
                        if (num.isNotEmpty()) {
                            val res = num.toString().toIntOrNull() ?: 0
                            repeat(res) { sb.append(tstr) }
                            num.setLength(0)
                        }
                    } else sb.append(tstr)
                    continue
                } else {
                    sb.append(tstr)
                    break
                }
            } else if (st.current == '\\') {
                st.shiftNext()
                when (st.current) {
                    'n' -> if (!hasRet) sb.append('\n') else hasRet = false
                    'r' -> sb.append('\r')
                    'e' -> { sb.append("\\e\n"); hasRet = true }
                    '\n' -> {}
                    else -> sb.append(st.current)
                }
            } else sb.append(st.current)
            st.shiftNext()
        }
        return sb.toString()
    }

    var runERBFromMemory = false

    private fun doSystemCommand(command: String) {
        if (timerEnabled) {
            printError("タイマー系命令の待ち時間中はコマンドを入力できません")
            refreshStrings(true)
            return
        }
        print(command)
        printFlush(false)
        refreshStrings(true)
        val com = command.substring(1)
        if (com.isEmpty()) return
        when {
            com.equals("REBOOT", true) -> { host.reboot(); return }
            com.equals("OUTPUT", true) || com.equals("OUTPUTLOG", true) -> { outputLog(Program.ExeDir + "emuera.log"); return }
            com.equals("QUIT", true) || com.equals("EXIT", true) -> { host.closeGame(); return }
            else -> {
                if (!Config.UseDebugCommand) {
                    printError("デバッグコマンドを使用できない設定になっています")
                    refreshStrings(true)
                    return
                }
                debugCommand(com, Config.ChangeMasterNameIfDebug, false)
                printFlush(false)
            }
        }
        refreshStrings(true)
    }

    // ===================== 描画系 =====================
    private var lastUpdate = 0L
    var redraw = ConsoleRedraw.Normal
        private set

    fun setRedraw(i: Long) {
        redraw = if ((i and 1) == 0L) ConsoleRedraw.None else ConsoleRedraw.Normal
        if ((i and 2) != 0L) refreshStrings(true)
    }

    private var windowTitle = "Emuera"
    fun setWindowTitle(str: String?) {
        windowTitle = str ?: ""
        host.setWindowTitle(windowTitle)
    }
    fun getWindowTitle(): String = windowTitle

    @Volatile var displayVersion: Long = 0
        private set

    fun refreshStrings(forcePaint: Boolean) {
        if (redraw == ConsoleRedraw.None && !forcePaint) return
        val sb = selectingButton
        if (sb != null) {
            if (state != ConsoleState.Error && state != ConsoleState.WaitInput) selectingButton = null
            else if (state == ConsoleState.WaitInput && !inputReq!!.needValue) selectingButton = null
            else if (sb.generation != lastButtonGeneration) selectingButton = null
        }
        if (!forcePaint) {
            if (lastDrawnLineNo == lineNo && lastSelectingButton === selectingButton) return
            val now = System.currentTimeMillis()
            if (now - lastUpdate < msPerFrame && (state == ConsoleState.Running || state == ConsoleState.Initializing)) return
        }
        lastUpdate = System.currentTimeMillis()
        lastDrawnLineNo = lineNo
        lastSelectingButton = selectingButton
        displayVersion++
        host.requestRedraw()
    }

    /** UI 用のスナップショット */
    class Snapshot(
        val lines: List<ConsoleDisplayLine>,
        val cbg: List<ClientBackGroundImage>,
        val bgColor: EraColor,
        val selectingButton: ConsoleButtonString?,
        val selectingCBGButton: Int,
        val version: Long,
    )

    fun snapshot(): Snapshot = synchronized(lock) {
        Snapshot(ArrayList(displayLineList), ArrayList(cbgList), bgColor, selectingButton, selectingCBGButtonInt, displayVersion)
    }

    fun setToolTipColor(fore: EraColor, back: EraColor) = host.setToolTipColor(fore, back)
    fun setToolTipDelay(delay: Int) = host.setToolTipDelay(delay)
    fun setToolTipDuration(duration: Int) = host.setToolTipDuration(duration)
    fun clearTextBox() = host.clearTextBox()
    fun confirmInfiniteLoop(message: String): Boolean = host.confirmInfiniteLoop(message)
    fun reportLoadProgress(text: String) = host.reportLoadProgress(text)

    // ===================== Debug =====================
    private val dConsoleLog = StringBuilder()
    val debugConsoleLog: String get() = dConsoleLog.toString()
    private val dTraceLogList = ArrayList<String>()

    fun debugPrint(str: String) { if (Program.DebugMode) dConsoleLog.append(str) }
    fun debugClear() { dConsoleLog.setLength(0) }
    fun debugNewLine() { if (Program.DebugMode) dConsoleLog.append('\n') }
    fun debugAddTraceLog(str: String) { if (Program.DebugMode && !runERBFromMemory) dTraceLogList.add(str) }
    fun debugRemoveTraceLog() { if (Program.DebugMode && !runERBFromMemory && dTraceLogList.isNotEmpty()) dTraceLogList.removeAt(dTraceLogList.size - 1) }
    fun debugClearTraceLog() { if (Program.DebugMode && !runERBFromMemory) dTraceLogList.clear() }

    fun debugCommand(comIn: String, munchkin: Boolean, outputDebugConsole: Boolean) {
        var com = comIn
        val tempState = state
        runERBFromMemory = true
        GlobalStatic.Process!!.saveCurrentState(false)
        try {
            var line: LogicalLine? = null
            if (!com.startsWith("@") && !com.startsWith("\"") && !com.startsWith("\\")) line = LogicalLineParser.parseLine(com, null)
            if (line == null || line is InvalidLine) {
                val wc = LexicalAnalyzer.analyse(StringStream(com), LexEndWith.EoL, LexAnalyzeFlag.None)
                val term = ExpressionParser.reduceExpressionTerm(wc, TermEndWith.EoL) ?: throw CodeEE("解釈不能なコードです")
                com = if (term.getOperandType() == EType.Int64) {
                    if (outputDebugConsole) "DEBUGPRINTFORML {$com}" else "PRINTVL $com"
                } else {
                    if (outputDebugConsole) "DEBUGPRINTFORML %$com%" else "PRINTFORMSL $com"
                }
                line = LogicalLineParser.parseLine(com, null)
            }
            if (line == null) throw CodeEE("解釈不能なコードです")
            if (line is InvalidLine) throw CodeEE(line.errMes)
            if (line !is InstructionLine) throw CodeEE("デバッグコマンドで使用できるのは代入文か命令文だけです")
            val func = line
            if (func.function.isFlowContorol()) throw CodeEE("フロー制御命令は使用できません")
            if (func.function.isWaitInput() || !func.function.isMethodSafe() || func.function.isPartial()) throw CodeEE(func.function.name + "命令は使用できません")
            when (func.functionCode) {
                FunctionCode.PUTFORM, FunctionCode.UPCHECK, FunctionCode.CUPCHECK, FunctionCode.SAVEDATA -> throw CodeEE(func.function.name + "命令は使用できません")
                else -> {}
            }
            ArgumentParser.setArgumentTo(func)
            if (func.isError) throw CodeEE(func.errMes)
            emuera.doDebugNormalFunction(func, munchkin)
            if (func.functionCode == FunctionCode.SET && !outputDebugConsole) printSingleLine(com)
        } catch (e: Exception) {
            if (outputDebugConsole) { debugPrint(e.message ?: ""); debugNewLine() } else printError(e.message ?: e.toString())
            emuera.clearMethodStack()
        } finally {
            GlobalStatic.Process!!.loadPrevState()
            runERBFromMemory = false
            state = tempState
        }
    }

    // ===================== マウス =====================
    private var mousePos = EPoint(0, 0)
    /** UI からマウス/指の位置を知らせる (x, 画面下端からの y(負)) */
    fun setMousePosition(x: Int, yFromBottom: Int) { mousePos = EPoint(x, yFromBottom) }
    fun getMousePosition(): EPoint = mousePos

    fun gotoTitle() {
        forceStopTimer()
        clearDisplay()
        AppContents.unloadGraphicList()
        redraw = ConsoleRedraw.Normal
        useUserStyle = false
        userStyle = StringStyle(Config.ForeColor, FontStyle.Regular, null)
        emuera.beginTitle()
        readAnyKey(false, false)
        callEmueraProgram("")
        refreshStrings(true)
    }

    private var forceTemporary = false
    private var prevState = ConsoleState.Initializing
    private var prevReq: InputRequest? = null

    fun reloadErbFinished() {
        state = prevState
        inputReq = prevReq
        printSingleLine(" ")
    }

    // ===================== Print =====================
    var noOutputLog = false
    var bgColor: EraColor = Config.BackColor
        @JvmName("setBgColorField") private set

    fun clearDisplay() {
        synchronized(lock) { displayLineList.clear() }
        logicalLineCount = 0
        lineNo = 0
        lastDrawnLineNo = -1
        displayVersion++
        host.requestRedraw()
    }

    var useUserStyle = false
    var useSetColorStyle = false
    private val defaultStyle get() = StringStyle(Config.ForeColor, FontStyle.Regular, null)
    private var userStyle = StringStyle(Config.ForeColor, FontStyle.Regular, null)

    private val style: StringStyle
        get() {
            if (!useUserStyle) return defaultStyle
            if (useSetColorStyle) return userStyle
            if (userStyle.color == defaultStyle.color) return userStyle
            return StringStyle(defaultStyle.color, userStyle.fontStyle, userStyle.fontname)
        }

    val stringStyle: StringStyle get() = userStyle
    fun setStringStyle(fs: Int) { userStyle = userStyle.copy(fontStyle = fs) }
    fun setStringStyle(color: EraColor) { userStyle = userStyle.copy(color = color, colorChanged = color != Config.ForeColor) }
    fun setFont(fontname: String?) { userStyle = userStyle.copy(fontnameIn = if (!fontname.isNullOrEmpty()) fontname else Config.FontName) }
    var alignment = DisplayLineAlignment.LEFT

    fun resetStyle() {
        userStyle = defaultStyle
        alignment = DisplayLineAlignment.LEFT
    }

    val emptyLine: Boolean get() = printBuffer.isEmpty
    private var stBar: String? = null

    fun setBgColor(color: EraColor) {
        bgColor = color
        if (redraw == ConsoleRedraw.None) return
        refreshStrings(true)
    }

    private var lastDrawnLineNo = -1
    private var lineNo = 0
    var lineCount: Long = 0
        private set
    private var logicalLineCount: Long
        get() = lineCount
        set(v) { lineCount = v }

    private fun addRangeDisplayLine(lineList: Array<ConsoleDisplayLine>) { for (l in lineList) addDisplayLine(l, false) }

    private fun addDisplayLine(line: ConsoleDisplayLine, forceLEFT: Boolean) {
        if (lastLineIsTemporary) deleteLine(1)
        if (forceLEFT) line.setAlignment(DisplayLineAlignment.LEFT) else line.setAlignment(alignment)
        line.lineNo = lineNo
        synchronized(lock) {
            displayLineList.add(line)
            if (displayLineList.size > Config.MaxLog) displayLineList.removeAt(0)
        }
        lineNo++
        if (line.isLogicalLine) logicalLineCount++
        if (lineNo == Int.MAX_VALUE) { lastDrawnLineNo = -1; lineNo = 0 }
        if (logicalLineCount == Long.MAX_VALUE) logicalLineCount = 0
    }

    fun deleteLine(argNum: Int) {
        var delNum = 0
        synchronized(lock) {
            while (delNum < argNum) {
                if (displayLineList.isEmpty()) break
                val line = displayLineList.removeAt(displayLineList.size - 1)
                lineNo--
                if (line.isLogicalLine) { delNum++; logicalLineCount-- }
            }
        }
        if (lineNo < 0) lineNo += Int.MAX_VALUE
        lastDrawnLineNo = -1
    }

    val lastLineIsTemporary: Boolean get() = synchronized(lock) { displayLineList.lastOrNull()?.isTemporary ?: false }
    val lastLineIsEmpty: Boolean get() = synchronized(lock) { displayLineList.lastOrNull()?.toString()?.trim()?.isEmpty() ?: false }

    fun printTemporaryLine(str: String) = printSingleLine(str, true)

    private fun changeLastLine(str: String) {
        deleteLine(1)
        printSingleLine(str, false)
    }

    fun printWarning(str: String, position: ScriptPosition?, level: Int) {
        if (level < Config.DisplayWarningLevel && !Program.AnalysisMode) return
        val b = forceTemporary
        forceTemporary = false
        if (position != null) {
            if (position.lineNo >= 0) {
                printErrorButton("警告Lv$level:${position.filename}:${position.lineNo}行目:$str", position)
                GlobalStatic.Process?.printRawLine(position)
            } else printErrorButton("警告Lv$level:${position.filename}:$str", position)
        } else printError("警告Lv$level:$str")
        forceTemporary = b
    }

    fun printSystemLine(str: String) {
        printFlush(false)
        useUserStyle = false
        printSingleLine(str, false)
    }

    fun printError(str: String?) {
        if (str.isNullOrEmpty()) return
        if (Program.DebugMode) { debugPrint(str); debugNewLine() }
        printFlush(false)
        useUserStyle = false
        val dispLine = printPlainwithSingleLine(str) ?: return
        addDisplayLine(dispLine, true)
        refreshStrings(false)
    }

    fun printErrorButton(str: String?, pos: ScriptPosition?) {
        if (str.isNullOrEmpty()) return
        if (Program.DebugMode) { debugPrint(str); debugNewLine() }
        useUserStyle = false
        val dispLine = printBuffer.appendAndFlushErrButton(str, style, ErrorButtonsText, pos, stringMeasure)
        addDisplayLine(dispLine, true)
        refreshStrings(false)
    }

    fun printSingleLine(str: String?) = printSingleLine(str, false)

    fun printSingleLine(str: String?, temporary: Boolean) {
        if (str.isNullOrEmpty()) return
        printFlush(false)
        printBuffer.append(str, style)
        val dispLine = bufferToSingleLine(true, temporary) ?: return
        addDisplayLine(dispLine, false)
        refreshStrings(false)
    }

    fun print(str: String?) {
        if (str.isNullOrEmpty()) return
        var s: String = str
        while (true) {
            val newline = s.indexOf('\n')
            if (newline < 0) break
            printBuffer.append(s.substring(0, newline), style)
            newLine()
            if (newline >= s.length - 1) return
            s = s.substring(newline + 1)
        }
        printBuffer.append(s, style)
    }

    fun printImg(str: String) = printBuffer.append(ConsoleImagePart(str, null, 0, 0, 0))

    fun printShape(type: String, param: IntArray) =
        printBuffer.append(ConsoleShapePart.createShape(type, param, userStyle.color, userStyle.buttonColor, false))

    fun printHtml(str: String?) {
        if (str.isNullOrEmpty()) return
        if (!printBuffer.isEmpty) addRangeDisplayLine(printBuffer.flush(stringMeasure, forceTemporary))
        addRangeDisplayLine(HtmlManager.html2DisplayLine(str, stringMeasure, this))
        refreshStrings(false)
    }

    private var printCWidth = -1
    private var printCWidthL = -1

    fun printC(str: String?, alignmentRight: Boolean) {
        if (str.isNullOrEmpty()) return
        printBuffer.appendC(createTypeCString(str, alignmentRight), style)
    }

    private var printCWidthFor = -1

    private fun calcPrintCWidth() {
        printCWidthFor = Config.PrintCLength
        var s = " ".repeat(Config.PrintCLength)
        val font = configFont()
        printCWidth = stringMeasure.getDisplayLength(s, font)
        s += " "
        printCWidthL = stringMeasure.getDisplayLength(s, font)
    }

    private fun createTypeCString(strIn: String, alignmentRight: Boolean): String {
        var str = strIn
        if (printCWidth == -1 || printCWidthFor != Config.PrintCLength) calcPrintCWidth()
        val length = LangManager.getStrlenLang(str)
        val printcLength = Config.PrintCLength
        val font = com.eraandroid.emuera.platform.EFont(style.fontname, Config.FontSize, style.fontStyle)
        if (alignmentRight && length < printcLength) {
            str = " ".repeat(printcLength - length) + str
            var width = stringMeasure.getDisplayLength(str, font)
            while (width > printCWidth) {
                if (str[0] != ' ') break
                str = str.substring(1)
                width = stringMeasure.getDisplayLength(str, font)
            }
        } else if (!alignmentRight && length < printcLength + 1) {
            str += " ".repeat(printcLength + 1 - length)
            var width = stringMeasure.getDisplayLength(str, font)
            while (width > printCWidthL) {
                if (str[str.length - 1] != ' ') break
                str = str.substring(0, str.length - 1)
                width = stringMeasure.getDisplayLength(str, font)
            }
        }
        return str
    }

    fun printButton(str: String?, p: String) { if (!str.isNullOrEmpty()) printBuffer.appendButton(str, style, p) }
    fun printButton(str: String?, p: Long) { if (!str.isNullOrEmpty()) printBuffer.appendButton(str, style, p) }
    fun printButtonC(str: String?, p: String, isRight: Boolean) { if (!str.isNullOrEmpty()) printBuffer.appendButtonC(createTypeCString(str, isRight), style, p) }
    fun printButtonC(str: String?, p: Long, isRight: Boolean) { if (!str.isNullOrEmpty()) printBuffer.appendButtonC(createTypeCString(str, isRight), style, p) }
    fun printPlain(str: String?) { if (!str.isNullOrEmpty()) printBuffer.appendPlainText(str, style) }

    fun newLine() {
        printFlush(true)
        refreshStrings(false)
    }

    fun bufferToSingleLine(force: Boolean, temporary: Boolean): ConsoleDisplayLine? {
        if (!force && printBuffer.isEmpty) return null
        if (force && printBuffer.isEmpty) printBuffer.append(" ", style)
        return printBuffer.flushSingleLine(stringMeasure, temporary or forceTemporary)
    }

    fun printPlainwithSingleLine(str: String?): ConsoleDisplayLine? {
        if (str.isNullOrEmpty()) return null
        printBuffer.appendPlainText(str, style)
        return printBuffer.flushSingleLine(stringMeasure, false)
    }

    fun printFlush(force: Boolean) {
        if (!force && printBuffer.isEmpty) return
        if (force && printBuffer.isEmpty) printBuffer.append(" ", style)
        addRangeDisplayLine(printBuffer.flush(stringMeasure, forceTemporary))
    }

    fun printBar() {
        val ss = userStyle
        userStyle = userStyle.copy(fontStyle = FontStyle.Regular)
        print(stBar)
        userStyle = ss
    }

    fun printCustomBar(barStr: String?, isConst: Boolean) {
        if (barStr.isNullOrEmpty()) throw CodeEE("空文字列によるDRAWLINEが行われました")
        val ss = userStyle
        userStyle = userStyle.copy(fontStyle = FontStyle.Regular)
        if (isConst) print(barStr) else print(getStBar(barStr))
        userStyle = ss
    }

    fun getDefStBar(): String? = stBar

    fun getStBar(barStr: String): String {
        val bar = StringBuilder()
        bar.append(barStr)
        var width = 0
        val font = configFont()
        var guard = 0
        while (width < Config.DrawableWidth && guard++ < 10000) {
            bar.append(barStr)
            width = stringMeasure.getDisplayLength(bar.toString(), font)
        }
        while (width > Config.DrawableWidth && bar.isNotEmpty()) {
            bar.setLength(bar.length - 1)
            width = stringMeasure.getDisplayLength(bar.toString(), font)
        }
        return bar.toString()
    }

    fun setStBar(barStr: String) { stBar = getStBar(barStr) }

    fun outputLog(filenameIn: String?): Boolean {
        val filename = filenameIn ?: (Program.ExeDir + "emuera.log")
        return try {
            val sb = StringBuilder()
            synchronized(lock) { for (l in displayLineList) sb.append(l.toString()).append("\r\n") }
            File(filename).writeText("\uFEFF" + sb.toString(), Charsets.UTF_16LE)
            printSystemLine("※※※ログファイルを" + filename.replace(Program.ExeDir, "") + "に出力しました※※※")
            refreshStrings(true)
            true
        } catch (e: Exception) { false }
    }

    fun getDisplayStrings(builder: StringBuilder) {
        synchronized(lock) { for (l in displayLineList) builder.append(l.toString()).append('\n') }
    }

    fun getDisplayLines(lineNo: Long): Array<ConsoleDisplayLine>? {
        synchronized(lock) {
            if (lineNo < 0 || lineNo > displayLineList.size) return null
            var count = 0L
            val list = ArrayList<ConsoleDisplayLine>()
            for (i in displayLineList.indices.reversed()) {
                if (count == lineNo) list.add(0, displayLineList[i])
                if (displayLineList[i].isLogicalLine) count++
                if (count > lineNo) break
            }
            if (list.isEmpty()) return null
            return list.toTypedArray()
        }
    }

    fun popDisplayingLines(): Array<ConsoleDisplayLine>? {
        if (printBuffer.isEmpty) return null
        return printBuffer.flush(stringMeasure, forceTemporary)
    }

    companion object {
        const val ErrorButtonsText = "__openFileWithDebug__"
    }
}
