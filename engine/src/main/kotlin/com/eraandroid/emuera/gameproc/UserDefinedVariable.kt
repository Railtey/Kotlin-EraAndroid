package com.eraandroid.emuera.gameproc

import com.eraandroid.emuera.GlobalStatic
import com.eraandroid.emuera.config.Config
import com.eraandroid.emuera.gamedata.ParserMediator
import com.eraandroid.emuera.gamedata.expression.*
import com.eraandroid.emuera.sub.*

@Suppress("PropertyName")
class UserDefinedVariableData {
    var Name: String? = null
    var TypeIsStr = false
    var Reference = false
    var Dimension = 1
    var Lengths: IntArray? = null
    var DefaultInt: LongArray? = null
    var DefaultStr: Array<String?>? = null
    var Global = false
    var Save = false
    var Static = true
    var Private = false
    var CharaData = false
    var Const = false

    companion object {
        fun create(dimline: DimLineWC): UserDefinedVariableData = create(dimline.wc, dimline.dims, dimline.isPrivate, dimline.sc)

        fun create(wc: WordCollection, dims: Boolean, isPrivate: Boolean, sc: ScriptPosition?): UserDefinedVariableData {
            val dimtype = if (dims) "#DIM" else "#DIMS"
            val ret = UserDefinedVariableData()
            ret.TypeIsStr = dims
            var staticDefined = false
            ret.Const = false
            var keyword = dimtype
            whilebreak@ while (!wc.eol) {
                val idw = wc.current as? IdentifierWord ?: break
                wc.shiftNext()
                keyword = idw.code
                if (Config.ICVariable) keyword = keyword.uppercase()
                when (keyword) {
                    "CONST" -> {
                        if (ret.CharaData) throw CodeEE("${keyword}とCHARADATAキーワードは同時に指定できません", sc)
                        if (ret.Global) throw CodeEE("${keyword}とGLOBALキーワードは同時に指定できません", sc)
                        if (ret.Save) throw CodeEE("${keyword}とSAVEDATAキーワードは同時に指定できません", sc)
                        if (ret.Reference) throw CodeEE("${keyword}とREFキーワードは同時に指定できません", sc)
                        if (!ret.Static) throw CodeEE("${keyword}とDYNAMICキーワードは同時に指定できません", sc)
                        if (ret.Const) throw CodeEE("${keyword}キーワードが二重に指定されています", sc)
                        ret.Const = true
                    }
                    "REF" -> {
                        if (staticDefined && ret.Static) throw CodeEE("${keyword}とSTATICキーワードは同時に指定できません", sc)
                        if (ret.CharaData) throw CodeEE("${keyword}とCHARADATAキーワードは同時に指定できません", sc)
                        if (ret.Global) throw CodeEE("${keyword}とGLOBALキーワードは同時に指定できません", sc)
                        if (ret.Save) throw CodeEE("${keyword}とSAVEDATAキーワードは同時に指定できません", sc)
                        if (ret.Const) throw CodeEE("${keyword}とCONSTキーワードは同時に指定できません", sc)
                        if (ret.Reference) throw CodeEE("${keyword}キーワードが二重に指定されています", sc)
                        ret.Reference = true
                        ret.Static = false
                    }
                    "DYNAMIC" -> {
                        if (!isPrivate) throw CodeEE("広域変数の宣言に${keyword}キーワードは指定できません", sc)
                        if (ret.CharaData) throw CodeEE("${keyword}とCHARADATAキーワードは同時に指定できません", sc)
                        if (ret.Const) throw CodeEE("${keyword}とCONSTキーワードは同時に指定できません", sc)
                        if (staticDefined) {
                            if (ret.Static) throw CodeEE("STATICとDYNAMICキーワードは同時に指定できません", sc)
                            else throw CodeEE("${keyword}キーワードが二重に指定されています", sc)
                        }
                        staticDefined = true
                        ret.Static = false
                    }
                    "STATIC" -> {
                        if (!isPrivate) throw CodeEE("広域変数の宣言に${keyword}キーワードは指定できません", sc)
                        if (ret.CharaData) throw CodeEE("${keyword}とCHARADATAキーワードは同時に指定できません", sc)
                        if (staticDefined) {
                            if (!ret.Static) throw CodeEE("STATICとDYNAMICキーワードは同時に指定できません", sc)
                            else throw CodeEE("${keyword}キーワードが二重に指定されています", sc)
                        }
                        if (ret.Reference) throw CodeEE("${keyword}とREFキーワードは同時に指定できません", sc)
                        staticDefined = true
                        ret.Static = true
                    }
                    "GLOBAL" -> {
                        if (isPrivate) throw CodeEE("ローカル変数の宣言に${keyword}キーワードは指定できません", sc)
                        if (ret.CharaData) throw CodeEE("${keyword}とCHARADATAキーワードは同時に指定できません", sc)
                        if (ret.Reference) throw CodeEE("${keyword}とREFキーワードは同時に指定できません", sc)
                        if (ret.Const) throw CodeEE("${keyword}とCONSTキーワードは同時に指定できません", sc)
                        if (staticDefined) {
                            if (ret.Static) throw CodeEE("STATICとGLOBALキーワードは同時に指定できません", sc)
                            else throw CodeEE("DYNAMICとGLOBALキーワードは同時に指定できません", sc)
                        }
                        ret.Global = true
                    }
                    "SAVEDATA" -> {
                        if (isPrivate) throw CodeEE("ローカル変数の宣言に${keyword}キーワードは指定できません", sc)
                        if (staticDefined) {
                            if (ret.Static) throw CodeEE("STATICとSAVEDATAキーワードは同時に指定できません", sc)
                            else throw CodeEE("DYNAMICとSAVEDATAキーワードは同時に指定できません", sc)
                        }
                        if (ret.Reference) throw CodeEE("${keyword}とREFキーワードは同時に指定できません", sc)
                        if (ret.Const) throw CodeEE("${keyword}とCONSTキーワードは同時に指定できません", sc)
                        if (ret.Save) throw CodeEE("${keyword}キーワードが二重に指定されています", sc)
                        ret.Save = true
                    }
                    "CHARADATA" -> {
                        if (isPrivate) throw CodeEE("ローカル変数の宣言に${keyword}キーワードは指定できません", sc)
                        if (ret.Reference) throw CodeEE("${keyword}とREFキーワードは同時に指定できません", sc)
                        if (ret.Const) throw CodeEE("${keyword}とCONSTキーワードは同時に指定できません", sc)
                        if (staticDefined) {
                            if (ret.Static) throw CodeEE("${keyword}とSTATICキーワードは同時に指定できません", sc)
                            else throw CodeEE("${keyword}とDYNAMICキーワードは同時に指定できません", sc)
                        }
                        if (ret.Global) throw CodeEE("${keyword}とGLOBALキーワードは同時に指定できません", sc)
                        if (ret.CharaData) throw CodeEE("${keyword}キーワードが二重に指定されています", sc)
                        ret.CharaData = true
                    }
                    else -> {
                        ret.Name = keyword
                        break@whilebreak
                    }
                }
            }
            if (ret.Name == null) throw CodeEE("${keyword}の後に有効な変数名が指定されていません", sc)
            val err = GlobalStatic.IdentifierDictionary!!.let { d ->
                if (isPrivate) d.checkUserPrivateVarName(ret.Name!!) else d.checkUserVarName(ret.Name!!)
            }
            if (err != null) {
                if (err.second >= 2) throw CodeEE(err.first, sc)
                ParserMediator.warn(err.first, sc, err.second)
            }
            val sizeNum = ArrayList<Int>()
            if (wc.eol) {
                if (ret.Const) throw CodeEE("CONSTキーワードが指定されていますが初期値が設定されていません")
                sizeNum.add(1)
            } else if (wc.current.type == ',') {
                while (!wc.eol) {
                    if (wc.current.type == '=') break
                    if (wc.current.type != ',') throw CodeEE("書式が間違っています", sc)
                    wc.shiftNext()
                    if (ret.Reference) {
                        sizeNum.add(0)
                        if (wc.eol) break
                        if (wc.current.type == ',') continue
                    }
                    if (wc.eol) throw CodeEE("カンマの後に有効な定数式が指定されていません", sc)
                    val arg = ExpressionParser.reduceIntegerTerm(wc, TermEndWith.Comma_Assignment)
                    val sizeTerm = arg.restructure(GlobalStatic.EMediator!!) as? SingleTerm
                    if (sizeTerm == null || sizeTerm.getOperandType() != EType.Int64)
                        throw CodeEE("カンマの後に有効な定数式が指定されていません", sc)
                    if (ret.Reference) {
                        if (sizeTerm.int != 0L) throw CodeEE("参照型変数にはサイズを指定できません(サイズを省略するか0を指定してください)", sc)
                        continue
                    } else if (sizeTerm.int <= 0 || sizeTerm.int > 1000000)
                        throw CodeEE("ユーザー定義変数のサイズは1以上1000000以下でなければなりません", sc)
                    sizeNum.add(sizeTerm.int.toInt())
                }
            }
            if (wc.current.type != '=') {
                if (ret.Const) throw CodeEE("CONSTキーワードが指定されていますが初期値が設定されていません")
            } else {
                if ((wc.current as OperatorWord).code != OperatorCode.Assignment) throw CodeEE("予期しない演算子を発見しました")
                if (ret.Reference) throw CodeEE("参照型変数には初期値を設定できません")
                if (sizeNum.size >= 2) throw CodeEE("多次元変数には初期値を設定できません")
                if (ret.CharaData) throw CodeEE("キャラ型変数には初期値を設定できません")
                var size = 0
                if (sizeNum.size == 1) size = sizeNum[0]
                wc.shiftNext()
                val terms = ExpressionParser.reduceArguments(wc, ArgsEndWith.EoL, false)
                if (terms.isEmpty()) throw CodeEE("配列の初期値は省略できません")
                if (size > 0) {
                    if (terms.size > size) throw CodeEE("初期値の数が配列のサイズを超えています")
                    if (ret.Const && terms.size != size) throw CodeEE("定数の初期値の数が配列のサイズと一致しません")
                }
                if (dims) ret.DefaultStr = arrayOfNulls(terms.size) else ret.DefaultInt = LongArray(terms.size)
                for (i in terms.indices) {
                    val t = terms[i] ?: throw CodeEE("配列の初期値は省略できません")
                    val r = t.restructure(GlobalStatic.EMediator!!)
                    val sTerm = r as? SingleTerm ?: throw CodeEE("配列の初期値には定数のみ指定できます")
                    if (dims != sTerm.isString) throw CodeEE("変数の型と初期値の型が一致していません")
                    if (dims) ret.DefaultStr!![i] = sTerm.str else ret.DefaultInt!![i] = sTerm.int
                }
                if (sizeNum.isEmpty()) sizeNum.add(terms.size)
            }
            if (!wc.eol) throw CodeEE("書式が間違っています", sc)
            if (sizeNum.isEmpty()) sizeNum.add(1)
            ret.Private = isPrivate
            ret.Dimension = sizeNum.size
            if (ret.Const && ret.Dimension > 1) throw CodeEE("CONSTキーワードが指定された変数を多次元配列にはできません")
            if (ret.CharaData && ret.Dimension > 2) throw CodeEE("3次元以上のキャラ型変数を宣言することはできません", sc)
            if (ret.Dimension > 3) throw CodeEE("4次元以上の配列変数を宣言することはできません", sc)
            ret.Lengths = IntArray(sizeNum.size)
            if (ret.Reference) return ret
            var totalBytes = 1L
            for (i in sizeNum.indices) {
                ret.Lengths!![i] = sizeNum[i]
                totalBytes *= sizeNum[i]
            }
            if (totalBytes <= 0 || totalBytes > 1000000)
                throw CodeEE("ユーザー定義変数のサイズは1以上1000000以下でなければなりません", sc)
            // Binary save is used internally so SAVEDATA restrictions for text saves are not needed.
            return ret
        }
    }
}

class DimLineWC(val wc: WordCollection, val dims: Boolean, val isPrivate: Boolean, val sc: ScriptPosition?)
