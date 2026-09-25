package com.eraandroid.emuera.gamedata.variable

import com.eraandroid.emuera.GlobalStatic
import com.eraandroid.emuera.config.Config
import com.eraandroid.emuera.gamedata.expression.EType
import com.eraandroid.emuera.gamedata.expression.ExpressionMediator
import com.eraandroid.emuera.gameproc.UserDefinedVariableData
import com.eraandroid.emuera.sub.ArrayRangeCodeEE
import com.eraandroid.emuera.sub.CodeEE

/**
 * 変数トークン。IndexOutOfBoundsExceptionを投げることがある。VariableTermの方で処理すること。
 */
abstract class VariableToken protected constructor(val code: Int, protected val varData: VariableData?) {
    val varCodeInt: Int = code and VariableCode.__LOWERCASE__
    val variableType: EType = if ((code and VariableCode.__INTEGER__) == VariableCode.__INTEGER__) EType.Int64 else EType.String
    protected var varName: String = VariableCode.toString(code)
    var canRestructure = false
        protected set
    val name: String get() = varName

    var isForbid = false
        protected set
    var isPrivate = false
        protected set
    var isGlobal = (code == VariableCode.GLOBAL) || (code == VariableCode.GLOBALS)
        protected set
    var isSavedata = false
        protected set
    var isReference = false
        protected set
    var dimension = 0
        protected set

    init {
        if (has(VariableCode.__ARRAY_1D__)) dimension = 1
        if (has(VariableCode.__ARRAY_2D__)) dimension = 2
        if (has(VariableCode.__ARRAY_3D__)) dimension = 3
        if (code == VariableCode.GLOBAL || code == VariableCode.GLOBALS) isSavedata = true
        else if (has(VariableCode.__SAVE_EXTENDED__)) isSavedata = true
        else if (!has(VariableCode.__EXTENDED__) && !has(VariableCode.__CALC__) && !has(VariableCode.__UNCHANGEABLE__) &&
            !has(VariableCode.__LOCAL__) && !varName.startsWith("NOTUSE_")) {
            val flag = code and (VariableCode.__ARRAY_1D__ or VariableCode.__ARRAY_2D__ or VariableCode.__ARRAY_3D__ or
                VariableCode.__STRING__ or VariableCode.__INTEGER__ or VariableCode.__CHARACTER_DATA__)
            val v = varCodeInt
            isSavedata = when (flag) {
                VariableCode.__CHARACTER_DATA__ or VariableCode.__INTEGER__ -> v < VariableCode.__COUNT_SAVE_CHARACTER_INTEGER__
                VariableCode.__CHARACTER_DATA__ or VariableCode.__STRING__ -> v < VariableCode.__COUNT_SAVE_CHARACTER_STRING__
                VariableCode.__CHARACTER_DATA__ or VariableCode.__INTEGER__ or VariableCode.__ARRAY_1D__ -> v < VariableCode.__COUNT_SAVE_CHARACTER_INTEGER_ARRAY__
                VariableCode.__CHARACTER_DATA__ or VariableCode.__STRING__ or VariableCode.__ARRAY_1D__ -> v < VariableCode.__COUNT_SAVE_CHARACTER_STRING_ARRAY__
                VariableCode.__INTEGER__ -> v < VariableCode.__COUNT_SAVE_INTEGER__
                VariableCode.__STRING__ -> v < VariableCode.__COUNT_SAVE_STRING__
                VariableCode.__INTEGER__ or VariableCode.__ARRAY_1D__ -> v < VariableCode.__COUNT_SAVE_INTEGER_ARRAY__
                VariableCode.__STRING__ or VariableCode.__ARRAY_1D__ -> v < VariableCode.__COUNT_SAVE_STRING_ARRAY__
                else -> false
            }
        }
    }

    protected fun has(flag: Int) = (code and flag) == flag

