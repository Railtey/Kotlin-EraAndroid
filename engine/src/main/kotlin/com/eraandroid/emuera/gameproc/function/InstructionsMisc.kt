package com.eraandroid.emuera.gameproc.function

import com.eraandroid.emuera.GlobalStatic
import com.eraandroid.emuera.config.Config
import com.eraandroid.emuera.config.EraColor
import com.eraandroid.emuera.gamedata.ParserMediator
import com.eraandroid.emuera.gamedata.expression.*
import com.eraandroid.emuera.gameproc.*
import com.eraandroid.emuera.sub.*

/* Instraction.Child.cs part 3 */

internal class CVARSET_Instruction : AbstractInstruction() {
    init { argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.SP_CVAR_SET); flag = METHOD_SAFE or EXTENDED }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) {
        val a = func.argument as SpCVarSetArgument
        val p = a.variableDest.getFixedVariableTerm(exm)
        val index = a.index.getValue(exm)
        val charaNum = exm.vEvaluator.CHARANUM.toInt()
        var start = 0
        if (a.start != null) {
            start = a.start.getIntValue(exm).toInt()
            if (start < 0 || start >= charaNum) throw CodeEE("命令CVARSETの第４引数(${start})がキャラクタの範囲外です")
        }
        var end: Int
        if (a.end != null) {
            end = a.end.getIntValue(exm).toInt()
            if (end < 0 || end > charaNum) throw CodeEE("命令CVARSETの第５引数(${end})がキャラクタの範囲外です")
        } else end = charaNum
        if (start > end) { val t = start; start = end; end = t }
        if (!p.identifier.isCharacterData) throw CodeEE("命令CVARSETにキャラクタ変数でない変数${p.identifier.name}が渡されました")
        if (index.getOperandType() == EType.String && p.identifier.isArray1D) {
            if (!GlobalStatic.ConstantData!!.isDefined(p.identifier.code, index.str))
                throw CodeEE("文字列${index.str}は配列変数${p.identifier.name}の要素ではありません")
        }
        if (p.identifier.isString) exm.vEvaluator.setValueAllEachChara(p, index, a.term!!.getStrValue(exm), start, end)
        else exm.vEvaluator.setValueAllEachChara(p, index, a.term!!.getIntValue(exm), start, end)
    }
}

internal class RANDOMIZE_Instruction : AbstractInstruction() {
    init { argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.INT_EXPRESSION_NULLABLE); flag = METHOD_SAFE or EXTENDED }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) = exm.vEvaluator.randomize(argInt(exm, func))
}

internal class INITRAND_Instruction : AbstractInstruction() {
    init { argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.VOID); flag = METHOD_SAFE or EXTENDED }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) = exm.vEvaluator.initRanddata()
}

internal class DUMPRAND_Instruction : AbstractInstruction() {
    init { argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.VOID); flag = METHOD_SAFE or EXTENDED }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) = exm.vEvaluator.dumpRanddata()
}

internal class SAVEGLOBAL_Instruction : AbstractInstruction() {
    init { argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.VOID); flag = METHOD_SAFE or EXTENDED }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) { exm.vEvaluator.saveGlobal() }
}

internal class LOADGLOBAL_Instruction : AbstractInstruction() {
    init { argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.VOID); flag = METHOD_SAFE or EXTENDED }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) {
        exm.vEvaluator.RESULT = if (exm.vEvaluator.loadGlobal()) 1 else 0
    }
}

internal class RESETDATA_Instruction : AbstractInstruction() {
    init { argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.VOID); flag = METHOD_SAFE or EXTENDED }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) {
        exm.vEvaluator.resetData()
        exm.console.resetStyle()
    }
}

internal class RESETGLOBAL_Instruction : AbstractInstruction() {
    init { argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.VOID); flag = METHOD_SAFE or EXTENDED }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) = exm.vEvaluator.resetGlobalData()
}

internal class SAVECHARA_Instruction : AbstractInstruction() {
    init { argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.SP_SAVECHARA); flag = METHOD_SAFE or EXTENDED }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) {
        val terms = (func.argument as ExpressionArrayArgument).termList
        val datFilename = terms[0]!!.getStrValue(exm)
        val savMes = terms[1]!!.getStrValue(exm)
        val savCharaList = IntArray(terms.size - 2)
        val charanum = exm.vEvaluator.CHARANUM.toInt()
        for (i in savCharaList.indices) {
            savCharaList[i] = toUInt32inArg(terms[i + 2]!!.getIntValue(exm), "SAVECHARA", i + 3)
            if (savCharaList[i] >= charanum) throw CodeEE("SAVECHARAの第${i + 3}引数の値がキャラ登録番号の範囲を超えています")
            for (j in 0 until i) if (savCharaList[i] == savCharaList[j]) throw CodeEE("同一のキャラ登録番号(${savCharaList[i]})が複数回指定されました")
        }
        exm.vEvaluator.saveChara(datFilename, savMes, savCharaList)
    }
}

