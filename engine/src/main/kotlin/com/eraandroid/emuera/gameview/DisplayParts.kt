package com.eraandroid.emuera.gameview

import com.eraandroid.emuera.config.Config
import com.eraandroid.emuera.config.EraColor
import com.eraandroid.emuera.content.ASprite
import com.eraandroid.emuera.content.AppContents
import com.eraandroid.emuera.content.SpriteDrawCommand
import com.eraandroid.emuera.platform.EFont
import com.eraandroid.emuera.platform.ERect
import com.eraandroid.emuera.platform.Platform

/** System.Drawing.FontStyle 互換フラグ */
object FontStyle {
    const val Regular = 0
    const val Bold = 1
    const val Italic = 2
    const val Underline = 4
    const val Strikeout = 8
}

enum class DisplayLineLastState { None, Normal, Selected, BackLog }
enum class DisplayLineAlignment { LEFT, CENTER, RIGHT }
enum class ConsoleRedraw { None, Normal }

/** UI 側が実装する描画先 (座標は Emuera の仮想ピクセル) */
interface ConsoleCanvas {
    fun drawText(text: String, x: Int, y: Int, font: EFont, argb: Int)
    fun fillRect(rect: ERect, argb: Int)
    fun drawSprite(cmd: SpriteDrawCommand)
}

data class StringStyle(
    val color: EraColor,
    val colorChanged: Boolean,
    val buttonColor: EraColor,
    val fontStyle: Int,
    private val fontnameIn: String?,
) {
    val fontname: String = if (fontnameIn.isNullOrEmpty()) Config.FontName else fontnameIn

    constructor(color: EraColor, fontStyle: Int, fontname: String?) : this(color, false, Config.FocusColor, fontStyle, fontname)

    override fun equals(other: Any?): Boolean = other is StringStyle && color == other.color && buttonColor == other.buttonColor &&
        colorChanged == other.colorChanged && fontStyle == other.fontStyle && fontname.equals(other.fontname, ignoreCase = true)

    override fun hashCode(): Int = color.hashCode() xor buttonColor.hashCode() xor colorChanged.hashCode() xor fontStyle xor fontname.lowercase().hashCode()
}

/** 文字幅の計測 */
class StringMeasure {
    fun getDisplayLength(sIn: String?, font: EFont): Int {
        if (sIn.isNullOrEmpty()) return 0
        val s = if (sIn.contains('\t')) sIn.replace("\t", "        ") else sIn
        return Platform.graphics.measureText(s, font)
    }
}

internal fun configFont(): EFont = EFont(Config.FontName, Config.FontSize, FontStyle.Regular)

abstract class AConsoleDisplayPart {
    var error = false
        protected set
    var str: String = ""
        protected set
    var altText: String? = null
        protected set
    var pointX = 0
    var xsubPixel = 0f
    var widthF = 0f
    var width = 0
    open val top: Int get() = 0
    open val bottom: Int get() = Config.FontSize
    abstract val canDivide: Boolean
    abstract fun drawTo(canvas: ConsoleCanvas, pointY: Int, isSelecting: Boolean, isBackLog: Boolean)
    abstract fun setWidth(sm: StringMeasure, subPixel: Float)
    override fun toString(): String = str
}

abstract class AConsoleColoredPart : AConsoleDisplayPart() {
    var color: EraColor = Config.ForeColor
        protected set
    var buttonColor: EraColor = Config.FocusColor
        protected set
    var colorChanged = false
        protected set
}

class ConsoleStyledString private constructor() : AConsoleColoredPart() {
    lateinit var font: EFont
        private set
    lateinit var stringStyle: StringStyle
        private set

    constructor(str: String, style: StringStyle) : this() {
        this.str = str
        stringStyle = style
        font = EFont(style.fontname, Config.FontSize, style.fontStyle)
        color = style.color
        buttonColor = style.buttonColor
        colorChanged = style.colorChanged
        if (!colorChanged && color != Config.ForeColor) colorChanged = true
        pointX = -1
        width = -1
    }

    override val canDivide: Boolean get() = true

    fun divideAt(index: Int, sm: StringMeasure): ConsoleStyledString? {
        val ret = divideAt(index) ?: return null
        setWidth(sm, xsubPixel)
        ret.setWidth(sm, xsubPixel)
        return ret
    }

