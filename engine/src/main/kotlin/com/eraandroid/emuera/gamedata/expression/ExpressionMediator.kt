package com.eraandroid.emuera.gamedata.expression

import com.eraandroid.emuera.config.Config
import com.eraandroid.emuera.gamedata.variable.VariableEvaluator
import com.eraandroid.emuera.gameproc.Process
import com.eraandroid.emuera.gameproc.function.FunctionIdentifier
import com.eraandroid.emuera.gameview.EmueraConsole
import com.eraandroid.emuera.sub.CodeEE
import com.eraandroid.emuera.sub.StringStream

class ExpressionMediator(val process: Process, val vEvaluator: VariableEvaluator, val console: EmueraConsole) {
    private var forceHiragana = false
    private var forceKatakana = false
    private var halftoFull = false

    fun forceKana(flag: Long) {
        if (flag < 0 || flag > 3) throw CodeEE("命令FORCEKANAの引数が指定可能な範囲(0～3)を超えています")
        forceKatakana = flag == 1L
        forceHiragana = flag > 1
        halftoFull = flag == 3L
    }

    fun forceKana(): Boolean = forceHiragana || forceKatakana || halftoFull

    fun outputToConsole(str: String, func: FunctionIdentifier) {
        if (func.isPrintSingle()) console.printSingleLine(str, false)
        else {
            console.print(str)
            if (func.isNewLine() || func.isWaitInput()) {
                console.newLine()
                if (func.isWaitInput()) console.readAnyKey()
            }
        }
        console.useSetColorStyle = true
    }

    fun convertStringType(str: String): String {
        if (!(forceHiragana || forceKatakana || halftoFull)) return str
        if (forceKatakana) return KanaConv.toKatakana(str)
        if (forceHiragana) return if (halftoFull) KanaConv.toHiragana(KanaConv.toWide(str)) else KanaConv.toHiragana(str)
        return str
    }

    fun checkEscape(str: String): String {
        val st = StringStream(str)
        val buffer = StringBuilder()
        while (!st.eos) {
            if (st.current == '\\') {
                st.shiftNext()
                when (st.current) {
                    '\\' -> buffer.append("\\\\")
                    '{', '}', '%', '@' -> { buffer.append('\\'); buffer.append(st.current) }
                    else -> { buffer.append("\\\\"); buffer.append(st.current) }
                }
                st.shiftNext()
                continue
            }
            buffer.append(st.current)
            st.shiftNext()
        }
        return buffer.toString()
    }

    fun createBar(v: Long, max: Long, length: Long): String {
        if (max <= 0) throw CodeEE("BARの最大値が正の値ではありません")
        if (length <= 0) throw CodeEE("BARの長さが正の値ではありません")
        if (length >= 100) throw CodeEE("BARが長すぎます")
        val builder = StringBuilder()
        builder.append('[')
        var count = (v * length / max).toInt()
        if (count < 0) count = 0
        if (count > length) count = length.toInt()
        repeat(count) { builder.append(Config.BarChar1) }
        repeat(length.toInt() - count) { builder.append(Config.BarChar2) }
        builder.append(']')
        return builder.toString()
    }
}

/** VB Strings.StrConv (Katakana / Hiragana / Wide) の簡易実装 */
object KanaConv {
    private const val HALF_KANA = "｡｢｣､･ｦｧｨｩｪｫｬｭｮｯｰｱｲｳｴｵｶｷｸｹｺｻｼｽｾｿﾀﾁﾂﾃﾄﾅﾆﾇﾈﾉﾊﾋﾌﾍﾎﾏﾐﾑﾒﾓﾔﾕﾖﾗﾘﾙﾚﾛﾜﾝﾞﾟ"
    private const val FULL_KANA = "。「」、・ヲァィゥェォャュョッーアイウエオカキクケコサシスセソタチツテトナニヌネノハヒフヘホマミムメモヤユヨラリルレロワン゛゜"

    fun toKatakana(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) sb.append(if (c in 'ぁ'..'ゖ') c + 0x60 else c)
        return sb.toString()
    }

    fun toHiragana(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) sb.append(if (c in 'ァ'..'ヶ') c - 0x60 else c)
        return sb.toString()
    }

    fun toWide(s: String): String {
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c == ' ' -> sb.append('　')
                c in '!'..'~' -> sb.append(c + 0xFEE0)
                else -> {
                    val k = HALF_KANA.indexOf(c)
                    if (k >= 0) {
                        var f = FULL_KANA[k]
                        val next = if (i + 1 < s.length) s[i + 1] else '\u0000'
                        if (next == 'ﾞ' && (f in "カキクケコサシスセソタチツテトハヒフヘホ" || f == 'ウ')) {
                            f = if (f == 'ウ') 'ヴ' else f + 1; i++
                        } else if (next == 'ﾟ' && f in "ハヒフヘホ") {
                            f += 2; i++
                        }
                        sb.append(f)
                    } else sb.append(c)
                }
            }
            i++
        }
        return sb.toString()
    }

    fun toNarrow(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            when {
                c == '\u3000' -> sb.append(' ')
                c in '\uFF01'..'\uFF5E' -> sb.append(c - 0xFEE0)
                else -> {
                    val k = FULL_KANA.indexOf(c)
                    if (k >= 0) { sb.append(HALF_KANA[k]); continue }
                    val dak = "ガギグゲゴザジズゼゾダヂヅデドバビブベボ".indexOf(c)
                    if (dak >= 0) { sb.append(HALF_KANA[FULL_KANA.indexOf(c - 1)]).append('ﾞ'); continue }
                    val han = "パピプペポ".indexOf(c)
                    if (han >= 0) { sb.append(HALF_KANA[FULL_KANA.indexOf(c - 2)]).append('ﾟ'); continue }
                    if (c == 'ヴ') { sb.append("ｳﾞ"); continue }
                    sb.append(c)
                }
            }
        }
        return sb.toString()
    }
}
