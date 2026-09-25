package com.eraandroid.emuera.gamedata.function

import com.eraandroid.emuera.GlobalStatic
import com.eraandroid.emuera.config.ConfigData
import com.eraandroid.emuera.gamedata.ParserMediator
import com.eraandroid.emuera.gamedata.StrForm
import com.eraandroid.emuera.gamedata.expression.*
import com.eraandroid.emuera.gamedata.variable.VariableTerm
import com.eraandroid.emuera.gameview.HtmlManager
import com.eraandroid.emuera.sub.*

/* Creator.Method.cs part 4: 文字列操作系 / html系 */

internal class StrlenMethod : FunctionMethod() {
    init { returnType = EType.Int64; argumentTypeArray = arrayOf(EType.String); canRestructure = true }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long = LangManager.getStrlenLang(arguments[0]!!.getStrValue(exm)).toLong()
}

internal class StrlenuMethod : FunctionMethod() {
    init { returnType = EType.Int64; argumentTypeArray = arrayOf(EType.String); canRestructure = true }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long = arguments[0]!!.getStrValue(exm).length.toLong()
}

private fun checkSubstringArgs(name: String, arguments: Array<IOperandTerm?>): String? {
    if (arguments.isEmpty()) return name + "関数には少なくとも1つの引数が必要です"
    if (arguments.size > 3) return name + "関数の引数が多すぎます"
    val a0 = arguments[0] ?: return name + "関数の1番目の引数は省略できません"
    if (a0.getOperandType() != EType.String) return name + "関数の1番目の引数の型が正しくありません"
    if (arguments.size >= 2 && arguments[1] != null && arguments[1]!!.getOperandType() != EType.Int64) return name + "関数の2番目の引数の型が正しくありません"
    if (arguments.size >= 3 && arguments[2] != null && arguments[2]!!.getOperandType() != EType.Int64) return name + "関数の3番目の引数の型が正しくありません"
    return null
}

internal class SubstringMethod : FunctionMethod() {
    init { returnType = EType.String; argumentTypeArray = null; canRestructure = true }
    override fun checkArgumentType(name: String, arguments: Array<IOperandTerm?>): String? = checkSubstringArgs(name, arguments)
    override fun getStrValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): String {
        val str = arguments[0]!!.getStrValue(exm)
        var start = 0
        var length = -1
        if (arguments.size >= 2 && arguments[1] != null) start = arguments[1]!!.getIntValue(exm).toInt()
        if (arguments.size >= 3 && arguments[2] != null) length = arguments[2]!!.getIntValue(exm).toInt()
        return LangManager.getSubStringLang(str, start, length)
    }
}

internal class SubstringuMethod : FunctionMethod() {
    init { returnType = EType.String; argumentTypeArray = null; canRestructure = true }
    override fun checkArgumentType(name: String, arguments: Array<IOperandTerm?>): String? = checkSubstringArgs(name, arguments)
    override fun getStrValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): String {
        val str = arguments[0]!!.getStrValue(exm)
        var start = 0
        var length = -1
        if (arguments.size >= 2 && arguments[1] != null) start = arguments[1]!!.getIntValue(exm).toInt()
        if (arguments.size >= 3 && arguments[2] != null) length = arguments[2]!!.getIntValue(exm).toInt()
        if (start >= str.length || length == 0) return ""
        if (length < 0 || length > str.length) length = str.length
        if (start <= 0) {
            if (length == str.length) return str
            start = 0
        }
        if (start + length > str.length) length = str.length - start
        return str.substring(start, start + length)
    }
}

internal class StrfindMethod(private val unicode: Boolean) : FunctionMethod() {
    init { returnType = EType.Int64; argumentTypeArray = null; canRestructure = true }
    override fun checkArgumentType(name: String, arguments: Array<IOperandTerm?>): String? {
        if (arguments.size < 2) return name + "関数には少なくとも2つの引数が必要です"
        if (arguments.size > 3) return name + "関数の引数が多すぎます"
        val a0 = arguments[0] ?: return name + "関数の1番目の引数は省略できません"
        if (a0.getOperandType() != EType.String) return name + "関数の1番目の引数の型が正しくありません"
        val a1 = arguments[1] ?: return name + "関数の2番目の引数は省略できません"
        if (a1.getOperandType() != EType.String) return name + "関数の2番目の引数の型が正しくありません"
        if (arguments.size >= 3 && arguments[2] != null && arguments[2]!!.getOperandType() != EType.Int64) return name + "関数の3番目の引数の型が正しくありません"
        return null
    }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long {
        val target = arguments[0]!!.getStrValue(exm)
        val word = arguments[1]!!.getStrValue(exm)
        var uftStart = 0
        if (arguments.size >= 3 && arguments[2] != null) {
            uftStart = if (unicode) arguments[2]!!.getIntValue(exm).toInt()
            else LangManager.getUFTIndex(target, arguments[2]!!.getIntValue(exm).toInt())
        }
        if (uftStart < 0 || uftStart >= target.length) return -1
        var index = target.indexOf(word, uftStart)
        if (index > 0 && !unicode) index = LangManager.getStrlenLang(target.substring(0, index))
        return index.toLong()
    }
}

