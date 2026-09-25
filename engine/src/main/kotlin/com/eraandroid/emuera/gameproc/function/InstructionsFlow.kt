package com.eraandroid.emuera.gameproc.function

import com.eraandroid.emuera.GlobalStatic
import com.eraandroid.emuera.config.Config
import com.eraandroid.emuera.gamedata.ParserMediator
import com.eraandroid.emuera.gamedata.expression.*
import com.eraandroid.emuera.gameproc.*
import com.eraandroid.emuera.sub.*

/* Instraction.Child.cs part 4: flowControlFunction */

internal class IF_Instruction : AbstractInstruction() {
    init { argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.INT_EXPRESSION); flag = METHOD_SAFE or FLOW_CONTROL or PARTIAL or FORCE_SETARG }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) {
        var ifJumpto: LogicalLine? = func.jumpTo
        for (line in func.ifCaseList!!) {
            if (line.isError) continue
            if (line.functionCode == FunctionCode.ELSE) { ifJumpto = line; break }
            state.currentLine = line
            if ((line.argument as ExpressionArgument).term!!.getIntValue(exm) != 0L) { ifJumpto = line; break }
        }
        if (ifJumpto !== func) state.jumpTo(ifJumpto)
    }
}

internal class SELECTCASE_Instruction : AbstractInstruction() {
    init { argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.EXPRESSION); flag = METHOD_SAFE or EXTENDED or FLOW_CONTROL or PARTIAL or FORCE_SETARG }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) {
        var caseJumpto: LogicalLine? = func.jumpTo
        val selectValue = (func.argument as ExpressionArgument).term!!
        var sValue: String? = null
        var iValue = 0L
        if (selectValue.isInteger) iValue = selectValue.getIntValue(exm) else sValue = selectValue.getStrValue(exm)
        search@ for (line in func.ifCaseList!!) {
            if (line.isError) continue
            if (line.functionCode == FunctionCode.CASEELSE) { caseJumpto = line; break }
            val caseArg = line.argument as CaseArgument
            state.currentLine = line
            for (caseExp in caseArg.caseExps) {
                val hit = if (selectValue.isInteger) caseExp.getBool(iValue, exm) else caseExp.getBool(sValue!!, exm)
                if (hit) { caseJumpto = line; break@search }
            }
        }
        state.jumpTo(caseJumpto)
    }
}

internal class RETURNFORM_Instruction : AbstractInstruction() {
    init { argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.FORM_STR); flag = EXTENDED or FLOW_CONTROL }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) {
        val aSt = StringStream((func.argument as ExpressionArgument).term!!.getStrValue(exm))
        val termList = ArrayList<Long>()
        while (!aSt.eos) {
            val wc = LexicalAnalyzer.analyse(aSt, LexEndWith.Comma, LexAnalyzeFlag.None)
            termList.add(ExpressionParser.reduceIntegerTerm(wc, TermEndWith.EoL).getIntValue(exm))
            aSt.shiftNext()
            LexicalAnalyzer.skipHalfSpace(aSt)
        }
        if (termList.isEmpty()) termList.add(0)
        exm.vEvaluator.setResultX(termList)
        state.`return`(exm.vEvaluator.RESULT)
    }
}

internal class RETURN_Instruction : AbstractInstruction() {
    init { argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.INT_ANY); flag = FLOW_CONTROL }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) {
        val a = func.argument as ExpressionArrayArgument
        if (a.termList.isEmpty()) {
            exm.vEvaluator.RESULT = 0
            state.`return`(0)
            return
        }
        val termList = a.termList.map { it!!.getIntValue(exm) }.toMutableList()
        if (termList.isEmpty()) termList.add(0)
        exm.vEvaluator.setResultX(termList)
        state.`return`(exm.vEvaluator.RESULT)
    }
}

internal class CATCH_Instruction : AbstractInstruction() {
    init { argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.VOID); flag = METHOD_SAFE or EXTENDED or FLOW_CONTROL or PARTIAL }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) = state.jumpTo(func.jumpToEndCatch)
}

internal class RESTART_Instruction : AbstractInstruction() {
    init { argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.VOID); flag = METHOD_SAFE or FLOW_CONTROL or EXTENDED }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) = state.jumpTo(func.parentLabelLine)
}

internal class BREAK_Instruction : AbstractInstruction() {
    init { argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.VOID); flag = METHOD_SAFE or FLOW_CONTROL }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) {
        val jumpTo = func.jumpTo as InstructionLine
        val iLine = jumpTo.jumpTo as InstructionLine
        if (jumpTo.functionCode != FunctionCode.WHILE && jumpTo.functionCode != FunctionCode.DO)
            jumpTo.loopCounter?.plusValue(jumpTo.loopStep, exm)
        state.jumpTo(iLine)
    }
}

