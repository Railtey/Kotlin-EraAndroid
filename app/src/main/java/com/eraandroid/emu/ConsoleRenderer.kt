package com.eraandroid.emu

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.eraandroid.emuera.config.Config
import com.eraandroid.emuera.content.SpriteDrawCommand
import com.eraandroid.emuera.gameview.ConsoleButtonString
import com.eraandroid.emuera.gameview.ConsoleCanvas
import com.eraandroid.emuera.gameview.EmueraConsole
import com.eraandroid.emuera.platform.EFont
import com.eraandroid.emuera.platform.EPoint
import com.eraandroid.emuera.platform.ERect
import com.eraandroid.emuera.platform.Platform
import com.eraandroid.emuera.platform.Raster
import java.util.WeakHashMap

/**
 * 엔진 콘솔의 스냅샷을 안드로이드 Canvas 에 그린다 (Emuera 의 EmueraConsole.OnPaint 에 해당).
 * 좌표는 Emuera 가상 픽셀이며, 화면 너비가 Config.WindowX 가 되도록 확대한다.
 */
class ConsoleRenderer {
    private val paint = Paint()
    private val bmpPaint = Paint().apply { isFilterBitmap = true; isAntiAlias = true }

    private class CachedBitmap(val version: Long, val bitmap: Bitmap)
    private val bitmapCache = WeakHashMap<Raster, CachedBitmap>()

    /** 화면 너비(px) → 가상 픽셀 배율 */
    fun scaleFor(viewWidth: Int): Float = viewWidth.toFloat() / maxOf(1, Config.WindowX)

    private fun bitmapOf(r: Raster): Bitmap {
        val c = bitmapCache[r]
        if (c != null && c.version == r.version && c.bitmap.width == r.width && c.bitmap.height == r.height) return c.bitmap
        val bmp = if (c != null && c.bitmap.width == r.width && c.bitmap.height == r.height) c.bitmap
        else Bitmap.createBitmap(r.width, r.height, Bitmap.Config.ARGB_8888)
        bmp.setPixels(r.pixels, 0, r.width, 0, 0, r.width, r.height)
        bitmapCache[r] = CachedBitmap(r.version, bmp)
        return bmp
    }

    private inner class AndroidConsoleCanvas(val canvas: Canvas) : ConsoleCanvas {
        override fun drawText(text: String, x: Int, y: Int, font: EFont, argb: Int) {
            TextCells.draw(canvas, text, x.toFloat(), y.toFloat(), font, argb, paint)
        }

        override fun fillRect(rect: ERect, argb: Int) {
            paint.style = Paint.Style.FILL
            paint.color = argb
            canvas.drawRect(rect.x.toFloat(), rect.y.toFloat(), rect.right.toFloat(), rect.bottom.toFloat(), paint)
        }

        override fun drawSprite(cmd: SpriteDrawCommand) = drawCommand(canvas, cmd)
    }

    private fun drawCommand(canvas: Canvas, cmd: SpriteDrawCommand) {
        val s = cmd.src
        val d = cmd.dest
        if (s.width == 0 || s.height == 0 || d.width == 0 || d.height == 0) return
        val bmp = bitmapOf(cmd.raster)
        val src = Rect(
            minOf(s.x, s.x + s.width), minOf(s.y, s.y + s.height),
            maxOf(s.x, s.x + s.width), maxOf(s.y, s.y + s.height),
        )
        val dst = RectF(
            minOf(d.x, d.x + d.width).toFloat(), minOf(d.y, d.y + d.height).toFloat(),
            maxOf(d.x, d.x + d.width).toFloat(), maxOf(d.y, d.y + d.height).toFloat(),
        )
        val flipX = (s.width < 0) != (d.width < 0)
        val flipY = (s.height < 0) != (d.height < 0)
        if (flipX || flipY) {
            canvas.save()
            canvas.scale(if (flipX) -1f else 1f, if (flipY) -1f else 1f, dst.centerX(), dst.centerY())
            canvas.drawBitmap(bmp, src, dst, bmpPaint)
            canvas.restore()
        } else {
            canvas.drawBitmap(bmp, src, dst, bmpPaint)
        }
    }

    /**
     * 그리기. [scrollBack] 은 맨 아래에서 몇 줄 위로 스크롤했는지.
     */
    fun draw(canvas: Canvas, snap: EmueraConsole.Snapshot, viewWidth: Int, viewHeight: Int, scrollBack: Int) {
        Platform.updateFrameTime()
        val scale = scaleFor(viewWidth)
        val vh = (viewHeight / scale).toInt()
        canvas.drawColor(snap.bgColor.argb)
        canvas.save()
        canvas.scale(scale, scale)
        val cc = AndroidConsoleCanvas(canvas)
        val lines = snap.lines
        val lh = Config.LineHeight
        val isBackLog = scrollBack > 0
        var pointY = vh - lh
        val bottomLineNo = lines.size - 1 - scrollBack
        var topLineNo = bottomLineNo - (pointY / lh + 1)
        if (topLineNo < 0) topLineNo = 0
        pointY -= (bottomLineNo - topLineNo) * lh
        for (cbg in snap.cbg) {
            if (cbg.zdepth == 0) {
                var y = pointY
                for (i in topLineNo..bottomLineNo) {
                    if (i in lines.indices) lines[i].drawTo(cc, y, isBackLog)
                    y += lh
                }
                continue
            }
            var img = cbg.img
            if (cbg.isButton && cbg.buttonValue == snap.selectingCBGButton) img = cbg.imgB
            if (img == null || !img.isCreated) continue
            img.drawCommand(EPoint(cbg.x, cbg.y + vh - img.destBaseSize.height))?.let { drawCommand(canvas, it) }
        }
        canvas.restore()
    }

    /** 터치 위치(px)에 있는 버튼 찾기 (Emuera 의 MoveMouse 판정과 같다) */
    fun hitTest(snap: EmueraConsole.Snapshot, xPx: Float, yPx: Float, viewWidth: Int, viewHeight: Int, scrollBack: Int): ConsoleButtonString? {
        val scale = scaleFor(viewWidth)
        val vh = (viewHeight / scale).toInt()
        val px = (xPx / scale).toInt()
        val py = (yPx / scale).toInt()
        val lines = snap.lines
        val lh = Config.LineHeight
        val bottomLineNo = lines.size - 1 - scrollBack
        var topLineNo = bottomLineNo - vh / lh
        if (topLineNo < 0) topLineNo = 0
        var rel = py - vh
        var found: ConsoleButtonString? = null
        // 손가락은 마우스보다 부정확하므로 위아래로 조금 여유를 준다
        val slop = lh / 3
        for (i in bottomLineNo downTo topLineNo) {
            rel += lh
            if (i !in lines.indices) continue
            val line = lines[i]
            for (b in line.buttons.indices.reversed()) {
                val button = line.buttons[b]
                if (button.pointX > px || button.pointX + button.width < px) continue
                for (part in button.strArray) {
                    if (part.pointX <= px && part.pointX + part.width >= px && rel >= part.top - slop && rel <= part.bottom + slop) {
                        if (button.isButton) return button
                        if (found == null) found = button
                    }
                }
            }
        }
        return found
    }
}
