package com.eraandroid.emuera.sub

import com.eraandroid.emuera.config.Config
import java.io.BufferedReader
import java.io.File
import java.io.OutputStreamWriter
import java.io.PrintWriter

enum class EraDataState { OK, FILENOTFOUND, GAME_ERROR, VIRSION_ERROR, ETC_ERROR }

class EraDataResult {
    var state = EraDataState.OK
    var dataMes = ""
}

/** セーブデータ読み取り */
class EraDataReader(file: File) : AutoCloseable {
    private var reader: BufferedReader? = TextFileReader.open(file)

    private fun r(): BufferedReader = reader ?: throw FileEE("無効なストリームです")
    private fun parseLong(s: String): Long = s.trim().toLongOrNull() ?: throw FileEE("数値として認識できません")

    fun readString(): String = r().readLine() ?: throw FileEE("読み取るべき文字列がありません")

    fun readInt64(): Long {
        val str = r().readLine() ?: throw FileEE("読み取るべき数値がありません")
        return parseLong(str)
    }

    fun readInt64Array(array: LongArray) {
        val rd = r()
        var i = -1
        while (true) {
            i++
            val str = rd.readLine() ?: throw FileEE("予期しないセーブデータの終端です")
            if (str == FINISHER) break
            if (i >= array.size) continue
            array[i] = parseLong(str)
        }
        while (i < array.size) { array[i] = 0; i++ }
    }

    fun readStringArray(array: Array<String?>) {
        val rd = r()
        var i = -1
        while (true) {
            i++
            val str = rd.readLine() ?: throw FileEE("予期しないセーブデータの終端です")
            if (str == FINISHER) break
            if (i >= array.size) continue
            array[i] = str
        }
        while (i < array.size) { array[i] = ""; i++ }
    }

    var dataVersion = -1
        private set

    fun seekEmuStart(): Boolean {
        val rd = r()
        while (true) {
            val str = rd.readLine() ?: return false
            when (str) {
                EMU_1700_START -> { dataVersion = 1700; return true }
                EMU_1708_START -> { dataVersion = 1708; return true }
                EMU_1729_START -> { dataVersion = 1729; return true }
                EMU_1803_START -> { dataVersion = 1803; return true }
                EMU_1808_START -> { dataVersion = 1808; return true }
            }
        }
    }

    private fun nextLine(): String = r().readLine() ?: throw FileEE("予期しないセーブデータの終端です")

    fun readStringExtended(): Map<String, String> {
        val ret = LinkedHashMap<String, String>()
        while (true) {
            val str = nextLine()
            if (str == FINISHER) throw FileEE("セーブデータの形式が不正です")
            if (str == EMU_SEPARATOR) break
            val index = str.indexOf(':')
            if (index < 0) throw FileEE("セーブデータの形式が不正です")
            val key = str.substring(0, index)
            if (!ret.containsKey(key)) ret[key] = str.substring(index + 1)
        }
        return ret
    }

    fun readInt64Extended(): Map<String, Long> {
        val ret = LinkedHashMap<String, Long>()
        while (true) {
            val str = nextLine()
            if (str == FINISHER) throw FileEE("セーブデータの形式が不正です")
            if (str == EMU_SEPARATOR) break
            val index = str.indexOf(':')
            if (index < 0) throw FileEE("セーブデータの形式が不正です")
            val key = str.substring(0, index)
            val v = parseLong(str.substring(index + 1))
            if (!ret.containsKey(key)) ret[key] = v
        }
        return ret
    }

    fun readInt64ArrayExtended(): Map<String, List<Long>> {
        val ret = LinkedHashMap<String, List<Long>>()
        while (true) {
            var str = nextLine()
            if (str == FINISHER) throw FileEE("セーブデータの形式が不正です")
            if (str == EMU_SEPARATOR) break
            val key = str
            val list = ArrayList<Long>()
            while (true) {
                str = nextLine()
                if (str == EMU_SEPARATOR) throw FileEE("セーブデータの形式が不正です")
                if (str == FINISHER) break
                list.add(parseLong(str))
            }
            if (!ret.containsKey(key)) ret[key] = list
        }
        return ret
    }

