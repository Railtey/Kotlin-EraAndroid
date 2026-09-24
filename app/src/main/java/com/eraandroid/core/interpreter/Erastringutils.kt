package com.eraandroid.core.interpreter

// ─── ERA 문자 너비/바이트 유틸 (PC SHIFT-JIS 호환) ──────────────────────────
//
// Interpreter.kt에서 분리된 순수 함수 모음.
// EraInterpreter 의존성 없음 — 어디서든 자유롭게 호출 가능.

object EraStringUtils {

    /**
     * ERA 바이트 너비: ASCII = 1, 한글/한자/전각 = 2
     * PC emuera의 STRLENS와 동일한 동작.
     */
    fun eraByteWidth(s: String): Int {
        var w = 0
        for (c in s) w += if (c.code > 0x7F) 2 else 1
        return w
    }

    /**
     * ERA 표시 너비: 한글/한자/전각 = 2, 나머지 = 1
     * PRINTC 컬럼 정렬에 사용.
     */
    fun eraDisplayWidth(s: String): Int {
        var w = 0
        for (c in s) {
            w += if (c.code > 0xFF ||
                c.code in 0x3000..0x9FFF ||
                c.code in 0xAC00..0xD7A3 ||
                c.code in 0xFF00..0xFFEF) 2 else 1
        }
        return w
    }

    /**
     * 바이트 오프셋 기준 substring.
     * startByte: 시작 바이트 위치
     * lenByte: 추출 바이트 길이 (-1이면 끝까지)
     */
    fun eraSubstringByByte(s: String, startByte: Int, lenByte: Int): String {
        val sb = StringBuilder()
        var bp = 0; var cp = 0
        while (cp < s.length && bp < startByte) {
            bp += if (s[cp].code > 0x7F) 2 else 1
            cp++
        }
        var rb = 0
        while (cp < s.length) {
            val cw = if (s[cp].code > 0x7F) 2 else 1
            if (lenByte >= 0 && rb + cw > lenByte) break
            sb.append(s[cp]); rb += cw; cp++
        }
        return sb.toString()
    }

    /**
     * 바이트 오프셋을 문자 인덱스로 변환.
     */
    fun eraByteToCharIndex(s: String, byteOffset: Int): Int {
        var bp = 0
        for (i in s.indices) {
            if (bp >= byteOffset) return i
            bp += if (s[i].code > 0x7F) 2 else 1
        }
        return s.length
    }

    /**
     * 표시 너비 기준 substring (너비 초과 시 잘라냄).
     */
    fun eraSubstringByWidth(s: String, maxWidth: Int): String {
        var w = 0
        val sb = StringBuilder()
        for (c in s) {
            val cw = if (c.code > 0xFF ||
                c.code in 0x3000..0x9FFF ||
                c.code in 0xAC00..0xD7A3 ||
                c.code in 0xFF00..0xFFEF) 2 else 1
            if (w + cw > maxWidth) break
            sb.append(c); w += cw
        }
        return sb.toString()
    }

    /**
     * 표시 너비 기준으로 width까지 공백 패딩 (PRINTC 컬럼 정렬).
     */
    fun eraColumnPad(text: String, width: Int): String {
        val dw = eraDisplayWidth(text)
        return if (dw < width) text + " ".repeat(width - dw) else text
    }
}