    fun divideAt(index: Int): ConsoleStyledString? {
        if (index <= 0 || index > str.length || error) return null
        val s = str.substring(index)
        str = str.substring(0, index)
        val ret = ConsoleStyledString()
        ret.font = font
        ret.str = s
        ret.color = color
        ret.buttonColor = buttonColor
        ret.colorChanged = colorChanged
        ret.stringStyle = stringStyle
        ret.xsubPixel = xsubPixel
        return ret
    }

    override fun setWidth(sm: StringMeasure, subPixel: Float) {
        if (error) { width = 0; return }
        width = sm.getDisplayLength(str, font)
        xsubPixel = subPixel
    }

    override fun drawTo(canvas: ConsoleCanvas, pointY: Int, isSelecting: Boolean, isBackLog: Boolean) {
        if (error) return
        var c = color
        if (isSelecting) c = buttonColor
        else if (isBackLog && !colorChanged) c = Config.LogColor
        canvas.drawText(str, pointX, pointY, font, c.argb)
    }
}

class ConsoleImagePart(resName: String?, resNameb: String?, rawHeight: Int, rawWidth: Int, rawYpos: Int) : AConsoleDisplayPart() {
    val resourceName: String = resName ?: ""
    val buttonResourceName: String? = resNameb
    private val cImage: ASprite?
    private var cImageB: ASprite? = null
    private var topV = 0
    private var bottomV = Config.FontSize
    private var destRect = ERect(0, 0, 0, 0)

    init {
        val sb = StringBuilder()
        sb.append("<img src='").append(resourceName)
        if (buttonResourceName != null) sb.append("' srcb='").append(buttonResourceName)
        if (rawHeight != 0) sb.append("' height='").append(rawHeight)
        if (rawWidth != 0) sb.append("' width='").append(rawWidth)
        if (rawYpos != 0) sb.append("' ypos='").append(rawYpos)
        sb.append("'>")
        altText = sb.toString()
        cImage = AppContents.getSprite(resourceName)
        if (cImage == null) {
            str = altText!!
        } else {
            var height = if (rawHeight == 0) Config.FontSize else Config.FontSize * rawHeight / 100
            if (rawWidth == 0) {
                width = cImage.destBaseSize.width * height / cImage.destBaseSize.height
                xsubPixel = cImage.destBaseSize.width.toFloat() * height / cImage.destBaseSize.height - width
            } else {
                width = Config.FontSize * rawWidth / 100
                xsubPixel = Config.FontSize.toFloat() * rawWidth / 100f - width
            }
            topV = rawYpos * Config.FontSize / 100
            destRect = ERect(0, topV, width, height)
            if (destRect.width < 0) { destRect.x = -destRect.width; width = -destRect.width }
            if (destRect.height < 0) { destRect.y = destRect.y - destRect.height; height = -destRect.height }
            bottomV = topV + height
            if (buttonResourceName != null) cImageB = AppContents.getSprite(buttonResourceName)
        }
    }

    override val top: Int get() = topV
    override val bottom: Int get() = bottomV
    override val canDivide: Boolean get() = false

    override fun setWidth(sm: StringMeasure, subPixel: Float) {
        if (error) { width = 0; return }
        if (cImage != null) return
        width = sm.getDisplayLength(str, configFont())
        xsubPixel = subPixel
    }

    override fun toString(): String = altText ?: ""

    override fun drawTo(canvas: ConsoleCanvas, pointY: Int, isSelecting: Boolean, isBackLog: Boolean) {
        if (error) return
        var img = cImage
        if (isSelecting && cImageB != null) img = cImageB
        if (img != null && img.isCreated) {
            val rect = destRect.copy()
            rect.x = destRect.x + pointX + Config.DrawingParam_ShapePositionShift
            rect.y = destRect.y + pointY
            img.drawCommand(rect)?.let { canvas.drawSprite(it) }
        } else canvas.drawText(altText ?: "", pointX, pointY, configFont(), Config.ForeColor.argb)
    }

    /** アニメーションスプライトを含むか (UI が定期再描画を行う判断用) */
    val isAnimated: Boolean get() = (cImage as? com.eraandroid.emuera.content.SpriteAnime)?.isAnimating == true
}

