package com.eraandroid.emuera.gamedata

import com.eraandroid.emuera.Program
import com.eraandroid.emuera.config.Config
import com.eraandroid.emuera.gamedata.variable.VariableCode
import com.eraandroid.emuera.gamedata.variable.VariableIdentifier
import com.eraandroid.emuera.gameview.EmueraConsole
import com.eraandroid.emuera.sub.*

enum class CharacterStrData { NAME, CALLNAME, NICKNAME, MASTERNAME, CSTR }
enum class CharacterIntData { BASE, ABL, TALENT, MARK, EXP, RELATION, CFLAG, EQUIP, JUEL }

@Suppress("PropertyName")
class ConstantData {
    companion object {
        private const val L = VariableCode.__LOWERCASE__
        private const val ablIndex = VariableCode.ABLNAME and L
        private const val expIndex = VariableCode.EXPNAME and L
        private const val talentIndex = VariableCode.TALENTNAME and L
        private const val paramIndex = VariableCode.PALAMNAME and L
        private const val trainIndex = VariableCode.TRAINNAME and L
        private const val markIndex = VariableCode.MARKNAME and L
        private const val itemIndex = VariableCode.ITEMNAME and L
        private const val baseIndex = VariableCode.BASENAME and L
        private const val sourceIndex = VariableCode.SOURCENAME and L
        private const val exIndex = VariableCode.EXNAME and L
        private const val strIndex = VariableCode.__DUMMY_STR__ and L
        private const val equipIndex = VariableCode.EQUIPNAME and L
        private const val tequipIndex = VariableCode.TEQUIPNAME and L
        private const val flagIndex = VariableCode.FLAGNAME and L
        private const val tflagIndex = VariableCode.TFLAGNAME and L
        private const val cflagIndex = VariableCode.CFLAGNAME and L
        private const val tcvarIndex = VariableCode.TCVARNAME and L
        private const val cstrIndex = VariableCode.CSTRNAME and L
        private const val stainIndex = VariableCode.STAINNAME and L
        private const val cdflag1Index = VariableCode.CDFLAGNAME1 and L
        private const val cdflag2Index = VariableCode.CDFLAGNAME2 and L
        private const val strnameIndex = VariableCode.STRNAME and L
        private const val tstrnameIndex = VariableCode.TSTRNAME and L
        private const val savestrnameIndex = VariableCode.SAVESTRNAME and L
        private const val globalIndex = VariableCode.GLOBALNAME and L
        private const val globalsIndex = VariableCode.GLOBALSNAME and L
        private const val countNameCsv = VariableCode.__COUNT_CSV_STRING_ARRAY_1D__
    }

    val MaxDataList = IntArray(countNameCsv)
    private val changedCode = HashSet<Int>()

    lateinit var VariableIntArrayLength: IntArray
    lateinit var VariableStrArrayLength: IntArray
    lateinit var VariableIntArray2DLength: LongArray
    lateinit var VariableStrArray2DLength: LongArray
    lateinit var VariableIntArray3DLength: LongArray
    lateinit var VariableStrArray3DLength: LongArray
    lateinit var CharacterIntArrayLength: IntArray
    lateinit var CharacterStrArrayLength: IntArray
    lateinit var CharacterIntArray2DLength: LongArray
    lateinit var CharacterStrArray2DLength: LongArray

    private val names = arrayOfNulls<Array<String?>>(countNameCsv)
    private val nameToIntDics = arrayOfNulls<HashMap<String, Int>>(countNameCsv)
    private val relationDic = HashMap<String, Int>()

    fun getCsvNameList(code: Int): Array<String?> = names[code and L]!!

    var ItemPrice: LongArray = LongArray(0)
    private val characterTmplList = ArrayList<CharacterTemplate>()
    private var output: EmueraConsole? = null
    private val useCompatiName = Config.CompatiCALLNAME

    init { setDefaultArrayLength() }

    private fun setDefaultArrayLength() {
        MaxDataList[ablIndex] = 100; MaxDataList[talentIndex] = 1000; MaxDataList[expIndex] = 100
        MaxDataList[markIndex] = 100; MaxDataList[trainIndex] = 1000; MaxDataList[paramIndex] = 200
        MaxDataList[itemIndex] = 1000; MaxDataList[baseIndex] = 100; MaxDataList[sourceIndex] = 1000
        MaxDataList[exIndex] = 100; MaxDataList[equipIndex] = 100; MaxDataList[tequipIndex] = 100
        MaxDataList[flagIndex] = 10000; MaxDataList[tflagIndex] = 1000; MaxDataList[cflagIndex] = 1000
        MaxDataList[tcvarIndex] = 100; MaxDataList[cstrIndex] = 100; MaxDataList[stainIndex] = 1000
        MaxDataList[strIndex] = 20000; MaxDataList[cdflag1Index] = 1; MaxDataList[cdflag2Index] = 1
        MaxDataList[strnameIndex] = 20000; MaxDataList[tstrnameIndex] = 100; MaxDataList[savestrnameIndex] = 100
        MaxDataList[globalIndex] = 1000; MaxDataList[globalsIndex] = 100

        VariableIntArrayLength = IntArray(VariableCode.__COUNT_INTEGER_ARRAY__) { 1000 }
        VariableStrArrayLength = IntArray(VariableCode.__COUNT_STRING_ARRAY__) { 100 }
        VariableIntArray2DLength = LongArray(VariableCode.__COUNT_INTEGER_ARRAY_2D__) { (100L shl 32) + 100L }
        VariableStrArray2DLength = LongArray(VariableCode.__COUNT_STRING_ARRAY_2D__) { (100L shl 32) + 100L }
        VariableIntArray3DLength = LongArray(VariableCode.__COUNT_INTEGER_ARRAY_3D__) { (100L shl 40) + (100L shl 20) + 100L }
        VariableStrArray3DLength = LongArray(VariableCode.__COUNT_STRING_ARRAY_3D__) { (100L shl 40) + (100L shl 20) + 100L }
        CharacterIntArrayLength = IntArray(VariableCode.__COUNT_CHARACTER_INTEGER_ARRAY__) { 100 }
        CharacterStrArrayLength = IntArray(VariableCode.__COUNT_CHARACTER_STRING_ARRAY__) { 100 }
        CharacterIntArray2DLength = LongArray(VariableCode.__COUNT_CHARACTER_INTEGER_ARRAY_2D__) { (1L shl 32) + 1L }
        CharacterStrArray2DLength = LongArray(VariableCode.__COUNT_CHARACTER_STRING_ARRAY_2D__) { (1L shl 32) + 1L }

        VariableIntArrayLength[L and VariableCode.FLAG] = 10000
        VariableIntArrayLength[L and VariableCode.ITEMPRICE] = MaxDataList[itemIndex]
        VariableIntArrayLength[L and VariableCode.RANDDATA] = 625
        VariableStrArrayLength[L and VariableCode.STR] = MaxDataList[strIndex]
        CharacterIntArrayLength[L and VariableCode.TALENT] = 1000
        CharacterIntArrayLength[L and VariableCode.CFLAG] = 1000
        CharacterIntArrayLength[L and VariableCode.JUEL] = 200
        CharacterIntArrayLength[L and VariableCode.GOTJUEL] = 200
    }

