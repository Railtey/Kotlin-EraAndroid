package com.eraandroid.emuera.gamedata.expression

import com.eraandroid.emuera.config.Config

import com.eraandroid.emuera.gamedata.function.FunctionMethod
import com.eraandroid.emuera.gamedata.function.FunctionMethodTerm
import com.eraandroid.emuera.gamedata.variable.VariableTerm
import com.eraandroid.emuera.sub.CodeEE
import com.eraandroid.emuera.sub.ExeEE

/** 引数のチェック、戻り値の型チェック等は全て呼び出し元が責任を負うこと。 */
abstract class OperatorMethod(ret: EType, restructurable: Boolean = true) : FunctionMethod() {
    init {
        argumentTypeArray = null
        returnType = ret
        canRestructure = restructurable
    }
    override fun checkArgumentType(name: String, arguments: Array<IOperandTerm?>): String? = throw ExeEE("型チェックは呼び出し元が行うこと")
}

private typealias Args = Array<IOperandTerm?>

private class IntOp(private val f: (ExpressionMediator, Args) -> Long, restructurable: Boolean = true) : OperatorMethod(EType.Int64, restructurable) {
    override fun getIntValue(exm: ExpressionMediator, arguments: Args): Long = f(exm, arguments)
}

private class StrOp(private val f: (ExpressionMediator, Args) -> String) : OperatorMethod(EType.String) {
    override fun getStrValue(exm: ExpressionMediator, arguments: Args): String = f(exm, arguments)
}

private fun b(v: Boolean) = if (v) 1L else 0L

object OperatorMethodManager {
    private val unaryDic = HashMap<OperatorCode, OperatorMethod>()
    private val unaryAfterDic = HashMap<OperatorCode, OperatorMethod>()
    private val binaryIntIntDic = HashMap<OperatorCode, OperatorMethod>()
    private val binaryStrStrDic = HashMap<OperatorCode, OperatorMethod>()
    private val binaryMultIntStr: OperatorMethod
    private val ternaryIntIntInt: OperatorMethod
    private val ternaryIntStrStr: OperatorMethod

