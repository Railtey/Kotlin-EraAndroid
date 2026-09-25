package com.eraandroid.emuera.gameview

import com.eraandroid.emuera.config.Config
import com.eraandroid.emuera.config.EraColor
import com.eraandroid.emuera.gamedata.expression.OperatorCode
import com.eraandroid.emuera.sub.*

/** Emuera用Htmlもどき */
object HtmlManager {
    private val repDic = mapOf('&' to "&amp;", '>' to "&gt;", '<' to "&lt;", '"' to "&quot;", '\'' to "&apos;")

    private class FontTag {
        var color = -1
        var bColor = -1
        var fontName: String? = null
    }

    private class ButtonTag {
        var isButton = true
        var isButtonTag = true
        var buttonValueInt: Long = 0
        var buttonValueStr: String? = null
        var buttonTitle: String? = null
        var buttonIsInteger = false
        var pointX = 0
        var pointXisLocked = false
    }

    private class State {
        var lineHead = true
        var fontStyle = FontStyle.Regular
        val fonttagList = ArrayList<FontTag>()
        var flagNobr = false
        var flagP = false
        var flagNobrClosed = false
        var flagPClosed = false
        var alignment = DisplayLineAlignment.LEFT
        var lastButtonTag: ButtonTag? = null
        var currentButtonTag: ButtonTag? = null
        var flagBr = false
        var flagButton = false

        fun getSS(): StringStyle {
            var c = Config.ForeColor
            var b = Config.FocusColor
            var fontname: String? = null
            var colorChanged = false
            if (fonttagList.isNotEmpty()) {
                val font = fonttagList[fonttagList.size - 1]
                fontname = font.fontName
                if (font.color >= 0) { colorChanged = true; c = EraColor(font.color) }
                if (font.bColor >= 0) b = EraColor(font.bColor)
            }
            return StringStyle(c, colorChanged, b, fontStyle, fontname)
        }
    }

    fun displayLine2Html(lines: Array<ConsoleDisplayLine>?, needPandN: Boolean): String {
        if (lines == null || lines.isEmpty()) return ""
        val b = StringBuilder()
        if (needPandN) {
            b.append(when (lines[0].align) {
                DisplayLineAlignment.LEFT -> "<p align='left'>"
                DisplayLineAlignment.CENTER -> "<p align='center'>"
                DisplayLineAlignment.RIGHT -> "<p align='right'>"
            })
            b.append("<nobr>")
        }
        for ((dispCounter, line) in lines.withIndex()) {
            if (dispCounter != 0) b.append("<br>")
            for (button in line.buttons) {
                val titleValue = if (!button.title.isNullOrEmpty()) escape(button.title!!) else null
                val hasTag = button.isButton || titleValue != null || button.pointXisLocked
                if (hasTag) {
                    if (button.isButton) b.append("<button value='").append(escape(button.inputs ?: "")).append("'")
                    else b.append("<nonbutton")
                    if (titleValue != null) b.append(" title='").append(titleValue).append("'")
                    if (button.pointXisLocked) b.append(" pos='").append(button.relativePointX).append("'")
                    b.append(">")
                }
                for (part in button.strArray) {
                    when (part) {
                        is ConsoleStyledString -> {
                            b.append(getStringStyleStartingTag(part.stringStyle))
                            b.append(escape(part.str))
                            b.append(getClosingStyleStartingTag(part.stringStyle))
                        }
                        is ConsoleImagePart, is ConsoleShapePart -> b.append(part.altText ?: "")
                    }
                }
                if (hasTag) b.append(if (button.isButton) "</button>" else "</nonbutton>")
            }
        }
        if (needPandN) b.append("</nobr></p>")
        return b.toString()
    }

    fun htmlTagSplit(str: String): Array<String>? {
        val list = ArrayList<String>()
        val st = StringStream(str)
        while (!st.eos) {
            var found = st.find('<')
            if (found < 0) { list.add(st.substring()); break }
            else if (found > 0) {
                list.add(st.substring(st.currentPosition, found))
                st.currentPosition += found
            }
            found = st.find('>')
            if (found < 0) return null
            found++
            list.add(st.substring(st.currentPosition, found))
            st.currentPosition += found
        }
        return list.toTypedArray()
    }

