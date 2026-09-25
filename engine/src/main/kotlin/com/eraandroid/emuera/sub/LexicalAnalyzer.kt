package com.eraandroid.emuera.sub

import com.eraandroid.emuera.GlobalStatic
import com.eraandroid.emuera.Program
import com.eraandroid.emuera.config.Config
import com.eraandroid.emuera.config.ConfigCode
import com.eraandroid.emuera.gamedata.ParserMediator
import com.eraandroid.emuera.gamedata.expression.OperatorCode

enum class LexEndWith {
    None, EoL, Operator, Question, Percent, RightCurlyBrace, Comma, GreaterThan,
}

enum class FormStrEndWith {
    None, EoL, DoubleQuotation, Sharp, YenAt, Comma, LeftParenthesis_Bracket_Comma_Semicolon,
}

enum class StrEndWith {
    None, EoL, SingleQuotation, DoubleQuotation, Comma, LeftParenthesis_Bracket_Comma_Semicolon,
}

object LexAnalyzeFlag {
    const val None = 0
    const val AnalyzePrintV = 1
    const val AllowAssignment = 2
    const val AllowSingleQuotationStr = 4
}

/** Lexicalといいつつ構文解析を含む */
object LexicalAnalyzer {
    private const val MAX_EXPAND_MACRO = 100
    private fun isHex(c: Char) = c in 'a'..'f' || c in 'A'..'F'
    private fun isDigit(c: Char) = Character.isDigit(c)

    @JvmField var useMacro = true

    fun readInt64(st: StringStream, retZero: Boolean): Long {
        var significand: Long
        var expBase = 0
        var exponent = 0
        val stStartPos = st.currentPosition
        var fromBase = 10
        if (st.current == '0') {
            val c = st.next
            if (c == 'x' || c == 'X') { fromBase = 16; st.shiftNext(); st.shiftNext() }
            else if (c == 'b' || c == 'B') { fromBase = 2; st.shiftNext(); st.shiftNext() }
        }
        if (retZero && st.current != '+' && st.current != '-' && !isDigit(st.current)) {
            if (fromBase != 16) return 0
            else if (!isHex(st.current)) return 0
        }
        significand = readDigits(st, fromBase)
        if (st.current == 'p' || st.current == 'P') expBase = 2
        else if (st.current == 'e' || st.current == 'E') expBase = 10
        if (expBase != 0) {
            st.shiftNext()
            exponent = readDigits(st, fromBase).toInt()
        }
        val stEndPos = st.currentPosition
        if (expBase != 0 && exponent != 0) {
            val d = significand * Math.pow(expBase.toDouble(), exponent.toDouble())
            if (d.isNaN() || d.isInfinite() || d > Long.MAX_VALUE || d < Long.MIN_VALUE)
                throw CodeEE("\"" + st.substring(stStartPos, stEndPos) + "\"は64ビット符号付整数の範囲を超えています")
            significand = d.toLongDotNet()
        }
        return significand
    }

    private fun readDigits(st: StringStream, fromBase: Int): Long {
        val start = st.currentPosition
        var c = st.current
        if (c == '-' || c == '+') st.shiftNext()
        when (fromBase) {
            10 -> while (!st.eos) { c = st.current; if (isDigit(c)) { st.shiftNext(); continue }; break }
            16 -> while (!st.eos) { c = st.current; if (isDigit(c) || isHex(c)) { st.shiftNext(); continue }; break }
            2 -> while (!st.eos) {
                c = st.current
                if (isDigit(c)) {
                    if (c != '0' && c != '1') throw CodeEE("二進法表記の中で使用できない文字が使われています")
                    st.shiftNext(); continue
                }
                break
            }
        }
        val strInt = st.substring(start, st.currentPosition - start)
        if (strInt.isEmpty()) throw CodeEE("数値として認識できる文字が必要です")
        var s = strInt
        if (s.startsWith("+")) s = s.substring(1)
        try {
            if (fromBase == 10) return s.toLong()
            // C# Convert.ToInt64(x, 16/2) accepts two's complement for 64 bits
            if (s.startsWith("-")) throw NumberFormatException()
            val u = java.lang.Long.parseUnsignedLong(s, fromBase)
            return u
        } catch (e: NumberFormatException) {
            if (s.isNotEmpty() && s.trimStart('-').all { if (fromBase == 16) isDigit(it) || isHex(it) else isDigit(it) })
                throw CodeEE("\"$strInt\"は64ビット符号付き整数の範囲を超えています")
            throw CodeEE("\"$strInt\"は整数値に変換できません")
        }
    }

