package com.eraandroid.emuera.gamedata.variable

import com.eraandroid.emuera.GlobalStatic
import com.eraandroid.emuera.config.Config
import com.eraandroid.emuera.gamedata.CharacterTemplate
import com.eraandroid.emuera.gamedata.ConstantData
import com.eraandroid.emuera.sub.*

class CharacterData(constant: ConstantData, varData: VariableData) {
    val dataInteger = LongArray(VariableCode.__COUNT_CHARACTER_INTEGER__)
    val dataString = arrayOfNulls<String>(VariableCode.__COUNT_CHARACTER_STRING__)
    val dataIntegerArray: Array<LongArray> = Array(VariableCode.__COUNT_CHARACTER_INTEGER_ARRAY__) { LongArray(constant.CharacterIntArrayLength[it]) }
    val dataStringArray: Array<Array<String?>> = Array(VariableCode.__COUNT_CHARACTER_STRING_ARRAY__) { arrayOfNulls<String>(constant.CharacterStrArrayLength[it]) }
    val dataIntegerArray2D: Array<IntArr2> = Array(VariableCode.__COUNT_CHARACTER_INTEGER_ARRAY_2D__) {
        val l64 = constant.CharacterIntArray2DLength[it]
        EraArrays.newInt2((l64 shr 32).toInt(), (l64 and 0x7FFFFFFF).toInt())
    }
    val dataStringArray2D: Array<StrArr2> = Array(VariableCode.__COUNT_CHARACTER_STRING_ARRAY_2D__) {
        val l64 = constant.CharacterStrArray2DLength[it]
        EraArrays.newStr2((l64 shr 32).toInt(), (l64 and 0x7FFFFFFF).toInt())
    }
    val userDefCVarDataList: MutableList<Any> = ArrayList()

    init {
        for (v in varData.userDefinedCharaVarList) {
            val d = v.dimData
            val l = d.Lengths!!
            val array: Any = if (d.TypeIsStr) when (d.Dimension) {
                1 -> EraArrays.newStr1(l[0]); 2 -> EraArrays.newStr2(l[0], l[1]); 3 -> EraArrays.newStr3(l[0], l[1], l[2])
                else -> throw ExeEE("")
            } else when (d.Dimension) {
                1 -> LongArray(l[0]); 2 -> EraArrays.newInt2(l[0], l[1]); 3 -> EraArrays.newInt3(l[0], l[1], l[2])
                else -> throw ExeEE("")
            }
            userDefCVarDataList.add(array)
        }
    }

    constructor(constant: ConstantData, tmpl: CharacterTemplate, varData: VariableData) : this(constant, varData) {
        val L = VariableCode.__LOWERCASE__
        dataInteger[L and VariableCode.NO] = tmpl.No
        dataString[L and VariableCode.NAME] = tmpl.Name
        dataString[L and VariableCode.CALLNAME] = tmpl.Callname
        dataString[L and VariableCode.NICKNAME] = tmpl.Nickname
        dataString[L and VariableCode.MASTERNAME] = tmpl.Mastername
        val maxbase = dataIntegerArray[L and VariableCode.MAXBASE]
        val base = dataIntegerArray[L and VariableCode.BASE]
        for ((k, v) in tmpl.Maxbase) { maxbase[k] = v; base[k] = v }
        fun copy(code: Int, m: Map<Int, Long>) { val a = dataIntegerArray[L and code]; for ((k, v) in m) a[k] = v }
        copy(VariableCode.MARK, tmpl.Mark)
        copy(VariableCode.EXP, tmpl.Exp)
        copy(VariableCode.ABL, tmpl.Abl)
        copy(VariableCode.TALENT, tmpl.Talent)
        val rel = dataIntegerArray[L and VariableCode.RELATION]
        rel.fill(Config.RelationDef)
        for ((k, v) in tmpl.Relation) rel[k] = v
        copy(VariableCode.CFLAG, tmpl.CFlag)
        copy(VariableCode.EQUIP, tmpl.Equip)
        copy(VariableCode.JUEL, tmpl.Juel)
        val cstr = dataStringArray[L and VariableCode.CSTR]
        for ((k, v) in tmpl.CStr) cstr[k] = v
    }

