package com.eraandroid.emuera.gamedata.variable

import com.eraandroid.emuera.gamedata.expression.EType
import com.eraandroid.emuera.gamedata.expression.ExpressionMediator
import com.eraandroid.emuera.gamedata.expression.IOperandTerm
import com.eraandroid.emuera.gamedata.expression.SingleTerm
import com.eraandroid.emuera.config.Config
import com.eraandroid.emuera.sub.ArrayRangeCodeEE
import com.eraandroid.emuera.sub.CodeEE

open class VariableTerm protected constructor(token: VariableToken, private val arguments: Array<IOperandTerm?>, dummy: Unit) : IOperandTerm(token.variableType) {
    var identifier: VariableToken = token
    protected var transporter: LongArray = LongArray(arguments.size)
    protected var allArgIsConst = false

    constructor(token: VariableToken, args: Array<IOperandTerm?>) : this(token, args, Unit) {
        allArgIsConst = false
        var allConst = true
        for (i in args.indices) {
            val a = args[i]
            if (a !is SingleTerm) { allConst = false; break }
            transporter[i] = a.int
        }
        allArgIsConst = allConst
    }

    fun getElementInt(i: Int, exm: ExpressionMediator): Long = if (allArgIsConst) transporter[i] else arguments[i]!!.getIntValue(exm)

    val isAllConst: Boolean get() = allArgIsConst
    val getEl1forArg: Int get() = transporter[0].toInt()

    protected fun evalArgs(exm: ExpressionMediator) {
        if (!allArgIsConst) for (i in arguments.indices) transporter[i] = arguments[i]!!.getIntValue(exm)
    }

    protected inline fun <T> guard(block: () -> T): T {
        try {
            return block()
        } catch (e: IndexOutOfBoundsException) {
            identifier.checkElement(transporter)
            throw e
        } catch (e: IllegalArgumentException) {
            identifier.checkElement(transporter)
            throw e
        } catch (e: ClassCastException) {
            identifier.checkElement(transporter)
            throw e
        }
    }

    /**
     * 配列の範囲外アクセス: PC 版 Emuera はエラーで止まるが、Android 版では
     * 読み取りは 0/空文字、書き込みは無視して続行する (Config.LenientArrayAccess)
     */
    protected inline fun <T> lenient(default: T, block: () -> T): T {
        try {
            return guard(block)
        } catch (e: ArrayRangeCodeEE) {
            if (!Config.LenientArrayAccess) throw e
            return default
        }
    }

    override fun getIntValue(exm: ExpressionMediator): Long = lenient(0L) { evalArgs(exm); identifier.getIntValue(exm, transporter) }
    override fun getStrValue(exm: ExpressionMediator): String = lenient("") { evalArgs(exm); identifier.getStrValue(exm, transporter) ?: "" }
    open fun setValue(value: Long, exm: ExpressionMediator) = lenient(Unit) { evalArgs(exm); identifier.setValue(value, transporter) }
    open fun setValue(value: String?, exm: ExpressionMediator) = lenient(Unit) { evalArgs(exm); identifier.setValue(value, transporter) }

    open fun setValues(array: LongArray, exm: ExpressionMediator) {
        try {
            evalArgs(exm)
            identifier.setValues(array, transporter)
        } catch (e: IndexOutOfBoundsException) {
            identifier.checkElement(transporter)
            throw CodeEE("配列変数${identifier.name}の要素数を超えて代入しようとしました")
        }
    }

    open fun setValues(array: Array<String?>, exm: ExpressionMediator) {
        try {
            evalArgs(exm)
            identifier.setValues(array, transporter)
        } catch (e: IndexOutOfBoundsException) {
            identifier.checkElement(transporter)
            throw CodeEE("配列変数${identifier.name}の要素数を超えて代入しようとしました")
        }
    }

    open fun plusValue(value: Long, exm: ExpressionMediator): Long = lenient(0L) { evalArgs(exm); identifier.plusValue(value, transporter) }

