package com.eraandroid.emuera.gamedata.variable

import com.eraandroid.emuera.GlobalStatic
import com.eraandroid.emuera.Program
import com.eraandroid.emuera.config.Config
import com.eraandroid.emuera.gamedata.ConstantData
import com.eraandroid.emuera.gamedata.GameBase
import com.eraandroid.emuera.gameproc.UserDefinedVariableData
import com.eraandroid.emuera.sub.*

/** 変数全部 */
class VariableData(val gameBase: GameBase, val constant: ConstantData) {
    private val L = VariableCode.__LOWERCASE__

    val dataInteger = LongArray(VariableCode.__COUNT_INTEGER__)
    val dataIntegerArray: Array<LongArray> = Array(VariableCode.__COUNT_INTEGER_ARRAY__) { LongArray(constant.VariableIntArrayLength[it]) }
    val dataString = arrayOfNulls<String>(VariableCode.__COUNT_STRING__)
    val dataStringArray: Array<Array<String?>> = Array(VariableCode.__COUNT_STRING_ARRAY__) { arrayOfNulls<String>(constant.VariableStrArrayLength[it]) }
    val dataIntegerArray2D: Array<IntArr2> = Array(VariableCode.__COUNT_INTEGER_ARRAY_2D__) {
        val l = constant.VariableIntArray2DLength[it]; EraArrays.newInt2((l shr 32).toInt(), (l and 0x7FFFFFFF).toInt())
    }
    val dataStringArray2D: Array<StrArr2> = Array(VariableCode.__COUNT_STRING_ARRAY_2D__) {
        val l = constant.VariableStrArray2DLength[it]; EraArrays.newStr2((l shr 32).toInt(), (l and 0x7FFFFFFF).toInt())
    }
    val dataIntegerArray3D: Array<IntArr3> = Array(VariableCode.__COUNT_INTEGER_ARRAY_3D__) {
        val l = constant.VariableIntArray3DLength[it]
        EraArrays.newInt3((l shr 40).toInt(), ((l shr 20) and 0xFFFFF).toInt(), (l and 0xFFFFF).toInt())
    }
    val dataStringArray3D: Array<StrArr3> = Array(VariableCode.__COUNT_STRING_ARRAY_3D__) {
        val l = constant.VariableStrArray3DLength[it]
        EraArrays.newStr3((l shr 40).toInt(), ((l shr 20) and 0xFFFFF).toInt(), (l and 0xFFFFF).toInt())
    }
    val characterList: MutableList<CharacterData> = ArrayList()

    var lastLoadVersion = -1L
    var lastLoadNo = -1L
    var lastLoadText = ""

    private val varTokenDic = LinkedHashMap<String, VariableToken>()
    private val localvarTokenDic = LinkedHashMap<String, VariableLocal>()
    private val userDefinedStaticVarList = ArrayList<UserDefinedVariableToken>()
    private val userDefinedGlobalVarList = ArrayList<UserDefinedVariableToken>()
    private val userDefinedSaveVarList = Array(6) { ArrayList<UserDefinedVariableToken>() }
    private val userDefinedGlobalSaveVarList = Array(6) { ArrayList<UserDefinedVariableToken>() }
    val userDefinedCharaVarList = ArrayList<UserDefinedCharaVariableToken>()