    fun readStringArrayExtended(): Map<String, List<String>> {
        val ret = LinkedHashMap<String, List<String>>()
        while (true) {
            var str = nextLine()
            if (str == FINISHER) throw FileEE("セーブデータの形式が不正です")
            if (str == EMU_SEPARATOR) break
            val key = str
            val list = ArrayList<String>()
            while (true) {
                str = nextLine()
                if (str == EMU_SEPARATOR) throw FileEE("セーブデータの形式が不正です")
                if (str == FINISHER) break
                list.add(str)
            }
            if (!ret.containsKey(key)) ret[key] = list
        }
        return ret
    }

    private fun parseCsvLongs(str: String): LongArray {
        if (str.isEmpty()) return LongArray(0)
        val tokens = str.split(',')
        return LongArray(tokens.size) { x -> tokens[x].trim().toLongOrNull() ?: throw FileEE(tokens[x] + "は数値として認識できません") }
    }

    fun readInt64Array2DExtended(): Map<String, List<LongArray>> {
        val ret = LinkedHashMap<String, List<LongArray>>()
        if (dataVersion < 1708) return ret
        while (true) {
            var str = nextLine()
            if (str == FINISHER) throw FileEE("セーブデータの形式が不正です")
            if (str == EMU_SEPARATOR) break
            val key = str
            val list = ArrayList<LongArray>()
            while (true) {
                str = nextLine()
                if (str == EMU_SEPARATOR) throw FileEE("セーブデータの形式が不正です")
                if (str == FINISHER) break
                list.add(parseCsvLongs(str))
            }
            if (!ret.containsKey(key)) ret[key] = list
        }
        return ret
    }

    fun readStringArray2DExtended(): Map<String, List<Array<String>>> {
        val ret = LinkedHashMap<String, List<Array<String>>>()
        if (dataVersion < 1708) return ret
        while (true) {
            val str = nextLine()
            if (str == FINISHER) throw FileEE("セーブデータの形式が不正です")
            if (str == EMU_SEPARATOR) break
            throw FileEE("StringArray2Dのロードには対応していません")
        }
        return ret
    }

    fun readInt64Array3DExtended(): Map<String, List<List<LongArray>>> {
        val ret = LinkedHashMap<String, List<List<LongArray>>>()
        if (dataVersion < 1729) return ret
        while (true) {
            var str = nextLine()
            if (str == FINISHER) throw FileEE("セーブデータの形式が不正です")
            if (str == EMU_SEPARATOR) break
            val key = str
            val list = ArrayList<List<LongArray>>()
            while (true) {
                str = nextLine()
                if (str == EMU_SEPARATOR) throw FileEE("セーブデータの形式が不正です")
                if (str == FINISHER) break
                if (str.contains("{")) {
                    val tokenList = ArrayList<LongArray>()
                    while (true) {
                        str = nextLine()
                        if (str == "}") break
                        tokenList.add(parseCsvLongs(str))
                    }
                    list.add(tokenList)
                }
            }
            if (!ret.containsKey(key)) ret[key] = list
        }
        return ret
    }

    fun readStringArray3DExtended(): Map<String, List<List<Array<String>>>> {
        val ret = LinkedHashMap<String, List<List<Array<String>>>>()
        if (dataVersion < 1729) return ret
        while (true) {
            val str = nextLine()
            if (str == FINISHER) throw FileEE("セーブデータの形式が不正です")
            if (str == EMU_SEPARATOR) break
            throw FileEE("StringArray2Dのロードには対応していません")
        }
        return ret
    }

    override fun close() {
        try { reader?.close() } catch (_: Exception) {}
        reader = null
    }

    companion object {
        const val FINISHER = "__FINISHED"
        const val EMU_1700_START = "__EMUERA_STRAT__"
        const val EMU_1708_START = "__EMUERA_1708_STRAT__"
        const val EMU_1729_START = "__EMUERA_1729_STRAT__"
        const val EMU_1803_START = "__EMUERA_1803_STRAT__"
        const val EMU_1808_START = "__EMUERA_1808_STRAT__"
        const val EMU_SEPARATOR = "__EMU_SEPARATOR__"
    }
}

