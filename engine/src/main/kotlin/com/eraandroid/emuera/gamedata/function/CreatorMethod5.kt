package com.eraandroid.emuera.gamedata.function

import com.eraandroid.emuera.Program
import com.eraandroid.emuera.Resources
import com.eraandroid.emuera.config.Config
import com.eraandroid.emuera.content.*
import com.eraandroid.emuera.gamedata.expression.*
import com.eraandroid.emuera.gamedata.variable.*
import com.eraandroid.emuera.platform.*
import com.eraandroid.emuera.sub.*
import java.io.File

/* Creator.Method.cs part 5: 画像処理系 */

internal object GraphicsArgs {
    fun readGraphics(name: String, exm: ExpressionMediator, arguments: Array<IOperandTerm?>, argNo: Int): GraphicsImage {
        val target = arguments[argNo]!!.getIntValue(exm)
        if (target < 0) throw CodeEE(Resources.fmt(Resources.RuntimeErrMesMethodGraphicsID0, name, target))
        else if (target > Int.MAX_VALUE) throw CodeEE(Resources.fmt(Resources.RuntimeErrMesMethodGraphicsID1, name, target))
        return AppContents.getGraphics(target.toInt())
    }

    fun readColor(name: String, exm: ExpressionMediator, arguments: Array<IOperandTerm?>, argNo: Int): Int {
        val c64 = arguments[argNo]!!.getIntValue(exm)
        if (c64 < 0 || c64 > 0xFFFFFFFFL) throw CodeEE(Resources.fmt(Resources.RuntimeErrMesMethodColorARGB0, name, c64))
        return c64.toInt()
    }

    private fun readInt(name: String, exm: ExpressionMediator, arguments: Array<IOperandTerm?>, argNo: Int, nonZero: Boolean = false): Int {
        val v = arguments[argNo]!!.getIntValue(exm)
        if (v < Int.MIN_VALUE || v > Int.MAX_VALUE || (nonZero && v == 0L))
            throw CodeEE(Resources.fmt(Resources.RuntimeErrMesMethodDefaultArgumentOutOfRange0, name, v, argNo + 1))
        return v.toInt()
    }

    fun readPoint(name: String, exm: ExpressionMediator, arguments: Array<IOperandTerm?>, argNo: Int): EPoint =
        EPoint(readInt(name, exm, arguments, argNo), readInt(name, exm, arguments, argNo + 1))

    fun readRectangle(name: String, exm: ExpressionMediator, arguments: Array<IOperandTerm?>, argNo: Int): ERect =
        ERect(readInt(name, exm, arguments, argNo), readInt(name, exm, arguments, argNo + 1),
            readInt(name, exm, arguments, argNo + 2, true), readInt(name, exm, arguments, argNo + 3, true))

    fun readColormatrix(name: String, exm: ExpressionMediator, arguments: Array<IOperandTerm?>, argNo: Int): Array<FloatArray> {
        val p = (arguments[argNo] as VariableTerm).getFixedVariableTerm(exm)
        val cm = Array(5) { FloatArray(5) }
        val id = p.identifier
        if (id.isArray2D) {
            val array: IntArr2
            val e1: Long
            val e2: Long
            if (id.isCharacterData) {
                @Suppress("UNCHECKED_CAST")
                array = id.getArrayChara(p.index1.toInt()) as IntArr2
                e1 = p.index2; e2 = p.index3
            } else {
                @Suppress("UNCHECKED_CAST")
                array = id.getArray() as IntArr2
                e1 = p.index1; e2 = p.index2
            }
            if (e1 < 0 || e2 < 0 || e1 + 5 > array.size || e2 + 5 > array[0].size)
                throw CodeEE(Resources.fmt(Resources.RuntimeErrMesMethodGColorMatrix0, name, e1, e2))
            for (x in 0 until 5) for (y in 0 until 5) cm[x][y] = array[(e1 + x).toInt()][(e2 + y).toInt()].toFloat() / 256f
        }
        if (id.isArray3D) {
            if (id.isCharacterData) throw NotImplCodeEE()
            @Suppress("UNCHECKED_CAST")
            val array = id.getArray() as IntArr3
            val e1 = p.index1; val e2 = p.index2; val e3 = p.index3
            if (e1 < 0 || e1 >= array.size) throw CodeEE(Resources.fmt(Resources.RuntimeErrMesMethodGColorMatrix0, name, e2, e3))
            if (e2 < 0 || e3 < 0 || e2 + 5 > array[0].size || e3 + 5 > array[0][0].size)
                throw CodeEE(Resources.fmt(Resources.RuntimeErrMesMethodGColorMatrix0, name, e2, e3))
            for (x in 0 until 5) for (y in 0 until 5) cm[x][y] = array[e1.toInt()][(e2 + x).toInt()][(e3 + y).toInt()].toFloat() / 256f
        }
        return cm
    }