internal class CONTINUE_Instruction : AbstractInstruction() {
    init { argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.VOID); flag = METHOD_SAFE or FLOW_CONTROL }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) {
        val jumpTo = func.jumpTo as InstructionLine
        if (jumpTo.functionCode == FunctionCode.REPEAT || jumpTo.functionCode == FunctionCode.FOR) {
            val cnt = jumpTo.loopCounter ?: run { state.jumpTo(jumpTo.jumpTo); return }
            cnt.plusValue(jumpTo.loopStep, exm)
            val counter = cnt.getIntValue(exm)
            if ((jumpTo.loopStep > 0 && jumpTo.loopEnd > counter) || (jumpTo.loopStep < 0 && jumpTo.loopEnd < counter)) state.jumpTo(func.jumpTo)
            else state.jumpTo(jumpTo.jumpTo)
            return
        }
        if (jumpTo.functionCode == FunctionCode.WHILE) {
            if ((jumpTo.argument as ExpressionArgument).term!!.getIntValue(exm) != 0L) state.jumpTo(func.jumpTo)
            else state.jumpTo(jumpTo.jumpTo)
            return
        }
        if (jumpTo.functionCode == FunctionCode.DO) {
            val tFunc = jumpTo.jumpTo as InstructionLine
            if (tFunc.isError) throw CodeEE(tFunc.errMes, tFunc.position)
            if ((tFunc.argument as ExpressionArgument).term!!.getIntValue(exm) != 0L) state.jumpTo(jumpTo)
            else state.jumpTo(tFunc)
            return
        }
        throw ExeEE("異常なCONTINUE")
    }
}

internal class REND_Instruction : AbstractInstruction() {
    init { argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.VOID); flag = METHOD_SAFE or FLOW_CONTROL or PARTIAL }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) {
        val jumpTo = func.jumpTo as InstructionLine
        val cnt = jumpTo.loopCounter ?: run { state.jumpTo(jumpTo.jumpTo); return }
        cnt.plusValue(jumpTo.loopStep, exm)
        val counter = cnt.getIntValue(exm)
        if ((jumpTo.loopStep > 0 && jumpTo.loopEnd > counter) || (jumpTo.loopStep < 0 && jumpTo.loopEnd < counter)) state.jumpTo(func.jumpTo)
    }
}

internal class WEND_Instruction : AbstractInstruction() {
    init { argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.VOID); flag = METHOD_SAFE or EXTENDED or FLOW_CONTROL or PARTIAL }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) {
        val jumpTo = func.jumpTo as InstructionLine
        if ((jumpTo.argument as ExpressionArgument).term!!.getIntValue(exm) != 0L) state.jumpTo(func.jumpTo)
    }
}

internal class LOOP_Instruction : AbstractInstruction() {
    init { argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.INT_EXPRESSION); flag = METHOD_SAFE or EXTENDED or FLOW_CONTROL or PARTIAL or FORCE_SETARG }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) {
        if ((func.argument as ExpressionArgument).term!!.getIntValue(exm) != 0L) state.jumpTo(func.jumpTo)
    }
}

internal class RETURNF_Instruction : AbstractInstruction() {
    init { argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.EXPRESSION_NULLABLE); flag = METHOD_SAFE or EXTENDED or FLOW_CONTROL }
    override fun setJumpTo(useCallForm: BooleanArray, func: InstructionLine, currentDepth: Int, functionNotFoundName: Array<String?>) {
        val label = func.parentLabelLine!!
        if (!label.isMethod) ParserMediator.warn("RETURNFは#FUNCTION以外では使用できません", func, 2, true, false)
        val term = (func.argument as? ExpressionArgument)?.term ?: return
        if (label.methodType != term.getOperandType()) {
            if (label.methodType == EType.Int64) ParserMediator.warn("#FUNCTIONで始まる関数の戻り値に文字列型が指定されました", func, 2, true, false)
            else if (label.methodType == EType.String) ParserMediator.warn("#FUCNTIONSで始まる関数の戻り値に数値型が指定されました", func, 2, true, false)
        }
    }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) {
        val term = (func.argument as ExpressionArgument).term
        state.returnF(term?.getValue(exm))
    }
}

