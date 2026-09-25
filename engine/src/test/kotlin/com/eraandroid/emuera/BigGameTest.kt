package com.eraandroid.emuera

import com.eraandroid.emuera.gameview.HeadlessConsoleHost
import java.io.File
import kotlin.test.Test

class BigGameTest {
    @Test
    fun loadBig() {
        val dir = System.getProperty("bigGame") ?: return
        if (!File(dir).isDirectory) return
        val t0 = System.currentTimeMillis()
        val c = EmueraEngine.bootSync(dir, HeadlessConsoleHost())
        println("elapsed " + (System.currentTimeMillis() - t0) + "ms")
        println(c.snapshot().lines.takeLast(8).joinToString("\n"))
    }
}