    init {
        setDefaultValue(constant)
        fun i1(name: String, code: Int) { varTokenDic[name] = SystemArrayToken(code, this, dataIntegerArray[code and L]) }
        fun s1(name: String, code: Int) { varTokenDic[name] = SystemArrayToken(code, this, dataStringArray[code and L]) }
        for (n in listOf("DAY", "MONEY", "ITEM", "FLAG", "TFLAG", "UP", "PALAMLV", "EXPLV", "EJAC", "DOWN", "RESULT", "COUNT",
            "TARGET", "ASSI", "MASTER", "NOITEM", "LOSEBASE", "SELECTCOM", "ASSIPLAY", "PREVCOM", "TIME", "ITEMSALES",
            "PLAYER", "NEXTCOM", "PBAND", "BOUGHT", "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N",
            "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z", "GLOBAL", "RANDDATA"))
            i1(n, VariableIdentifier.getVarNameDic()[n]!!)
        s1("SAVESTR", VariableCode.SAVESTR)
        s1("TSTR", VariableCode.TSTR)
        s1("STR", VariableCode.STR)
        s1("RESULTS", VariableCode.RESULTS)
        s1("GLOBALS", VariableCode.GLOBALS)
        varTokenDic["SAVEDATA_TEXT"] = StrVariableToken(VariableCode.SAVEDATA_TEXT, this)
        varTokenDic["ISASSI"] = CharaIntVariableToken(VariableCode.ISASSI, this)
        varTokenDic["NO"] = CharaIntVariableToken(VariableCode.NO, this)
        for (n in listOf("BASE", "MAXBASE", "ABL", "TALENT", "EXP", "MARK", "PALAM", "SOURCE", "EX", "CFLAG", "JUEL", "RELATION",
            "EQUIP", "TEQUIP", "STAIN", "GOTJUEL", "NOWEX", "DOWNBASE", "CUP", "CDOWN", "TCVAR"))
            varTokenDic[n] = CharaInt1DVariableToken(VariableIdentifier.getVarNameDic()[n]!!, this)
        for (n in listOf("NAME", "CALLNAME", "NICKNAME", "MASTERNAME"))
            varTokenDic[n] = CharaStrVariableToken(VariableIdentifier.getVarNameDic()[n]!!, this)
        varTokenDic["CSTR"] = CharaStr1DVariableToken(VariableCode.CSTR, this)
        varTokenDic["CDFLAG"] = CharaInt2DVariableToken(VariableCode.CDFLAG, this)
        for ((n, c) in listOf("DITEMTYPE" to VariableCode.DITEMTYPE, "DA" to VariableCode.DA, "DB" to VariableCode.DB,
            "DC" to VariableCode.DC, "DD" to VariableCode.DD, "DE" to VariableCode.DE))
            varTokenDic[n] = SystemArrayToken(c, this, dataIntegerArray2D[c and L])
        varTokenDic["TA"] = SystemArrayToken(VariableCode.TA, this, dataIntegerArray3D[VariableCode.TA and L])
        varTokenDic["TB"] = SystemArrayToken(VariableCode.TB, this, dataIntegerArray3D[VariableCode.TB and L])

        varTokenDic["ITEMPRICE"] = Int1DConstantToken(VariableCode.ITEMPRICE, this, constant.ItemPrice)
        for (n in listOf("ABLNAME", "TALENTNAME", "EXPNAME", "MARKNAME", "PALAMNAME", "ITEMNAME", "TRAINNAME", "BASENAME",
            "SOURCENAME", "EXNAME", "EQUIPNAME", "TEQUIPNAME", "FLAGNAME", "TFLAGNAME", "CFLAGNAME", "TCVARNAME", "CSTRNAME",
            "STAINNAME", "CDFLAGNAME1", "CDFLAGNAME2", "STRNAME", "TSTRNAME", "SAVESTRNAME", "GLOBALNAME", "GLOBALSNAME"))
            varTokenDic[n] = Str1DConstantToken(VariableIdentifier.getVarNameDic()[n]!!, this)

        val author = StrConstantToken(VariableCode.GAMEBASE_AUTHOR, this, gameBase.ScriptAutherName)
        varTokenDic["GAMEBASE_AUTHER"] = author
        varTokenDic["GAMEBASE_AUTHOR"] = author
        varTokenDic["GAMEBASE_INFO"] = StrConstantToken(VariableCode.GAMEBASE_INFO, this, gameBase.ScriptDetail)
        varTokenDic["GAMEBASE_YEAR"] = StrConstantToken(VariableCode.GAMEBASE_YEAR, this, gameBase.ScriptYear)
        varTokenDic["GAMEBASE_TITLE"] = StrConstantToken(VariableCode.GAMEBASE_TITLE, this, gameBase.ScriptTitle)
        varTokenDic["GAMEBASE_GAMECODE"] = IntConstantToken(VariableCode.GAMEBASE_GAMECODE, this, gameBase.ScriptUniqueCode)
        varTokenDic["GAMEBASE_VERSION"] = IntConstantToken(VariableCode.GAMEBASE_VERSION, this, gameBase.ScriptVersion)
        varTokenDic["GAMEBASE_ALLOWVERSION"] = IntConstantToken(VariableCode.GAMEBASE_ALLOWVERSION, this, gameBase.ScriptCompatibleMinVersion)
        varTokenDic["GAMEBASE_DEFAULTCHARA"] = IntConstantToken(VariableCode.GAMEBASE_DEFAULTCHARA, this, gameBase.DefaultCharacter)
        varTokenDic["GAMEBASE_NOITEM"] = IntConstantToken(VariableCode.GAMEBASE_NOITEM, this, gameBase.DefaultNoItem)

        varTokenDic["RAND"] = if (Config.CompatiRAND) PseudoIntToken(VariableCode.RAND, this, false) { exm, a ->
            var i = a[0]
            if (i == 0L) 0L else { if (i < 0) i = -i; exm.vEvaluator.getNextRand(32768) % i }
        } else PseudoIntToken(VariableCode.RAND, this, false) { exm, a ->
            val i = a[0]
            if (i <= 0) throw CodeEE("RANDの引数に0以下の値(${i})が指定されました")
            exm.vEvaluator.getNextRand(i)
        }
        varTokenDic["CHARANUM"] = PseudoIntToken(VariableCode.CHARANUM, this, false) { _, _ -> characterList.size.toLong() }
        varTokenDic["LASTLOAD_TEXT"] = PseudoStrToken(VariableCode.LASTLOAD_TEXT, this, false) { _, _ -> lastLoadText }
        varTokenDic["LASTLOAD_VERSION"] = PseudoIntToken(VariableCode.LASTLOAD_VERSION, this, false) { _, _ -> lastLoadVersion }
        varTokenDic["LASTLOAD_NO"] = PseudoIntToken(VariableCode.LASTLOAD_NO, this, false) { _, _ -> lastLoadNo }
        varTokenDic["LINECOUNT"] = PseudoIntToken(VariableCode.LINECOUNT, this, false) { exm, _ -> exm.console.lineCount }
        varTokenDic["ISTIMEOUT"] = PseudoIntToken(VariableCode.ISTIMEOUT, this, false) { _, _ -> if (GlobalStatic.Console!!.isTimeOut) 1L else 0L }
        varTokenDic["__INT_MAX__"] = PseudoIntToken(VariableCode.__INT_MAX__, this, true) { _, _ -> Long.MAX_VALUE }
        varTokenDic["__INT_MIN__"] = PseudoIntToken(VariableCode.__INT_MIN__, this, true) { _, _ -> Long.MIN_VALUE }
        varTokenDic["EMUERA_VERSION"] = PseudoStrToken(VariableCode.EMUERA_VERSION, this, true) { _, _ -> Program.InternalEmueraVer }
        varTokenDic["WINDOW_TITLE"] = WindowTitleToken(VariableCode.WINDOW_TITLE, this)
        varTokenDic["MONEYLABEL"] = SimpleStrToken(VariableCode.MONEYLABEL, this) { Config.MoneyLabel }
        varTokenDic["DRAWLINESTR"] = SimpleStrToken(VariableCode.DRAWLINESTR, this) { it.console.getDefStBar() }
        if (!Program.DebugMode) {
            varTokenDic["__FILE__"] = PseudoStrToken(VariableCode.__FILE__, this, true) { _, _ -> "" }
            varTokenDic["__FUNCTION__"] = PseudoStrToken(VariableCode.__FUNCTION__, this, true) { _, _ -> "" }
            varTokenDic["__LINE__"] = PseudoIntToken(VariableCode.__LINE__, this, true) { _, _ -> 0L }
        } else {
            varTokenDic["__FILE__"] = PseudoStrToken(VariableCode.__FILE__, this, true) { exm, _ -> exm.process.getScaningLine()?.position?.filename ?: "" }
            varTokenDic["__FUNCTION__"] = PseudoStrToken(VariableCode.__FUNCTION__, this, true) { exm, _ -> exm.process.getScaningLine()?.parentLabelLine?.labelName ?: "" }
            varTokenDic["__LINE__"] = PseudoIntToken(VariableCode.__LINE__, this, true) { exm, _ -> exm.process.getScaningLine()?.position?.lineNo?.toLong() ?: -1L }
        }

        val createInt = { code: Int, sub: String, size: Int -> LocalInt1DVariableToken(code, this, sub, size) as LocalVariableToken }
        val createStr = { code: Int, sub: String, size: Int -> LocalStr1DVariableToken(code, this, sub, size) as LocalVariableToken }
        localvarTokenDic["LOCAL"] = VariableLocal(VariableCode.LOCAL, constant.VariableIntArrayLength[L and VariableCode.LOCAL], createInt)
        localvarTokenDic["ARG"] = VariableLocal(VariableCode.ARG, constant.VariableIntArrayLength[L and VariableCode.ARG], createInt)
        localvarTokenDic["LOCALS"] = VariableLocal(VariableCode.LOCALS, constant.VariableStrArrayLength[L and VariableCode.LOCALS], createStr)
        localvarTokenDic["ARGS"] = VariableLocal(VariableCode.ARGS, constant.VariableStrArrayLength[L and VariableCode.ARGS], createStr)
    }