    private fun loadVariableSizeData(csvPath: String, disp: Boolean) {
        if (!FileUtil.exists(csvPath)) return
        val eReader = EraStreamReader(false)
        if (!eReader.open(FileUtil.resolve(csvPath).path)) { output?.printError(eReader.fileName + "のオープンに失敗しました"); return }
        var position: ScriptPosition? = null
        if (disp) output?.printSystemLine(eReader.fileName + "読み込み中・・・")
        try {
            while (true) {
                val st = eReader.readEnabledLine() ?: break
                position = ScriptPosition(eReader.fileName, eReader.lineNo)
                changeVariableSizeData(st.substring(), position)
            }
            position = ScriptPosition(eReader.fileName, -1)
        } catch (e: Exception) {
            if (position != null) ParserMediator.warn("予期しないエラーが発生しました", position, 3)
            else output?.printError("予期しないエラーが発生しました")
            return
        } finally {
            eReader.close()
        }
        decideActualArraySize(position)
    }

    private fun digitFirst(s: String?) = s != null && s.isNotEmpty() && s.trim().isNotEmpty() && Character.isDigit(s.trim()[0])

    private fun changeVariableSizeData(line: String, position: ScriptPosition) {
        val tokens = line.split(',')
        if (tokens.size < 2) { ParserMediator.warn("\",\"が必要です", position, 1); return }
        val id = VariableIdentifier.getVariableId(tokens[0].trim())
        if (id == null) { ParserMediator.warn("一つ目の値を変数名として認識できません", position, 1); return }
        if (!id.isArray1D && !id.isArray2D && !id.isArray3D) { ParserMediator.warn("配列変数でない変数${id}のサイズを変更できません", position, 1); return }
        if (id.isCalc || id.code == VariableCode.RANDDATA) { ParserMediator.warn("${id}のサイズは変更できません", position, 1); return }
        var length2 = 0
        var length3 = 0
        var length = tokens[1].trim().toIntOrNull()
        if (length == null) { ParserMediator.warn("二つ目の値を整数値として認識できません", position, 1); return }
        run check1@{
            if (length!! <= 0) {
                if (length == 0) { ParserMediator.warn("配列長に0は指定できません（変数を使用禁止にするには配列長に負の値を指定してください）", position, 2); return }
                if (!id.canForbid) { ParserMediator.warn("使用禁止にできない変数に対して負の配列長が指定されています", position, 2); return }
                if (tokens.size > 2 && digitFirst(tokens[2])) ParserMediator.warn("一次元配列のサイズ指定に不必要なデータは無視されます", position, 0)
                length = 0
                return@check1
            }
            val len = length!!
            if (id.isArray1D) {
                if (tokens.size > 2 && digitFirst(tokens[2])) ParserMediator.warn("一次元配列のサイズ指定に不必要なデータは無視されます", position, 0)
                if (id.isLocal && len < 1) { ParserMediator.warn("ローカル変数のサイズを1未満にはできません", position, 1); return }
                if (!id.isLocal && len < 100) { ParserMediator.warn("ローカル変数でない一次元配列のサイズを100未満にはできません", position, 1); return }
                if (len > 1000000) { ParserMediator.warn("一次元配列のサイズを1000000より大きくすることはできません", position, 1); return }
            } else if (id.isArray2D) {
                if (tokens.size < 3) { ParserMediator.warn("二次元配列のサイズ指定には2つの数値が必要です", position, 1); return }
                if (tokens.size > 3 && digitFirst(tokens[3])) ParserMediator.warn("二次元配列のサイズ指定に不必要なデータは無視されます", position, 0)
                length2 = tokens[2].trim().toIntOrNull() ?: run { ParserMediator.warn("三つ目の値を整数値として認識できません", position, 1); return }
                if (len < 1 || length2 < 1) { ParserMediator.warn("配列サイズを1未満にはできません", position, 1); return }
                if (len > 1000000 || length2 > 1000000) { ParserMediator.warn("配列サイズを1000000より大きくすることはできません", position, 1); return }
                if (len.toLong() * length2 > 1000000) { ParserMediator.warn("二次元配列の要素数は最大で100万個までです", position, 1); return }
            } else if (id.isArray3D) {
                if (tokens.size < 4) { ParserMediator.warn("三次元配列のサイズ指定には3つの数値が必要です", position, 1); return }
                if (tokens.size > 4 && digitFirst(tokens[4])) ParserMediator.warn("三次元配列のサイズ指定に不必要なデータは無視されます", position, 0)
                length2 = tokens[2].trim().toIntOrNull() ?: run { ParserMediator.warn("三つ目の値を整数値として認識できません", position, 1); return }
                length3 = tokens[3].trim().toIntOrNull() ?: run { ParserMediator.warn("四つ目の値を整数値として認識できません", position, 1); return }
                if (len < 1 || length2 < 1 || length3 < 1) { ParserMediator.warn("配列サイズを1未満にはできません", position, 1); return }
                if (len > 1000000 || length2 > 1000000 || length3 > 1000000) { ParserMediator.warn("配列サイズを1000000より大きくすることはできません", position, 1); return }
                if (len.toLong() * length2 * length3 > 10000000) { ParserMediator.warn("三次元配列の要素数は最大で1000万個までです", position, 1); return }
            }
        }
        val len = length!!
        when (id.code) {
            VariableCode.ITEMNAME, VariableCode.ITEMPRICE -> {
                VariableIntArrayLength[L and VariableCode.ITEMPRICE] = len
                MaxDataList[itemIndex] = len
            }
            VariableCode.STR -> {
                VariableStrArrayLength[L and VariableCode.STR] = len
                MaxDataList[strIndex] = len
            }
            VariableCode.ABLNAME, VariableCode.TALENTNAME, VariableCode.EXPNAME, VariableCode.MARKNAME, VariableCode.PALAMNAME,
            VariableCode.TRAINNAME, VariableCode.BASENAME, VariableCode.SOURCENAME, VariableCode.EXNAME, VariableCode.EQUIPNAME,
            VariableCode.TEQUIPNAME, VariableCode.FLAGNAME, VariableCode.TFLAGNAME, VariableCode.CFLAGNAME, VariableCode.TCVARNAME,
            VariableCode.CSTRNAME, VariableCode.STAINNAME, VariableCode.CDFLAGNAME1, VariableCode.CDFLAGNAME2, VariableCode.TSTRNAME,
            VariableCode.SAVESTRNAME, VariableCode.STRNAME, VariableCode.GLOBALNAME, VariableCode.GLOBALSNAME ->
                MaxDataList[id.code and L] = len
            else -> {
                if (id.isCharacterData) {
                    if (id.isArray2D) {
                        val l64 = (len.toLong() shl 32) + length2.toLong()
                        if (id.isInteger) CharacterIntArray2DLength[id.codeInt] = l64
                        else if (id.isString) CharacterStrArray2DLength[id.codeInt] = l64
                    } else {
                        if (id.isInteger) CharacterIntArrayLength[id.codeInt] = len
                        else if (id.isString) CharacterStrArrayLength[id.codeInt] = len
                    }
                } else if (id.isArray2D) {
                    val l64 = (len.toLong() shl 32) + length2.toLong()
                    if (id.isInteger) VariableIntArray2DLength[id.codeInt] = l64
                    else if (id.isString) VariableStrArray2DLength[id.codeInt] = l64
                } else if (id.isArray3D) {
                    val l3 = (len.toLong() shl 40) + (length2.toLong() shl 20) + length3.toLong()
                    if (id.isInteger) VariableIntArray3DLength[id.codeInt] = l3 else VariableStrArray3DLength[id.codeInt] = l3
                } else {
                    if (id.isInteger) VariableIntArrayLength[id.codeInt] = len
                    else if (id.isString) VariableStrArrayLength[id.codeInt] = len
                }
            }
        }
        if (!changedCode.add(id.code)) ParserMediator.warn(VariableCode.toString(id.code) + "の要素数は既に定義されています（上書きします）", position, 1)
    }

