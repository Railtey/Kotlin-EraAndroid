package com.eraandroid.emuera.sub

/** 文字列を1文字ずつ評価するためのクラス */
class StringStream(s: String?) {
    private var source: String = s ?: ""
    private var pointer = 0

    val rowString: String get() = source

    var currentPosition: Int
        get() = pointer
        set(value) { pointer = value }

    val current: Char
        get() = if (pointer >= source.length) EndOfString else source[pointer]

    fun appendString(str: String) {
        if (pointer > source.length) pointer = source.length
        source += " $str"
    }

    /** 文字列終端に達した */
    val eos: Boolean get() = pointer >= source.length

    val next: Char
        get() = if (pointer + 1 >= source.length) EndOfString else source[pointer + 1]

    fun substring(): String {
        if (pointer >= source.length) return ""
        if (pointer == 0) return source
        return source.substring(pointer)
    }

    fun substring(start: Int, length: Int): String {
        var len = length
        if (start >= source.length || len == 0) return ""
        if (start + len > source.length) len = source.length - start
        return source.substring(start, start + len)
    }

    fun replace(start: Int, count: Int, src: String) {
        source = source.substring(0, start) + src + source.substring(start + count)
        pointer = start
    }

    fun shiftNext() { pointer++ }

    fun jump(skip: Int) { pointer += skip }

    /** 検索文字列の相対位置を返す。見つからない場合、負の値。 */
    fun find(str: String): Int = source.indexOf(str, pointer) - pointer

    fun find(c: Char): Int = source.indexOf(c, pointer) - pointer

    override fun toString(): String = source

    fun currentEqualTo(rother: String): Boolean {
        if (pointer + rother.length > source.length) return false
        for (i in rother.indices) if (source[pointer + i] != rother[i]) return false
        return true
    }

    fun tripleSymbol(): Boolean {
        if (pointer + 3 > source.length) return false
        return source[pointer] == source[pointer + 1] && source[pointer] == source[pointer + 2]
    }

    fun currentEqualTo(rother: String, ignoreCase: Boolean): Boolean {
        if (pointer + rother.length > source.length) return false
        return source.regionMatches(pointer, rother, 0, rother.length, ignoreCase)
    }

    fun seekBegin(offset: Int) { pointer = offset; if (pointer < 0) pointer = 0 }
    fun seekCurrent(offset: Int) { pointer += offset; if (pointer < 0) pointer = 0 }
    fun seekEnd(offset: Int) { pointer = source.length + offset; if (pointer < 0) pointer = 0 }

    companion object {
        const val EndOfString = '\u0000'
    }
}
