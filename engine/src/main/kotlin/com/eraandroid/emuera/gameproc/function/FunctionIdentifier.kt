package com.eraandroid.emuera.gameproc.function

import com.eraandroid.emuera.config.Config
import com.eraandroid.emuera.gamedata.function.FunctionMethod
import com.eraandroid.emuera.gamedata.function.FunctionMethodCreator

class FunctionIdentifier private constructor(
    val name: String,
    val code: FunctionCode,
    val argBuilder: ArgumentBuilder?,
    private val flag: Int,
    val method: FunctionMethod?,
    val instruction: AbstractInstruction?,
) {
    private constructor(name: String, code: FunctionCode, instruction: AbstractInstruction, additionalFlag: Int = 0) :
        this(name, code, instruction.argBuilder, instruction.flag or additionalFlag, null, instruction)
    private constructor(name: String, code: FunctionCode, arg: ArgumentBuilder, flag: Int) :
        this(name, code, arg, flag, null, null)
    private constructor(methodName: String, method: FunctionMethod, instruction: AbstractInstruction) :
        this(methodName, FunctionCode.__NULL__, instruction.argBuilder, instruction.flag, method, instruction)

    fun isFlowContorol() = (flag and FLOW_CONTROL) == FLOW_CONTROL
    fun isExtended() = (flag and EXTENDED) == EXTENDED
    fun isPrintDFunction() = (flag and ISPRINTDFUNC) == ISPRINTDFUNC
    fun isPrintKFunction() = (flag and ISPRINTKFUNC) == ISPRINTKFUNC
    fun isNewLine() = (flag and PRINT_NEWLINE) == PRINT_NEWLINE
    fun isWaitInput() = (flag and PRINT_WAITINPUT) == PRINT_WAITINPUT
    fun isPrintSingle() = (flag and PRINT_SINGLE) == PRINT_SINGLE
    fun isPartial() = (flag and PARTIAL) == PARTIAL
    fun isMethodSafe() = (flag and METHOD_SAFE) == METHOD_SAFE
    fun isPrint() = (flag and IS_PRINT) == IS_PRINT
    fun isInput() = (flag and IS_INPUT) == IS_INPUT
    fun isPrintData() = (flag and IS_PRINTDATA) == IS_PRINTDATA
    fun isForceSetArg() = (flag and FORCE_SETARG) == FORCE_SETARG
    fun isDebug() = (flag and DEBUG_FUNC) == DEBUG_FUNC
    fun isTry() = (flag and IS_TRY) == IS_TRY
    fun isJump() = (flag and IS_JUMP) == IS_JUMP
    fun isMethod() = method != null
    override fun toString() = name

    companion object {

        private val funcDic = LinkedHashMap<String, FunctionIdentifier>()
        private val funcMatch = HashMap<FunctionCode, String>()
        private val funcParent = HashMap<FunctionCode, FunctionCode>()
        private val methodArgumentBuilder: ArgumentBuilder
        private val methodInstruction: AbstractInstruction
        lateinit var SETFunction: FunctionIdentifier
            private set

        private fun addFunction(code: FunctionCode, inst: AbstractInstruction, additionalFlag: Int = 0) {
            var key = code.name
            if (Config.ICFunction) key = key.uppercase()
            funcDic[key] = FunctionIdentifier(key, code, inst, additionalFlag)
        }

        private fun addFunction(code: FunctionCode, arg: ArgumentBuilder, flag: Int = 0) {
            var key = code.name
            if (Config.ICFunction) key = key.uppercase()
            funcDic[key] = FunctionIdentifier(key, code, arg, flag)
        }

        fun getInstructionNameDic(): Map<String, FunctionIdentifier> = funcDic

        private fun addPrintFunction(code: FunctionCode) = addFunction(code, PRINT_Instruction(code.name))
        private fun addPrintDataFunction(code: FunctionCode) = addFunction(code, PRINT_DATA_Instruction(code.name))

        fun getMatchFunction(func: FunctionCode): String? = funcMatch[func]
        fun getParentFunc(func: FunctionCode): FunctionCode = funcParent[func] ?: FunctionCode.__NULL__

        init {
            val argb = ArgumentParser.getArgumentBuilderDictionary()
            methodArgumentBuilder = argb.getValue(FunctionArgType.METHOD)
            methodInstruction = METHOD_Instruction()
            SETFunction = FunctionIdentifier("SET", FunctionCode.SET, SET_Instruction())
        addPrintFunction(FunctionCode.PRINT)
        addPrintFunction(FunctionCode.PRINTL)
        addPrintFunction(FunctionCode.PRINTW)
        addPrintFunction(FunctionCode.PRINTV)
        addPrintFunction(FunctionCode.PRINTVL)
        addPrintFunction(FunctionCode.PRINTVW)
        addPrintFunction(FunctionCode.PRINTS)
        addPrintFunction(FunctionCode.PRINTSL)
        addPrintFunction(FunctionCode.PRINTSW)
        addPrintFunction(FunctionCode.PRINTFORM)
        addPrintFunction(FunctionCode.PRINTFORML)
        addPrintFunction(FunctionCode.PRINTFORMW)
        addPrintFunction(FunctionCode.PRINTFORMS)
        addPrintFunction(FunctionCode.PRINTFORMSL)
        addPrintFunction(FunctionCode.PRINTFORMSW)
        addPrintFunction(FunctionCode.PRINTK)
        addPrintFunction(FunctionCode.PRINTKL)
        addPrintFunction(FunctionCode.PRINTKW)
        addPrintFunction(FunctionCode.PRINTVK)
        addPrintFunction(FunctionCode.PRINTVKL)
        addPrintFunction(FunctionCode.PRINTVKW)
        addPrintFunction(FunctionCode.PRINTSK)
        addPrintFunction(FunctionCode.PRINTSKL)
        addPrintFunction(FunctionCode.PRINTSKW)
        addPrintFunction(FunctionCode.PRINTFORMK)
        addPrintFunction(FunctionCode.PRINTFORMKL)
        addPrintFunction(FunctionCode.PRINTFORMKW)
        addPrintFunction(FunctionCode.PRINTFORMSK)
        addPrintFunction(FunctionCode.PRINTFORMSKL)
        addPrintFunction(FunctionCode.PRINTFORMSKW)
        addPrintFunction(FunctionCode.PRINTD)
        addPrintFunction(FunctionCode.PRINTDL)
        addPrintFunction(FunctionCode.PRINTDW)
        addPrintFunction(FunctionCode.PRINTVD)
        addPrintFunction(FunctionCode.PRINTVDL)
        addPrintFunction(FunctionCode.PRINTVDW)
        addPrintFunction(FunctionCode.PRINTSD)
        addPrintFunction(FunctionCode.PRINTSDL)
        addPrintFunction(FunctionCode.PRINTSDW)
        addPrintFunction(FunctionCode.PRINTFORMD)
        addPrintFunction(FunctionCode.PRINTFORMDL)
        addPrintFunction(FunctionCode.PRINTFORMDW)
        addPrintFunction(FunctionCode.PRINTFORMSD)
        addPrintFunction(FunctionCode.PRINTFORMSDL)
        addPrintFunction(FunctionCode.PRINTFORMSDW)
        addPrintFunction(FunctionCode.PRINTSINGLE)
        addPrintFunction(FunctionCode.PRINTSINGLEV)
        addPrintFunction(FunctionCode.PRINTSINGLES)
        addPrintFunction(FunctionCode.PRINTSINGLEFORM)
        addPrintFunction(FunctionCode.PRINTSINGLEFORMS)
        addPrintFunction(FunctionCode.PRINTSINGLEK)
        addPrintFunction(FunctionCode.PRINTSINGLEVK)
        addPrintFunction(FunctionCode.PRINTSINGLESK)
        addPrintFunction(FunctionCode.PRINTSINGLEFORMK)
        addPrintFunction(FunctionCode.PRINTSINGLEFORMSK)
        addPrintFunction(FunctionCode.PRINTSINGLED)
        addPrintFunction(FunctionCode.PRINTSINGLEVD)
        addPrintFunction(FunctionCode.PRINTSINGLESD)
        addPrintFunction(FunctionCode.PRINTSINGLEFORMD)
        addPrintFunction(FunctionCode.PRINTSINGLEFORMSD)
        addPrintFunction(FunctionCode.PRINTC)
        addPrintFunction(FunctionCode.PRINTLC)
        addPrintFunction(FunctionCode.PRINTFORMC)
        addPrintFunction(FunctionCode.PRINTFORMLC)
        addPrintFunction(FunctionCode.PRINTCK)
        addPrintFunction(FunctionCode.PRINTLCK)
        addPrintFunction(FunctionCode.PRINTFORMCK)
        addPrintFunction(FunctionCode.PRINTFORMLCK)
        addPrintFunction(FunctionCode.PRINTCD)
        addPrintFunction(FunctionCode.PRINTLCD)
        addPrintFunction(FunctionCode.PRINTFORMCD)
        addPrintFunction(FunctionCode.PRINTFORMLCD)
        addPrintDataFunction(FunctionCode.PRINTDATA)
        addPrintDataFunction(FunctionCode.PRINTDATAL)
        addPrintDataFunction(FunctionCode.PRINTDATAW)
        addPrintDataFunction(FunctionCode.PRINTDATAK)
        addPrintDataFunction(FunctionCode.PRINTDATAKL)
        addPrintDataFunction(FunctionCode.PRINTDATAKW)
        addPrintDataFunction(FunctionCode.PRINTDATAD)
        addPrintDataFunction(FunctionCode.PRINTDATADL)
        addPrintDataFunction(FunctionCode.PRINTDATADW)
        addFunction(FunctionCode.PRINTBUTTON, argb.getValue(FunctionArgType.SP_BUTTON), METHOD_SAFE or EXTENDED)
        addFunction(FunctionCode.PRINTBUTTONC, argb.getValue(FunctionArgType.SP_BUTTON), METHOD_SAFE or EXTENDED)
        addFunction(FunctionCode.PRINTBUTTONLC, argb.getValue(FunctionArgType.SP_BUTTON), METHOD_SAFE or EXTENDED)
        addFunction(FunctionCode.PRINTPLAIN, argb.getValue(FunctionArgType.STR_NULLABLE), METHOD_SAFE or EXTENDED)
        addFunction(FunctionCode.PRINTPLAINFORM, argb.getValue(FunctionArgType.FORM_STR_NULLABLE), METHOD_SAFE or EXTENDED)
        addFunction(FunctionCode.PRINT_ABL, argb.getValue(FunctionArgType.INT_EXPRESSION), METHOD_SAFE)
        addFunction(FunctionCode.PRINT_TALENT, argb.getValue(FunctionArgType.INT_EXPRESSION), METHOD_SAFE)
        addFunction(FunctionCode.PRINT_MARK, argb.getValue(FunctionArgType.INT_EXPRESSION), METHOD_SAFE)
        addFunction(FunctionCode.PRINT_EXP, argb.getValue(FunctionArgType.INT_EXPRESSION), METHOD_SAFE)
        addFunction(FunctionCode.PRINT_PALAM, argb.getValue(FunctionArgType.INT_EXPRESSION), METHOD_SAFE)
        addFunction(FunctionCode.PRINT_ITEM, argb.getValue(FunctionArgType.VOID), METHOD_SAFE)
        addFunction(FunctionCode.PRINT_SHOPITEM, argb.getValue(FunctionArgType.VOID), METHOD_SAFE)
        addFunction(FunctionCode.DRAWLINE, argb.getValue(FunctionArgType.VOID), METHOD_SAFE)
        addFunction(FunctionCode.BAR, BAR_Instruction(false))
        addFunction(FunctionCode.BARL, BAR_Instruction(true))
        addFunction(FunctionCode.TIMES, TIMES_Instruction())
        addFunction(FunctionCode.WAIT, WAIT_Instruction(false))
        addFunction(FunctionCode.INPUT, INPUT_Instruction())
        addFunction(FunctionCode.INPUTS, INPUTS_Instruction())
        addFunction(FunctionCode.TINPUT, TINPUT_Instruction(false))
        addFunction(FunctionCode.TINPUTS, TINPUTS_Instruction(false))
        addFunction(FunctionCode.TONEINPUT, TINPUT_Instruction(true))
        addFunction(FunctionCode.TONEINPUTS, TINPUTS_Instruction(true))
        addFunction(FunctionCode.TWAIT, TWAIT_Instruction())
        addFunction(FunctionCode.WAITANYKEY, WAITANYKEY_Instruction())
        addFunction(FunctionCode.FORCEWAIT, WAIT_Instruction(true))
        addFunction(FunctionCode.ONEINPUT, ONEINPUT_Instruction())
        addFunction(FunctionCode.ONEINPUTS, ONEINPUTS_Instruction())
        addFunction(FunctionCode.CLEARLINE, CLEARLINE_Instruction())
        addFunction(FunctionCode.REUSELASTLINE, REUSELASTLINE_Instruction())
        addFunction(FunctionCode.UPCHECK, argb.getValue(FunctionArgType.VOID), METHOD_SAFE)
        addFunction(FunctionCode.CUPCHECK, argb.getValue(FunctionArgType.INT_EXPRESSION), METHOD_SAFE or EXTENDED)
        addFunction(FunctionCode.ADDCHARA, ADDCHARA_Instruction(false, false))
        addFunction(FunctionCode.ADDSPCHARA, ADDCHARA_Instruction(true, false))
        addFunction(FunctionCode.ADDDEFCHARA, argb.getValue(FunctionArgType.VOID), METHOD_SAFE or EXTENDED)
        addFunction(FunctionCode.ADDVOIDCHARA, ADDVOIDCHARA_Instruction())
        addFunction(FunctionCode.DELCHARA, ADDCHARA_Instruction(false, true))
        addFunction(FunctionCode.PUTFORM, argb.getValue(FunctionArgType.FORM_STR_NULLABLE), METHOD_SAFE)
        addFunction(FunctionCode.QUIT, argb.getValue(FunctionArgType.VOID)).
        addFunction(FunctionCode.OUTPUTLOG, argb.getValue(FunctionArgType.VOID)).
        addFunction(FunctionCode.BEGIN, BEGIN_Instruction())
        addFunction(FunctionCode.SAVEGAME, SAVELOADGAME_Instruction(true))
        addFunction(FunctionCode.LOADGAME, SAVELOADGAME_Instruction(false))
        addFunction(FunctionCode.SAVEDATA, argb.getValue(FunctionArgType.SP_SAVEDATA), METHOD_SAFE or EXTENDED)
        addFunction(FunctionCode.LOADDATA, argb.getValue(FunctionArgType.INT_EXPRESSION), EXTENDED or FLOW_CONTROL)
        addFunction(FunctionCode.DELDATA, DELDATA_Instruction())
        addFunction(FunctionCode.SAVEGLOBAL, SAVEGLOBAL_Instruction())
        addFunction(FunctionCode.LOADGLOBAL, LOADGLOBAL_Instruction())
        addFunction(FunctionCode.RESETDATA, RESETDATA_Instruction())
        addFunction(FunctionCode.RESETGLOBAL, RESETGLOBAL_Instruction())
        addFunction(FunctionCode.SIF, SIF_Instruction())
        addFunction(FunctionCode.IF, IF_Instruction())
        addFunction(FunctionCode.ELSE, ELSEIF_Instruction(FunctionArgType.VOID))
        addFunction(FunctionCode.ELSEIF, ELSEIF_Instruction(FunctionArgType.INT_EXPRESSION))
        addFunction(FunctionCode.ENDIF, ENDIF_Instruction(), METHOD_SAFE)
        addFunction(FunctionCode.SELECTCASE, SELECTCASE_Instruction())
        addFunction(FunctionCode.CASE, ELSEIF_Instruction(FunctionArgType.CASE), EXTENDED)
        addFunction(FunctionCode.CASEELSE, ELSEIF_Instruction(FunctionArgType.VOID), EXTENDED)
        addFunction(FunctionCode.ENDSELECT, ENDIF_Instruction(), METHOD_SAFE or EXTENDED)
        addFunction(FunctionCode.REPEAT, REPEAT_Instruction(false))
        addFunction(FunctionCode.REND, REND_Instruction())
        addFunction(FunctionCode.FOR, REPEAT_Instruction(true), EXTENDED)
        addFunction(FunctionCode.NEXT, REND_Instruction(), EXTENDED)
        addFunction(FunctionCode.WHILE, WHILE_Instruction())
        addFunction(FunctionCode.WEND, WEND_Instruction())
        addFunction(FunctionCode.DO, ENDIF_Instruction(), METHOD_SAFE or EXTENDED)
        addFunction(FunctionCode.LOOP, LOOP_Instruction())
        addFunction(FunctionCode.CONTINUE, CONTINUE_Instruction())
        addFunction(FunctionCode.BREAK, BREAK_Instruction())
        addFunction(FunctionCode.RETURN, RETURN_Instruction())
        addFunction(FunctionCode.RETURNFORM, RETURNFORM_Instruction())
        addFunction(FunctionCode.RETURNF, RETURNF_Instruction())
        addFunction(FunctionCode.STRLEN, STRLEN_Instruction(false, false))
        addFunction(FunctionCode.STRLENFORM, STRLEN_Instruction(true, false))
        addFunction(FunctionCode.STRLENU, STRLEN_Instruction(false, true))
        addFunction(FunctionCode.STRLENFORMU, STRLEN_Instruction(true, true))
        addFunction(FunctionCode.SWAPCHARA, SWAPCHARA_Instruction())
        addFunction(FunctionCode.COPYCHARA, COPYCHARA_Instruction())
        addFunction(FunctionCode.ADDCOPYCHARA, ADDCOPYCHARA_Instruction())
        addFunction(FunctionCode.SPLIT, argb.getValue(FunctionArgType.SP_SPLIT), METHOD_SAFE or EXTENDED)
        addFunction(FunctionCode.SETCOLOR, argb.getValue(FunctionArgType.SP_COLOR), METHOD_SAFE or EXTENDED)
        addFunction(FunctionCode.SETCOLORBYNAME, argb.getValue(FunctionArgType.STR), METHOD_SAFE or EXTENDED)
        addFunction(FunctionCode.RESETCOLOR, RESETCOLOR_Instruction())
        addFunction(FunctionCode.SETBGCOLOR, argb.getValue(FunctionArgType.SP_COLOR), METHOD_SAFE or EXTENDED)
        addFunction(FunctionCode.SETBGCOLORBYNAME, argb.getValue(FunctionArgType.STR), METHOD_SAFE or EXTENDED)
        addFunction(FunctionCode.RESETBGCOLOR, RESETBGCOLOR_Instruction())
        addFunction(FunctionCode.FONTBOLD, FONTBOLD_Instruction())
        addFunction(FunctionCode.FONTITALIC, FONTITALIC_Instruction())
        addFunction(FunctionCode.FONTREGULAR, FONTREGULAR_Instruction())
        addFunction(FunctionCode.SORTCHARA, SORTCHARA_Instruction())
        addFunction(FunctionCode.FONTSTYLE, argb.getValue(FunctionArgType.INT_EXPRESSION_NULLABLE), METHOD_SAFE or EXTENDED)
        addFunction(FunctionCode.ALIGNMENT, argb.getValue(FunctionArgType.STR), METHOD_SAFE or EXTENDED)
        addFunction(FunctionCode.CUSTOMDRAWLINE, CUSTOMDRAWLINE_Instruction())
        addFunction(FunctionCode.DRAWLINEFORM, argb.getValue(FunctionArgType.FORM_STR), METHOD_SAFE or EXTENDED)
        addFunction(FunctionCode.CLEARTEXTBOX, argb.getValue(FunctionArgType.VOID), METHOD_SAFE or EXTENDED)
        addFunction(FunctionCode.SETFONT, argb.getValue(FunctionArgType.STR_EXPRESSION_NULLABLE), METHOD_SAFE or EXTENDED)
        addFunction(FunctionCode.SWAP, argb.getValue(FunctionArgType.SP_SWAPVAR), METHOD_SAFE or EXTENDED)
        addFunction(FunctionCode.RANDOMIZE, RANDOMIZE_Instruction())
        addFunction(FunctionCode.DUMPRAND, DUMPRAND_Instruction())
        addFunction(FunctionCode.INITRAND, INITRAND_Instruction())
        addFunction(FunctionCode.REDRAW, argb.getValue(FunctionArgType.INT_EXPRESSION), METHOD_SAFE or EXTENDED)
        addFunction(FunctionCode.CALLTRAIN, argb.getValue(FunctionArgType.INT_EXPRESSION), EXTENDED or FLOW_CONTROL)
        addFunction(FunctionCode.STOPCALLTRAIN, argb.getValue(FunctionArgType.VOID), EXTENDED or FLOW_CONTROL)
        addFunction(FunctionCode.DOTRAIN, argb.getValue(FunctionArgType.INT_EXPRESSION), EXTENDED or FLOW_CONTROL)
        addFunction(FunctionCode.DATA, argb.getValue(FunctionArgType.STR_NULLABLE), METHOD_SAFE or EXTENDED or PARTIAL or PARTIAL)
        addFunction(FunctionCode.DATAFORM, argb.getValue(FunctionArgType.FORM_STR_NULLABLE), METHOD_SAFE or EXTENDED or PARTIAL)
        addFunction(FunctionCode.ENDDATA, DO_NOTHING_Instruction())
        addFunction(FunctionCode.DATALIST, argb.getValue(FunctionArgType.VOID), METHOD_SAFE or EXTENDED or PARTIAL)
        addFunction(FunctionCode.ENDLIST, argb.getValue(FunctionArgType.VOID), METHOD_SAFE or EXTENDED or PARTIAL)
        addFunction(FunctionCode.STRDATA, argb.getValue(FunctionArgType.VAR_STR), METHOD_SAFE or EXTENDED or PARTIAL)
        addFunction(FunctionCode.SETBIT, SETBIT_Instruction(1))
        addFunction(FunctionCode.CLEARBIT, SETBIT_Instruction(0))
        addFunction(FunctionCode.INVERTBIT, SETBIT_Instruction(-1))
        addFunction(FunctionCode.DELALLCHARA, argb.getValue(FunctionArgType.VOID), METHOD_SAFE or EXTENDED)
        addFunction(FunctionCode.PICKUPCHARA, argb.getValue(FunctionArgType.INT_ANY), METHOD_SAFE or EXTENDED)
        addFunction(FunctionCode.VARSET, VARSET_Instruction())
        addFunction(FunctionCode.CVARSET, CVARSET_Instruction())
        addFunction(FunctionCode.RESET_STAIN, argb.getValue(FunctionArgType.INT_EXPRESSION), METHOD_SAFE or EXTENDED)
        addFunction(FunctionCode.FORCEKANA, argb.getValue(FunctionArgType.INT_EXPRESSION), METHOD_SAFE or EXTENDED)
        addFunction(FunctionCode.SKIPDISP, argb.getValue(FunctionArgType.INT_EXPRESSION), METHOD_SAFE or EXTENDED)
        addFunction(FunctionCode.NOSKIP, argb.getValue(FunctionArgType.VOID), METHOD_SAFE or EXTENDED or PARTIAL)
        addFunction(FunctionCode.ENDNOSKIP, argb.getValue(FunctionArgType.VOID), METHOD_SAFE or EXTENDED or PARTIAL)
        addFunction(FunctionCode.ARRAYSHIFT, argb.getValue(FunctionArgType.SP_SHIFT_ARRAY), METHOD_SAFE or EXTENDED)
        addFunction(FunctionCode.ARRAYREMOVE, argb.getValue(FunctionArgType.SP_CONTROL_ARRAY), METHOD_SAFE or EXTENDED)
        addFunction(FunctionCode.ARRAYSORT, argb.getValue(FunctionArgType.SP_SORTARRAY), METHOD_SAFE or EXTENDED)
        addFunction(FunctionCode.ARRAYCOPY, argb.getValue(FunctionArgType.SP_COPY_ARRAY), METHOD_SAFE or EXTENDED)
        addFunction(FunctionCode.JUMP, CALL_Instruction(false, true, false, false))
        addFunction(FunctionCode.CALL, CALL_Instruction(false, false, false, false))
        addFunction(FunctionCode.TRYJUMP, CALL_Instruction(false, true, true, false), EXTENDED)
        addFunction(FunctionCode.TRYCALL, CALL_Instruction(false, false, true, false), EXTENDED)
        addFunction(FunctionCode.JUMPFORM, CALL_Instruction(true, true, false, false), EXTENDED)
        addFunction(FunctionCode.CALLFORM, CALL_Instruction(true, false, false, false), EXTENDED)
        addFunction(FunctionCode.TRYJUMPFORM, CALL_Instruction(true, true, true, false), EXTENDED)
        addFunction(FunctionCode.TRYCALLFORM, CALL_Instruction(true, false, true, false), EXTENDED)
        addFunction(FunctionCode.TRYCJUMP, CALL_Instruction(false, true, true, true), EXTENDED)
        addFunction(FunctionCode.TRYCCALL, CALL_Instruction(false, false, true, true), EXTENDED)
        addFunction(FunctionCode.TRYCJUMPFORM, CALL_Instruction(true, true, true, true), EXTENDED)
        addFunction(FunctionCode.TRYCCALLFORM, CALL_Instruction(true, false, true, true), EXTENDED)
        addFunction(FunctionCode.CALLEVENT, CALLEVENT_Instruction())
        addFunction(FunctionCode.CALLF, CALLF_Instruction(false))
        addFunction(FunctionCode.CALLFORMF, CALLF_Instruction(true))
        addFunction(FunctionCode.RESTART, RESTART_Instruction())
        addFunction(FunctionCode.GOTO, GOTO_Instruction(false, false, false))
        addFunction(FunctionCode.TRYGOTO, GOTO_Instruction(false, true, false), EXTENDED)
        addFunction(FunctionCode.GOTOFORM, GOTO_Instruction(true, false, false), EXTENDED)
        addFunction(FunctionCode.TRYGOTOFORM, GOTO_Instruction(true, true, false), EXTENDED)
        addFunction(FunctionCode.TRYCGOTO, GOTO_Instruction(false, true, true), EXTENDED)
        addFunction(FunctionCode.TRYCGOTOFORM, GOTO_Instruction(true, true, true), EXTENDED)
        addFunction(FunctionCode.CATCH, CATCH_Instruction())
        addFunction(FunctionCode.ENDCATCH, ENDIF_Instruction(), METHOD_SAFE or EXTENDED)
        addFunction(FunctionCode.TRYCALLLIST, argb.getValue(FunctionArgType.VOID), EXTENDED or FLOW_CONTROL or PARTIAL or IS_TRY)
        addFunction(FunctionCode.TRYJUMPLIST, argb.getValue(FunctionArgType.VOID), EXTENDED or FLOW_CONTROL or PARTIAL or IS_JUMP or IS_TRY)
        addFunction(FunctionCode.TRYGOTOLIST, argb.getValue(FunctionArgType.VOID), EXTENDED or FLOW_CONTROL or PARTIAL or IS_TRY)
        addFunction(FunctionCode.FUNC, argb.getValue(FunctionArgType.SP_CALLFORM), EXTENDED or FLOW_CONTROL or PARTIAL or FORCE_SETARG)
        addFunction(FunctionCode.ENDFUNC, ENDIF_Instruction(), EXTENDED)
        addFunction(FunctionCode.DEBUGPRINT, DEBUGPRINT_Instruction(false, false))
        addFunction(FunctionCode.DEBUGPRINTL, DEBUGPRINT_Instruction(false, true))
        addFunction(FunctionCode.DEBUGPRINTFORM, DEBUGPRINT_Instruction(true, false))
        addFunction(FunctionCode.DEBUGPRINTFORML, DEBUGPRINT_Instruction(true, true))
        addFunction(FunctionCode.DEBUGCLEAR, DEBUGCLEAR_Instruction())
        addFunction(FunctionCode.ASSERT, argb.getValue(FunctionArgType.INT_EXPRESSION), METHOD_SAFE or EXTENDED or DEBUG_FUNC)
        addFunction(FunctionCode.THROW, argb.getValue(FunctionArgType.FORM_STR_NULLABLE), METHOD_SAFE or EXTENDED)
        addFunction(FunctionCode.SAVEVAR, SAVEVAR_Instruction())
        addFunction(FunctionCode.LOADVAR, LOADVAR_Instruction())
        addFunction(FunctionCode.SAVECHARA, SAVECHARA_Instruction())
        addFunction(FunctionCode.LOADCHARA, LOADCHARA_Instruction())
        addFunction(FunctionCode.REF, REF_Instruction(false))
        addFunction(FunctionCode.REFBYNAME, REF_Instruction(true))
        addFunction(FunctionCode.HTML_PRINT, HTML_PRINT_Instruction())
        addFunction(FunctionCode.HTML_TAGSPLIT, HTML_TAGSPLIT_Instruction())
        addFunction(FunctionCode.PRINT_IMG, PRINT_IMG_Instruction())
        addFunction(FunctionCode.PRINT_RECT, PRINT_RECT_Instruction())
        addFunction(FunctionCode.PRINT_SPACE, PRINT_SPACE_Instruction())
        addFunction(FunctionCode.TOOLTIP_SETCOLOR, TOOLTIP_SETCOLOR_Instruction())
        addFunction(FunctionCode.TOOLTIP_SETDELAY, TOOLTIP_SETDELAY_Instruction())
        addFunction(FunctionCode.TOOLTIP_SETDURATION, TOOLTIP_SETDURATION_Instruction())
        addFunction(FunctionCode.INPUTMOUSEKEY, INPUTMOUSEKEY_Instruction())
        addFunction(FunctionCode.AWAIT, AWAIT_Instruction())
        addFunction(FunctionCode.VARSIZE, argb.getValue(FunctionArgType.SP_VAR), METHOD_SAFE or EXTENDED)
        addFunction(FunctionCode.GETTIME, argb.getValue(FunctionArgType.VOID), METHOD_SAFE or EXTENDED)
        addFunction(FunctionCode.POWER, argb.getValue(FunctionArgType.SP_POWER), METHOD_SAFE or EXTENDED)
        addFunction(FunctionCode.PRINTCPERLINE, argb.getValue(FunctionArgType.SP_GETINT), METHOD_SAFE or EXTENDED)
        addFunction(FunctionCode.SAVENOS, argb.getValue(FunctionArgType.SP_GETINT), METHOD_SAFE or EXTENDED)
        addFunction(FunctionCode.ENCODETOUNI, argb.getValue(FunctionArgType.FORM_STR_NULLABLE), METHOD_SAFE or EXTENDED)
        for ((key, m) in FunctionMethodCreator.getMethodList()) if (!funcDic.containsKey(key)) funcDic[key] = FunctionIdentifier(key, m, methodInstruction)
        funcMatch[FunctionCode.IF] = "ENDIF"
        funcMatch[FunctionCode.SELECTCASE] = "ENDSELECT"
        funcMatch[FunctionCode.REPEAT] = "REND"
        funcMatch[FunctionCode.FOR] = "NEXT"
        funcMatch[FunctionCode.WHILE] = "WEND"
        funcMatch[FunctionCode.TRYCGOTO] = "CATCH"
        funcMatch[FunctionCode.TRYCJUMP] = "CATCH"
        funcMatch[FunctionCode.TRYCCALL] = "CATCH"
        funcMatch[FunctionCode.TRYCGOTOFORM] = "CATCH"
        funcMatch[FunctionCode.TRYCJUMPFORM] = "CATCH"
        funcMatch[FunctionCode.TRYCCALLFORM] = "CATCH"
        funcMatch[FunctionCode.CATCH] = "ENDCATCH"
        funcMatch[FunctionCode.DO] = "LOOP"
        funcMatch[FunctionCode.PRINTDATA] = "ENDDATA"
        funcMatch[FunctionCode.PRINTDATAL] = "ENDDATA"
        funcMatch[FunctionCode.PRINTDATAW] = "ENDDATA"
        funcMatch[FunctionCode.PRINTDATAK] = "ENDDATA"
        funcMatch[FunctionCode.PRINTDATAKL] = "ENDDATA"
        funcMatch[FunctionCode.PRINTDATAKW] = "ENDDATA"
        funcMatch[FunctionCode.PRINTDATAD] = "ENDDATA"
        funcMatch[FunctionCode.PRINTDATADL] = "ENDDATA"
        funcMatch[FunctionCode.PRINTDATADW] = "ENDDATA"
        funcMatch[FunctionCode.DATALIST] = "ENDLIST"
        funcMatch[FunctionCode.STRDATA] = "ENDDATA"
        funcMatch[FunctionCode.NOSKIP] = "ENDNOSKIP"
        funcMatch[FunctionCode.TRYCALLLIST] = "ENDFUNC"
        funcMatch[FunctionCode.TRYGOTOLIST] = "ENDFUNC"
        funcMatch[FunctionCode.TRYJUMPLIST] = "ENDFUNC"
        funcParent[FunctionCode.REND] = FunctionCode.REPEAT
        funcParent[FunctionCode.NEXT] = FunctionCode.FOR
        funcParent[FunctionCode.WEND] = FunctionCode.WHILE
        funcParent[FunctionCode.LOOP] = FunctionCode.DO
        }
    }
}
