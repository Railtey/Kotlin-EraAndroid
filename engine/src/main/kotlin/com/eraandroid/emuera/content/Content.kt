package com.eraandroid.emuera.content

import com.eraandroid.emuera.Program
import com.eraandroid.emuera.config.Config
import com.eraandroid.emuera.gamedata.ParserMediator
import com.eraandroid.emuera.platform.*
import com.eraandroid.emuera.sub.ScriptPosition
import java.io.File

abstract class AContentFile {
    abstract val isCreated: Boolean
    abstract fun dispose()
}

abstract class AbstractImage : AContentFile() {
    var bitmap: Raster? = null

    companion object {
        const val MAX_IMAGESIZE = 8192
    }
}

class ConstImage(val name: String) : AbstractImage() {
    fun createFrom(bmp: Raster) {
        if (bitmap != null) throw IllegalStateException()
        bitmap = bmp
    }
    override fun dispose() { bitmap = null }
    override val isCreated: Boolean get() = bitmap != null
}

abstract class AContentItem protected constructor(val name: String) {
    abstract val isCreated: Boolean
}

/** 描画要求: base 画像の src 矩形を dest 矩形へ */
class SpriteDrawCommand(val raster: Raster, val src: ERect, val dest: ERect)

abstract class ASprite(name: String, size: ESize) : AContentItem(name) {
    val destBaseSize: ESize = ESize(kotlin.math.abs(size.width), kotlin.math.abs(size.height))
    var destBasePosition = EPoint()

    abstract fun spriteGetColor(x: Int, y: Int): Int
    /** offset を左上として等倍描画する時の描画命令 */
    abstract fun drawCommand(offset: EPoint): SpriteDrawCommand?
    /** destRect に拡大縮小描画する時の描画命令 */
    abstract fun drawCommand(destRect: ERect): SpriteDrawCommand?
    abstract fun dispose()

    fun graphicsDraw(g: Raster, offset: EPoint) { drawCommand(offset)?.let { g.drawImage(it.raster, it.dest, it.src) } }
    fun graphicsDraw(g: Raster, destRect: ERect) { drawCommand(destRect)?.let { g.drawImage(it.raster, it.dest, it.src) } }
    fun graphicsDraw(g: Raster, destRect: ERect, cm: Array<FloatArray>) { drawCommand(destRect)?.let { g.drawImage(it.raster, it.dest, it.src, cm) } }

    fun move(point: EPoint) { destBasePosition.offset(point) }
}

abstract class ASpriteSingle(name: String, var baseImage: AbstractImage?, val srcRectangle: ERect) :
    ASprite(name, ESize(srcRectangle.width, srcRectangle.height)) {

    private val bitmap: Raster? get() = baseImage?.let { if (it.isCreated) it.bitmap else null }
    override val isCreated: Boolean get() = baseImage != null && baseImage!!.isCreated

    override fun spriteGetColor(x: Int, y: Int): Int {
        val bmp = bitmap ?: return 0
        val bx = x + srcRectangle.x
        val by = y + srcRectangle.y
        if (bx < 0 || bx >= bmp.width || by < 0 || by >= bmp.height) return 0
        return bmp.getPixel(bx, by)
    }

    override fun dispose() { baseImage = null }

    override fun drawCommand(offset: EPoint): SpriteDrawCommand? {
        val bmp = bitmap ?: return null
        val o = EPoint(offset.x + destBasePosition.x, offset.y + destBasePosition.y)
        return SpriteDrawCommand(bmp, srcRectangle, ERect(o.x, o.y, destBaseSize.width, destBaseSize.height))
    }

    override fun drawCommand(destRect: ERect): SpriteDrawCommand? {
        val bmp = bitmap ?: return null
        val d = destRect.copy()
        if (!destBasePosition.isEmpty) {
            d.x = d.x + destBasePosition.x * d.width / srcRectangle.width
            d.y = d.y + destBasePosition.y * d.height / srcRectangle.height
        }
        return SpriteDrawCommand(bmp, srcRectangle, d)
    }
}

class SpriteG(name: String, gra: GraphicsImage, rect: ERect) : ASpriteSingle(name, gra, rect)

class SpriteF(name: String, image: ConstImage, rect: ERect, pos: EPoint) : ASpriteSingle(name, image, rect) {
    init { destBasePosition = pos }
}

