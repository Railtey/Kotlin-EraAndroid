package com.eraandroid.emuera.gamedata.expression

private const val UNARY = 0x10000
private const val BINARY = 0x20000
private const val TERNARY = 0x40000
private const val UNARY_AFTER = 0x80000

enum class OperatorCode(val value: Int) {
    NULL(0),
    Plus((0x0100 + 0x80) or UNARY or BINARY),
    Minus((0x0200 + 0x80) or UNARY or BINARY),
    Mult((0x0300 + 0x90) or BINARY),
    Div((0x0400 + 0x90) or BINARY),
    Mod((0x0500 + 0x90) or BINARY),
    Equal((0x0600 + 0x60) or BINARY),
    Greater((0x0700 + 0x65) or BINARY),
    Less((0x0800 + 0x65) or BINARY),
    GreaterEqual((0x0900 + 0x65) or BINARY),
    LessEqual((0x0A00 + 0x65) or BINARY),
    NotEqual((0x0B00 + 0x60) or BINARY),
    Increment(0x2000 or UNARY or UNARY_AFTER),
    Decrement(0x2100 or UNARY or UNARY_AFTER),
    And((0x0C00 + 0x40) or BINARY),
    Or((0x0D00 + 0x40) or BINARY),
    Xor((0x1500 + 0x40) or BINARY),
    Nand((0x1600 + 0x40) or BINARY),
    Nor((0x1700 + 0x40) or BINARY),
    BitAnd((0x0E00 + 0x50) or BINARY),
    BitOr((0x0F00 + 0x50) or BINARY),
    BitXor((0x1000 + 0x50) or BINARY),
    Not(0x1100 or UNARY),
    BitNot(0x1200 or UNARY),
    RightShift((0x1300 + 0x70) or BINARY),
    LeftShift((0x1400 + 0x70) or BINARY),
    Ternary_a((0x1800 + 0x05) or TERNARY),
    Ternary_b((0x1900 + 0x10) or TERNARY),
    Assignment(0x0100 + 0xFE),
    AssignmentStr(0x0200 + 0xFE),
}

object OperatorManager {
    private val opDictionary: LinkedHashMap<String, OperatorCode> = linkedMapOf(
        "+" to OperatorCode.Plus, "-" to OperatorCode.Minus, "*" to OperatorCode.Mult, "/" to OperatorCode.Div,
        "%" to OperatorCode.Mod, "==" to OperatorCode.Equal, ">" to OperatorCode.Greater, "<" to OperatorCode.Less,
        ">=" to OperatorCode.GreaterEqual, "<=" to OperatorCode.LessEqual, "!=" to OperatorCode.NotEqual,
        "&&" to OperatorCode.And, "||" to OperatorCode.Or, "^^" to OperatorCode.Xor, "!&" to OperatorCode.Nand,
        "!|" to OperatorCode.Nor, "&" to OperatorCode.BitAnd, "|" to OperatorCode.BitOr, "!" to OperatorCode.Not,
        "^" to OperatorCode.BitXor, "~" to OperatorCode.BitNot, "?" to OperatorCode.Ternary_a, "#" to OperatorCode.Ternary_b,
        ">>" to OperatorCode.RightShift, "<<" to OperatorCode.LeftShift, "++" to OperatorCode.Increment,
        "--" to OperatorCode.Decrement, "=" to OperatorCode.Assignment, "'=" to OperatorCode.AssignmentStr,
    )

    fun toOperatorString(op: OperatorCode): String {
        if (op == OperatorCode.NULL) return ""
        for ((k, v) in opDictionary) if (v == op) return k
        return ""
    }

    fun isUnary(type: OperatorCode) = (type.value and UNARY) == UNARY
    fun isUnaryAfter(type: OperatorCode) = (type.value and UNARY_AFTER) == UNARY_AFTER
    fun isBinary(type: OperatorCode) = (type.value and BINARY) == BINARY
    fun isTernary(type: OperatorCode) = (type.value and TERNARY) == TERNARY

    /** 大きい方が優先度が高い。 */
    fun getPriority(type: OperatorCode) = type.value and 0xFF
}