    private fun decideSub(mainCode: Int, nameCode: Int, arraylength: IntArray, position: ScriptPosition?) {
        val nameIndex = nameCode and L
        val mainLengthIndex = mainCode and L
        val hasName = changedCode.contains(nameCode)
        val hasMain = changedCode.contains(mainCode)
        if (hasName && hasMain) {
            if (MaxDataList[nameIndex] != arraylength[mainLengthIndex]) {
                val i = maxOf(MaxDataList[nameIndex], arraylength[mainLengthIndex])
                arraylength[mainLengthIndex] = i
                MaxDataList[nameIndex] = i
                if (MaxDataList[nameIndex] == 0 || arraylength[mainLengthIndex] == 0)
                    ParserMediator.warn(VariableCode.toString(mainCode) + "と" + VariableCode.toString(nameCode) + "の禁止設定が異なります（使用禁止を解除します）", position, 1)
                else
                    ParserMediator.warn(VariableCode.toString(mainCode) + "と" + VariableCode.toString(nameCode) + "の要素数が異なります（大きい方に合わせます）", position, 1)
            }
        } else if (hasName && !hasMain) arraylength[mainLengthIndex] = MaxDataList[nameIndex]
        else if (!hasName && hasMain) MaxDataList[nameIndex] = arraylength[mainLengthIndex]
    }