internal class LOADCHARA_Instruction : AbstractInstruction() {
    init { argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.STR_EXPRESSION); flag = METHOD_SAFE or EXTENDED }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) = exm.vEvaluator.loadChara(argStr(exm, func))
}

internal class SAVEVAR_Instruction : AbstractInstruction() {
    init { argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.SP_SAVEVAR); flag = METHOD_SAFE or EXTENDED }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState): Unit = throw NotImplCodeEE()
}

internal class LOADVAR_Instruction : AbstractInstruction() {
    init { argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.STR_EXPRESSION); flag = METHOD_SAFE or EXTENDED }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState): Unit = throw NotImplCodeEE()
}

internal class DELDATA_Instruction : AbstractInstruction() {
    init { argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.INT_EXPRESSION); flag = METHOD_SAFE or EXTENDED }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) =
        exm.vEvaluator.delData(toUInt32inArg(argInt(exm, func), "DELDATA", 1))
}

internal class DO_NOTHING_Instruction : AbstractInstruction() {
    init { argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.VOID); flag = METHOD_SAFE or EXTENDED or PARTIAL }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) {}
}

internal class REF_Instruction(byname: Boolean) : AbstractInstruction() {
    init {
        argBuilder = ArgumentParser.getArgumentBuilder(if (byname) FunctionArgType.SP_REFBYNAME else FunctionArgType.SP_REF)
        flag = METHOD_SAFE or EXTENDED
    }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState): Unit = throw NotImplCodeEE()
}

private fun rgbColor(v: Long): EraColor = EraColor.fromArgb((v shr 16).toInt(), ((v shr 8) and 0xFF).toInt(), (v and 0xFF).toInt())

internal class TOOLTIP_SETCOLOR_Instruction : AbstractInstruction() {
    init { argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.SP_SWAP); flag = METHOD_SAFE or EXTENDED }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) {
        val a = func.argument as SpSwapCharaArgument
        val fore = a.x.getIntValue(exm)
        val back = a.y!!.getIntValue(exm)
        if (fore < 0 || fore > 0xFFFFFF) throw CodeEE("第１引数が色を表す整数の範囲外です")
        if (back < 0 || back > 0xFFFFFF) throw CodeEE("第２引数が色を表す整数の範囲外です")
        exm.console.setToolTipColor(rgbColor(fore), rgbColor(back))
    }
}

internal class TOOLTIP_SETDELAY_Instruction : AbstractInstruction() {
    init { argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.INT_EXPRESSION); flag = METHOD_SAFE or EXTENDED }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) {
        val delay = argInt(exm, func)
        if (delay < 0 || delay > Int.MAX_VALUE) throw CodeEE("引数の値が適切な範囲外です")
        exm.console.setToolTipDelay(delay.toInt())
    }
}

internal class TOOLTIP_SETDURATION_Instruction : AbstractInstruction() {
    init { argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.INT_EXPRESSION); flag = METHOD_SAFE or EXTENDED }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) {
        var duration = argInt(exm, func)
        if (duration < 0 || duration > Int.MAX_VALUE) throw CodeEE("引数の値が適切な範囲外です")
        if (duration > Short.MAX_VALUE) duration = Short.MAX_VALUE.toLong()
        exm.console.setToolTipDuration(duration.toInt())
    }
}

internal class INPUTMOUSEKEY_Instruction : AbstractInstruction() {
    init { argBuilder = ArgumentParser.getNormalArgumentBuilder("I", 0); flag = EXTENDED }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) {
        val a = func.argument as ExpressionsArgument
        var time = 0L
        if (a.argumentArray.isNotEmpty()) time = a.argumentArray[0]!!.getIntValue(exm)
        val req = InputRequest()
        req.inputType = InputType.PrimitiveMouseKey
        if (time > 0) req.timelimit = time.toInt().toLong()
        exm.console.waitInput(req)
    }
}

internal class AWAIT_Instruction : AbstractInstruction() {
    init { argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.EXPRESSION_NULLABLE); flag = EXTENDED }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) {
        var waittime = -1L
        val a = func.argument as? ExpressionArgument
        if (a?.term != null) {
            waittime = a.term.getIntValue(exm)
            if (waittime < 0) throw CodeEE("AWAIT命令:負の値(${waittime})が指定されました")
            if (waittime > 10000) throw CodeEE("AWAIT命令:10秒以上の待機時間(${waittime} ms)が指定されました")
        }
        exm.console.await(waittime.toInt())
    }
}

