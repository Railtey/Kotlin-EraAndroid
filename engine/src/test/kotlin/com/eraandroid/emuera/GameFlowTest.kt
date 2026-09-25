package com.eraandroid.emuera

import com.eraandroid.emuera.content.SpriteDrawCommand
import com.eraandroid.emuera.gameview.ConsoleCanvas
import com.eraandroid.emuera.gameview.ConsoleState
import com.eraandroid.emuera.gameview.EmueraConsole
import com.eraandroid.emuera.gameview.HeadlessConsoleHost
import com.eraandroid.emuera.platform.EFont
import com.eraandroid.emuera.platform.ERect
import com.eraandroid.emuera.platform.NullGraphics
import com.eraandroid.emuera.platform.Platform
import com.eraandroid.emuera.platform.Raster
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** テスト用: ImageIO で画像を読む */
class AwtGraphics : NullGraphics() {
    override fun loadImage(path: String): Raster? {
        val img = ImageIO.read(File(path)) ?: return null
        val r = Raster(img.width, img.height)
        img.getRGB(0, 0, img.width, img.height, r.pixels, 0, img.width)
        return r
    }
}

class GameFlowTest {
    private fun copyGame(name: String): File {
        val src = File(javaClass.classLoader.getResource("games/$name/csv")!!.toURI()).parentFile
        val dst = Files.createTempDirectory("era").toFile()
        src.copyRecursively(dst)
        return dst
    }

    private fun text(c: EmueraConsole): String = c.snapshot().lines.joinToString("\n") { it.toString() }

    @AfterTest
    fun cleanup() { Platform.graphics = NullGraphics() }

    @Test
    fun standardFlowWithSaveLoad() {
        val dir = copyGame("std")
        val c = EmueraEngine.bootSync(dir.path, HeadlessConsoleHost())
        val t = text(c)
        println(t)
        assertEquals(ConsoleState.WaitInput, c.consoleState, t)
        assertTrue("最初からはじめる" in t, t)
        c.pressEnterKey(false, "0", false)
        val t2 = text(c)
        println(t2)
        assertTrue("キャラ数=1 名前=あなた 体力=1000" in t2, t2)
        assertTrue("存在=0" in t2, t2)
        assertTrue("ロード MONEY=500 FLAG=7 NAME=あなた" in t2, t2)
        assertTrue(File(dir, "sav/save00.sav").isFile || File(dir, "save00.sav").isFile, dir.walk().joinToString())
        // [10] はボタンになる
        val btn = c.snapshot().lines.flatMap { it.buttons.toList() }.firstOrNull { it.isButton && it.toString().contains("ボタン") }
        assertTrue(btn != null && btn.isInteger && btn.input == 10L, "button: $btn")
    }

    @Test
    fun graphicsAndImages() {
        val dir = copyGame("gfx")
        val img = BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until 32) for (x in 0 until 32) img.setRGB(x, y, 0xFF0000FF.toInt())
        File(dir, "resources").mkdirs()
        ImageIO.write(img, "png", File(dir, "resources/face.png"))
        File(dir, "resources/img.csv").writeText("face,face.png\n")
        Platform.graphics = AwtGraphics()

        val c = EmueraEngine.bootSync(dir.path, HeadlessConsoleHost())
        val t = text(c)
        println(t)
        assertEquals(ConsoleState.WaitInput, c.consoleState, t)
        assertTrue("G=${0xFFFF0000L}" in t, t)
        assertTrue("SW=100 FW=32" in t, t)
        assertTrue("P=${0xFF0000FFL}" in t, t)

        val sprites = ArrayList<SpriteDrawCommand>()
        val texts = ArrayList<Pair<String, Int>>()
        val canvas = object : ConsoleCanvas {
            override fun drawText(text: String, x: Int, y: Int, font: EFont, argb: Int) { texts.add(text to argb) }
            override fun fillRect(rect: ERect, argb: Int) {}
            override fun drawSprite(cmd: SpriteDrawCommand) { sprites.add(cmd) }
        }
        var y = 0
        for (line in c.snapshot().lines) { line.drawTo(canvas, y, false); y += 20 }
        assertTrue(sprites.size >= 3, "sprites=${sprites.size}")
        assertTrue(texts.any { it.first == "緑" && (it.second and 0xFFFFFF) == 0x00FF00 }, texts.toString())
    }
}