    open fun getIntValue(exm: ExpressionMediator, arguments: LongArray): Long = throw CodeEE("整数型でない変数${varName}を整数型として呼び出しました")
    open fun getStrValue(exm: ExpressionMediator, arguments: LongArray): String? = throw CodeEE("文字列型でない変数${varName}を文字列型として呼び出しました")
    open fun setValue(value: Long, arguments: LongArray?): Unit = throw CodeEE("整数型でない変数${varName}を整数型として呼び出しました")
    open fun setValue(value: String?, arguments: LongArray?): Unit = throw CodeEE("文字列型でない変数${varName}を文字列型として呼び出しました")
    open fun setValues(values: LongArray, arguments: LongArray): Unit = throw CodeEE("整数型配列でない変数${varName}を整数型配列として呼び出しました")
    open fun setValues(values: Array<String?>, arguments: LongArray): Unit = throw CodeEE("文字列型配列でない変数${varName}を文字列型配列として呼び出しました")
    open fun setValueAll(value: Long, start: Int, end: Int, charaPos: Int): Unit = throw CodeEE("整数型配列でない変数${varName}を整数型配列として呼び出しました")
    open fun setValueAll(value: String?, start: Int, end: Int, charaPos: Int): Unit = throw CodeEE("文字列型配列でない変数${varName}を文字列型配列として呼び出しました")
    open fun plusValue(value: Long, arguments: LongArray): Long = throw CodeEE("整数型でない変数${varName}を整数型として呼び出しました")
    open fun getLength(): Int = throw CodeEE("配列型でない変数${varName}の長さを取得しようとしました")
    open fun getLength(dimension: Int): Int = throw CodeEE("配列型でない変数${varName}の長さを取得しようとしました")
    open fun getArray(): Any {
        if (isCharacterData) throw CodeEE("キャラクタ変数${varName}を非キャラ変数として呼び出しました")
        throw CodeEE("配列型でない変数${varName}の配列を取得しようとしました")
    }
    open fun getArrayChara(charano: Int): Any {
        if (!isCharacterData) throw CodeEE("非キャラクタ変数${varName}をキャラ変数として呼び出しました")
        throw CodeEE("配列型でない変数${varName}の配列を取得しようとしました")
    }

    fun throwOutOfRangeException(arguments: LongArray, e: Exception): Nothing {
        checkElement(arguments, booleanArrayOf(true, true, true))
        throw e
    }
    open fun checkElement(arguments: LongArray, doCheck: BooleanArray) {}
    fun checkElement(arguments: LongArray) = checkElement(arguments, booleanArrayOf(true, true, true))
    open fun isArrayRangeValid(arguments: LongArray, index1: Long, index2: Long, funcName: String, i1: Long, i2: Long) {
        checkElement(arguments, booleanArrayOf(true, true, true))
    }

    val codeInt: Int get() = varCodeInt
    val codeFlag: Int get() = code and VariableCode.__UPPERCASE__
    val isNull: Boolean get() = code == VariableCode.__NULL__
    val isCharacterData: Boolean get() = has(VariableCode.__CHARACTER_DATA__)
    val isInteger: Boolean get() = has(VariableCode.__INTEGER__)
    val isString: Boolean get() = has(VariableCode.__STRING__)
    val isArray1D: Boolean get() = has(VariableCode.__ARRAY_1D__)
    val isArray2D: Boolean get() = has(VariableCode.__ARRAY_2D__)
    val isArray3D: Boolean get() = has(VariableCode.__ARRAY_3D__)
    open val isConst: Boolean get() = has(VariableCode.__UNCHANGEABLE__)
    val isCalc: Boolean get() = has(VariableCode.__CALC__)
    val isLocal: Boolean get() = has(VariableCode.__LOCAL__)
    val canForbid: Boolean get() = has(VariableCode.__CAN_FORBID__)
}

// ─── Array-backed helpers ────────────────────────────────────────────────────

private fun dimName(d: Int) = when (d) { 2 -> "二次元配列"; 3 -> "三次元配列"; else -> "配列変数" }
private val ordinals = arrayOf("１", "２", "３", "４")

/** Common implementation for a token whose storage is a (possibly lazily resolved) array. */
abstract class ArrayVariableToken protected constructor(code: Int, varData: VariableData?) : VariableToken(code, varData) {
    /** returns the current backing array or throws */
    abstract fun arr(): Any
    protected open val rangeLabel: String get() = dimName(dimension)

    override fun getIntValue(exm: ExpressionMediator, arguments: LongArray): Long = EraArrays.getInt(arr(), dimension, arguments)
    override fun getStrValue(exm: ExpressionMediator, arguments: LongArray): String? = EraArrays.getStr(arr(), dimension, arguments)
    override fun setValue(value: Long, arguments: LongArray?) { if (isString) super.setValue(value, arguments) else EraArrays.setInt(arr(), dimension, arguments!!, value) }
    override fun setValue(value: String?, arguments: LongArray?) { if (isInteger) super.setValue(value, arguments) else EraArrays.setStr(arr(), dimension, arguments!!, value) }
    override fun setValues(values: LongArray, arguments: LongArray) { if (isString) super.setValues(values, arguments) else EraArrays.setInts(arr(), dimension, arguments, values) }
    override fun setValues(values: Array<String?>, arguments: LongArray) { if (isInteger) super.setValues(values, arguments) else EraArrays.setStrs(arr(), dimension, arguments, values) }
    override fun setValueAll(value: Long, start: Int, end: Int, charaPos: Int) { if (isString) super.setValueAll(value, start, end, charaPos) else EraArrays.fillInt(arr(), dimension, value, start, end) }
    override fun setValueAll(value: String?, start: Int, end: Int, charaPos: Int) { if (isInteger) super.setValueAll(value, start, end, charaPos) else EraArrays.fillStr(arr(), dimension, value, start, end) }
    override fun plusValue(value: Long, arguments: LongArray): Long = if (isString) super.plusValue(value, arguments) else EraArrays.plusInt(arr(), dimension, arguments, value)
    override fun getLength(): Int {
        if (dimension == 1) return EraArrays.length(arr(), 0)
        throw CodeEE("${dimension}次元配列型変数${varName}の長さを取得しようとしました")
    }
    override fun getLength(dimension: Int): Int {
        if (dimension < this.dimension) return EraArrays.length(arr(), dimension)
        throw CodeEE("配列型変数${varName}の存在しない次元の長さを取得しようとしました")
    }
    override fun getArray(): Any = arr()