    fun getVarTokenDicClone(): MutableMap<String, VariableToken> = LinkedHashMap(varTokenDic)
    fun getVarTokenDic(): Map<String, VariableToken> = varTokenDic
    fun getLocalvarTokenDic(): Map<String, VariableLocal> = localvarTokenDic
    fun getSystemVariableToken(str: String): VariableToken = varTokenDic[str]!!

    fun createUserDefCharaVariable(data: UserDefinedVariableData): UserDefinedCharaVariableToken {
        val index = userDefinedCharaVarList.size
        val code = if (data.TypeIsStr) {
            when (data.Dimension) { 1 -> VariableCode.CVARS; 2 -> VariableCode.CVARS2D; else -> throw ExeEE("異常な変数宣言") }
        } else {
            when (data.Dimension) { 1 -> VariableCode.CVAR; 2 -> VariableCode.CVAR2D; else -> throw ExeEE("異常な変数宣言") }
        }
        val ret = UserDefinedCharaVariableToken(code, data, this, index)
        userDefinedCharaVarList.add(ret)
        return ret
    }

    private fun staticCode(data: UserDefinedVariableData): Int = if (data.TypeIsStr) when (data.Dimension) {
        1 -> VariableCode.VARS; 2 -> VariableCode.VARS2D; 3 -> VariableCode.VARS3D; else -> throw ExeEE("異常な変数宣言")
    } else when (data.Dimension) {
        1 -> VariableCode.VAR; 2 -> VariableCode.VAR2D; 3 -> VariableCode.VAR3D; else -> throw ExeEE("異常な変数宣言")
    }

