package com.eraandroid.emuera.gameview

import com.eraandroid.emuera.config.Config
import com.eraandroid.emuera.sub.ScriptPosition

/**
 * ConsoleStyledString = string + StringStyle
 * ConsoleButtonString = (ConsoleStyledString) * n + ButtonValue
 * ConsoleDisplayLine = (ConsoleButtonString) * n
 * PrintStringBufferはERBのPRINT命令からConsoleDisplayLineを作る
 */
class PrintStringBuffer(private val parent: EmueraConsole) {
    private val builder = StringBuilder()
    private val mStringList = ArrayList<AConsoleDisplayPart>()
    private var lastStringStyle: StringStyle? = null
    private val mButtonList = ArrayList<ConsoleButtonString?>()

    val bufferStrLength: Int
        get() {
            var length = 0
            for (css in mStringList) length += if (css is ConsoleStyledString) css.str.length else 1
            return length
        }

    private fun lastStyle(): StringStyle = lastStringStyle ?: StringStyle(Config.ForeColor, FontStyle.Regular, null)

    fun append(part: AConsoleDisplayPart) {
        if (builder.isNotEmpty()) {
            mStringList.add(ConsoleStyledString(builder.toString(), lastStyle()))
            builder.setLength(0)
        }
        mStringList.add(part)
    }

    fun append(strIn: String, style: StringStyle, forceButton: Boolean = false) {
        var str = strIn
        if (bufferStrLength > 2000) return
        if (forceButton) fromCssToButton()
        if (builder.isEmpty() || lastStringStyle == style) {
            if (builder.length > 2000) return
            if (builder.length + str.length > 2000)
                str = str.substring(0, 2000 - builder.length) + "※※※バッファーの文字数が2000字(全角1000字)を超えています。これ以降は表示できません※※※"
            builder.append(str)
            lastStringStyle = style
        } else {
            mStringList.add(ConsoleStyledString(builder.toString(), lastStyle()))
            builder.setLength(0)
            builder.append(str)
            lastStringStyle = style
        }
        if (forceButton) fromCssToButton()
    }

    fun appendButton(str: String, style: StringStyle, input: String) {
        fromCssToButton()
        mStringList.add(ConsoleStyledString(str, style))
        mButtonList.add(ConsoleButtonString(parent, takeParts(), input))
    }

    fun appendButton(str: String, style: StringStyle, input: Long) {
        fromCssToButton()
        mStringList.add(ConsoleStyledString(str, style))
        mButtonList.add(ConsoleButtonString(parent, takeParts(), input))
    }

    fun appendPlainText(str: String, style: StringStyle) {
        fromCssToButton()
        mStringList.add(ConsoleStyledString(str, style))
        mButtonList.add(ConsoleButtonString(parent, takeParts()))
    }

    val isEmpty: Boolean get() = mButtonList.isEmpty() && builder.isEmpty() && mStringList.isEmpty()

    override fun toString(): String {
        val buf = StringBuilder()
        for (b in mButtonList) buf.append(b?.toString() ?: "")
        for (css in mStringList) buf.append(css.str)
        buf.append(builder)
        return buf.toString()
    }

    fun appendAndFlushErrButton(str: String, style: StringStyle, input: String, pos: ScriptPosition?, sm: StringMeasure): ConsoleDisplayLine {
        fromCssToButton()
        mStringList.add(ConsoleStyledString(str, style))
        mButtonList.add(ConsoleButtonString(parent, takeParts(), input, pos))
        return flushSingleLine(sm, false)
    }

    fun flushSingleLine(stringMeasure: StringMeasure, temporary: Boolean): ConsoleDisplayLine {
        fromCssToButton()
        setWidthToButtonList(mButtonList, stringMeasure, true)
        val line = ConsoleDisplayLine(mButtonList.filterNotNull().toTypedArray(), true, temporary)
        clearBuffer()
        return line
    }

    fun flush(stringMeasure: StringMeasure, temporary: Boolean): Array<ConsoleDisplayLine> {
        fromCssToButton()
        val ret = buttonsToDisplayLines(mButtonList, stringMeasure, false, temporary)
        clearBuffer()
        return ret
    }

    private fun takeParts(): Array<AConsoleDisplayPart> {
        val a = mStringList.toTypedArray()
        mStringList.clear()
        return a
    }

    private fun clearBuffer() {
        builder.setLength(0)
        mStringList.clear()
        mButtonList.clear()
    }

    private fun fromCssToButton() {
        if (builder.isNotEmpty()) {
            mStringList.add(ConsoleStyledString(builder.toString(), lastStyle()))
            builder.setLength(0)
        }
        if (mStringList.isEmpty()) return
        mButtonList.addAll(createButtons(mStringList))
        mStringList.clear()
    }