    override fun checkElement(arguments: LongArray, doCheck: BooleanArray) {
        val a = arr()
        for (d in 0 until dimension) {
            if (d < doCheck.size && doCheck[d] && (arguments[d] < 0 || arguments[d] >= EraArrays.length(a, d)))
                throw ArrayRangeCodeEE("$rangeLabel${varName}の第${ordinals[d]}引数(${arguments[d]})は配列の範囲外です")
        }
    }
    override fun isArrayRangeValid(arguments: LongArray, index1: Long, index2: Long, funcName: String, i1: Long, i2: Long) {
        checkElement(arguments)
        val len = EraArrays.length(arr(), dimension - 1)
        if (index1 < 0 || index1 > len) throw ArrayRangeCodeEE("${funcName}命令の第${i1}引数(${index1})は配列${varName}の範囲外です")
        if (index2 < 0 || index2 > len) throw ArrayRangeCodeEE("${funcName}命令の第${i2}引数(${index2})は配列${varName}の範囲外です")
    }
}

/** System (VariableData owned) arrays: Int1D/2D/3D, Str1D/2D/3D */
class SystemArrayToken(code: Int, varData: VariableData, private val array: Any) : ArrayVariableToken(code, varData) {
    init {
        canRestructure = false
        isForbid = EraArrays.totalLength(array) == 0
    }
    override fun arr(): Any = array
    override fun getLength(): Int {
        if (dimension == 1) return EraArrays.length(array, 0)
        throw CodeEE("${dimension}次元配列型変数${varName}の長さを取得しようとしました")
    }
}

class IntVariableToken(code: Int, varData: VariableData) : VariableToken(code, varData) {
    private val array = varData.dataInteger
    init { canRestructure = false }
    override fun getIntValue(exm: ExpressionMediator, arguments: LongArray): Long = array[varCodeInt]
    override fun setValue(value: Long, arguments: LongArray?) { array[varCodeInt] = value }
    override fun setValueAll(value: Long, start: Int, end: Int, charaPos: Int) { array[varCodeInt] = value }
    override fun plusValue(value: Long, arguments: LongArray): Long { array[varCodeInt] += value; return array[varCodeInt] }
}

class StrVariableToken(code: Int, varData: VariableData) : VariableToken(code, varData) {
    private val array = varData.dataString
    init { canRestructure = false; isForbid = array.isEmpty() }
    override fun getStrValue(exm: ExpressionMediator, arguments: LongArray): String? = array[varCodeInt]
    override fun setValue(value: String?, arguments: LongArray?) { array[varCodeInt] = value }
    override fun setValueAll(value: String?, start: Int, end: Int, charaPos: Int) { array[varCodeInt] = value }
}

// ─── Character variables ─────────────────────────────────────────────────────

abstract class CharaVariableToken protected constructor(code: Int, varData: VariableData) : VariableToken(code, varData) {
    protected var sizes: IntArray? = CharacterData.characterVarLength(code, varData.constant)
    protected var totalSize = 0

    init {
        sizes?.let { s -> totalSize = s.fold(1) { a, b -> a * b }; isForbid = totalSize == 0 }
        isPrivate = false
        canRestructure = false
    }

    protected val vd: VariableData get() = varData!!
    protected fun chara(i: Long): CharacterData = vd.characterList[i.ix()]

