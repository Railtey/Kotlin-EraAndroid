package com.eraandroid.emuera.sub

import java.io.DataInputStream
import java.io.EOFException
import java.io.File
import java.io.InputStream

enum class EraSaveFileType(val code: Int) {
    Normal(0x00), Global(0x01), Var(0x02), CharVar(0x03);
    companion object { fun of(b: Int) = entries.firstOrNull { it.code == b } }
}

enum class EraSaveDataType(val code: Int) {
    Int(0x00), IntArray(0x01), IntArray2D(0x02), IntArray3D(0x03),
    Str(0x10), StrArray(0x11), StrArray2D(0x12), StrArray3D(0x13),
    Separator(0xFD), EOC(0xFE), EOF(0xFF);
    companion object { fun of(b: kotlin.Int) = entries.firstOrNull { it.code == b } }
}

/** EraBinaryData中のマジックナンバーなバイト */
object Ebdb {
    const val Byte = 0xCF
    const val Int16 = 0xD0
    const val Int32 = 0xD1
    const val Int64 = 0xD2
    const val String = 0xD8
    const val EoA1 = 0xE0
    const val EoA2 = 0xE1
    const val Zero = 0xF0
    const val ZeroA1 = 0xF1
    const val ZeroA2 = 0xF2
    const val EoD = 0xFF
}

object EraBDConst {
    const val Header: Long = 0x0A1A0A0D41524589L
    const val Version1808 = 1808
    const val DataCount = 0
}

/** Little-endian reader compatible with .NET BinaryReader (strings: 7-bit length prefix, UTF-16LE). */
class LEBinaryReader(input: InputStream) : AutoCloseable {
    private val din = DataInputStream(input.buffered())
    fun readByte(): Int { val b = din.read(); if (b < 0) throw EOFException(); return b }
    fun readInt16(): Short = (readByte() or (readByte() shl 8)).toShort()
    fun readInt32(): Int = readByte() or (readByte() shl 8) or (readByte() shl 16) or (readByte() shl 24)
    fun readUInt32(): Long = readInt32().toLong() and 0xFFFFFFFFL
    fun readInt64(): Long {
        var r = 0L
        for (i in 0 until 8) r = r or (readByte().toLong() shl (8 * i))
        return r
    }
    fun read7BitEncodedInt(): Int {
        var count = 0
        var shift = 0
        var b: Int
        do {
            if (shift == 35) throw FileEE("バイナリデータの異常")
            b = readByte()
            count = count or ((b and 0x7F) shl shift)
            shift += 7
        } while ((b and 0x80) != 0)
        return count
    }
    fun readString(): String {
        val len = read7BitEncodedInt()
        val bytes = ByteArray(len)
        din.readFully(bytes)
        return String(bytes, Charsets.UTF_16LE)
    }
    override fun close() = din.close()
}

