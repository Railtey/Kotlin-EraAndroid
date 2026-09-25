package com.eraandroid.emuera.gameview

import com.eraandroid.emuera.config.EraColor

/** EmueraConsole が UI (Android) に要求する機能 */
interface ConsoleHost {
    /** 表示内容が変わったので再描画してほしい */
    fun requestRedraw()
    fun setWindowTitle(title: String)
    /** QUIT などでゲームを閉じる */
    fun closeGame()
    /** @REBOOT */
    fun reboot()
    /** 描画領域の大きさ (Emuera の仮想ピクセル) */
    val clientWidth: Int
    val clientHeight: Int
    /** アプリが前面にあるか */
    val isActive: Boolean
    /** 無限ループ警告。true を返すと強制終了 */
    fun confirmInfiniteLoop(message: String): Boolean = false
    fun clearTextBox() {}
    fun setToolTipColor(fore: EraColor, back: EraColor) {}
    fun setToolTipDelay(delay: Int) {}
    fun setToolTipDuration(duration: Int) {}
    /** ロード中の進捗 (ファイル名) */
    fun reportLoadProgress(text: String) {}
}

/** テスト用の何もしないホスト */
open class HeadlessConsoleHost(override val clientWidth: Int = 760, override val clientHeight: Int = 480) : ConsoleHost {
    var closed = false
    override fun requestRedraw() {}
    override fun setWindowTitle(title: String) {}
    override fun closeGame() { closed = true }
    override fun reboot() {}
    override val isActive: Boolean get() = true
}
