package com.eraandroid.emuera.gamedata.expression

import com.eraandroid.emuera.gamedata.StrForm

/** Replacement for C# System.Type in operand typing: typeof(Int64) / typeof(string) / typeof(void) */
enum class EType { Int64, String, Void }

abstract class IOperandTerm(private val type: EType) {
    fun getOperandType(): EType = type

    open fun getIntValue(exm: ExpressionMediator): Long = 0
    open fun getStrValue(exm: ExpressionMediator): String = ""
    open fun getValue(exm: ExpressionMediator): SingleTerm =
        if (type == EType.Int64) SingleTerm(0L) else SingleTerm("")

    val isInteger: Boolean get() = type == EType.Int64
    val isString: Boolean get() = type == EType.String

    /** 定数を解体して可能ならSingleTerm化する */
    open fun restructure(exm: ExpressionMediator): IOperandTerm = this
}

class NullTerm private constructor(t: EType) : IOperandTerm(t) {
    constructor(@Suppress("UNUSED_PARAMETER") i: Long) : this(EType.Int64)
    constructor(@Suppress("UNUSED_PARAMETER") s: String) : this(EType.String)
}

/** 項。一単語だけ。 */
class SingleTerm private constructor(t: EType, private val iValue: Long, private val sValue: String?) : IOperandTerm(t) {
    constructor(b: Boolean) : this(EType.Int64, if (b) 1L else 0L, null)
    constructor(i: Long) : this(EType.Int64, i, null)
    constructor(i: Int) : this(EType.Int64, i.toLong(), null)
    constructor(s: String?) : this(EType.String, 0L, s)

    override fun getIntValue(exm: ExpressionMediator): Long = iValue
    override fun getStrValue(exm: ExpressionMediator): String = sValue ?: ""
    override fun getValue(exm: ExpressionMediator): SingleTerm = this

    val str: String get() = sValue ?: ""
    val int: Long get() = iValue

    override fun toString(): String = if (getOperandType() == EType.Int64) iValue.toString() else (sValue ?: "")
    override fun restructure(exm: ExpressionMediator): IOperandTerm = this
}

class StrFormTerm(val strForm: StrForm) : IOperandTerm(EType.String) {
    override fun getStrValue(exm: ExpressionMediator): String = strForm.getString(exm)
    override fun getValue(exm: ExpressionMediator): SingleTerm = SingleTerm(strForm.getString(exm))
    override fun restructure(exm: ExpressionMediator): IOperandTerm {
        strForm.restructure(exm)
        if (strForm.isConst) return SingleTerm(strForm.getString(exm))
        return strForm.getIOperandTerm() ?: this
    }
}

enum class CaseExpressionType { Normal, To, Is }

class CaseExpression {
    var caseType = CaseExpressionType.Normal
    var leftTerm: IOperandTerm? = null
    var rightTerm: IOperandTerm? = null
    var operator: OperatorCode = OperatorCode.NULL

    fun getOperandType(): EType = leftTerm?.getOperandType() ?: EType.Void

    fun reduce(exm: ExpressionMediator) {
        leftTerm = leftTerm!!.restructure(exm)
        if (caseType == CaseExpressionType.To) rightTerm = rightTerm!!.restructure(exm)
    }

    override fun toString(): String = when (caseType) {
        CaseExpressionType.Normal -> leftTerm.toString()
        CaseExpressionType.Is -> "Is $operator $leftTerm"
        CaseExpressionType.To -> "$leftTerm To $rightTerm"
    }

    fun getBool(isVal: Long, exm: ExpressionMediator): Boolean {
        if (caseType == CaseExpressionType.To)
            return leftTerm!!.getIntValue(exm) <= isVal && isVal <= rightTerm!!.getIntValue(exm)
        if (caseType == CaseExpressionType.Is) {
            val term = OperatorMethodManager.reduceBinaryTerm(operator, SingleTerm(isVal), leftTerm!!)
            return term.getIntValue(exm) != 0L
        }
        return leftTerm!!.getIntValue(exm) == isVal
    }

    fun getBool(isVal: String, exm: ExpressionMediator): Boolean {
        if (caseType == CaseExpressionType.To) {
            return ordinalCompare(leftTerm!!.getStrValue(exm), isVal) <= 0 &&
                ordinalCompare(isVal, rightTerm!!.getStrValue(exm)) <= 0
        }
        if (caseType == CaseExpressionType.Is) {
            val term = OperatorMethodManager.reduceBinaryTerm(operator, SingleTerm(isVal), leftTerm!!)
            return term.getIntValue(exm) != 0L
        }
        return leftTerm!!.getStrValue(exm) == isVal
    }
}

/** string.Compare(a, b, StringComparison.Ordinal) */
fun ordinalCompare(a: String, b: String): Int = a.compareTo(b)
