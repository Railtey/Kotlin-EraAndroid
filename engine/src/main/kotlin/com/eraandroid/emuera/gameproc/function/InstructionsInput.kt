package com.eraandroid.emuera.gameproc.function

import com.eraandroid.emuera.GlobalStatic
import com.eraandroid.emuera.Program
import com.eraandroid.emuera.config.Config
import com.eraandroid.emuera.gamedata.ParserMediator
import com.eraandroid.emuera.gamedata.expression.*
import com.eraandroid.emuera.gameproc.InputRequest
import com.eraandroid.emuera.gameproc.InputType
import com.eraandroid.emuera.gameproc.InstructionLine
import com.eraandroid.emuera.gameproc.ProcessState
import com.eraandroid.emuera.gameview.FontStyle
import com.eraandroid.emuera.sub.*
import java.math.BigDecimal

/* Instraction.Child.cs part 2 */

internal class WAITANYKEY_Instruction : AbstractInstruction() {
    init { argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.VOID); flag = IS_PRINT }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) = exm.console.readAnyKey(true, false)
}

internal class TWAIT_Instruction : AbstractInstruction() {
    init { argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.SP_SWAP); flag = IS_PRINT or EXTENDED }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) {
        exm.console.readAnyKey()
        val arg = func.argument as SpSwapCharaArgument
        val time = arg.x.getIntValue(exm)
        val f = arg.y!!.getIntValue(exm)
        val req = InputRequest()
        req.inputType = if (f != 0L) InputType.Void else InputType.EnterKey
        req.timelimit = time
        exm.console.waitInput(req)
    }
}

internal class INPUT_Instruction : AbstractInstruction() {
    init { argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.SP_INPUT); flag = IS_PRINT or IS_INPUT }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) {
        val arg = func.argument as ExpressionArgument
        val req = InputRequest()
        req.inputType = InputType.IntValue
        if (arg.term != null) {
            req.hasDefValue = true
            req.defIntValue = if (arg.isConst) arg.constInt else arg.term.getIntValue(exm)
        }
        exm.console.waitInput(req)
    }
}

internal class INPUTS_Instruction : AbstractInstruction() {
    init { argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.SP_INPUTS); flag = IS_PRINT or IS_INPUT }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) {
        val arg = func.argument as ExpressionArgument
        val req = InputRequest()
        req.inputType = InputType.StrValue
        if (arg.term != null) {
            req.hasDefValue = true
            req.defStrValue = if (arg.isConst) arg.constStr else arg.term.getStrValue(exm)
        }
        exm.console.waitInput(req)
    }
}

internal class ONEINPUT_Instruction : AbstractInstruction() {
    init { argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.SP_INPUT); flag = IS_PRINT or IS_INPUT or EXTENDED }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) {
        val arg = func.argument as ExpressionArgument
        val req = InputRequest()
        req.inputType = InputType.IntValue
        req.oneInput = true
        if (arg.term != null) {
            var def = if (arg.isConst) arg.constInt else arg.term.getIntValue(exm)
            if (def > 9) def = def.toString().substring(0, 1).toLong()
            if (def >= 0) { req.hasDefValue = true; req.defIntValue = def }
        }
        exm.console.waitInput(req)
    }
}

internal class ONEINPUTS_Instruction : AbstractInstruction() {
    init { argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.SP_INPUTS); flag = IS_PRINT or IS_INPUT or EXTENDED }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) {
        val arg = func.argument as ExpressionArgument
        val req = InputRequest()
        req.inputType = InputType.StrValue
        req.oneInput = true
        if (arg.term != null) {
            var def = (if (arg.isConst) arg.constStr else arg.term.getStrValue(exm)) ?: ""
            if (def.length > 1) def = def.substring(0, 1)
            if (def.isNotEmpty()) { req.hasDefValue = true; req.defStrValue = def }
        }
        exm.console.waitInput(req)
    }
}

internal class TINPUT_Instruction(private val isOne: Boolean) : AbstractInstruction() {
    init { argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.SP_TINPUT); flag = IS_PRINT or IS_INPUT or EXTENDED }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) {
        val a = func.argument as SpTInputsArgument
        val req = InputRequest()
        req.inputType = InputType.IntValue
        req.hasDefValue = true
        req.oneInput = isOne
        val x = a.time.getIntValue(exm)
        var y = a.def.getIntValue(exm)
        if (isOne) {
            if (y < 0) y = kotlin.math.abs(y)
            if (y >= 10) y /= Math.pow(10.0, Math.log10(y.toDouble())).toLong()
        }
        val z = a.disp?.getIntValue(exm) ?: 1L
        req.timelimit = x
        req.defIntValue = y
        req.displayTime = z != 0L
        req.timeUpMes = a.timeout?.getStrValue(exm) ?: Config.TimeupLabel
        exm.console.waitInput(req)
    }
}