    private fun refCode(data: UserDefinedVariableData): Int = if (data.TypeIsStr) when (data.Dimension) {
        1 -> VariableCode.REFS; 2 -> VariableCode.REFS2D; 3 -> VariableCode.REFS3D; else -> throw ExeEE("異常な変数宣言")
    } else when (data.Dimension) {
        1 -> VariableCode.REF; 2 -> VariableCode.REF2D; 3 -> VariableCode.REF3D; else -> throw ExeEE("異常な変数宣言")
    }

    fun createUserDefVariable(data: UserDefinedVariableData): UserDefinedVariableToken {
        val ret = StaticVariableToken(staticCode(data), data)
        if (ret.isGlobal) userDefinedGlobalVarList.add(ret) else userDefinedStaticVarList.add(ret)
        if (ret.isSavedata) {
            var type = ret.dimension * 2 - 2
            if (!ret.isString) type++
            if (ret.isGlobal) userDefinedGlobalSaveVarList[type].add(ret) else userDefinedSaveVarList[type].add(ret)
        }
        return ret
    }

    fun createPrivateVariable(data: UserDefinedVariableData): UserDefinedVariableToken {
        return when {
            data.Reference -> ReferenceToken(refCode(data), data)
            data.Static -> StaticVariableToken(staticCode(data), data).also { userDefinedStaticVarList.add(it) }
            else -> PrivateVariableToken(staticCode(data), data)
        }
    }

