package com.eraandroid.emu

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import com.eraandroid.emuera.gameview.FontStyle
import com.eraandroid.emuera.platform.EFont
import com.eraandroid.emuera.platform.PlatformGraphics
import com.eraandroid.emuera.platform.Raster
import java.io.File
import java.io.FileOutputStream

/**
 * Emuera 의 기본 글꼴(ＭＳ ゴシック)처럼 반각 문자는 글자 크기의 절반, 전각 문자는 글자 크기만큼의
 * 폭을 차지하도록 한 글자씩 칸에 맞춰 그린다. 게임들이 이 폭을 전제로 줄을 맞추기 때문.
 */
object TextCells {
    private val fullWidthLatin = setOf(0xA7, 0xA8, 0xB0, 0xB1, 0xB4, 0xB6, 0xD7, 0xF7)

    fun isHalfWidth(cp: Int): Boolean = when {
        cp < 0x80 -> true
        cp in 0xFF61..0xFF9F -> true // 반각 가타카나
        cp in 0xFFE8..0xFFEE -> true
        cp in 0xA0..0x24F -> cp !in fullWidthLatin // 라틴 확장
        cp == 0x20A9 -> true
        else -> false
    }

    fun cellWidth(cp: Int, size: Int): Int = if (isHalfWidth(cp)) size / 2 else size

    fun measure(text: String, size: Int): Int {
        var w = 0
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            w += cellWidth(cp, size)
            i += Character.charCount(cp)
        }
        return w
    }

    private val typeface: Typeface = Typeface.MONOSPACE
    private val boldTypeface: Typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)

    fun setupPaint(paint: Paint, font: EFont, argb: Int) {
        paint.isAntiAlias = true
        paint.textSize = font.sizePx.toFloat()
        paint.typeface = if (font.style and FontStyle.Bold != 0) boldTypeface else typeface
        paint.textSkewX = if (font.style and FontStyle.Italic != 0) -0.25f else 0f
        paint.color = argb
        paint.style = Paint.Style.FILL
        paint.isUnderlineText = false
        paint.isStrikeThruText = false
    }

    /** (x, yTop) 을 왼쪽 위로 해서 한 줄을 그린다 */
    fun draw(canvas: Canvas, text: String, x: Float, yTop: Float, font: EFont, argb: Int, paint: Paint) {
        setupPaint(paint, font, argb)
        val size = font.sizePx
        val baseline = yTop + size * 0.86f
        var cx = x
        var i = 0
        val buf = CharArray(2)
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val n = Character.toChars(cp, buf, 0)
            val cell = cellWidth(cp, size).toFloat()
            if (cp != 0x20 && cp != 0x3000 && cp != '\t'.code) {
                val natural = paint.measureText(buf, 0, n)
                if (natural > cell + 0.5f && natural > 0f) {
                    // 칸보다 넓은 글리프는 가로로 줄여서 칸에 맞춘다
                    val sx = cell / natural
                    canvas.save()
                    canvas.scale(sx, 1f, cx, baseline)
                    canvas.drawText(buf, 0, n, cx, baseline, paint)
                    canvas.restore()
                } else {
                    canvas.drawText(buf, 0, n, cx + (cell - natural) / 2f, baseline, paint)
                }
            }
            cx += cell
            i += Character.charCount(cp)
        }
        if (font.style and (FontStyle.Underline or FontStyle.Strikeout) != 0) {
            val stroke = maxOf(1f, size / 16f)
            if (font.style and FontStyle.Underline != 0)
                canvas.drawRect(x, yTop + size - stroke, cx, yTop + size, paint)
            if (font.style and FontStyle.Strikeout != 0)
                canvas.drawRect(x, yTop + size / 2f - stroke / 2f, cx, yTop + size / 2f + stroke / 2f, paint)
        }
    }
}

/** 엔진의 이미지 입출력과 글자 그리기를 안드로이드 API 로 구현 */
class AndroidGraphics : PlatformGraphics {
    private val paint = Paint()

    override fun loadImage(path: String): Raster? {
        return try {
            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inPremultiplied = false
            }
            val bmp = BitmapFactory.decodeFile(path, opts) ?: return null
            val w = bmp.width
            val h = bmp.height
            val px = IntArray(w * h)
            bmp.getPixels(px, 0, w, 0, 0, w, h)
            bmp.recycle()
            Raster(w, h, px)
        } catch (e: Throwable) {
            null
        }
    }

    override fun saveImage(raster: Raster, path: String): Boolean {
        return try {
            val bmp = Bitmap.createBitmap(raster.pixels, raster.width, raster.height, Bitmap.Config.ARGB_8888)
            File(path).parentFile?.mkdirs()
            FileOutputStream(path).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bmp.recycle()
            true
        } catch (e: Throwable) {
            false
        }
    }

    @Synchronized
    override fun drawText(raster: Raster, text: String, font: EFont, argb: Int, x: Int, y: Int, width: Int, height: Int) {
        if (text.isEmpty()) return
        val size = font.sizePx
        val lines = if (width > 0) wrap(text, size, width) else text.split('\n')
        val textW = lines.maxOf { TextCells.measure(it, size) }
        val textH = lines.size * size + size / 4
        // 글자가 닿는 부분만 비트맵으로 꺼내서 그린 뒤 되돌린다
        val rx1 = maxOf(0, x)
        val ry1 = maxOf(0, y)
        var rx2 = minOf(raster.width, x + textW + size)
        var ry2 = minOf(raster.height, y + textH)
        if (width > 0) rx2 = minOf(rx2, x + width)
        if (height > 0) ry2 = minOf(ry2, y + height)
        val rw = rx2 - rx1
        val rh = ry2 - ry1
        if (rw <= 0 || rh <= 0) return
        val region = IntArray(rw * rh)
        for (row in 0 until rh) System.arraycopy(raster.pixels, (ry1 + row) * raster.width + rx1, region, row * rw, rw)
        val bmp = Bitmap.createBitmap(rw, rh, Bitmap.Config.ARGB_8888)
        bmp.setPixels(region, 0, rw, 0, 0, rw, rh)
        val canvas = Canvas(bmp)
        for ((i, line) in lines.withIndex())
            TextCells.draw(canvas, line, (x - rx1).toFloat(), (y - ry1 + i * size).toFloat(), font, argb, paint)
        bmp.getPixels(region, 0, rw, 0, 0, rw, rh)
        bmp.recycle()
        for (row in 0 until rh) System.arraycopy(region, row * rw, raster.pixels, (ry1 + row) * raster.width + rx1, rw)
        raster.touch()
    }

    private fun wrap(text: String, size: Int, width: Int): List<String> {
        val out = ArrayList<String>()
        for (para in text.split('\n')) {
            val sb = StringBuilder()
            var w = 0
            var i = 0
            while (i < para.length) {
                val cp = para.codePointAt(i)
                val cw = TextCells.cellWidth(cp, size)
                if (w + cw > width && sb.isNotEmpty()) {
                    out.add(sb.toString()); sb.setLength(0); w = 0
                }
                sb.appendCodePoint(cp)
                w += cw
                i += Character.charCount(cp)
            }
            out.add(sb.toString())
        }
        return out
    }

    override fun measureText(text: String, font: EFont): Int = TextCells.measure(text, font.sizePx)

    override fun isFontInstalled(name: String): Boolean = true
}
