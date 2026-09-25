package com.eraandroid.emuera.ui

import com.eraandroid.emuera.config.Config
import com.eraandroid.emuera.content.SpriteDrawCommand
import com.eraandroid.emuera.gameproc.InputType
import com.eraandroid.emuera.gameview.AConsoleColoredPart
import com.eraandroid.emuera.gameview.AConsoleDisplayPart
import com.eraandroid.emuera.gameview.ConsoleButtonString
import com.eraandroid.emuera.gameview.ConsoleCanvas
import com.eraandroid.emuera.gameview.ConsoleDisplayLine
import com.eraandroid.emuera.gameview.ConsoleImagePart
import com.eraandroid.emuera.gameview.ConsoleSpacePart
import com.eraandroid.emuera.gameview.ConsoleState
import com.eraandroid.emuera.gameview.ConsoleStyledString
import com.eraandroid.emuera.gameview.DisplayLineAlignment
import com.eraandroid.emuera.gameview.EmueraConsole
import com.eraandroid.emuera.gameview.FontStyle
import com.eraandroid.emuera.platform.EFont
import com.eraandroid.emuera.platform.ERect
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

/**
 * 콘솔 출력을 "줄 목록" UI 용 모델로 바꾼다 (안드로이드 앱의 TextLine/TextSpan 에 대응).
 * 순수 코틀린이라 JVM 테스트로 화면에 무엇이 보일지 확인할 수 있다.
 */
enum class UiAlign { LEFT, CENTER, RIGHT }

class UiSpan(
    val text: String,
    val color: Int,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isButton: Boolean = false,
    val buttonValue: Long = 0L,
    /** 이미지 (없으면 null). 크기는 글자 크기 배수 */
    val image: SpriteDrawCommand? = null,
    val imageWidthEm: Float = 0f,
    val imageHeightEm: Float = 0f,
)

class UiLine(val spans: List<UiSpan>, val align: UiAlign = UiAlign.LEFT, val isDrawLine: Boolean = false) {
    val text: String get() = spans.joinToString("") { it.text }
}

class UiSnapshot(
    val lines: List<UiLine>,
    /** 이 줄부터 버튼을 누를 수 있다 (Emuera 의 버튼 세대) */
    val activeLineIndex: Int,
    val waitingValue: Boolean,
    val waitingNumber: Boolean,
    val waitingKey: Boolean,
    val bgColor: Int,
    val foreColor: Int,
    val loading: Boolean,
    val hasDefaultValue: Boolean,
)

class UiConverter {
    private class Cached(val buttons: Array<ConsoleButtonString>, val line: UiLine)
    private val cache = WeakHashMap<ConsoleDisplayLine, Cached>()

    // 문자열 값 버튼 (PRINTBUTTON "..", "문자열") 은 숫자 대신 가상의 번호로 구분한다
    private val stringButtonIds = ConcurrentHashMap<String, Long>()
    private val stringButtonValues = ConcurrentHashMap<Long, String>()

    /** 화면 청소: 이 개수만큼 앞 줄을 숨긴다 */
    @Volatile var hiddenLineCount = 0
    private var lastLineCount = 0

    fun reset() {
        synchronized(cache) { cache.clear() }
        stringButtonIds.clear()
        stringButtonValues.clear()
        hiddenLineCount = 0
        lastLineCount = 0
    }

    fun stringValueOf(id: Long): String? = stringButtonValues[id]

    /** 엔진 스레드에서 호출 */
    fun snapshot(c: EmueraConsole): UiSnapshot {
        val snap = c.snapshot()
        val all = snap.lines
        if (all.size < lastLineCount) hiddenLineCount = 0
        lastLineCount = all.size
        val start = hiddenLineCount.coerceIn(0, all.size)
        val bar = c.getDefStBar()
        val lines = ArrayList<UiLine>(all.size - start)
        var active = -1
        for (i in start until all.size) {
            val line = all[i]
            lines.add(convert(line, bar))
            if (active < 0 && line.buttons.any { it.isButton && c.canSelect(it) }) active = i - start
        }
        val req = c.currentInputRequest
        val state = c.consoleState
        val waitingValue = req != null && (req.inputType == InputType.IntValue || req.inputType == InputType.StrValue)
        val waitingKey = state == ConsoleState.Error || state == ConsoleState.Quit ||
            (req != null && (req.inputType == InputType.EnterKey || req.inputType == InputType.AnyKey || req.inputType == InputType.PrimitiveMouseKey))
        return UiSnapshot(
            lines = lines,
            activeLineIndex = if (active >= 0) active else lines.size,
            waitingValue = waitingValue,
            waitingNumber = req?.inputType == InputType.IntValue,
            waitingKey = waitingKey,
            bgColor = snap.bgColor.argb,
            foreColor = Config.ForeColor.argb,
            loading = state == ConsoleState.Initializing,
            hasDefaultValue = req?.hasDefValue == true,
        )
    }

