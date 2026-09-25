package com.eraandroid.emuera.gameproc.function

import com.eraandroid.emuera.GlobalStatic
import com.eraandroid.emuera.gamedata.StrForm
import com.eraandroid.emuera.gamedata.expression.*
import com.eraandroid.emuera.gameproc.InstructionLine
import com.eraandroid.emuera.gameproc.ProcessState
import com.eraandroid.emuera.gameview.HtmlManager
import com.eraandroid.emuera.sub.*

/* Instraction.Child.cs part 1 */

internal fun argStr(exm: ExpressionMediator, func: InstructionLine): String {
    val a = func.argument!!
    return if (a.isConst) a.constStr ?: "" else (a as ExpressionArgument).term!!.getStrValue(exm)
}

internal fun argInt(exm: ExpressionMediator, func: InstructionLine): Long {
    val a = func.argument!!
    return if (a.isConst) a.constInt else (a as ExpressionArgument).term!!.getIntValue(exm)
}

internal fun toUInt32inArg(value: Long, funcName: String, argnum: Int): Int {
    if (value < 0) throw CodeEE("${funcName}の第${argnum}引数に負の値(${value})が指定されました")
    if (value > Int.MAX_VALUE) throw CodeEE("${funcName}の第${argnum}引数の値(${value})が大きすぎます")
    return value.toInt()
}

internal class PRINT_Instruction(name: String) : AbstractInstruction() {
    private var isPrintV = false
    private var isLC = false
    private var isC = false
    private var isForms = false

    init {
        flag = IS_PRINT
        val st = StringStream(name)
        st.jump(5)
        if (st.currentEqualTo("SINGLE")) { flag = flag or PRINT_SINGLE or EXTENDED; st.jump(6) }
        if (st.currentEqualTo("V")) { argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.SP_PRINTV); isPrintV = true; st.jump(1) }
        else if (st.currentEqualTo("S")) { argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.STR_EXPRESSION); st.jump(1) }
        else if (st.currentEqualTo("FORMS")) { argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.STR_EXPRESSION); isForms = true; st.jump(5) }
        else if (st.currentEqualTo("FORM")) { argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.FORM_STR_NULLABLE); st.jump(4) }
        else argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.STR_NULLABLE)
        if (st.currentEqualTo("LC")) { flag = flag or EXTENDED; isLC = true; st.jump(2) }
        else if (st.currentEqualTo("C")) { if (name == "PRINTFORMC") flag = flag or EXTENDED; isC = true; st.jump(1) }
        if (st.currentEqualTo("K")) { flag = flag or ISPRINTKFUNC or EXTENDED; st.jump(1) }
        if (st.currentEqualTo("D")) { flag = flag or ISPRINTDFUNC or EXTENDED; st.jump(1) }
        if (st.currentEqualTo("L")) { flag = flag or PRINT_NEWLINE or METHOD_SAFE; st.jump(1) }
        else if (st.currentEqualTo("W")) { flag = flag or PRINT_NEWLINE or PRINT_WAITINPUT; st.jump(1) }
        else flag = flag or METHOD_SAFE
        if (argBuilder == null || !st.eos) throw ExeEE("PRINT異常")
    }

    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) {
        if (GlobalStatic.Process!!.skipPrint) return
        exm.console.useUserStyle = true
        exm.console.useSetColorStyle = !func.function.isPrintDFunction()
        var str: String
        val arg = func.argument!!
        if (arg.isConst) str = arg.constStr ?: ""
        else if (isPrintV) {
            val builder = StringBuilder()
            for (termV in (arg as SpPrintVArgument).terms) {
                if (termV!!.getOperandType() == EType.Int64) builder.append(termV.getIntValue(exm)) else builder.append(termV.getStrValue(exm))
            }
            str = builder.toString()
        } else {
            str = (arg as ExpressionArgument).term!!.getStrValue(exm)
            if (isForms) {
                str = exm.checkEscape(str)
                val wt = LexicalAnalyzer.analyseFormattedString(StringStream(str), FormStrEndWith.EoL, false)
                str = StrForm.fromWordToken(wt).getString(exm)
            }
        }
        if (func.function.isPrintKFunction()) str = exm.convertStringType(str)
        if (isC) exm.console.printC(str, true)
        else if (isLC) exm.console.printC(str, false)
        else exm.outputToConsole(str, func.function)
        exm.console.useSetColorStyle = true
    }
}

