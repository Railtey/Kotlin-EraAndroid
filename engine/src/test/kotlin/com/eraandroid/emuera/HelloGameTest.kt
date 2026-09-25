package com.eraandroid.emuera

import com.eraandroid.emuera.gameview.ConsoleState
import com.eraandroid.emuera.gameview.EmueraConsole
import com.eraandroid.emuera.gameview.HeadlessConsoleHost
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HelloGameTest {
    private fun gameDir(name: String): String =
        File(javaClass.classLoader.getResource("games/$name/csv")!!.toURI()).parentFile.path

    private fun text(c: EmueraConsole): String = c.snapshot().lines.joinToString("\n") { it.toString() }

    private fun waitWhile(c: EmueraConsole, timeoutMs: Long = 5000) {
        val end = System.currentTimeMillis() + timeoutMs
        while (c.consoleState == ConsoleState.WaitInput && c.currentInputRequest?.timelimit ?: 0 > 0 && System.currentTimeMillis() < end) {
            Thread.sleep(20)
            c.tick()
        }
    }

    @Test
    fun runsTitle() {
        val host = HeadlessConsoleHost()
        val c = EmueraEngine.bootSync(gameDir("hello"), host)
        val t = text(c)
        println(t)
        assertEquals(ConsoleState.WaitInput, c.consoleState, t)
        assertTrue("1+2=3" in t, t)
        assertTrue("[0][1][2]" in t, t)
        assertTrue("안녕世界" in t, t)
        assertTrue("FIB(10)=55" in t, t)
        assertTrue("ABL名=体力" in t, t)
        assertTrue("関数=12" in t, t)
        assertTrue("STRCOUNT=6" in t, t)
        assertTrue("SPLIT=abc" in t, t)

        c.pressEnterKey(false, "5", false)
        c.pressEnterKey(false, "0", false)
        assertTrue("はじめました" in text(c), text(c))
        c.pressEnterKey(false, "テスト", false)
        assertTrue("入力=テスト" in text(c), text(c))
        c.pressEnterKey(false, "", false)
        waitWhile(c)
        val t2 = text(c)
        println(t2)
        assertTrue("WAIT後" in t2, t2)
        assertTrue("終了" in t2, t2)
    }
}

class SmokeTest {
    @Test
    fun smoke() {
        val dir = java.io.File(javaClass.classLoader.getResource("games/smoke/csv")!!.toURI()).parentFile.path
        val c = EmueraEngine.bootSync(dir, HeadlessConsoleHost())
        val t = c.snapshot().lines.joinToString("\n") { it.toString() }
        println(t)
        assertTrue("END" in t, t)
    }
}

class ExprTest {
    @Test
    fun literals() {
        val dir = java.io.File(javaClass.classLoader.getResource("games/expr/csv")!!.toURI()).parentFile.path
        val c = EmueraEngine.bootSync(dir, HeadlessConsoleHost())
        val t = c.snapshot().lines.joinToString("\n") { it.toString() }
        println(t)
        val mx = Long.MAX_VALUE; val mn = Long.MIN_VALUE
        assertTrue("A=$mx B=$mn C=$mn D=1024 E=5 F=" in t, t)
        assertTrue("H=$mx I=105 J=-2 K=-2 L=5 M=-1" in t, t)
    }
}

class AndroidLayoutTest {
    @Test
    fun upperCaseFoldersAndPrintCGrid() {
        val dir = java.io.File(javaClass.classLoader.getResource("games/upper/CSV")!!.toURI()).parentFile.path
        com.eraandroid.emuera.config.Config.setScreenOverride(40, 2, 19)
        try {
            val c = EmueraEngine.bootSync(dir, HeadlessConsoleHost())
            val lines = c.snapshot().lines.map { it.toString() }
            println(lines.joinToString("\n"))
            assertTrue(lines.any { "TITLE=大文字テスト" in it }, lines.toString())
            // 2 列ずつ並ぶ (長い項目でも列数で改行)
            val grid = c.snapshot().lines.filter { l -> l.buttons.any { it.isPrintC } }
            assertEquals(2, grid.size, lines.toString())
            assertEquals(listOf(2, 2), grid.map { l -> l.buttons.count { it.isButton } })
        } finally {
            com.eraandroid.emuera.config.Config.setScreenOverride(0, 0, 0)
        }
    }
}
