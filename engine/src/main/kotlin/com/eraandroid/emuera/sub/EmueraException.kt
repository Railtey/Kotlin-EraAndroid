package com.eraandroid.emuera.sub

abstract class EmueraException(message: String, var position: ScriptPosition? = null) : RuntimeException(message)

/** emuera本体に起因すると思われるエラー */
class ExeEE(message: String, position: ScriptPosition? = null) : EmueraException(message, position)

/** スクリプト側に起因すると思われるエラー */
open class CodeEE(message: String, position: ScriptPosition? = null) : EmueraException(message, position)

/** 未定義の識別子に関連するもの */
open class IdentifierNotFoundCodeEE(message: String, position: ScriptPosition? = null) : CodeEE(message, position)

/** 未実装エラー */
class NotImplCodeEE(position: ScriptPosition? = null) : CodeEE("この機能は現バージョンでは使えません", position)

/** Save, Load中のエラー */
class FileEE(message: String) : EmueraException(message)

/** エラー箇所を表示するための位置データ */
class ScriptPosition(srcFile: String? = null, srcLineNo: Int = -1) {
    val lineNo: Int = srcLineNo
    val filename: String = srcFile ?: ""

    override fun toString(): String = if (lineNo == -1) "ScriptPosition" else "$filename:$lineNo"

    override fun equals(other: Any?): Boolean =
        other is ScriptPosition && other.filename == filename && other.lineNo == lineNo

    override fun hashCode(): Int = filename.hashCode() xor lineNo.hashCode()
}