internal class PRINT_DATA_Instruction(name: String) : AbstractInstruction() {
    init {
        flag = EXTENDED or IS_PRINT or IS_PRINTDATA or PARTIAL
        argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.VAR_INT)
        val st = StringStream(name)
        st.jump(9)
        if (st.currentEqualTo("K")) { flag = flag or ISPRINTKFUNC or EXTENDED; st.jump(1) }
        if (st.currentEqualTo("D")) { flag = flag or ISPRINTDFUNC or EXTENDED; st.jump(1) }
        if (st.currentEqualTo("L")) { flag = flag or PRINT_NEWLINE or METHOD_SAFE; st.jump(1) }
        else if (st.currentEqualTo("W")) { flag = flag or PRINT_NEWLINE or PRINT_WAITINPUT; st.jump(1) }
        else flag = flag or METHOD_SAFE
        if (argBuilder == null || !st.eos) throw ExeEE("PRINTDATA異常")
    }

    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) {
        if (GlobalStatic.Process!!.skipPrint) return
        exm.console.useUserStyle = true
        exm.console.useSetColorStyle = !func.function.isPrintDFunction()
        val dataList = func.dataList!!
        if (dataList.isEmpty()) { state.jumpTo(func.jumpTo); return }
        val count = dataList.size
        val choice = exm.vEvaluator.getNextRand(count.toLong()).toInt()
        (func.argument as PrintDataArgument).variable?.setValue(choice.toLong(), exm)
        val iList = dataList[choice]
        var i = 0
        for (selectedLine in iList) {
            state.currentLine = selectedLine
            if (selectedLine.argument == null) ArgumentParser.setArgumentTo(selectedLine)
            var str = (selectedLine.argument as ExpressionArgument).term!!.getStrValue(exm)
            if (func.function.isPrintKFunction()) str = exm.convertStringType(str)
            exm.console.print(str)
            if (++i < iList.size) exm.console.newLine()
        }
        if (func.function.isNewLine() || func.function.isWaitInput()) {
            exm.console.newLine()
            if (func.function.isWaitInput()) exm.console.readAnyKey()
        }
        exm.console.useSetColorStyle = true
        state.jumpTo(func.jumpTo)
    }
}

internal class HTML_PRINT_Instruction : AbstractInstruction() {
    init { flag = EXTENDED or METHOD_SAFE; argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.STR_EXPRESSION) }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) {
        if (GlobalStatic.Process!!.skipPrint) return
        exm.console.printHtml(argStr(exm, func))
    }
}

internal class HTML_TAGSPLIT_Instruction : AbstractInstruction() {
    init { flag = EXTENDED or METHOD_SAFE; argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.SP_HTMLSPLIT) }
    @Suppress("UNCHECKED_CAST")
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) {
        val arg = func.argument as SpHtmlSplitArgument
        val strs = HtmlManager.htmlTagSplit(arg.targetStr.getStrValue(exm))
        if (strs == null) { arg.num!!.setValue(-1L, exm); return }
        arg.num!!.setValue(strs.size.toLong(), exm)
        val output = arg.variable.getArray() as Array<String?>
        for (i in 0 until minOf(output.size, strs.size)) output[i] = strs[i]
    }
}

internal class PRINT_IMG_Instruction : AbstractInstruction() {
    init { flag = EXTENDED or METHOD_SAFE; argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.STR_EXPRESSION) }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) {
        if (GlobalStatic.Process!!.skipPrint) return
        exm.console.printImg(argStr(exm, func))
    }
}

internal class PRINT_RECT_Instruction : AbstractInstruction() {
    init { flag = EXTENDED or METHOD_SAFE; argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.INT_ANY) }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) {
        if (GlobalStatic.Process!!.skipPrint) return
        val a = func.argument as ExpressionArrayArgument
        val param = IntArray(a.termList.size) { toUInt32inArg(a.termList[it]!!.getIntValue(exm), "PRINT_RECT", it + 1) }
        exm.console.printShape("rect", param)
    }
}

internal class PRINT_SPACE_Instruction : AbstractInstruction() {
    init { flag = EXTENDED or METHOD_SAFE; argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.INT_EXPRESSION) }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) {
        if (GlobalStatic.Process!!.skipPrint) return
        exm.console.printShape("space", intArrayOf(toUInt32inArg(argInt(exm, func), "PRINT_SPACE", 1)))
    }
}

internal class CUSTOMDRAWLINE_Instruction : AbstractInstruction() {
    init { argBuilder = null; flag = METHOD_SAFE or EXTENDED }
    override fun createArgument(line: InstructionLine, exm: ExpressionMediator): Argument? {
        val st = line.popArgumentPrimitive()
        if (st.eos) throw CodeEE("引数が設定されていません")
        val rowStr = GlobalStatic.Console!!.getStBar(st.substring())
        return ExpressionArgument(SingleTerm(rowStr)).also { it.constStr = rowStr; it.isConst = true }
    }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) {
        if (GlobalStatic.Process!!.skipPrint) return
        GlobalStatic.Console!!.printCustomBar(func.argument!!.constStr ?: "", true)
        exm.console.newLine()
    }
}