    fun setDefaultGlobalValue() {
        dataIntegerArray[L and VariableCode.GLOBAL].fill(0)
        dataStringArray[L and VariableCode.GLOBALS].fill(null)
        for (v in userDefinedGlobalVarList) v.setDefault()
    }

    fun setDefaultLocalValue() {
        for (local in localvarTokenDic.values) local.setDefault()
        for (v in userDefinedStaticVarList) v.setDefault()
    }

    fun clearLocalValue() { for (local in localvarTokenDic.values) local.clear() }

    /** ローカルとグローバル以外初期化 */
    fun setDefaultValue(constant: ConstantData) {
        dataInteger.fill(0)
        for (i in dataIntegerArray.indices) {
            when (i) {
                L and VariableCode.GLOBAL -> {}
                L and VariableCode.ITEMPRICE -> constant.ItemPrice.copyInto(dataIntegerArray[i], 0, 0, minOf(constant.ItemPrice.size, dataIntegerArray[i].size))
                else -> dataIntegerArray[i].fill(0)
            }
        }
        dataString.fill(null)
        for (i in dataStringArray.indices) {
            when (i) {
                L and VariableCode.GLOBALS -> {}
                L and VariableCode.STR -> {
                    val csv = constant.getCsvNameList(VariableCode.__DUMMY_STR__)
                    val dst = dataStringArray[i]
                    dst.fill(null)
                    csv.copyInto(dst, 0, 0, minOf(csv.size, dst.size))
                }
                else -> dataStringArray[i].fill(null)
            }
        }
        for (a in dataIntegerArray2D) EraArrays.clear(a)
        for (a in dataStringArray2D) EraArrays.clear(a)
        for (a in dataIntegerArray3D) EraArrays.clear(a)
        for (a in dataStringArray3D) EraArrays.clear(a)

        val palamlv = dataIntegerArray[L and VariableCode.PALAMLV]
        val defPalam = Config.PalamLvDef
        for (i in 0 until minOf(palamlv.size, defPalam.size)) palamlv[i] = defPalam[i]
        val explv = dataIntegerArray[L and VariableCode.EXPLV]
        val defExp = Config.ExpLvDef
        for (i in 0 until minOf(explv.size, defExp.size)) explv[i] = defExp[i]
        dataIntegerArray[L and VariableCode.ASSI].let { if (it.isNotEmpty()) it[0] = -1 }
        dataIntegerArray[L and VariableCode.TARGET].let { if (it.isNotEmpty()) it[0] = 1 }
        dataIntegerArray[L and VariableCode.PBAND].let { if (it.isNotEmpty()) it[0] = Config.PbandDef }
        dataIntegerArray[L and VariableCode.EJAC].let { if (it.isNotEmpty()) it[0] = 10000 }
        lastLoadVersion = -1
        lastLoadNo = -1
        lastLoadText = ""
    }

    fun saveToStream(writer: EraDataWriter) {
        for (i in 0 until VariableCode.__COUNT_SAVE_STRING__) writer.write(dataString[i])
        for (i in 0 until VariableCode.__COUNT_SAVE_INTEGER__) writer.write(dataInteger[i])
        for (i in 0 until VariableCode.__COUNT_SAVE_INTEGER_ARRAY__) writer.write(dataIntegerArray[i])
        for (i in 0 until VariableCode.__COUNT_SAVE_STRING_ARRAY__) writer.write(dataStringArray[i])
    }

    fun loadFromStream(reader: EraDataReader) {
        for (i in 0 until VariableCode.__COUNT_SAVE_STRING__) dataString[i] = reader.readString()
        for (i in 0 until VariableCode.__COUNT_SAVE_INTEGER__) dataInteger[i] = reader.readInt64()
        for (i in 0 until VariableCode.__COUNT_SAVE_INTEGER_ARRAY__) reader.readInt64Array(dataIntegerArray[i])
        for (i in 0 until VariableCode.__COUNT_SAVE_STRING_ARRAY__) reader.readStringArray(dataStringArray[i])
    }

    private fun key(code: Int) = VariableCode.toString(code)

