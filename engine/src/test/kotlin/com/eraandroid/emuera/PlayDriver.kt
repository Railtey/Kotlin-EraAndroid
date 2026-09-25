package com.eraandroid.emuera

import com.eraandroid.emuera.gameview.ConsoleState
import com.eraandroid.emuera.gameview.HeadlessConsoleHost
import java.io.File
import kotlin.test.Test

/**
 * 실제 게임을 헤드리스로 플레이하는 드라이버 (-DplayGame=폴더 -DplayInputs=입력1|입력2|... -DplayOut=출력파일)
 * 입력 "@T" 는 타이머 대기(tick), 입력 "~" 는 빈 Enter.
 */
class PlayDriver {
    @Test
    fun play() {
        val dir = System.getProperty("playGame") ?: return
        val inputs = (System.getProperty("playInputs") ?: "").split('|').filter { it.isNotEmpty() }
        val out = File(System.getProperty("playOut") ?: "play.txt")
        val sb = StringBuilder()
        val t0 = System.currentTimeMillis()
        val c = EmueraEngine.bootSync(dir, HeadlessConsoleHost(1024, 700))
        sb.append("=== boot ${System.currentTimeMillis() - t0}ms state=${c.consoleState}\n")
        var printed = 0
        fun dump(tag: String) {
            val lines = c.snapshot().lines
            if (lines.size < printed) printed = 0
            sb.append("=== $tag (state=${c.consoleState} req=${c.currentInputRequest?.inputType})\n")
            for (i in printed until lines.size) sb.append(lines[i].toString()).append('\n')
            printed = lines.size
        }
        dump("after boot")
        for (inp in inputs) {
            if (c.consoleState != ConsoleState.WaitInput) { sb.append("=== stop: state=${c.consoleState}\n"); break }
            val s = if (inp == "~") "" else inp
            try {
                c.pressEnterKey(false, s, false)
                var guard = 0
                while (c.consoleState == ConsoleState.WaitInput && (c.currentInputRequest?.timelimit ?: 0) > 0 && guard++ < 300) {
                    Thread.sleep(10); c.tick()
                }
            } catch (t: Throwable) {
                sb.append("!!! EXCEPTION ${t}\n${t.stackTrace.take(15).joinToString("\n")}\n")
            }
            dump("input '$inp'")
        }
        out.writeText(sb.toString())
    }
}