/** 1808追加 新しいデータ保存形式 */
abstract class EraBinaryDataReader protected constructor(
    protected var reader: LEBinaryReader?,
    protected val version: Int,
    protected val data: LongArray
) : AutoCloseable {
    abstract val readerVersion: Int
    abstract fun readFileType(): EraSaveFileType
    abstract fun readInt64(): Long
    abstract fun readString(): String
    abstract fun readInt(): Long
    abstract fun readIntArray(refArray: LongArray?, needInit: Boolean)
    abstract fun readIntArray2D(refArray: Array<LongArray>?, needInit: Boolean)
    abstract fun readIntArray3D(refArray: Array<Array<LongArray>>?, needInit: Boolean)
    abstract fun readStrArray(refArray: Array<String?>?, needInit: Boolean)
    abstract fun readStrArray2D(refArray: Array<Array<String?>>?, needInit: Boolean)
    abstract fun readStrArray3D(refArray: Array<Array<Array<String?>>>?, needInit: Boolean)
    abstract fun readVariableCode(): Pair<String?, EraSaveDataType>

    override fun close() {
        try { reader?.close() } catch (_: Exception) {}
        reader = null
    }

    companion object {
        /** 不正なファイルの場合はnullを返す・例外は投げない */
        fun createReader(file: File): EraBinaryDataReader? {
            try {
                if (!file.exists() || file.length() < 16) return null
                val reader = LEBinaryReader(file.inputStream())
                if (reader.readInt64() != EraBDConst.Header) { reader.close(); return null }
                val version = reader.readUInt32().toInt()
                val datacount = reader.readUInt32().toInt()
                val data = LongArray(datacount) { reader.readUInt32() }
                if (version == EraBDConst.Version1808) return EraBinaryDataReader1808(reader, version, data)
                reader.close()
                return null
            } catch (e: Exception) {
                return null
            }
        }
    }

    private class EraBinaryDataReader1808(stream: LEBinaryReader, ver: Int, buf: LongArray) : EraBinaryDataReader(stream, ver, buf) {
        private val r get() = reader!!
        override val readerVersion: Int get() = 1808

        override fun readFileType(): EraSaveFileType {
            val type = r.readByte()
            return EraSaveFileType.of(type) ?: throw FileEE("ファイルデータ型異常")
        }

        private fun mReadInt(): Long {
            val b = r.readByte()
            if (b <= Ebdb.Byte) return b.toLong()
            if (b == Ebdb.Int16) return r.readInt16().toLong()
            if (b == Ebdb.Int32) return r.readInt32().toLong()
            if (b == Ebdb.Int64) return r.readInt64()
            throw FileEE("バイナリデータの異常")
        }

        private fun readValue(b: Int): Long = when {
            b <= Ebdb.Byte -> b.toLong()
            b == Ebdb.Int16 -> r.readInt16().toLong()
            b == Ebdb.Int32 -> r.readInt32().toLong()
            b == Ebdb.Int64 -> r.readInt64()
            else -> throw FileEE("バイナリデータの異常")
        }

        override fun readInt64(): Long = r.readInt64()

        override fun readVariableCode(): Pair<String?, EraSaveDataType> {
            val b = r.readByte()
            val type = EraSaveDataType.of(b) ?: throw FileEE("バイナリデータの異常")
            if (type == EraSaveDataType.EOC || type == EraSaveDataType.EOF || type == EraSaveDataType.Separator)
                return Pair(null, type)
            return Pair(r.readString(), type)
        }

        override fun readInt(): Long = mReadInt()
        override fun readString(): String = r.readString()

        override fun readIntArray(refArray: LongArray?, needInit: Boolean) {
            var ori: LongArray? = null
            var x = 0
            val saveLength0 = r.readInt32()
            var arr = refArray ?: LongArray(saveLength0)
            var length0 = arr.size
            if (length0 < saveLength0) {
                ori = arr
                arr = LongArray(maxOf(length0, saveLength0))
                length0 = minOf(length0, saveLength0)
            }
            while (true) {
                val b = r.readByte()
                if (b == Ebdb.EoD) break
                if (b == Ebdb.Zero) {
                    val cnt = mReadInt().toInt()
                    if (needInit) for (i in 0 until cnt) arr[x + i] = 0
                    x += cnt
                    continue
                }
                arr[x] = readValue(b)
                x++
            }
            if (needInit) while (x < length0) { arr[x] = 0; x++ }
            if (ori != null) for (i in 0 until length0) ori[i] = arr[i]
        }

        override fun readIntArray2D(refArray: Array<LongArray>?, needInit: Boolean) {
            var ori: Array<LongArray>? = null
            var x = 0
            var y = 0
            val saveLength0 = r.readInt32()
            val saveLength1 = r.readInt32()
            var arr = refArray ?: Array(saveLength0) { LongArray(saveLength1) }
            var length0 = arr.size
            var length1 = if (arr.isNotEmpty()) arr[0].size else 0
            if (length0 < saveLength0 || length1 < saveLength1) {
                ori = arr
                val l1 = maxOf(length1, saveLength1)
                arr = Array(maxOf(length0, saveLength0)) { LongArray(l1) }
                length0 = minOf(length0, saveLength0)
                length1 = minOf(length1, saveLength1)
            }
            while (true) {
                val b = r.readByte()
                if (b == Ebdb.EoD) break
                if (b == Ebdb.ZeroA1) {
                    val cnt = mReadInt().toInt()
                    if (needInit) for (i in 0 until cnt) for (yy in 0 until length1) arr[x + i][yy] = 0
                    x += cnt; y = 0; continue
                }
                if (b == Ebdb.EoA1) {
                    if (needInit) while (y < length1) { arr[x][y] = 0; y++ }
                    x++; y = 0; continue
                }
                if (b == Ebdb.Zero) {
                    val cnt = mReadInt().toInt()
                    if (needInit) for (i in 0 until cnt) arr[x][y + i] = 0
                    y += cnt; continue
                }
                arr[x][y] = readValue(b)
                y++
            }
            if (needInit) {
                while (x < length0) { while (y < length1) { arr[x][y] = 0; y++ }; y = 0; x++ }
            }
            if (ori != null) for (i in 0 until length0) for (j in 0 until length1) ori[i][j] = arr[i][j]
        }

        override fun readIntArray3D(refArray: Array<Array<LongArray>>?, needInit: Boolean) {
            var ori: Array<Array<LongArray>>? = null
            var x = 0; var y = 0; var z = 0
            val s0 = r.readInt32(); val s1 = r.readInt32(); val s2 = r.readInt32()
            var arr = refArray ?: Array(s0) { Array(s1) { LongArray(s2) } }
            var l0 = arr.size
            var l1 = if (l0 > 0) arr[0].size else 0
            var l2 = if (l1 > 0) arr[0][0].size else 0
            if (l0 < s0 || l1 < s1 || l2 < s2) {
                ori = arr
                val m1 = maxOf(l1, s1); val m2 = maxOf(l2, s2)
                arr = Array(maxOf(l0, s0)) { Array(m1) { LongArray(m2) } }
                l0 = minOf(l0, s0); l1 = minOf(l1, s1); l2 = minOf(l2, s2)
            }
            while (true) {
                val b = r.readByte()
                if (b == Ebdb.EoD) break
                if (b == Ebdb.ZeroA2) {
                    val cnt = mReadInt().toInt()
                    if (needInit) for (i in 0 until cnt) for (yy in 0 until l1) for (zz in 0 until l2) arr[x + i][yy][zz] = 0
                    x += cnt; y = 0; z = 0; continue
                }
                if (b == Ebdb.EoA2) {
                    if (needInit) { while (y < l1) { while (z < l2) { arr[x][y][z] = 0; z++ }; z = 0; y++ } }
                    x++; y = 0; z = 0; continue
                }
                if (b == Ebdb.ZeroA1) {
                    val cnt = mReadInt().toInt()
                    if (needInit) for (i in 0 until cnt) for (zz in 0 until l2) arr[x][y + i][zz] = 0
                    y += cnt; z = 0; continue
                }
                if (b == Ebdb.EoA1) {
                    if (needInit) while (z < l2) { arr[x][y][z] = 0; z++ }
                    y++; z = 0; continue
                }
                if (b == Ebdb.Zero) {
                    val cnt = mReadInt().toInt()
                    if (needInit) for (i in 0 until cnt) arr[x][y][z + i] = 0
                    z += cnt; continue
                }
                arr[x][y][z] = readValue(b)
                z++
            }
            if (needInit) {
                while (x < l0) { while (y < l1) { while (z < l2) { arr[x][y][z] = 0; z++ }; z = 0; y++ }; y = 0; x++ }
            }
            if (ori != null) for (i in 0 until l0) for (j in 0 until l1) for (k in 0 until l2) ori[i][j][k] = arr[i][j][k]
        }

        override fun readStrArray(refArray: Array<String?>?, needInit: Boolean) {
            var ori: Array<String?>? = null
            var x = 0
            val saveLength0 = r.readInt32()
            var arr = refArray ?: arrayOfNulls(saveLength0)
            var length0 = arr.size
            if (length0 < saveLength0) {
                ori = arr
                arr = arrayOfNulls(maxOf(length0, saveLength0))
                length0 = minOf(length0, saveLength0)
            }
            while (true) {
                val b = r.readByte()
                if (b == Ebdb.EoD) break
                if (b == Ebdb.Zero) {
                    val cnt = mReadInt().toInt()
                    if (needInit) for (i in 0 until cnt) arr[x + i] = null
                    x += cnt; continue
                }
                if (b == Ebdb.String) arr[x] = readString() else throw FileEE("バイナリデータの異常")
                x++
            }
            if (needInit) while (x < length0) { arr[x] = null; x++ }
            if (ori != null) for (i in 0 until length0) ori[i] = arr[i]
        }

        override fun readStrArray2D(refArray: Array<Array<String?>>?, needInit: Boolean) {
            var ori: Array<Array<String?>>? = null
            var x = 0; var y = 0
            val s0 = r.readInt32(); val s1 = r.readInt32()
            var arr = refArray ?: Array(s0) { arrayOfNulls<String>(s1) }
            var l0 = arr.size
            var l1 = if (l0 > 0) arr[0].size else 0
            if (l0 < s0 || l1 < s1) {
                ori = arr
                val m1 = maxOf(l1, s1)
                arr = Array(maxOf(l0, s0)) { arrayOfNulls<String>(m1) }
                l0 = minOf(l0, s0); l1 = minOf(l1, s1)
            }
            while (true) {
                val b = r.readByte()
                if (b == Ebdb.EoD) break
                if (b == Ebdb.ZeroA1) {
                    val cnt = mReadInt().toInt()
                    if (needInit) for (i in 0 until cnt) for (yy in 0 until l1) arr[x + i][yy] = null
                    x += cnt; y = 0; continue
                }
                if (b == Ebdb.EoA1) {
                    if (needInit) while (y < l1) { arr[x][y] = null; y++ }
                    x++; y = 0; continue
                }
                if (b == Ebdb.Zero) {
                    val cnt = mReadInt().toInt()
                    if (needInit) for (i in 0 until cnt) arr[x][y + i] = null
                    y += cnt; continue
                }
                if (b == Ebdb.String) arr[x][y] = readString() else throw FileEE("バイナリデータの異常")
                y++
            }
            if (needInit) { while (x < l0) { while (y < l1) { arr[x][y] = null; y++ }; y = 0; x++ } }
            if (ori != null) for (i in 0 until l0) for (j in 0 until l1) ori[i][j] = arr[i][j]
        }

        override fun readStrArray3D(refArray: Array<Array<Array<String?>>>?, needInit: Boolean) {
            var ori: Array<Array<Array<String?>>>? = null
            var x = 0; var y = 0; var z = 0
            val s0 = r.readInt32(); val s1 = r.readInt32(); val s2 = r.readInt32()
            var arr = refArray ?: Array(s0) { Array(s1) { arrayOfNulls<String>(s2) } }
            var l0 = arr.size
            var l1 = if (l0 > 0) arr[0].size else 0
            var l2 = if (l1 > 0) arr[0][0].size else 0
            if (l0 < s0 || l1 < s1 || l2 < s2) {
                ori = arr
                val m1 = maxOf(l1, s1); val m2 = maxOf(l2, s2)
                arr = Array(maxOf(l0, s0)) { Array(m1) { arrayOfNulls<String>(m2) } }
                l0 = minOf(l0, s0); l1 = minOf(l1, s1); l2 = minOf(l2, s2)
            }
            while (true) {
                val b = r.readByte()
                if (b == Ebdb.EoD) break
                if (b == Ebdb.ZeroA2) {
                    val cnt = mReadInt().toInt()
                    if (needInit) for (i in 0 until cnt) for (yy in 0 until l1) for (zz in 0 until l2) arr[x + i][yy][zz] = null
                    x += cnt; y = 0; z = 0; continue
                }
                if (b == Ebdb.EoA2) {
                    if (needInit) { while (y < l1) { while (z < l2) { arr[x][y][z] = null; z++ }; z = 0; y++ } }
                    x++; y = 0; z = 0; continue
                }
                if (b == Ebdb.ZeroA1) {
                    val cnt = mReadInt().toInt()
                    if (needInit) for (i in 0 until cnt) for (zz in 0 until l2) arr[x][y + i][zz] = null
                    y += cnt; z = 0; continue
                }
                if (b == Ebdb.EoA1) {
                    if (needInit) while (z < l2) { arr[x][y][z] = null; z++ }
                    y++; z = 0; continue
                }
                if (b == Ebdb.Zero) {
                    val cnt = mReadInt().toInt()
                    if (needInit) for (i in 0 until cnt) arr[x][y][z + i] = null
                    z += cnt; continue
                }
                if (b == Ebdb.String) arr[x][y][z] = readString() else throw FileEE("バイナリデータの異常")
                z++
            }
            if (needInit) {
                while (x < l0) { while (y < l1) { while (z < l2) { arr[x][y][z] = null; z++ }; z = 0; y++ }; y = 0; x++ }
            }
            if (ori != null) for (i in 0 until l0) for (j in 0 until l1) for (k in 0 until l2) ori[i][j][k] = arr[i][j][k]
        }
    }
}