    fun copyTo(other: CharacterData, varData: VariableData) {
        dataInteger.copyInto(other.dataInteger)
        dataString.copyInto(other.dataString)
        for (i in dataIntegerArray.indices) dataIntegerArray[i].copyInto(other.dataIntegerArray[i])
        for (i in dataStringArray.indices) dataStringArray[i].copyInto(other.dataStringArray[i])
        for (i in dataIntegerArray2D.indices) EraArrays.copyInto(dataIntegerArray2D[i], other.dataIntegerArray2D[i])
        for (i in dataStringArray2D.indices) EraArrays.copyInto(dataStringArray2D[i], other.dataStringArray2D[i])
        if (userDefCVarDataList.isNotEmpty()) {
            for (v in varData.userDefinedCharaVarList) {
                if (!v.isCharacterData) continue
                if (v.isArray1D || v.isArray2D) EraArrays.copyInto(userDefCVarDataList[v.arrayIndex], other.userDefCVarDataList[v.arrayIndex])
            }
        }
    }

    fun saveToStream(writer: EraDataWriter) {
        for (i in 0 until VariableCode.__COUNT_SAVE_CHARACTER_STRING__) writer.write(dataString[i])
        for (i in 0 until VariableCode.__COUNT_SAVE_CHARACTER_INTEGER__) writer.write(dataInteger[i])
        for (i in 0 until VariableCode.__COUNT_SAVE_CHARACTER_INTEGER_ARRAY__) writer.write(dataIntegerArray[i])
        for (i in 0 until VariableCode.__COUNT_SAVE_CHARACTER_STRING_ARRAY__) writer.write(dataStringArray[i])
    }

    fun loadFromStream(reader: EraDataReader) {
        for (i in 0 until VariableCode.__COUNT_SAVE_CHARACTER_STRING__) dataString[i] = reader.readString()
        for (i in 0 until VariableCode.__COUNT_SAVE_CHARACTER_INTEGER__) dataInteger[i] = reader.readInt64()
        for (i in 0 until VariableCode.__COUNT_SAVE_CHARACTER_INTEGER_ARRAY__) reader.readInt64Array(dataIntegerArray[i])
        for (i in 0 until VariableCode.__COUNT_SAVE_CHARACTER_STRING_ARRAY__) reader.readStringArray(dataStringArray[i])
    }

    private fun low(code: Int) = VariableCode.__LOWERCASE__ and code
    private fun key(code: Int) = VariableCode.toString(code)

    fun saveToStreamExtended(writer: EraDataWriter) {
        val C = VariableCode.__CHARACTER_DATA__
        for (code in VariableIdentifier.getExtSaveList(C or VariableCode.__STRING__)) writer.writeExtended(key(code), dataString[low(code)])
        writer.emuSeparete()
        for (code in VariableIdentifier.getExtSaveList(C or VariableCode.__INTEGER__)) writer.writeExtended(key(code), dataInteger[low(code)])
        writer.emuSeparete()
        for (code in VariableIdentifier.getExtSaveList(C or VariableCode.__ARRAY_1D__ or VariableCode.__STRING__)) writer.writeExtended(key(code), dataStringArray[low(code)])
        writer.emuSeparete()
        for (code in VariableIdentifier.getExtSaveList(C or VariableCode.__ARRAY_1D__ or VariableCode.__INTEGER__)) writer.writeExtended(key(code), dataIntegerArray[low(code)])
        writer.emuSeparete()
        writer.emuSeparete() // string 2D: none
        for (code in VariableIdentifier.getExtSaveList(C or VariableCode.__ARRAY_2D__ or VariableCode.__INTEGER__)) writer.writeExtended2D(key(code), dataIntegerArray2D[low(code)])
        writer.emuSeparete()
    }

