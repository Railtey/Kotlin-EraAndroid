package com.eraandroid.emuera

import com.eraandroid.emuera.config.ConfigData
import com.eraandroid.emuera.content.AppContents
import com.eraandroid.emuera.gameview.ConsoleHost
import com.eraandroid.emuera.gameview.EmueraConsole
import java.util.concurrent.Future
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit

/**
 * Android 版の入口 (Emuera の MainWindow が担っていたゲーム起動・入力の受け渡し)。
 *
 * エンジンはすべて専用スレッド 1 本の上で動く。UI からの操作は [post] で渡し、
 * 表示内容は [EmueraConsole.snapshot] で読む。
 */
class EmueraEngine(val gameDir: String, val host: ConsoleHost) {
    private val executor = ScheduledThreadPoolExecutor(1, ThreadFactory { r ->
        // 深い再帰 (式の解析・関数呼び出し) に備えてスタックを大きく取る
        Thread(null, r, "emuera-engine", 256L * 1024 * 1024).apply { isDaemon = true }
    })
    private var tickTask: ScheduledFuture<*>? = null

    @Volatile var console: EmueraConsole? = null
        private set

    /** ゲームを読み込んで開始する */
    fun start(): Future<*> = executor.submit {
        boot()
        tickTask = executor.scheduleWithFixedDelay({ safe { console?.tick() } }, 50, 50, TimeUnit.MILLISECONDS)
    }

    /** エンジンスレッドで [block] を実行する */
    fun post(block: (EmueraConsole) -> Unit): Future<*> = executor.submit { console?.let { c -> safe { block(c) } } }

    /** 入力 (数値・文字列・空文字=Enter) を送る */
    fun input(text: String?) = post { it.pressEnterKey(false, text, false) }

    fun shutdown() {
        tickTask?.cancel(false)
        executor.shutdownNow()
    }

    private fun boot() {
        resetStatics()
        Program.setGameDir(gameDir)
        ConfigData.instance.loadConfig()
        val c = EmueraConsole(host)
        console = c
        safe { c.initialize() }
    }

    private inline fun safe(block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            // エンジン内の想定外の例外で UI ごと落ちないようにする
            try {
                console?.printError("内部エラー: " + t.javaClass.simpleName + ": " + (t.message ?: ""))
                console?.throwError(false)
            } catch (_: Throwable) {
            }
            t.printStackTrace()
        }
    }

    companion object {
        /** 前のゲームの状態を捨てる */
        fun resetStatics() {
            GlobalStatic.reset()
            ConfigData.reset()
            AppContents.unloadContents()
        }

        /** テスト用: 呼び出したスレッド上で同期的に起動する */
        fun bootSync(gameDir: String, host: ConsoleHost): EmueraConsole {
            resetStatics()
            Program.setGameDir(gameDir)
            ConfigData.instance.loadConfig()
            val c = EmueraConsole(host)
            c.initialize()
            return c
        }
    }
}