    @Suppress("UNCHECKED_CAST")
    fun loadFromStreamExtended(reader: EraDataReader, version: Int) {
        val strDic = reader.readStringExtended()
        val intDic = reader.readInt64Extended()
        val strListDic = reader.readStringArrayExtended()
        val intListDic = reader.readInt64ArrayExtended()
        reader.readStringArray2DExtended()
        val int2DListDic = reader.readInt64Array2DExtended()
        reader.readStringArray3DExtended()
        val int3DListDic = reader.readInt64Array3DExtended()
        for (code in VariableIdentifier.getExtSaveList(VariableCode.__STRING__)) strDic[key(code)]?.let { dataString[L and code] = it }
        for (code in VariableIdentifier.getExtSaveList(VariableCode.__INTEGER__)) intDic[key(code)]?.let { dataInteger[L and code] = it }
        for (code in VariableIdentifier.getExtSaveList(VariableCode.__ARRAY_1D__ or VariableCode.__STRING__))
            strListDic[key(code)]?.let { copyList(it, dataStringArray[L and code]) }
        for (code in VariableIdentifier.getExtSaveList(VariableCode.__ARRAY_1D__ or VariableCode.__INTEGER__))
            intListDic[key(code)]?.let { copyListL(it, dataIntegerArray[L and code]) }
        for (code in VariableIdentifier.getExtSaveList(VariableCode.__ARRAY_2D__ or VariableCode.__INTEGER__))
            int2DListDic[key(code)]?.let { copyList2D(it, dataIntegerArray2D[L and code]) }
        for (code in VariableIdentifier.getExtSaveList(VariableCode.__ARRAY_3D__ or VariableCode.__INTEGER__))
            int3DListDic[key(code)]?.let { copyList3D(it, dataIntegerArray3D[L and code]) }
        if (version < 1808) return
        val sl = reader.readStringArrayExtended()
        val il = reader.readInt64ArrayExtended()
        reader.readStringArray2DExtended()
        val i2 = reader.readInt64Array2DExtended()
        reader.readStringArray3DExtended()
        val i3 = reader.readInt64Array3DExtended()
        for (v in userDefinedSaveVarList[0]) sl[v.name]?.let { copyList(it, v.getArray() as Array<String?>) }
        for (v in userDefinedSaveVarList[1]) il[v.name]?.let { copyListL(it, v.getArray() as LongArray) }
        for (v in userDefinedSaveVarList[3]) i2[v.name]?.let { copyList2D(it, v.getArray() as IntArr2) }
        for (v in userDefinedSaveVarList[5]) i3[v.name]?.let { copyList3D(it, v.getArray() as IntArr3) }
    }

    private fun copyList(src: List<String>, dest: Array<String?>) { for (i in 0 until minOf(src.size, dest.size)) dest[i] = src[i] }
    private fun copyListL(src: List<Long>, dest: LongArray) { for (i in 0 until minOf(src.size, dest.size)) dest[i] = src[i] }
    private fun copyList2D(src: List<LongArray>, dest: IntArr2) {
        for (x in 0 until minOf(src.size, dest.size)) { val s = src[x]; for (y in 0 until minOf(s.size, dest[x].size)) dest[x][y] = s[y] }
    }
    private fun copyList3D(src: List<List<LongArray>>, dest: IntArr3) {
        for (x in 0 until minOf(src.size, dest.size)) {
            val sx = src[x]
            for (y in 0 until minOf(sx.size, dest[x].size)) { val s = sx[y]; for (z in 0 until minOf(s.size, dest[x][y].size)) dest[x][y][z] = s[z] }
        }
    }

    fun loadGlobalFromStream(reader: EraDataReader) {
        reader.readInt64Array(dataIntegerArray[L and VariableCode.GLOBAL])
        reader.readStringArray(dataStringArray[L and VariableCode.GLOBALS])
    }

    @Suppress("UNCHECKED_CAST")
    fun loadGlobalFromStream1808(reader: EraDataReader) {
        val sl = reader.readStringArrayExtended()
        val il = reader.readInt64ArrayExtended()
        reader.readStringArray2DExtended()
        val i2 = reader.readInt64Array2DExtended()
        reader.readStringArray3DExtended()
        val i3 = reader.readInt64Array3DExtended()
        for (v in userDefinedGlobalSaveVarList[0]) sl[v.name]?.let { copyList(it, v.getArray() as Array<String?>) }
        for (v in userDefinedGlobalSaveVarList[1]) il[v.name]?.let { copyListL(it, v.getArray() as LongArray) }
        for (v in userDefinedGlobalSaveVarList[3]) i2[v.name]?.let { copyList2D(it, v.getArray() as IntArr2) }
        for (v in userDefinedGlobalSaveVarList[5]) i3[v.name]?.let { copyList3D(it, v.getArray() as IntArr3) }
    }

