package com.eraandroid.emuera.sub

import com.eraandroid.emuera.config.Config
import com.eraandroid.emuera.gamedata.ParserMediator
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.Charset

/** Reads text files the way .NET StreamReader does: BOM detection first, then the configured encoding. */
object TextFileReader {
    fun open(file: File, fallback: Charset = Config.Encode): BufferedReader {
        val bytes = file.readBytes()
        var offset = 0
        val cs: Charset = when {
            bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() -> { offset = 3; Charsets.UTF_8 }
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> { offset = 2; Charsets.UTF_16LE }
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> { offset = 2; Charsets.UTF_16BE }
            else -> fallback
        }
        return BufferedReader(InputStreamReader(bytes.inputStream(offset, bytes.size - offset), cs))
    }

    fun readAllLines(file: File): List<String> = open(file).use { r -> r.readLines() }
}

class EraStreamReader(private val useRename: Boolean) : AutoCloseable {
    private var filepath: String? = null
    private var filename: String? = null
    private var curNo = 0
    private var nextNo = 0
    private var reader: BufferedReader? = null

    fun open(path: String): Boolean = open(path, File(path).name)

    fun open(path: String, name: String): Boolean {
        filepath = path
        filename = name
        nextNo = 0
        curNo = 0
        try {
            reader = TextFileReader.open(FileUtil.resolve(path))
        } catch (e: Exception) {
            close()
            return false
        }
        return true
    }

    fun readLine(): String? {
        nextNo++
        curNo = nextNo
        return reader!!.readLine()
    }

    private fun rename(line: String): String {
        var l = line
        if (useRename && l.indexOf("[[") >= 0 && l.indexOf("]]") >= 0) {
            ParserMediator.RenameDic?.let { dic -> for ((k, v) in dic) l = l.replace(k, v) }
        }
        return l
    }

    /** 次の有効な行を読む。 */
    fun readEnabledLine(disabled: Boolean = false): StringStream? {
        var line: String?
        var st: StringStream
        curNo = nextNo
        while (true) {
            line = reader!!.readLine()
            curNo++
            nextNo++
            if (line == null) return null
            if (line.isEmpty()) continue
            line = rename(line)
            st = StringStream(line)
            LexicalAnalyzer.skipWhiteSpace(st)
            if (st.eos) continue
            if (!disabled) {
                if (st.current == '}')
                    throw CodeEE("予期しない行連結終端記号'}'が見つかりました", ScriptPosition(filename, curNo))
                if (st.current == '{') {
                    if (line.trim() != "{")
                        throw CodeEE("行連結始端記号'{'の行に'{'以外の文字を含めることはできません", ScriptPosition(filename, curNo))
                    break
                }
            }
            return st
        }
        val b = StringBuilder()
        while (true) {
            line = reader!!.readLine()
            nextNo++
            if (line == null)
                throw CodeEE("行連結始端記号'{'が使われましたが終端記号'}'が見つかりません", ScriptPosition(filename, curNo))
            line = rename(line)
            val test = line.trimStart()
            if (test.isNotEmpty()) {
                if (test[0] == '}') {
                    if (test.trim() != "}")
                        throw CodeEE("行連結終端記号'}'の行に'}'以外の文字を含めることはできません", ScriptPosition(filename, nextNo))
                    break
                }
                if (test[0] == '{' && test.length == 1)
                    throw CodeEE("予期しない行連結始端記号'{'が見つかりました", ScriptPosition(filename, nextNo))
            }
            b.append(line)
            b.append(" ")
        }
        st = StringStream(b.toString())
        LexicalAnalyzer.skipWhiteSpace(st)
        return st
    }

    /** 直前に読んだ行の行番号 */
    val lineNo: Int get() = curNo
    val fileName: String? get() = filename

    override fun close() {
        try { reader?.close() } catch (_: Exception) {}
        reader = null
        filepath = null
        filename = null
    }
}
