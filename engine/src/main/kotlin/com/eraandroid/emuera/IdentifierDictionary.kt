package com.eraandroid.emuera

import com.eraandroid.emuera.config.Config
import com.eraandroid.emuera.gamedata.DefineMacro
import com.eraandroid.emuera.gamedata.ParserMediator
import com.eraandroid.emuera.gamedata.expression.IOperandTerm
import com.eraandroid.emuera.gamedata.function.*
import com.eraandroid.emuera.gamedata.variable.*
import com.eraandroid.emuera.gameproc.LabelDictionary
import com.eraandroid.emuera.gameproc.function.FunctionIdentifier
import com.eraandroid.emuera.sub.*

class IdentifierDictionary(private val varData: VariableData) {
    private enum class DefinedNameType {
        None, Reserved, SystemVariable, SystemMethod, SystemInstrument, UserGlobalVariable, UserMacro, UserRefMethod, NameSpace
    }

    private val nameDic = HashMap<String, DefinedNameType>()
    private val privateDimList = ArrayList<String>()
    private val disableList = ArrayList<String>()
    private val varTokenDic: MutableMap<String, VariableToken>
    private val localvarTokenDic: Map<String, VariableLocal>
    private val instructionDic: Map<String, FunctionIdentifier>
    private val methodDic: Map<String, FunctionMethod>
    private val refmethodDic = HashMap<String, UserDefinedRefMethod>()
    val CharaDimList = ArrayList<UserDefinedCharaVariableToken>()
    private val macroDic = HashMap<String, DefineMacro>()

    init {
        for (r in arrayOf("IS", "TO", "INT", "STR", "REFFUNC", "STATIC", "DYNAMIC", "GLOBAL", "PRIVATE", "SAVEDATA", "CHARADATA", "REF", "__DEBUG__", "__SKIP__", "_"))
            nameDic[r] = DefinedNameType.Reserved
        instructionDic = FunctionIdentifier.getInstructionNameDic()
        varTokenDic = varData.getVarTokenDicClone()
        localvarTokenDic = varData.getLocalvarTokenDic()
        methodDic = FunctionMethodCreator.getMethodList()
        for (k in methodDic.keys) nameDic[k] = DefinedNameType.SystemMethod
        for (k in varTokenDic.keys) if (!nameDic.containsKey(k)) nameDic[k] = DefinedNameType.SystemVariable
        for (k in localvarTokenDic.keys) nameDic[k] = DefinedNameType.SystemVariable
        for (k in instructionDic.keys) if (!nameDic.containsKey(k)) nameDic[k] = DefinedNameType.SystemInstrument
    }

    private fun hasBadSymbol(s: String) = s.any { it in badSymbolAsIdentifier }

    /** @return (errMes, warnLevel) or null */
    fun checkUserLabelName(isFunction: Boolean, labelName: String): Pair<String, Int>? {
        if (labelName.isEmpty()) return Pair("ラベル名がありません", 2)
        if (hasBadSymbol(labelName)) return Pair("ラベル名${labelName}に\"_\"以外の記号が含まれています", 1)
        if (labelName[0].isDigit() && LangManager.getStrlenLang(labelName[0].toString()) == 1)
            return Pair("ラベル名${labelName}が半角数字から始まっています", 0)
        if (!isFunction || !Config.WarnFunctionOverloading) return null
        val t = nameDic[labelName] ?: return null
        return when (t) {
            DefinedNameType.Reserved -> if (Config.AllowFunctionOverloading)
                Pair("関数名${labelName}はEmueraの予約語と衝突しています。Emuera専用構文の構文解析に支障をきたす恐れがあります", 1)
            else Pair("関数名${labelName}はEmueraの予約語です", 2)
            DefinedNameType.SystemMethod -> if (Config.AllowFunctionOverloading)
                Pair("関数名${labelName}はEmueraの式中関数を上書きします", 1)
            else Pair("関数名${labelName}はEmueraの式中関数名として使われています", 2)
            DefinedNameType.SystemVariable -> Pair("関数名${labelName}はEmueraの変数で使われています", 1)
            DefinedNameType.SystemInstrument -> Pair("関数名${labelName}はEmueraの変数もしくは命令で使われています", 1)
            DefinedNameType.UserMacro -> Pair("関数名${labelName}はマクロに使用されています", 2)
            DefinedNameType.UserRefMethod -> Pair("関数名${labelName}は参照型関数の名称に使用されています", 2)
            else -> null
        }
    }