    fun loadFromStreamExtended(reader: EraDataReader) {
        val strDic = reader.readStringExtended()
        val intDic = reader.readInt64Extended()
        val strListDic = reader.readStringArrayExtended()
        val intListDic = reader.readInt64ArrayExtended()
        reader.readStringArray2DExtended()
        val int2DListDic = reader.readInt64Array2DExtended()
        val C = VariableCode.__CHARACTER_DATA__
        for (code in VariableIdentifier.getExtSaveList(C or VariableCode.__STRING__)) strDic[key(code)]?.let { dataString[low(code)] = it }
        for (code in VariableIdentifier.getExtSaveList(C or VariableCode.__INTEGER__)) intDic[key(code)]?.let { dataInteger[low(code)] = it }
        for (code in VariableIdentifier.getExtSaveList(C or VariableCode.__ARRAY_1D__ or VariableCode.__STRING__))
            strListDic[key(code)]?.let { copyList(it, dataStringArray[low(code)]) }
        for (code in VariableIdentifier.getExtSaveList(C or VariableCode.__ARRAY_1D__ or VariableCode.__INTEGER__))
            intListDic[key(code)]?.let { copyList(it, dataIntegerArray[low(code)]) }
        for (code in VariableIdentifier.getExtSaveList(C or VariableCode.__ARRAY_2D__ or VariableCode.__INTEGER__))
            int2DListDic[key(code)]?.let { copyList2D(it, dataIntegerArray2D[low(code)]) }
    }

    fun loadFromStreamExtended_Old1802(reader: EraDataReader) {
        val strDic = reader.readStringExtended()
        val intDic = reader.readInt64Extended()
        val strListDic = reader.readStringArrayExtended()
        val intListDic = reader.readInt64ArrayExtended()
        val C = VariableCode.__CHARACTER_DATA__
        for (code in VariableIdentifier.getExtSaveList(C or VariableCode.__STRING__)) strDic[key(code)]?.let { dataString[low(code)] = it }
        for (code in VariableIdentifier.getExtSaveList(C or VariableCode.__INTEGER__)) intDic[key(code)]?.let { dataInteger[low(code)] = it }
        for (code in VariableIdentifier.getExtSaveList(C or VariableCode.__ARRAY_1D__ or VariableCode.__STRING__))
            strListDic[key(code)]?.let { copyList(it, dataStringArray[low(code)]) }
        for (code in VariableIdentifier.getExtSaveList(C or VariableCode.__ARRAY_1D__ or VariableCode.__INTEGER__))
            intListDic[key(code)]?.let { copyList(it, dataIntegerArray[low(code)]) }
    }

    fun saveToStreamBinary(writer: EraBinaryDataWriter, varData: VariableData) {
        for ((_, v) in varData.getVarTokenDic()) {
            if (!v.isSavedata || !v.isCharacterData || v.isGlobal) continue
            val code = v.code
            val flag = code and (VariableCode.__ARRAY_1D__ or VariableCode.__ARRAY_2D__ or VariableCode.__ARRAY_3D__ or VariableCode.__STRING__ or VariableCode.__INTEGER__)
            val ci = v.codeInt
            when (flag) {
                VariableCode.__INTEGER__ -> writer.writeWithKey(key(code), dataInteger[ci])
                VariableCode.__STRING__ -> writer.writeWithKey(key(code), dataString[ci] ?: "")
                VariableCode.__INTEGER__ or VariableCode.__ARRAY_1D__ -> writer.writeWithKey(key(code), dataIntegerArray[ci])
                VariableCode.__STRING__ or VariableCode.__ARRAY_1D__ -> writer.writeWithKey(key(code), dataStringArray[ci])
                VariableCode.__INTEGER__ or VariableCode.__ARRAY_2D__ -> writer.writeWithKey(key(code), dataIntegerArray2D[ci])
                VariableCode.__STRING__ or VariableCode.__ARRAY_2D__ -> writer.writeWithKey(key(code), dataStringArray2D[ci])
            }
        }
        if (userDefCVarDataList.isNotEmpty()) {
            writer.writeSeparator()
            for (v in varData.userDefinedCharaVarList) {
                if (!v.isSavedata || !v.isCharacterData || v.isGlobal) continue
                writer.writeWithKey(v.name, userDefCVarDataList[v.arrayIndex])
            }
        }
        writer.writeEOC()
    }

