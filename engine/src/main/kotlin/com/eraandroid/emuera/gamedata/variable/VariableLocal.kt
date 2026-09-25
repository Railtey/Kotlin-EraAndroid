package com.eraandroid.emuera.gamedata.variable

import com.eraandroid.emuera.GlobalStatic
import com.eraandroid.emuera.gamedata.ParserMediator
import com.eraandroid.emuera.gameproc.FunctionLabelLine

class VariableLocal(
    private val varCode: Int,
    private val size: Int,
    private val creater: (Int, String, Int) -> LocalVariableToken
) {
    val isForbid: Boolean get() = size == 0
    private val localVarTokens = HashMap<String, LocalVariableToken>()

    fun getExistLocalVariableToken(subKey: String): LocalVariableToken? = localVarTokens[subKey]

    fun getDefaultSize(): Int = size

    fun getNewLocalVariableToken(subKey: String, func: FunctionLabelLine): LocalVariableToken {
        val ret: LocalVariableToken
        var newSize = 0
        when (varCode) {
            VariableCode.LOCAL -> newSize = func.localLength
            VariableCode.LOCALS -> newSize = func.localsLength
            VariableCode.ARG -> newSize = func.argLength
            VariableCode.ARGS -> newSize = func.argsLength
        }
        if (newSize > 0) {
            if (newSize < size && (varCode == VariableCode.ARG || varCode == VariableCode.ARGS)) newSize = size
            ret = creater(varCode, subKey, newSize)
        } else if (newSize == 0) {
            ret = creater(varCode, subKey, size)
        } else {
            ret = creater(varCode, subKey, size)
            val line = GlobalStatic.Process?.getScaningLine()
            if (line != null) {
                val vname = VariableCode.toString(varCode)
                if (!func.isSystem)
                    ParserMediator.warn("関数宣言に引数変数\"$vname\"が使われていない関数中で\"$vname\"が使われています(関数の引数以外の用途に使うことは推奨されません。代わりに#DIMの使用を検討してください)", line, 1, false, false)
                else
                    ParserMediator.warn("システム関数${func.labelName}中で\"$vname\"が使われています(関数の引数以外の用途に使うことは推奨されません。代わりに#DIMの使用を検討してください)", line, 1, false, false)
            }
        }
        localVarTokens[subKey] = ret
        return ret
    }

    fun resizeLocalVariableToken(subKey: String, newSize: Int) {
        val existing = localVarTokens[subKey]
        if (existing != null) {
            if (size < newSize) existing.resize(newSize) else existing.resize(size)
        } else {
            val ret = when {
                newSize > size -> creater(varCode, subKey, newSize)
                newSize == 0 -> creater(varCode, subKey, size)
                else -> return
            }
            localVarTokens[subKey] = ret
        }
    }

    fun clear() = localVarTokens.clear()

    fun setDefault() { for (t in localVarTokens.values) t.setDefault() }
}