internal class CALL_Instruction(form: Boolean, private val isJump: Boolean, private val isTry: Boolean, isTryCatch: Boolean) : AbstractInstruction() {
    init {
        argBuilder = ArgumentParser.getArgumentBuilder(if (form) FunctionArgType.SP_CALLFORM else FunctionArgType.SP_CALL)
        flag = FLOW_CONTROL or FORCE_SETARG
        if (isJump) flag = flag or IS_JUMP
        if (isTry) flag = flag or IS_TRY
        if (isTryCatch) flag = flag or IS_TRYC or PARTIAL
    }
    override fun setJumpTo(useCallForm: BooleanArray, func: InstructionLine, currentDepth: Int, functionNotFoundName: Array<String?>) {
        val arg = func.argument!!
        if (!arg.isConst) { useCallForm[0] = true; return }
        val callArg = arg as SpCallArgment
        var labelName = callArg.constStr!!
        if (Config.ICFunction) labelName = labelName.uppercase()
        val call = CalledFunction.callFunction(GlobalStatic.Process!!, labelName, func)
        if (call == null && !func.function.isTry()) { functionNotFoundName[0] = labelName; return }
        if (call != null) {
            func.jumpTo = call.topLabel
            if (call.topLabel.depth < 0) call.topLabel.depth = currentDepth + 1
            if (call.topLabel.isError) {
                func.isError = true
                func.errMes = call.topLabel.errMes
                return
            }
            val errMes = arrayOfNulls<String>(1)
            callArg.udfArgument = call.convertArg(callArg.rowArgs, errMes)
            if (callArg.udfArgument == null) {
                ParserMediator.warn(errMes[0] ?: "", func, 2, true, false)
                return
            }
        }
        callArg.callFunc = call
    }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) {
        val spCallArg = func.argument as SpCallArgment
        val call: CalledFunction?
        val labelName: String
        var arg: UserDefinedFunctionArgument? = null
        if (spCallArg.isConst) {
            call = spCallArg.callFunc
            labelName = spCallArg.constStr!!
            arg = spCallArg.udfArgument
        } else {
            var l = spCallArg.funcnameTerm.getStrValue(exm)
            if (Config.ICFunction) l = l.uppercase()
            labelName = l
            call = CalledFunction.callFunction(GlobalStatic.Process!!, labelName, func)
        }
        if (call == null) {
            if (!isTry) throw CodeEE("関数\"@$labelName\"が見つかりません")
            if (func.jumpToEndCatch != null) state.jumpTo(func.jumpToEndCatch)
            return
        }
        call.isJump = isJump
        if (arg == null) {
            val errMes = arrayOfNulls<String>(1)
            arg = call.convertArg(spCallArg.rowArgs, errMes) ?: throw CodeEE(errMes[0] ?: "")
        }
        state.intoFunction(call, arg, exm)
    }
}

internal class CALLEVENT_Instruction : AbstractInstruction() {
    init { argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.STR); flag = FLOW_CONTROL or EXTENDED }
    override fun setJumpTo(useCallForm: BooleanArray, func: InstructionLine, currentDepth: Int, functionNotFoundName: Array<String?>) {
        if (func.parentLabelLine!!.isEvent) ParserMediator.warn("EVENT関数中にCALLEVENT命令は使用できません", func, 2, true, false)
    }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) {
        var labelName = func.argument!!.constStr!!
        if (Config.ICFunction) labelName = labelName.uppercase()
        val call = CalledFunction.callEventFunction(GlobalStatic.Process!!, labelName, func) ?: return
        state.intoFunction(call, null, null)
    }
}

internal class GOTO_Instruction(form: Boolean, private val isTry: Boolean, isTryCatch: Boolean) : AbstractInstruction() {
    init {
        argBuilder = ArgumentParser.getArgumentBuilder(if (form) FunctionArgType.SP_CALLFORM else FunctionArgType.SP_CALL)
        flag = METHOD_SAFE or FLOW_CONTROL or FORCE_SETARG
        if (isTry) flag = flag or IS_TRY
        if (isTryCatch) flag = flag or IS_TRYC or PARTIAL
    }
    override fun setJumpTo(useCallForm: BooleanArray, func: InstructionLine, currentDepth: Int, functionNotFoundName: Array<String?>) {
        func.jumpTo = null
        val arg = func.argument!!
        if (arg.isConst) {
            var labelName = arg.constStr!!
            if (Config.ICVariable) labelName = labelName.uppercase()
            val jumpto = GlobalStatic.LabelDictionary!!.getLabelDollar(labelName, func.parentLabelLine!!)
            if (jumpto == null) {
                if (!func.function.isTry()) ParserMediator.warn("指定されたラベル名\"\$$labelName\"は現在の関数内に存在しません", func, 2, true, false)
                else return
            } else if (jumpto.isError) ParserMediator.warn("指定されたラベル名\"\$$labelName\"は無効な\$ラベル行です", func, 2, true, false)
            else func.jumpTo = jumpto
        }
    }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) {
        val label: String
        val jumpto: LogicalLine?
        val arg = func.argument!!
        if (arg.isConst) {
            label = arg.constStr!!
            jumpto = func.jumpTo ?: return
        } else {
            var l = (arg as SpCallArgment).funcnameTerm.getStrValue(exm)
            if (Config.ICVariable) l = l.uppercase()
            label = l
            jumpto = state.currentCalled!!.callLabel(GlobalStatic.Process!!, label)
        }
        if (jumpto == null) {
            if (!func.function.isTry()) throw CodeEE("指定されたラベル名\"\$$label\"は現在の関数内に存在しません")
            if (func.jumpToEndCatch != null) state.jumpTo(func.jumpToEndCatch)
            return
        } else if (jumpto.isError) throw CodeEE("指定されたラベル名\"\$$label\"は無効な\$ラベル行です")
        state.jumpTo(jumpto)
    }
}
