package com.eraandroid.emuera

import com.eraandroid.emuera.gameproc.InputType
import com.eraandroid.emuera.gameview.ConsoleState
import com.eraandroid.emuera.gameview.HeadlessConsoleHost
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.random.Random

/**
 * 랜덤 자동 플레이: java ... AutoPlayKt <gameDir> <prefixInputs> <steps> <seed> <out>
 * 입력 대기 때마다 선택 가능한 버튼 중 하나를 고르고, 에러/예외/멈춤을 찾는다.
 */
private val SUSPICIOUS = Regex("""\\@|\{[A-Z_]+[:}]|%[A-Z_]+[:%]|\bnull\b|NaN|Exception|整数型最小値|警告""")

fun main(args: Array<String>) {
    val dir = args[0]
    val prefix = File(args[1]).readLines().filter { it.isNotEmpty() && !it.startsWith("#") }
    val steps = args[2].toInt()
    val rnd = Random(args[3].toLong())
    com.eraandroid.emuera.sub.MTRandom.fixedSeed = args[3].toLong()
    val out = File(args[4])
    val log = StringBuilder()
    System.getProperty("cols")?.toInt()?.let { n ->
        val cols = when { n >= 130 -> 4; n >= 80 -> 3; n >= 40 -> 2; else -> 1 }
        com.eraandroid.emuera.config.Config.setScreenOverride(n, cols, n / cols - 1)
    }
    val exec = Executors.newSingleThreadExecutor { r -> Thread(null, r, "engine", 512L shl 20) }
    val host = HeadlessConsoleHost(1024, 700)
    val c = exec.submit<com.eraandroid.emuera.gameview.EmueraConsole> { EmueraEngine.bootSync(dir, host) }.get()
    var lastSeen: Any? = null
    val recent = ArrayDeque<String>()
    fun collect(): List<String> {
        val lines = c.snapshot().lines
        var start = 0
        if (lastSeen != null) {
            val idx = lines.indexOfLast { it === lastSeen }
            start = if (idx >= 0) idx + 1 else 0
        }
        val new = (start until lines.size).map { lines[it].toString() }
        lastSeen = lines.lastOrNull()
        for (l in new) { recent.addLast(l); if (recent.size > 80) recent.removeFirst() }
        return new
    }
    collect()
    val conv = com.eraandroid.emuera.ui.UiConverter()
    val suspicious = LinkedHashSet<String>()
    var step = 0
    var problem: String? = null
    val history = ArrayList<String>()
    while (step < steps) {
        if (host.closed) { log.append("=== game closed at step $step\n"); break }
        val state = c.consoleState
        if (state == ConsoleState.Error) { problem = "ERROR state"; break }
        if (state != ConsoleState.WaitInput) { problem = "unexpected state $state"; break }
        val req = c.currentInputRequest!!
        val input: String = if (step < prefix.size) prefix[step].let { if (it == "~") "" else it } else when (req.inputType) {
            InputType.IntValue -> {
                val btns = c.snapshot().lines.takeLast(60).flatMap { it.buttons.toList() }.filter { c.canSelect(it) && it.isInteger }
                if (btns.isNotEmpty() && rnd.nextInt(20) != 0) btns[rnd.nextInt(btns.size)].input.toString()
                else if (req.hasDefValue) "" else listOf("0", "1", "2", "100", "999", "-1")[rnd.nextInt(6)]
            }
            InputType.StrValue -> {
                val btns = c.snapshot().lines.takeLast(60).flatMap { it.buttons.toList() }.filter { c.canSelect(it) }
                if (btns.isNotEmpty() && rnd.nextBoolean()) (btns[rnd.nextInt(btns.size)].inputs ?: "") else listOf("테스트", "あ", "1", "YES")[rnd.nextInt(4)]
            }
            else -> ""
        }
        history.add(input)
        val f = exec.submit {
            c.pressEnterKey(false, input, false)
            var g = 0
            while (c.consoleState == ConsoleState.WaitInput && (c.currentInputRequest?.timelimit ?: 0) > 0 && g++ < 1000) { Thread.sleep(5); c.tick() }
        }
        try {
            f.get((System.getProperty("hangSec") ?: "30").toLong(), TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            val t = Thread.getAllStackTraces().keys.firstOrNull { it.name == "engine" }
            val vals = (System.getProperty("hangEval") ?: "").split(';').filter { it.isNotBlank() }.joinToString(" ") { ex ->
                try {
                    val wc = com.eraandroid.emuera.sub.LexicalAnalyzer.analyse(com.eraandroid.emuera.sub.StringStream(ex), com.eraandroid.emuera.sub.LexEndWith.EoL, com.eraandroid.emuera.sub.LexAnalyzeFlag.None)
                    val term = com.eraandroid.emuera.gamedata.expression.ExpressionParser.reduceExpressionTerm(wc, com.eraandroid.emuera.gamedata.expression.TermEndWith.EoL)
                    "$ex=" + term?.getIntValue(GlobalStatic.EMediator!!)
                } catch (e: Throwable) { "$ex=?($e)" }
            }
            log.append("=== hang eval: $vals\n")
            val cl = GlobalStatic.Process?.getCurrentLine
            val pos = cl?.position
            problem = "HANG after input '$input' at ${pos?.filename}:${pos?.lineNo} @${cl?.parentLabelLine?.labelName}\n" + (t?.stackTrace?.take(40)?.joinToString("\n") ?: "")
            break
        } catch (e: Exception) {
            val cause = e.cause ?: e
            problem = "EXCEPTION after input '$input': $cause\n" + cause.stackTrace.take(40).joinToString("\n")
            break
        }
        try { conv.snapshot(c) } catch (e: Exception) { problem = "UiConverter: $e\n" + e.stackTrace.take(20).joinToString("\n"); break }
        val new = collect()
        for (l in new) if (SUSPICIOUS.containsMatchIn(l) && suspicious.size < 200) suspicious.add(l)
        val bad = new.firstOrNull { it.contains("エラーが発生しました") || it.contains("에러가 발생") || it.contains("内部エラー") || it.startsWith("警告Lv") }
        if (bad != null) { problem = "error text: $bad"; break }
        step++
    }
    log.append("=== steps=$step\n")
    if (problem != null) log.append("=== PROBLEM: $problem\n")
    log.append("=== recent inputs: ${history.takeLast(30)}\n")
    log.append("=== recent output:\n")
    collect()
    for (l in recent) log.append(l).append('\n')
    log.append("=== suspicious lines: ${suspicious.size}\n")
    for (l in suspicious) log.append("?? ").append(l).append('\n')
    log.append("=== full input history:\n").append(history.joinToString("\n")).append('\n')
    out.writeText(log.toString())
    System.exit(if (problem == null) 0 else 1)
}