    private fun nameConflict(kind: String, name: String, macroMsg: String): Pair<String, Int>? {
        val t = nameDic[name] ?: return null
        return when (t) {
            DefinedNameType.Reserved -> Pair("$kind${name}はEmueraの予約語です", 2)
            DefinedNameType.SystemInstrument, DefinedNameType.SystemMethod -> Pair("$kind${name}はEmueraの命令名として使われています", 2)
            DefinedNameType.SystemVariable -> Pair("$kind${name}はEmueraの変数名として使われています", 2)
            DefinedNameType.UserMacro -> Pair("$kind$name$macroMsg", 2)
            DefinedNameType.UserGlobalVariable -> Pair("$kind${name}はユーザー定義の広域変数名に使用されています", 2)
            DefinedNameType.UserRefMethod -> Pair("$kind${name}は参照型関数の名称に使用されています", 2)
            else -> null
        }
    }

    fun checkUserVarName(varName: String): Pair<String, Int>? {
        if (hasBadSymbol(varName)) return Pair("変数名${varName}に\"_\"以外の記号が含まれています", 2)
        return nameConflict("変数名", varName, "は既にマクロ名に使用されています")
    }

    fun checkUserMacroName(macroName: String): Pair<String, Int>? {
        if (hasBadSymbol(macroName)) return Pair("マクロ名${macroName}に\"_\"以外の記号が含まれています", 2)
        return nameConflict("マクロ名", macroName, "は既にマクロ名に使用されています")
    }

    fun checkUserPrivateVarName(varName: String): Pair<String, Int>? {
        if (varName.isEmpty()) return Pair("変数名がありません", 2)
        if (hasBadSymbol(varName)) return Pair("変数名${varName}に\"_\"以外の記号が含まれています", 2)
        if (varName[0].isDigit()) return Pair("変数名${varName}が半角数字から始まっています", 2)
        val t = nameDic[varName]
        if (t == DefinedNameType.Reserved || t == DefinedNameType.SystemInstrument || t == DefinedNameType.SystemMethod)
            return nameConflict("変数名", varName, "")
        val ret = nameConflict("変数名", varName, "はマクロに使用されています")
        privateDimList.add(varName)
        return ret
    }

    fun addUseDefinedVariable(v: VariableToken) {
        varTokenDic[v.name] = v
        nameDic[v.name] = DefinedNameType.UserGlobalVariable
    }

    fun addMacro(mac: DefineMacro) {
        if (nameDic.containsKey(mac.keyword)) throw IllegalArgumentException(mac.keyword)
        nameDic[mac.keyword] = DefinedNameType.UserMacro
        macroDic[mac.keyword] = mac
    }

    fun addRefMethod(refm: UserDefinedRefMethod) {
        if (nameDic.containsKey(refm.name)) throw IllegalArgumentException(refm.name)
        refmethodDic[refm.name] = refm
        nameDic[refm.name] = DefinedNameType.UserRefMethod
    }

    fun useMacro(): Boolean = macroDic.isNotEmpty()

    fun getMacro(keyIn: String): DefineMacro? {
        val key = if (Config.ICVariable) keyIn.uppercase() else keyIn
        return macroDic[key]
    }

