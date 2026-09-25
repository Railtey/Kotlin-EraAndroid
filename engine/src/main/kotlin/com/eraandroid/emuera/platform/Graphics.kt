package com.eraandroid.emuera.platform

/** 整数矩形 (System.Drawing.Rectangle 相当) */
data class ERect(var x: Int, var y: Int, var width: Int, var height: Int) {
    val left: Int get() = x
    val top: Int get() = y
    val right: Int get() = x + width
    val bottom: Int get() = y + height
    val isEmpty: Boolean get() = width == 0 && height == 0 && x == 0 && y == 0
    fun intersectsWith(r: ERect): Boolean = r.x < x + width && x < r.x + r.width && r.y < y + height && y < r.y + r.height
    fun contains(px: Int, py: Int): Boolean = px >= x && px < x + width && py >= y && py < y + height

    companion object {
        fun intersect(a: ERect, b: ERect): ERect {
            val x1 = maxOf(a.x, b.x)
            val x2 = minOf(a.x + a.width, b.x + b.width)
            val y1 = maxOf(a.y, b.y)
            val y2 = minOf(a.y + a.height, b.y + b.height)
            if (x2 >= x1 && y2 >= y1) return ERect(x1, y1, x2 - x1, y2 - y1)
            return ERect(0, 0, 0, 0)
        }
    }
}

data class EPoint(var x: Int = 0, var y: Int = 0) {
    val isEmpty: Boolean get() = x == 0 && y == 0
    fun offset(p: EPoint) { x += p.x; y += p.y }
}

data class ESize(var width: Int, var height: Int)

/** フォント指定 */
data class EFont(val name: String, val sizePx: Int, val style: Int = 0)

/**
 * ARGB (非乗算済み) のソフトウェアビットマップ。Emuera の Bitmap(Format32bppArgb) 相当。
 * 描画処理は全て純 Kotlin で行い、ファイル入出力と文字描画だけ [PlatformGraphics] に委譲する。
 */
class Raster(val width: Int, val height: Int, val pixels: IntArray = IntArray(width * height)) {
    /** 内容が変わるたびに増える (UI 側のキャッシュ判定用) */
    @Volatile var version: Long = 0
        private set

    fun touch() { version++ }

    fun getPixel(x: Int, y: Int): Int = pixels[y * width + x]
    fun setPixel(x: Int, y: Int, argb: Int) { pixels[y * width + x] = argb; touch() }

    fun clear(argb: Int) { pixels.fill(argb); touch() }

    fun copy(): Raster = Raster(width, height, pixels.clone())

    /** SourceOver で矩形を塗る */
    fun fillRect(rect: ERect, argb: Int) {
        val x1 = maxOf(0, rect.x); val y1 = maxOf(0, rect.y)
        val x2 = minOf(width, rect.x + rect.width); val y2 = minOf(height, rect.y + rect.height)
        val a = argb ushr 24
        for (y in y1 until y2) {
            val row = y * width
            for (x in x1 until x2) {
                pixels[row + x] = if (a == 255) argb else blend(pixels[row + x], argb)
            }
        }
        touch()
    }

    /** 1px 幅以上の枠線 */
    fun drawRect(rect: ERect, argb: Int, penWidth: Int) {
        val w = maxOf(1, penWidth)
        fillRect(ERect(rect.x, rect.y, rect.width + 1, w), argb)
        fillRect(ERect(rect.x, rect.y + rect.height - w + 1, rect.width + 1, w), argb)
        fillRect(ERect(rect.x, rect.y + w, w, rect.height - 2 * w + 1), argb)
        fillRect(ERect(rect.x + rect.width - w + 1, rect.y + w, w, rect.height - 2 * w + 1), argb)
    }

