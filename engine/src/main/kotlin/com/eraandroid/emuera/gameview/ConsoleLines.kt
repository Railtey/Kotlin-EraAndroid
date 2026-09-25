package com.eraandroid.emuera.gameview

import com.eraandroid.emuera.config.Config
import com.eraandroid.emuera.sub.ExeEE
import com.eraandroid.emuera.sub.ScriptPosition

class ConsoleButtonString(private var parent: EmueraConsole?, strs: Array<AConsoleDisplayPart>) {
    var strArray: Array<AConsoleDisplayPart> = strs
        private set
    var parentLine: ConsoleDisplayLine? = null
    var isButton = false
        private set
    var isInteger = false
        private set
    var input: Long = 0
        private set
    var inputs: String? = null
        private set
    var pointX = -1
    var pointXisLocked = false
    var width = -1
    var xsubPixel = 0f
    var generation: Long = 0
        private set
    var errPos: ScriptPosition? = null
    var title: String? = null
    /** PRINTC 系で作られた (Android の列表示でまとめる) */
    var isPrintC = false
    var relativePointX = 0
        private set

    private fun asButton() {
        isButton = true
        val p = parent
        if (p != null) {
            generation = p.newButtonGeneration
            p.updateGeneration()
        }
    }

    constructor(console: EmueraConsole?, strs: Array<AConsoleDisplayPart>, input: Long) : this(console, strs) {
        this.input = input
        inputs = input.toString()
        isInteger = true
        asButton()
    }

    constructor(console: EmueraConsole?, strs: Array<AConsoleDisplayPart>, inputs: String) : this(console, strs) {
        this.inputs = inputs
        isInteger = false
        asButton()
    }

    constructor(console: EmueraConsole?, strs: Array<AConsoleDisplayPart>, input: Long, inputs: String) : this(console, strs) {
        this.input = input
        this.inputs = inputs
        isInteger = true
        asButton()
    }

    constructor(console: EmueraConsole?, strs: Array<AConsoleDisplayPart>, inputs: String, pos: ScriptPosition?) : this(console, strs) {
        this.inputs = inputs
        isInteger = false
        asButton()
        errPos = pos
    }

    fun lockPointX(relPx: Int) {
        pointX = relPx * Config.FontSize / 100
        xsubPixel = relPx * Config.FontSize / 100.0f - pointX
        pointXisLocked = true
        relativePointX = relPx
    }

    fun divideAt(divIndex: Int, sm: StringMeasure): ConsoleButtonString? {
        if (divIndex <= 0) return null
        val listA = ArrayList<AConsoleDisplayPart>()
        val listB = ArrayList<AConsoleDisplayPart>()
        var index = 0
        var cssIndex = 0
        var b = false
        while (cssIndex < strArray.size) {
            val part = strArray[cssIndex]
            if (b) { listB.add(part); cssIndex++; continue }
            val length = part.str.length
            if (divIndex < index + length) {
                val oldcss = part as? ConsoleStyledString
                if (oldcss == null || !oldcss.canDivide) throw ExeEE("文字列分割異常")
                val newCss = oldcss.divideAt(divIndex - index, sm)
                listA.add(oldcss)
                if (newCss != null) listB.add(newCss)
                b = true
                cssIndex++
                continue
            } else if (divIndex == index + length) {
                listA.add(part)
                b = true
                cssIndex++
                continue
            }
            index += length
            listA.add(part)
            cssIndex++
        }
        if (cssIndex >= strArray.size && listB.isEmpty()) return null
        strArray = listA.toTypedArray()
        val ret = ConsoleButtonString(null, listB.toTypedArray())
        calcWidth(sm, xsubPixel)
        ret.calcWidth(sm, 0f)
        calcPointX(pointX)
        ret.calcPointX(pointX + width)
        ret.parent = parent
        ret.parentLine = parentLine
        ret.isButton = isButton
        ret.isInteger = isInteger
        ret.input = input
        ret.inputs = inputs
        ret.generation = generation
        ret.errPos = errPos
        ret.title = title
        return ret
    }

    fun calcWidth(sm: StringMeasure, subpixelIn: Float) {
        var subpixel = subpixelIn
        width = -1
        if (strArray.isNotEmpty()) {
            width = 0
            for (css in strArray) {
                if (css.width <= 0) css.setWidth(sm, subpixel)
                width += css.width
                subpixel = css.xsubPixel
            }
            if (width <= 0) width = -1
        }
        xsubPixel = subpixel
    }

    fun calcPointX(pointx: Int) {
        var px = pointx
        if (!pointXisLocked) pointX = px else px = pointX
        for (p in strArray) {
            p.pointX = px
            px += p.width
        }
        if (strArray.isNotEmpty()) {
            pointX = strArray[0].pointX
            width = strArray[strArray.size - 1].pointX + strArray[strArray.size - 1].width - pointX
        }
    }

    fun shiftPositionX(shiftX: Int) {
        pointX += shiftX
        for (css in strArray) css.pointX += shiftX
    }

    fun drawTo(canvas: ConsoleCanvas, pointY: Int, isBackLog: Boolean) {
        val isSelecting = isButton && (parent?.buttonIsSelected(this) == true)
        for (css in strArray) css.drawTo(canvas, pointY, isSelecting, isBackLog)
    }

    override fun toString(): String {
        val sb = StringBuilder()
        for (css in strArray) sb.append(css.toString())
        return sb.toString()
    }
}

class ConsoleDisplayLine(buttons: Array<ConsoleButtonString>?, isLogical: Boolean, temporary: Boolean) {
    var lineNo = -1
    val isLogicalLine: Boolean = if (buttons == null) true else isLogical
    val isTemporary: Boolean = if (buttons == null) false else temporary
    var buttons: Array<ConsoleButtonString> = buttons ?: arrayOf()
        private set
    var align = DisplayLineAlignment.LEFT
        private set
    private var aligned = false

    init {
        for (b in this.buttons) b.parentLine = this
    }

    fun setAlignment(align: DisplayLineAlignment) {
        if (aligned) return
        aligned = true
        this.align = align
        if (buttons.isEmpty()) return
        var width = 0
        for (b in buttons) width += b.width
        val pointX = buttons[0].pointX
        val movetoX = when (align) {
            DisplayLineAlignment.LEFT -> { if (isLogicalLine) return; 0 }
            DisplayLineAlignment.CENTER -> Config.WindowX / 2 - width / 2
            DisplayLineAlignment.RIGHT -> Config.WindowX - width
        }
        val shiftX = movetoX - pointX
        if (shiftX != 0) shiftPositionX(shiftX)
    }

    fun shiftPositionX(shiftX: Int) { for (b in buttons) b.shiftPositionX(shiftX) }

    fun changeStr(newButtons: Array<ConsoleButtonString>) {
        for (b in newButtons) b.parentLine = this
        buttons = newButtons
    }

    fun drawTo(canvas: ConsoleCanvas, pointY: Int, isBackLog: Boolean) {
        for (b in buttons) b.drawTo(canvas, pointY, isBackLog)
    }

    /** この行の上端・下端 (画像などで行の高さを超える場合) */
    val top: Int get() = buttons.flatMap { it.strArray.asList() }.minOfOrNull { it.top } ?: 0
    val bottom: Int get() = buttons.flatMap { it.strArray.asList() }.maxOfOrNull { it.bottom } ?: Config.FontSize

    override fun toString(): String {
        val sb = StringBuilder()
        for (b in buttons) sb.append(b.toString())
        return sb.toString()
    }
}