    fun saveGlobalToStreamBinary(writer: EraBinaryDataWriter) {
        for ((k, v) in varTokenDic) if (v.isSavedata && !v.isCharacterData && v.isGlobal) writer.writeWithKey(k, v.getArray())
        for (v in userDefinedGlobalVarList) if (v.isSavedata) writer.writeWithKey(v.name, v.getArray())
    }

    fun saveToStreamBinary(writer: EraBinaryDataWriter) {
        for ((k, v) in varTokenDic) {
            if (v.isSavedata && !v.isCharacterData && !v.isGlobal) {
                val value: Any? = when {
                    v.dimension == 0 && v.isInteger -> dataInteger[v.codeInt]
                    v.dimension == 0 && v.isString -> dataString[v.codeInt] ?: ""
                    else -> v.getArray()
                }
                writer.writeWithKey(k, value)
            }
        }
        for (v in userDefinedStaticVarList) if (v.isSavedata) writer.writeWithKey(v.name, v.getArray())
    }

    fun loadFromStreamBinary(bReader: EraBinaryDataReader) { while (loadVariableBinary(bReader)) {} }

    /** 1808 キャラクタ型でない変数を一つ読む。ファイル終端の場合はfalseを返す */
    @Suppress("UNCHECKED_CAST")
    fun loadVariableBinary(reader: EraBinaryDataReader): Boolean {
        val (name, type) = reader.readVariableCode()
        var vToken: VariableToken? = null
        val dic = GlobalStatic.IdentifierDictionary!!
        if (name != null && !dic.getVarTokenIsForbid(name)) vToken = dic.getVariableToken(name, null, false)
        if (vToken != null && (vToken.isCharacterData || vToken.isConst || vToken.isPrivate || vToken.isLocal || vToken.isCalc)) vToken = null
        when (type) {
            EraSaveDataType.EOF -> return false
            EraSaveDataType.Int -> if (vToken == null || !vToken.isInteger || vToken.dimension != 0) reader.readInt() else vToken.setValue(reader.readInt(), null)
            EraSaveDataType.Str -> if (vToken == null || !vToken.isString || vToken.dimension != 0) reader.readString() else vToken.setValue(reader.readString(), null)
            EraSaveDataType.IntArray -> if (vToken == null || !vToken.isInteger || vToken.dimension != 1) reader.readIntArray(null, true) else reader.readIntArray(vToken.getArray() as LongArray, true)
            EraSaveDataType.IntArray2D -> if (vToken == null || !vToken.isInteger || vToken.dimension != 2) reader.readIntArray2D(null, true) else reader.readIntArray2D(vToken.getArray() as IntArr2, true)
            EraSaveDataType.IntArray3D -> if (vToken == null || !vToken.isInteger || vToken.dimension != 3) reader.readIntArray3D(null, true) else reader.readIntArray3D(vToken.getArray() as IntArr3, true)
            EraSaveDataType.StrArray -> if (vToken == null || !vToken.isString || vToken.dimension != 1) reader.readStrArray(null, true) else reader.readStrArray(vToken.getArray() as Array<String?>, true)
            EraSaveDataType.StrArray2D -> if (vToken == null || !vToken.isString || vToken.dimension != 2) reader.readStrArray2D(null, true) else reader.readStrArray2D(vToken.getArray() as StrArr2, true)
            EraSaveDataType.StrArray3D -> if (vToken == null || !vToken.isString || vToken.dimension != 3) reader.readStrArray3D(null, true) else reader.readStrArray3D(vToken.getArray() as StrArr3, true)
            else -> throw FileEE("データ異常")
        }
        return true
    }

    fun dispose() {
        clearLocalValue()
        characterList.clear()
    }
}