    /**
     * src の srcRect を destRect に拡大縮小して SourceOver 描画する (幅/高さが負なら反転)。
     * colorMatrix は GDI+ ColorMatrix (5x5, 行ベクトル [r g b a 1] に右から掛ける)。
     */
    fun drawImage(src: Raster, destRect: ERect, srcRect: ERect, colorMatrix: Array<FloatArray>? = null, smooth: Boolean = true) {
        if (destRect.width == 0 || destRect.height == 0 || srcRect.width == 0 || srcRect.height == 0) return
        val dw = kotlin.math.abs(destRect.width); val dh = kotlin.math.abs(destRect.height)
        val dx0 = if (destRect.width < 0) destRect.x + destRect.width else destRect.x
        val dy0 = if (destRect.height < 0) destRect.y + destRect.height else destRect.y
        val flipX = (destRect.width < 0) != (srcRect.width < 0)
        val flipY = (destRect.height < 0) != (srcRect.height < 0)
        val sw = kotlin.math.abs(srcRect.width); val sh = kotlin.math.abs(srcRect.height)
        val sx0 = if (srcRect.width < 0) srcRect.x + srcRect.width else srcRect.x
        val sy0 = if (srcRect.height < 0) srcRect.y + srcRect.height else srcRect.y
        val scaleX = sw.toDouble() / dw
        val scaleY = sh.toDouble() / dh
        val same = dw == sw && dh == sh
        val xStart = maxOf(0, dx0); val xEnd = minOf(width, dx0 + dw)
        val yStart = maxOf(0, dy0); val yEnd = minOf(height, dy0 + dh)
        for (y in yStart until yEnd) {
            var ly = y - dy0
            if (flipY) ly = dh - 1 - ly
            val row = y * width
            for (x in xStart until xEnd) {
                var lx = x - dx0
                if (flipX) lx = dw - 1 - lx
                var c: Int
                if (same || !smooth) {
                    val sx = sx0 + if (same) lx else (lx * scaleX).toInt()
                    val sy = sy0 + if (same) ly else (ly * scaleY).toInt()
                    if (sx < 0 || sy < 0 || sx >= src.width || sy >= src.height) continue
                    c = src.pixels[sy * src.width + sx]
                } else {
                    c = src.sampleBilinear(sx0 + (lx + 0.5) * scaleX - 0.5, sy0 + (ly + 0.5) * scaleY - 0.5, sx0, sy0, sx0 + sw - 1, sy0 + sh - 1)
                }
                if (colorMatrix != null) c = applyMatrix(c, colorMatrix)
                val a = c ushr 24
                if (a == 0) continue
                pixels[row + x] = if (a == 255) c else blend(pixels[row + x], c)
            }
        }
        touch()
    }

    private fun sampleBilinear(fx: Double, fy: Double, minX: Int, minY: Int, maxX: Int, maxY: Int): Int {
        val lx = maxOf(0, minX); val ly = maxOf(0, minY)
        val hx = minOf(width - 1, maxX); val hy = minOf(height - 1, maxY)
        if (hx < lx || hy < ly) return 0
        val cx = fx.coerceIn(lx.toDouble(), hx.toDouble())
        val cy = fy.coerceIn(ly.toDouble(), hy.toDouble())
        val x0 = cx.toInt(); val y0 = cy.toInt()
        val x1 = minOf(x0 + 1, hx); val y1 = minOf(y0 + 1, hy)
        val tx = cx - x0; val ty = cy - y0
        val c00 = pixels[y0 * width + x0]; val c10 = pixels[y0 * width + x1]
        val c01 = pixels[y1 * width + x0]; val c11 = pixels[y1 * width + x1]
        // 乗算済みで補間して透明部分の色が滲まないようにする
        fun ch(c: Int, s: Int) = (c ushr s) and 0xFF
        val w00 = (1 - tx) * (1 - ty); val w10 = tx * (1 - ty); val w01 = (1 - tx) * ty; val w11 = tx * ty
        val a00 = ch(c00, 24); val a10 = ch(c10, 24); val a01 = ch(c01, 24); val a11 = ch(c11, 24)
        val a = a00 * w00 + a10 * w10 + a01 * w01 + a11 * w11
        if (a <= 0.0) return 0
        fun comp(s: Int): Int {
            val v = (ch(c00, s) * a00 * w00 + ch(c10, s) * a10 * w10 + ch(c01, s) * a01 * w01 + ch(c11, s) * a11 * w11) / a
            return v.toInt().coerceIn(0, 255)
        }
        return ((a + 0.5).toInt().coerceIn(0, 255) shl 24) or (comp(16) shl 16) or (comp(8) shl 8) or comp(0)
    }

