package com.eraandroid.emuera.gamedata.function

import com.eraandroid.emuera.config.Config
import com.eraandroid.emuera.gamedata.expression.*
import com.eraandroid.emuera.gameview.ConsoleRedraw
import com.eraandroid.emuera.gameview.DisplayLineAlignment
import com.eraandroid.emuera.sub.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.TimeZone

/* Creator.Method.cs part 2: 汎用処理系(続き) / 定数取得 / 数学関数 */

internal class BarStringMethod : FunctionMethod() {
    init { returnType = EType.String; argumentTypeArray = arrayOf(EType.Int64, EType.Int64, EType.Int64); canRestructure = true }
    override fun getStrValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): String =
        exm.createBar(arguments[0]!!.getIntValue(exm), arguments[1]!!.getIntValue(exm), arguments[2]!!.getIntValue(exm))
}

internal class CurrentAlignMethod : FunctionMethod() {
    init { returnType = EType.String; argumentTypeArray = arrayOf(); canRestructure = false }
    override fun getStrValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): String = when (exm.console.alignment) {
        DisplayLineAlignment.LEFT -> "LEFT"
        DisplayLineAlignment.CENTER -> "CENTER"
        else -> "RIGHT"
    }
}

internal class CurrentRedrawMethod : FunctionMethod() {
    init { returnType = EType.Int64; argumentTypeArray = arrayOf(); canRestructure = false }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long = if (exm.console.redraw == ConsoleRedraw.None) 0L else 1L
}

internal class ColorFromNameMethod : FunctionMethod() {
    init { returnType = EType.Int64; argumentTypeArray = arrayOf(EType.String); canRestructure = true }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long {
        val colorName = arguments[0]!!.getStrValue(exm)
        val c = NamedColors.fromName(colorName)
        if (c != null) return c.toLong()
        if (colorName.equals("transparent", ignoreCase = true)) throw CodeEE("無色透明(Transparent)は色として指定できません")
        return -1
    }
}

internal class ColorFromRGBMethod : FunctionMethod() {
    init { returnType = EType.Int64; argumentTypeArray = arrayOf(EType.Int64, EType.Int64, EType.Int64); canRestructure = true }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long {
        val r = arguments[0]!!.getIntValue(exm)
        if (r < 0 || r > 255) throw CodeEE("第１引数が0から255の範囲外です")
        val g = arguments[1]!!.getIntValue(exm)
        if (g < 0 || g > 255) throw CodeEE("第２引数が0から255の範囲外です")
        val b = arguments[2]!!.getIntValue(exm)
        if (b < 0 || b > 255) throw CodeEE("第３引数が0から255の範囲外です")
        return (r shl 16) + (g shl 8) + b
    }
}

internal class GetRefMethod : FunctionMethod() {
    init { returnType = EType.String; argumentTypeArray = null; canRestructure = false }
    override fun checkArgumentType(name: String, arguments: Array<IOperandTerm?>): String? {
        if (arguments.isEmpty()) return name + "関数には少なくとも1つの引数が必要です"
        if (arguments.size > 1) return name + "関数の引数が多すぎます"
        val a0 = arguments[0] ?: return name + "関数の1番目の引数は省略できません"
        if (a0 !is UserDefinedRefMethodNoArgTerm) return name + "関数の1番目の引数が関数参照ではありません"
        return null
    }
    override fun getStrValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): String = (arguments[0] as UserDefinedRefMethodNoArgTerm).getRefName()
}

internal class MoneyStrMethod : FunctionMethod() {
    init { returnType = EType.String; argumentTypeArray = null; canRestructure = true }
    override fun checkArgumentType(name: String, arguments: Array<IOperandTerm?>): String? {
        if (arguments.isEmpty()) return name + "関数には少なくとも1つの引数が必要です"
        if (arguments.size > 2) return name + "関数の引数が多すぎます"
        val a0 = arguments[0] ?: return name + "関数の1番目の引数は省略できません"
        if (a0.getOperandType() != EType.Int64) return name + "関数の1番目の引数の型が正しくありません"
        if (arguments.size >= 2 && arguments[1] != null && arguments[1]!!.getOperandType() != EType.String) return name + "関数の2番目の引数の型が正しくありません"
        return null
    }
    override fun getStrValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): String {
        val money = arguments[0]!!.getIntValue(exm)
        if (arguments.size < 2 || arguments[1] == null)
            return if (Config.MoneyFirst) Config.MoneyLabel + money else money.toString() + Config.MoneyLabel
        val format = arguments[1]!!.getStrValue(exm)
        val ret = try { DotNetFormat.formatLong(money, format) } catch (e: DotNetFormat.FormatException) {
            throw CodeEE("MONEYSTR関数の第2引数の書式指定が間違っています")
        }
        return if (Config.MoneyFirst) Config.MoneyLabel + ret else ret + Config.MoneyLabel
    }
}