    fun checkVarArgs(name: String, arguments: Array<IOperandTerm?>, types: Array<EType>, min: Int, max: Int): String? {
        if (arguments.size < min) return Resources.fmt(Resources.SyntaxErrMesMethodDefaultArgumentNum1, name, min)
        if (arguments.size > max) return Resources.fmt(Resources.SyntaxErrMesMethodDefaultArgumentNum2, name)
        for (i in arguments.indices) {
            val a = arguments[i] ?: return Resources.fmt(Resources.SyntaxErrMesMethodDefaultArgumentNotNullable0, name, i + 1)
            if (i < types.size && types[i] != a.getOperandType()) return Resources.fmt(Resources.SyntaxErrMesMethodDefaultArgumentType0, name, i + 1)
        }
        return null
    }

    fun isColorMatrixVar(t: IOperandTerm?): Boolean = t is VariableTerm && t.isInteger && (t.identifier.isArray2D || t.identifier.isArray3D)

    fun loadBitmap(path: String): Raster? {
        val f = FileUtil.resolve(path)
        if (!f.isFile) return null
        return Platform.graphics.loadImage(f.path)
    }

    fun getSaveDataPathText(index: Int, dir: String): String = dir + "txt" + String.format("%02d", index) + ".txt"
    fun getSaveDataPathGraphics(index: Int): String = Config.SavDir + "img" + String.format("%04d", index) + ".png"
}

private val I = EType.Int64
private val S = EType.String

internal class GraphicsStateMethod : FunctionMethod() {
    init { returnType = I; argumentTypeArray = arrayOf(I); canRestructure = false }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long {
        val g = GraphicsArgs.readGraphics(name, exm, arguments, 0)
        if (!g.isCreated) return 0
        return when (name) {
            "GCREATED" -> 1
            "GWIDTH" -> g.width.toLong()
            "GHEIGHT" -> g.height.toLong()
            else -> throw ExeEE("GraphicsState:$name:異常な分岐")
        }
    }
}

internal class GraphicsGetColorMethod : FunctionMethod() {
    init { returnType = I; argumentTypeArray = arrayOf(I, I, I); canRestructure = false }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long {
        val g = GraphicsArgs.readGraphics(name, exm, arguments, 0)
        if (!g.isCreated) return -1
        val p = GraphicsArgs.readPoint(name, exm, arguments, 1)
        if (p.x < 0 || p.x >= g.width || p.y < 0 || p.y >= g.height) return -1
        return g.gGetColor(p.x, p.y).toLong() and 0xFFFFFFFFL
    }
}

internal class GraphicsSetColorMethod : FunctionMethod() {
    init { returnType = I; argumentTypeArray = arrayOf(I, I, I, I); canRestructure = false }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long {
        val g = GraphicsArgs.readGraphics(name, exm, arguments, 0)
        if (!g.isCreated) return 0
        val c = GraphicsArgs.readColor(name, exm, arguments, 1)
        val p = GraphicsArgs.readPoint(name, exm, arguments, 2)
        if (p.x < 0 || p.x >= g.width || p.y < 0 || p.y >= g.height) return 0
        g.gSetColor(c, p.x, p.y)
        return 1
    }
}

internal class GraphicsSetBrushMethod : FunctionMethod() {
    init { returnType = I; argumentTypeArray = arrayOf(I, I); canRestructure = false }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long {
        val g = GraphicsArgs.readGraphics(name, exm, arguments, 0)
        if (!g.isCreated) return 0
        g.gSetBrush(GraphicsArgs.readColor(name, exm, arguments, 1))
        return 1
    }
}

internal class GraphicsSetFontMethod : FunctionMethod() {
    init { returnType = I; argumentTypeArray = arrayOf(I, S, I); canRestructure = false }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long {
        val g = GraphicsArgs.readGraphics(name, exm, arguments, 0)
        if (!g.isCreated) return 0
        val fontname = arguments[1]!!.getStrValue(exm)
        val fontsize = arguments[2]!!.getIntValue(exm)
        if (fontsize <= 0 || fontsize > Int.MAX_VALUE) return 0
        g.gSetFont(EFont(fontname, fontsize.toInt(), 0))
        return 1
    }
}

