package com.eraandroid.emuera.gameview

import com.eraandroid.emuera.sub.LexicalAnalyzer
import com.eraandroid.emuera.sub.StringStream

class ButtonPrimitive {
    var str = ""
    var input: Long = 0
    var canSelect = false
    override fun toString() = str
}

object ButtonStringCreator {
    fun split(printBuffer: String): List<String> = syn(printBuffer).map { it.str }
    fun splitButton(printBuffer: String): List<ButtonPrimitive> = syn(printBuffer)

    private fun nonButton(s: String): List<ButtonPrimitive> = listOf(ButtonPrimitive().also { it.str = s })

    private fun syn(printString: String): List<ButtonPrimitive> {
        val ret = ArrayList<ButtonPrimitive>()
        if (printString.isEmpty()) return nonButton(printString)
        if (!printString.contains('[') || !printString.contains(']')) return nonButton(printString)
        val strs = lex(StringStream(printString)) ?: return nonButton(printString)
        var beforeButton = false
        var afterButton = false
        var buttonCount = 0
        val inpL = LongArray(1)
        for (s in strs) {
            if (s.isEmpty()) continue
            val c = s[0]
            if (LexicalAnalyzer.isWhiteSpace(c)) {
            } else if (isButtonCore(s, inpL)) {
                buttonCount++
                afterButton = false
            } else {
                afterButton = true
                if (buttonCount == 0) beforeButton = true
            }
        }
        if (buttonCount <= 1) {
            ret.add(ButtonPrimitive().also { it.str = printString; it.canSelect = buttonCount >= 1; it.input = inpL[0] })
            return ret
        }
        buttonCount = 0
        val alignmentRight = !beforeButton && afterButton
        val alignmentLeft = beforeButton && !afterButton
        val alignmentEtc = !alignmentRight && !alignmentLeft
        var canSelect = false
        var input = 0L
        var state = 0
        val buffer = StringBuilder()
        fun reduce() {
            if (buffer.isEmpty()) return
            ret.add(ButtonPrimitive().also { it.str = buffer.toString(); it.canSelect = canSelect; it.input = input })
            buffer.setLength(0)
            canSelect = false
            input = 0
        }
        for (s in strs) {
            if (s.isEmpty()) continue
            val c = s[0]
            if (LexicalAnalyzer.isWhiteSpace(c)) {
                if ((state and 3) == 3 && alignmentEtc && s.length >= 2) {
                    reduce()
                    buffer.append(s)
                    state = 0
                } else buffer.append(s)
                continue
            }
            if (isButtonCore(s, inpL)) {
                buttonCount++
                if ((state and 1) == 1 || alignmentRight) {
                    reduce()
                    buffer.append(s)
                    input = inpL[0]
                    canSelect = true
                    state = 1
                } else if (alignmentLeft) {
                    buffer.append(s)
                    input = inpL[0]
                    canSelect = true
                    reduce()
                    state = 0
                } else {
                    buffer.append(s)
                    input = inpL[0]
                    canSelect = true
                    state = 1
                }
                continue
            }
            buffer.append(s)
            state = state or 2
        }
        reduce()
        return ret
    }

    private val numReg = Regex("\\[\\s*([0][xXbB])?[+-]?[0-9]+([eEpP][0-9]+)?\\s*\\]")

    private fun isButtonCore(str: String?, input: LongArray): Boolean {
        if (str == null || str.length < 3 || str[0] != '[' || str[str.length - 1] != ']') return false
        if (!numReg.containsMatchIn(str)) return false
        val stInt = StringStream(str.substring(1, str.length - 1))
        LexicalAnalyzer.skipAllSpace(stInt)
        return try {
            input[0] = LexicalAnalyzer.readInt64(stInt, false)
            true
        } catch (e: Exception) { false }
    }

    private fun lex(st: StringStream): List<String>? {
        val strs = ArrayList<String>()
        var state = 0
        var startIndex = 0
        fun reduce() {
            if (st.currentPosition == startIndex) return
            strs.add(st.substring(startIndex, st.currentPosition - startIndex))
            startIndex = st.currentPosition
        }
        while (!st.eos) {
            val c = st.current
            if (c == '[') {
                if (state == 1) return null
                reduce()
                state = 1
                st.shiftNext()
            } else if (c == ']') {
                if (state != 1) return null
                st.shiftNext()
                reduce()
                state = 0
            } else if (state == 0 && LexicalAnalyzer.isWhiteSpace(c)) {
                reduce()
                LexicalAnalyzer.skipAllSpace(st)
                reduce()
            } else st.shiftNext()
        }
        reduce()
        return strs
    }
}