    fun html2DisplayLine(str: String, sm: StringMeasure, console: EmueraConsole?): Array<ConsoleDisplayLine> {
        val cssList = ArrayList<AConsoleDisplayPart>()
        val buttonList = ArrayList<ConsoleButtonString?>()
        val st = StringStream(str)
        val hasComment = str.indexOf("<!--") >= 0
        val hasReturn = str.indexOf('\n') >= 0
        val state = State()
        while (!st.eos) {
            var found = st.find('<')
            if (hasReturn) {
                val rFound = st.find('\n')
                if (rFound >= 0 && (found > rFound || found < 0)) found = rFound
            }
            if (found < 0) {
                cssList.add(ConsoleStyledString(unescape(st.substring()), state.getSS()))
                if (state.flagPClosed) throw CodeEE("</p>の後にテキストがあります")
                if (state.flagNobrClosed) throw CodeEE("</nobr>の後にテキストがあります")
                break
            } else if (found > 0) {
                cssList.add(ConsoleStyledString(unescape(st.substring(st.currentPosition, found)), state.getSS()))
                state.lineHead = false
                st.currentPosition += found
            }
            if (hasComment && st.currentEqualTo("<!--")) {
                st.currentPosition += 4
                found = st.find("-->")
                if (found < 0) throw CodeEE("コメント終了タグ\"-->\"がみつかりません")
                st.currentPosition += found + 3
                continue
            }
            if (hasReturn && st.current == '\n') {
                state.flagBr = true
                st.shiftNext()
            } else {
                st.shiftNext()
                val part = tagAnalyze(state, st)
                if (st.current != '>') throw CodeEE("タグ終端'>'が見つかりません")
                if (part != null) cssList.add(part)
                st.shiftNext()
            }
            if (state.flagBr) {
                state.lastButtonTag = state.currentButtonTag
                if (cssList.isNotEmpty()) buttonList.add(cssToButton(cssList, state, console))
                buttonList.add(null)
            }
            if (state.flagButton && cssList.isNotEmpty()) buttonList.add(cssToButton(cssList, state, console))
            state.flagBr = false
            state.flagButton = false
            state.lastButtonTag = state.currentButtonTag
        }
        if (state.currentButtonTag != null || state.fontStyle != FontStyle.Regular || state.fonttagList.isNotEmpty())
            throw CodeEE("閉じられていないタグがあります")
        if (cssList.isNotEmpty()) buttonList.add(cssToButton(cssList, state, console))
        for (button in buttonList) {
            if (button != null && button.pointXisLocked) {
                if (!state.flagNobr) throw CodeEE("<nobr>が設定されていない行ではpos属性は使用できません")
                if (state.alignment != DisplayLineAlignment.LEFT) throw CodeEE("alignがleftでない行ではpos属性は使用できません")
                break
            }
        }
        val ret = PrintStringBuffer.buttonsToDisplayLines(buttonList, sm, state.flagNobr, false)
        for (dl in ret) dl.setAlignment(state.alignment)
        return ret
    }

    fun html2PlainText(str: String): String = unescape(str.replace(Regex("<[^<]*>"), ""))

    fun escape(str: String): String {
        val b = StringBuilder()
        for (c in str) b.append(repDic[c] ?: c.toString())
        return b.toString()
    }