class SpriteAnime(name: String, size: ESize) : ASprite(name, size) {
    private class AnimeFrame {
        var index = 0
        var baseImage: AbstractImage? = null
        var srcRectangle = ERect(0, 0, 0, 0)
        var offset = EPoint()
        var delayTimeMs = 0
        fun normalize(parentSize: ESize) {
            val rect = ERect.intersect(ERect(offset.x, offset.y, srcRectangle.width, srcRectangle.height), ERect(0, 0, parentSize.width, parentSize.height))
            if (rect.width == 0 || rect.height == 0) { baseImage = null; return }
            offset.x = rect.x
            offset.y = rect.y
            srcRectangle.width = rect.width
            srcRectangle.height = rect.height
        }
    }

    private val frameList = ArrayList<AnimeFrame>()
    var totaltime: Long = 0

    fun addFrame(parentImage: AbstractImage, rect: ERect, pos: EPoint, delayIn: Int): Boolean {
        val frame = AnimeFrame()
        frame.index = frameList.size
        frame.baseImage = parentImage
        frame.srcRectangle = rect.copy()
        frame.offset = pos.copy()
        val delay = if (delayIn <= 0) 1 else delayIn
        frame.delayTimeMs = delay
        frame.normalize(destBaseSize)
        totaltime += delay
        frameList.add(frame)
        return true
    }

    private var startTime: Long = -1
    private var lastFrameTime: Long = 0
    private var lastFrame = -1

    fun resetTime() { startTime = -1; lastFrameTime = 0; lastFrame = -1 }

    private fun getCurrentFrame(): AnimeFrame? {
        if (totaltime <= 0 || frameList.isEmpty()) return null
        val now = Platform.currentFrameTime
        if (startTime < 0) {
            startTime = now
            lastFrame = 0
            return frameList[0]
        }
        if (now == lastFrameTime && lastFrame >= 0) return frameList[lastFrame]
        var time = (now - startTime) % totaltime
        if (time < 0) time += totaltime
        for (frame in frameList) {
            time -= frame.delayTimeMs
            if (time <= 0) { lastFrame = frame.index; return frame }
        }
        return frameList.last()
    }

    override val isCreated: Boolean get() = true

    override fun dispose() {
        frameList.clear()
        totaltime = 0
        lastFrameTime = 0
        startTime = -1
        lastFrame = -1
    }

    override fun spriteGetColor(x: Int, y: Int): Int = throw UnsupportedOperationException()

    override fun drawCommand(offset: EPoint): SpriteDrawCommand? {
        val frame = getCurrentFrame() ?: return null
        val bmp = frame.baseImage?.let { if (it.isCreated) it.bitmap else null } ?: return null
        val x = offset.x + destBasePosition.x + frame.offset.x
        val y = offset.y + destBasePosition.y + frame.offset.y
        return SpriteDrawCommand(bmp, frame.srcRectangle, ERect(x, y, frame.srcRectangle.width, frame.srcRectangle.height))
    }

    override fun drawCommand(destRect: ERect): SpriteDrawCommand? {
        val frame = getCurrentFrame() ?: return null
        val bmp = frame.baseImage?.let { if (it.isCreated) it.bitmap else null } ?: return null
        val d = destRect.copy()
        d.x = d.x + (destBasePosition.x + frame.offset.x) * destRect.width / destBaseSize.width
        d.y = d.y + (destBasePosition.y + frame.offset.y) * destRect.height / destBaseSize.height
        d.width = frame.srcRectangle.width * destRect.width / destBaseSize.width
        d.height = frame.srcRectangle.height * destRect.height / destBaseSize.height
        return SpriteDrawCommand(bmp, frame.srcRectangle, d)
    }

    /** アニメーション中かどうか (UI が再描画を続けるか判断する) */
    val isAnimating: Boolean get() = totaltime > 0 && frameList.size > 1
}

class GraphicsImage(val id: Int) : AbstractImage() {
    private var size = ESize(0, 0)
    private var created = false
    var brushColor: Int? = null
        private set
    var penColor: Int? = null
        private set
    var penWidth: Int = 1
        private set
    var font: EFont? = null
        private set

    private fun g(): Raster = bitmap ?: throw NullPointerException()

    fun gCreate(x: Int, y: Int) {
        gDispose()
        bitmap = Raster(x, y)
        size = ESize(x, y)
        created = true
    }