    override fun getLength(): Int {
        val s = sizes!!
        if (s.size == 1) return s[0]
        if (s.isEmpty()) throw CodeEE("非配列型のキャラ変数${varName}の長さを取得しようとしました")
        throw CodeEE("${dimension}次元配列型のキャラ変数${varName}の長さを次元を指定せずに取得しようとしました")
    }
    override fun getLength(dimension: Int): Int {
        val s = sizes!!
        if (s.isEmpty()) throw CodeEE("非配列型のキャラ変数${varName}の長さを取得しようとしました")
        if (dimension < s.size) return s[dimension]
        throw CodeEE("配列型変数のキャラ変数${varName}の存在しない次元の長さを取得しようとしました")
    }
    override fun checkElement(arguments: LongArray, doCheck: BooleanArray) {
        val s = sizes ?: IntArray(0)
        if (doCheck[0] && (arguments[0] < 0 || arguments[0] >= vd.characterList.size))
            throw ArrayRangeCodeEE("キャラクタ配列変数${varName}の第１引数(${arguments[0]})はキャラ登録番号の範囲外です")
        if (doCheck.size > 1 && s.isNotEmpty() && doCheck[1] && (arguments[1] < 0 || arguments[1] >= s[0]))
            throw ArrayRangeCodeEE("キャラクタ配列変数${varName}の第２引数(${arguments[1]})は配列の範囲外です")
        if (doCheck.size > 2 && s.size > 1 && doCheck[2] && (arguments[2] < 0 || arguments[2] >= s[1]))
            throw ArrayRangeCodeEE("キャラクタ配列変数${varName}の第３引数(${arguments[2]})は配列の範囲外です")
    }
    override fun isArrayRangeValid(arguments: LongArray, index1: Long, index2: Long, funcName: String, i1: Long, i2: Long) {
        checkElement(arguments)
        val s0 = sizes!![0]
        if (index1 < 0 || index1 > s0) throw ArrayRangeCodeEE("${funcName}命令の第${i1}引数(${index1})は配列${varName}の範囲外です")
        if (index2 < 0 || index2 > s0) throw ArrayRangeCodeEE("${funcName}命令の第${i2}引数(${index2})は配列${varName}の範囲外です")
    }

    /** array dimension excluding the character index */
    protected val arrDim: Int get() = dimension

    /** backing array (for array chara vars) */
    open fun charaArray(c: CharacterData): Any = throw IllegalStateException()

    override fun getIntValue(exm: ExpressionMediator, arguments: LongArray): Long =
        if (isString) super.getIntValue(exm, arguments) else EraArrays.getInt(charaArray(chara(arguments[0])), arrDim, arguments, 1)
    override fun getStrValue(exm: ExpressionMediator, arguments: LongArray): String? =
        if (isInteger) super.getStrValue(exm, arguments) else EraArrays.getStr(charaArray(chara(arguments[0])), arrDim, arguments, 1)
    override fun setValue(value: Long, arguments: LongArray?) {
        if (isString) super.setValue(value, arguments) else EraArrays.setInt(charaArray(chara(arguments!![0])), arrDim, arguments, value, 1)
    }
    override fun setValue(value: String?, arguments: LongArray?) {
        if (isInteger) super.setValue(value, arguments) else EraArrays.setStr(charaArray(chara(arguments!![0])), arrDim, arguments, value, 1)
    }
    override fun setValues(values: LongArray, arguments: LongArray) {
        if (isString) super.setValues(values, arguments) else EraArrays.setInts(charaArray(chara(arguments[0])), arrDim, arguments, values, 1)
    }
    override fun setValues(values: Array<String?>, arguments: LongArray) {
        if (isInteger) super.setValues(values, arguments) else EraArrays.setStrs(charaArray(chara(arguments[0])), arrDim, arguments, values, 1)
    }
    override fun setValueAll(value: Long, start: Int, end: Int, charaPos: Int) {
        if (isString) super.setValueAll(value, start, end, charaPos) else EraArrays.fillInt(charaArray(vd.characterList[charaPos]), arrDim, value, start, end)
    }
    override fun setValueAll(value: String?, start: Int, end: Int, charaPos: Int) {
        if (isInteger) super.setValueAll(value, start, end, charaPos) else EraArrays.fillStr(charaArray(vd.characterList[charaPos]), arrDim, value, start, end)
    }
    override fun plusValue(value: Long, arguments: LongArray): Long =
        if (isString) super.plusValue(value, arguments) else EraArrays.plusInt(charaArray(chara(arguments[0])), arrDim, arguments, value, 1)
    override fun getArrayChara(charano: Int): Any {
        if (dimension == 0) throw CodeEE("配列型でない変数${varName}の配列を取得しようとしました")
        return charaArray(vd.characterList[charano])
    }
}

class CharaIntVariableToken(code: Int, varData: VariableData) : CharaVariableToken(code, varData) {
    override fun getIntValue(exm: ExpressionMediator, arguments: LongArray): Long = chara(arguments[0]).dataInteger[varCodeInt]
    override fun setValue(value: Long, arguments: LongArray?) { chara(arguments!![0]).dataInteger[varCodeInt] = value }
    override fun setValueAll(value: Long, start: Int, end: Int, charaPos: Int) { vd.characterList[charaPos].dataInteger[varCodeInt] = value }
    override fun plusValue(value: Long, arguments: LongArray): Long {
        val c = chara(arguments[0]); c.dataInteger[varCodeInt] += value; return c.dataInteger[varCodeInt]
    }
}