internal class GraphicsSetPenMethod : FunctionMethod() {
    init { returnType = I; argumentTypeArray = arrayOf(I, I, I); canRestructure = false }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long {
        val g = GraphicsArgs.readGraphics(name, exm, arguments, 0)
        if (!g.isCreated) return 0
        val c = GraphicsArgs.readColor(name, exm, arguments, 1)
        val width = arguments[2]!!.getIntValue(exm)
        g.gSetPen(c, width.coerceIn(1, 1000).toInt())
        return 1
    }
}

internal class SpriteStateMethod : FunctionMethod() {
    init { returnType = I; argumentTypeArray = arrayOf(S); canRestructure = false }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long {
        val img = AppContents.getSprite(arguments[0]!!.getStrValue(exm))
        if (img == null || !img.isCreated) return 0
        return when (name) {
            "SPRITECREATED" -> 1
            "SPRITEWIDTH" -> img.destBaseSize.width.toLong()
            "SPRITEHEIGHT" -> img.destBaseSize.height.toLong()
            "SPRITEPOSX" -> img.destBasePosition.x.toLong()
            "SPRITEPOSY" -> img.destBasePosition.y.toLong()
            else -> throw ExeEE("SpriteStateMethod:$name:異常な分岐")
        }
    }
}

internal class SpriteSetPosMethod : FunctionMethod() {
    init { returnType = I; argumentTypeArray = arrayOf(S, I, I); canRestructure = false }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long {
        val img = AppContents.getSprite(arguments[0]!!.getStrValue(exm))
        if (img == null || !img.isCreated) return 0
        val p = GraphicsArgs.readPoint(name, exm, arguments, 1)
        when (name) {
            "SPRITEMOVE" -> img.destBasePosition.offset(p)
            "SPRITESETPOS" -> img.destBasePosition = p
            else -> throw ExeEE("SpriteStateMethod:$name:異常な分岐")
        }
        return 1
    }
}

internal class SpriteGetColorMethod : FunctionMethod() {
    init { returnType = I; argumentTypeArray = arrayOf(S, I, I); canRestructure = false }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long {
        val img = AppContents.getSprite(arguments[0]!!.getStrValue(exm))
        if (img == null || !img.isCreated) return -1
        val p = GraphicsArgs.readPoint(name, exm, arguments, 1)
        if (p.x < 0 || p.x >= img.destBaseSize.width) return -1
        if (p.y < 0 || p.y >= img.destBaseSize.height) return -1
        val c = img.spriteGetColor(p.x, p.y)
        // 元実装は演算子優先順位のバグで A << (24 + R) << (16 + G) << (8 + B) になっている。互換のため再現する
        val a = (c ushr 24).toLong(); val r = (c ushr 16) and 0xFF; val gg = (c ushr 8) and 0xFF; val b = c and 0xFF
        return ((a shl (24 + r)) shl (16 + gg)) shl (8 + b)
    }
}

internal class ClientSizeMethod : FunctionMethod() {
    init { returnType = I; argumentTypeArray = arrayOf(); canRestructure = false }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long = when (name) {
        "CLIENTWIDTH" -> exm.console.clientWidth.toLong()
        "CLIENTHEIGHT" -> exm.console.clientHeight.toLong()
        else -> throw ExeEE("ClientSize:$name:異常な分岐")
    }
}

private fun checkSize(name: String, width: Int, height: Int) {
    if (width <= 0) throw CodeEE(Resources.fmt(Resources.RuntimeErrMesMethodGWidth0, name, width))
    else if (width > AbstractImage.MAX_IMAGESIZE) throw CodeEE(Resources.fmt(Resources.RuntimeErrMesMethodGWidth1, name, width, AbstractImage.MAX_IMAGESIZE))
    if (height <= 0) throw CodeEE(Resources.fmt(Resources.RuntimeErrMesMethodGHeight0, name, height))
    else if (height > AbstractImage.MAX_IMAGESIZE) throw CodeEE(Resources.fmt(Resources.RuntimeErrMesMethodGHeight1, name, height, AbstractImage.MAX_IMAGESIZE))
}

internal class GraphicsCreateMethod : FunctionMethod() {
    init { returnType = I; argumentTypeArray = arrayOf(I, I, I); canRestructure = false }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long {
        val g = GraphicsArgs.readGraphics(name, exm, arguments, 0)
        if (g.isCreated) return 0
        val p = GraphicsArgs.readPoint(name, exm, arguments, 1)
        checkSize(name, p.x, p.y)
        g.gCreate(p.x, p.y)
        return 1
    }
}

