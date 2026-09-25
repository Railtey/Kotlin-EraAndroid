package com.eraandroid.emuera.gameproc

import com.eraandroid.emuera.GlobalStatic
import com.eraandroid.emuera.gamedata.expression.EType
import com.eraandroid.emuera.gamedata.expression.OperatorCode
import com.eraandroid.emuera.gamedata.expression.SingleTerm
import com.eraandroid.emuera.gamedata.variable.UserDefinedVariableToken
import com.eraandroid.emuera.gamedata.variable.VariableTerm
import com.eraandroid.emuera.gameproc.function.Argument
import com.eraandroid.emuera.gameproc.function.FunctionCode
import com.eraandroid.emuera.gameproc.function.FunctionIdentifier
import com.eraandroid.emuera.sub.ScriptPosition
import com.eraandroid.emuera.sub.StringStream
import com.eraandroid.emuera.sub.WordCollection

abstract class LogicalLine {
    var position: ScriptPosition? = null
        protected set
    var parentLabelLine: FunctionLabelLine? = null
    var nextLine: LogicalLine? = null

    override fun toString(): String {
        val p = position ?: return super.toString()
        return "${p.filename}:${p.lineNo}:${GlobalStatic.Process?.getRawTextFormFilewithLine(p)}"
    }

    protected var isErrorField = false
    open var errMes: String = ""
    open var isError: Boolean
        get() = isErrorField
        set(v) { isErrorField = v }
}

class InvalidLine(thePosition: ScriptPosition?, err: String) : LogicalLine() {
    init { position = thePosition; errMes = err }
    override var isError: Boolean
        get() = true
        set(_) {}
}

class InstructionLine private constructor(
    thePosition: ScriptPosition?,
    val function: FunctionIdentifier,
    private var argprimitive: StringStream?,
    val assignOperator: OperatorCode?,
    private var assigndest: WordCollection?,
) : LogicalLine() {
    constructor(thePosition: ScriptPosition?, theFunc: FunctionIdentifier, theArgPrimitive: StringStream?) :
        this(thePosition, theFunc, theArgPrimitive, null, null)
    constructor(thePosition: ScriptPosition?, functionIdentifier: FunctionIdentifier, assignOP: OperatorCode, dest: WordCollection, theArgPrimitive: StringStream?) :
        this(thePosition, functionIdentifier, theArgPrimitive, assignOP, dest)

    init { position = thePosition }

    val functionCode: FunctionCode get() = function.code
    var argument: Argument? = null

    fun popArgumentPrimitive(): StringStream {
        val ret = argprimitive ?: StringStream("")
        argprimitive = null
        return ret
    }

    fun popAssignmentDestStr(): WordCollection? {
        val ret = assigndest
        assigndest = null
        return ret
    }

    var loopEnd: Long = 0
    var loopCounter: VariableTerm? = null
    var loopStep: Long = 0
    var jumpTo: LogicalLine? = null
    var jumpToEndCatch: LogicalLine? = null
    var ifCaseList: MutableList<InstructionLine>? = null
    var dataList: MutableList<MutableList<InstructionLine>>? = null
    var callList: MutableList<InstructionLine>? = null
}

class NullLine : LogicalLine()

open class FunctionLabelLine protected constructor() : LogicalLine(), Comparable<FunctionLabelLine> {
    constructor(thePosition: ScriptPosition?, labelname: String, wc: WordCollection?) : this() {
        position = thePosition
        labelName = labelname
        this.wc = wc
    }

    private var wc: WordCollection? = null
    fun popRowArgs(): WordCollection? {
        val ret = wc
        wc = null
        return ret
    }

    var labelName: String = ""
        protected set
    var isEvent = false
    var isSystem = false
    var isSingle = false
    var isPri = false
    var isLater = false
    var isOnly = false
    var hasPrivDynamicVar = false
    var localLength = 0
    var localsLength = 0
    var argLength = 0
    var argsLength = 0
    var isMethod = false
    var methodType: EType = EType.Void
    var arg: Array<VariableTerm> = arrayOf()
    var def: Array<SingleTerm?> = arrayOf()
    var depth = -1
    var index = -1
    var fileIndex = 0

    override fun compareTo(other: FunctionLabelLine): Int {
        if (fileIndex != other.fileIndex) return fileIndex.compareTo(other.fileIndex)
        val a = position?.lineNo ?: 0
        val b = other.position?.lineNo ?: 0
        if (a != b) return a.compareTo(b)
        return index.compareTo(other.index)
    }

    private val privateVar = HashMap<String, UserDefinedVariableToken>()

    fun addPrivateVariable(data: UserDefinedVariableData): Boolean {
        val n = data.Name!!
        if (privateVar.containsKey(n)) return false
        val v = GlobalStatic.VariableData!!.createPrivateVariable(data)
        privateVar[n] = v
        if (!data.Static) hasPrivDynamicVar = true
        return true
    }

    fun getPrivateVariable(key: String): UserDefinedVariableToken? = privateVar[key]

    fun enter() { for (v in privateVar.values) if (!v.isStatic) v.`in`() }
    fun exit() { for (v in privateVar.values) if (!v.isStatic) v.out() }
}

class InvalidLabelLine(thePosition: ScriptPosition?, labelname: String, err: String) : FunctionLabelLine() {
    init {
        position = thePosition
        labelName = labelname
        errMes = err
        isSingle = false
        index = -1
        depth = -1
        isMethod = false
        methodType = EType.Void
    }
    override var isError: Boolean
        get() = true
        set(_) {}
}

class GotoLabelLine(thePosition: ScriptPosition?, val labelName: String) : LogicalLine() {
    init { position = thePosition }
    override fun equals(other: Any?): Boolean =
        other is GotoLabelLine && other.parentLabelLine == parentLabelLine && other.labelName == labelName
    override fun hashCode(): Int = labelName.hashCode() xor (parentLabelLine?.hashCode() ?: 0)
}
