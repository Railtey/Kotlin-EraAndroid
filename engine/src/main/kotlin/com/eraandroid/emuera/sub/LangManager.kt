package com.eraandroid.emuera.sub

import com.eraandroid.emuera.config.Config
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction

/** マルチ言語に対応可能な形式 (byte length in the configured legacy encoding) */
object LangManager {
    private fun byteCount(s: String): Int {
        if (s.isEmpty()) return 0
        // fast path for ASCII
        var ascii = true
        for (c in s) if (c.code >= 0x80) { ascii = false; break }
        if (ascii) return s.length
        val enc = Config.LangEncode.newEncoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE)
            .replaceWith(byteArrayOf('?'.code.toByte()))
        return enc.encode(CharBuffer.wrap(s)).remaining()
    }

    fun getStrlenLang(str: String): Int = byteCount(str)

    fun getUFTIndex(str: String, langIndex: Int): Int {
        if (langIndex <= 0) return 0
        val totalByte = getStrlenLang(str)
        if (langIndex >= totalByte) return str.length
        var utf = 0
        var jis = 0
        for (i in str.indices) {
            jis += byteCount(str[utf].toString())
            utf++
            if (jis >= langIndex) break
        }
        return utf
    }

    fun getSubStringLang(str: String, startindex: Int, lengthIn: Int): String {
        var length = lengthIn
        val totalByte = getStrlenLang(str)
        if (startindex >= totalByte || length == 0) return ""
        if (length < 0 || length > totalByte) length = totalByte
        val ret = StringBuilder()
        var utf = 0
        var jis = 0
        if (startindex <= 0) {
            if (length == totalByte) return str
        } else {
            for (i in str.indices) {
                jis += byteCount(str[utf].toString())
                utf++
                if (jis >= startindex) break
            }
            if (utf >= str.length) return ""
        }
        jis = 0
        while (true) {
            ret.append(str[utf])
            jis += byteCount(str[utf].toString())
            utf++
            if (jis >= length) break
            if (utf >= str.length) break
        }
        return ret.toString()
    }
}