    fun gCreateFromF(bmp: Raster) {
        gDispose()
        val r = Raster(bmp.width, bmp.height)
        r.drawImage(bmp, ERect(0, 0, bmp.width, bmp.height), ERect(0, 0, bmp.width, bmp.height))
        bitmap = r
        size = ESize(bmp.width, bmp.height)
        created = true
    }

    fun gClear(argb: Int) = g().clear(argb)

    private fun defaultFont() = EFont(Config.FontName, Config.FontSize, 0)

    fun gDrawString(text: String, x: Int, y: Int) {
        Platform.graphics.drawText(g(), text, font ?: defaultFont(), brushColor ?: Config.ForeColor.argb, x, y)
        g().touch()
    }

    fun gDrawString(text: String, x: Int, y: Int, width: Int, height: Int) {
        Platform.graphics.drawText(g(), text, font ?: defaultFont(), brushColor ?: Config.ForeColor.argb, x, y, width, height)
        g().touch()
    }

    fun gDrawRectangle(rect: ERect) = g().drawRect(rect, penColor ?: Config.ForeColor.argb, penWidth)

    fun gFillRectangle(rect: ERect) = g().fillRect(rect, brushColor ?: Config.BackColor.argb)

    fun gDrawCImg(img: ASprite, destRect: ERect) = img.graphicsDraw(g(), destRect)
    fun gDrawCImg(img: ASprite, destRect: ERect, cm: Array<FloatArray>) = img.graphicsDraw(g(), destRect, cm)

    fun gDrawG(srcGra: GraphicsImage, destRect: ERect, srcRect: ERect) = g().drawImage(srcGra.getBitmap(), destRect, srcRect)
    fun gDrawG(srcGra: GraphicsImage, destRect: ERect, srcRect: ERect, cm: Array<FloatArray>) = g().drawImage(srcGra.getBitmap(), destRect, srcRect, cm)

    fun gDrawGWithMask(srcGra: GraphicsImage, maskGra: GraphicsImage, destPoint: EPoint) {
        val dest = g()
        val src = srcGra.getBitmap()
        val mask = maskGra.getBitmap()
        for (y in 0 until srcGra.height) {
            val dy = destPoint.y + y
            if (dy < 0 || dy >= dest.height) continue
            for (x in 0 until srcGra.width) {
                val dx = destPoint.x + x
                if (dx < 0 || dx >= dest.width) continue
                // マスク画像の B チャンネルを透過度として用いる
                val m = mask.pixels[y * mask.width + x] and 0xFF
                val s = src.pixels[y * src.width + x]
                val di = dy * dest.width + dx
                when (m) {
                    255 -> dest.pixels[di] = s
                    0 -> {}
                    else -> {
                        val mm = m + 1
                        val d = dest.pixels[di]
                        var out = 0
                        for (sh in intArrayOf(0, 8, 16, 24)) {
                            val v = ((((s ushr sh) and 0xFF) * mm + ((d ushr sh) and 0xFF) * (256 - mm)) shr 8) and 0xFF
                            out = out or (v shl sh)
                        }
                        dest.pixels[di] = out
                    }
                }
            }
        }
        dest.touch()
    }

    fun gSetFont(f: EFont?) { font = f }
    fun gSetBrush(argb: Int?) { brushColor = argb }
    fun gSetPen(argb: Int?, width: Int) { penColor = argb; penWidth = width }

    /** array[x][y] に ARGB を書き込む */
    fun gBitmapToInt64Array(array: Array<LongArray>, xstart: Int, ystart: Int): Boolean {
        val bmp = g()
        val w = bmp.width; val h = bmp.height
        if (xstart + w > array.size || (array.isNotEmpty() && ystart + h > array[0].size)) return false
        for (y in 0 until h) for (x in 0 until w) array[x + xstart][y + ystart] = bmp.pixels[y * w + x].toLong() and 0xFFFFFFFFL
        return true
    }

    fun gByteArrayToBitmap(array: Array<LongArray>, xstart: Int, ystart: Int): Boolean {
        val bmp = g()
        val w = bmp.width; val h = bmp.height
        if (xstart + w > array.size || (array.isNotEmpty() && ystart + h > array[0].size)) return false
        for (y in 0 until h) for (x in 0 until w) bmp.pixels[y * w + x] = array[x + xstart][y + ystart].toInt()
        bmp.touch()
        return true
    }