    private fun decideActualArraySize(position: ScriptPosition?) {
        val C = CharacterIntArrayLength
        decideSub(VariableCode.ABL, VariableCode.ABLNAME, C, position)
        decideSub(VariableCode.TALENT, VariableCode.TALENTNAME, C, position)
        decideSub(VariableCode.EXP, VariableCode.EXPNAME, C, position)
        decideSub(VariableCode.MARK, VariableCode.MARKNAME, C, position)
        decideSub(VariableCode.BASE, VariableCode.BASENAME, C, position)
        decideSub(VariableCode.SOURCE, VariableCode.SOURCENAME, C, position)
        decideSub(VariableCode.EX, VariableCode.EXNAME, C, position)
        decideSub(VariableCode.EQUIP, VariableCode.EQUIPNAME, C, position)
        decideSub(VariableCode.TEQUIP, VariableCode.TEQUIPNAME, C, position)
        decideSub(VariableCode.FLAG, VariableCode.FLAGNAME, VariableIntArrayLength, position)
        decideSub(VariableCode.TFLAG, VariableCode.TFLAGNAME, VariableIntArrayLength, position)
        decideSub(VariableCode.CFLAG, VariableCode.CFLAGNAME, C, position)
        decideSub(VariableCode.TCVAR, VariableCode.TCVARNAME, C, position)
        decideSub(VariableCode.CSTR, VariableCode.CSTRNAME, CharacterStrArrayLength, position)
        decideSub(VariableCode.STAIN, VariableCode.STAINNAME, C, position)
        decideSub(VariableCode.STR, VariableCode.STRNAME, VariableStrArrayLength, position)
        decideSub(VariableCode.TSTR, VariableCode.TSTRNAME, VariableStrArrayLength, position)
        decideSub(VariableCode.SAVESTR, VariableCode.SAVESTRNAME, VariableStrArrayLength, position)
        decideSub(VariableCode.GLOBAL, VariableCode.GLOBALNAME, VariableIntArrayLength, position)
        decideSub(VariableCode.GLOBALS, VariableCode.GLOBALSNAME, VariableStrArrayLength, position)

        val palamI = L and VariableCode.PALAM
        val juelI = L and VariableCode.JUEL
        if (changedCode.contains(VariableCode.PALAM) || changedCode.contains(VariableCode.JUEL)) {
            val palamJuelMax = maxOf(C[palamI], C[juelI])
            if (changedCode.contains(VariableCode.PALAMNAME)) {
                if (MaxDataList[paramIndex] != palamJuelMax) {
                    val i = maxOf(MaxDataList[paramIndex], palamJuelMax)
                    MaxDataList[paramIndex] = i
                    if (C[palamI] == palamJuelMax) C[palamI] = i
                    if (C[juelI] == palamJuelMax) C[juelI] = i
                    ParserMediator.warn("PALAMとJUELとPALAMNAMEの要素数が不適切です", position, 1)
                }
            } else MaxDataList[paramIndex] = palamJuelMax
        } else if (changedCode.contains(VariableCode.PALAMNAME)) {
            C[palamI] = MaxDataList[paramIndex]
            if (MaxDataList[paramIndex] < C[juelI]) {
                ParserMediator.warn("PALAMNAMEの要素数がJUELより少なくなっています（JUELに合わせます）", position, 1)
                MaxDataList[paramIndex] = C[juelI]
            }
        }
        val cdflagNameLengthChanged = changedCode.contains(VariableCode.CDFLAGNAME1) || changedCode.contains(VariableCode.CDFLAGNAME2)
        val mainLengthIndex = L and VariableCode.CDFLAG
        val length64 = CharacterIntArray2DLength[mainLengthIndex]
        var length1 = (length64 shr 32).toInt()
        var length2 = (length64 and 0x7FFFFFFF).toInt()
        if (changedCode.contains(VariableCode.CDFLAG) && cdflagNameLengthChanged) {
            if (length1 != MaxDataList[cdflag1Index] || length2 != MaxDataList[cdflag2Index])
                throw CodeEE("CDFLAGの要素数とCDFLAGNAME1及びCDFLAGNAME2の要素数が一致していません", position)
        } else if (cdflagNameLengthChanged && !changedCode.contains(VariableCode.CDFLAG)) {
            length1 = MaxDataList[cdflag1Index]
            length2 = MaxDataList[cdflag2Index]
            if (length1.toLong() * length2 > 1000000)
                throw CodeEE("CDFLAGの要素数が多すぎます（CDFLAGNAME1とCDFLAGNAME2の要素数の積が100万を超えています）", position)
            CharacterIntArray2DLength[mainLengthIndex] = (length1.toLong() shl 32) + length2.toLong()
        } else if (!cdflagNameLengthChanged && changedCode.contains(VariableCode.CDFLAG)) {
            MaxDataList[cdflag1Index] = length1
            MaxDataList[cdflag2Index] = length2
        }
        changedCode.clear()
    }