class CharaStrVariableToken(code: Int, varData: VariableData) : CharaVariableToken(code, varData) {
    override fun getStrValue(exm: ExpressionMediator, arguments: LongArray): String? = chara(arguments[0]).dataString[varCodeInt]
    override fun setValue(value: String?, arguments: LongArray?) { chara(arguments!![0]).dataString[varCodeInt] = value }
    override fun setValueAll(value: String?, start: Int, end: Int, charaPos: Int) { vd.characterList[charaPos].dataString[varCodeInt] = value }
}

class CharaInt1DVariableToken(code: Int, varData: VariableData) : CharaVariableToken(code, varData) {
    override fun charaArray(c: CharacterData): Any = c.dataIntegerArray[varCodeInt]
}

class CharaStr1DVariableToken(code: Int, varData: VariableData) : CharaVariableToken(code, varData) {
    override fun charaArray(c: CharacterData): Any = c.dataStringArray[varCodeInt]
}

class CharaInt2DVariableToken(code: Int, varData: VariableData) : CharaVariableToken(code, varData) {
    override fun charaArray(c: CharacterData): Any = c.dataIntegerArray2D[varCodeInt]
}

class CharaStr2DVariableToken(code: Int, varData: VariableData) : CharaVariableToken(code, varData) {
    override fun charaArray(c: CharacterData): Any = c.dataStringArray2D[varCodeInt]
}

// ─── Constants ────────────────────────────────────────────────────────────────

abstract class ConstantToken(code: Int, varData: VariableData) : VariableToken(code, varData) {
    init { canRestructure = true }
    private fun ro(): Nothing = throw CodeEE("読み取り専用の変数${varName}に代入しようとしました")
    override fun setValue(value: Long, arguments: LongArray?) = ro()
    override fun setValue(value: String?, arguments: LongArray?) = ro()
    override fun setValues(values: LongArray, arguments: LongArray) = ro()
    override fun setValues(values: Array<String?>, arguments: LongArray) = ro()
    override fun plusValue(value: Long, arguments: LongArray): Long = ro()
}

class IntConstantToken(code: Int, varData: VariableData, private val i: Long) : ConstantToken(code, varData) {
    override fun getIntValue(exm: ExpressionMediator, arguments: LongArray): Long = i
}

class StrConstantToken(code: Int, varData: VariableData, private val s: String?) : ConstantToken(code, varData) {
    override fun getStrValue(exm: ExpressionMediator, arguments: LongArray): String? = s
}

class Int1DConstantToken(code: Int, varData: VariableData, private val array: LongArray) : ConstantToken(code, varData) {
    init { isForbid = array.isEmpty() }
    override fun getIntValue(exm: ExpressionMediator, arguments: LongArray): Long = array[arguments[0].ix()]
    override fun getLength(): Int = array.size
    override fun getLength(dimension: Int): Int {
        if (dimension == 0) return array.size
        throw CodeEE("配列型変数${varName}の存在しない次元の長さを取得しようとしました")
    }
    override fun getArray(): Any = array
    override fun checkElement(arguments: LongArray, doCheck: BooleanArray) {
        if (doCheck[0] && (arguments[0] < 0 || arguments[0] >= array.size))
            throw ArrayRangeCodeEE("配列変数${varName}の第１引数(${arguments[0]})は配列の範囲外です")
    }
    override fun isArrayRangeValid(arguments: LongArray, index1: Long, index2: Long, funcName: String, i1: Long, i2: Long) {
        checkElement(arguments)
        if (index1 < 0 || index1 > array.size) throw ArrayRangeCodeEE("${funcName}命令の第${i1}引数(${index1})は配列${varName}の範囲外です")
        if (index2 < 0 || index2 > array.size) throw ArrayRangeCodeEE("${funcName}命令の第${i2}引数(${index2})は配列${varName}の範囲外です")
    }
}

class Str1DConstantToken(code: Int, varData: VariableData, private val array: Array<String?>) : ConstantToken(code, varData) {
    constructor(code: Int, varData: VariableData) : this(code, varData, varData.constant.getCsvNameList(code))
    init { isForbid = array.isEmpty() }
    override fun getStrValue(exm: ExpressionMediator, arguments: LongArray): String? = array[arguments[0].ix()]
    override fun getLength(): Int = array.size
    override fun getLength(dimension: Int): Int {
        if (dimension == 0) return array.size
        throw CodeEE("配列型変数${varName}の存在しない次元の長さを取得しようとしました")
    }
    override fun getArray(): Any = array
    override fun checkElement(arguments: LongArray, doCheck: BooleanArray) {
        if (doCheck[0] && (arguments[0] < 0 || arguments[0] >= array.size))
            throw ArrayRangeCodeEE("配列変数${varName}の第１引数(${arguments[0]})は配列の範囲外です")
    }
    override fun isArrayRangeValid(arguments: LongArray, index1: Long, index2: Long, funcName: String, i1: Long, i2: Long) {
        checkElement(arguments)
        if (index1 < 0 || index1 > array.size) throw ArrayRangeCodeEE("${funcName}命令の第${i1}引数(${index1})は配列${varName}の範囲外です")
        if (index2 < 0 || index2 > array.size) throw ArrayRangeCodeEE("${funcName}命令の第${i2}引数(${index2})は配列${varName}の範囲外です")
    }
}

