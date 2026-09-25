package com.eraandroid.emuera.sub

import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** .NET の Int64.ToString(format) 互換 (よく使われる範囲のみ) */
object DotNetFormat {
    class FormatException : RuntimeException()

    fun formatLong(value: Long, format: String?): String {
        if (format.isNullOrEmpty()) return value.toString()
        val m = Regex("^([A-Za-z])(\\d{0,2})$").matchEntire(format)
        if (m != null) {
            val c = m.groupValues[1][0]
            val p = m.groupValues[2].toIntOrNull()
            return when (c) {
                'D', 'd' -> {
                    val s = kotlin.math.abs(value).toString().let { if (value == Long.MIN_VALUE) it.removePrefix("-") else it }
                    (if (value < 0) "-" else "") + s.padStart(p ?: 0, '0')
                }
                'N', 'n' -> group(BigDecimal(value), p ?: 2)
                'F', 'f' -> BigDecimal(value).setScale(p ?: 2).toPlainString()
                'X', 'x' -> {
                    val s = java.lang.Long.toHexString(value).padStart(p ?: 0, '0')
                    if (c == 'X') s.uppercase() else s
                }
                'G', 'g' -> value.toString()
                'E', 'e' -> String.format(Locale.ROOT, "%." + (p ?: 6) + (if (c == 'E') "E" else "e"), value.toDouble())
                    .replace(Regex("([eE])([+-])(\\d)$"), "$1$2" + "00$3").replace(Regex("([eE])([+-])(\\d\\d)$"), "$1$20$3")
                'C', 'c' -> "¥" + group(BigDecimal(value), p ?: 0)
                'P', 'p' -> group(BigDecimal(value).multiply(BigDecimal(100)), p ?: 2) + " %"
                else -> throw FormatException()
            }
        }
        return custom(value, format)
    }

    private fun group(v: BigDecimal, decimals: Int): String {
        val pattern = "#,##0" + if (decimals > 0) "." + "0".repeat(decimals) else ""
        return DecimalFormat(pattern, DecimalFormatSymbols(Locale.US)).format(v)
    }

    private fun custom(value: Long, format: String): String {
        // セクション区切り ; (正;負;ゼロ)
        val sections = splitSections(format)
        var v = value
        var fmt = sections[0]
        if (sections.size >= 2 && value < 0) { fmt = sections[1]; v = -value }
        if (sections.size >= 3 && value == 0L) fmt = sections[2]
        // リテラル部分 ('...', "...", \x) を退避し DecimalFormat 用に変換
        val sb = StringBuilder()
        var i = 0
        var hasDigit = false
        while (i < fmt.length) {
            val c = fmt[i]
            when {
                c == '\'' || c == '"' -> {
                    val end = fmt.indexOf(c, i + 1).let { if (it < 0) fmt.length else it }
                    sb.append('\'').append(fmt.substring(i + 1, end).replace("'", "''")).append('\'')
                    i = end + 1; continue
                }
                c == '\\' && i + 1 < fmt.length -> { sb.append('\'').append(fmt[i + 1].toString().replace("'", "''")).append('\''); i += 2; continue }
                c == '0' || c == '#' -> { hasDigit = true; sb.append(c) }
                c == ',' || c == '.' || c == '%' -> sb.append(c)
                c == '-' -> sb.append("'-'")
                else -> sb.append('\'').append(if (c == '\'') "''" else c.toString()).append('\'')
            }
            i++
        }
        if (!hasDigit) return fmt.replace(Regex("['\"\\\\]"), "")
        return try {
            val df = DecimalFormat(sb.toString(), DecimalFormatSymbols(Locale.US))
            if (sections.size >= 2 && value < 0) df.negativePrefix = ""
            df.format(v)
        } catch (e: IllegalArgumentException) {
            throw FormatException()
        }
    }

    private fun splitSections(format: String): List<String> {
        val list = ArrayList<String>()
        val cur = StringBuilder()
        var q: Char? = null
        var i = 0
        while (i < format.length) {
            val c = format[i]
            if (q != null) { cur.append(c); if (c == q) q = null }
            else if (c == '\'' || c == '"') { q = c; cur.append(c) }
            else if (c == '\\' && i + 1 < format.length) { cur.append(c).append(format[i + 1]); i++ }
            else if (c == ';') { list.add(cur.toString()); cur.clear() }
            else cur.append(c)
            i++
        }
        list.add(cur.toString())
        return list
    }
}