    fun numericCheck(st: StringStream): Boolean {
        var significand: Long
        var expBase = 0
        var exponent = 0
        val stStartPos = st.currentPosition
        var fromBase = 10
        if (st.current == '0') {
            val c = st.next
            if (c == 'x' || c == 'X') { fromBase = 16; st.shiftNext(); st.shiftNext() }
            else if (c == 'b' || c == 'B') { fromBase = 2; st.shiftNext(); st.shiftNext() }
        }
        if (st.current != '+' && st.current != '-' && !isDigit(st.current)) {
            if (fromBase != 16) return false
            else if (!isHex(st.current)) return false
        }
        significand = readDigits(st, fromBase)
        if (st.current == 'p' || st.current == 'P') expBase = 2
        else if (st.current == 'e' || st.current == 'E') expBase = 10
        if (expBase != 0) {
            st.shiftNext()
            if (st.eos || !isDigit(st.current)) return false
            exponent = readDigits(st, fromBase).toInt()
        }
        val stEndPos = st.currentPosition
        if (expBase != 0 && exponent != 0) {
            val d = significand * Math.pow(expBase.toDouble(), exponent.toDouble())
            if (d.isNaN() || d.isInfinite() || d > Long.MAX_VALUE || d < Long.MIN_VALUE)
                throw CodeEE("\"" + st.substring(stStartPos, stEndPos) + "\"は64ビット符号付整数の範囲を超えています")
        }
        return true
    }

    /** TIMES第二引数のみが使用する。 */
    fun readDouble(st: StringStream): Double {
        val start = st.currentPosition
        if (st.current == '-' || st.current == '+') st.shiftNext()
        while (!st.eos) { val c = st.current; if (isDigit(c) || c == '.') { st.shiftNext(); continue }; break }
        if (st.current == 'e' || st.current == 'E') {
            st.shiftNext()
            if (st.current == '-') st.shiftNext()
            while (!st.eos) { val c = st.current; if (isDigit(c) || c == '.') { st.shiftNext(); continue }; break }
        }
        return st.substring(start, st.currentPosition - start).toDouble()
    }

    /** 行頭の単語の取得 */
    fun readFirstIdentifierWord(st: StringStream): IdentifierWord {
        val str = readSingleIdentifier(st)
        if (str.isEmpty()) throw CodeEE("不正な文字で行が始まっています")
        return IdentifierWord(str)
    }

    /** 単語の取得。マクロ展開あり。関数型マクロ展開なし */
    fun readSingleIdentifierWord(st: StringStream): IdentifierWord? {
        val str = readSingleIdentifier(st)
        if (str.isEmpty()) return null
        if (useMacro) {
            var i = 0
            while (true) {
                val macro = GlobalStatic.IdentifierDictionary!!.getMacro(str)
                i++
                if (i > MAX_EXPAND_MACRO)
                    throw CodeEE("マクロの展開数が1文あたりの上限値${MAX_EXPAND_MACRO}を超えました(自己参照・循環参照のおそれ)")
                if (macro == null) break
                // 原作の挙動をそのまま維持 (単語マクロであっても例外)
                throw CodeEE("マクロ" + macro.keyword + "はこの文脈では使用できません(1単語に置き換えるマクロのみが使用できます)")
            }
        }
        return IdentifierWord(str)
    }

    /** 単語を文字列で取得。マクロ適用なし */
    fun readSingleIdentifier(st: StringStream): String {
        val start = st.currentPosition
        loop@ while (!st.eos) {
            when (st.current) {
                ' ', '\t', '+', '-', '*', '/', '%', '=', '!', '<', '>', '|', '&', '^', '~', '?', '#',
                ')', '}', ']', ',', ':', '(', '{', '[', '$', '\\', '\'', '"', '@', '.', ';' -> break@loop
                '　' -> {
                    if (!Config.SystemAllowFullSpace)
                        throw CodeEE("予期しない全角スペースを発見しました(この警告はシステムオプション「" + Config.getConfigName(ConfigCode.SystemAllowFullSpace) + "」により無視できます)")
                    break@loop
                }
            }
            st.shiftNext()
        }
        return st.substring(start, st.currentPosition - start)
    }

