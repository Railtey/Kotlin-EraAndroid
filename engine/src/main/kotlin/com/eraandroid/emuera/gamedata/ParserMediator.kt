package com.eraandroid.emuera.gamedata

import com.eraandroid.emuera.Program
import com.eraandroid.emuera.config.Config
import com.eraandroid.emuera.gameproc.LogicalLine
import com.eraandroid.emuera.gameview.EmueraConsole
import com.eraandroid.emuera.sub.*

/** ParserやLexicalAnalyzerなどが知りたい情報をまとめる */
object ParserMediator {
    private var console: EmueraConsole? = null
    private val warningList = ArrayList<ParserWarning>()

    fun configWarn(str: String, pos: ScriptPosition?, level: Int, stack: String?) {
        if (level < Config.DisplayWarningLevel && !Program.AnalysisMode) return
        synchronized(warningList) { warningList.add(ParserWarning(str, pos, level, stack)) }
    }

    fun initialize(console: EmueraConsole?) { this.console = console }

    @JvmStatic var RenameDic: MutableMap<String, String>? = null
        private set

    fun loadEraExRenameFile(filepath: String) {
        RenameDic?.clear()
        RenameDic = LinkedHashMap()
        val eReader = EraStreamReader(false)
        if (!FileUtil.exists(filepath) || !eReader.open(filepath)) return
        var pos: ScriptPosition? = null
        val reg = Regex("\\\\,")
        try {
            while (true) {
                val line = eReader.readLine() ?: break
                if (line.isEmpty() || line.startsWith(";")) continue
                val baseTokens = reg.split(line).toMutableList()
                if (!baseTokens.last().contains(",")) continue
                val last = baseTokens.last().split(',')
                baseTokens[baseTokens.size - 1] = last[0]
                pos = ScriptPosition(eReader.fileName, eReader.lineNo)
                val value = baseTokens.joinToString(",").trim()
                val key = "[[" + last[1].trim() + "]]"
                RenameDic!![key] = value
                pos = null
            }
        } catch (e: Exception) {
            if (pos != null) throw CodeEE(e.message ?: "", pos) else throw CodeEE(e.message ?: "")
        } finally {
            eReader.close()
        }
    }

    fun warn(str: String, pos: ScriptPosition?, level: Int) = warn(str, pos, level, null)

    fun warn(str: String, pos: ScriptPosition?, level: Int, stack: String?) {
        if (level < Config.DisplayWarningLevel && !Program.AnalysisMode) return
        val c = console
        if (c != null && !c.runERBFromMemory) synchronized(warningList) { warningList.add(ParserWarning(str, pos, level, stack)) }
    }

    /** Parser中での警告出力 level 0:軽微なミス.1:無視できる行.2:行が実行されなければ無害.3:致命的 */
    fun warn(str: String, line: LogicalLine?, level: Int, isError: Boolean, isBackComp: Boolean) = warn(str, line, level, isError, isBackComp, null)

    fun warn(str: String, line: LogicalLine?, level: Int, isError: Boolean, isBackComp: Boolean, stack: String?) {
        if (isError && line != null) {
            line.isError = true
            line.errMes = str
        }
        if (level < Config.DisplayWarningLevel && !Program.AnalysisMode) return
        if (isBackComp && !Config.WarnBackCompatibility) return
        val c = console
        if (c != null && !c.runERBFromMemory) synchronized(warningList) { warningList.add(ParserWarning(str, line?.position, level, stack)) }
    }

    val hasWarning: Boolean get() = warningList.isNotEmpty()

    fun clearWarningList() = synchronized(warningList) { warningList.clear() }

    fun flushWarningList() {
        val c = console ?: return
        val copy = synchronized(warningList) { ArrayList(warningList).also { warningList.clear() } }
        for (w in copy) {
            c.printWarning(w.mes, w.pos, w.level)
            w.stack?.split('\n')?.forEach { c.printSystemLine(it) }
        }
    }

    private class ParserWarning(val mes: String, val pos: ScriptPosition?, val level: Int, val stack: String?)
}

class DefineMacro(val keyword: String, val statement: WordCollection, val argCount: Int) {
    val hasArguments: Boolean = argCount != 0
    val idWord: IdentifierWord?
    val isNull: Boolean = statement.collection.isEmpty()

    init {
        statement.pointer = 0
        idWord = if (statement.collection.size == 1) statement.current as? IdentifierWord else null
    }
}