internal class BEGIN_Instruction : AbstractInstruction() {
    init { argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.STR); flag = FLOW_CONTROL }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) {
        var keyword = func.argument!!.constStr!!
        if (Config.ICFunction) keyword = keyword.uppercase()
        state.setBegin(keyword)
        state.`return`(0)
        exm.console.resetStyle()
    }
}

internal class SAVELOADGAME_Instruction(private val isSave: Boolean) : AbstractInstruction() {
    init { argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.VOID); flag = FLOW_CONTROL }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) {
        if ((state.systemState and SystemStateCode.__CAN_SAVE__) != SystemStateCode.__CAN_SAVE__) {
            throw CodeEE("@" + (state.scope ?: "") + "中でSAVEGAME/LOADGAME命令を実行することはできません")
        }
        val proc = GlobalStatic.Process!!
        proc.saveCurrentState(true)
        proc.getCurrentState.saveLoadData(isSave)
    }
}

internal class REPEAT_Instruction(fornext: Boolean) : AbstractInstruction() {
    init {
        flag = METHOD_SAFE or FLOW_CONTROL or PARTIAL
        if (fornext) { argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.SP_FOR_NEXT); flag = flag or EXTENDED }
        else argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.INT_EXPRESSION)
    }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) {
        val a = func.argument as SpForNextArgment
        val cnt = a.cnt
        func.loopCounter = cnt
        cnt.setValue(a.start.getIntValue(exm), exm)
        func.loopEnd = a.end.getIntValue(exm)
        func.loopStep = a.step.getIntValue(exm)
        if (func.loopStep > 0 && func.loopEnd > cnt.getIntValue(exm)) return
        else if (func.loopStep < 0 && func.loopEnd < cnt.getIntValue(exm)) return
        state.jumpTo(func.jumpTo)
    }
}

internal class WHILE_Instruction : AbstractInstruction() {
    init { argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.INT_EXPRESSION); flag = METHOD_SAFE or EXTENDED or FLOW_CONTROL or PARTIAL }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) {
        if ((func.argument as ExpressionArgument).term!!.getIntValue(exm) != 0L) return
        state.jumpTo(func.jumpTo)
    }
}

internal class SIF_Instruction : AbstractInstruction() {
    init { argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.INT_EXPRESSION); flag = METHOD_SAFE or FLOW_CONTROL or PARTIAL or FORCE_SETARG }
    override fun setJumpTo(useCallForm: BooleanArray, func: InstructionLine, currentDepth: Int, functionNotFoundName: Array<String?>) {
        val jumpto = func.nextLine
        if (jumpto == null || jumpto.nextLine == null || jumpto is FunctionLabelLine || jumpto is NullLine) {
            ParserMediator.warn("SIF文の次の行がありません", func, 2, true, false)
            return
        } else if (jumpto is InstructionLine) {
            if (jumpto.function.isPartial()) ParserMediator.warn("SIF文の次の行を" + jumpto.function.name + "文にすることはできません", func, 2, true, false)
            else func.jumpTo = func.nextLine!!.nextLine
        } else if (jumpto is GotoLabelLine) ParserMediator.warn("SIF文の次の行をラベル行にすることはできません", func, 2, true, false)
        else func.jumpTo = func.nextLine!!.nextLine
        if (func.jumpTo != null && (func.position?.lineNo ?: 0) + 1 != func.nextLine!!.position?.lineNo)
            ParserMediator.warn("SIF文の次の行が空行またはコメント行です(eramaker:SIF文は意味を失います)", func, 0, false, true)
    }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) {
        if ((func.argument as ExpressionArgument).term!!.getIntValue(exm) == 0L) state.shiftNextLine()
    }
}

internal class ELSEIF_Instruction(argtype: FunctionArgType) : AbstractInstruction() {
    init { argBuilder = ArgumentParser.getArgumentBuilder(argtype); flag = METHOD_SAFE or FLOW_CONTROL or PARTIAL or FORCE_SETARG }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) = state.jumpTo(func.jumpTo)
}

internal class ENDIF_Instruction : AbstractInstruction() {
    init { argBuilder = ArgumentParser.getArgumentBuilder(FunctionArgType.VOID); flag = FLOW_CONTROL or PARTIAL or FORCE_SETARG }
    override fun doInstruction(exm: ExpressionMediator, func: InstructionLine, state: ProcessState) {}
}
