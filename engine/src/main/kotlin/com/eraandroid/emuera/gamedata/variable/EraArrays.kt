package com.eraandroid.emuera.gamedata.variable

/**
 * Helpers emulating C# multi-dimensional arrays with jagged Kotlin arrays.
 * Int64[] -> LongArray, Int64[,] -> Array<LongArray>, Int64[,,] -> Array<Array<LongArray>>
 * string[] -> Array<String?>, string[,] -> Array<Array<String?>>, string[,,] -> Array<Array<Array<String?>>>
 */
typealias IntArr2 = Array<LongArray>
typealias IntArr3 = Array<Array<LongArray>>
typealias StrArr1 = Array<String?>
typealias StrArr2 = Array<Array<String?>>
typealias StrArr3 = Array<Array<Array<String?>>>

/** Long index -> Int index; out-of-Int-range values become -1 so that array access throws. */
@Suppress("NOTHING_TO_INLINE")
inline fun Long.ix(): Int = if (this >= 0 && this <= Int.MAX_VALUE) this.toInt() else -1

object EraArrays {
    fun newInt2(a: Int, b: Int): IntArr2 = Array(a) { LongArray(b) }
    fun newInt3(a: Int, b: Int, c: Int): IntArr3 = Array(a) { Array(b) { LongArray(c) } }
    fun newStr1(a: Int): StrArr1 = arrayOfNulls(a)
    fun newStr2(a: Int, b: Int): StrArr2 = Array(a) { arrayOfNulls<String>(b) }
    fun newStr3(a: Int, b: Int, c: Int): StrArr3 = Array(a) { Array(b) { arrayOfNulls<String>(c) } }

    /** array.GetLength(dimension) */
    @Suppress("UNCHECKED_CAST")
    fun length(array: Any, dimension: Int): Int {
        return when (array) {
            is LongArray -> if (dimension == 0) array.size else throw IndexOutOfBoundsException()
            is Array<*> -> {
                when (dimension) {
                    0 -> array.size
                    1 -> if (array.isEmpty()) 0 else length(array[0]!!, 0)
                    2 -> if (array.isEmpty()) 0 else length(array[0]!!, 1)
                    else -> throw IndexOutOfBoundsException()
                }
            }
            else -> throw IllegalArgumentException()
        }
    }

    /** Array.Length (total element count) */
    fun totalLength(array: Any): Int = when (array) {
        is LongArray -> array.size
        is Array<*> -> {
            if (array.isEmpty()) 0
            else {
                val first = array[0]
                if (first is LongArray || first is Array<*>) array.size * totalLength(first) else array.size
            }
        }
        else -> 0
    }

    fun dims(array: Any): Int = when (array) {
        is LongArray -> 1
        is Array<*> -> {
            val f = if (array.isEmpty()) null else array[0]
            when (f) {
                is LongArray -> 2
                is Array<*> -> {
                    val g = if (f.isEmpty()) null else f[0]
                    if (g is LongArray || g is Array<*>) 3 else 2
                }
                else -> if (array.isArrayOf<LongArray>()) 2 else if (array.isArrayOf<Array<*>>()) 2 else 1
            }
        }
        else -> 0
    }

    @Suppress("UNCHECKED_CAST")
    fun getInt(array: Any, dim: Int, a: LongArray, off: Int = 0): Long = when (dim) {
        1 -> (array as LongArray)[a[off].ix()]
        2 -> (array as IntArr2)[a[off].ix()][a[off + 1].ix()]
        3 -> (array as IntArr3)[a[off].ix()][a[off + 1].ix()][a[off + 2].ix()]
        else -> throw IllegalStateException()
    }

    @Suppress("UNCHECKED_CAST")
    fun setInt(array: Any, dim: Int, a: LongArray, value: Long, off: Int = 0) {
        when (dim) {
            1 -> (array as LongArray)[a[off].ix()] = value
            2 -> (array as IntArr2)[a[off].ix()][a[off + 1].ix()] = value
            3 -> (array as IntArr3)[a[off].ix()][a[off + 1].ix()][a[off + 2].ix()] = value
        }
    }