internal class GraphicsCreateFromFileMethod : FunctionMethod() {
    init { returnType = I; argumentTypeArray = arrayOf(I, S); canRestructure = false }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long {
        val g = GraphicsArgs.readGraphics(name, exm, arguments, 0)
        if (g.isCreated) return 0
        val filename = arguments[1]!!.getStrValue(exm)
        try {
            var filepath = filename.replace('\\', '/')
            if (!File(filepath).isAbsolute) filepath = Program.ContentDir + filepath
            val bmp = GraphicsArgs.loadBitmap(filepath) ?: return 0
            if (bmp.width > AbstractImage.MAX_IMAGESIZE || bmp.height > AbstractImage.MAX_IMAGESIZE) return 0
            g.gCreateFromF(bmp)
        } catch (e: CodeEE) {
            throw e
        } catch (e: Exception) {
        }
        return if (g.isCreated) 1 else 0
    }
}

internal class GraphicsDisposeMethod : FunctionMethod() {
    init { returnType = I; argumentTypeArray = arrayOf(I); canRestructure = false }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long {
        val g = GraphicsArgs.readGraphics(name, exm, arguments, 0)
        if (!g.isCreated) return 0
        g.gDispose()
        return 1
    }
}

internal class SpriteCreateMethod : FunctionMethod() {
    init { returnType = I; argumentTypeArray = arrayOf(S); canRestructure = false }
    override fun checkArgumentType(name: String, arguments: Array<IOperandTerm?>): String? {
        if (arguments.size < 2) return Resources.fmt(Resources.SyntaxErrMesMethodDefaultArgumentNum1, name, 2)
        if (arguments.size > 6) return Resources.fmt(Resources.SyntaxErrMesMethodDefaultArgumentNum2, name)
        val a0 = arguments[0] ?: return Resources.fmt(Resources.SyntaxErrMesMethodDefaultArgumentNotNullable0, name, 1)
        val a1 = arguments[1] ?: return Resources.fmt(Resources.SyntaxErrMesMethodDefaultArgumentNotNullable0, name, 2)
        if (a0.getOperandType() != S) return Resources.fmt(Resources.SyntaxErrMesMethodDefaultArgumentType0, name, 1)
        if (a1.getOperandType() != I) return Resources.fmt(Resources.SyntaxErrMesMethodDefaultArgumentType0, name, 2)
        if (arguments.size == 2) return null
        if (arguments.size != 6) return Resources.fmt(Resources.SyntaxErrMesMethodDefaultArgumentNum0, name)
        for (i in 2 until arguments.size) {
            val a = arguments[i] ?: return Resources.fmt(Resources.SyntaxErrMesMethodDefaultArgumentNotNullable0, name, i + 1)
            if (a.getOperandType() != I) return Resources.fmt(Resources.SyntaxErrMesMethodDefaultArgumentType0, name, i + 1)
        }
        return null
    }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long {
        val imgname = arguments[0]!!.getStrValue(exm)
        if (imgname.isEmpty()) return 0
        val img = AppContents.getSprite(imgname)
        if (img != null && img.isCreated) return 0
        val g = GraphicsArgs.readGraphics(name, exm, arguments, 1)
        if (!g.isCreated) return 0
        var rect = ERect(0, 0, g.width, g.height)
        if (arguments.size == 6) {
            rect = GraphicsArgs.readRectangle(name, exm, arguments, 2)
            if (rect.x + rect.width < 0 || rect.x + rect.width > g.width || rect.y + rect.height < 0 || rect.y + rect.height > g.height)
                throw CodeEE(Resources.fmt(Resources.RuntimeErrMesMethodCIMGCreateOutOfRange0, name))
        }
        AppContents.createSpriteG(imgname, g, rect)
        return 1
    }
}

internal class SpriteDisposeMethod : FunctionMethod() {
    init { returnType = I; argumentTypeArray = arrayOf(S); canRestructure = false }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long {
        val imgname = arguments[0]!!.getStrValue(exm)
        val img = AppContents.getSprite(imgname)
        if (img == null || !img.isCreated) return 0
        AppContents.spriteDispose(imgname)
        return 1
    }
}

internal class GraphicsClearMethod : FunctionMethod() {
    init { returnType = I; argumentTypeArray = arrayOf(I, I); canRestructure = false }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long {
        val g = GraphicsArgs.readGraphics(name, exm, arguments, 0)
        val c = GraphicsArgs.readColor(name, exm, arguments, 1)
        if (!g.isCreated) return 0
        g.gClear(c)
        return 1
    }
}