    fun getVariableToken(keyIn: String, subKeyIn: String?, allowPrivate: Boolean): VariableToken? {
        var key = keyIn
        var subKey = subKeyIn
        if (Config.ICVariable) key = key.uppercase()
        if (allowPrivate) {
            val line = GlobalStatic.Process?.getScaningLine()
            val parent = line?.parentLabelLine
            if (parent != null) {
                val ret = parent.getPrivateVariable(key)
                if (ret != null) {
                    if (subKey != null) throw CodeEE("プライベート変数${key}に対して@が使われました")
                    return ret
                }
            }
        }
        val local = localvarTokenDic[key]
        if (local != null) {
            if (local.isForbid) throw CodeEE("呼び出された変数\"$key\"は設定により使用が禁止されています")
            val line = GlobalStatic.Process?.getScaningLine()
            if (subKey.isNullOrEmpty()) {
                if (line?.parentLabelLine == null) throw CodeEE("実行中の関数が存在しないため${key}を取得又は変更できませんでした")
                subKey = line.parentLabelLine!!.labelName
            } else {
                ParserMediator.warn("コード中でローカル変数を@付きで呼ぶことは推奨されません(代わりに*.ERHファイルの利用を検討してください)", line, 1, false, false)
                if (Config.ICFunction) subKey = subKey.uppercase()
            }
            return local.getExistLocalVariableToken(subKey) ?: local.getNewLocalVariableToken(subKey, line!!.parentLabelLine!!)
        }
        val ret = varTokenDic[key]
        if (ret != null) {
            if (ret.isForbid) {
                if (!ret.canForbid) throw ExeEE("CanForbidでない変数\"${ret.name}\"にIsForbidがついている")
                throw CodeEE("呼び出された変数\"${ret.name}\"は設定により使用が禁止されています")
            }
            if (subKey != null) throw CodeEE("ローカル変数でない変数${key}に対して@が使われました")
            return ret
        }
        if (subKey != null) throw CodeEE("@の使い方が不正です")
        return null
    }

    fun getFunctionIdentifier(str: String?): FunctionIdentifier? {
        if (str.isNullOrEmpty()) return null
        val key = if (Config.ICFunction) str.uppercase() else str
        return instructionDic[key]
    }

    fun getOverloadedList(labelDic: LabelDictionary): List<String> {
        val list = ArrayList<String>()
        for (k in methodDic.keys) {
            val func = labelDic.getNonEventLabel(k) ?: continue
            if (!func.isMethod) continue
            list.add(k)
        }
        return list
    }

    fun getRefMethod(codeStrIn: String): UserDefinedRefMethod? {
        val codeStr = if (Config.ICFunction) codeStrIn.uppercase() else codeStrIn
        return refmethodDic[codeStr]
    }

    fun getFunctionMethod(labelDic: LabelDictionary?, codeStrIn: String, arguments: Array<IOperandTerm?>?, userDefinedOnly: Boolean): IOperandTerm? {
        val codeStr = if (Config.ICFunction) codeStrIn.uppercase() else codeStrIn
        if (arguments == null) {
            val r = refmethodDic[codeStr] ?: return null
            return UserDefinedRefMethodNoArgTerm(r)
        }
        if (labelDic != null && labelDic.initialized) {
            val r = refmethodDic[codeStr]
            if (r != null) return UserDefinedRefMethodTerm(r, arguments)
            val func = labelDic.getNonEventLabel(codeStr)
            if (func != null) {
                if (userDefinedOnly && !func.isMethod)
                    throw CodeEE("#FUNCTIONが指定されていない関数\"@${func.labelName}\"をCALLF系命令で呼び出そうとしました")
                if (func.isMethod) {
                    val errMes = arrayOfNulls<String>(1)
                    return UserDefinedMethodTerm.create(func, arguments, errMes) ?: throw CodeEE(errMes[0] ?: "")
                }
                if (!methodDic.containsKey(codeStr))
                    throw CodeEE("#FUNCTIONが定義されていない関数(${func.position?.filename}:${func.position?.lineNo}行目)を式中で呼び出そうとしました")
            }
        }
        if (userDefinedOnly) return null
        val method = methodDic[codeStr] ?: return null
        val errmes = method.checkArgumentType(codeStr, arguments)
        if (errmes != null) throw CodeEE(errmes)
        return FunctionMethodTerm(method, arguments)
    }