    private fun createButtons(cssList: MutableList<AConsoleDisplayPart>): List<ConsoleButtonString> {
        val buf = StringBuilder()
        for (c in cssList) buf.append(c.str)
        val bpList = ButtonStringCreator.splitButton(buf.toString())
        if (bpList.size == 1) {
            val arr = cssList.toTypedArray()
            return listOf(if (bpList[0].canSelect) ConsoleButtonString(parent, arr, bpList[0].input) else ConsoleButtonString(parent, arr))
        }
        val ret = ArrayList<ConsoleButtonString>()
        var cssStartCharIndex = 0
        var buttonEndCharIndex = 0
        var cssIndex = 0
        val buttonCssList = ArrayList<AConsoleDisplayPart>()
        for (bp in bpList) {
            buttonEndCharIndex += bp.str.length
            while (cssIndex < cssList.size) {
                val css = cssList[cssIndex]
                if (cssStartCharIndex + css.str.length >= buttonEndCharIndex) {
                    val used = buttonEndCharIndex - cssStartCharIndex
                    if (used > 0 && css.canDivide) {
                        val newCss = (css as ConsoleStyledString).divideAt(used)
                        if (newCss != null) {
                            cssList.add(cssIndex + 1, newCss)
                            newCss.pointX = css.pointX + css.width
                        }
                    }
                    buttonCssList.add(css)
                    cssStartCharIndex += css.str.length
                    cssIndex++
                    break
                }
                buttonCssList.add(css)
                cssStartCharIndex += css.str.length
                cssIndex++
            }
            val arr = buttonCssList.toTypedArray()
            ret.add(if (bp.canSelect) ConsoleButtonString(parent, arr, bp.input) else ConsoleButtonString(parent, arr))
            buttonCssList.clear()
        }
        return ret
    }

    companion object {
        private fun toLine(lineButtonList: MutableList<ConsoleButtonString>, firstLine: Boolean, temporary: Boolean): ConsoleDisplayLine {
            val arr = lineButtonList.toTypedArray()
            lineButtonList.clear()
            return ConsoleDisplayLine(arr, firstLine, temporary)
        }

        fun buttonsToDisplayLines(buttonList: MutableList<ConsoleButtonString?>, stringMeasure: StringMeasure, nobr: Boolean, temporary: Boolean): Array<ConsoleDisplayLine> {
            if (buttonList.isEmpty()) return arrayOf()
            setWidthToButtonList(buttonList, stringMeasure, nobr)
            val lineList = ArrayList<ConsoleDisplayLine>()
            val lineButtonList = ArrayList<ConsoleButtonString>()
            val windowWidth = Config.DrawableWidth
            var firstLine = true
            var i = 0
            while (i < buttonList.size) {
                val b = buttonList[i]
                if (b == null) {
                    lineList.add(toLine(lineButtonList, firstLine, temporary))
                    firstLine = false
                    buttonList.removeAt(i)
                    continue
                }
                if (nobr || b.pointX + b.width <= windowWidth) {
                    lineButtonList.add(b)
                    i++
                    continue
                }
                if (!Config.ButtonWrap || lineButtonList.isEmpty() || (!b.isButton && !Config.CompatiLinefeedAs1739)) {
                    val divIndex = getDivideIndex(b, stringMeasure)
                    if (divIndex > 0) {
                        val newButton = b.divideAt(divIndex, stringMeasure)
                        buttonList.add(i + 1, newButton)
                        lineButtonList.add(b)
                        i++
                    } else if (divIndex == 0 && lineButtonList.isNotEmpty()) {
                        // まるごと次の行に送る
                    } else {
                        lineButtonList.add(b)
                        i++
                        continue
                    }
                }
                lineList.add(toLine(lineButtonList, firstLine, temporary))
                firstLine = false
                var pointX = 0
                for (j in i until buttonList.size) {
                    val bj = buttonList[j] ?: break
                    bj.calcPointX(pointX)
                    pointX += bj.width
                }
                // buttonList[i] は次の行で再検討
            }
            if (lineButtonList.isNotEmpty()) lineList.add(toLine(lineButtonList, firstLine, temporary))
            return lineList.toTypedArray()
        }

        private fun setWidthToButtonList(buttonList: List<ConsoleButtonString?>, stringMeasure: StringMeasure, @Suppress("UNUSED_PARAMETER") nobr: Boolean) {
            var pointX = 0
            var subPixel = 0.5f
            for (button in buttonList) {
                if (button == null) { pointX = 0; continue }
                button.calcWidth(stringMeasure, subPixel)
                button.calcPointX(pointX)
                pointX = button.pointX + button.width
                subPixel = button.xsubPixel
            }
        }

        private fun getDivideIndex(button: ConsoleButtonString, sm: StringMeasure): Int {
            var divCss: AConsoleDisplayPart? = null
            var pointX = button.pointX
            var strLength = 0
            var index = 0
            for (css in button.strArray) {
                if (pointX + css.width > Config.DrawableWidth) {
                    if (index == 0 && !css.canDivide) continue
                    divCss = css
                    break
                }
                index++
                strLength += css.str.length
                pointX += css.width
            }
            if (divCss != null) {
                val cssDivIndex = getDivideIndex(divCss, sm)
                if (cssDivIndex > 0) strLength += cssDivIndex
            }
            return strLength
        }

        private fun getDivideIndex(part: AConsoleDisplayPart, sm: StringMeasure): Int {
            if (!part.canDivide) return -1
            val css = part as? ConsoleStyledString ?: return -1
            val widthLimit = Config.DrawableWidth - css.pointX
            val str = css.str
            // 二分探索 (元実装は線形探索だが結果は同じ)
            var lo = 0
            var hi = str.length
            while (hi - lo > 1) {
                val mid = (lo + hi) / 2
                if (sm.getDisplayLength(str.substring(0, mid), css.font) <= widthLimit) lo = mid else hi = mid
            }
            return lo
        }
    }
}
