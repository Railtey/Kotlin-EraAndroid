package com.eraandroid.emuera.gamedata.function

import com.eraandroid.emuera.GlobalStatic
import com.eraandroid.emuera.gamedata.expression.*
import com.eraandroid.emuera.gamedata.variable.*
import com.eraandroid.emuera.sub.*

/* Creator.Method.cs part 3: 変数操作系 */

internal class SumArrayMethod(private val isCharaRange: Boolean = false) : FunctionMethod() {
    init { returnType = EType.Int64; argumentTypeArray = null; canRestructure = false }
    override fun checkArgumentType(name: String, arguments: Array<IOperandTerm?>): String? {
        if (arguments.isEmpty()) return name + "関数には少なくとも1つの引数が必要です"
        if (arguments.size > 3) return name + "関数の引数が多すぎます"
        val a0 = arguments[0] ?: return name + "関数の1番目の引数は省略できません"
        if (a0 !is VariableTerm) return name + "関数の1番目の引数が変数ではありません"
        if (a0.isString) return name + "関数の1番目の引数が数値変数ではありません"
        if (isCharaRange && !a0.identifier.isCharacterData) return name + "関数の1番目の引数がキャラクタ変数ではありません"
        if (!isCharaRange && !a0.identifier.isArray1D && !a0.identifier.isArray2D && !a0.identifier.isArray3D) return name + "関数の1番目の引数が配列変数ではありません"
        if (arguments.size == 1) return null
        if (arguments[1] != null && arguments[1]!!.getOperandType() != EType.Int64) return name + "関数の2番目の変数が数値ではありません"
        if (arguments.size == 2) return null
        if (arguments[2] != null && arguments[2]!!.getOperandType() != EType.Int64) return name + "関数の3番目の変数が数値ではありません"
        return null
    }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long {
        val varTerm = arguments[0] as VariableTerm
        val index1 = if (arguments.size >= 2 && arguments[1] != null) arguments[1]!!.getIntValue(exm) else 0L
        val index2 = if (arguments.size == 3 && arguments[2] != null) arguments[2]!!.getIntValue(exm)
            else if (isCharaRange) exm.vEvaluator.CHARANUM else varTerm.getLastLength().toLong()
        val p = varTerm.getFixedVariableTerm(exm)
        if (!isCharaRange) {
            p.isArrayRangeValid(index1, index2, "SUMARRAY", 2L, 3L)
            return exm.vEvaluator.getArraySum(p, index1, index2)
        }
        val charaNum = exm.vEvaluator.CHARANUM
        if (index1 >= charaNum || index1 < 0 || index2 > charaNum || index2 < 0)
            throw CodeEE("SUMCARRAY関数の範囲指定がキャラクタ配列の範囲を超えています(${index1}～${index2})")
        return exm.vEvaluator.getArraySumChara(p, index1, index2)
    }
}

