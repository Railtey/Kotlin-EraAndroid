package com.eraandroid.emuera

import com.eraandroid.emuera.gameview.ConsoleState
import com.eraandroid.emuera.gameview.HeadlessConsoleHost
import java.io.File

/** java -cp ... com.eraandroid.emuera.PlayMainKt <gameDir> <inputsFile> <outFile> */
fun main(args: Array<String>) {
    val dir = args[0]
    val inputs = File(args[1]).readLines().filter { it.isNotEmpty() && !it.startsWith("#") }
    val out = File(args[2])
    val sb = StringBuilder()
    val t0 = System.currentTimeMillis()
    System.getProperty("cols")?.toInt()?.let { n ->
        val cols = when { n >= 130 -> 4; n >= 80 -> 3; n >= 40 -> 2; else -> 1 }
        com.eraandroid.emuera.config.Config.setScreenOverride(n, cols, n / cols - 1)
    }
    val c = EmueraEngine.bootSync(dir, HeadlessConsoleHost(1024, 700))
    sb.append("=== boot ${System.currentTimeMillis() - t0}ms\n")
    var printed = 0
    fun dump(tag: String) {
        val lines = c.snapshot().lines
        if (lines.size < printed) { sb.append("--- (display cleared)\n"); printed = 0 }
        sb.append("=== $tag (state=${c.consoleState} req=${c.currentInputRequest?.inputType})\n")
        for (i in printed until lines.size) sb.append(lines[i].toString()).append('\n')
        printed = lines.size
    }
    val conv = com.eraandroid.emuera.ui.UiConverter()
    fun uiDump() {
        val ui = conv.snapshot(c)
        sb.append("--- UI view (active=${ui.activeLineIndex}/${ui.lines.size} value=${ui.waitingValue} num=${ui.waitingNumber} key=${ui.waitingKey})\n")
        val from = maxOf(0, ui.lines.size - 30)
        for (i in from until ui.lines.size) {
            val l = ui.lines[i]
            val mark = if (i >= ui.activeLineIndex) ">" else " "
            if (l.isDrawLine) { sb.append("$mark ────────\n"); continue }
            val txt = l.spans.joinToString("") { sp ->
                when {
                    sp.image != null -> "<IMG ${sp.imageWidthEm}x${sp.imageHeightEm}>"
                    sp.isButton -> "⟦" + sp.text + "|" + (if (sp.buttonValue < -9000000000000000000L) "s" else sp.buttonValue.toString()) + "⟧"
                    else -> sp.text
                }
            }
            sb.append("$mark[${l.align.name[0]}] $txt\n")
        }
    }
    dump("after boot")
    if (System.getProperty("ui") != null) uiDump()
    for (inp in inputs) {
        if (c.consoleState != ConsoleState.WaitInput) { sb.append("=== stop: state=${c.consoleState}\n"); break }
        val s = if (inp == "~") "" else inp
        try {
            c.pressEnterKey(false, s, false)
            var guard = 0
            while (c.consoleState == ConsoleState.WaitInput && (c.currentInputRequest?.timelimit ?: 0) > 0 && guard++ < 500) {
                Thread.sleep(10); c.tick()
            }
        } catch (t: Throwable) {
            sb.append("!!! EXCEPTION $t\n${t.stackTrace.take(20).joinToString("\n")}\n")
        }
        dump("input '$inp'")
        if (System.getProperty("ui") != null) uiDump()
    }
    out.writeText(sb.toString())
    System.exit(0)
}