    fun loadData(csvDir: String, console: EmueraConsole?, disp: Boolean) {
        output = console
        loadVariableSizeData(csvDir + "VariableSize.CSV", disp)
        for (i in 0 until countNameCsv) {
            names[i] = arrayOfNulls(MaxDataList[i])
            nameToIntDics[i] = HashMap()
        }
        ItemPrice = LongArray(MaxDataList[itemIndex])
        loadDataTo(csvDir + "ABL.CSV", ablIndex, null, disp)
        loadDataTo(csvDir + "EXP.CSV", expIndex, null, disp)
        loadDataTo(csvDir + "TALENT.CSV", talentIndex, null, disp)
        loadDataTo(csvDir + "PALAM.CSV", paramIndex, null, disp)
        loadDataTo(csvDir + "TRAIN.CSV", trainIndex, null, disp)
        loadDataTo(csvDir + "MARK.CSV", markIndex, null, disp)
        loadDataTo(csvDir + "ITEM.CSV", itemIndex, ItemPrice, disp)
        loadDataTo(csvDir + "BASE.CSV", baseIndex, null, disp)
        loadDataTo(csvDir + "SOURCE.CSV", sourceIndex, null, disp)
        loadDataTo(csvDir + "EX.CSV", exIndex, null, disp)
        loadDataTo(csvDir + "STR.CSV", strIndex, null, disp)
        loadDataTo(csvDir + "EQUIP.CSV", equipIndex, null, disp)
        loadDataTo(csvDir + "TEQUIP.CSV", tequipIndex, null, disp)
        loadDataTo(csvDir + "FLAG.CSV", flagIndex, null, disp)
        loadDataTo(csvDir + "TFLAG.CSV", tflagIndex, null, disp)
        loadDataTo(csvDir + "CFLAG.CSV", cflagIndex, null, disp)
        loadDataTo(csvDir + "TCVAR.CSV", tcvarIndex, null, disp)
        loadDataTo(csvDir + "CSTR.CSV", cstrIndex, null, disp)
        loadDataTo(csvDir + "STAIN.CSV", stainIndex, null, disp)
        loadDataTo(csvDir + "CDFLAG1.CSV", cdflag1Index, null, disp)
        loadDataTo(csvDir + "CDFLAG2.CSV", cdflag2Index, null, disp)
        loadDataTo(csvDir + "STRNAME.CSV", strnameIndex, null, disp)
        loadDataTo(csvDir + "TSTR.CSV", tstrnameIndex, null, disp)
        loadDataTo(csvDir + "SAVESTR.CSV", savestrnameIndex, null, disp)
        loadDataTo(csvDir + "GLOBAL.CSV", globalIndex, null, disp)
        loadDataTo(csvDir + "GLOBALS.CSV", globalsIndex, null, disp)
        for (i in names.indices) {
            if (i == 10) continue
            val nameArray = names[i]!!
            val dic = nameToIntDics[i]!!
            for (j in nameArray.indices) {
                val n = nameArray[j]
                if (!n.isNullOrEmpty() && !dic.containsKey(n)) dic[n] = j
            }
        }
        loadCharacterData(csvDir, disp)
        for (tmpl in characterTmplList) {
            for (n in listOf(tmpl.Name, tmpl.Callname, tmpl.Nickname, tmpl.Mastername))
                if (!n.isNullOrEmpty() && !relationDic.containsKey(n)) relationDic[n] = tmpl.No.toInt()
        }
    }

    fun isDefined(varCode: Int, str: String?): Boolean {
        if (str.isNullOrEmpty()) return false
        if (varCode == VariableCode.CDFLAG) {
            var dic = getKeywordDictionary(null, VariableCode.CDFLAGNAME1, -1)
            if (dic == null || !dic.containsKey(str)) dic = getKeywordDictionary(null, VariableCode.CDFLAGNAME2, -1)
            return dic?.containsKey(str) ?: false
        }
        return getKeywordDictionary(null, varCode, -1)?.containsKey(str) ?: false
    }

    fun tryKeywordToInteger(code: Int, key: String?, index: Int): Int? {
        if (key.isNullOrEmpty()) return null
        val dic = try { getKeywordDictionary(null, code, index) ?: return null } catch (e: Exception) { return null }
        return dic[key]
    }

    fun keywordToInteger(code: Int, key: String?, index: Int): Int {
        if (key.isNullOrEmpty()) throw CodeEE("キーワードを空には出来ません")
        val errPos = arrayOfNulls<String>(1)
        val dic = getKeywordDictionary(errPos, code, index)
        dic?.get(key)?.let { return it }
        if (errPos[0] == null) throw CodeEE("配列変数" + VariableCode.toString(code) + "の要素を文字列で指定することはできません")
        throw CodeEE(errPos[0] + "の中に\"" + key + "\"の定義がありません")
    }