    /** endWithが見つかるまで読み込む。エスケープあり。 */
    fun readString(st: StringStream, endWith: StrEndWith): String {
        val buffer = StringBuilder(100)
        while (true) {
            when (st.current) {
                '\u0000' -> return buffer.toString()
                '"' -> if (endWith == StrEndWith.DoubleQuotation) return buffer.toString()
                '\'' -> if (endWith == StrEndWith.SingleQuotation) return buffer.toString()
                ',' -> if (endWith == StrEndWith.Comma || endWith == StrEndWith.LeftParenthesis_Bracket_Comma_Semicolon) return buffer.toString()
                '(', '[', ';' -> if (endWith == StrEndWith.LeftParenthesis_Bracket_Comma_Semicolon) return buffer.toString()
                '\\' -> {
                    st.shiftNext()
                    when (st.current) {
                        StringStream.EndOfString -> throw CodeEE("エスケープ文字\\の後に文字がありません")
                        '\n' -> {}
                        's' -> buffer.append(' ')
                        'S' -> buffer.append('　')
                        't' -> buffer.append('\t')
                        'n' -> buffer.append('\n')
                        else -> buffer.append(st.current)
                    }
                    st.shiftNext()
                    continue
                }
            }
            buffer.append(st.current)
            st.shiftNext()
        }
    }

    fun readOperator(st: StringStream, allowAssignment: Boolean): OperatorCode {
        val cur = st.current
        st.shiftNext()
        val next = st.current
        when (cur) {
            '+' -> { if (next == '+') { st.shiftNext(); return OperatorCode.Increment }; return OperatorCode.Plus }
            '-' -> { if (next == '-') { st.shiftNext(); return OperatorCode.Decrement }; return OperatorCode.Minus }
            '*' -> return OperatorCode.Mult
            '/' -> return OperatorCode.Div
            '%' -> return OperatorCode.Mod
            '=' -> {
                if (next == '=') { st.shiftNext(); return OperatorCode.Equal }
                if (allowAssignment) return OperatorCode.Assignment
                throw CodeEE("予期しない代入演算子'='を発見しました(等価比較には'=='を使用してください)")
            }
            '!' -> {
                if (next == '=') { st.shiftNext(); return OperatorCode.NotEqual }
                if (next == '&') { st.shiftNext(); return OperatorCode.Nand }
                if (next == '|') { st.shiftNext(); return OperatorCode.Nor }
                return OperatorCode.Not
            }
            '<' -> {
                if (next == '=') { st.shiftNext(); return OperatorCode.LessEqual }
                if (next == '<') { st.shiftNext(); return OperatorCode.LeftShift }
                return OperatorCode.Less
            }
            '>' -> {
                if (next == '=') { st.shiftNext(); return OperatorCode.GreaterEqual }
                if (next == '>') { st.shiftNext(); return OperatorCode.RightShift }
                return OperatorCode.Greater
            }
            '|' -> { if (next == '|') { st.shiftNext(); return OperatorCode.Or }; return OperatorCode.BitOr }
            '&' -> { if (next == '&') { st.shiftNext(); return OperatorCode.And }; return OperatorCode.BitAnd }
            '^' -> { if (next == '^') { st.shiftNext(); return OperatorCode.Xor }; return OperatorCode.BitXor }
            '~' -> return OperatorCode.BitNot
            '?' -> return OperatorCode.Ternary_a
            '#' -> return OperatorCode.Ternary_b
        }
        throw CodeEE("'$cur'は演算子として認識できません")
    }