internal class MatchMethod(private val isCharaRange: Boolean = false) : FunctionMethod() {
    init { returnType = EType.Int64; argumentTypeArray = null; canRestructure = false; hasUniqueRestructure = true }
    override fun checkArgumentType(name: String, arguments: Array<IOperandTerm?>): String? {
        if (arguments.size < 2) return name + "関数には少なくとも2つの引数が必要です"
        if (arguments.size > 4) return name + "関数の引数が多すぎます"
        val a0 = arguments[0] ?: return name + "関数の1番目の引数は省略できません"
        if (a0 !is VariableTerm) return name + "関数の1番目の引数が変数ではありません"
        if (isCharaRange && !a0.identifier.isCharacterData) return name + "関数の1番目の引数がキャラクタ変数ではありません"
        if (!isCharaRange && (a0.identifier.isArray2D || a0.identifier.isArray3D)) return name + "関数は二重配列・三重配列には対応していません"
        if (!isCharaRange && !a0.identifier.isArray1D) return name + "関数の1番目の引数が配列変数ではありません"
        val a1 = arguments[1] ?: return name + "関数の2番目の引数は省略できません"
        if (a1.getOperandType() != a0.getOperandType()) return name + "関数の1番目の引数と2番目の引数の型が異なります"
        if (arguments.size >= 3 && arguments[2] != null && arguments[2]!!.getOperandType() != EType.Int64) return name + "関数の3番目の引数の型が正しくありません"
        if (arguments.size >= 4 && arguments[3] != null && arguments[3]!!.getOperandType() != EType.Int64) return name + "関数の4番目の引数の型が正しくありません"
        return null
    }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long {
        val varTerm = arguments[0] as VariableTerm
        val start = if (arguments.size > 2 && arguments[2] != null) arguments[2]!!.getIntValue(exm) else 0L
        val end = if (arguments.size > 3 && arguments[3] != null) arguments[3]!!.getIntValue(exm)
            else if (isCharaRange) exm.vEvaluator.CHARANUM else varTerm.getLength().toLong()
        val p = varTerm.getFixedVariableTerm(exm)
        val isInt = arguments[0]!!.getOperandType() == EType.Int64
        if (!isCharaRange) {
            p.isArrayRangeValid(start, end, "MATCH", 3L, 4L)
            return if (isInt) exm.vEvaluator.getMatch(p, arguments[1]!!.getIntValue(exm), start, end)
            else exm.vEvaluator.getMatch(p, arguments[1]!!.getStrValue(exm), start, end)
        }
        val charaNum = exm.vEvaluator.CHARANUM
        if (start >= charaNum || start < 0 || end > charaNum || end < 0)
            throw CodeEE("CMATCH関数の範囲指定がキャラクタ配列の範囲を超えています(${start}～${end})")
        return if (isInt) exm.vEvaluator.getMatchChara(p, arguments[1]!!.getIntValue(exm), start, end)
        else exm.vEvaluator.getMatchChara(p, arguments[1]!!.getStrValue(exm), start, end)
    }
    override fun uniqueRestructure(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Boolean {
        arguments[0]!!.restructure(exm)
        for (i in 1 until arguments.size) arguments[i] = arguments[i]?.restructure(exm)
        return false
    }
}

private fun checkAllSameType(name: String, arguments: Array<IOperandTerm?>): String? {
    if (arguments.size < 2) return name + "関数には少なくとも2つの引数が必要です"
    val a0 = arguments[0] ?: return name + "関数の1番目の引数は省略できません"
    val baseType = a0.getOperandType()
    for (i in 1 until arguments.size) {
        val a = arguments[i] ?: return name + "関数の" + (i + 1) + "番目の引数は省略できません"
        if (a.getOperandType() != baseType) return name + "関数の" + (i + 1) + "番目の引数の型が正しくありません"
    }
    return null
}

internal class GroupMatchMethod : FunctionMethod() {
    init { returnType = EType.Int64; argumentTypeArray = null; canRestructure = false }
    override fun checkArgumentType(name: String, arguments: Array<IOperandTerm?>): String? = checkAllSameType(name, arguments)
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long {
        var ret = 0L
        if (arguments[0]!!.getOperandType() == EType.Int64) {
            val b = arguments[0]!!.getIntValue(exm)
            for (i in 1 until arguments.size) if (b == arguments[i]!!.getIntValue(exm)) ret++
        } else {
            val b = arguments[0]!!.getStrValue(exm)
            for (i in 1 until arguments.size) if (b == arguments[i]!!.getStrValue(exm)) ret++
        }
        return ret
    }
}

internal class NosamesMethod : FunctionMethod() {
    init { returnType = EType.Int64; argumentTypeArray = null; canRestructure = false }
    override fun checkArgumentType(name: String, arguments: Array<IOperandTerm?>): String? = checkAllSameType(name, arguments)
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long {
        val distinct = if (arguments[0]!!.getOperandType() == EType.Int64) arguments.map { it!!.getIntValue(exm) }.distinct().size
            else arguments.map { it!!.getStrValue(exm) }.distinct().size
        return if (distinct != arguments.size) 0L else 1L
    }
}

internal class AllsamesMethod : FunctionMethod() {
    init { returnType = EType.Int64; argumentTypeArray = null; canRestructure = false }
    override fun checkArgumentType(name: String, arguments: Array<IOperandTerm?>): String? = checkAllSameType(name, arguments)
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long {
        if (arguments[0]!!.getOperandType() == EType.Int64) {
            val b = arguments[0]!!.getIntValue(exm)
            for (i in 1 until arguments.size) if (b != arguments[i]!!.getIntValue(exm)) return 0L
        } else {
            val b = arguments[0]!!.getStrValue(exm)
            for (i in 1 until arguments.size) if (b != arguments[i]!!.getStrValue(exm)) return 0L
        }
        return 1L
    }
}

internal class MaxArrayMethod(private val isCharaRange: Boolean = false, private val isMax: Boolean = true) : FunctionMethod() {
    private val funcName = (if (isMax) "MAX" else "MIN") + (if (isCharaRange) "C" else "") + "ARRAY"
    init { returnType = EType.Int64; argumentTypeArray = null; canRestructure = false }
    override fun checkArgumentType(name: String, arguments: Array<IOperandTerm?>): String? {
        if (arguments.isEmpty()) return name + "関数には少なくとも1つの引数が必要です"
        if (arguments.size > 3) return name + "関数の引数が多すぎます"
        val a0 = arguments[0] ?: return name + "関数の1番目の引数は省略できません"
        if (a0 !is VariableTerm) return name + "関数の1番目の引数が変数ではありません"
        if (isCharaRange && !a0.identifier.isCharacterData) return name + "関数の1番目の引数がキャラクタ変数ではありません"
        if (!a0.isInteger) return name + "関数の1番目の引数が数値変数ではありません"
        if (!isCharaRange && (a0.identifier.isArray2D || a0.identifier.isArray3D)) return name + "関数は二重配列・三重配列には対応していません"
        if (!a0.identifier.isArray1D) return name + "関数の1番目の引数が配列変数ではありません"
        if (arguments.size >= 2 && arguments[1] != null && arguments[1]!!.getOperandType() != EType.Int64) return name + "関数の2番目の引数の型が正しくありません"
        if (arguments.size >= 3 && arguments[2] != null && arguments[2]!!.getOperandType() != EType.Int64) return name + "関数の3番目の引数の型が正しくありません"
        return null
    }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long {
        val vTerm = arguments[0] as VariableTerm
        val start = if (arguments.size > 1 && arguments[1] != null) arguments[1]!!.getIntValue(exm) else 0L
        val end = if (arguments.size > 2 && arguments[2] != null) arguments[2]!!.getIntValue(exm)
            else if (isCharaRange) exm.vEvaluator.CHARANUM else vTerm.getLength().toLong()
        val p = vTerm.getFixedVariableTerm(exm)
        if (!isCharaRange) {
            p.isArrayRangeValid(start, end, funcName, 2L, 3L)
            return exm.vEvaluator.getMaxArray(p, start, end, isMax)
        }
        val charaNum = exm.vEvaluator.CHARANUM
        if (start >= charaNum || start < 0 || end > charaNum || end < 0)
            throw CodeEE("${funcName}関数の範囲指定がキャラクタ配列の範囲を超えています(${start}～${end})")
        return exm.vEvaluator.getMaxArrayChara(p, start, end, isMax)
    }
}

internal class GetbitMethod : FunctionMethod() {
    init { returnType = EType.Int64; argumentTypeArray = arrayOf(EType.Int64, EType.Int64); canRestructure = true }
    override fun checkArgumentType(name: String, arguments: Array<IOperandTerm?>): String? {
        super.checkArgumentType(name, arguments)?.let { return it }
        val a1 = arguments[1]
        if (a1 is SingleTerm) {
            val m = a1.int
            if (m < 0 || m > 63) return "GETBIT関数の第２引数(${m})が範囲(０～６３)を超えています"
        }
        return null
    }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long {
        val n = arguments[0]!!.getIntValue(exm)
        val m = arguments[1]!!.getIntValue(exm)
        if (m < 0 || m > 63) throw CodeEE("GETBIT関数の第２引数(${m})が範囲(０～６３)を超えています")
        return (n shr m.toInt()) and 1
    }
}

internal class GetnumMethod : FunctionMethod() {
    init { returnType = EType.Int64; argumentTypeArray = null; canRestructure = true; hasUniqueRestructure = true }
    override fun checkArgumentType(name: String, arguments: Array<IOperandTerm?>): String? {
        if (arguments.size != 2) return name + "関数には2つの引数が必要です"
        val a0 = arguments[0] ?: return name + "関数の1番目の引数は省略できません"
        if (a0 !is VariableTerm) return name + "関数の1番目の引数の型が正しくありません"
        val a1 = arguments[1] ?: return name + "関数の2番目の引数は省略できません"
        if (a1.getOperandType() != EType.String) return name + "関数の2番目の引数の型が正しくありません"
        return null
    }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long {
        val vToken = arguments[0] as VariableTerm
        val key = arguments[1]!!.getStrValue(exm)
        return exm.vEvaluator.constant.tryKeywordToInteger(vToken.identifier.code, key, -1)?.toLong() ?: -1L
    }
    override fun uniqueRestructure(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Boolean {
        arguments[1] = arguments[1]!!.restructure(exm)
        return arguments[1] is SingleTerm
    }
}

internal class GetnumBMethod : FunctionMethod() {
    init { returnType = EType.Int64; argumentTypeArray = arrayOf(EType.String, EType.String); canRestructure = true }
    override fun checkArgumentType(name: String, arguments: Array<IOperandTerm?>): String? {
        super.checkArgumentType(name, arguments)?.let { return it }
        val a0 = arguments[0] ?: return name + "関数の1番目の引数は省略できません"
        if (a0 is SingleTerm && GlobalStatic.IdentifierDictionary!!.getVariableToken(a0.str, null, true) == null)
            return name + "関数の1番目の引数が変数名ではありません"
        return null
    }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long {
        val s = arguments[0]!!.getStrValue(exm)
        val v = GlobalStatic.IdentifierDictionary!!.getVariableToken(s, null, true) ?: throw CodeEE("GETNUMBの1番目の引数(\"$s\")が変数名ではありません")
        val key = arguments[1]!!.getStrValue(exm)
        return exm.vEvaluator.constant.tryKeywordToInteger(v.code, key, -1)?.toLong() ?: -1L
    }
}

internal class GetPalamLVMethod(private val exp: Boolean = false) : FunctionMethod() {
    init { returnType = EType.Int64; argumentTypeArray = arrayOf(EType.Int64, EType.Int64); canRestructure = false }
    override fun checkArgumentType(name: String, arguments: Array<IOperandTerm?>): String? {
        super.checkArgumentType(name, arguments)?.let { return it }
        if (arguments[0] == null) return name + "関数の1番目の引数は省略できません"
        return null
    }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long {
        val value = arguments[0]!!.getIntValue(exm)
        val maxLv = arguments[1]!!.getIntValue(exm)
        return if (exp) exm.vEvaluator.getExpLv(value, maxLv) else exm.vEvaluator.getPalamLv(value, maxLv)
    }
}

internal fun makeRegex(pattern: String, err: String): Regex = try { Regex(pattern) } catch (e: IllegalArgumentException) {
    throw CodeEE(err + (e.message ?: ""))
}

internal class FindElementMethod(private val isLast: Boolean) : FunctionMethod() {
    private val funcName = if (isLast) "FINDLASTELEMENT" else "FINDELEMENT"
    init { returnType = EType.Int64; argumentTypeArray = null; canRestructure = true; hasUniqueRestructure = true }
    override fun checkArgumentType(name: String, arguments: Array<IOperandTerm?>): String? {
        if (arguments.size < 2) return name + "関数には少なくとも2つの引数が必要です"
        if (arguments.size > 5) return name + "関数の引数が多すぎます"
        val a0 = arguments[0] ?: return name + "関数の1番目の引数は省略できません"
        if (a0 !is VariableTerm) return name + "関数の1番目の引数が変数ではありません"
        if (a0.identifier.isArray2D || a0.identifier.isArray3D) return name + "関数は二重配列・三重配列には対応していません"
        if (!a0.identifier.isArray1D) return name + "関数の1番目の引数が配列変数ではありません"
        val a1 = arguments[1] ?: return name + "関数の2番目の引数は省略できません"
        if (a1.getOperandType() != a0.getOperandType()) return name + "関数の2番目の引数の型が正しくありません"
        for (i in 2..4) if (arguments.size > i && arguments[i] != null && arguments[i]!!.getOperandType() != EType.Int64)
            return name + "関数の" + (i + 1) + "番目の引数の型が正しくありません"
        return null
    }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long {
        val varTerm = arguments[0] as VariableTerm
        val start = if (arguments.size > 2 && arguments[2] != null) arguments[2]!!.getIntValue(exm) else 0L
        val end = if (arguments.size > 3 && arguments[3] != null) arguments[3]!!.getIntValue(exm) else varTerm.getLength().toLong()
        val isExact = arguments.size > 4 && arguments[4] != null && arguments[4]!!.getIntValue(exm) != 0L
        val p = varTerm.getFixedVariableTerm(exm)
        p.isArrayRangeValid(start, end, funcName, 3L, 4L)
        if (arguments[0]!!.getOperandType() == EType.Int64)
            return exm.vEvaluator.findElement(p, arguments[1]!!.getIntValue(exm), start, end, isExact, isLast)
        val reg = try { Regex(arguments[1]!!.getStrValue(exm)) } catch (e: IllegalArgumentException) { throw CodeEE("第2引数が正規表現として不正です") }
        return exm.vEvaluator.findElement(p, reg, start, end, isExact, isLast)
    }
    override fun uniqueRestructure(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Boolean {
        arguments[0]!!.restructure(exm)
        var isConst = (arguments[0] as VariableTerm).identifier.isConst
        for (i in 1 until arguments.size) {
            val a = arguments[i] ?: continue
            arguments[i] = a.restructure(exm)
            if (isConst && arguments[i] !is SingleTerm) isConst = false
        }
        return isConst
    }
}

internal class InRangeMethod : FunctionMethod() {
    init { returnType = EType.Int64; argumentTypeArray = arrayOf(EType.Int64, EType.Int64, EType.Int64); canRestructure = true }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long {
        val value = arguments[0]!!.getIntValue(exm)
        return if (value >= arguments[1]!!.getIntValue(exm) && value <= arguments[2]!!.getIntValue(exm)) 1L else 0L
    }
}

internal class InRangeArrayMethod(private val isCharaRange: Boolean = false) : FunctionMethod() {
    init { returnType = EType.Int64; argumentTypeArray = null; canRestructure = false }
    override fun checkArgumentType(name: String, arguments: Array<IOperandTerm?>): String? {
        if (arguments.size < 2) return name + "関数には少なくとも2つの引数が必要です"
        if (arguments.size > 6) return name + "関数の引数が多すぎます"
        val a0 = arguments[0] ?: return name + "関数の1番目の引数は省略できません"
        if (a0 !is VariableTerm) return name + "関数の1番目の引数が変数ではありません"
        if (isCharaRange && !a0.identifier.isCharacterData) return name + "関数の1番目の引数がキャラクタ変数ではありません"
        if (!isCharaRange && (a0.identifier.isArray2D || a0.identifier.isArray3D)) return name + "関数は二重配列・三重配列には対応していません"
        if (!isCharaRange && !a0.identifier.isArray1D) return name + "関数の1番目の引数が配列変数ではありません"
        if (!a0.isInteger) return name + "関数の1番目の引数が数値型変数ではありません"
        val a1 = arguments[1] ?: return name + "関数の2番目の引数は省略できません"
        if (a1.getOperandType() != EType.Int64) return name + "関数の2番目の引数が数値型ではありません"
        val a2 = (if (arguments.size > 2) arguments[2] else null) ?: return name + "関数の3番目の引数は省略できません"
        if (a2.getOperandType() != EType.Int64) return name + "関数の3番目の引数が数値型ではありません"
        if (arguments.size >= 4 && arguments[3] != null && arguments[3]!!.getOperandType() != EType.Int64) return name + "関数の4番目の引数の型が正しくありません"
        if (arguments.size >= 5 && arguments[4] != null && arguments[4]!!.getOperandType() != EType.Int64) return name + "関数の5番目の引数の型が正しくありません"
        return null
    }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long {
        val min = arguments[1]!!.getIntValue(exm)
        val max = arguments[2]!!.getIntValue(exm)
        val varTerm = arguments[0] as VariableTerm
        val start = if (arguments.size > 3 && arguments[3] != null) arguments[3]!!.getIntValue(exm) else 0L
        val end = if (arguments.size > 4 && arguments[4] != null) arguments[4]!!.getIntValue(exm)
            else if (isCharaRange) exm.vEvaluator.CHARANUM else varTerm.getLength().toLong()
        val p = varTerm.getFixedVariableTerm(exm)
        if (!isCharaRange) {
            p.isArrayRangeValid(start, end, "INRANGEARRAY", 4L, 5L)
            return exm.vEvaluator.getInRangeArray(p, min, max, start, end)
        }
        val charaNum = exm.vEvaluator.CHARANUM
        if (start >= charaNum || start < 0 || end > charaNum || end < 0)
            throw CodeEE("INRANGECARRAY関数の範囲指定がキャラクタ配列の範囲を超えています(${start}～${end})")
        return exm.vEvaluator.getInRangeArrayChara(p, min, max, start, end)
    }
}

internal class ArrayMultiSortMethod : FunctionMethod() {
    init { returnType = EType.Int64; argumentTypeArray = null; canRestructure = false; hasUniqueRestructure = true }
    override fun checkArgumentType(name: String, arguments: Array<IOperandTerm?>): String? {
        if (arguments.size < 2) return "${name}関数:少なくとも2の引数が必要です"
        for (i in arguments.indices) {
            val a = arguments[i] ?: return "${name}関数:${i + 1}番目の引数は省略できません"
            if (a !is VariableTerm || a.identifier.isCalc || a.identifier.isConst) return "${name}関数:${i + 1}番目の引数が変数ではありません"
            if (a.identifier.isCharacterData) return "${name}関数:${i + 1}番目の引数がキャラクタ変数です"
            if (i == 0 && !a.identifier.isArray1D) return "${name}関数:${i + 1}番目の引数が一次元配列ではありません"
            if (!a.identifier.isArray1D && !a.identifier.isArray2D && !a.identifier.isArray2D) return "${name}関数:${i + 1}番目の引数が配列変数ではありません"
        }
        return null
    }
    @Suppress("UNCHECKED_CAST")
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long {
        val varTerm = arguments[0] as VariableTerm
        val sortedArray: IntArray
        if (varTerm.identifier.isInteger) {
            val sortList = ArrayList<Pair<Long, Int>>()
            val array = varTerm.identifier.getArray() as LongArray
            for (i in array.indices) {
                if (array[i] == 0L) break
                sortList.add(Pair(array[i], i))
            }
            sortList.sortWith { a, b -> java.lang.Long.signum(a.first - b.first) }
            sortedArray = IntArray(sortList.size) { sortList[it].second }
        } else {
            val sortList = ArrayList<Pair<String, Int>>()
            val array = varTerm.identifier.getArray() as Array<String?>
            for (i in array.indices) {
                val s = array[i]
                if (s.isNullOrEmpty()) return 0
                sortList.add(Pair(s, i))
            }
            sortList.sortWith { a, b -> a.first.compareTo(b.first) }
            sortedArray = IntArray(sortList.size) { sortList[it].second }
        }
        for (t in arguments) {
            val term = t as VariableTerm
            val id = term.identifier
            if (id.isArray1D) {
                if (term.isInteger) {
                    val array = id.getArray() as LongArray
                    val clone = array.clone()
                    if (array.size < sortedArray.size) return 0
                    for (i in sortedArray.indices) array[i] = clone[sortedArray[i]]
                } else {
                    val array = id.getArray() as Array<String?>
                    val clone = array.clone()
                    if (array.size < sortedArray.size) return 0
                    for (i in sortedArray.indices) array[i] = clone[sortedArray[i]]
                }
            } else if (id.isArray2D) {
                if (term.isInteger) {
                    val array = id.getArray() as IntArr2
                    val clone = Array(array.size) { array[it].clone() }
                    if (array.size < sortedArray.size) return 0
                    for (i in sortedArray.indices) for (x in array[i].indices) array[i][x] = clone[sortedArray[i]][x]
                } else {
                    val array = id.getArray() as StrArr2
                    val clone = Array(array.size) { array[it].clone() }
                    if (array.size < sortedArray.size) return 0
                    for (i in sortedArray.indices) for (x in array[i].indices) array[i][x] = clone[sortedArray[i]][x]
                }
            } else if (id.isArray3D) {
                if (term.isInteger) {
                    val array = id.getArray() as IntArr3
                    val clone = Array(array.size) { a -> Array(array[a].size) { array[a][it].clone() } }
                    if (array.size < sortedArray.size) return 0
                    for (i in sortedArray.indices) for (x in array[i].indices) for (y in array[i][x].indices) array[i][x][y] = clone[sortedArray[i]][x][y]
                } else {
                    val array = id.getArray() as StrArr3
                    val clone = Array(array.size) { a -> Array(array[a].size) { array[a][it].clone() } }
                    if (array.size < sortedArray.size) return 0
                    for (i in sortedArray.indices) for (x in array[i].indices) for (y in array[i][x].indices) array[i][x][y] = clone[sortedArray[i]][x][y]
                }
            } else throw ExeEE("異常な配列")
        }
        return 1
    }
    override fun uniqueRestructure(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Boolean {
        for (i in arguments.indices) arguments[i] = arguments[i]!!.restructure(exm)
        return false
    }
}
