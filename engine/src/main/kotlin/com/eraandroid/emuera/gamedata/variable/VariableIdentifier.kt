package com.eraandroid.emuera.gamedata.variable

import com.eraandroid.emuera.config.Config
import com.eraandroid.emuera.sub.CodeEE

/** VariableCodeのラッパー */
class VariableIdentifier private constructor(val code: Int, val scope: String? = null) {
    val codeInt: Int get() = code and VariableCode.__LOWERCASE__
    val codeFlag: Int get() = code and VariableCode.__UPPERCASE__

    private fun has(flag: Int) = (code and flag) == flag
    val isNull: Boolean get() = code == VariableCode.__NULL__
    val isCharacterData: Boolean get() = has(VariableCode.__CHARACTER_DATA__)
    val isInteger: Boolean get() = has(VariableCode.__INTEGER__)
    val isString: Boolean get() = has(VariableCode.__STRING__)
    val isArray1D: Boolean get() = has(VariableCode.__ARRAY_1D__)
    val isArray2D: Boolean get() = has(VariableCode.__ARRAY_2D__)
    val isArray3D: Boolean get() = has(VariableCode.__ARRAY_3D__)
    val readonly: Boolean get() = has(VariableCode.__UNCHANGEABLE__)
    val isCalc: Boolean get() = has(VariableCode.__CALC__)
    val isLocal: Boolean get() = has(VariableCode.__LOCAL__)
    val canForbid: Boolean get() = has(VariableCode.__CAN_FORBID__)

    override fun toString(): String = VariableCode.toString(code)

    companion object {
        private val nameDic = LinkedHashMap<String, Int>()
        private val localvarNameDic = HashMap<String, Int>()
        private val extSaveListDic = HashMap<Int, MutableList<Int>>()
        private var initialized = false

        private const val SAVE_FLAG_MASK = VariableCode.__ARRAY_1D__ or VariableCode.__ARRAY_2D__ or VariableCode.__ARRAY_3D__ or
            VariableCode.__CHARACTER_DATA__ or VariableCode.__STRING__ or VariableCode.__INTEGER__

        @Synchronized
        fun initialize() {
            nameDic.clear(); localvarNameDic.clear(); extSaveListDic.clear()
            nameDic["__FILE__"] = VariableCode.__FILE__
            nameDic["__LINE__"] = VariableCode.__LINE__
            nameDic["__FUNCTION__"] = VariableCode.__FUNCTION__
            for ((name, code) in VariableCode.allNames) {
                var key = name
                if (key.startsWith("__") && key.endsWith("__")) continue
                if (Config.ICVariable) key = key.uppercase()
                if (nameDic.containsKey(key)) continue
                nameDic[key] = code
                if ((code and VariableCode.__LOCAL__) == VariableCode.__LOCAL__) localvarNameDic[key] = code
                if ((code and VariableCode.__SAVE_EXTENDED__) == VariableCode.__SAVE_EXTENDED__) {
                    val flag = code and SAVE_FLAG_MASK
                    extSaveListDic.getOrPut(flag) { ArrayList() }.add(code)
                }
            }
            initialized = true
        }

        private fun ensure() { if (!initialized) initialize() }

        fun getVarNameDic(): Map<String, Int> { ensure(); return nameDic }

        fun getExtSaveList(flag: Int): List<Int> {
            ensure()
            return extSaveListDic[flag and SAVE_FLAG_MASK] ?: emptyList()
        }

        fun getVariableId(code: Int): VariableIdentifier = VariableIdentifier(code)

        fun getVariableId(key: String?): VariableIdentifier? = getVariableId(key, null)

        fun getVariableId(keyIn: String?, subStrIn: String?): VariableIdentifier? {
            ensure()
            if (keyIn.isNullOrEmpty()) return null
            var key = keyIn
            var subStr = subStrIn
            if (Config.ICVariable) key = key.uppercase()
            if (subStr != null) {
                if (Config.ICFunction) subStr = subStr.uppercase()
                localvarNameDic[key]?.let { return VariableIdentifier(it, subStr) }
                if (nameDic.containsKey(key)) throw CodeEE("ローカル変数でない変数${key}に対して@が使われました")
                throw CodeEE("@の使い方が不正です")
            }
            return nameDic[key]?.let { VariableIdentifier(it) }
        }
    }
}