    fun readAssignmentOperator(st: StringStream): OperatorCode {
        var ret = OperatorCode.NULL
        val cur = st.current
        st.shiftNext()
        val next = st.current
        when (cur) {
            '+' -> if (next == '+') ret = OperatorCode.Increment else if (next == '=') ret = OperatorCode.Plus
            '-' -> if (next == '-') ret = OperatorCode.Decrement else if (next == '=') ret = OperatorCode.Minus
            '*' -> if (next == '=') ret = OperatorCode.Mult
            '/' -> if (next == '=') ret = OperatorCode.Div
            '%' -> if (next == '=') ret = OperatorCode.Mod
            '=' -> { if (next == '=') ret = OperatorCode.Equal else return OperatorCode.Assignment }
            '\'' -> { if (next == '=') ret = OperatorCode.AssignmentStr else throw CodeEE("\"'\"は代入演算子として認識できません") }
            '<' -> if (next == '<') {
                st.shiftNext()
                if (st.current == '=') ret = OperatorCode.LeftShift
                else throw CodeEE("'<'は代入演算子として認識できません")
            }
            '>' -> if (next == '>') {
                st.shiftNext()
                if (st.current == '=') ret = OperatorCode.RightShift
                else throw CodeEE("'>'は代入演算子として認識できません")
            }
            '|' -> if (next == '=') ret = OperatorCode.BitOr
            '&' -> if (next == '=') ret = OperatorCode.BitAnd
            '^' -> if (next == '=') ret = OperatorCode.BitXor
        }
        if (ret == OperatorCode.NULL) throw CodeEE("'$cur'は代入演算子として認識できません")
        st.shiftNext()
        return ret
    }

    /** Consoleの文字表示用。 */
    fun skipAllSpace(st: StringStream): Int {
        var count = 0
        while (true) {
            when (st.current) {
                ' ', '\t', '　' -> { count++; st.shiftNext() }
                else -> return count
            }
        }
    }

    fun isWhiteSpace(c: Char): Boolean = c == ' ' || c == '\t' || c == '　'

    /** 字句解析・構文解析用。ホワイトスペースの他、コメントも飛ばす。 */
    fun skipWhiteSpace(st: StringStream): Int {
        var count = 0
        while (true) {
            when (st.current) {
                ' ', '\t' -> { count++; st.shiftNext() }
                '　' -> {
                    if (!Config.SystemAllowFullSpace) return count
                    count++; st.shiftNext()
                }
                ';' -> {
                    if (st.currentEqualTo(";#;") && Program.DebugMode) { st.jump(3); continue }
                    else if (st.currentEqualTo(";!;")) { st.jump(3); continue }
                    st.seekEnd(0)
                    return count
                }
                else -> return count
            }
        }
    }

    /** 文字列直前の半角スペースを飛ばす。 */
    fun skipHalfSpace(st: StringStream): Int {
        var count = 0
        while (st.current == ' ') { count++; st.shiftNext() }
        return count
    }