    companion object {
        /** dst に src を SourceOver 合成 (いずれも非乗算 ARGB) */
        fun blend(dst: Int, src: Int): Int {
            val sa = src ushr 24
            if (sa == 255) return src
            if (sa == 0) return dst
            val da = dst ushr 24
            val outA = sa + da * (255 - sa) / 255
            if (outA == 0) return 0
            fun c(s: Int): Int {
                val sc = (src ushr s) and 0xFF
                val dc = (dst ushr s) and 0xFF
                return ((sc * sa + dc * da * (255 - sa) / 255) / outA).coerceIn(0, 255)
            }
            return (outA shl 24) or (c(16) shl 16) or (c(8) shl 8) or c(0)
        }

        fun applyMatrix(c: Int, m: Array<FloatArray>): Int {
            val r = ((c ushr 16) and 0xFF) / 255f
            val g = ((c ushr 8) and 0xFF) / 255f
            val b = (c and 0xFF) / 255f
            val a = (c ushr 24) / 255f
            fun out(j: Int): Int = ((r * m[0][j] + g * m[1][j] + b * m[2][j] + a * m[3][j] + m[4][j]) * 255f + 0.5f).toInt().coerceIn(0, 255)
            return (out(3) shl 24) or (out(0) shl 16) or (out(1) shl 8) or out(2)
        }
    }
}

/** プラットフォーム依存部分 (画像ファイルの読み書き・文字描画) */
interface PlatformGraphics {
    /** 画像ファイル (png/bmp/jpg/gif/webp) を読み込む。失敗時 null */
    fun loadImage(path: String): Raster?
    /** PNG で保存 */
    fun saveImage(raster: Raster, path: String): Boolean
    /** 文字列を描画。width/height が正ならその矩形内で折り返す */
    fun drawText(raster: Raster, text: String, font: EFont, argb: Int, x: Int, y: Int, width: Int = -1, height: Int = -1)
    /** 文字列の描画幅 (px) */
    fun measureText(text: String, font: EFont): Int
    fun isFontInstalled(name: String): Boolean
}

/** 何も描画しない既定実装 (テストや初期化前用) */
open class NullGraphics : PlatformGraphics {
    override fun loadImage(path: String): Raster? = null
    override fun saveImage(raster: Raster, path: String): Boolean = false
    override fun drawText(raster: Raster, text: String, font: EFont, argb: Int, x: Int, y: Int, width: Int, height: Int) {}
    override fun measureText(text: String, font: EFont): Int {
        var w = 0
        for (c in text) w += if (c.code < 0x80 || c in '｡'..'ﾟ') font.sizePx / 2 else font.sizePx
        return w
    }
    override fun isFontInstalled(name: String): Boolean = false
}

object Platform {
    @JvmStatic var graphics: PlatformGraphics = NullGraphics()

    /** WinmmTimer.CurrentFrameTime 相当 (アニメーション用) */
    @JvmStatic var currentFrameTime: Long = 0
    fun updateFrameTime() { currentFrameTime = System.currentTimeMillis() and 0xFFFFFFFFL }

    /** Win32 GetKeyState 相当 (最上位ビット=押下中, 最下位ビット=トグル)。既定は常に 0 */
    @JvmStatic var keyState: (Int) -> Short = { 0 }
}
