package com.eraandroid.emuera.gameproc.function

import com.eraandroid.emuera.gamedata.expression.ExpressionMediator
import com.eraandroid.emuera.gameproc.InstructionLine
import com.eraandroid.emuera.gameproc.ProcessState
import com.eraandroid.emuera.sub.ExeEE

const val FLOW_CONTROL = 0x00001
const val EXTENDED = 0x00002
const val METHOD_SAFE = 0x00004
const val DEBUG_FUNC = 0x00008
const val PARTIAL = 0x00010
const val FORCE_SETARG = 0x00020
const val IS_JUMP = 0x00040
const val IS_TRY = 0x00080
const val IS_TRYC = 0x08000
const val PRINT_NEWLINE = 0x00100
const val PRINT_WAITINPUT = 0x00200
const val PRINT_SINGLE = 0x00400
const val ISPRINTDFUNC = 0x00800
const val ISPRINTKFUNC = 0x01000
const val IS_PRINT = 0x02000
const val IS_INPUT = 0x04000
const val IS_PRINTDATA = 0x10000

abstract class AbstractInstruction {
    var flag = 0
        protected set
    var argBuilder: ArgumentBuilder? = null
        protected set

    /** useCallForm[0], functionNotFoundName[0] は ref 引数 */
    open fun setJumpTo(useCallForm: BooleanArray, func: InstructionLine, currentDepth: Int, functionNotFoundName: Array<String?>) {}
    open fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState): Unit = throw ExeEE("未実装 or 呼び出しミス")
    open fun createArgument(line: InstructionLine, exm: ExpressionMediator): Argument? = throw ExeEE("実装されていない")
}