    /**
     * 解析できるものは関数宣言や式のみ。
     * return時にはendWithの文字がCurrentになっているはず。
     */
    fun analyse(st: StringStream, endWith: LexEndWith, flag: Int): WordCollection {
        val ret = WordCollection()
        var nestBracketS = 0
        var nestBracketL = 0
        loop@ while (true) {
            val c = st.current
            when (c) {
                '\n', '\u0000' -> break@loop
                ' ', '\t' -> { st.shiftNext(); continue@loop }
                '　' -> {
                    if (!Config.SystemAllowFullSpace)
                        throw CodeEE("字句解析中に予期しない全角スペースを発見しました(この警告はシステムオプション「" + Config.getConfigName(ConfigCode.SystemAllowFullSpace) + "」により無視できます)")
                    st.shiftNext(); continue@loop
                }
                in '0'..'9' -> ret.add(LiteralIntegerWord(readInt64(st, false)))
                '>', '+', '-', '*', '/', '%', '=', '!', '<', '|', '&', '^', '~', '?', '#' -> {
                    if (c == '>' && endWith == LexEndWith.GreaterThan) break@loop
                    if (nestBracketS == 0 && nestBracketL == 0) {
                        if (endWith == LexEndWith.Operator) break@loop
                        else if (endWith == LexEndWith.Percent && st.current == '%') break@loop
                        else if (endWith == LexEndWith.Question && st.current == '?') break@loop
                    }
                    ret.add(OperatorWord(readOperator(st, (flag and LexAnalyzeFlag.AllowAssignment) == LexAnalyzeFlag.AllowAssignment)))
                }
                ')' -> { ret.add(SymbolWord(')')); nestBracketS--; st.shiftNext(); continue@loop }
                ']' -> { ret.add(SymbolWord(']')); nestBracketL--; st.shiftNext(); continue@loop }
                '(' -> { ret.add(SymbolWord('(')); nestBracketS++; st.shiftNext(); continue@loop }
                '[' -> {
                    if (st.next == '[') {
                        if (ParserMediator.RenameDic == null)
                            throw CodeEE("字句解析中に予期しない文字\"[[\"を発見しました")
                        val start = st.currentPosition
                        val find = st.find("]]")
                        if (find <= 2) {
                            if (find == 2) throw CodeEE("空の[[]]です")
                            else throw CodeEE("対応する\"]]\"のない\"[[\"です")
                        }
                        val key = st.substring(start, find + 2)
                        throw CodeEE("字句解析中に置換(rename)できない符号${key}を発見しました")
                    }
                    ret.add(SymbolWord('[')); nestBracketL++; st.shiftNext(); continue@loop
                }
                ':' -> { ret.add(SymbolWord(':')); st.shiftNext(); continue@loop }
                ',' -> {
                    if (endWith == LexEndWith.Comma && nestBracketS == 0) break@loop
                    ret.add(SymbolWord(',')); st.shiftNext(); continue@loop
                }
                '\'' -> {
                    if ((flag and LexAnalyzeFlag.AllowSingleQuotationStr) == LexAnalyzeFlag.AllowSingleQuotationStr) {
                        st.shiftNext()
                        ret.add(LiteralStringWord(readString(st, StrEndWith.SingleQuotation)))
                        if (st.current != '\'') throw CodeEE("'が閉じられていません")
                        st.shiftNext()
                        continue@loop
                    }
                    if ((flag and LexAnalyzeFlag.AnalyzePrintV) != LexAnalyzeFlag.AnalyzePrintV) {
                        if (endWith == LexEndWith.Operator && nestBracketS == 0 && nestBracketL == 0 && st.next == '=')
                            break@loop
                        throw CodeEE("字句解析中に予期しない文字'" + st.current + "'を発見しました")
                    }
                    st.shiftNext()
                    ret.add(LiteralStringWord(readString(st, StrEndWith.Comma)))
                    if (st.current == ',') {
                        if (endWith == LexEndWith.Comma && nestBracketS == 0) break@loop
                        ret.add(SymbolWord(',')); st.shiftNext(); continue@loop
                    }
                    break@loop
                }
                '}' -> {
                    if (endWith == LexEndWith.RightCurlyBrace) break@loop
                    throw CodeEE("字句解析中に予期しない文字'" + st.current + "'を発見しました")
                }
                '"' -> {
                    st.shiftNext()
                    ret.add(LiteralStringWord(readString(st, StrEndWith.DoubleQuotation)))
                    if (st.current != '"') throw CodeEE("\"が閉じられていません")
                    st.shiftNext()
                }
                '@' -> {
                    if (st.next != '"') {
                        ret.add(SymbolWord('@')); st.shiftNext(); continue@loop
                    }
                    st.shiftNext(); st.shiftNext()
                    ret.add(analyseFormattedString(st, FormStrEndWith.DoubleQuotation, false))
                    if (st.current != '"') throw CodeEE("\"が閉じられていません")
                    st.shiftNext()
                }
                '.' -> { ret.add(SymbolWord('.')); st.shiftNext(); continue@loop }
                '\\' -> {
                    if (st.next != '@') throw CodeEE("字句解析中に予期しない文字'" + st.current + "'を発見しました")
                    st.jump(2)
                    ret.add(StrFormWord(arrayOf("", ""), arrayOf(analyseYenAt(st))))
                }
                '{', '$' -> throw CodeEE("字句解析中に予期しない文字'" + st.current + "'を発見しました")
                ';' -> {
                    if (st.currentEqualTo(";#;") && Program.DebugMode) { st.jump(3); continue@loop }
                    else if (st.currentEqualTo(";!;")) { st.jump(3); continue@loop }
                    st.seekEnd(0)
                    break@loop
                }
                else -> ret.add(IdentifierWord(readSingleIdentifier(st)))
            }
        }
        if (nestBracketS != 0 || nestBracketL != 0) {
            if (nestBracketS < 0) throw CodeEE("字句解析中に対応する'('のない')'を発見しました")
            else if (nestBracketS > 0) throw CodeEE("字句解析中に対応する')'のない'('を発見しました")
            if (nestBracketL < 0) throw CodeEE("字句解析中に対応する'['のない']'を発見しました")
            else if (nestBracketL > 0) throw CodeEE("字句解析中に対応する']'のない'['を発見しました")
        }
        if (useMacro) return expandMacro(ret)
        return ret
    }