    @Suppress("UNCHECKED_CAST")
    fun loadFromStreamBinary(reader: EraBinaryDataReader) {
        var codeInt = 0
        var userDefineData = false
        while (true) {
            val (keyName, type) = reader.readVariableCode()
            var vToken: VariableToken? = null
            var array: Any? = null
            if (keyName != null) {
                val dic = GlobalStatic.IdentifierDictionary!!
                if (!dic.getVarTokenIsForbid(keyName)) vToken = dic.getVariableToken(keyName, null, false)
                if (userDefineData) {
                    val t = vToken
                    array = if (t == null || !t.isSavedata || !t.isCharacterData || t !is UserDefinedCharaVariableToken) null
                    else userDefCVarDataList[t.arrayIndex]
                    vToken = null
                } else {
                    if (vToken != null) codeInt = VariableCode.__LOWERCASE__ and vToken.code
                    array = null
                }
            }
            when (type) {
                EraSaveDataType.Separator -> { userDefineData = true; continue }
                EraSaveDataType.EOF, EraSaveDataType.EOC -> return
                EraSaveDataType.Int ->
                    if (vToken == null || !vToken.isInteger || vToken.dimension != 0) reader.readInt() else dataInteger[codeInt] = reader.readInt()
                EraSaveDataType.Str ->
                    if (vToken == null || !vToken.isString || vToken.dimension != 0) reader.readString() else dataString[codeInt] = reader.readString()
                EraSaveDataType.IntArray -> when {
                    userDefineData && array != null -> reader.readIntArray(array as? LongArray, true)
                    vToken == null || !vToken.isInteger || vToken.dimension != 1 -> reader.readIntArray(null, true)
                    else -> reader.readIntArray(dataIntegerArray[codeInt], true)
                }
                EraSaveDataType.StrArray -> when {
                    userDefineData && array != null -> reader.readStrArray(array as? Array<String?>, true)
                    vToken == null || !vToken.isString || vToken.dimension != 1 -> reader.readStrArray(null, true)
                    else -> reader.readStrArray(dataStringArray[codeInt], true)
                }
                EraSaveDataType.IntArray2D -> when {
                    userDefineData && array != null -> reader.readIntArray2D(array as? IntArr2, true)
                    vToken == null || !vToken.isInteger || vToken.dimension != 2 -> reader.readIntArray2D(null, true)
                    else -> reader.readIntArray2D(dataIntegerArray2D[codeInt], true)
                }
                EraSaveDataType.StrArray2D -> when {
                    userDefineData && array != null -> reader.readStrArray2D(array as? StrArr2, true)
                    vToken == null || !vToken.isString || vToken.dimension != 2 -> reader.readStrArray2D(null, true)
                    else -> reader.readStrArray2D(dataStringArray2D[codeInt], true)
                }
                else -> throw FileEE("データ異常")
            }
        }
    }

    private fun <T> copyList(src: List<T>, dest: Array<T>) { for (i in 0 until minOf(src.size, dest.size)) dest[i] = src[i] }
    private fun copyList(src: List<Long>, dest: LongArray) { for (i in 0 until minOf(src.size, dest.size)) dest[i] = src[i] }
    private fun copyList2D(src: List<LongArray>, dest: IntArr2) {
        val countX = minOf(src.size, dest.size)
        for (x in 0 until countX) { val s = src[x]; for (y in 0 until minOf(s.size, dest[x].size)) dest[x][y] = s[y] }
    }

    val cFlag: LongArray get() = dataIntegerArray[VariableCode.__LOWERCASE__ and VariableCode.CFLAG]
    val NO: Long get() = dataInteger[VariableCode.__LOWERCASE__ and VariableCode.NO]

    // sort
    var tempSortKey: Comparable<Any>? = null
    var tempCurrentOrder = 0