internal class StrCountMethod : FunctionMethod() {
    init { returnType = EType.Int64; argumentTypeArray = arrayOf(EType.String, EType.String); canRestructure = true }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long {
        val reg = makeRegex(arguments[1]!!.getStrValue(exm), "第2引数が正規表現として不正です：")
        return reg.findAll(arguments[0]!!.getStrValue(exm)).count().toLong()
    }
}

internal class ToStrMethod : FunctionMethod() {
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
        val i = arguments[0]!!.getIntValue(exm)
        if (arguments.size < 2 || arguments[1] == null) return i.toString()
        return try { DotNetFormat.formatLong(i, arguments[1]!!.getStrValue(exm)) } catch (e: DotNetFormat.FormatException) {
            throw CodeEE("TOSTR関数の書式指定が間違っています")
        }
    }
}

private fun parseNumericLike(str: String, readValue: Boolean): Long? {
    if (str.isEmpty()) return null
    if (str.length < LangManager.getStrlenLang(str)) return null
    val st = StringStream(str)
    if (!st.current.isDigit() && st.current != '+' && st.current != '-') return null
    else if ((st.current == '+' || st.current == '-') && !st.next.isDigit()) return null
    val ret: Long
    if (readValue) ret = LexicalAnalyzer.readInt64(st, true)
    else { if (!LexicalAnalyzer.numericCheck(st)) return null; ret = 1 }
    if (!st.eos) {
        if (st.current == '.') {
            st.shiftNext()
            while (!st.eos) {
                if (!st.current.isDigit()) return null
                st.shiftNext()
            }
        } else return null
    }
    return ret
}

internal class ToIntMethod : FunctionMethod() {
    init { returnType = EType.Int64; argumentTypeArray = arrayOf(EType.String); canRestructure = true }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long = parseNumericLike(arguments[0]!!.getStrValue(exm), true) ?: 0L
}

enum class StrFormType { Upper, Lower, Half, Full }

internal class StrChangeStyleMethod(private val strType: StrFormType = StrFormType.Upper) : FunctionMethod() {
    init { returnType = EType.String; argumentTypeArray = arrayOf(EType.String); canRestructure = true }
    override fun getStrValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): String {
        val str = arguments[0]!!.getStrValue(exm)
        if (str.isEmpty()) return ""
        return when (strType) {
            StrFormType.Upper -> str.uppercase()
            StrFormType.Lower -> str.lowercase()
            StrFormType.Half -> KanaConv.toNarrow(str)
            StrFormType.Full -> KanaConv.toWide(str)
        }
    }
}

internal class LineIsEmptyMethod : FunctionMethod() {
    init { returnType = EType.Int64; argumentTypeArray = arrayOf(); canRestructure = false }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long = if (GlobalStatic.Console!!.emptyLine) 1L else 0L
}

internal class ReplaceMethod : FunctionMethod() {
    init { returnType = EType.String; argumentTypeArray = arrayOf(EType.String, EType.String, EType.String); canRestructure = true }
    override fun getStrValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): String {
        val baseString = arguments[0]!!.getStrValue(exm)
        val reg = makeRegex(arguments[1]!!.getStrValue(exm), "第２引数が正規表現として不正です：")
        return reg.toPattern().matcher(baseString).replaceAll(DotNetRegex.convertReplacement(arguments[2]!!.getStrValue(exm)))
    }
}