    fun getBitmap(): Raster = bitmap ?: throw NullPointerException()
    fun gSetColor(argb: Int, x: Int, y: Int) = getBitmap().setPixel(x, y, argb)
    fun gGetColor(x: Int, y: Int): Int = getBitmap().getPixel(x, y)

    fun gDispose() {
        size = ESize(0, 0)
        bitmap = null
        created = false
        brushColor = null
        penColor = null
        penWidth = 1
        font = null
    }

    override fun dispose() = gDispose()
    override val isCreated: Boolean get() = created
    val width: Int get() = size.width
    val height: Int get() = size.height
}

object AppContents {
    private val resourceDic = HashMap<String, AContentFile>()
    private val imageDictionary = HashMap<String, ASprite>()
    private val gList = HashMap<Int, GraphicsImage>()

    fun getGraphics(i: Int): GraphicsImage = gList.getOrPut(i) { GraphicsImage(i) }

    fun getSprite(nameIn: String?): ASprite? {
        if (nameIn == null) return null
        return imageDictionary[nameIn.uppercase()]
    }

    fun spriteDispose(nameIn: String?) {
        if (nameIn == null) return
        val name = nameIn.uppercase()
        imageDictionary.remove(name)?.dispose()
    }

    fun createSpriteG(imgNameIn: String?, parent: GraphicsImage, rect: ERect) {
        if (imgNameIn.isNullOrEmpty()) throw IllegalArgumentException()
        val imgName = imgNameIn.uppercase()
        imageDictionary[imgName] = SpriteG(imgName, parent, rect)
    }

    fun createSpriteAnime(imgNameIn: String?, w: Int, h: Int) {
        if (imgNameIn.isNullOrEmpty()) throw IllegalArgumentException()
        val imgName = imgNameIn.uppercase()
        imageDictionary[imgName] = SpriteAnime(imgName, ESize(w, h))
    }

    fun loadContents(): Boolean {
        val dir = File(Program.ContentDir)
        if (!dir.isDirectory) {
            // 大文字小文字違いのフォルダ (Resources 等) も探す
            val alt = File(Program.ExeDir).listFiles()?.firstOrNull { it.isDirectory && it.name.equals("resources", true) } ?: return true
            return loadFrom(alt)
        }
        return loadFrom(dir)
    }