    private fun expandMacro(wcIn: WordCollection): WordCollection {
        var wc = wcIn
        wc.pointer = 0
        var count = 0
        while (!wc.eol) {
            val word = wc.current as? IdentifierWord
            if (word == null) { wc.shiftNext(); continue }
            val macro = GlobalStatic.IdentifierDictionary?.getMacro(word.code)
            if (macro == null) { wc.shiftNext(); continue }
            count++
            if (count > MAX_EXPAND_MACRO)
                throw CodeEE("マクロの展開数が1文あたりの上限${MAX_EXPAND_MACRO}を超えました(自己参照・循環参照のおそれ)")
            if (!macro.hasArguments) {
                wc.remove()
                wc.insertRange(macro.statement)
                continue
            }
            wc = expandFunctionlikeMacro(macro, wc)
        }
        wc.pointer = 0
        return wc
    }

    private fun expandFunctionlikeMacro(macro: com.eraandroid.emuera.gamedata.DefineMacro, wc: WordCollection): WordCollection {
        val macroStart = wc.pointer
        wc.shiftNext()
        var symbol = wc.current as? SymbolWord
        if (symbol == null || symbol.type != '(')
            throw CodeEE("関数形式のマクロ" + macro.keyword + "に引数がありません")
        val macroWC = macro.statement.clone()
        val args = arrayOfNulls<WordCollection>(macro.argCount)
        run exitfor@{
            for (i in 0 until macro.argCount) {
                var macroNestBracketS = 0
                val a = WordCollection()
                args[i] = a
                exitwhile@ while (true) {
                    wc.shiftNext()
                    if (wc.eol) throw CodeEE("関数形式のマクロ" + macro.keyword + "の用法が正しくありません")
                    symbol = wc.current as? SymbolWord
                    if (symbol == null) { a.add(wc.current); continue }
                    when (symbol!!.type) {
                        '(' -> macroNestBracketS++
                        ')' -> {
                            if (macroNestBracketS > 0) macroNestBracketS--
                            else {
                                if (i != macro.argCount - 1)
                                    throw CodeEE("関数形式のマクロ" + macro.keyword + "の引数の数が正しくありません")
                                return@exitfor
                            }
                        }
                        ',' -> if (macroNestBracketS == 0) break@exitwhile
                    }
                    a.add(wc.current)
                }
                if (a.collection.isEmpty())
                    throw CodeEE("関数形式のマクロ" + macro.keyword + "の引数を省略することはできません")
            }
        }
        symbol = wc.current as? SymbolWord
        if (symbol == null || symbol!!.type != ')')
            throw CodeEE("関数形式のマクロ" + macro.keyword + "の用法が正しくありません")
        val macroLength = wc.pointer - macroStart + 1
        wc.pointer = macroStart
        repeat(macroLength) { wc.collection.removeAt(macroStart) }
        while (!macroWC.eol) {
            val w = macroWC.current as? MacroWord
            if (w == null) { macroWC.shiftNext(); continue }
            macroWC.remove()
            macroWC.insertRange(args[w.number]!!)
            macroWC.pointer += args[w.number]!!.collection.size
        }
        wc.insertRange(macroWC)
        wc.pointer = macroStart
        return wc
    }

