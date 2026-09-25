package com.eraandroid.emuera.gameproc

import com.eraandroid.emuera.GlobalStatic
import com.eraandroid.emuera.Program
import com.eraandroid.emuera.gamedata.expression.EType
import com.eraandroid.emuera.gamedata.expression.ExpressionMediator
import com.eraandroid.emuera.gamedata.expression.SingleTerm
import com.eraandroid.emuera.gamedata.variable.ReferenceToken
import com.eraandroid.emuera.gameview.EmueraConsole
import com.eraandroid.emuera.sub.CodeEE

@Suppress("unused")
object SystemStateCode {
    const val __CAN_SAVE__ = 0x10000
    const val __CAN_BEGIN__ = 0x20000
    const val Title_Begin = 0
    const val Openning = 1
    const val Train_Begin = 0x10
    const val Train_CallEventTrain = 0x11
    const val Train_CallShowStatus = 0x12
    const val Train_CallComAbleXX = 0x13
    const val Train_CallShowUserCom = 0x14
    const val Train_WaitInput = 0x15
    const val Train_CallEventCom = 0x16 or __CAN_BEGIN__
    const val Train_CallComXX = 0x17 or __CAN_BEGIN__
    const val Train_CallSourceCheck = 0x18 or __CAN_BEGIN__
    const val Train_CallEventComEnd = 0x19 or __CAN_BEGIN__
    const val Train_DoTrain = 0x1A
    const val AfterTrain_Begin = 0x20 or __CAN_BEGIN__
    const val Ablup_Begin = 0x30
    const val Ablup_CallShowJuel = 0x31
    const val Ablup_CallShowAblupSelect = 0x32
    const val Ablup_WaitInput = 0x33
    const val Ablup_CallAblupXX = 0x34 or __CAN_BEGIN__
    const val Turnend_Begin = 0x40 or __CAN_BEGIN__
    const val Shop_Begin = 0x50 or __CAN_SAVE__
    const val Shop_CallEventShop = 0x51 or __CAN_BEGIN__ or __CAN_SAVE__
    const val Shop_CallShowShop = 0x52 or __CAN_SAVE__
    const val Shop_WaitInput = 0x53 or __CAN_SAVE__
    const val Shop_CallEventBuy = 0x54 or __CAN_BEGIN__ or __CAN_SAVE__
    const val SaveGame_Begin = 0x100
    const val SaveGame_WaitInput = 0x101
    const val SaveGame_WaitInputOverwrite = 0x102
    const val SaveGame_CallSaveInfo = 0x103
    const val LoadGame_Begin = 0x110
    const val LoadGame_WaitInput = 0x111
    const val LoadGameOpenning_Begin = 0x120
    const val LoadGameOpenning_WaitInput = 0x121
    const val AutoSave_CallSaveInfo = 0x201
    const val AutoSave_CallUniqueAutosave = 0x202
    const val AutoSave_Skipped = 0x203
    const val LoadData_DataLoaded = 0x210
    const val LoadData_CallSystemLoad = 0x211 or __CAN_BEGIN__
    const val LoadData_CallEventLoad = 0x212 or __CAN_BEGIN__
    const val Openning_TitleLoadgame = 0x220
    const val System_Reloaderb = 0x230
    const val First_Begin = 0x240
    const val Normal = 0xFFFF or __CAN_BEGIN__ or __CAN_SAVE__
}

enum class BeginType { NULL, SHOP, TRAIN, AFTERTRAIN, ABLUP, TURNEND, FIRST, TITLE }

class ProcessState(console: EmueraConsole?) {
    private val console: EmueraConsole? = if (Program.DebugMode) console else null
    private val functionList = ArrayList<CalledFunction>()
    var currentLine: LogicalLine? = null
    var lineCount = 0
    var currentMin = 0
    val scriptEnd: Boolean get() = functionList.size == currentMin
    val functionCount: Int get() = functionList.size
    var systemState: Int = SystemStateCode.Title_Begin
    private var begintype = BeginType.NULL
    val isBegun: Boolean get() = begintype != BeginType.NULL
    val errorLine: LogicalLine? get() = currentLine
    val currentCalled: CalledFunction? get() = functionList.lastOrNull()

    fun shiftNextLine() {
        currentLine = currentLine!!.nextLine
        lineCount++
    }

    fun jumpTo(line: LogicalLine?) {
        currentLine = line
        lineCount++
    }

    fun setBegin(keyword: String) {
        when (keyword) {
            "SHOP" -> setBegin(BeginType.SHOP)
            "TRAIN" -> setBegin(BeginType.TRAIN)
            "AFTERTRAIN" -> setBegin(BeginType.AFTERTRAIN)
            "ABLUP" -> setBegin(BeginType.ABLUP)
            "TURNEND" -> setBegin(BeginType.TURNEND)
            "FIRST" -> setBegin(BeginType.FIRST)
            "TITLE" -> setBegin(BeginType.TITLE)
            else -> throw CodeEE("BEGINのキーワード\"$keyword\"は未定義です")
        }
    }

    fun setBegin(type: BeginType) {
        when (type) {
            BeginType.SHOP, BeginType.TRAIN, BeginType.AFTERTRAIN, BeginType.ABLUP, BeginType.TURNEND, BeginType.FIRST ->
                if ((systemState and SystemStateCode.__CAN_BEGIN__) != SystemStateCode.__CAN_BEGIN__)
                    throw CodeEE("@" + functionList[0].functionName + "中でBEGIN命令を実行することはできません")
            else -> {}
        }
        begintype = type
    }