internal class GraphicsFillRectangleMethod : FunctionMethod() {
    init { returnType = I; argumentTypeArray = arrayOf(I, I, I, I, I); canRestructure = false }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long {
        val g = GraphicsArgs.readGraphics(name, exm, arguments, 0)
        if (!g.isCreated) return 0
        g.gFillRectangle(GraphicsArgs.readRectangle(name, exm, arguments, 1))
        return 1
    }
}

internal class GraphicsDrawGMethod : FunctionMethod() {
    init { returnType = I; argumentTypeArray = null; canRestructure = false; hasUniqueRestructure = true }
    override fun checkArgumentType(name: String, arguments: Array<IOperandTerm?>): String? {
        if (arguments.size < 10) return Resources.fmt(Resources.SyntaxErrMesMethodDefaultArgumentNum1, name, 10)
        if (arguments.size > 11) return Resources.fmt(Resources.SyntaxErrMesMethodDefaultArgumentNum2, name)
        for (i in 0 until 10) {
            val a = arguments[i] ?: return Resources.fmt(Resources.SyntaxErrMesMethodDefaultArgumentNotNullable0, name, i + 1)
            if (a.getOperandType() != I) return Resources.fmt(Resources.SyntaxErrMesMethodDefaultArgumentType0, name, i + 1)
        }
        if (arguments.size == 10) return null
        if (!GraphicsArgs.isColorMatrixVar(arguments[10])) return Resources.fmt(Resources.SyntaxErrMesMethodGraphicsColorMatrix0, name)
        return null
    }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long {
        val dest = GraphicsArgs.readGraphics(name, exm, arguments, 0)
        if (!dest.isCreated) return 0
        val src = GraphicsArgs.readGraphics(name, exm, arguments, 1)
        if (!src.isCreated) return 0
        val destRect = GraphicsArgs.readRectangle(name, exm, arguments, 2)
        val srcRect = GraphicsArgs.readRectangle(name, exm, arguments, 6)
        if (arguments.size == 10 || arguments[10] == null) {
            dest.gDrawG(src, destRect, srcRect)
            return 1
        }
        dest.gDrawG(src, destRect, srcRect, GraphicsArgs.readColormatrix(name, exm, arguments, 10))
        return 1
    }
    override fun uniqueRestructure(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Boolean {
        for (i in arguments.indices) {
            val a = arguments[i] ?: continue
            if (i == 10) a.restructure(exm) else arguments[i] = a.restructure(exm)
        }
        return false
    }
}

internal class GraphicsDrawGWithMaskMethod : FunctionMethod() {
    init { returnType = I; argumentTypeArray = arrayOf(I, I, I, I, I); canRestructure = false }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long {
        val dest = GraphicsArgs.readGraphics(name, exm, arguments, 0)
        if (!dest.isCreated) return 0
        val src = GraphicsArgs.readGraphics(name, exm, arguments, 1)
        if (!src.isCreated) return 0
        val mask = GraphicsArgs.readGraphics(name, exm, arguments, 2)
        if (!mask.isCreated) return 0
        if (src.width != mask.width || src.height != mask.height) return 0
        val destPoint = GraphicsArgs.readPoint(name, exm, arguments, 3)
        if (destPoint.x + src.width > dest.width || destPoint.y + src.height > dest.height) return 0
        dest.gDrawGWithMask(src, mask, destPoint)
        return 1
    }
}

internal class GraphicsDrawSpriteMethod : FunctionMethod() {
    private val types = arrayOf(I, S, I, I, I, I)
    init { returnType = I; argumentTypeArray = types; canRestructure = false; hasUniqueRestructure = true }
    override fun checkArgumentType(name: String, arguments: Array<IOperandTerm?>): String? {
        if (arguments.size < 2) return Resources.fmt(Resources.SyntaxErrMesMethodDefaultArgumentNum1, name, 2)
        if (arguments.size > 7) return Resources.fmt(Resources.SyntaxErrMesMethodDefaultArgumentNum2, name)
        if (arguments.size != 2 && arguments.size != 4 && arguments.size != 6 && arguments.size != 7) return Resources.fmt(Resources.SyntaxErrMesMethodDefaultArgumentNum0, name)
        for (i in arguments.indices) {
            val a = arguments[i] ?: return Resources.fmt(Resources.SyntaxErrMesMethodDefaultArgumentNotNullable0, name, i + 1)
            if (i < types.size && types[i] != a.getOperandType()) return Resources.fmt(Resources.SyntaxErrMesMethodDefaultArgumentType0, name, i + 1)
        }
        if (arguments.size <= 6) return null
        if (!GraphicsArgs.isColorMatrixVar(arguments[6])) return Resources.fmt(Resources.SyntaxErrMesMethodGraphicsColorMatrix0, name)
        return null
    }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long {
        val dest = GraphicsArgs.readGraphics(name, exm, arguments, 0)
        if (!dest.isCreated) return 0
        val img = AppContents.getSprite(arguments[1]!!.getStrValue(exm))
        if (img == null || !img.isCreated) return 0
        var destRect = ERect(0, 0, img.destBaseSize.width, img.destBaseSize.height)
        when (arguments.size) {
            2 -> { dest.gDrawCImg(img, destRect); return 1 }
            4 -> {
                val p = GraphicsArgs.readPoint(name, exm, arguments, 2)
                destRect.x = p.x; destRect.y = p.y
                dest.gDrawCImg(img, destRect)
                return 1
            }
            6 -> {
                destRect = GraphicsArgs.readRectangle(name, exm, arguments, 2)
                dest.gDrawCImg(img, destRect)
                return 1
            }
        }
        destRect = GraphicsArgs.readRectangle(name, exm, arguments, 2)
        dest.gDrawCImg(img, destRect, GraphicsArgs.readColormatrix(name, exm, arguments, 6))
        return 1
    }
    override fun uniqueRestructure(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Boolean {
        for (i in arguments.indices) {
            val a = arguments[i] ?: continue
            if (i == 6) a.restructure(exm) else arguments[i] = a.restructure(exm)
        }
        return false
    }
}

internal class SpriteAnimeCreateMethod : FunctionMethod() {
    init { returnType = I; argumentTypeArray = arrayOf(S, I, I); canRestructure = false }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long {
        val imgname = arguments[0]!!.getStrValue(exm)
        if (imgname.isEmpty()) return 0
        val img = AppContents.getSprite(imgname)
        if (img != null && img.isCreated) return 0
        val pos = GraphicsArgs.readPoint(name, exm, arguments, 1)
        checkSize(name, pos.x, pos.y)
        AppContents.createSpriteAnime(imgname, pos.x, pos.y)
        return 1
    }
}

internal class SpriteAnimeAddFrameMethod : FunctionMethod() {
    init { returnType = I; argumentTypeArray = arrayOf(S, I, I, I, I, I, I, I, I); canRestructure = false }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long {
        val imgname = arguments[0]!!.getStrValue(exm)
        if (imgname.isEmpty()) return 0
        val img = AppContents.getSprite(imgname) as? SpriteAnime ?: return 0
        val g = GraphicsArgs.readGraphics(name, exm, arguments, 1)
        if (!g.isCreated) return 0
        val rect = GraphicsArgs.readRectangle(name, exm, arguments, 2)
        if (rect.width <= 0 || rect.height <= 0 || rect.x < 0 || rect.x + rect.width > g.width || rect.y < 0 || rect.y + rect.height > g.height) return 0
        val offset = GraphicsArgs.readPoint(name, exm, arguments, 6)
        val delay = arguments[8]!!.getIntValue(exm)
        if (delay <= 0 || delay > Int.MAX_VALUE) return 0
        img.addFrame(g, rect, offset, delay.toInt())
        return 1
    }
}

internal class CBGClearMethod : FunctionMethod() {
    init { returnType = I; argumentTypeArray = arrayOf(); canRestructure = false }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long { exm.console.cbgClear(); return 1 }
}

internal class CBGRemoveRangeMethod : FunctionMethod() {
    init { returnType = I; argumentTypeArray = arrayOf(I, I); canRestructure = false }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long {
        exm.console.cbgClearRange(arguments[0]!!.getIntValue(exm).toInt(), arguments[1]!!.getIntValue(exm).toInt())
        return 1
    }
}

internal class CBGClearButtonMethod : FunctionMethod() {
    init { returnType = I; argumentTypeArray = arrayOf(); canRestructure = false }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long { exm.console.cbgClearButton(); return 1 }
}

internal class CBGRemoveBMapMethod : FunctionMethod() {
    init { returnType = I; argumentTypeArray = arrayOf(); canRestructure = false }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long { exm.console.cbgClearBMap(); return 1 }
}

private fun readZ(name: String, exm: ExpressionMediator, arguments: Array<IOperandTerm?>, n: Int): Int {
    val z64 = arguments[n]!!.getIntValue(exm)
    if (z64 < Int.MIN_VALUE || z64 > Int.MAX_VALUE || z64 == 0L)
        throw CodeEE(Resources.fmt(Resources.RuntimeErrMesMethodDefaultArgumentOutOfRange0, name, z64, n + 1))
    return z64.toInt()
}

internal class CBGSetGraphicsMethod : FunctionMethod() {
    init { returnType = I; argumentTypeArray = arrayOf(I, I, I, I); canRestructure = false }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long {
        val g = GraphicsArgs.readGraphics(name, exm, arguments, 0)
        if (!g.isCreated) return 0
        val p = GraphicsArgs.readPoint(name, exm, arguments, 1)
        exm.console.cbgSetGraphics(g, p.x, p.y, readZ(name, exm, arguments, 3))
        return 1
    }
}

internal class CBGSetBMapGMethod : FunctionMethod() {
    init { returnType = I; argumentTypeArray = arrayOf(I); canRestructure = false }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long {
        val g = GraphicsArgs.readGraphics(name, exm, arguments, 0)
        if (!g.isCreated) return 0
        exm.console.cbgSetButtonMap(g)
        return 1
    }
}

internal class CBGSetCIMGMethod : FunctionMethod() {
    init { returnType = I; argumentTypeArray = arrayOf(S, I, I, I); canRestructure = false }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long {
        val img = AppContents.getSprite(arguments[0]!!.getStrValue(exm))
        if (img == null || !img.isCreated) return 0
        val p = GraphicsArgs.readPoint(name, exm, arguments, 1)
        return if (exm.console.cbgSetImage(img, p.x, p.y, readZ(name, exm, arguments, 3))) 1 else 0
    }
}

internal class CBGSETButtonSpriteMethod : FunctionMethod() {
    private val types = arrayOf(I, S, S, I, I, I, S)
    init { returnType = I; argumentTypeArray = types; canRestructure = false }
    override fun checkArgumentType(name: String, arguments: Array<IOperandTerm?>): String? {
        return GraphicsArgs.checkVarArgs(name, arguments, types, 6, 7)
    }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long {
        val b64 = arguments[0]!!.getIntValue(exm)
        if (b64 < 0 || b64 > 0xFFFFFF) return 0
        val imgN = AppContents.getSprite(arguments[1]!!.getStrValue(exm))
        val imgB = AppContents.getSprite(arguments[2]!!.getStrValue(exm))
        val p = GraphicsArgs.readPoint(name, exm, arguments, 3)
        val z = readZ(name, exm, arguments, 5)
        val tooltip = if (arguments.size > 6) arguments[6]!!.getStrValue(exm) else null
        return if (exm.console.cbgSetButtonImage(b64.toInt(), imgN, imgB, p.x, p.y, z, tooltip)) 1 else 0
    }
}

private val keytoggle = ShortArray(256)

internal class GetKeyStateMethod : FunctionMethod() {
    init { returnType = I; argumentTypeArray = arrayOf(I); canRestructure = false }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long {
        if (!exm.console.isActive) return 0
        val keycode = arguments[0]!!.getIntValue(exm)
        if (keycode < 0 || keycode > 255) return 0
        val k = keycode.toInt()
        val s = Platform.keyState(k)
        val toggle = keytoggle[k]
        keytoggle[k] = ((s.toInt() and 1) + 1).toShort()
        return when (name) {
            "GETKEY" -> if (s < 0) 1 else 0
            "GETKEYTRIGGERED" -> if (s < 0 && toggle != keytoggle[k]) 1 else 0
            else -> throw ExeEE("異常な分岐")
        }
    }
}

internal class MousePosMethod : FunctionMethod() {
    init { returnType = I; argumentTypeArray = arrayOf(); canRestructure = false }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long = when (name) {
        "MOUSEX" -> exm.console.getMousePosition().x.toLong()
        "MOUSEY" -> exm.console.getMousePosition().y.toLong()
        else -> throw ExeEE("異常な名前")
    }
}

internal class IsActiveMethod : FunctionMethod() {
    init { returnType = I; argumentTypeArray = arrayOf(); canRestructure = false }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long = if (exm.console.isActive) 1 else 0
}

internal class SetAnimeTimerMethod : FunctionMethod() {
    init { returnType = I; argumentTypeArray = arrayOf(I); canRestructure = false }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long {
        val i64 = arguments[0]!!.getIntValue(exm)
        if (i64 < Int.MIN_VALUE || i64 > Short.MAX_VALUE) throw CodeEE(Resources.fmt(Resources.RuntimeErrMesMethodDefaultArgumentOutOfRange0, name, i64, 1))
        exm.console.setRedrawTimer(i64.toInt())
        return 1
    }
}

internal class SaveTextMethod : FunctionMethod() {
    private val types = arrayOf(S, I, I, I)
    init { returnType = I; argumentTypeArray = types; canRestructure = false }
    override fun checkArgumentType(name: String, arguments: Array<IOperandTerm?>): String? = GraphicsArgs.checkVarArgs(name, arguments, types, 2, 4)
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long {
        val savText = arguments[0]!!.getStrValue(exm)
        val i64 = arguments[1]!!.getIntValue(exm)
        if (i64 < 0 || i64 > Int.MAX_VALUE) return 0
        val forceSavdir = arguments.size > 2 && arguments[2]!!.getIntValue(exm) != 0L
        val forceUTF8 = arguments.size > 3 && arguments[3]!!.getIntValue(exm) != 0L
        val filepath = GraphicsArgs.getSaveDataPathText(i64.toInt(), if (forceSavdir) Config.ForceSavDir else Config.SavDir)
        val encoding = if (forceUTF8) Charsets.UTF_8 else Config.SaveEncode
        try {
            if (forceSavdir) Config.forceCreateSavDir() else Config.createSavDir()
            val bytes = savText.toByteArray(encoding)
            // .NET File.WriteAllText(UTF-8) は BOM を付ける
            File(filepath).writeBytes(if (encoding == Charsets.UTF_8) byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + bytes else bytes)
        } catch (e: Exception) {
            return 0
        }
        return 1
    }
}

internal class LoadTextMethod : FunctionMethod() {
    private val types = arrayOf(I, I, I)
    init { returnType = S; argumentTypeArray = types; canRestructure = false }
    override fun checkArgumentType(name: String, arguments: Array<IOperandTerm?>): String? = GraphicsArgs.checkVarArgs(name, arguments, types, 1, 3)
    override fun getStrValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): String {
        val i64 = arguments[0]!!.getIntValue(exm)
        if (i64 < 0 || i64 > Int.MAX_VALUE) return ""
        val forceSavdir = arguments.size > 1 && arguments[1]!!.getIntValue(exm) != 0L
        val forceUTF8 = arguments.size > 2 && arguments[2]!!.getIntValue(exm) != 0L
        val filepath = GraphicsArgs.getSaveDataPathText(i64.toInt(), if (forceSavdir) Config.ForceSavDir else Config.SavDir)
        val f = FileUtil.resolve(filepath)
        if (!f.isFile) return ""
        return try {
            TextFileReader.open(f, if (forceUTF8) Charsets.UTF_8 else Config.SaveEncode).use { it.readText() }.replace("\r", "")
        } catch (e: Exception) { "" }
    }
}

internal class GraphicsSaveMethod : FunctionMethod() {
    init { returnType = I; argumentTypeArray = arrayOf(I, I); canRestructure = false }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long {
        val g = GraphicsArgs.readGraphics(name, exm, arguments, 0)
        if (!g.isCreated) return 0
        val i64 = arguments[1]!!.getIntValue(exm)
        if (i64 < 0 || i64 > Int.MAX_VALUE) return 0
        return try {
            Config.createSavDir()
            if (Platform.graphics.saveImage(g.getBitmap(), GraphicsArgs.getSaveDataPathGraphics(i64.toInt()))) 1 else 0
        } catch (e: Exception) { 0 }
    }
}

internal class GraphicsLoadMethod : FunctionMethod() {
    init { returnType = I; argumentTypeArray = arrayOf(I, I); canRestructure = false }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long {
        val g = GraphicsArgs.readGraphics(name, exm, arguments, 0)
        if (g.isCreated) return 0
        val i64 = arguments[1]!!.getIntValue(exm)
        if (i64 < 0 || i64 > Int.MAX_VALUE) return 0
        try {
            val bmp = GraphicsArgs.loadBitmap(GraphicsArgs.getSaveDataPathGraphics(i64.toInt())) ?: return 0
            if (bmp.width > AbstractImage.MAX_IMAGESIZE || bmp.height > AbstractImage.MAX_IMAGESIZE) return 0
            g.gCreateFromF(bmp)
        } catch (e: CodeEE) { throw e } catch (e: Exception) {}
        return if (g.isCreated) 1 else 0
    }
}