    override fun getValue(exm: ExpressionMediator): SingleTerm =
        if (identifier.variableType == EType.Int64) SingleTerm(getIntValue(exm)) else SingleTerm(getStrValue(exm))

    open fun setValue(value: SingleTerm, exm: ExpressionMediator) {
        if (identifier.variableType == EType.Int64) setValue(value.int, exm) else setValue(value.str, exm)
    }

    open fun setValue(value: IOperandTerm, exm: ExpressionMediator) {
        if (identifier.variableType == EType.Int64) setValue(value.getIntValue(exm), exm) else setValue(value.getStrValue(exm), exm)
    }

    fun getLength(): Int = identifier.getLength()
    fun getLength(dimension: Int): Int = identifier.getLength(dimension)
    fun getLastLength(): Int = when {
        identifier.isArray1D -> identifier.getLength()
        identifier.isArray2D -> identifier.getLength(1)
        identifier.isArray3D -> identifier.getLength(2)
        else -> 0
    }

    open fun getFixedVariableTerm(exm: ExpressionMediator): FixedVariableTerm {
        evalArgs(exm)
        val fp = FixedVariableTerm(identifier)
        if (transporter.isNotEmpty()) fp.index1 = transporter[0]
        if (transporter.size >= 2) fp.index2 = transporter[1]
        if (transporter.size >= 3) fp.index3 = transporter[2]
        return fp
    }

    override fun restructure(exm: ExpressionMediator): IOperandTerm {
        val canCheck = BooleanArray(arguments.size)
        allArgIsConst = true
        for (i in arguments.indices) {
            arguments[i] = arguments[i]!!.restructure(exm)
            if (arguments[i] !is SingleTerm) {
                allArgIsConst = false
                canCheck[i] = false
            } else {
                canCheck[i] = !((i == 0 && identifier.isCharacterData) || identifier.name == "ARG" || identifier.name == "ARGS")
                transporter[i] = arguments[i]!!.getIntValue(exm)
            }
        }
        if (!identifier.isReference) {
            try {
                identifier.checkElement(transporter, padCheck(canCheck))
            } catch (e: ArrayRangeCodeEE) {
                // 定数の添字が範囲外: Android 版では実行時に 0/空文字として扱う
                if (!Config.LenientArrayAccess) throw e
                return this
            }
        }
        if (identifier.canRestructure && allArgIsConst) return getValue(exm)
        else if (allArgIsConst) return FixedVariableTerm(identifier, transporter)
        return this
    }

    private fun padCheck(c: BooleanArray): BooleanArray = if (c.size >= 3) c else BooleanArray(3) { if (it < c.size) c[it] else false }

    fun checkSameTerm(term: VariableTerm): Boolean {
        if (!allArgIsConst) return false
        if (identifier.name != term.identifier.name) return false
        for (i in transporter.indices) if (i >= term.transporter.size || transporter[i] != term.transporter[i]) return false
        return true
    }

    fun getFullString(): String {
        if (!allArgIsConst) return ""
        return when {
            identifier.isArray1D -> identifier.name + ":" + transporter[0]
            identifier.isArray2D -> identifier.name + ":" + transporter[0] + ":" + transporter[1]
            identifier.isArray3D -> identifier.name + ":" + transporter[0] + ":" + transporter[1] + ":" + transporter[2]
            else -> identifier.name
        }
    }
}

class FixedVariableTerm(token: VariableToken, args: LongArray? = null) : VariableTerm(token, arrayOf(), Unit) {
    init {
        allArgIsConst = true
        transporter = LongArray(3)
        args?.let { for (i in it.indices) if (i < 3) transporter[i] = it[i] }
    }

    var index1: Long get() = transporter[0]; set(v) { transporter[0] = v }
    var index2: Long get() = transporter[1]; set(v) { transporter[1] = v }
    var index3: Long get() = transporter[2]; set(v) { transporter[2] = v }

