package com.eraandroid.emuera

/** Properties.Resources strings. Use [fmt] for .NET style {0} placeholders. */
object Resources {
    const val RuntimeErrMesMethodCIMGCreateOutOfRange0 = "{0}関数:画像の範囲外が指定されています"
    const val RuntimeErrMesMethodColorARGB0 = "{0}関数:ColorARGB引数に不適切な値(0x{1:X8})が指定されました"
    const val RuntimeErrMesMethodDefaultArgumentOutOfRange0 = "{0}関数:第{2}引数に不適切な値({1})が指定されました"
    const val RuntimeErrMesMethodGColorMatrix0 = "{0}関数:ColorMatrixの指定された要素({1}, {2})が不適切であるか5x5に足りていません"
    const val RuntimeErrMesMethodGDIPLUSOnly = "{0}関数:描画オプションがWINAPIの時には使用できません"
    const val RuntimeErrMesMethodGHeight0 = "{0}関数:GraphicsのHeightに0以下の値({1})が指定されました"
    const val RuntimeErrMesMethodGHeight1 = "{0}関数:GraphicsのHeightに{2}以上の値({1})が指定されました"
    const val RuntimeErrMesMethodGraphicsID0 = "{0}関数:GraphicsIDに負の値({1})が指定されました"
    const val RuntimeErrMesMethodGraphicsID1 = "{0}関数:GraphicsIDの値({1})が大きすぎます"
    const val RuntimeErrMesMethodGWidth0 = "{0}関数:GraphicsのWidthに0以下の値({1})が指定されました"
    const val RuntimeErrMesMethodGWidth1 = "{0}関数:GraphicsのWidthに{2}以上の値({1})が指定されました"
    const val SyntaxErrMesMethodDefaultArgumentNotNullable0 = "{0}関数:第{1}引数は省略できません"
    const val SyntaxErrMesMethodDefaultArgumentNum0 = "{0}関数:引数の数が間違っています"
    const val SyntaxErrMesMethodDefaultArgumentNum1 = "{0}関数:少なくとも{1}個の引数が必要です"
    const val SyntaxErrMesMethodDefaultArgumentNum2 = "{0}関数:引数の数が多すぎます"
    const val SyntaxErrMesMethodDefaultArgumentType0 = "{0}関数:第{1}引数の型が間違っています"
    const val SyntaxErrMesMethodGraphicsColorMatrix0 = "{0}関数:ColorMatrixに5x5以上の二次元数値型配列変数でない引数が指定されました"

    /** Minimal .NET string.Format: supports {n} and {n:X8}. */
    fun fmt(format: String, vararg args: Any?): String {
        val sb = StringBuilder()
        var i = 0
        while (i < format.length) {
            val c = format[i]
            if (c == '{') {
                val end = format.indexOf('}', i)
                if (end > i) {
                    val inner = format.substring(i + 1, end)
                    val colon = inner.indexOf(':')
                    val idx = (if (colon >= 0) inner.substring(0, colon) else inner).toIntOrNull()
                    if (idx != null && idx < args.size) {
                        val arg = args[idx]
                        if (colon >= 0) {
                            val spec = inner.substring(colon + 1)
                            if (spec.startsWith("X") && arg is Number) {
                                val w = spec.substring(1).toIntOrNull() ?: 0
                                sb.append(String.format("%0${if (w > 0) w else 1}X", arg.toLong()))
                            } else sb.append(arg)
                        } else sb.append(arg)
                        i = end + 1
                        continue
                    }
                }
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }
}