    fun unescape(str: String): String {
        if (str.indexOf('&') < 0) return str
        var index = 0
        val b = StringBuilder()
        while (index < str.length) {
            var found = str.indexOf('&', index)
            if (found < 0) { b.append(str.substring(index)); break }
            if (found > index) b.append(str, index, found)
            index = found
            found = str.indexOf(';', index)
            if (found <= index + 1) {
                if (found < 0) throw CodeEE("'&'に対応する';'がみつかりません")
                throw CodeEE("'&'と';'が連続しています")
            }
            val escWordRow = str.substring(index + 1, found)
            index = found + 1
            var escWord = escWordRow.lowercase()
            when (escWord) {
                "nbsp" -> b.append(' ')
                "amp" -> b.append('&')
                "gt" -> b.append('>')
                "lt" -> b.append('<')
                "quot" -> b.append('"')
                "apos" -> b.append('\'')
                else -> {
                    var base = 10
                    if (escWord[0] != '#') throw CodeEE("\"&$escWordRow;\"は適切な文字参照ではありません")
                    if (escWord.length > 1 && escWord[1] == 'x') { base = 16; escWord = escWord.substring(2) } else escWord = escWord.substring(1)
                    val unicode = escWord.toIntOrNull(base) ?: throw CodeEE("\"&$escWordRow;\"は適切な文字参照ではありません")
                    if (unicode < 0 || unicode > 0xFFFF) throw CodeEE("\"&$escWordRow;\"はUnicodeの範囲外です(サロゲートペアは使えません)")
                    b.append(unicode.toChar())
                }
            }
        }
        return b.toString()
    }

    private fun cssToButton(cssList: MutableList<AConsoleDisplayPart>, state: State, console: EmueraConsole?): ConsoleButtonString {
        val css = cssList.toTypedArray()
        cssList.clear()
        val lb = state.lastButtonTag
        val ret = if (lb != null && lb.isButton) {
            if (lb.buttonIsInteger) ConsoleButtonString(console, css, lb.buttonValueInt, lb.buttonValueStr ?: "")
            else ConsoleButtonString(console, css, lb.buttonValueStr ?: "")
        } else ConsoleButtonString(console, css).also { it.title = null }
        if (lb != null) {
            ret.title = lb.buttonTitle
            if (lb.pointXisLocked) ret.lockPointX(lb.pointX)
        }
        return ret
    }

    fun getColorToString(color: EraColor): String = "#" + String.format("%06X", color.rgb and 0xFFFFFF)

    private fun fontChanged(style: StringStyle) =
        !(style.fontname == Config.FontName && !style.colorChanged && style.buttonColor == Config.FocusColor)

    private fun getStringStyleStartingTag(style: StringStyle): String {
        val fc = fontChanged(style)
        if (!fc && style.fontStyle == FontStyle.Regular) return ""
        val b = StringBuilder()
        if (fc) {
            b.append("<font")
            if (style.fontname != Config.FontName) b.append(" face='").append(escape(style.fontname)).append("'")
            if (style.colorChanged) b.append(" color='").append(getColorToString(style.color)).append("'")
            if (style.buttonColor != Config.FocusColor) b.append(" bcolor='").append(getColorToString(style.buttonColor)).append("'")
            b.append(">")
        }
        val fs = style.fontStyle
        if ((fs and FontStyle.Strikeout) != 0) b.append("<s>")
        if ((fs and FontStyle.Underline) != 0) b.append("<u>")
        if ((fs and FontStyle.Italic) != 0) b.append("<i>")
        if ((fs and FontStyle.Bold) != 0) b.append("<b>")
        return b.toString()
    }

    private fun getClosingStyleStartingTag(style: StringStyle): String {
        val fc = fontChanged(style)
        if (!fc && style.fontStyle == FontStyle.Regular) return ""
        val b = StringBuilder()
        val fs = style.fontStyle
        if ((fs and FontStyle.Bold) != 0) b.append("</b>")
        if ((fs and FontStyle.Italic) != 0) b.append("</i>")
        if ((fs and FontStyle.Underline) != 0) b.append("</u>")
        if ((fs and FontStyle.Strikeout) != 0) b.append("</s>")
        if (fc) b.append("</font>")
        return b.toString()
    }

    private class Attr(val name: String, val value: String)