internal object DotNetRegex {
    /** .NET の置換文字列 ($1, ${name}, $$) を Java 形式に変換する */
    fun convertReplacement(s: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\') { sb.append("\\\\"); i++; continue }
            if (c == '$' && i + 1 < s.length) {
                val n = s[i + 1]
                when {
                    n == '$' -> { sb.append("\\$"); i += 2; continue }
                    n.isDigit() || n == '{' -> { sb.append('$'); i++; continue }
                    n == '&' -> { sb.append("$0"); i += 2; continue }
                }
            }
            if (c == '$') { sb.append("\\$"); i++; continue }
            sb.append(c)
            i++
        }
        return sb.toString()
    }

    /** Regex.Escape 互換 */
    fun escape(s: String): String {
        val sb = StringBuilder()
        for (c in s) {
            when (c) {
                '\\', '*', '+', '?', '|', '{', '[', '(', ')', '^', '$', '.', '#', ' ' -> sb.append('\\').append(c)
                '\t' -> sb.append("\\t")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\u000C' -> sb.append("\\f")
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }
}

internal class UnicodeMethod : FunctionMethod() {
    init { returnType = EType.String; argumentTypeArray = arrayOf(EType.Int64); canRestructure = true }
    override fun getStrValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): String {
        val i = arguments[0]!!.getIntValue(exm)
        if (i < 0 || i > 0xFFFF) throw CodeEE("UNICODE関数に範囲外の値(${i})が渡されました")
        if ((i < 0x001F && i != 0x000AL && i != 0x000DL) || (i in 0x007F..0x009F)) {
            val proc = GlobalStatic.Process
            val cur = proc?.getCurrentLine
            val hex = java.lang.Long.toHexString(i).uppercase()
            if (cur != null) GlobalStatic.Console!!.printSystemLine("注意:" + cur.position?.filename + "の" + cur.position?.lineNo + "行目でUNICODE関数に制御文字に対応する値(0x" + hex + ")が渡されました")
            else ParserMediator.warn("UNICODE関数に制御文字に対応する値(0x$hex)が渡されました", proc?.scaningLine, 1, false, false, null)
            return ""
        }
        return i.toInt().toChar().toString()
    }
}

internal class UnicodeByteMethod : FunctionMethod() {
    init { returnType = EType.Int64; argumentTypeArray = arrayOf(EType.String); canRestructure = true }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long {
        val target = arguments[0]!!.getStrValue(exm)
        if (target.isEmpty()) throw CodeEE("UNICODEBYTE関数に空文字列が渡されました")
        return target.codePointAt(0).toLong()
    }
}

internal class ConvertIntMethod : FunctionMethod() {
    init { returnType = EType.String; argumentTypeArray = arrayOf(EType.Int64, EType.Int64); canRestructure = true }
    override fun getStrValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): String {
        val toBase = arguments[1]!!.getIntValue(exm)
        val v = arguments[0]!!.getIntValue(exm)
        return when (toBase) {
            2L -> java.lang.Long.toBinaryString(v)
            8L -> java.lang.Long.toOctalString(v)
            10L -> v.toString()
            16L -> java.lang.Long.toHexString(v)
            else -> throw CodeEE("CONVERT関数の第２引数は2, 8, 10, 16のいずれかでなければなりません")
        }
    }
}

internal class IsNumericMethod : FunctionMethod() {
    init { returnType = EType.Int64; argumentTypeArray = arrayOf(EType.String); canRestructure = true }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long = if (parseNumericLike(arguments[0]!!.getStrValue(exm), false) != null) 1L else 0L
}

internal class EscapeMethod : FunctionMethod() {
    init { returnType = EType.String; argumentTypeArray = arrayOf(EType.String); canRestructure = true }
    override fun getStrValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): String = DotNetRegex.escape(arguments[0]!!.getStrValue(exm))
}

internal class EncodeToUniMethod : FunctionMethod() {
    init { returnType = EType.Int64; argumentTypeArray = null; canRestructure = true }
    override fun checkArgumentType(name: String, arguments: Array<IOperandTerm?>): String? {
        if (arguments.isEmpty()) return name + "関数には少なくとも1つの引数が必要です"
        if (arguments.size > 2) return name + "関数の引数が多すぎます"
        val a0 = arguments[0] ?: return name + "関数の1番目の引数は省略できません"
        if (a0.getOperandType() != EType.String) return name + "関数の1番目の引数の型が正しくありません"
        if (arguments.size >= 2 && arguments[1] != null && arguments[1]!!.getOperandType() != EType.Int64) return name + "関数の2番目の引数の型が正しくありません"
        return null
    }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long {
        val baseStr = arguments[0]!!.getStrValue(exm)
        if (baseStr.isEmpty()) return -1
        val position = if (arguments.size > 1 && arguments[1] != null) arguments[1]!!.getIntValue(exm) else 0L
        if (position < 0) throw CodeEE("ENCOIDETOUNI関数の第２引数(${position})が負の値です")
        if (position >= baseStr.length) throw CodeEE("ENCOIDETOUNI関数の第２引数(${position})が第１引数の文字列(${baseStr})の文字数を超えています")
        val c = baseStr[position.toInt()]
        if (Character.isLowSurrogate(c)) throw CodeEE("ENCOIDETOUNI関数:無効なサロゲートペアです")
        return baseStr.codePointAt(position.toInt()).toLong()
    }
}