    init {
        unaryDic[OperatorCode.Plus] = IntOp({ e, a -> a[0]!!.getIntValue(e) })
        unaryDic[OperatorCode.Minus] = IntOp({ e, a ->
            // PC 版はここで「整数型最小値は-を取っても値は変化しません」と表示するが、
            // -1p63-1 のように意図して使うゲームがあるため表示しない
            -a[0]!!.getIntValue(e)
        })
        unaryDic[OperatorCode.Not] = IntOp({ e, a -> b(a[0]!!.getIntValue(e) == 0L) })
        unaryDic[OperatorCode.BitNot] = IntOp({ e, a -> a[0]!!.getIntValue(e).inv() })
        unaryDic[OperatorCode.Increment] = IntOp({ e, a -> (a[0] as VariableTerm).plusValue(1L, e) }, false)
        unaryDic[OperatorCode.Decrement] = IntOp({ e, a -> (a[0] as VariableTerm).plusValue(-1L, e) }, false)

        unaryAfterDic[OperatorCode.Increment] = IntOp({ e, a -> (a[0] as VariableTerm).plusValue(1L, e) - 1 }, false)
        unaryAfterDic[OperatorCode.Decrement] = IntOp({ e, a -> (a[0] as VariableTerm).plusValue(-1L, e) + 1 }, false)

        binaryIntIntDic[OperatorCode.Plus] = IntOp({ e, a -> a[0]!!.getIntValue(e) + a[1]!!.getIntValue(e) })
        binaryIntIntDic[OperatorCode.Minus] = IntOp({ e, a -> a[0]!!.getIntValue(e) - a[1]!!.getIntValue(e) })
        binaryIntIntDic[OperatorCode.Mult] = IntOp({ e, a -> a[0]!!.getIntValue(e) * a[1]!!.getIntValue(e) })
        binaryIntIntDic[OperatorCode.Div] = IntOp({ e, a ->
            val right = a[1]!!.getIntValue(e)
            if (right == 0L) {
                // Android 版: 0 で割っても止めずに 0 とする (Config.LenientArrayAccess)
                if (!Config.LenientArrayAccess) throw CodeEE("0による除算が行なわれました")
                a[0]!!.getIntValue(e)
                return@IntOp 0L
            }
            a[0]!!.getIntValue(e) / right
        })
        binaryIntIntDic[OperatorCode.Mod] = IntOp({ e, a ->
            val right = a[1]!!.getIntValue(e)
            if (right == 0L) {
                // Android 版: 0 で割っても止めずに 0 とする (Config.LenientArrayAccess)
                if (!Config.LenientArrayAccess) throw CodeEE("0による除算が行なわれました")
                a[0]!!.getIntValue(e)
                return@IntOp 0L
            }
            a[0]!!.getIntValue(e) % right
        })
        binaryIntIntDic[OperatorCode.Equal] = IntOp({ e, a -> b(a[0]!!.getIntValue(e) == a[1]!!.getIntValue(e)) })
        binaryIntIntDic[OperatorCode.Greater] = IntOp({ e, a -> b(a[0]!!.getIntValue(e) > a[1]!!.getIntValue(e)) })
        binaryIntIntDic[OperatorCode.Less] = IntOp({ e, a -> b(a[0]!!.getIntValue(e) < a[1]!!.getIntValue(e)) })
        binaryIntIntDic[OperatorCode.GreaterEqual] = IntOp({ e, a -> b(a[0]!!.getIntValue(e) >= a[1]!!.getIntValue(e)) })
        binaryIntIntDic[OperatorCode.LessEqual] = IntOp({ e, a -> b(a[0]!!.getIntValue(e) <= a[1]!!.getIntValue(e)) })
        binaryIntIntDic[OperatorCode.NotEqual] = IntOp({ e, a -> b(a[0]!!.getIntValue(e) != a[1]!!.getIntValue(e)) })
        binaryIntIntDic[OperatorCode.And] = IntOp({ e, a -> b(a[0]!!.getIntValue(e) != 0L && a[1]!!.getIntValue(e) != 0L) })
        binaryIntIntDic[OperatorCode.Or] = IntOp({ e, a -> b(a[0]!!.getIntValue(e) != 0L || a[1]!!.getIntValue(e) != 0L) })
        binaryIntIntDic[OperatorCode.Xor] = IntOp({ e, a ->
            val i1 = a[0]!!.getIntValue(e); val i2 = a[1]!!.getIntValue(e)
            b((i1 == 0L && i2 != 0L) || (i1 != 0L && i2 == 0L))
        })
        binaryIntIntDic[OperatorCode.Nand] = IntOp({ e, a -> b(a[0]!!.getIntValue(e) == 0L || a[1]!!.getIntValue(e) == 0L) })
        binaryIntIntDic[OperatorCode.Nor] = IntOp({ e, a -> b(a[0]!!.getIntValue(e) == 0L && a[1]!!.getIntValue(e) == 0L) })
        binaryIntIntDic[OperatorCode.BitAnd] = IntOp({ e, a -> a[0]!!.getIntValue(e) and a[1]!!.getIntValue(e) })
        binaryIntIntDic[OperatorCode.BitOr] = IntOp({ e, a -> a[0]!!.getIntValue(e) or a[1]!!.getIntValue(e) })
        binaryIntIntDic[OperatorCode.BitXor] = IntOp({ e, a -> a[0]!!.getIntValue(e) xor a[1]!!.getIntValue(e) })
        binaryIntIntDic[OperatorCode.RightShift] = IntOp({ e, a -> a[0]!!.getIntValue(e) shr a[1]!!.getIntValue(e).toInt() })
        binaryIntIntDic[OperatorCode.LeftShift] = IntOp({ e, a -> a[0]!!.getIntValue(e) shl a[1]!!.getIntValue(e).toInt() })

        binaryStrStrDic[OperatorCode.Plus] = StrOp { e, a -> a[0]!!.getStrValue(e) + a[1]!!.getStrValue(e) }
        binaryStrStrDic[OperatorCode.Equal] = IntOp({ e, a -> b(a[0]!!.getStrValue(e) == a[1]!!.getStrValue(e)) })
        binaryStrStrDic[OperatorCode.Greater] = IntOp({ e, a -> b(ordinalCompare(a[0]!!.getStrValue(e), a[1]!!.getStrValue(e)) > 0) })
        binaryStrStrDic[OperatorCode.Less] = IntOp({ e, a -> b(ordinalCompare(a[0]!!.getStrValue(e), a[1]!!.getStrValue(e)) < 0) })
        // 原作どおり (>= と <= も c < 0 で判定している)
        binaryStrStrDic[OperatorCode.GreaterEqual] = IntOp({ e, a -> b(ordinalCompare(a[0]!!.getStrValue(e), a[1]!!.getStrValue(e)) < 0) })
        binaryStrStrDic[OperatorCode.LessEqual] = IntOp({ e, a -> b(ordinalCompare(a[0]!!.getStrValue(e), a[1]!!.getStrValue(e)) < 0) })
        binaryStrStrDic[OperatorCode.NotEqual] = IntOp({ e, a -> b(a[0]!!.getStrValue(e) != a[1]!!.getStrValue(e)) })

        binaryMultIntStr = StrOp { e, a ->
            val str: String
            val value: Long
            if (a[0]!!.getOperandType() == EType.Int64) { value = a[0]!!.getIntValue(e); str = a[1]!!.getStrValue(e) }
            else { str = a[0]!!.getStrValue(e); value = a[1]!!.getIntValue(e) }
            if (value < 0) throw CodeEE("文字列に負の値($value)を乗算しようとしました")
            if (value >= 10000) throw CodeEE("文字列に10000以上の値($value)を乗算しようとしました")
            if (str == "" || value == 0L) "" else str.repeat(value.toInt())
        }
        ternaryIntIntInt = IntOp({ e, a -> if (a[0]!!.getIntValue(e) != 0L) a[1]!!.getIntValue(e) else a[2]!!.getIntValue(e) })
        ternaryIntStrStr = StrOp { e, a -> if (a[0]!!.getIntValue(e) != 0L) a[1]!!.getStrValue(e) else a[2]!!.getStrValue(e) }
    }