abstract class ConsoleShapePart : AConsoleColoredPart() {
    override val canDivide: Boolean get() = false
    override fun toString(): String = altText ?: ""

    companion object {
        fun createShape(shapeType: String, param: IntArray, color: EraColor, bcolor: EraColor, colorchangedIn: Boolean): ConsoleShapePart {
            val type = shapeType.lowercase()
            val colorchanged = colorchangedIn || color != Config.ForeColor
            val sb = StringBuilder()
            sb.append("<shape type='").append(type).append("' param='")
            sb.append(param.joinToString(", ")).append("'")
            if (colorchanged) sb.append(" color='").append(HtmlManager.getColorToString(color)).append("'")
            if (bcolor != Config.FocusColor) sb.append(" bcolor='").append(HtmlManager.getColorToString(bcolor)).append("'")
            sb.append(">")
            val lineHeight = Config.FontSize
            val pp = FloatArray(param.size) { param[it].toFloat() * lineHeight / 100f }
            var ret: ConsoleShapePart? = null
            when (type) {
                "space" -> if (pp.size == 1 && pp[0] >= 0) ret = ConsoleSpacePart(pp[0])
                "rect" -> {
                    if (pp.size == 1 && pp[0] > 0) ret = ConsoleRectangleShapePart(0f, 0f, pp[0], lineHeight.toFloat())
                    else if (pp.size == 4 && pp[0] >= 0 && pp[2] > 0 && pp[3] > 0) ret = ConsoleRectangleShapePart(pp[0], pp[1], pp[2], pp[3])
                }
            }
            val r = ret ?: ConsoleErrorShapePart(sb.toString())
            r.altText = sb.toString()
            r.color = color
            r.buttonColor = bcolor
            r.colorChanged = colorchanged
            return r
        }
    }
}

class ConsoleRectangleShapePart(private val rx: Float, ry: Float, rw: Float, rh: Float) : ConsoleShapePart() {
    private val rect = ERect(0, ry.toInt(), 0, rh.toInt())
    private val topV: Int
    private val bottomV: Int
    private var visible = false

    init {
        str = ""
        widthF = rx + rw
        if (rect.height == 0 && rh >= 0.001f) rect.height = 1
        topV = minOf(0, rect.y)
        bottomV = maxOf(Config.FontSize, rect.y + rect.height)
    }

    override val top: Int get() = topV
    override val bottom: Int get() = bottomV

    override fun drawTo(canvas: ConsoleCanvas, pointY: Int, isSelecting: Boolean, isBackLog: Boolean) {
        if (!visible) return
        val t = rect.copy(x = rect.x + pointX, y = rect.y + pointY)
        canvas.fillRect(t, (if (isSelecting) buttonColor else color).argb)
    }

    override fun setWidth(sm: StringMeasure, subPixel: Float) {
        val widF = subPixel + widthF
        width = widF.toInt()
        xsubPixel = widF - width
        rect.x = (subPixel + rx).toInt()
        rect.width = width - rect.x
        rect.x += Config.DrawingParam_ShapePositionShift
        visible = rect.x >= 0 && rect.width > 0
    }
}

class ConsoleSpacePart(w: Float) : ConsoleShapePart() {
    init { str = ""; widthF = w }
    override fun drawTo(canvas: ConsoleCanvas, pointY: Int, isSelecting: Boolean, isBackLog: Boolean) {}
    override fun setWidth(sm: StringMeasure, subPixel: Float) {
        val widF = subPixel + widthF
        width = widF.toInt()
        xsubPixel = widF - width
    }
}

class ConsoleErrorShapePart(errMes: String) : ConsoleShapePart() {
    init { str = errMes; altText = errMes }
    override fun drawTo(canvas: ConsoleCanvas, pointY: Int, isSelecting: Boolean, isBackLog: Boolean) =
        canvas.drawText(str, pointX, pointY, configFont(), Config.ForeColor.argb)
    override fun setWidth(sm: StringMeasure, subPixel: Float) {
        if (error) { width = 0; return }
        width = sm.getDisplayLength(str, configFont())
        xsubPixel = subPixel
    }
}