internal class CharAtMethod : FunctionMethod() {
    init { returnType = EType.String; argumentTypeArray = arrayOf(EType.String, EType.Int64); canRestructure = true }
    override fun getStrValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): String {
        val str = arguments[0]!!.getStrValue(exm)
        val pos = arguments[1]!!.getIntValue(exm)
        if (pos < 0 || pos >= str.length) return ""
        return str[pos.toInt()].toString()
    }
}

internal class GetLineStrMethod : FunctionMethod() {
    init { returnType = EType.String; argumentTypeArray = arrayOf(EType.String); canRestructure = true }
    override fun getStrValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): String {
        val str = arguments[0]!!.getStrValue(exm)
        if (str.isEmpty()) throw CodeEE("GETLINESTR関数の引数が空文字列です")
        return exm.console.getStBar(str)
    }
}

internal class StrFormMethod : FunctionMethod() {
    init { returnType = EType.String; argumentTypeArray = arrayOf(EType.String); hasUniqueRestructure = true; canRestructure = true }
    override fun getStrValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): String {
        val str = arguments[0]!!.getStrValue(exm)
        try {
            val wt = LexicalAnalyzer.analyseFormattedString(StringStream(str), FormStrEndWith.EoL, false)
            return StrForm.fromWordToken(wt).getString(exm)
        } catch (e: CodeEE) {
            throw CodeEE("STRFORM関数:文字列\"$str\"の展開エラー:" + e.message)
        } catch (e: Exception) {
            throw CodeEE("STRFORM関数:文字列\"$str\"の展開処理中にエラーが発生しました")
        }
    }
    override fun uniqueRestructure(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Boolean {
        arguments[0]!!.restructure(exm)
        val a0 = arguments[0]
        if (a0 !is SingleTerm && a0 !is VariableTerm) return false
        if (a0 is VariableTerm && !a0.identifier.isConst) return false
        val str = a0.getStrValue(exm)
        try {
            val wt = LexicalAnalyzer.analyseFormattedString(StringStream(str), FormStrEndWith.EoL, false)
            if (!StrForm.fromWordToken(wt).isConst) return false
        } catch (e: Exception) {
            return false
        }
        return true
    }
}

internal class JoinMethod : FunctionMethod() {
    init { returnType = EType.String; argumentTypeArray = null; hasUniqueRestructure = true; canRestructure = true }
    override fun checkArgumentType(name: String, arguments: Array<IOperandTerm?>): String? {
        if (arguments.isEmpty()) return name + "関数には少なくとも1つの引数が必要です"
        if (arguments.size > 4) return name + "関数の引数が多すぎます"
        val a0 = arguments[0] ?: return name + "関数の1番目の引数は省略できません"
        if (a0 !is VariableTerm) return name + "関数の1番目の引数が変数ではありません"
        if (!a0.identifier.isArray1D && !a0.identifier.isArray2D && !a0.identifier.isArray3D) return name + "関数の1番目の引数が配列変数ではありません"
        if (arguments.size == 1) return null
        if (arguments[1] != null && arguments[1]!!.getOperandType() != EType.String) return name + "関数の2番目の変数が文字列ではありません"
        if (arguments.size == 2) return null
        if (arguments[2] != null && arguments[2]!!.getOperandType() != EType.Int64) return name + "関数の3番目の変数が数値ではありません"
        if (arguments.size == 3) return null
        if (arguments[3] != null && arguments[3]!!.getOperandType() != EType.Int64) return name + "関数の4番目の変数が数値ではありません"
        return null
    }
    override fun getStrValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): String {
        val varTerm = arguments[0] as VariableTerm
        val delimiter = if (arguments.size >= 2 && arguments[1] != null) arguments[1]!!.getStrValue(exm) else ","
        val index1 = if (arguments.size >= 3 && arguments[2] != null) arguments[2]!!.getIntValue(exm) else 0L
        val index2 = if (arguments.size == 4 && arguments[3] != null) arguments[3]!!.getIntValue(exm) else varTerm.getLastLength() - index1
        val p = varTerm.getFixedVariableTerm(exm)
        if (index2 < 0) throw CodeEE("STRJOINの第4引数(${index2})が負の値になっています")
        p.isArrayRangeValid(index1, index1 + index2, "STRJOIN", 2L, 3L)
        return exm.vEvaluator.getJoinedStr(p, delimiter, index1, index2)
    }
    override fun uniqueRestructure(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Boolean {
        var can = (arguments[0] as VariableTerm).identifier.isConst
        for (i in 1 until arguments.size) {
            val a = arguments[i] ?: continue
            arguments[i] = a.restructure(exm)
            can = can and (arguments[i] is SingleTerm)
        }
        return can
    }
}

