package com.eraandroid.emuera

import com.eraandroid.emuera.gamedata.ConstantData
import com.eraandroid.emuera.gamedata.GameBase
import com.eraandroid.emuera.gamedata.expression.ExpressionMediator
import com.eraandroid.emuera.gamedata.variable.VariableData
import com.eraandroid.emuera.gamedata.variable.VariableEvaluator
import com.eraandroid.emuera.gameproc.LabelDictionary
import com.eraandroid.emuera.gameproc.Process
import com.eraandroid.emuera.gameview.EmueraConsole

/** 行儀の悪い参照の仕方をするものたちをせめて一箇所に集めて管理する */
object GlobalStatic {
    @JvmField var Console: EmueraConsole? = null
    @JvmField var Process: Process? = null
    @JvmField var GameBaseData: GameBase? = null
    @JvmField var ConstantData: ConstantData? = null
    @JvmField var VariableData: VariableData? = null
    @JvmField var VEvaluator: VariableEvaluator? = null
    @JvmField var IdentifierDictionary: IdentifierDictionary? = null
    @JvmField var EMediator: ExpressionMediator? = null
    @JvmField var LabelDictionary: LabelDictionary? = null
    @JvmField val tempDic: MutableMap<String, Long> = HashMap()

    fun reset() {
        Process = null
        ConstantData = null
        GameBaseData = null
        EMediator = null
        VEvaluator = null
        VariableData = null
        Console = null
        LabelDictionary = null
        IdentifierDictionary = null
        tempDic.clear()
    }
}