    private fun typeName(t: EType) = when (t) { EType.Int64 -> "数値型"; EType.String -> "文字列型"; else -> "不定型" }

    fun reduceUnaryTerm(op: OperatorCode, o1: IOperandTerm): IOperandTerm {
        var method: OperatorMethod? = null
        if (op == OperatorCode.Increment || op == OperatorCode.Decrement) {
            val v = o1 as? VariableTerm ?: throw CodeEE("変数以外をインクリメントすることはできません")
            if (v.identifier.isConst) throw CodeEE("変更できない変数をインクリメントすることはできません")
        }
        if (o1.getOperandType() == EType.Int64) {
            if (op == OperatorCode.Plus) return o1
            method = unaryDic[op]
        }
        if (method != null) return FunctionMethodTerm(method, arrayOf(o1))
        throw CodeEE(typeName(o1.getOperandType()) + "に単項演算子'" + OperatorManager.toOperatorString(op) + "'は適用できません")
    }

    fun reduceUnaryAfterTerm(op: OperatorCode, o1: IOperandTerm): IOperandTerm {
        var method: OperatorMethod? = null
        if (op == OperatorCode.Increment || op == OperatorCode.Decrement) {
            val v = o1 as? VariableTerm ?: throw CodeEE("変数以外をインクリメントすることはできません")
            if (v.identifier.isConst) throw CodeEE("変更できない変数をインクリメントすることはできません")
        }
        if (o1.getOperandType() == EType.Int64) method = unaryAfterDic[op]
        if (method != null) return FunctionMethodTerm(method, arrayOf(o1))
        throw CodeEE(typeName(o1.getOperandType()) + "に後置単項演算子'" + OperatorManager.toOperatorString(op) + "'は適用できません")
    }

    fun reduceBinaryTerm(op: OperatorCode, left: IOperandTerm, right: IOperandTerm): IOperandTerm {
        var method: OperatorMethod? = null
        val lt = left.getOperandType()
        val rt = right.getOperandType()
        if (lt == EType.Int64 && rt == EType.Int64) method = binaryIntIntDic[op]
        else if (lt == EType.String && rt == EType.String) method = binaryStrStrDic[op]
        else if ((lt == EType.Int64 && rt == EType.String) || (lt == EType.String && rt == EType.Int64)) {
            if (op == OperatorCode.Mult) method = binaryMultIntStr
        }
        if (method != null) return FunctionMethodTerm(method, arrayOf(left, right))
        throw CodeEE(typeName(lt) + "と" + typeName(rt) + "の演算に二項演算子'" + OperatorManager.toOperatorString(op) + "'は適用できません")
    }

    fun reduceTernaryTerm(o1: IOperandTerm, o2: IOperandTerm, o3: IOperandTerm): IOperandTerm {
        var method: OperatorMethod? = null
        if (o1.getOperandType() == EType.Int64 && o2.getOperandType() == EType.Int64 && o3.getOperandType() == EType.Int64) method = ternaryIntIntInt
        else if (o1.getOperandType() == EType.Int64 && o2.getOperandType() == EType.String && o3.getOperandType() == EType.String) method = ternaryIntStrStr
        if (method != null) return FunctionMethodTerm(method, arrayOf(o1, o2, o3))
        throw CodeEE("三項演算子の使用法が不正です")
    }
}