internal class GetConfigMethod(typeisInt: Boolean) : FunctionMethod() {
    private val funcname = if (typeisInt) "GETCONFIG" else "GETCONFIGS"
    init { returnType = if (typeisInt) EType.Int64 else EType.String; argumentTypeArray = arrayOf(EType.String); canRestructure = true }
    private fun getSingleTerm(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): SingleTerm {
        val str = arguments[0]!!.getStrValue(exm)
        if (str.isEmpty()) throw CodeEE(funcname + "関数に空文字列が渡されました")
        val errMes = arrayOfNulls<String>(1)
        val term = ConfigData.instance.getConfigValueInERB(str, errMes)
        if (errMes[0] != null || term == null) throw CodeEE(funcname + "関数:" + errMes[0])
        return term
    }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long {
        if (returnType != EType.Int64) throw ExeEE(funcname + "関数:不正な呼び出し")
        val term = getSingleTerm(exm, arguments)
        if (term.getOperandType() != EType.Int64) throw CodeEE(funcname + "関数:型が違います（GETCONFIGS関数を使用してください）")
        return term.int
    }
    override fun getStrValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): String {
        if (returnType != EType.String) throw ExeEE(funcname + "関数:不正な呼び出し")
        val term = getSingleTerm(exm, arguments)
        if (term.getOperandType() != EType.String) throw CodeEE(funcname + "関数:型が違います（GETCONFIG関数を使用してください）")
        return term.str
    }
}

internal class HtmlGetPrintedStrMethod : FunctionMethod() {
    init { returnType = EType.String; argumentTypeArray = null; canRestructure = false }
    override fun checkArgumentType(name: String, arguments: Array<IOperandTerm?>): String? {
        if (arguments.size > 1) return name + "関数の引数が多すぎます"
        if (arguments.isEmpty() || arguments[0] == null) return null
        if (arguments[0]!!.getOperandType() != EType.Int64) return name + "関数の1番目の引数の型が正しくありません"
        return null
    }
    override fun getStrValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): String {
        var lineNo = 0L
        if (arguments.isNotEmpty() && arguments[0] != null) lineNo = arguments[0]!!.getIntValue(exm)
        if (lineNo < 0) throw CodeEE("引数を0未満にできません")
        val dispLines = exm.console.getDisplayLines(lineNo) ?: return ""
        return HtmlManager.displayLine2Html(dispLines, true)
    }
}

internal class HtmlPopPrintingStrMethod : FunctionMethod() {
    init { returnType = EType.String; argumentTypeArray = arrayOf(); canRestructure = false }
    override fun getStrValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): String {
        val dispLines = exm.console.popDisplayingLines() ?: return ""
        return HtmlManager.displayLine2Html(dispLines, false)
    }
}

internal class HtmlToPlainTextMethod : FunctionMethod() {
    init { returnType = EType.String; argumentTypeArray = arrayOf(EType.String); canRestructure = false }
    override fun getStrValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): String = HtmlManager.html2PlainText(arguments[0]!!.getStrValue(exm))
}

internal class HtmlEscapeMethod : FunctionMethod() {
    init { returnType = EType.String; argumentTypeArray = arrayOf(EType.String); canRestructure = false }
    override fun getStrValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): String = HtmlManager.escape(arguments[0]!!.getStrValue(exm))
}