    fun getKeywordDictionary(errPosOut: Array<String?>?, code: Int, index: Int): Map<String, Int>? {
        var errPos: String? = null
        var allowIndex = -1
        var ret: Map<String, Int>? = null
        fun set(i: Int, pos: String, allow: Int) { ret = nameToIntDics[i]; errPos = pos; allowIndex = allow }
        when (code) {
            VariableCode.ABL -> set(ablIndex, "abl.csv", 1)
            VariableCode.EXP -> set(expIndex, "exp.csv", 1)
            VariableCode.TALENT -> set(talentIndex, "talent.csv", 1)
            VariableCode.UP, VariableCode.DOWN -> set(paramIndex, "palam.csv", 0)
            VariableCode.PALAM, VariableCode.JUEL, VariableCode.GOTJUEL, VariableCode.CUP, VariableCode.CDOWN -> set(paramIndex, "palam.csv", 1)
            VariableCode.TRAINNAME -> set(trainIndex, "train.csv", 0)
            VariableCode.MARK -> set(markIndex, "mark.csv", 1)
            VariableCode.ITEM, VariableCode.ITEMSALES, VariableCode.ITEMPRICE -> set(itemIndex, "Item.csv", 0)
            VariableCode.LOSEBASE -> set(baseIndex, "base.csv", 0)
            VariableCode.BASE, VariableCode.MAXBASE, VariableCode.DOWNBASE -> set(baseIndex, "base.csv", 1)
            VariableCode.SOURCE -> set(sourceIndex, "source.csv", 1)
            VariableCode.EX, VariableCode.NOWEX -> set(exIndex, "ex.csv", 1)
            VariableCode.EQUIP -> set(equipIndex, "equip.csv", 1)
            VariableCode.TEQUIP -> set(tequipIndex, "tequip.csv", 1)
            VariableCode.FLAG -> set(flagIndex, "flag.csv", 0)
            VariableCode.TFLAG -> set(tflagIndex, "tflag.csv", 0)
            VariableCode.CFLAG -> set(cflagIndex, "cflag.csv", 1)
            VariableCode.TCVAR -> set(tcvarIndex, "tcvar.csv", 1)
            VariableCode.CSTR -> set(cstrIndex, "cstr.csv", 1)
            VariableCode.STAIN -> set(stainIndex, "stain.csv", 1)
            VariableCode.CDFLAGNAME1 -> set(cdflag1Index, "cdflag1.csv", 0)
            VariableCode.CDFLAGNAME2 -> set(cdflag2Index, "cdflag2.csv", 0)
            VariableCode.CDFLAG -> {
                when {
                    index == 1 -> { ret = nameToIntDics[cdflag1Index]; errPos = "cdflag1.csv" }
                    index == 2 -> { ret = nameToIntDics[cdflag2Index]; errPos = "cdflag2.csv" }
                    index >= 0 -> throw CodeEE("配列変数" + VariableCode.toString(code) + "の" + (index + 1) + "番目の要素を文字列で指定することはできません")
                    else -> throw CodeEE("CDFLAGの要素の取得にはCDFLAGNAME1又はCDFLAGNAME2を使用します")
                }
                errPosOut?.set(0, errPos)
                return ret
            }
            VariableCode.STR -> set(strnameIndex, "strname.csv", 0)
            VariableCode.TSTR -> set(tstrnameIndex, "tstr.csv", 0)
            VariableCode.SAVESTR -> set(savestrnameIndex, "savestr.csv", 0)
            VariableCode.GLOBAL -> set(globalIndex, "global.csv", 0)
            VariableCode.GLOBALS -> set(globalsIndex, "globals.csv", 0)
            VariableCode.RELATION -> { ret = relationDic; errPos = "chara*.csv"; allowIndex = 1 }
            VariableCode.NAME, VariableCode.CALLNAME, VariableCode.NICKNAME, VariableCode.MASTERNAME -> { ret = relationDic; errPos = "chara*.csv"; allowIndex = -1 }
        }
        errPosOut?.set(0, errPos)
        if (index < 0) return ret
        if (ret == null) throw CodeEE("配列変数" + VariableCode.toString(code) + "の要素を文字列で指定することはできません")
        if (index != allowIndex) {
            if (allowIndex < 0) throw CodeEE("配列変数" + VariableCode.toString(code) + "の要素を文字列で指定することはできません")
            throw CodeEE("配列変数" + VariableCode.toString(code) + "の" + (index + 1) + "番目の要素を文字列で指定することはできません")
        }
        return ret
    }

    fun getCharacterTemplate(index: Long): CharacterTemplate? = characterTmplList.firstOrNull { it.No == index }

    fun getCharacterTemplate_UseSp(index: Long, sp: Boolean): CharacterTemplate? =
        characterTmplList.firstOrNull { it.No == index && !(Config.CompatiSPChara && sp != it.IsSpchara) }

    fun getCharacterTemplateFromCsvNo(index: Long): CharacterTemplate? = characterTmplList.firstOrNull { it.csvNo == index }

    fun getPseudoChara(): CharacterTemplate = CharacterTemplate(0, this)

    private fun loadCharacterData(csvDir: String, disp: Boolean) {
        if (!FileUtil.isDirectory(csvDir)) return
        val csvPaths = Config.getFiles(FileUtil.resolve(csvDir).path + "/", "CHARA*.CSV")
        for ((k, v) in csvPaths) loadCharacterDataFile(v, k, disp)
        if (useCompatiName) for (t in characterTmplList) if (t.Callname.isNullOrEmpty()) t.Callname = t.Name
        for (t in characterTmplList) t.setSpFlag()
        val nList = HashMap<Long, CharacterTemplate>()
        val spList = HashMap<Long, CharacterTemplate>()
        for (t in characterTmplList) {
            val target = if (Config.CompatiSPChara && t.IsSpchara) spList else nList
            val ex = target[t.No]
            if (ex != null) {
                if (!Config.CompatiSPChara && t.IsSpchara != ex.IsSpchara)
                    ParserMediator.warn("番号${t.No}のキャラが複数回定義されています(SPキャラとして定義するには互換性オプション「SPキャラを使用する」をONにしてください)", null, 1)
                else ParserMediator.warn("番号${t.No}のキャラが複数回定義されています", null, 1)
            } else target[t.No] = t
        }
    }