// ─── Pseudo variables ─────────────────────────────────────────────────────────

abstract class PseudoVariableToken(code: Int, varData: VariableData) : VariableToken(code, varData) {
    init { canRestructure = false }
    private fun ps(): Nothing = throw CodeEE("擬似変数${varName}に代入しようとしました")
    override fun setValue(value: Long, arguments: LongArray?) = ps()
    override fun setValue(value: String?, arguments: LongArray?) = ps()
    override fun setValues(values: LongArray, arguments: LongArray) = ps()
    override fun setValues(values: Array<String?>, arguments: LongArray) = ps()
    override fun plusValue(value: Long, arguments: LongArray): Long = ps()
    override fun getLength(): Int = throw CodeEE("擬似変数${varName}の長さを取得しようとしました")
    override fun getLength(dimension: Int): Int = throw CodeEE("擬似変数${varName}の長さを取得しようとしました")
    override fun getArray(): Any = throw CodeEE("擬似変数${varName}の配列を取得しようとしました")
}

class PseudoIntToken(code: Int, varData: VariableData, restructurable: Boolean, private val f: (ExpressionMediator, LongArray) -> Long) : PseudoVariableToken(code, varData) {
    init { canRestructure = restructurable }
    override fun getIntValue(exm: ExpressionMediator, arguments: LongArray): Long = f(exm, arguments)
}

class PseudoStrToken(code: Int, varData: VariableData, restructurable: Boolean, private val f: (ExpressionMediator, LongArray) -> String?) : PseudoVariableToken(code, varData) {
    init { canRestructure = restructurable }
    override fun getStrValue(exm: ExpressionMediator, arguments: LongArray): String? = f(exm, arguments)
}

class WindowTitleToken(code: Int, varData: VariableData) : VariableToken(code, varData) {
    init { canRestructure = false }
    override fun getStrValue(exm: ExpressionMediator, arguments: LongArray): String? = GlobalStatic.Console!!.getWindowTitle()
    override fun setValue(value: String?, arguments: LongArray?) { GlobalStatic.Console!!.setWindowTitle(value ?: "") }
}

class SimpleStrToken(code: Int, varData: VariableData, private val f: (ExpressionMediator) -> String) : VariableToken(code, varData) {
    init { canRestructure = true }
    override fun getStrValue(exm: ExpressionMediator, arguments: LongArray): String? = f(exm)
}

// ─── LOCAL / ARG ──────────────────────────────────────────────────────────────

abstract class LocalVariableToken(code: Int, varData: VariableData, protected val subID: String?, protected var size: Int) : ArrayVariableToken(code, varData) {
    init { canRestructure = false }
    abstract fun setDefault()
    abstract fun resize(newSize: Int)
    override fun getLength(): Int = size
    override fun getLength(dimension: Int): Int {
        if (dimension == 0) return size
        throw CodeEE("配列型変数${varName}の存在しない次元の長さを取得しようとしました")
    }
    override fun checkElement(arguments: LongArray, doCheck: BooleanArray) {
        if (doCheck[0] && (arguments[0] < 0 || arguments[0] >= size))
            throw ArrayRangeCodeEE("配列変数${varName}の第１引数(${arguments[0]})は配列の範囲外です")
    }
    override fun isArrayRangeValid(arguments: LongArray, index1: Long, index2: Long, funcName: String, i1: Long, i2: Long) {
        checkElement(arguments)
        if (index1 < 0 || index1 > size) throw ArrayRangeCodeEE("${funcName}命令の第${i1}引数(${index1})は配列${varName}の範囲外です")
        if (index2 < 0 || index2 > size) throw ArrayRangeCodeEE("${funcName}命令の第${i2}引数(${index2})は配列${varName}の範囲外です")
    }
}

class LocalInt1DVariableToken(code: Int, varData: VariableData, subId: String?, size: Int) : LocalVariableToken(code, varData, subId, size) {
    private var array: LongArray? = null
    override fun setDefault() { array?.fill(0) }
    override fun arr(): Any { if (array == null) array = LongArray(size); return array!! }
    override fun resize(newSize: Int) { size = newSize; array = null }
}

class LocalStr1DVariableToken(code: Int, varData: VariableData, subId: String?, size: Int) : LocalVariableToken(code, varData, subId, size) {
    private var array: Array<String?>? = null
    override fun setDefault() { array?.fill(null) }
    override fun arr(): Any { if (array == null) array = arrayOfNulls(size); return array!! }
    override fun resize(newSize: Int) { size = newSize; array = null }
}

