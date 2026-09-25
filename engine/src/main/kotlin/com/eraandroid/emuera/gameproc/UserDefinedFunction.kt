package com.eraandroid.emuera.gameproc

import com.eraandroid.emuera.GlobalStatic
import com.eraandroid.emuera.config.Config
import com.eraandroid.emuera.gamedata.ParserMediator
import com.eraandroid.emuera.sub.*

/** UserDifinedFunctionDataArgType (flags) */
object UserDifinedFunctionDataArgType {
    const val Null = 0
    const val Int = 0x10
    const val Str = 0x20
    const val RefInt1 = 0x51
    const val RefInt2 = 0x52
    const val RefInt3 = 0x53
    const val RefStr1 = 0x61
    const val RefStr2 = 0x62
    const val RefStr3 = 0x63
    const val __Ref = 0x40
    const val __Dimention = 0x0F
}

class UserDefinedFunctionData private constructor() {
    var name: String? = null
    var typeIsStr = false
    lateinit var argList: IntArray

    companion object {
        fun create(wc: WordCollection, dims: Boolean, sc: ScriptPosition?): UserDefinedFunctionData {
            val dimtype = if (dims) "#FUNCTION" else "#FUNCTIONS"
            val ret = UserDefinedFunctionData()
            ret.typeIsStr = dims
            var keyword = dimtype
            while (!wc.eol) {
                val idw = wc.current as? IdentifierWord ?: break
                wc.shiftNext()
                keyword = idw.code
                if (Config.ICVariable) keyword = keyword.uppercase()
                when (keyword) {
                    "CONST", "REF", "DYNAMIC", "STATIC", "GLOBAL", "SAVEDATA", "CHARADATA" ->
                        throw CodeEE("${dims}中では${keyword}キーワードは指定できません", sc)
                    else -> { ret.name = keyword; break }
                }
            }
            val name = ret.name ?: throw CodeEE("${keyword}の後に有効な識別子が指定されていません", sc)
            if (wc.eol || wc.current.type != '(') throw CodeEE("識別子の後に引数定義がありません", sc)
            val dic = GlobalStatic.IdentifierDictionary!!
            var err = dic.checkUserLabelName(true, name)
            if (err != null && err.second == 0) dic.checkUserVarName(name)?.let { err = it }
            err?.let {
                if (it.second >= 2) throw CodeEE(it.first, sc)
                ParserMediator.warn(it.first, sc, it.second)
            }
            val argList = ArrayList<Int>()
            var argType = UserDifinedFunctionDataArgType.Null
            var state = 0
            fun argerr(): Nothing {
                if (!wc.eol) throw CodeEE("引数の解析中に予期しないトークン${wc.current}を発見しました", sc)
                throw CodeEE("引数の解析中にエラーが発生しました", sc)
            }
            loop@ while (true) {
                wc.shiftNext()
                when (wc.current.type) {
                    '\u0000' -> throw CodeEE("括弧が閉じられていません", sc)
                    ')' -> {
                        if (state == 0 || state == 1) break@loop
                        if (state == 4 || state == 5) {
                            if ((argType and UserDifinedFunctionDataArgType.__Dimention) == 0) throw CodeEE("REF引数は配列変数でなければなりません", sc)
                            argList.add(argType)
                            break@loop
                        }
                        throw CodeEE("予期しない括弧です", sc)
                    }
                    '0' -> {
                        if ((wc.current as LiteralIntegerWord).int != 0L) argerr()
                        if (state == 5) { state = 4; continue@loop }
                        argerr()
                    }
                    ':' -> {
                        if (state == 4 || state == 5) {
                            state = 5
                            argType++
                            if ((argType and UserDifinedFunctionDataArgType.__Dimention) > 3) throw CodeEE("REF引数は4次元以上の配列にできません", sc)
                            continue@loop
                        }
                        argerr()
                    }
                    ',' -> {
                        if (state == 1) { state = 2; continue@loop }
                        if (state == 4 || state == 5) {
                            if ((argType and UserDifinedFunctionDataArgType.__Dimention) == 0) throw CodeEE("REF引数は配列変数でなければなりません", sc)
                            state = 2
                            argList.add(argType)
                            continue@loop
                        }
                        argerr()
                    }
                    'A' -> {
                        var str = (wc.current as IdentifierWord).code
                        if (Config.ICVariable) str = str.uppercase()
                        if (str == "REF") {
                            if (state == 0 || state == 2) { state = 3; continue@loop }
                            argerr()
                        } else if (str == "INT" || str == "STR") {
                            argType = if (str == "INT") UserDifinedFunctionDataArgType.Int else UserDifinedFunctionDataArgType.Str
                            if (state == 0 || state == 2) { state = 1; argList.add(argType); continue@loop }
                            if (state == 3) { argType = argType or UserDifinedFunctionDataArgType.__Ref; state = 4; continue@loop }
                            argerr()
                        } else argerr()
                    }
                    else -> argerr()
                }
            }
            wc.shiftNext()
            if (!wc.eol) throw CodeEE("宣言の後に余分な文字があります", sc)
            ret.argList = argList.toIntArray()
            return ret
        }
    }
}