internal class DEBUGPRINT_Instruction(form: Boolean, newline: Boolean) : AbstractInstruction() {
    init {
        argBuilder = ArgumentParser.getArgumentBuilder(if (form) FunctionArgType.FORM_STR_NULLABLE else FunctionArgType.STR_NULLABLE)
        flag = METHOD_SAFE or EXTENDED or DEBUG_FUNC
        if (newline) flag = flag or PRINT_NEWLINE
    }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) {
        exm.console.debugPrint(argStr(exm, func))
        if (func.function.isNewLine()) exm.console.debugNewLine()
    }
}

internal class DEBUGCLEAR_Instruction : AbstractInstruction() {
    init { argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.VOID); flag = METHOD_SAFE or EXTENDED or DEBUG_FUNC }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) = exm.console.debugClear()
}

internal class METHOD_Instruction : AbstractInstruction() {
    init { argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.METHOD); flag = METHOD_SAFE or EXTENDED }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) {
        val term = (func.argument as MethodArgument).methodTerm
        if (term.getOperandType() == EType.Int64) exm.vEvaluator.RESULT = term.getIntValue(exm)
        else exm.vEvaluator.RESULTS = term.getStrValue(exm)
    }
}

internal class SET_Instruction : AbstractInstruction() {
    init { argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.SP_SET); flag = METHOD_SAFE }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) {
        val a = func.argument
        if (a is SpSetArrayArgument) {
            if (a.variableDest.isInteger) {
                if (a.isConst) a.variableDest.setValues(a.constIntList!!, exm)
                else a.variableDest.setValues(LongArray(a.termList.size) { a.termList[it]!!.getIntValue(exm) }, exm)
            } else {
                if (a.isConst) a.variableDest.setValues(a.constStrList!!, exm)
                else a.variableDest.setValues(Array<String?>(a.termList.size) { a.termList[it]!!.getStrValue(exm) }, exm)
            }
            return
        }
        val s = a as SpSetArgument
        if (s.variableDest.isInteger) {
            val src = if (s.isConst) s.constInt else s.term!!.getIntValue(exm)
            if (s.addConst) s.variableDest.plusValue(src, exm) else s.variableDest.setValue(src, exm)
        } else {
            val src = if (s.isConst) s.constStr else s.term!!.getStrValue(exm)
            s.variableDest.setValue(src, exm)
        }
    }
}

internal class REUSELASTLINE_Instruction : AbstractInstruction() {
    init { argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.FORM_STR_NULLABLE); flag = METHOD_SAFE or EXTENDED or IS_PRINT }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) {
        exm.console.printTemporaryLine((func.argument as ExpressionArgument).term!!.getStrValue(exm))
    }
}

internal class CLEARLINE_Instruction : AbstractInstruction() {
    init { argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.INT_EXPRESSION); flag = METHOD_SAFE or EXTENDED or IS_PRINT }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) {
        val delNum = (func.argument as ExpressionArgument).term!!.getIntValue(exm).toInt()
        exm.console.deleteLine(delNum)
        exm.console.refreshStrings(false)
    }
}

internal class STRLEN_Instruction(argisform: Boolean, private val unicode: Boolean) : AbstractInstruction() {
    init {
        argBuilder = ArgumentParser.getArgumentBuilder(if (argisform) FunctionArgType.FORM_STR_NULLABLE else FunctionArgType.STR_NULLABLE)
        flag = METHOD_SAFE or EXTENDED
    }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) {
        val str = argStr(exm, func)
        exm.vEvaluator.RESULT = if (unicode) str.length.toLong() else LangManager.getStrlenLang(str).toLong()
    }
}

internal class SETBIT_Instruction(private val op: Int) : AbstractInstruction() {
    init { argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.BIT_ARG); flag = METHOD_SAFE or EXTENDED }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) {
        val a = func.argument as BitArgument
        val varTerm = a.variableDest
        for (t in a.term) {
            val x = t!!.getIntValue(exm)
            if (x < 0 || x > 63) throw CodeEE("第2引数がビットのレンジ(0から63)を超えています")
            var baseValue = varTerm.getIntValue(exm)
            val shift = 1L shl x.toInt()
            baseValue = when (op) { 1 -> baseValue or shift; 0 -> baseValue and shift.inv(); else -> baseValue xor shift }
            varTerm.setValue(baseValue, exm)
        }
    }
}

internal class WAIT_Instruction(private val isForce: Boolean) : AbstractInstruction() {
    init { argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.VOID); flag = IS_PRINT }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) {
        if (isForce) exm.console.readAnyKey(false, true) else exm.console.readAnyKey()
    }
}