    fun plusInt(array: Any, dim: Int, a: LongArray, value: Long, off: Int = 0): Long {
        val v = getInt(array, dim, a, off) + value
        setInt(array, dim, a, v, off)
        return v
    }

    @Suppress("UNCHECKED_CAST")
    fun getStr(array: Any, dim: Int, a: LongArray, off: Int = 0): String? = when (dim) {
        1 -> (array as StrArr1)[a[off].ix()]
        2 -> (array as StrArr2)[a[off].ix()][a[off + 1].ix()]
        3 -> (array as StrArr3)[a[off].ix()][a[off + 1].ix()][a[off + 2].ix()]
        else -> throw IllegalStateException()
    }

    @Suppress("UNCHECKED_CAST")
    fun setStr(array: Any, dim: Int, a: LongArray, value: String?, off: Int = 0) {
        when (dim) {
            1 -> (array as StrArr1)[a[off].ix()] = value
            2 -> (array as StrArr2)[a[off].ix()][a[off + 1].ix()] = value
            3 -> (array as StrArr3)[a[off].ix()][a[off + 1].ix()][a[off + 2].ix()] = value
        }
    }

    /** SetValue(Int64[] values, args): writes along the last dimension starting at the last index. */
    @Suppress("UNCHECKED_CAST")
    fun setInts(array: Any, dim: Int, a: LongArray, values: LongArray, off: Int = 0) {
        val row: LongArray = when (dim) {
            1 -> array as LongArray
            2 -> (array as IntArr2)[a[off].ix()]
            3 -> (array as IntArr3)[a[off].ix()][a[off + 1].ix()]
            else -> throw IllegalStateException()
        }
        val start = a[off + dim - 1].toInt()
        for (i in values.indices) row[start + i] = values[i]
    }

    @Suppress("UNCHECKED_CAST")
    fun setStrs(array: Any, dim: Int, a: LongArray, values: Array<String?>, off: Int = 0) {
        val row: StrArr1 = when (dim) {
            1 -> array as StrArr1
            2 -> (array as StrArr2)[a[off].ix()]
            3 -> (array as StrArr3)[a[off].ix()][a[off + 1].ix()]
            else -> throw IllegalStateException()
        }
        val start = a[off + dim - 1].toInt()
        for (i in values.indices) row[start + i] = values[i]
    }

    /** SetValueAll: 1D uses [start,end), multi-dim fills all */
    @Suppress("UNCHECKED_CAST")
    fun fillInt(array: Any, dim: Int, value: Long, start: Int, end: Int) {
        when (dim) {
            1 -> { val arr = array as LongArray; for (i in start until end) arr[i] = value }
            2 -> for (row in array as IntArr2) row.fill(value)
            3 -> for (m in array as IntArr3) for (row in m) row.fill(value)
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun fillStr(array: Any, dim: Int, value: String?, start: Int, end: Int) {
        when (dim) {
            1 -> { val arr = array as StrArr1; for (i in start until end) arr[i] = value }
            2 -> for (row in array as StrArr2) row.fill(value)
            3 -> for (m in array as StrArr3) for (row in m) row.fill(value)
        }
    }

    /** Array.Clear(whole array) */
    @Suppress("UNCHECKED_CAST")
    fun clear(array: Any) {
        when (array) {
            is LongArray -> array.fill(0)
            is Array<*> -> {
                if (array.isEmpty()) return
                val f = array[0]
                if (f is LongArray || f is Array<*>) for (x in array) clear(x!!)
                else (array as Array<Any?>).fill(null)
            }
        }
    }

    /** Deep copy of an array value (used for COPYCHARA etc.) */
    @Suppress("UNCHECKED_CAST")
    fun copyInto(src: Any, dst: Any) {
        when (src) {
            is LongArray -> src.copyInto(dst as LongArray, 0, 0, minOf(src.size, dst.size))
            is Array<*> -> {
                val d = dst as Array<Any?>
                val n = minOf(src.size, d.size)
                for (i in 0 until n) {
                    val s = src[i]
                    if (s is LongArray || s is Array<*>) copyInto(s, d[i]!!) else d[i] = s
                }
            }
        }
    }
}