internal class GetPrintCPerLineMethod : FunctionMethod() {
    init { returnType = EType.Int64; argumentTypeArray = arrayOf(); canRestructure = true }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long = Config.PrintCPerLine.toLong()
}

internal class PrintCLengthMethod : FunctionMethod() {
    init { returnType = EType.Int64; argumentTypeArray = arrayOf(); canRestructure = true }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long = Config.PrintCLength.toLong()
}

internal class GetSaveNosMethod : FunctionMethod() {
    init { returnType = EType.Int64; argumentTypeArray = arrayOf(); canRestructure = true }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long = Config.SaveDataNos.toLong()
}

internal object DotNetTime {
    /** DateTime.Now.Ticks 相当 (ローカル時刻, 100ns 単位) */
    fun nowTicks(): Long {
        val ms = System.currentTimeMillis()
        val local = ms + TimeZone.getDefault().getOffset(ms)
        return (local + 62135596800000L) * 10000L
    }
}

internal class GettimeMethod : FunctionMethod() {
    init { returnType = EType.Int64; argumentTypeArray = arrayOf(); canRestructure = false }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long {
        val n = LocalDateTime.now()
        var date = n.year.toLong()
        date = date * 100 + n.monthValue
        date = date * 100 + n.dayOfMonth
        date = date * 100 + n.hour
        date = date * 100 + n.minute
        date = date * 100 + n.second
        date = date * 1000 + n.nano / 1000000
        return date
    }
}

internal class GettimesMethod : FunctionMethod() {
    init { returnType = EType.String; argumentTypeArray = arrayOf(); canRestructure = false }
    override fun getStrValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): String =
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"))
}

internal class GetmsMethod : FunctionMethod() {
    init { returnType = EType.Int64; argumentTypeArray = arrayOf(); canRestructure = false }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long = DotNetTime.nowTicks() / 10000
}

internal class GetSecondMethod : FunctionMethod() {
    init { returnType = EType.Int64; argumentTypeArray = arrayOf(); canRestructure = false }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long = DotNetTime.nowTicks() / 10000000
}

internal class RandMethod : FunctionMethod() {
    init { returnType = EType.Int64; argumentTypeArray = null; canRestructure = false }
    override fun checkArgumentType(name: String, arguments: Array<IOperandTerm?>): String? {
        if (arguments.isEmpty()) return name + "関数には少なくとも1つの引数が必要です"
        if (arguments.size > 2) return name + "関数の引数が多すぎます"
        if (arguments.size == 1) {
            val a0 = arguments[0] ?: return name + "関数には少なくとも1つの引数が必要です"
            if (a0.getOperandType() != EType.Int64) return name + "関数の1番目の引数の型が正しくありません"
            return null
        }
        if (arguments[0] != null && arguments[0]!!.getOperandType() != EType.Int64) return name + "関数の1番目の引数の型が正しくありません"
        if (arguments[1] != null && arguments[1]!!.getOperandType() != EType.Int64) return name + "関数の2番目の引数の型が正しくありません"
        return null
    }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long {
        var min = 0L
        val max: Long
        if (arguments.size == 1) max = arguments[0]!!.getIntValue(exm)
        else {
            if (arguments[0] != null) min = arguments[0]!!.getIntValue(exm)
            max = arguments[1]!!.getIntValue(exm)
        }
        if (max <= min) {
            if (min == 0L) throw CodeEE("RANDの最大値に0以下の値(${max})が指定されました")
            else throw CodeEE("RANDの最大値に最小値以下の値(${max})が指定されました")
        }
        return exm.vEvaluator.getNextRand(max - min) + min
    }
}

internal class MaxMethod(private val isMax: Boolean = true) : FunctionMethod() {
    init { returnType = EType.Int64; argumentTypeArray = null; canRestructure = true }
    override fun checkArgumentType(name: String, arguments: Array<IOperandTerm?>): String? {
        if (arguments.isEmpty()) return name + "関数には少なくとも1つの引数が必要です"
        for (i in arguments.indices) {
            val a = arguments[i] ?: return name + "関数の" + (i + 1) + "番目の引数は省略できません"
            if (a.getOperandType() != EType.Int64) return name + "関数の" + (i + 1) + "番目の引数の型が正しくありません"
        }
        return null
    }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long {
        var ret = arguments[0]!!.getIntValue(exm)
        for (i in 1 until arguments.size) {
            val n = arguments[i]!!.getIntValue(exm)
            if (isMax) { if (ret < n) ret = n } else { if (ret > n) ret = n }
        }
        return ret
    }
}

