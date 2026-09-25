package com.eraandroid.emuera.sub

import java.io.BufferedOutputStream
import java.io.File

/** Little-endian writer compatible with .NET BinaryWriter (Encoding.Unicode). */
class LEBinaryWriter(file: File) : AutoCloseable {
    private val out = BufferedOutputStream(file.outputStream())
    fun writeByte(b: Int) = out.write(b and 0xFF)
    fun writeInt16(v: Int) { writeByte(v); writeByte(v shr 8) }
    fun writeInt32(v: Int) { for (i in 0 until 4) writeByte(v shr (8 * i)) }
    fun writeInt64(v: Long) { for (i in 0 until 8) writeByte((v shr (8 * i)).toInt()) }
    fun write7BitEncodedInt(value: Int) {
        var v = value.toLong() and 0xFFFFFFFFL
        while (v >= 0x80) { writeByte((v or 0x80).toInt()); v = v shr 7 }
        writeByte(v.toInt())
    }
    fun writeString(s: String) {
        val bytes = s.toByteArray(Charsets.UTF_16LE)
        write7BitEncodedInt(bytes.size)
        out.write(bytes)
    }
    override fun close() = out.close()
}

/** 1808追加 新しいデータ保存形式 WriteHeader -> WriteFileType -> ... -> WriteEFO */
class EraBinaryDataWriter(file: File) : AutoCloseable {
    private var writer: LEBinaryWriter? = LEBinaryWriter(file)
    private val w get() = writer!!

    fun writeHeader() {
        w.writeInt64(EraBDConst.Header)
        w.writeInt32(EraBDConst.Version1808)
        w.writeInt32(EraBDConst.DataCount)
    }

    fun writeFileType(type: EraSaveFileType) = w.writeByte(type.code)
    fun writeInt64(v: Long) = w.writeInt64(v)
    fun writeString(s: String) = w.writeString(s)
    fun writeSeparator() = w.writeByte(EraSaveDataType.Separator.code)
    fun writeEOC() = w.writeByte(EraSaveDataType.EOC.code)
    fun writeEOF() = w.writeByte(EraSaveDataType.EOF.code)

    @Suppress("UNCHECKED_CAST")
    fun writeWithKey(key: String, v: Any?) {
        when (v) {
            is Long -> { w.writeByte(EraSaveDataType.Int.code); w.writeString(key); mWriteInt(v) }
            is LongArray -> { w.writeByte(EraSaveDataType.IntArray.code); w.writeString(key); writeData1(v) }
            is String -> { w.writeByte(EraSaveDataType.Str.code); w.writeString(key); w.writeString(v) }
            is Array<*> -> {
                when {
                    v.isArrayOf<LongArray>() -> { w.writeByte(EraSaveDataType.IntArray2D.code); w.writeString(key); writeData2(v as Array<LongArray>) }
                    v.isArrayOf<Array<LongArray>>() -> { w.writeByte(EraSaveDataType.IntArray3D.code); w.writeString(key); writeData3(v as Array<Array<LongArray>>) }
                    v.isArrayOf<Array<Array<String?>>>() -> { w.writeByte(EraSaveDataType.StrArray3D.code); w.writeString(key); writeStr3(v as Array<Array<Array<String?>>>) }
                    v.isArrayOf<Array<String?>>() -> { w.writeByte(EraSaveDataType.StrArray2D.code); w.writeString(key); writeStr2(v as Array<Array<String?>>) }
                    else -> { w.writeByte(EraSaveDataType.StrArray.code); w.writeString(key); writeStr1(v as Array<String?>) }
                }
            }
        }
    }

    private fun mWriteInt(v: Long) {
        when {
            v in 0..Ebdb.Byte.toLong() -> w.writeByte(v.toInt())
            v >= Short.MIN_VALUE && v <= Short.MAX_VALUE -> { w.writeByte(Ebdb.Int16); w.writeInt16(v.toInt()) }
            v >= Int.MIN_VALUE && v <= Int.MAX_VALUE -> { w.writeByte(Ebdb.Int32); w.writeInt32(v.toInt()) }
            else -> { w.writeByte(Ebdb.Int64); w.writeInt64(v) }
        }
    }

    private fun writeData1(array: LongArray) {
        w.writeInt32(array.size)
        var countZero = 0
        for (x in array.indices) {
            if (array[x] == 0L) countZero++
            else {
                if (countZero > 0) { w.writeByte(Ebdb.Zero); mWriteInt(countZero.toLong()); countZero = 0 }
                mWriteInt(array[x])
            }
        }
        w.writeByte(Ebdb.EoD)
    }