internal class TINPUTS_Instruction(private val isOne: Boolean) : AbstractInstruction() {
    init { argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.SP_TINPUTS); flag = IS_PRINT or IS_INPUT or EXTENDED }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) {
        val a = func.argument as SpTInputsArgument
        val req = InputRequest()
        req.inputType = InputType.StrValue
        req.hasDefValue = true
        req.oneInput = isOne
        val x = a.time.getIntValue(exm)
        var strs = a.def.getStrValue(exm)
        if (isOne && strs.length > 1) strs = strs.substring(0, 1)
        val z = a.disp?.getIntValue(exm) ?: 1L
        req.timelimit = x
        req.defStrValue = strs
        req.displayTime = z != 0L
        req.timeUpMes = a.timeout?.getStrValue(exm) ?: Config.TimeupLabel
        exm.console.waitInput(req)
    }
}

internal class CALLF_Instruction(form: Boolean) : AbstractInstruction() {
    init {
        argBuilder = ArgumentParser.getArgumentBuilder(if (form) FunctionArgType.SP_CALLFORMF else FunctionArgType.SP_CALLF)
        flag = EXTENDED or METHOD_SAFE or FORCE_SETARG
    }
    override fun setJumpTo(useCallForm: BooleanArray, func: InstructionLine, currentDepth: Int, functionNotFoundName: Array<String?>) {
        val arg = func.argument!!
        if (!arg.isConst) { useCallForm[0] = true; return }
        val callfArg = arg as SpCallFArgment
        if (Config.ICFunction) callfArg.constStr = callfArg.constStr!!.uppercase()
        try {
            callfArg.funcTerm = GlobalStatic.IdentifierDictionary!!.getFunctionMethod(GlobalStatic.LabelDictionary, callfArg.constStr!!, callfArg.rowArgs, true)
        } catch (e: CodeEE) {
            ParserMediator.warn(e.message ?: "", func, 2, true, false)
            return
        }
        if (callfArg.funcTerm == null) {
            if (!Program.AnalysisMode) ParserMediator.warn("指定された関数名\"@" + callfArg.constStr + "\"は存在しません", func, 2, true, false)
            else ParserMediator.warn(callfArg.constStr!!, func, 2, true, false)
        }
    }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) {
        val mToken: IOperandTerm?
        val labelName: String
        val arg = func.argument as SpCallFArgment
        if (!arg.isConst || exm.console.runERBFromMemory) {
            labelName = arg.funcnameTerm.getStrValue(exm)
            mToken = GlobalStatic.IdentifierDictionary!!.getFunctionMethod(GlobalStatic.LabelDictionary, labelName, arg.rowArgs, true)
        } else {
            labelName = arg.constStr!!
            mToken = arg.funcTerm
        }
        if (mToken == null) throw CodeEE("式中関数\"@$labelName\"が見つかりません")
        mToken.getValue(exm)
    }
}

internal class BAR_Instruction(private val newline: Boolean) : AbstractInstruction() {
    init { argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.SP_BAR); flag = IS_PRINT or METHOD_SAFE or EXTENDED }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) {
        val a = func.argument as SpBarArgument
        exm.console.print(exm.createBar(a.terms[0]!!.getIntValue(exm), a.terms[1]!!.getIntValue(exm), a.terms[2]!!.getIntValue(exm)))
        if (newline) exm.console.newLine()
    }
}

internal class TIMES_Instruction : AbstractInstruction() {
    init { argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.SP_TIMES); flag = METHOD_SAFE }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) {
        val a = func.argument as SpTimesArgument
        val v = a.variableDest
        if (Config.TimesNotRigorousCalculation) {
            v.setValue((v.getIntValue(exm).toDouble() * a.doubleValue).toLong(), exm)
        } else {
            // C# (decimal)double は有効数字15桁に丸める
            val dv = BigDecimal(java.lang.Double.toString(a.doubleValue)).round(java.math.MathContext(15))
            val d = BigDecimal(v.getIntValue(exm)).multiply(dv)
            if (d <= BigDecimal(Long.MAX_VALUE) && d >= BigDecimal(Long.MIN_VALUE)) v.setValue(d.toLong(), exm)
            else v.setValue(d.toDouble().toLong(), exm)
        }
    }
}