    fun throwException(str: String, isFunc: Boolean): Nothing {
        var idStr = str
        if (Config.ICFunction || Config.ICVariable) idStr = idStr.uppercase()
        if (disableList.contains(idStr)) throw CodeEE("\"$str\"は#DISABLEが宣言されています")
        if (!isFunc && privateDimList.contains(idStr)) throw IdentifierNotFoundCodeEE("変数\"$str\"はこの関数中では定義されていません")
        when (nameDic[idStr]) {
            DefinedNameType.Reserved -> throw CodeEE("Emueraの予約語\"$str\"が不正な使われ方をしています")
            DefinedNameType.SystemVariable, DefinedNameType.UserGlobalVariable ->
                if (isFunc) throw CodeEE("変数名\"$str\"が関数のように使われています")
            DefinedNameType.SystemMethod, DefinedNameType.UserRefMethod ->
                if (!isFunc) throw CodeEE("関数名\"$str\"が変数のように使われています")
            DefinedNameType.UserMacro -> throw CodeEE("予期しないマクロ名\"$str\"です")
            DefinedNameType.SystemInstrument ->
                if (isFunc) throw CodeEE("命令名\"$str\"が関数のように使われています")
                else throw CodeEE("命令名\"$str\"が変数のように使われています")
            else -> {}
        }
        throw IdentifierNotFoundCodeEE("\"$idStr\"は解釈できない識別子です")
    }

    fun resizeLocalVars(key: String, subKey: String, newSize: Int) = localvarTokenDic[key]!!.resizeLocalVariableToken(subKey, newSize)
    fun getLocalDefaultSize(key: String): Int = localvarTokenDic[key]!!.getDefaultSize()
    fun getLocalIsForbid(key: String): Boolean = localvarTokenDic[key]!!.isForbid
    fun getVarTokenIsForbid(key: String): Boolean {
        localvarTokenDic[key]?.let { return it.isForbid }
        return varTokenDic[key]?.isForbid ?: true
    }

    companion object {
        private val badSymbolAsIdentifier = charArrayOf(
            '+', '-', '*', '/', '%', '=', '!', '<', '>', '|', '&', '^', '~',
            ' ', '　', '\t', '"', '(', ')', '{', '}', '[', ']', ',', '.', ':',
            '\\', '@', '$', '#', '?', ';', '\''
        )
        private val regexCom = Regex("^COM[0-9]+$")
        private val regexComAble = Regex("^COM_ABLE[0-9]+$")
        private val regexAblup = Regex("^ABLUP[0-9]+$")

        fun isEventLabelName(labelName: String): Boolean = when (labelName) {
            "EVENTFIRST", "EVENTTRAIN", "EVENTSHOP", "EVENTBUY", "EVENTCOM", "EVENTTURNEND", "EVENTCOMEND", "EVENTEND", "EVENTLOAD" -> true
            else -> false
        }

        fun isSystemLabelName(labelName: String): Boolean {
            when (labelName) {
                "EVENTFIRST", "EVENTTRAIN", "EVENTSHOP", "EVENTBUY", "EVENTCOM", "EVENTTURNEND", "EVENTCOMEND", "EVENTEND",
                "SHOW_STATUS", "SHOW_USERCOM", "USERCOM", "SOURCE_CHECK", "CALLTRAINEND", "SHOW_JUEL", "SHOW_ABLUP_SELECT",
                "USERABLUP", "SHOW_SHOP", "SAVEINFO", "USERSHOP", "EVENTLOAD", "TITLE_LOADGAME", "SYSTEM_AUTOSAVE",
                "SYSTEM_TITLE", "SYSTEM_LOADEND" -> return true
            }
            if (labelName.startsWith("COM") && (regexCom.matches(labelName) || regexComAble.matches(labelName))) return true
            if (labelName.startsWith("ABLUP") && regexAblup.matches(labelName)) return true
            return false
        }
    }
}