// ─── User defined (#DIM) ──────────────────────────────────────────────────────

abstract class UserDefinedVariableToken protected constructor(code: Int, data: UserDefinedVariableData) : ArrayVariableToken(code, null) {
    protected var isConstUD: Boolean = data.Const
    protected val sizes: IntArray = data.Lengths!!
    protected var totalSize: Int = sizes.fold(1) { a, b -> a * b }

    init {
        varName = data.Name!!
        isPrivate = data.Private
        isGlobal = data.Global
        isSavedata = data.Save
        isForbid = totalSize == 0
        canRestructure = isConstUD
    }

    override val rangeLabel: String get() = "配列型変数"
    abstract fun setDefault()
    abstract fun `in`()
    abstract fun out()
    var isStatic = false
        protected set
    override val isConst: Boolean get() = isConstUD

    override fun getLength(): Int {
        if (dimension == 1) return sizes[0]
        throw CodeEE("${dimension}次元配列型変数${varName}の長さを取得しようとしました")
    }
    override fun getLength(dimension: Int): Int {
        if (dimension < this.dimension) return sizes[dimension]
        throw CodeEE("配列型変数${varName}の存在しない次元の長さを取得しようとしました")
    }
    override fun checkElement(arguments: LongArray, doCheck: BooleanArray) {
        if (doCheck[0] && (arguments[0] < 0 || arguments[0] >= sizes[0]))
            throw ArrayRangeCodeEE("配列型変数${varName}の第１引数(${arguments[0]})は配列の範囲外です")
        if (sizes.size >= 2 && (arguments[1] < 0 || arguments[1] >= sizes[1]))
            throw ArrayRangeCodeEE("配列型変数${varName}の第２引数(${arguments[1]})は配列の範囲外です")
        if (sizes.size >= 3 && (arguments[2] < 0 || arguments[2] >= sizes[2]))
            throw ArrayRangeCodeEE("配列型変数${varName}の第３引数(${arguments[2]})は配列の範囲外です")
    }
    override fun isArrayRangeValid(arguments: LongArray, index1: Long, index2: Long, funcName: String, i1: Long, i2: Long) {
        checkElement(arguments)
        val len = sizes[dimension - 1]
        if (index1 < 0 || index1 > len) throw ArrayRangeCodeEE("${funcName}命令の第${i1}引数(${index1})は配列${varName}の範囲外です")
        if (index2 < 0 || index2 > len) throw ArrayRangeCodeEE("${funcName}命令の第${i2}引数(${index2})は配列${varName}の範囲外です")
    }

    protected fun newArray(): Any {
        val s = sizes
        return if (isString) when (dimension) {
            1 -> EraArrays.newStr1(s[0]); 2 -> EraArrays.newStr2(s[0], s[1]); else -> EraArrays.newStr3(s[0], s[1], s[2])
        } else when (dimension) {
            1 -> LongArray(s[0]); 2 -> EraArrays.newInt2(s[0], s[1]); else -> EraArrays.newInt3(s[0], s[1], s[2])
        }
    }
}

/** static (広域変数とprivate static の両方を含む) */
class StaticVariableToken(code: Int, data: UserDefinedVariableData) : UserDefinedVariableToken(code, data) {
    private val array: Any = newArray()
    private val defInt: LongArray? = data.DefaultInt
    private val defStr: Array<String?>? = data.DefaultStr

    init {
        isStatic = true
        applyDefault()
    }

    private fun applyDefault() {
        if (dimension == 1) {
            defInt?.copyInto(array as LongArray, 0, 0, minOf(defInt.size, (array as LongArray).size))
            @Suppress("UNCHECKED_CAST")
            defStr?.let { d -> val a = array as Array<String?>; for (i in d.indices) if (i < a.size) a[i] = d[i] }
        }
    }

    override fun setDefault() { EraArrays.clear(array); applyDefault() }
    override fun arr(): Any = array
    override fun `in`() {}
    override fun out() {}
}

/** private dynamic */
class PrivateVariableToken(code: Int, data: UserDefinedVariableData) : UserDefinedVariableToken(code, data) {
    private val arrayList = ArrayList<Any>()
    private var array: Any? = null
    private val defInt: LongArray? = data.DefaultInt
    private val defStr: Array<String?>? = data.DefaultStr

    init { isStatic = false }

    override fun setDefault() {}
    override fun arr(): Any = array ?: throw IndexOutOfBoundsException()
    override fun `in`() {
        array?.let { arrayList.add(it) }
        val a = newArray()
        if (dimension == 1) {
            defInt?.copyInto(a as LongArray, 0, 0, minOf(defInt.size, (a as LongArray).size))
            @Suppress("UNCHECKED_CAST")
            defStr?.let { d -> val s = a as Array<String?>; for (i in d.indices) if (i < s.size) s[i] = d[i] }
        }
        array = a
    }
    override fun out() {
        array = if (arrayList.isNotEmpty()) arrayList.removeAt(arrayList.size - 1) else null
    }
}