    fun convert(line: ConsoleDisplayLine, bar: String?): UiLine {
        synchronized(cache) {
            val cached = cache[line]
            if (cached != null && cached.buttons === line.buttons) return cached.line
        }
        val text = line.toString()
        val result = if (bar != null && text.isNotEmpty() && text == bar && line.buttons.none { it.isButton }) {
            UiLine(listOf(UiSpan(text, 0xFF888888.toInt())), UiAlign.LEFT, isDrawLine = true)
        } else {
            val spans = ArrayList<UiSpan>()
            for (b in line.buttons) {
                val value = if (b.isButton) (if (b.isInteger) b.input else buttonIdFor(b)) else 0L
                for (part in b.strArray) convertPart(part, b.isButton, value)?.let { spans.add(it) }
            }
            val align = when (line.align) {
                DisplayLineAlignment.CENTER -> UiAlign.CENTER
                DisplayLineAlignment.RIGHT -> UiAlign.RIGHT
                else -> UiAlign.LEFT
            }
            UiLine(spans, align)
        }
        synchronized(cache) { cache[line] = Cached(line.buttons, result) }
        return result
    }

    private fun convertPart(part: AConsoleDisplayPart, isButton: Boolean, value: Long): UiSpan? = when (part) {
        is ConsoleStyledString -> {
            val style = part.stringStyle.fontStyle
            UiSpan(
                part.str, part.color.argb,
                isBold = style and FontStyle.Bold != 0,
                isItalic = style and FontStyle.Italic != 0,
                isButton = isButton, buttonValue = value,
            )
        }
        is ConsoleImagePart -> imageSpan(part, isButton, value)
        is ConsoleSpacePart -> {
            val n = (part.width / maxOf(1, Config.FontSize / 2)).coerceIn(0, 400)
            UiSpan(" ".repeat(n), Config.ForeColor.argb, isButton = isButton, buttonValue = value)
        }
        is AConsoleColoredPart -> null // 도형(PRINT_RECT 등)은 줄 목록 화면에서는 생략
        else -> null
    }

    private fun imageSpan(part: ConsoleImagePart, isButton: Boolean, value: Long): UiSpan {
        var captured: SpriteDrawCommand? = null
        part.drawTo(object : ConsoleCanvas {
            override fun drawText(text: String, x: Int, y: Int, font: EFont, argb: Int) {}
            override fun fillRect(rect: ERect, argb: Int) {}
            override fun drawSprite(cmd: SpriteDrawCommand) { captured = cmd }
        }, 0, false, false)
        val cmd = captured ?: return UiSpan(part.toString(), Config.ForeColor.argb, isButton = isButton, buttonValue = value)
        val fs = Config.FontSize.toFloat()
        return UiSpan(
            "", Config.ForeColor.argb, isButton = isButton, buttonValue = value,
            image = cmd,
            imageWidthEm = kotlin.math.abs(cmd.dest.width) / fs,
            imageHeightEm = kotlin.math.abs(cmd.dest.height) / fs,
        )
    }

    private fun buttonIdFor(b: ConsoleButtonString): Long {
        val s = b.inputs ?: ""
        return stringButtonIds.getOrPut(s) {
            val id = Long.MIN_VALUE + stringButtonIds.size
            stringButtonValues[id] = s
            id
        }
    }

    /** UI 에서 버튼(값)이 눌렸을 때. 엔진 스레드에서 호출 */
    fun clickButton(c: EmueraConsole, value: Long) {
        val str = stringButtonValues[value]
        val target = c.snapshot().lines.asReversed().asSequence()
            .flatMap { it.buttons.asSequence() }
            .firstOrNull { b -> c.canSelect(b) && (if (str != null) !b.isInteger && b.inputs == str else b.isInteger && b.input == value) }
        when {
            target != null -> c.clickButton(target)
            c.isWaitingEnterKey || c.isError -> c.pressEnterKey(false, "", true)
            c.isWaitingValue && str == null -> c.pressEnterKey(false, value.toString(), true)
        }
    }
}