    private fun writeData2(array: Array<LongArray>) {
        var countZero = 0
        var countAllZero = 0
        val l0 = array.size
        val l1 = if (l0 > 0) array[0].size else 0
        w.writeInt32(l0); w.writeInt32(l1)
        for (x in 0 until l0) {
            for (y in 0 until l1) {
                if (array[x][y] == 0L) countZero++
                else {
                    if (countAllZero > 0) { w.writeByte(Ebdb.ZeroA1); mWriteInt(countAllZero.toLong()); countAllZero = 0 }
                    if (countZero > 0) { w.writeByte(Ebdb.Zero); mWriteInt(countZero.toLong()); countZero = 0 }
                    mWriteInt(array[x][y])
                }
            }
            if (countZero == l1) countAllZero++ else w.writeByte(Ebdb.EoA1)
            countZero = 0
        }
        w.writeByte(Ebdb.EoD)
    }

    private fun writeData3(array: Array<Array<LongArray>>) {
        var countZero = 0
        var countAllZero = 0
        var countAllZero2D = 0
        val l0 = array.size
        val l1 = if (l0 > 0) array[0].size else 0
        val l2 = if (l1 > 0) array[0][0].size else 0
        w.writeInt32(l0); w.writeInt32(l1); w.writeInt32(l2)
        for (x in 0 until l0) {
            for (y in 0 until l1) {
                for (z in 0 until l2) {
                    if (array[x][y][z] == 0L) countZero++
                    else {
                        if (countAllZero2D > 0) { w.writeByte(Ebdb.ZeroA2); mWriteInt(countAllZero2D.toLong()); countAllZero2D = 0 }
                        if (countAllZero > 0) { w.writeByte(Ebdb.ZeroA1); mWriteInt(countAllZero.toLong()); countAllZero = 0 }
                        if (countZero > 0) { w.writeByte(Ebdb.Zero); mWriteInt(countZero.toLong()); countZero = 0 }
                        mWriteInt(array[x][y][z])
                    }
                }
                if (countZero == l2) countAllZero++ else w.writeByte(Ebdb.EoA1)
                countZero = 0
            }
            if (countAllZero == l1) countAllZero2D++ else w.writeByte(Ebdb.EoA2)
            countAllZero = 0
        }
        w.writeByte(Ebdb.EoD)
    }

    private fun writeStr1(array: Array<String?>) {
        var countZero = 0
        w.writeInt32(array.size)
        for (x in array.indices) {
            val s = array[x]
            if (s.isNullOrEmpty()) countZero++
            else {
                if (countZero > 0) { w.writeByte(Ebdb.Zero); mWriteInt(countZero.toLong()); countZero = 0 }
                w.writeByte(Ebdb.String); w.writeString(s)
            }
        }
        w.writeByte(Ebdb.EoD)
    }

    private fun writeStr2(array: Array<Array<String?>>) {
        var countZero = 0
        var countAllZero = 0
        val l0 = array.size
        val l1 = if (l0 > 0) array[0].size else 0
        w.writeInt32(l0); w.writeInt32(l1)
        for (x in 0 until l0) {
            for (y in 0 until l1) {
                val s = array[x][y]
                if (s.isNullOrEmpty()) countZero++
                else {
                    if (countAllZero > 0) { w.writeByte(Ebdb.ZeroA1); mWriteInt(countAllZero.toLong()); countAllZero = 0 }
                    if (countZero > 0) { w.writeByte(Ebdb.Zero); mWriteInt(countZero.toLong()); countZero = 0 }
                    w.writeByte(Ebdb.String); w.writeString(s)
                }
            }
            if (countZero == l1) countAllZero++ else w.writeByte(Ebdb.EoA1)
            countZero = 0
        }
        w.writeByte(Ebdb.EoD)
    }

    private fun writeStr3(array: Array<Array<Array<String?>>>) {
        var countZero = 0
        var countAllZero = 0
        var countAllZero2D = 0
        val l0 = array.size
        val l1 = if (l0 > 0) array[0].size else 0
        val l2 = if (l1 > 0) array[0][0].size else 0
        w.writeInt32(l0); w.writeInt32(l1); w.writeInt32(l2)
        for (x in 0 until l0) {
            for (y in 0 until l1) {
                for (z in 0 until l2) {
                    val s = array[x][y][z]
                    if (s.isNullOrEmpty()) countZero++
                    else {
                        if (countAllZero2D > 0) { w.writeByte(Ebdb.ZeroA2); mWriteInt(countAllZero2D.toLong()); countAllZero2D = 0 }
                        if (countAllZero > 0) { w.writeByte(Ebdb.ZeroA1); mWriteInt(countAllZero.toLong()); countAllZero = 0 }
                        if (countZero > 0) { w.writeByte(Ebdb.Zero); mWriteInt(countZero.toLong()); countZero = 0 }
                        w.writeByte(Ebdb.String); w.writeString(s)
                    }
                }
                if (countZero == l2) countAllZero++ else w.writeByte(Ebdb.EoA1)
                countZero = 0
            }
            if (countAllZero == l1) countAllZero2D++ else w.writeByte(Ebdb.EoA2)
            countAllZero = 0
        }
        w.writeByte(Ebdb.EoD)
    }

    override fun close() {
        try { writer?.close() } catch (_: Exception) {}
        writer = null
    }
}
