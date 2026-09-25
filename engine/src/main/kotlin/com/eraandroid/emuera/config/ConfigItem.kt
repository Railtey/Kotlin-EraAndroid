package com.eraandroid.emuera.config

import com.eraandroid.emuera.sub.CodeEE

/** Color stored as 0xRRGGBB (no alpha). */
@JvmInline
value class EraColor(val rgb: Int) {
    val r: Int get() = (rgb shr 16) and 0xFF
    val g: Int get() = (rgb shr 8) and 0xFF
    val b: Int get() = rgb and 0xFF
    val argb: Int get() = rgb or (0xFF shl 24)
    companion object {
        fun fromArgb(r: Int, g: Int, b: Int) = EraColor(((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF))
    }
}

abstract class AConfigItem(val code: ConfigCode, val text: String) {
    val name: String = code.name
    var fixed = false
    abstract fun tryParse(param: String?): Boolean
    abstract fun valueToString(): String
    abstract val anyValue: Any?
    abstract fun setAnyValue(v: Any?)
    override fun toString(): String = "$text:${valueToString()}"
}

class ConfigItem<T : Any>(code: ConfigCode, text: String, initial: T, private val kind: Kind) : AConfigItem(code, text) {
    enum class Kind { BOOL, COLOR, CHAR, INT, LONG, LONG_LIST, STRING, ENUM }

    private var v: T = initial
    var value: T
        get() = v
        set(value) { if (!fixed) v = value }

    override val anyValue: Any get() = v

    @Suppress("UNCHECKED_CAST")
    override fun setAnyValue(v: Any?) { if (v != null) value = v as T }

    override fun valueToString(): String = when (kind) {
        Kind.BOOL -> if (v as Boolean) "YES" else "NO"
        Kind.COLOR -> (v as EraColor).let { "${it.r},${it.g},${it.b}" }
        Kind.LONG_LIST -> (v as List<*>).joinToString("/")
        else -> v.toString()
    }

    @Suppress("UNCHECKED_CAST")
    override fun tryParse(param: String?): Boolean {
        if (param.isNullOrEmpty()) return false
        if (fixed) return false
        val str = param.trim()
        when (kind) {
            Kind.BOOL -> {
                val i = str.toIntOrNull()
                val b = when {
                    i != null -> i != 0
                    str.equals("NO", true) || str.equals("FALSE", true) || str == "後" -> false
                    str.equals("YES", true) || str.equals("TRUE", true) || str == "前" -> true
                    else -> throw CodeEE("不正な指定です")
                }
                value = b as T
                return true
            }
            Kind.COLOR -> {
                val tokens = str.split(',')
                if (tokens.size < 3) throw CodeEE("値をColor指定子として認識できません")
                val r = tokens[0].trim().toIntOrNull()
                val g = tokens[1].trim().toIntOrNull()
                val b = tokens[2].trim().toIntOrNull()
                if (r == null || g == null || b == null || r !in 0..255 || g !in 0..255 || b !in 0..255)
                    throw CodeEE("値をColor指定子として認識できません")
                value = EraColor.fromArgb(r, g, b) as T
                return true
            }
            Kind.CHAR -> {
                if (str.length != 1) return false
                value = str[0] as T
                return true
            }
            Kind.INT -> {
                val i = str.toIntOrNull() ?: throw CodeEE("数字でない文字が含まれています")
                value = i as T
                return true
            }
            Kind.LONG -> {
                val i = str.toLongOrNull() ?: throw CodeEE("数字でない文字が含まれています")
                value = i as T
                return true
            }
            Kind.LONG_LIST -> {
                val list = ArrayList<Long>()
                for (s in str.split('/')) list.add(s.trim().toLongOrNull() ?: throw CodeEE("数字でない文字が含まれています"))
                value = list as T
                return true
            }
            Kind.STRING -> { value = str as T; return true }
            Kind.ENUM -> {
                val up = str.uppercase()
                val cls = (v as Enum<*>).declaringJavaClass
                val found = cls.enumConstants.firstOrNull { it.name == up } ?: throw CodeEE("不正な指定です")
                value = found as T
                return true
            }
        }
    }
}