    @Suppress("UNCHECKED_CAST")
    fun setSortKey(sortkey: VariableToken, elem64: Long) {
        val key: Any = if (sortkey.isString) {
            if (sortkey.isArray2D) {
                val array = if (sortkey is UserDefinedCharaVariableToken) userDefCVarDataList[sortkey.arrayIndex] as StrArr2 else dataStringArray2D[sortkey.codeInt]
                val e1 = (elem64 shr 32).toInt(); val e2 = (elem64 and 0x7FFFFFFF).toInt()
                if (e1 < 0 || e1 >= array.size || e2 < 0 || e2 >= EraArrays.length(array, 1)) throw CodeEE("ソートキーが配列外を参照しています")
                array[e1][e2] ?: ""
            } else if (sortkey.isArray1D) {
                val array = if (sortkey is UserDefinedCharaVariableToken) userDefCVarDataList[sortkey.arrayIndex] as StrArr1 else dataStringArray[sortkey.codeInt]
                if (elem64 < 0 || elem64 >= array.size) throw CodeEE("ソートキーが配列外を参照しています")
                array[elem64.toInt()] ?: ""
            } else dataString[sortkey.codeInt] ?: ""
        } else {
            if (sortkey.isArray2D) {
                val array = if (sortkey is UserDefinedCharaVariableToken) userDefCVarDataList[sortkey.arrayIndex] as IntArr2 else dataIntegerArray2D[sortkey.codeInt]
                val e1 = (elem64 shr 32).toInt(); val e2 = (elem64 and 0x7FFFFFFF).toInt()
                if (e1 < 0 || e1 >= array.size || e2 < 0 || e2 >= EraArrays.length(array, 1)) throw CodeEE("ソートキーが配列外を参照しています")
                array[e1][e2]
            } else if (sortkey.isArray1D) {
                val array = if (sortkey is UserDefinedCharaVariableToken) userDefCVarDataList[sortkey.arrayIndex] as LongArray else dataIntegerArray[sortkey.codeInt]
                if (elem64 < 0 || elem64 >= array.size) throw CodeEE("ソートキーが配列外を参照しています")
                array[elem64.toInt()]
            } else dataInteger[sortkey.codeInt]
        }
        tempSortKey = key as Comparable<Any>
    }

    companion object {
        fun characterVarLength(code: Int, constant: ConstantData): IntArray? {
            val type = code and (VariableCode.__ARRAY_1D__ or VariableCode.__ARRAY_2D__ or VariableCode.__ARRAY_3D__ or VariableCode.__INTEGER__ or VariableCode.__STRING__)
            val i = code and VariableCode.__LOWERCASE__
            if (i >= 0xF0) return null
            return when (type) {
                VariableCode.__STRING__, VariableCode.__INTEGER__ -> IntArray(0)
                VariableCode.__INTEGER__ or VariableCode.__ARRAY_1D__ -> intArrayOf(constant.CharacterIntArrayLength[i])
                VariableCode.__STRING__ or VariableCode.__ARRAY_1D__ -> intArrayOf(constant.CharacterStrArrayLength[i])
                VariableCode.__INTEGER__ or VariableCode.__ARRAY_2D__ -> {
                    val l = constant.CharacterIntArray2DLength[i]; intArrayOf((l shr 32).toInt(), (l and 0x7FFFFFFF).toInt())
                }
                VariableCode.__STRING__ or VariableCode.__ARRAY_2D__ -> {
                    val l = constant.CharacterStrArray2DLength[i]; intArrayOf((l shr 32).toInt(), (l and 0x7FFFFFFF).toInt())
                }
                VariableCode.__INTEGER__ or VariableCode.__ARRAY_3D__, VariableCode.__STRING__ or VariableCode.__ARRAY_3D__ -> throw NotImplCodeEE()
                else -> null
            }
        }

        val ascComparison = Comparator<CharacterData> { x, y ->
            val r = x.tempSortKey!!.compareTo(y.tempSortKey!!)
            if (r != 0) r else x.tempCurrentOrder.compareTo(y.tempCurrentOrder)
        }
        val descComparison = Comparator<CharacterData> { x, y ->
            val r = x.tempSortKey!!.compareTo(y.tempSortKey!!)
            if (r != 0) -r else x.tempCurrentOrder.compareTo(y.tempCurrentOrder)
        }
    }
}