    /** @"などの直後からの開始 */
    fun analyseFormattedString(st: StringStream, endWith: FormStrEndWith, trim: Boolean): StrFormWord {
        val strs = ArrayList<String>()
        val swts = ArrayList<SubWord>()
        val buffer = StringBuilder(100)
        loop@ while (true) {
            var cur = st.current
            when (cur) {
                '\n', '\u0000' -> break@loop
                '"' -> { if (endWith == FormStrEndWith.DoubleQuotation) break@loop; buffer.append(cur) }
                '#' -> { if (endWith == FormStrEndWith.Sharp) break@loop; buffer.append(cur) }
                ',' -> {
                    if (endWith == FormStrEndWith.Comma || endWith == FormStrEndWith.LeftParenthesis_Bracket_Comma_Semicolon) break@loop
                    buffer.append(cur)
                }
                '(', '[', ';' -> {
                    if (endWith == FormStrEndWith.LeftParenthesis_Bracket_Comma_Semicolon) break@loop
                    buffer.append(cur)
                }
                '%' -> {
                    strs.add(buffer.toString()); buffer.setLength(0)
                    st.shiftNext()
                    swts.add(PercentSubWord(analyse(st, LexEndWith.Percent, LexAnalyzeFlag.None)))
                    if (st.current != '%') throw CodeEE("'%'が使われましたが対応する'%'が見つかりません")
                }
                '{' -> {
                    strs.add(buffer.toString()); buffer.setLength(0)
                    st.shiftNext()
                    swts.add(CurlyBraceSubWord(analyse(st, LexEndWith.RightCurlyBrace, LexAnalyzeFlag.None)))
                    if (st.current != '}') throw CodeEE("'{'が使われましたが対応する'}'が見つかりません")
                }
                '*', '+', '=', '/', '$' -> {
                    if (!Config.SystemIgnoreTripleSymbol && st.tripleSymbol()) {
                        strs.add(buffer.toString()); buffer.setLength(0)
                        st.jump(3)
                        swts.add(TripleSymbolSubWord(cur))
                        continue@loop
                    } else buffer.append(cur)
                }
                '\\' -> {
                    st.shiftNext()
                    cur = st.current
                    when (cur) {
                        '\u0000' -> throw CodeEE("エスケープ文字\\の後に文字がありません")
                        '\n' -> {}
                        's' -> buffer.append(' ')
                        'S' -> buffer.append('　')
                        't' -> buffer.append('\t')
                        'n' -> buffer.append('\n')
                        '@' -> {
                            if (endWith == FormStrEndWith.YenAt || endWith == FormStrEndWith.Sharp) break@loop
                            strs.add(buffer.toString()); buffer.setLength(0)
                            st.shiftNext()
                            swts.add(analyseYenAt(st))
                            continue@loop
                        }
                        else -> { buffer.append(cur); st.shiftNext(); continue@loop }
                    }
                }
                else -> buffer.append(cur)
            }
            st.shiftNext()
        }
        strs.add(buffer.toString())
        val retStr = strs.toTypedArray()
        if (trim && retStr.isNotEmpty()) {
            retStr[0] = retStr[0].trimStart(' ', '\t')
            retStr[retStr.size - 1] = retStr[retStr.size - 1].trimEnd(' ', '\t')
        }
        return StrFormWord(retStr, swts.toTypedArray())
    }

    /** \@直後からの開始、\@の直後がCurrentになる */
    fun analyseYenAt(st: StringStream): YenAtSubWord {
        val w = analyse(st, LexEndWith.Question, LexAnalyzeFlag.None)
        if (st.current != '?') throw CodeEE("'\\@'が使われましたが対応する'?'が見つかりません")
        st.shiftNext()
        val left = analyseFormattedString(st, FormStrEndWith.Sharp, true)
        if (st.current != '#') {
            if (st.current != '@') throw CodeEE("'\\@','?'が使われましたが対応する'#'が見つかりません")
            st.shiftNext()
            ParserMediator.warn("'\\@','?'が使われましたが対応する'#'が見つかりません", GlobalStatic.Process?.getScaningLine(), 1, false, false)
            return YenAtSubWord(w, left, null)
        }
        st.shiftNext()
        val right = analyseFormattedString(st, FormStrEndWith.YenAt, true)
        if (st.current != '@') throw CodeEE("'\\@','?','#'が使われましたが対応する'\\@'が見つかりません")
        st.shiftNext()
        return YenAtSubWord(w, left, right)
    }
}
