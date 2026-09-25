package com.eraandroid.emuera.gameproc

import com.eraandroid.emuera.IdentifierDictionary
import com.eraandroid.emuera.config.Config
import com.eraandroid.emuera.gamedata.DefineMacro
import com.eraandroid.emuera.gamedata.ParserMediator
import com.eraandroid.emuera.gamedata.variable.VariableToken
import com.eraandroid.emuera.gameview.EmueraConsole
import com.eraandroid.emuera.sub.*
import java.util.ArrayDeque

class HeaderFileLoader(private val output: EmueraConsole, private val idDic: IdentifierDictionary, private val parentProcess: Process) {
    private var noError = true
    private val dimlines = ArrayDeque<DimLineWC>()

    fun loadHeaderFiles(headerDir: String, displayReport: Boolean): Boolean {
        val headerFiles = Config.getFiles(headerDir, "*.ERH")
        var ok = true
        dimlines.clear()
        try {
            for ((filename, file) in headerFiles) {
                if (displayReport) output.printSystemLine(filename + "読み込み中・・・")
                ok = loadHeaderFile(file, filename)
                if (!ok) break
            }
            if (dimlines.isNotEmpty()) ok = ok and analyzeSharpDimLines()
            dimlines.clear()
        } finally {
            ParserMediator.flushWarningList()
        }
        return ok
    }

    private fun loadHeaderFile(filepath: String, filename: String): Boolean {
        var position: ScriptPosition? = null
        val eReader = EraStreamReader(true)
        if (!eReader.open(filepath, filename)) throw CodeEE(eReader.fileName + "のオープンに失敗しました")
        try {
            while (true) {
                val st = eReader.readEnabledLine() ?: break
                if (!noError) return false
                position = ScriptPosition(filename, eReader.lineNo)
                LexicalAnalyzer.skipWhiteSpace(st)
                if (st.current != '#') throw CodeEE("ヘッダーの中に#で始まらない行があります", position)
                st.shiftNext()
                var sharpID = LexicalAnalyzer.readSingleIdentifier(st)
                if (Config.ICFunction) sharpID = sharpID.uppercase()
                LexicalAnalyzer.skipWhiteSpace(st)
                when (sharpID) {
                    "DEFINE" -> analyzeSharpDefine(st, position)
                    "FUNCTION", "FUNCTIONS" -> throw NotImplCodeEE()
                    "DIM", "DIMS" -> {
                        val wc = LexicalAnalyzer.analyse(st, LexEndWith.EoL, LexAnalyzeFlag.AllowAssignment)
                        dimlines.add(DimLineWC(wc, sharpID == "DIMS", false, position))
                    }
                    else -> throw CodeEE("#${sharpID}は解釈できないプリプロセッサです", position)
                }
            }
        } catch (e: CodeEE) {
            if (e.position != null) position = e.position
            ParserMediator.warn(e.message ?: "", position, 2)
            return false
        } finally {
            eReader.close()
        }
        return true
    }

    private fun analyzeSharpDefine(st: StringStream, position: ScriptPosition?) {
        var srcID = LexicalAnalyzer.readSingleIdentifier(st)
        if (srcID.isEmpty()) throw CodeEE("置換元の識別子がありません", position)
        if (Config.ICVariable) srcID = srcID.uppercase()
        val err = idDic.checkUserMacroName(srcID)
        if (err != null) {
            ParserMediator.warn(err.first, position, err.second)
            if (err.second >= 2) { noError = false; return }
        }
        val hasArg = st.current == '('
        val wc = LexicalAnalyzer.analyse(st, LexEndWith.EoL, LexAnalyzeFlag.AllowAssignment)
        if (wc.eol) {
            idDic.addMacro(DefineMacro(srcID, WordCollection(), 0))
            return
        }
        val argID = ArrayList<String>()
        if (hasArg) {
            wc.shiftNext()
            if (wc.current.type == ')') throw CodeEE("関数型マクロの引数を0個にすることはできません", position)
            while (!wc.eol) {
                val word = wc.current as? IdentifierWord ?: throw CodeEE("置換元の引数指定の書式が間違っています", position)
                word.setIsMacro()
                val id = word.code
                if (argID.contains(id)) throw CodeEE("置換元の引数に同じ文字が2回以上使われています", position)
                argID.add(id)
                wc.shiftNext()
                if (wc.current.type == ',') { wc.shiftNext(); continue }
                if (wc.current.type == ')') break
                throw CodeEE("置換元の引数指定の書式が間違っています", position)
            }
            if (wc.eol) throw CodeEE("')'が閉じられていません", position)
            wc.shiftNext()
        }
        if (wc.eol) throw CodeEE("置換先の式がありません", position)
        val destWc = WordCollection()
        while (!wc.eol) { destWc.add(wc.current); wc.shiftNext() }
        if (hasArg) throw CodeEE("関数型マクロは宣言できません", position)
        idDic.addMacro(DefineMacro(srcID, destWc, argID.size))
    }

    private fun analyzeSharpDimLines(): Boolean {
        var ok = true
        var tryAgain = true
        while (dimlines.isNotEmpty()) {
            val count = dimlines.size
            for (i in 0 until count) {
                val dimline = dimlines.poll()
                try {
                    val data = UserDefinedVariableData.create(dimline)
                    if (data.Reference) throw NotImplCodeEE()
                    val v: VariableToken = if (data.CharaData) parentProcess.vEvaluator.variableData.createUserDefCharaVariable(data)
                    else parentProcess.vEvaluator.variableData.createUserDefVariable(data)
                    idDic.addUseDefinedVariable(v)
                } catch (e: IdentifierNotFoundCodeEE) {
                    if (tryAgain) {
                        dimline.wc.pointer = 0
                        dimlines.add(dimline)
                    } else {
                        ParserMediator.warn(e.message ?: "", dimline.sc, 2)
                        ok = true
                    }
                } catch (e: CodeEE) {
                    ParserMediator.warn(e.message ?: "", dimline.sc, 2)
                    ok = false
                }
            }
            if (dimlines.size == count) tryAgain = false
        }
        return ok
    }
}
