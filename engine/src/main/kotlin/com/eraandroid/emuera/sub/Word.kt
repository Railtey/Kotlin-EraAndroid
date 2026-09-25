package com.eraandroid.emuera.sub

import com.eraandroid.emuera.gamedata.expression.IOperandTerm
import com.eraandroid.emuera.gamedata.expression.OperatorCode

abstract class Word {
    abstract val type: Char
    var isMacro = false
    open fun setIsMacro() { isMacro = true }
}

class NullWord : Word() {
    override val type: Char get() = '\u0000'
    override fun toString() = "/null/"
}

class IdentifierWord(val code: String) : Word() {
    override val type: Char get() = 'A'
    override fun toString() = code
}

class LiteralIntegerWord(val int: Long) : Word() {
    override val type: Char get() = '0'
    override fun toString() = int.toString()
}

class LiteralStringWord(val str: String) : Word() {
    override val type: Char get() = '"'
    override fun toString() = "\"$str\""
}

class OperatorWord(val code: OperatorCode) : Word() {
    override val type: Char get() = '='
    override fun toString() = code.toString()
}

class SymbolWord(private val code: Char) : Word() {
    override val type: Char get() = code
    override fun toString() = code.toString()
}

class StrFormWord(val strs: Array<String>, val subWords: Array<SubWord>) : Word() {
    override val type: Char get() = 'F'
    override fun setIsMacro() {
        isMacro = true
        for (s in subWords) s.setIsMacro()
    }
}

class TermWord(val term: IOperandTerm) : Word() {
    override val type: Char get() = 'T'
}

class MacroWord(val number: Int) : Word() {
    override val type: Char get() = 'M'
    override fun toString() = "Arg$number"
}

/** FormattedStringWTの中身用のトークン */
abstract class SubWord(val words: WordCollection?) {
    var isMacro = false
    open fun setIsMacro() {
        isMacro = true
        words?.setIsMacro()
    }
}

class TripleSymbolSubWord(val code: Char) : SubWord(null)
class CurlyBraceSubWord(w: WordCollection) : SubWord(w)
class PercentSubWord(w: WordCollection) : SubWord(w)

class YenAtSubWord(w: WordCollection, val left: StrFormWord, val right: StrFormWord?) : SubWord(w) {
    override fun setIsMacro() {
        isMacro = true
        words?.setIsMacro()
        left.setIsMacro()
        right?.setIsMacro()
    }
}

/** 字句解析結果の保存場所 */
class WordCollection {
    val collection: MutableList<Word> = ArrayList()
    var pointer = 0

    fun add(token: Word) { collection.add(token) }
    fun add(wc: WordCollection) { collection.addAll(wc.collection) }
    fun clear() { collection.clear() }
    fun shiftNext() { pointer++ }

    val current: Word get() = if (pointer >= collection.size) nullToken else collection[pointer]
    val eol: Boolean get() = pointer >= collection.size

    fun insert(w: Word) { collection.add(pointer, w) }
    fun insertRange(wc: WordCollection) { collection.addAll(pointer, wc.collection) }
    fun remove() { collection.removeAt(pointer) }

    fun setIsMacro() { for (w in collection) w.setIsMacro() }

    fun clone(): WordCollection {
        val ret = WordCollection()
        ret.collection.addAll(collection)
        return ret
    }

    fun clone(start: Int, count: Int): WordCollection {
        val ret = WordCollection()
        if (start > collection.size) return ret
        var end = start + count
        if (end > collection.size) end = collection.size
        for (i in start until end) ret.collection.add(collection[i])
        return ret
    }

    companion object {
        private val nullToken: Word = NullWord()
    }
}