/** 参照型 */
class ReferenceToken(code: Int, data: UserDefinedVariableData) : UserDefinedVariableToken(code, data) {
    private val arrayList = ArrayList<Any?>()
    private var array: Any? = null
    private var counter = 0

    init {
        canRestructure = false
        isStatic = !data.Private
        isReference = true
        isForbid = false
    }

    private fun noRef(): Nothing = throw CodeEE("参照型変数${varName}は何も参照していません")
    override fun arr(): Any = array ?: noRef()
    override fun setDefault() {}

    override fun getLength(): Int {
        val a = array ?: noRef()
        if (dimension != 1) throw CodeEE("${dimension}次元配列型変数${varName}の長さを取得しようとしました")
        return EraArrays.length(a, 0)
    }
    override fun getLength(dimension: Int): Int {
        val a = array ?: noRef()
        if (dimension < this.dimension) return EraArrays.length(a, dimension)
        throw CodeEE("配列型変数${varName}の存在しない次元の長さを取得しようとしました")
    }
    override fun checkElement(arguments: LongArray, doCheck: BooleanArray) {
        val a = array ?: noRef()
        if (doCheck[0] && (arguments[0] < 0 || arguments[0] >= EraArrays.length(a, 0)))
            throw ArrayRangeCodeEE("配列型変数${varName}の第１引数(${arguments[0]})は配列の範囲外です")
        if (dimension >= 2 && (arguments[1] < 0 || arguments[1] >= EraArrays.length(a, 1)))
            throw ArrayRangeCodeEE("配列型変数${varName}の第２引数(${arguments[1]})は配列の範囲外です")
        if (dimension >= 3 && (arguments[2] < 0 || arguments[2] >= EraArrays.length(a, 2)))
            throw ArrayRangeCodeEE("配列型変数${varName}の第３引数(${arguments[2]})は配列の範囲外です")
    }
    override fun isArrayRangeValid(arguments: LongArray, index1: Long, index2: Long, funcName: String, i1: Long, i2: Long) {
        checkElement(arguments)
        val len = EraArrays.length(array!!, dimension - 1)
        if (index1 < 0 || index1 > len) throw ArrayRangeCodeEE("${funcName}命令の第${i1}引数(${index1})は配列${varName}の範囲外です")
        if (index2 < 0 || index2 > len) throw ArrayRangeCodeEE("${funcName}命令の第${i2}引数(${index2})は配列${varName}の範囲外です")
    }

    override fun `in`() {
        if (counter > 0) arrayList.add(array)
        counter++
        array = null
    }
    override fun out() {
        array = if (arrayList.isNotEmpty()) arrayList.removeAt(arrayList.size - 1) else null
        counter--
    }
    override fun getArray(): Any = array ?: noRef()
    fun setRef(refArray: Any?) { array = refArray }

    /** 型が一致するかどうか（参照可能かどうか） */
    fun matchType(rother: VariableToken?, allowChara: Boolean, errMes: Array<String>): Boolean {
        errMes[0] = ""
        if (rother == null) { errMes[0] = "参照先変数は省略できません"; return false }
        if (rother.isCalc) { errMes[0] = "疑似変数は参照できません"; return false }
        if (rother.isConst) { errMes[0] = "定数は参照できません"; return false }
        if (!this.isPrivate && (rother.isPrivate || rother.isLocal)) { errMes[0] = "広域の参照変数はローカル変数を参照できません"; return false }
        if (rother.isCharacterData && !allowChara) { errMes[0] = "キャラ変数は参照できません"; return false }
        if (this.isInteger != rother.isInteger) { errMes[0] = "型が異なる変数は参照できません"; return false }
        if (this.dimension != rother.dimension) { errMes[0] = "次元数が異なる変数は参照できません"; return false }
        return true
    }
}

/** ユーザー定義キャラ変数 (広域のみ) */
class UserDefinedCharaVariableToken(code: Int, val dimData: UserDefinedVariableData, varData: VariableData, val arrayIndex: Int) : CharaVariableToken(code, varData) {
    init {
        varName = dimData.Name!!
        sizes = dimData.Lengths
        isGlobal = dimData.Global
        isSavedata = dimData.Save
        totalSize = sizes!!.fold(1) { a, b -> a * b }
        isForbid = totalSize == 0
    }
    override fun charaArray(c: CharacterData): Any = c.userDefCVarDataList[arrayIndex]
    override fun getArrayChara(charano: Int): Any = vd.characterList[charano].userDefCVarDataList[arrayIndex]
}