    private fun loadFrom(dir: File): Boolean {
        try {
            val csvFiles = dir.walkTopDown().filter { it.isFile && it.name.lowercase().endsWith(".csv") }.sortedBy { it.path.lowercase() }.toList()
            for (file in csvFiles) {
                var currentAnime: SpriteAnime? = null
                val directory = file.parentFile
                val filename = file.name
                val lines = com.eraandroid.emuera.sub.TextFileReader.readAllLines(file)
                var lineNo = 0
                for (line in lines) {
                    lineNo++
                    if (line.isEmpty()) continue
                    val str = line.trim()
                    if (str.isEmpty() || str.startsWith(";")) continue
                    val tokens = str.split(',')
                    val sp = ScriptPosition(filename, lineNo)
                    val item = createFromCsv(tokens, directory, currentAnime, sp)
                    if (item is ASprite) {
                        currentAnime = item as? SpriteAnime
                        if (!imageDictionary.containsKey(item.name)) imageDictionary[item.name] = item
                        else {
                            ParserMediator.warn("同名のリソースがすでに作成されています:" + item.name, sp, 0)
                            item.dispose()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            return false
        }
        return true
    }

    fun unloadContents() {
        for (img in resourceDic.values) img.dispose()
        resourceDic.clear()
        imageDictionary.clear()
        for (g in gList.values) g.gDispose()
        gList.clear()
    }

    fun unloadGraphicList() {
        for (g in gList.values) g.gDispose()
        gList.clear()
    }

    private fun findFileIgnoreCase(dir: File, name: String): File? {
        val direct = File(dir, name)
        if (direct.isFile) return direct
        // サブフォルダ指定 (a\b.png) にも対応
        var cur: File = dir
        val parts = name.replace('\\', '/').split('/')
        for ((i, p) in parts.withIndex()) {
            val next = cur.listFiles()?.firstOrNull { it.name.equals(p, ignoreCase = true) } ?: return null
            if (i == parts.size - 1) return if (next.isFile) next else null
            cur = next
        }
        return null
    }

    private fun createFromCsv(tokens: List<String>, dir: File, currentAnime: SpriteAnime?, sp: ScriptPosition): AContentItem? {
        if (tokens.size < 2) return null
        val name = tokens[0].trim().uppercase()
        val arg2 = tokens[1].trim().uppercase()
        if (name.isEmpty() || arg2.isEmpty()) return null
        if (arg2 == "ANIME") {
            if (tokens.size < 4) {
                ParserMediator.warn("アニメーションスプライトのサイズが宣言されていません", sp, 1)
                return null
            }
            val w = tokens[2].trim().toIntOrNull()
            val h = tokens[3].trim().toIntOrNull()
            if (w == null || h == null || w <= 0 || h <= 0 || w > AbstractImage.MAX_IMAGESIZE || h > AbstractImage.MAX_IMAGESIZE) {
                ParserMediator.warn("アニメーションスプライトのサイズの指定が適切ではありません", sp, 1)
                return null
            }
            return SpriteAnime(name, ESize(w, h))
        }
        if (arg2.indexOf('.') < 0) {
            ParserMediator.warn("第二引数に拡張子がありません:$arg2", sp, 1)
            return null
        }
        val parentName = dir.path.uppercase() + "/" + arg2
        if (!resourceDic.containsKey(parentName)) {
            val file = findFileIgnoreCase(dir, tokens[1].trim())
            if (file == null) {
                ParserMediator.warn("指定された画像ファイルが見つかりませんでした:$arg2", sp, 1)
                return null
            }
            val bmp = Platform.graphics.loadImage(file.path)
            if (bmp == null) {
                ParserMediator.warn("指定されたファイルの読み込みに失敗しました:$arg2", sp, 1)
                return null
            }
            if (bmp.width > AbstractImage.MAX_IMAGESIZE || bmp.height > AbstractImage.MAX_IMAGESIZE)
                ParserMediator.warn("指定された画像ファイルの大きさが大きすぎます(幅及び高さを${AbstractImage.MAX_IMAGESIZE}以下にすることを強く推奨します):$arg2", sp, 1)
            val img = ConstImage(parentName)
            img.createFrom(bmp)
            resourceDic[parentName] = img
        }
        val parentImage = resourceDic[parentName] as? ConstImage
        if (parentImage == null || !parentImage.isCreated) {
            ParserMediator.warn("作成に失敗したリソースを元にスプライトを作成しようとしました:$arg2", sp, 1)
            return null
        }
        val pb = parentImage.bitmap!!
        var rect = ERect(0, 0, pb.width, pb.height)
        var pos = EPoint()
        var delay = 1000
        if (tokens.size >= 6) {
            val rv = IntArray(4)
            var sccs = true
            for (i in 0 until 4) { val v = tokens[i + 2].trim().toIntOrNull(); if (v == null) sccs = false else rv[i] = v }
            if (sccs) {
                rect = ERect(rv[0], rv[1], rv[2], rv[3])
                if (rect.width <= 0 || rect.height <= 0) {
                    ParserMediator.warn("スプライトの高さ又は幅には正の値のみ指定できます:$name", sp, 1)
                    return null
                }
                if (!rect.intersectsWith(ERect(0, 0, pb.width, pb.height))) {
                    ParserMediator.warn("親画像の範囲外を参照しています:$name", sp, 1)
                    return null
                }
            }
            if (tokens.size >= 8) {
                val px = tokens[6].trim().toIntOrNull()
                val py = tokens[7].trim().toIntOrNull()
                if (px != null && py != null) pos = EPoint(px, py)
                if (tokens.size >= 9) {
                    val d = tokens[8].trim().toIntOrNull()
                    if (d != null) {
                        delay = d
                        if (delay <= 0) {
                            ParserMediator.warn("フレーム表示時間には正の値のみ指定できます:$name", sp, 1)
                            return null
                        }
                    }
                }
            }
        }
        if (currentAnime != null && currentAnime.name == name) {
            if (!currentAnime.addFrame(parentImage, rect, pos, delay))
                ParserMediator.warn("アニメーションスプライトのフレームの追加に失敗しました:$arg2", sp, 1)
            return null
        }
        return SpriteF(name, parentImage, rect, pos)
    }
}