internal class ADDCHARA_Instruction(private val isSp: Boolean, private val isDel: Boolean) : AbstractInstruction() {
    init { argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.INT_ANY); flag = METHOD_SAFE }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) {
        if (!Config.CompatiSPChara && isSp) throw CodeEE("SPキャラ関係の機能は標準では使用できません(互換性オプション「SPキャラを使用する」をONにしてください)")
        val a = func.argument as ExpressionArrayArgument
        val charaNoList = LongArray(a.termList.size)
        var i = 0
        for (t in a.termList) {
            val integer = t!!.getIntValue(exm)
            if (isDel) charaNoList[i++] = integer
            else if (Config.CompatiSPChara) exm.vEvaluator.addCharacter_UseSp(integer, isSp)
            else exm.vEvaluator.addCharacter(integer)
        }
        if (isDel) {
            if (charaNoList.size == 1) exm.vEvaluator.delCharacter(charaNoList[0]) else exm.vEvaluator.delCharacter(charaNoList)
        }
    }
}

internal class ADDVOIDCHARA_Instruction : AbstractInstruction() {
    init { argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.VOID); flag = METHOD_SAFE or EXTENDED }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) = exm.vEvaluator.addPseudoCharacter()
}

internal class SWAPCHARA_Instruction : AbstractInstruction() {
    init { argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.SP_SWAP); flag = METHOD_SAFE or EXTENDED }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) {
        val a = func.argument as SpSwapCharaArgument
        exm.vEvaluator.swapChara(a.x.getIntValue(exm), a.y!!.getIntValue(exm))
    }
}

internal class COPYCHARA_Instruction : AbstractInstruction() {
    init { argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.SP_SWAP); flag = METHOD_SAFE or EXTENDED }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) {
        val a = func.argument as SpSwapCharaArgument
        exm.vEvaluator.copyChara(a.x.getIntValue(exm), a.y!!.getIntValue(exm))
    }
}

internal class ADDCOPYCHARA_Instruction : AbstractInstruction() {
    init { argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.INT_ANY); flag = METHOD_SAFE }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) {
        for (t in (func.argument as ExpressionArrayArgument).termList) exm.vEvaluator.addCopyChara(t!!.getIntValue(exm))
    }
}

internal class SORTCHARA_Instruction : AbstractInstruction() {
    init { argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.SP_SORTCHARA); flag = METHOD_SAFE or EXTENDED }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) {
        val a = func.argument as SpSortcharaArgument
        var elem = 0L
        val sortKey = a.sortKey!!
        if (sortKey.identifier.isArray1D) elem = sortKey.getElementInt(1, exm)
        else if (sortKey.identifier.isArray2D) {
            elem = sortKey.getElementInt(1, exm) shl 32
            elem += sortKey.getElementInt(2, exm)
        }
        exm.vEvaluator.sortChara(sortKey.identifier, elem, a.sortOrder, true)
    }
}

internal class RESETCOLOR_Instruction : AbstractInstruction() {
    init { argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.VOID); flag = METHOD_SAFE or EXTENDED }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) = exm.console.setStringStyle(Config.ForeColor)
}

internal class RESETBGCOLOR_Instruction : AbstractInstruction() {
    init { argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.VOID); flag = METHOD_SAFE or EXTENDED }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) = exm.console.setBgColor(Config.BackColor)
}

internal class FONTBOLD_Instruction : AbstractInstruction() {
    init { argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.VOID); flag = METHOD_SAFE or EXTENDED }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) =
        exm.console.setStringStyle(exm.console.stringStyle.fontStyle or FontStyle.Bold)
}

internal class FONTITALIC_Instruction : AbstractInstruction() {
    init { argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.VOID); flag = METHOD_SAFE or EXTENDED }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) =
        exm.console.setStringStyle(exm.console.stringStyle.fontStyle or FontStyle.Italic)
}

internal class FONTREGULAR_Instruction : AbstractInstruction() {
    init { argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.VOID); flag = METHOD_SAFE or EXTENDED }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) = exm.console.setStringStyle(FontStyle.Regular)
}

internal class VARSET_Instruction : AbstractInstruction() {
    init { argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.SP_VAR_SET); flag = METHOD_SAFE or EXTENDED }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) {
        val a = func.argument as SpVarSetArgument
        val v = a.variableDest
        val p = v.getFixedVariableTerm(exm)
        var start = 0
        var end = 0
        if (a.end != null) end = a.end.getIntValue(exm).toInt()
        else if (v.identifier.isArray1D) end = v.getLength()
        if (a.start != null) {
            start = a.start.getIntValue(exm).toInt()
            if (start > end) { val t = start; start = end; end = t }
        }
        if (v.isString) exm.vEvaluator.setValueAll(p, a.term!!.getStrValue(exm), start, end)
        else exm.vEvaluator.setValueAll(p, a.term!!.getIntValue(exm), start, end)
    }
}