    private fun loadCharacterDataFile(csvPath: String, csvName: String, disp: Boolean) {
        var tmpl: CharacterTemplate? = null
        val eReader = EraStreamReader(false)
        if (!eReader.open(csvPath, csvName)) { output?.printError(csvName + "のオープンに失敗しました"); return }
        var position: ScriptPosition? = null
        if (disp) output?.printSystemLine(eReader.fileName + "読み込み中・・・")
        try {
            while (true) {
                val st = eReader.readEnabledLine() ?: break
                position = ScriptPosition(eReader.fileName, eReader.lineNo)
                val tokens = st.substring().split(',')
                if (tokens.size < 2) { ParserMediator.warn("\",\"が必要です", position, 1); continue }
                if (tokens[0].isEmpty()) { ParserMediator.warn("\",\"で始まっています", position, 1); continue }
                val t0 = tokens[0].trim()
                if (t0.equals("NO", ignoreCase = Config.ICVariable) || t0 == "番号" || t0 == "번호") {
                    if (tmpl != null) { ParserMediator.warn("番号が二重に定義されました", position, 1); continue }
                    val index = tokens[1].trim().toLongOrNull()
                    if (index == null) { ParserMediator.warn(tokens[1] + "を整数値に変換できません", position, 1); continue }
                    tmpl = CharacterTemplate(index, this)
                    var no = (eReader.fileName ?: csvName).uppercase()
                    val ci = no.indexOf("CHARA")
                    no = if (ci >= 0) no.substring(ci + 5) else ""
                    val sb = StringBuilder()
                    for (c in no) { if (!Character.isDigit(c)) break; sb.append(c) }
                    tmpl.csvNo = if (sb.isNotEmpty()) sb.toString().toLongOrNull() ?: 0 else 0
                    characterTmplList.add(tmpl)
                    continue
                }
                if (tmpl == null) { ParserMediator.warn("番号が定義される前に他のデータが始まりました", position, 1); continue }
                toCharacterTemplate(position, tmpl, tokens)
            }
        } catch (e: Exception) {
            if (position != null) ParserMediator.warn("予期しないエラーが発生しました", position, 3)
            else output?.printError("予期しないエラーが発生しました")
        } finally {
            eReader.close()
        }
    }

    private fun tryToInt64(str: String?): Long? {
        if (str.isNullOrEmpty()) return null
        val st = StringStream(str)
        var sign = 1
        if (st.current == '+') st.shiftNext()
        else if (st.current == '-') { sign = -1; st.shiftNext() }
        if (st.current !in '0'..'9') return null
        return try { LexicalAnalyzer.readInt64(st, false) * sign } catch (e: Exception) { null }
    }

    private fun toCharacterTemplate(position: ScriptPosition, chara: CharacterTemplate, tokens: List<String>) {
        val length: Int
        var intArray: MutableMap<Int, Long>? = null
        var strArray: MutableMap<Int, String>? = null
        val namearray: Map<String, Int>?
        var errPos: String? = null
        val varname = tokens[0].trim().uppercase()
        when (varname) {
            "NAME", "名前", "이름" -> { chara.Name = tokens[1]; return }
            "CALLNAME", "呼び名", "호칭", "부르는이름", "부르는 이름" -> { chara.Callname = tokens[1]; return }
            "NICKNAME", "あだ名", "별명", "애칭" -> { chara.Nickname = tokens[1]; return }
            "MASTERNAME", "主人の呼び方", "주인의 호칭", "주인호칭", "주인을 부르는 법" -> { chara.Mastername = tokens[1]; return }
            "MARK", "刻印", "각인" -> { length = CharacterIntArrayLength[L and VariableCode.MARK]; intArray = chara.Mark; namearray = nameToIntDics[markIndex]; errPos = "mark.csv" }
            "EXP", "経験", "경험" -> { length = CharacterIntArrayLength[L and VariableCode.EXP]; intArray = chara.Exp; namearray = nameToIntDics[expIndex]; errPos = "exp.csv" }
            "ABL", "能力", "능력" -> { length = CharacterIntArrayLength[L and VariableCode.ABL]; intArray = chara.Abl; namearray = nameToIntDics[ablIndex]; errPos = "abl.csv" }
            "BASE", "基礎", "기초" -> { length = CharacterIntArrayLength[L and VariableCode.MAXBASE]; intArray = chara.Maxbase; namearray = nameToIntDics[baseIndex]; errPos = "base.csv" }
            "TALENT", "素質", "소질" -> { length = CharacterIntArrayLength[L and VariableCode.TALENT]; intArray = chara.Talent; namearray = nameToIntDics[talentIndex]; errPos = "talent.csv" }
            "RELATION", "相性", "상성" -> { length = CharacterIntArrayLength[L and VariableCode.RELATION]; intArray = chara.Relation; namearray = null }
            "CFLAG", "フラグ", "플래그" -> { length = CharacterIntArrayLength[L and VariableCode.CFLAG]; intArray = chara.CFlag; namearray = nameToIntDics[cflagIndex]; errPos = "cflag.csv" }
            "EQUIP", "装着物", "장착물" -> { length = CharacterIntArrayLength[L and VariableCode.EQUIP]; intArray = chara.Equip; namearray = nameToIntDics[equipIndex]; errPos = "equip.csv" }
            "JUEL", "珠", "구슬" -> { length = CharacterIntArrayLength[L and VariableCode.JUEL]; intArray = chara.Juel; namearray = nameToIntDics[paramIndex]; errPos = "palam.csv" }
            "CSTR" -> { length = CharacterStrArrayLength[L and VariableCode.CSTR]; strArray = chara.CStr; namearray = nameToIntDics[cstrIndex]; errPos = "cstr.csv" }
            "ISASSI", "助手", "조수" -> return
            else -> { ParserMediator.warn("\"" + tokens[0] + "\"は解釈できない識別子です", position, 1); return }
        }
        if (length < 0) { ParserMediator.warn("プログラムミス", position, 3); return }
        if (length == 0) { ParserMediator.warn(varname + "は禁止設定された変数です", position, 2); return }
        val p1 = tryToInt64(tokens[1].trimEnd())
        val p1isNumeric = p1 != null
        if (p1 != null && (p1 < 0 || p1 >= length)) { ParserMediator.warn("${p1}は配列の範囲外です", position, 1); return }
        var index = p1?.toInt() ?: -1
        if (!p1isNumeric && namearray != null) {
            val found = namearray[tokens[1]]
            if (found == null) { ParserMediator.warn(errPos + "に\"" + tokens[1] + "\"の定義がありません", position, 1); return }
            if (found >= length) { ParserMediator.warn("\"" + tokens[1] + "\"は配列の範囲外です", position, 1); return }
            index = found
        }
        if (index < 0 || index >= length) {
            when {
                p1isNumeric -> ParserMediator.warn("${index}は配列の範囲外です", position, 1)
                tokens[1].isEmpty() -> ParserMediator.warn("二つ目の識別子がありません", position, 1)
                else -> ParserMediator.warn("\"" + tokens[1] + "\"は解釈できない識別子です", position, 1)
            }
            return
        }
        if (strArray != null) {
            if (tokens.size < 3) ParserMediator.warn("三つ目の識別子がありません", position, 1)
            if (strArray.containsKey(index)) ParserMediator.warn("${varname}の${index}番目の要素は既に定義されています(上書きします)", position, 1)
            strArray[index] = tokens.getOrNull(2) ?: ""
        } else {
            val p2 = if (tokens.size < 3) 1L else tryToInt64(tokens[2]) ?: 1L
            if (intArray!!.containsKey(index)) ParserMediator.warn("${varname}の${index}番目の要素は既に定義されています(上書きします)", position, 1)
            intArray[index] = p2
        }
    }