    private fun readAttrs(wc: WordCollection?, st: StringStream): List<Attr> {
        val list = ArrayList<Attr>()
        if (wc == null) return list
        while (!wc.eol) {
            val word = wc.current as? IdentifierWord
            wc.shiftNext()
            val op = wc.current as? OperatorWord
            wc.shiftNext()
            val attr = wc.current as? LiteralStringWord
            wc.shiftNext()
            if (word == null || op == null || op.code != OperatorCode.Assignment || attr == null) tagError(st)
            list.add(Attr(word.code, unescape(attr.str)))
        }
        return list
    }

    private fun tagError(st: StringStream): Nothing = throw CodeEE("html文字列\"" + st.rowString + "\"のタグ解析中にエラーが発生しました")

    private fun tagAnalyze(state: State, st: StringStream): AConsoleDisplayPart? {
        val endTag = st.current == '/'
        if (endTag) {
            st.shiftNext()
            val found = st.find('>')
            if (found < 0) { st.currentPosition = st.rowString.length; return null }
            val tag = st.substring(st.currentPosition, found).trim()
            st.currentPosition += found
            var endStyle = FontStyle.Strikeout
            when (tag.lowercase()) {
                "b", "i", "u", "s" -> {
                    endStyle = when (tag.lowercase()) { "b" -> FontStyle.Bold; "i" -> FontStyle.Italic; "u" -> FontStyle.Underline; else -> FontStyle.Strikeout }
                    if ((state.fontStyle and endStyle) == 0) throw CodeEE("</$tag>の前に<$tag>がありません")
                    state.fontStyle = state.fontStyle xor endStyle
                }
                "p" -> {
                    if (!state.flagP || state.flagPClosed) throw CodeEE("</p>の前に<p>がありません")
                    state.flagPClosed = true
                }
                "nobr" -> {
                    if (!state.flagNobr || state.flagNobrClosed) throw CodeEE("</nobr>の前に<nobr>がありません")
                    state.flagNobrClosed = true
                }
                "font" -> {
                    if (state.fonttagList.isEmpty()) throw CodeEE("</font>の前に<font>がありません")
                    state.fonttagList.removeAt(state.fonttagList.size - 1)
                }
                "button" -> {
                    if (state.currentButtonTag == null || !state.currentButtonTag!!.isButtonTag) throw CodeEE("</button>の前に<button>がありません")
                    state.currentButtonTag = null
                    state.flagButton = true
                }
                "nonbutton" -> {
                    if (state.currentButtonTag == null || state.currentButtonTag!!.isButtonTag) throw CodeEE("</nonbutton>の前に<nonbutton>がありません")
                    state.currentButtonTag = null
                    state.flagButton = true
                }
                else -> throw CodeEE("終了タグ</$tag>は解釈できません")
            }
            return null
        }
        val tempUseMacro = LexicalAnalyzer.useMacro
        var wc: WordCollection? = null
        val tag: String
        try {
            LexicalAnalyzer.useMacro = false
            tag = LexicalAnalyzer.readSingleIdentifier(st)
            LexicalAnalyzer.skipWhiteSpace(st)
            if (st.current != '>') wc = LexicalAnalyzer.analyse(st, LexEndWith.GreaterThan, LexAnalyzeFlag.AllowAssignment or LexAnalyzeFlag.AllowSingleQuotationStr)
        } finally {
            LexicalAnalyzer.useMacro = tempUseMacro
        }
        if (tag.isEmpty()) tagError(st)
        when (tag.lowercase()) {
            "b", "i", "u", "s" -> {
                val newStyle = when (tag.lowercase()) { "b" -> FontStyle.Bold; "i" -> FontStyle.Italic; "u" -> FontStyle.Underline; else -> FontStyle.Strikeout }
                if (wc != null) throw CodeEE("<$tag>タグにに属性が設定されています")
                if ((state.fontStyle and newStyle) != 0) throw CodeEE("<$tag>が二重に使われています")
                state.fontStyle = state.fontStyle or newStyle
                return null
            }
            "br" -> {
                if (wc != null) throw CodeEE("<$tag>タグにに属性が設定されています")
                state.flagBr = true
                return null
            }
            "nobr" -> {
                if (wc != null) throw CodeEE("<$tag>タグに属性が設定されています")
                if (!state.lineHead) throw CodeEE("<nobr>が行頭以外で使われています")
                if (state.flagNobr) throw CodeEE("<nobr>が2度以上使われています")
                state.flagNobr = true
                return null
            }
            "p" -> {
                if (wc == null) throw CodeEE("<$tag>タグに属性が設定されていません")
                if (!state.lineHead) throw CodeEE("<p>が行頭以外で使われています")
                if (state.flagNobr) throw CodeEE("<p>が2度以上使われています")
                val attrs = readAttrs(wc, st)
                if (attrs.size != 1) tagError(st)
                val a = attrs[0]
                if (!a.name.equals("align", ignoreCase = true)) throw CodeEE("<p>タグの属性名${a.name}は解釈できません")
                state.alignment = when (a.value.lowercase()) {
                    "left" -> DisplayLineAlignment.LEFT
                    "center" -> DisplayLineAlignment.CENTER
                    "right" -> DisplayLineAlignment.RIGHT
                    else -> throw CodeEE("属性値${a.value}は解釈できません")
                }
                state.flagP = true
                return null
            }
            "img" -> {
                if (wc == null) throw CodeEE("<$tag>タグに属性が設定されていません")
                var src: String? = null
                var srcb: String? = null
                var height = 0
                var width = 0
                var ypos = 0
                for (a in readAttrs(wc, st)) {
                    val dup = "<$tag>タグに${a.name}属性が2度以上指定されています"
                    when (a.name.lowercase()) {
                        "src" -> { if (src != null) throw CodeEE(dup); src = a.value }
                        "srcb" -> { if (srcb != null) throw CodeEE(dup); srcb = a.value }
                        "height" -> { if (height != 0) throw CodeEE(dup); height = a.value.trim().toIntOrNull() ?: throw CodeEE("<$tag>タグのheight属性の属性値が数値として解釈できません") }
                        "width" -> { if (width != 0) throw CodeEE(dup); width = a.value.trim().toIntOrNull() ?: throw CodeEE("<$tag>タグのwidth属性の属性値が数値として解釈できません") }
                        "ypos" -> { if (ypos != 0) throw CodeEE(dup); ypos = a.value.trim().toIntOrNull() ?: throw CodeEE("<$tag>タグのypos属性の属性値が数値として解釈できません") }
                        else -> throw CodeEE("<$tag>タグの属性名${a.name}は解釈できません")
                    }
                }
                if (src == null) throw CodeEE("<$tag>タグにsrc属性が設定されていません")
                return ConsoleImagePart(src, srcb, height, width, ypos)
            }
            "shape" -> {
                if (wc == null) throw CodeEE("<$tag>タグに属性が設定されていません")
                var param: IntArray? = null
                var type: String? = null
                var color = -1
                var bcolor = -1
                for (a in readAttrs(wc, st)) {
                    val dup = "<$tag>タグに${a.name}属性が2度以上指定されています"
                    when (a.name.lowercase()) {
                        "color" -> { if (color >= 0) throw CodeEE(dup); color = stringToColorInt32(a.value) }
                        "bcolor" -> { if (bcolor >= 0) throw CodeEE(dup); bcolor = stringToColorInt32(a.value) }
                        "type" -> { if (type != null) throw CodeEE(dup); type = a.value }
                        "param" -> {
                            if (param != null) throw CodeEE(dup)
                            val tokens = a.value.split(',')
                            param = IntArray(tokens.size) { tokens[it].trim().toIntOrNull() ?: throw CodeEE("<$tag>タグの${a.name}属性の属性値が数値として解釈できません") }
                        }
                        else -> throw CodeEE("<$tag>タグの属性名${a.name}は解釈できません")
                    }
                }
                if (param == null) throw CodeEE("<$tag>タグにparam属性が設定されていません")
                if (type == null) throw CodeEE("<$tag>タグにtype属性が設定されていません")
                val c = if (color >= 0) EraColor(color) else Config.ForeColor
                val b = if (bcolor >= 0) EraColor(bcolor) else Config.FocusColor
                return ConsoleShapePart.createShape(type, param, c, b, color >= 0)
            }
            "button", "nonbutton" -> {
                if (state.currentButtonTag != null) throw CodeEE("<button>又は<nonbutton>が入れ子にされています")
                val buttonTag = ButtonTag()
                val isButton = tag.lowercase() == "button"
                var value: String? = null
                for (a in readAttrs(wc, st)) {
                    val dup = "<$tag>タグに${a.name}属性が2度以上指定されています"
                    when (a.name.lowercase()) {
                        "value" -> {
                            if (!isButton) throw CodeEE("<$tag>タグにvalue属性が設定されています")
                            if (value != null) throw CodeEE(dup)
                            value = a.value
                        }
                        "title" -> { if (buttonTag.buttonTitle != null) throw CodeEE(dup); buttonTag.buttonTitle = a.value }
                        "pos" -> {
                            if (buttonTag.pointXisLocked) throw CodeEE(dup)
                            buttonTag.pointX = a.value.trim().toIntOrNull() ?: throw CodeEE("<$tag>タグのpos属性の属性値が数値として解釈できません")
                            buttonTag.pointXisLocked = true
                        }
                        else -> throw CodeEE("<$tag>タグの属性名${a.name}は解釈できません")
                    }
                }
                if (isButton) {
                    val iv = value?.trim()?.toLongOrNull()
                    buttonTag.buttonIsInteger = iv != null
                    buttonTag.buttonValueInt = iv ?: 0
                    buttonTag.buttonValueStr = value
                }
                buttonTag.isButton = value != null
                buttonTag.isButtonTag = isButton
                state.currentButtonTag = buttonTag
                state.flagButton = true
                return null
            }
            "font" -> {
                if (wc == null) throw CodeEE("<$tag>タグに属性が設定されていません")
                val font = FontTag()
                for (a in readAttrs(wc, st)) {
                    val dup = "<$tag>タグに${a.name}属性が2度以上指定されています"
                    when (a.name.lowercase()) {
                        "color" -> { if (font.color >= 0) throw CodeEE(dup); font.color = stringToColorInt32(a.value) }
                        "bcolor" -> { if (font.bColor >= 0) throw CodeEE(dup); font.bColor = stringToColorInt32(a.value) }
                        "face" -> { if (font.fontName != null) throw CodeEE(dup); font.fontName = a.value }
                        else -> throw CodeEE("<$tag>タグの属性名${a.name}は解釈できません")
                    }
                }
                if (state.fonttagList.isNotEmpty()) {
                    val old = state.fonttagList[state.fonttagList.size - 1]
                    if (font.color < 0) font.color = old.color
                    if (font.bColor < 0) font.bColor = old.bColor
                    if (font.fontName == null) font.fontName = old.fontName
                }
                state.fonttagList.add(font)
                return null
            }
            else -> tagError(st)
        }
    }

    private fun stringToColorInt32(str: String): Int {
        if (str.isEmpty()) throw CodeEE("色を表す単語又は#RRGGBB値が必要です")
        if (str[0] == '#') {
            val colorvalue = str.substring(1)
            val i = colorvalue.toIntOrNull(16) ?: throw CodeEE("${colorvalue}は数値として解釈できません")
            if (i < 0 || i > 0xFFFFFF) throw CodeEE("${colorvalue}は適切な色指定の範囲外です")
            return i
        }
        val c = NamedColors.fromName(str)
        if (c == null) {
            if (str.equals("transparent", ignoreCase = true)) throw CodeEE("無色透明(Transparent)は色として指定できません")
            if (str.toIntOrNull(16) == null) throw CodeEE("指定された色名\"$str\"は無効な色名です")
            throw CodeEE("指定された色名\"$str\"は無効な色名です(16進数で色を指定する場合には数値の前に#が必要です)")
        }
        return c
    }
}