/** セーブデータ書き込み */
class EraDataWriter(file: File) : AutoCloseable {
    private var writer: PrintWriter?

    init {
        val os = file.outputStream()
        val cs = Config.SaveEncode
        if (cs == Charsets.UTF_8) os.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
        writer = PrintWriter(OutputStreamWriter(os, cs))
    }

    private fun w(): PrintWriter = writer ?: throw FileEE("無効なストリームです")
    private fun line(s: String) { w().print(s); w().print("\r\n") }

    fun write(integer: Long) = line(integer.toString())
    fun write(str: String?) = line(str ?: "")

    fun write(array: LongArray) {
        var count = -1
        for (i in array.indices) if (array[i] != 0L) count = i
        count++
        for (i in 0 until count) line(array[i].toString())
        line(FINISHER)
    }

    fun write(array: Array<String?>) {
        var count = -1
        for (i in array.indices) if (!array[i].isNullOrEmpty()) count = i
        count++
        for (i in 0 until count) line(array[i] ?: "")
        line(FINISHER)
    }

    fun emuStart() = line(EMU_START)
    fun emuSeparete() = line(EMU_SEPARATOR)

    fun writeExtended(key: String, value: Long) {
        if (value == 0L) return
        line("$key:$value")
    }

    fun writeExtended(key: String, value: String?) {
        if (value.isNullOrEmpty()) return
        line("$key:$value")
    }

    fun writeExtended(key: String, array: LongArray) {
        var count = -1
        for (i in array.indices) if (array[i] != 0L) count = i
        count++
        if (count == 0) return
        line(key)
        for (i in 0 until count) line(array[i].toString())
        line(FINISHER)
    }

    fun writeExtended(key: String, array: Array<String?>) {
        var count = -1
        for (i in array.indices) if (!array[i].isNullOrEmpty()) count = i
        count++
        if (count == 0) return
        line(key)
        for (i in 0 until count) line(array[i] ?: "")
        line(FINISHER)
    }

    fun writeExtended2D(key: String, array2D: Array<LongArray>) {
        var countX = 0
        val length0 = array2D.size
        val countY = IntArray(length0)
        for (x in 0 until length0) for (y in array2D[x].indices) if (array2D[x][y] != 0L) { countX = x + 1; countY[x] = y + 1 }
        if (countX == 0) return
        line(key)
        for (x in 0 until countX) {
            if (countY[x] == 0) { line(""); continue }
            val b = StringBuilder()
            for (y in 0 until countY[x]) { b.append(array2D[x][y]); if (y != countY[x] - 1) b.append(",") }
            line(b.toString())
        }
        line(FINISHER)
    }

    fun writeExtended3D(key: String, array3D: Array<Array<LongArray>>) {
        var countX = 0
        val length0 = array3D.size
        val countY = IntArray(length0)
        val countZ = Array(length0) { IntArray(if (array3D[it].isNotEmpty()) array3D[it].size else 0) }
        for (x in 0 until length0) for (y in array3D[x].indices) for (z in array3D[x][y].indices)
            if (array3D[x][y][z] != 0L) { countX = x + 1; countY[x] = y + 1; countZ[x][y] = z + 1 }
        if (countX == 0) return
        line(key)
        for (x in 0 until countX) {
            line("$x{")
            if (countY[x] == 0) { line("}"); continue }
            for (y in 0 until countY[x]) {
                if (countZ[x][y] == 0) { line(""); continue }
                val b = StringBuilder()
                for (z in 0 until countZ[x][y]) { b.append(array3D[x][y][z]); if (z != countZ[x][y] - 1) b.append(",") }
                line(b.toString())
            }
            line("}")
        }
        line(FINISHER)
    }

    override fun close() {
        try { writer?.close() } catch (_: Exception) {}
        writer = null
    }

    companion object {
        const val FINISHER = EraDataReader.FINISHER
        const val EMU_START = EraDataReader.EMU_1808_START
        const val EMU_SEPARATOR = EraDataReader.EMU_SEPARATOR
    }
}