    override fun getIntValue(exm: ExpressionMediator): Long = lenient(0L) { identifier.getIntValue(exm, transporter) }
    override fun getStrValue(exm: ExpressionMediator): String = lenient("") { identifier.getStrValue(exm, transporter) ?: "" }
    override fun setValue(value: Long, exm: ExpressionMediator) = lenient(Unit) { identifier.setValue(value, transporter) }
    override fun setValue(value: String?, exm: ExpressionMediator) = lenient(Unit) { identifier.setValue(value, transporter) }
    override fun plusValue(value: Long, exm: ExpressionMediator): Long = lenient(0L) { identifier.plusValue(value, transporter) }
    override fun restructure(exm: ExpressionMediator): IOperandTerm = if (identifier.canRestructure) getValue(exm) else this
    override fun getFixedVariableTerm(exm: ExpressionMediator): FixedVariableTerm {
        val fp = FixedVariableTerm(identifier)
        fp.index1 = index1; fp.index2 = index2; fp.index3 = index3
        return fp
    }

    fun isArrayRangeValid(index1: Long, index2: Long, funcName: String, i1: Long, i2: Long) =
        identifier.isArrayRangeValid(transporter, index1, index2, funcName, i1, i2)

    val transporterArray: LongArray get() = transporter
}

/** 引数がない変数。値を参照、代入できない */
class VariableNoArgTerm(token: VariableToken) : VariableTerm(token, arrayOf(), Unit) {
    init { allArgIsConst = true }
    private fun err(): Nothing = throw CodeEE("変数${identifier.name}に必要な引数が不足しています")
    override fun getIntValue(exm: ExpressionMediator): Long = err()
    override fun getStrValue(exm: ExpressionMediator): String = err()
    override fun setValue(value: Long, exm: ExpressionMediator) = err()
    override fun setValue(value: String?, exm: ExpressionMediator) = err()
    override fun setValues(array: LongArray, exm: ExpressionMediator) = err()
    override fun setValues(array: Array<String?>, exm: ExpressionMediator) = err()
    override fun plusValue(value: Long, exm: ExpressionMediator): Long = err()
    override fun getValue(exm: ExpressionMediator): SingleTerm = err()
    override fun setValue(value: SingleTerm, exm: ExpressionMediator) = err()
    override fun setValue(value: IOperandTerm, exm: ExpressionMediator) = err()
    override fun getFixedVariableTerm(exm: ExpressionMediator): FixedVariableTerm = err()
    override fun restructure(exm: ExpressionMediator): IOperandTerm = this
}

/** 変数の引数のうち文字列型のもの */
class VariableStrArgTerm(private val parentCode: Int, private var strTerm: IOperandTerm, private val index: Int) : IOperandTerm(EType.Int64) {
    private var dic: Map<String, Int>? = null
    private val errPos = arrayOfNulls<String>(1)

    override fun getIntValue(exm: ExpressionMediator): Long {
        if (dic == null) dic = exm.vEvaluator.constant.getKeywordDictionary(errPos, parentCode, index)
        val key = strTerm.getStrValue(exm)
        if (key == "") throw CodeEE("キーワードを空には出来ません")
        val i = dic?.get(key)
        if (i == null) {
            if (errPos[0] == null) throw CodeEE("配列変数" + VariableCode.toString(parentCode) + "の要素を文字列で指定することはできません")
            else throw CodeEE(errPos[0] + "の中に\"" + key + "\"の定義がありません")
        }
        return i.toLong()
    }

    override fun restructure(exm: ExpressionMediator): IOperandTerm {
        if (dic == null) dic = exm.vEvaluator.constant.getKeywordDictionary(errPos, parentCode, index)
        strTerm = strTerm.restructure(exm)
        if (strTerm !is SingleTerm) return this
        return SingleTerm(getIntValue(exm))
    }
}
