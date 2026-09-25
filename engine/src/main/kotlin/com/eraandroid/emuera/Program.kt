package com.eraandroid.emuera

import java.io.File

/**
 * Emuera の Program クラスに相当。Android 版ではゲームフォルダを [setGameDir] で指定する。
 */
object Program {
    /** 1824 FileVersion 相当 */
    const val InternalEmueraVer = "1.824.0.0"

    @JvmStatic var ExeDir: String = ""
        private set
    @JvmStatic var CsvDir: String = ""
        private set
    @JvmStatic var ErbDir: String = ""
        private set
    @JvmStatic var DebugDir: String = ""
        private set
    @JvmStatic var DatDir: String = ""
        private set
    @JvmStatic var ContentDir: String = ""
        private set
    @JvmStatic var ExeName: String = "Emuera"
        private set

    @JvmField var Reboot = false
    @JvmField var AnalysisMode = false
    @JvmField var AnalysisFiles: MutableList<String>? = null
    @JvmField var debugMode = false
    val DebugMode: Boolean get() = debugMode
    @JvmStatic var StartTime: Long = System.currentTimeMillis()
        private set

    /** ゲームのルートフォルダ (csv/ erb/ があるフォルダ) を設定する */
    fun setGameDir(dir: String) {
        var d = dir.replace('\\', '/')
        if (!d.endsWith("/")) d += "/"
        ExeDir = d
        // Android/Linux はパスの大文字小文字を区別するので、実在するフォルダ名を探す
        CsvDir = d + sub(d, "csv") + "/"
        ErbDir = d + sub(d, "erb") + "/"
        DebugDir = d + sub(d, "debug") + "/"
        DatDir = d + sub(d, "dat") + "/"
        ContentDir = d + sub(d, "resources") + "/"
        StartTime = System.currentTimeMillis()
    }

    fun sub(dir: String, name: String): String =
        File(dir).listFiles()?.firstOrNull { it.isDirectory && it.name.equals(name, ignoreCase = true) }?.name ?: name

    val tickCount: Long get() = System.currentTimeMillis() - StartTime

    fun gameDirIsValid(dir: String): Boolean = File(dir, "csv").isDirectory || File(dir, "CSV").isDirectory
}