    private fun loadDataTo(csvPath: String, targetIndex: Int, targetI: LongArray?, disp: Boolean) {
        if (!FileUtil.exists(csvPath)) return
        val target = names[targetIndex]!!
        val defined = HashSet<Int>()
        val eReader = EraStreamReader(false)
        if (!eReader.open(FileUtil.resolve(csvPath).path)) { output?.printError(eReader.fileName + "のオープンに失敗しました"); return }
        var position: ScriptPosition? = null
        if (disp || Program.AnalysisMode) output?.printSystemLine(eReader.fileName + "読み込み中・・・")
        try {
            while (true) {
                val st = eReader.readEnabledLine() ?: break
                position = ScriptPosition(eReader.fileName, eReader.lineNo)
                val tokens = st.substring().split(',')
                if (tokens.size < 2) { ParserMediator.warn("\",\"が必要です", position, 1); continue }
                val index = tokens[0].trim().toIntOrNull()
                if (index == null) { ParserMediator.warn("一つ目の値を整数値に変換できません", position, 1); continue }
                if (target.isEmpty()) { ParserMediator.warn("禁止設定された名前配列です", position, 2); break }
                if (index < 0 || target.size <= index) { ParserMediator.warn("${index}は配列の範囲外です", position, 1); continue }
                if (!defined.add(index)) ParserMediator.warn("${index}番目の要素はすでに定義されています（新しい値で上書きします）", position, 1)
                target[index] = tokens[1]
                if (targetI != null && tokens.size >= 3) {
                    val price = tokens[2].trim().toLongOrNull()
                    if (price == null) { ParserMediator.warn("金額が読み取れません", position, 1); continue }
                    targetI[index] = price
                }
            }
        } catch (e: Exception) {
            if (position != null) ParserMediator.warn("予期しないエラーが発生しました", position, 3)
            else output?.printError("予期しないエラーが発生しました")
        } finally {
            eReader.close()
        }
    }
}

@Suppress("PropertyName")
class CharacterTemplate(index: Long, constant: ConstantData) {
    private val arraySize: IntArray = constant.CharacterIntArrayLength
    private val cstrSize: Int = constant.CharacterStrArrayLength[VariableCode.__LOWERCASE__ and VariableCode.CSTR]

    var Name: String? = null
    var Callname: String? = null
    var Nickname: String? = null
    var Mastername: String? = null
    val No: Long = index
    val Maxbase = LinkedHashMap<Int, Long>()
    val Mark = LinkedHashMap<Int, Long>()
    val Exp = LinkedHashMap<Int, Long>()
    val Abl = LinkedHashMap<Int, Long>()
    val Talent = LinkedHashMap<Int, Long>()
    val Relation = LinkedHashMap<Int, Long>()
    val CFlag = LinkedHashMap<Int, Long>()
    val Equip = LinkedHashMap<Int, Long>()
    val Juel = LinkedHashMap<Int, Long>()
    val CStr = LinkedHashMap<Int, String>()
    var csvNo = 0L
    var IsSpchara = false
        private set

    fun arrayStrLength(type: CharacterStrData): Int = when (type) {
        CharacterStrData.CSTR -> cstrSize
        else -> throw CodeEE("存在しないキーを参照しました")
    }

    fun arrayLength(type: CharacterIntData): Int {
        val L = VariableCode.__LOWERCASE__
        return when (type) {
            CharacterIntData.BASE -> maxOf(arraySize[L and VariableCode.BASE], arraySize[L and VariableCode.MAXBASE])
            CharacterIntData.MARK -> arraySize[L and VariableCode.MARK]
            CharacterIntData.ABL -> arraySize[L and VariableCode.ABL]
            CharacterIntData.EXP -> arraySize[L and VariableCode.EXP]
            CharacterIntData.RELATION -> arraySize[L and VariableCode.RELATION]
            CharacterIntData.TALENT -> arraySize[L and VariableCode.TALENT]
            CharacterIntData.CFLAG -> arraySize[L and VariableCode.CFLAG]
            CharacterIntData.EQUIP -> arraySize[L and VariableCode.EQUIP]
            CharacterIntData.JUEL -> arraySize[L and VariableCode.JUEL]
        }
    }

    fun setSpFlag() {
        val v = CFlag[0]
        if (v != null && v != 0L) IsSpchara = true
    }
}