    fun saveLoadData(saveData: Boolean) {
        systemState = if (saveData) SystemStateCode.SaveGame_Begin else SystemStateCode.LoadGame_Begin
    }

    fun clearFunctionList() {
        if (Program.DebugMode && !isClone && GlobalStatic.Process!!.methodStack() == 0) console?.debugClearTraceLog()
        for (called in functionList) called.currentLabel?.let { if (it.hasPrivDynamicVar) it.exit() }
        functionList.clear()
        begintype = BeginType.NULL
    }

    var calledWhenNormal = true

    fun begin() {
        if (systemState == SystemStateCode.Shop_CallEventShop) return
        when (begintype) {
            BeginType.SHOP -> {
                calledWhenNormal = systemState == SystemStateCode.Normal
                systemState = SystemStateCode.Shop_Begin
            }
            BeginType.TRAIN -> systemState = SystemStateCode.Train_Begin
            BeginType.AFTERTRAIN -> systemState = SystemStateCode.AfterTrain_Begin
            BeginType.ABLUP -> systemState = SystemStateCode.Ablup_Begin
            BeginType.TURNEND -> systemState = SystemStateCode.Turnend_Begin
            BeginType.FIRST -> systemState = SystemStateCode.First_Begin
            BeginType.TITLE -> systemState = SystemStateCode.Title_Begin
            BeginType.NULL -> {}
        }
        if (Program.DebugMode) {
            console?.debugClearTraceLog()
            console?.debugAddTraceLog("BEGIN:" + begintype.name)
        }
        for (called in functionList) called.currentLabel?.let { if (it.hasPrivDynamicVar) it.exit() }
        functionList.clear()
        begintype = BeginType.NULL
    }

    fun begin(type: BeginType) {
        begintype = type
        systemState = SystemStateCode.Title_Begin
        begin()
    }

    val getCurrentReturnAddress: LogicalLine?
        get() = if (functionList.size == currentMin) null else functionList[functionList.size - 1].returnAddress

    fun getReturnAddressSequensial(curerntDepth: Int): LogicalLine? =
        if (functionList.size == currentMin) null else functionList[functionList.size - curerntDepth - 1].returnAddress

    val scope: String? get() = functionList.lastOrNull()?.functionName

    fun `return`(ret: Long) {
        if (isFunctionMethod) { returnF(null); return }
        val called = functionList[functionList.size - 1]
        if (called.isJump) {
            if (called.topLabel.hasPrivDynamicVar) called.topLabel.exit()
            functionList.remove(called)
            if (Program.DebugMode) console?.debugRemoveTraceLog()
            `return`(ret)
            return
        }
        if (!called.isEvent) {
            if (called.topLabel.hasPrivDynamicVar) called.topLabel.exit()
            currentLine = null
        } else {
            if (called.currentLabel!!.hasPrivDynamicVar) called.currentLabel!!.exit()
            if (called.isOnly) called.finishEvent()
            else if (called.hasSingleFlag && ret == 1L) called.shiftNextGroup()
            else called.shiftNext()
            currentLine = called.currentLabel
            val cl = called.currentLabel
            if (cl != null) {
                lineCount++
                if (cl.hasPrivDynamicVar) cl.enter()
            }
        }
        if (Program.DebugMode) console?.debugRemoveTraceLog()
        if (currentLine == null) {
            currentLine = called.returnAddress
            functionList.removeAt(functionList.size - 1)
            if (currentLine == null) {
                if (begintype != BeginType.NULL) begin()
                return
            }
            lineCount++
            return
        } else if (Program.DebugMode) {
            val label = called.currentLabel!!
            console?.debugAddTraceLog("CALL :@" + label.labelName + ":" + label.position.toString() + "行目")
        }
        lineCount++
    }

    fun intoFunction(call: CalledFunction, srcArgs: UserDefinedFunctionArgument?, exm: ExpressionMediator?) {
        if (call.isEvent) {
            for (called in functionList) if (called.isEvent) throw CodeEE("EVENT関数の解決前にCALLEVENT命令が行われました")
        }
        if (Program.DebugMode) {
            val label = call.currentLabel!!
            console?.debugAddTraceLog((if (call.isJump) "JUMP :@" else "CALL :@") + label.labelName + ":" + label.position.toString() + "行目")
        }
        val top = call.topLabel
        if (srcArgs != null) {
            srcArgs.setTransporter(exm!!)
            if (top.hasPrivDynamicVar) top.enter()
            for (i in top.arg.indices) {
                val a = srcArgs.arguments[i] ?: continue
                if (top.arg[i].identifier.isReference) (top.arg[i].identifier as ReferenceToken).setRef(srcArgs.transporterRef[i])
                else if (a.getOperandType() == EType.Int64) top.arg[i].setValue(srcArgs.transporterInt[i], exm)
                else top.arg[i].setValue(srcArgs.transporterStr[i], exm)
            }
        } else {
            if (top.hasPrivDynamicVar) top.enter()
        }
        functionList.add(call)
        currentLine = call.currentLabel
        lineCount++
    }

    val isFunctionMethod: Boolean get() = functionList[currentMin].topLabel.isMethod

    var methodReturnValue: SingleTerm? = null

    fun returnF(ret: SingleTerm?) {
        if (Program.DebugMode) console?.debugRemoveTraceLog()
        currentLine = functionList[functionList.size - 1].returnAddress
        functionList.removeAt(functionList.size - 1)
        methodReturnValue = ret
    }

    var isClone = false

    fun clone(): ProcessState {
        val ret = ProcessState(console)
        ret.isClone = true
        ret.currentLine = currentLine
        ret.systemState = systemState
        ret.begintype = begintype
        return ret
    }
}