internal class AbsMethod : FunctionMethod() {
    init { returnType = EType.Int64; argumentTypeArray = arrayOf(EType.Int64); canRestructure = true }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long {
        val ret = arguments[0]!!.getIntValue(exm)
        if (ret == Long.MIN_VALUE) throw CodeEE("符号付き64bit整数の最小値(${Long.MIN_VALUE})に対して絶対値を取ることはできません")
        return kotlin.math.abs(ret)
    }
}

private fun checkDouble(d: Double, nan: String, inf: String): Long {
    if (d.isNaN()) throw CodeEE(nan)
    if (d.isInfinite()) throw CodeEE(inf)
    if (d >= Long.MAX_VALUE.toDouble() || d <= Long.MIN_VALUE.toDouble()) throw CodeEE("計算結果(${d})が64ビット符号付き整数の範囲外です")
    return d.toLong()
}

internal class PowerMethod : FunctionMethod() {
    init { returnType = EType.Int64; argumentTypeArray = arrayOf(EType.Int64, EType.Int64); canRestructure = true }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long {
        val x = arguments[0]!!.getIntValue(exm)
        val y = arguments[1]!!.getIntValue(exm)
        val pow = Math.pow(x.toDouble(), y.toDouble())
        if (pow.isNaN()) throw CodeEE("累乗結果が非数値です")
        if (pow.isInfinite()) throw CodeEE("累乗結果が無限大です")
        if (pow >= Long.MAX_VALUE.toDouble() || pow <= Long.MIN_VALUE.toDouble()) throw CodeEE("累乗結果(${pow})が64ビット符号付き整数の範囲外です")
        return pow.toLong()
    }
}

internal class SqrtMethod : FunctionMethod() {
    init { returnType = EType.Int64; argumentTypeArray = arrayOf(EType.Int64); canRestructure = true }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long {
        val ret = arguments[0]!!.getIntValue(exm)
        if (ret < 0) throw CodeEE("SQRT関数の引数に負の値が指定されました")
        return Math.sqrt(ret.toDouble()).toLong()
    }
}

internal class CbrtMethod : FunctionMethod() {
    init { returnType = EType.Int64; argumentTypeArray = arrayOf(EType.Int64); canRestructure = true }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long {
        val ret = arguments[0]!!.getIntValue(exm)
        if (ret < 0) throw CodeEE("CBRT関数の引数に負の値が指定されました")
        return Math.pow(ret.toDouble(), 1.0 / 3.0).toLong()
    }
}

internal class LogMethod(private val base: Double = Math.E) : FunctionMethod() {
    init { returnType = EType.Int64; argumentTypeArray = arrayOf(EType.Int64); canRestructure = true }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long {
        val ret = arguments[0]!!.getIntValue(exm)
        if (ret <= 0) throw CodeEE("対数関数の引数に0以下の値が指定されました")
        if (base <= 0.0) throw CodeEE("対数関数の底に0以下の値が指定されました")
        val d = if (base == Math.E) Math.log(ret.toDouble()) else Math.log10(ret.toDouble())
        return checkDouble(d, "計算値が非数値です", "計算値が無限大です")
    }
}

internal class ExpMethod : FunctionMethod() {
    init { returnType = EType.Int64; argumentTypeArray = arrayOf(EType.Int64); canRestructure = true }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long =
        checkDouble(Math.exp(arguments[0]!!.getIntValue(exm).toDouble()), "計算値が非数値です", "計算値が無限大です")
}

internal class SignMethod : FunctionMethod() {
    init { returnType = EType.Int64; argumentTypeArray = arrayOf(EType.Int64); canRestructure = true }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long = java.lang.Long.signum(arguments[0]!!.getIntValue(exm)).toLong()
}

internal class GetLimitMethod : FunctionMethod() {
    init { returnType = EType.Int64; argumentTypeArray = arrayOf(EType.Int64, EType.Int64, EType.Int64); canRestructure = true }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long {
        val value = arguments[0]!!.getIntValue(exm)
        val min = arguments[1]!!.getIntValue(exm)
        val max = arguments[2]!!.getIntValue(exm)
        return if (value < min) min else if (value > max) max else value
    }
}
