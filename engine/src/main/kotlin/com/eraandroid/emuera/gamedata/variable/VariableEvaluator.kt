package com.eraandroid.emuera.gamedata.variable

import com.eraandroid.emuera.GlobalStatic
import com.eraandroid.emuera.Program
import com.eraandroid.emuera.config.Config
import com.eraandroid.emuera.gamedata.CharacterIntData
import com.eraandroid.emuera.gamedata.CharacterStrData
import com.eraandroid.emuera.gamedata.ConstantData
import com.eraandroid.emuera.gamedata.GameBase
import com.eraandroid.emuera.gamedata.expression.EType
import com.eraandroid.emuera.gamedata.expression.SingleTerm
import com.eraandroid.emuera.gameproc.function.FunctionCode
import com.eraandroid.emuera.gameproc.function.SortOrder
import com.eraandroid.emuera.gameview.EmueraConsole
import com.eraandroid.emuera.sub.*
import java.io.File

class VariableEvaluator(private val gamebase: GameBase, val constant: ConstantData) {
    val variableData: VariableData = VariableData(gamebase, constant)
    private val varData get() = variableData
    private var rand = MTRandom()
    private val L = VariableCode.__LOWERCASE__

    init { GlobalStatic.VariableData = variableData }

    private val exm get() = GlobalStatic.EMediator!!

    fun randomize(seed: Long) { rand = MTRandom(seed) }
    fun initRanddata() = rand.setRand(RANDDATA)
    fun dumpRanddata() = rand.getRand(RANDDATA)
    fun getNextRand(max: Long): Long = rand.nextInt64(max)

    fun getPalamLv(pl: Long, maxlv: Long): Long {
        val arr = varData.dataIntegerArray[VariableCode.PALAMLV and L]
        for (i in 0 until maxlv.toInt()) if (pl < arr[i + 1]) return i.toLong()
        return maxlv
    }

    fun getExpLv(pl: Long, maxlv: Long): Long {
        val arr = varData.dataIntegerArray[VariableCode.EXPLV and L]
        for (i in 0 until maxlv.toInt()) if (pl < arr[i + 1]) return i.toLong()
        return maxlv
    }

    fun setValueAll(p: FixedVariableTerm, srcValue: Long, start: Int, end: Int) {
        if (p.identifier.isCalc) return
        if (p.identifier.isArray1D) {
            if (start != 0 || end != p.identifier.getLength()) p.isArrayRangeValid(start.toLong(), end.toLong(), "VARSET", 3L, 4L)
            else if (p.identifier.isCharacterData) p.identifier.checkElement(longArrayOf(p.index1, p.index2))
        } else if (p.identifier.isCharacterData) p.identifier.checkElement(longArrayOf(p.index1, p.index2, p.index3))
        p.identifier.setValueAll(srcValue, start, end, p.index1.toInt())
    }

    fun setValueAll(p: FixedVariableTerm, srcValue: String?, start: Int, end: Int) {
        if (p.identifier.isCalc) {
            if (p.identifier.code == VariableCode.WINDOW_TITLE) GlobalStatic.Console!!.setWindowTitle(srcValue ?: "")
            return
        }
        if (p.identifier.isArray1D) {
            if (start != 0 || end != p.identifier.getLength()) p.isArrayRangeValid(start.toLong(), end.toLong(), "VARSET", 3L, 4L)
            else if (p.identifier.isCharacterData) p.identifier.checkElement(longArrayOf(p.index1, p.index2))
        } else if (p.identifier.isCharacterData) p.identifier.checkElement(longArrayOf(p.index1, p.index2, p.index3))
        p.identifier.setValueAll(srcValue, start, end, p.index1.toInt())
    }

    fun setValueAllEachChara(p: FixedVariableTerm, index: SingleTerm, srcValue: Long, start: Int, end: Int) {
        if (!p.identifier.isInteger) throw CodeEE("整数型でない変数${p.identifier.name}に整数値を代入しようとしました")
        if (p.identifier.isConst) throw CodeEE("読み取り専用の変数${p.identifier.name}に代入しようとしました")
        if (p.identifier.isCalc) return
        if (varData.characterList.isEmpty()) return
        var indexNum = -1L
        if (p.identifier.isArray1D) {
            indexNum = if (index.getOperandType() == EType.Int64) index.int else constant.keywordToInteger(p.identifier.code, index.str, 1).toLong()
            if (indexNum < 0 || indexNum >= (p.identifier.getArrayChara(0) as LongArray).size)
                throw ArrayRangeCodeEE("キャラクタ配列変数${p.identifier.name}の第２引数(${indexNum})は配列の範囲外です")
        }
        for (i in start until end) p.identifier.setValue(srcValue, longArrayOf(i.toLong(), indexNum))
    }

    @Suppress("UNCHECKED_CAST")
    fun setValueAllEachChara(p: FixedVariableTerm, index: SingleTerm, srcValue: String?, start: Int, end: Int) {
        if (!p.identifier.isString) throw CodeEE("文字列型でない変数${p.identifier.name}に文字列型を代入しようとしました")
        if (p.identifier.isConst) throw CodeEE("読み取り専用の変数${p.identifier.name}に代入しようとしました")
        if (p.identifier.isCalc) {
            if (p.identifier.code == VariableCode.WINDOW_TITLE) GlobalStatic.Console!!.setWindowTitle(srcValue ?: "")
            return
        }
        if (varData.characterList.isEmpty()) return
        var indexNum = -1L
        if (p.identifier.isArray1D) {
            indexNum = if (index.getOperandType() == EType.Int64) index.int else constant.keywordToInteger(p.identifier.code, index.str, 1).toLong()
            if (indexNum < 0 || indexNum >= (p.identifier.getArrayChara(0) as Array<String?>).size)
                throw ArrayRangeCodeEE("キャラクタ配列変数${p.identifier.name}の第２引数(${indexNum})は配列の範囲外です")
        }
        for (i in start until end) p.identifier.setValue(srcValue, longArrayOf(i.toLong(), indexNum))
    }

    fun getArraySum(p: FixedVariableTerm, index1: Long, index2: Long): Long {
        var sum = 0L
        val id = p.identifier
        for (i in index1.toInt() until index2.toInt()) {
            val args = if (id.isCharacterData) {
                if (id.isArray1D) longArrayOf(p.index1, i.toLong()) else longArrayOf(p.index1, p.index2, i.toLong())
            } else when {
                id.isArray1D -> longArrayOf(i.toLong())
                id.isArray2D -> longArrayOf(p.index1, i.toLong())
                else -> longArrayOf(p.index1, p.index2, i.toLong())
            }
            sum += id.getIntValue(exm, args)
        }
        return sum
    }

    fun getArraySumChara(p: FixedVariableTerm, index1: Long, index2: Long): Long {
        var sum = 0L
        for (i in index1.toInt() until index2.toInt()) sum += p.identifier.getIntValue(exm, longArrayOf(i.toLong(), p.index2))
        return sum
    }

    @Suppress("UNCHECKED_CAST")
    fun getJoinedStr(p: FixedVariableTerm, delimiter: String, index1: Long, length: Long): String {
        val sb = StringBuilder()
        val id = p.identifier
        val len = length.toInt()
        if (p.isString) {
            if (id.isArray1D) {
                val arr = id.getArray() as Array<String?>
                for (i in 0 until len) { sb.append(arr[index1.toInt() + i] ?: ""); if (i < len - 1) sb.append(delimiter) }
                return sb.toString()
            }
            for (i in 0 until len) {
                val args = if (id.isArray2D) longArrayOf(p.index1, index1 + i) else longArrayOf(p.index1, p.index2, index1 + i)
                sb.append(id.getStrValue(exm, args) ?: ""); if (i < len - 1) sb.append(delimiter)
            }
        } else {
            for (i in 0 until len) {
                val args = when {
                    id.isArray1D -> longArrayOf(index1 + i)
                    id.isArray2D -> longArrayOf(p.index1, index1 + i)
                    else -> longArrayOf(p.index1, p.index2, index1 + i)
                }
                sb.append(id.getIntValue(exm, args)); if (i < len - 1) sb.append(delimiter)
            }
        }
        return sb.toString()
    }

    private fun a1(p: FixedVariableTerm, i: Long) = if (p.identifier.isCharacterData) longArrayOf(p.index1, i) else longArrayOf(i)

    fun getMatch(p: FixedVariableTerm, target: Long, start: Long, end: Long): Long {
        var ret = 0L
        for (i in start.toInt() until end.toInt()) if (p.identifier.getIntValue(exm, a1(p, i.toLong())) == target) ret++
        return ret
    }

    fun getMatch(p: FixedVariableTerm, target: String?, start: Long, end: Long): Long {
        var ret = 0L
        val empty = target.isNullOrEmpty()
        for (i in start.toInt() until end.toInt()) {
            val v = p.identifier.getStrValue(exm, a1(p, i.toLong()))
            if (v == target || (empty && v.isNullOrEmpty())) ret++
        }
        return ret
    }

    fun getMatchChara(p: FixedVariableTerm, target: Long, start: Long, end: Long): Long {
        var ret = 0L
        for (i in start.toInt() until end.toInt()) if (p.identifier.getIntValue(exm, longArrayOf(i.toLong(), p.index2, p.index3)) == target) ret++
        return ret
    }

    fun getMatchChara(p: FixedVariableTerm, target: String?, start: Long, end: Long): Long {
        var ret = 0L
        val empty = target.isNullOrEmpty()
        for (i in start.toInt() until end.toInt()) {
            val v = p.identifier.getStrValue(exm, longArrayOf(i.toLong(), p.index2, p.index3))
            if (v == target || (empty && v.isNullOrEmpty())) ret++
        }
        return ret
    }

    fun findElement(p: FixedVariableTerm, target: Long, start: Long, end: Long, isExact: Boolean, isLast: Boolean): Long {
        if (start >= end) return -1
        val array = (if (p.identifier.isCharacterData) p.identifier.getArrayChara(p.index1.toInt()) else p.identifier.getArray()) as LongArray
        if (isLast) { for (i in end.toInt() - 1 downTo start.toInt()) if (target == array[i]) return i.toLong() }
        else { for (i in start.toInt() until end.toInt()) if (target == array[i]) return i.toLong() }
        return -1
    }

    @Suppress("UNCHECKED_CAST")
    fun findElement(p: FixedVariableTerm, target: Regex, start: Long, end: Long, isExact: Boolean, isLast: Boolean): Long {
        if (start >= end) return -1
        val array = (if (p.identifier.isCharacterData) p.identifier.getArrayChara(p.index1.toInt()) else p.identifier.getArray()) as Array<String?>
        fun ok(s0: String?): Boolean {
            val s = s0 ?: ""
            if (isExact) {
                val m = target.find(s)
                return m != null && s.length == m.value.length
            }
            return target.containsMatchIn(s)
        }
        if (isLast) { for (i in end.toInt() - 1 downTo start.toInt()) if (ok(array[i])) return i.toLong() }
        else { for (i in start.toInt() until end.toInt()) if (ok(array[i])) return i.toLong() }
        return -1
    }

    fun getMaxArray(p: FixedVariableTerm, start: Long, end: Long, isMax: Boolean): Long {
        var ret = p.identifier.getIntValue(exm, a1(p, start))
        for (i in start.toInt() + 1 until end.toInt()) {
            val v = p.identifier.getIntValue(exm, a1(p, i.toLong()))
            if (isMax) { if (v > ret) ret = v } else { if (v < ret) ret = v }
        }
        return ret
    }

    fun getMaxArrayChara(p: FixedVariableTerm, start: Long, end: Long, isMax: Boolean): Long {
        var ret = p.identifier.getIntValue(exm, longArrayOf(start, p.index2, p.index3))
        for (i in start.toInt() + 1 until end.toInt()) {
            val v = p.identifier.getIntValue(exm, longArrayOf(i.toLong(), p.index2, p.index3))
            if (isMax) { if (v > ret) ret = v } else { if (v < ret) ret = v }
        }
        return ret
    }

    fun getInRangeArray(p: FixedVariableTerm, min: Long, max: Long, start: Long, end: Long): Long {
        var ret = 0L
        for (i in start.toInt() until end.toInt()) { val v = p.identifier.getIntValue(exm, a1(p, i.toLong())); if (v >= min && v < max) ret++ }
        return ret
    }

    fun getInRangeArrayChara(p: FixedVariableTerm, min: Long, max: Long, start: Long, end: Long): Long {
        var ret = 0L
        for (i in start.toInt() until end.toInt()) { val v = p.identifier.getIntValue(exm, longArrayOf(i.toLong(), p.index2, p.index3)); if (v >= min && v < max) ret++ }
        return ret
    }

    fun shiftArray(p: FixedVariableTerm, shift: Int, def: Long, start: Int, numIn: Int) {
        val array = (if (p.identifier.isCharacterData) p.identifier.getArrayChara(p.index1.toInt()) else p.identifier.getArray()) as LongArray
        var num = numIn
        if (start >= array.size) throw CodeEE("命令ARRAYSHIFTの第４引数(${start})が配列${p.identifier.name}の範囲を超えています")
        if (num == -1) num = array.size - start
        if (start + num > array.size) num = array.size - start
        if (Math.abs(shift) >= array.size && start == 0 && num >= array.size) { array.fill(def); return }
        var sourceStart = 0
        var destStart = start + shift
        val length = num - Math.abs(shift)
        if (shift < 0) { sourceStart = -shift; destStart = start }
        val temp = array.copyOfRange(start, start + num)
        if (sourceStart == 0) {
            if (length > 0) for (i in start until start + shift) array[i] = def
            else { for (i in start until start + num) array[i] = def; return }
        } else {
            if (length > 0) for (i in start + length until start + num) array[i] = def
            else { for (i in start until start + num) array[i] = def; return }
        }
        if (length > 0) temp.copyInto(array, destStart, sourceStart, sourceStart + length)
    }

    @Suppress("UNCHECKED_CAST")
    fun shiftArray(p: FixedVariableTerm, shift: Int, def: String?, start: Int, numIn: Int) {
        val arrays = (if (p.identifier.isCharacterData) p.identifier.getArrayChara(p.index1.toInt()) else p.identifier.getArray()) as Array<String?>
        var num = numIn
        if (start >= arrays.size) throw CodeEE("命令ARRAYSHIFTの第４引数(${start})が配列${p.identifier.name}の範囲を超えています")
        if (num == -1) num = arrays.size - start
        if (start + num > arrays.size) num = arrays.size - start
        if (Math.abs(shift) >= arrays.size && start == 0 && num >= arrays.size) { arrays.fill(def); return }
        var sourceStart = 0
        var destStart = start + shift
        val length = num - Math.abs(shift)
        if (shift < 0) { sourceStart = -shift; destStart = start }
        val temps = arrays.copyOfRange(start, start + num)
        if (destStart > start) {
            if (length > 0) for (i in start until start + shift) arrays[i] = def
            else { for (i in start until start + num) arrays[i] = def; return }
        } else {
            if (length > 0) for (i in start + length until start + num) arrays[i] = def
            else { for (i in start until start + num) arrays[i] = def; return }
        }
        if (length > 0) temps.copyInto(arrays, destStart, sourceStart, sourceStart + length)
    }

    @Suppress("UNCHECKED_CAST")
    fun removeArray(p: FixedVariableTerm, start: Int, numIn: Int) {
        var num = numIn
        if (p.identifier.isInteger) {
            val array = (if (p.identifier.isCharacterData) p.identifier.getArrayChara(p.index1.toInt()) else p.identifier.getArray()) as LongArray
            if (start >= array.size) throw CodeEE("命令ARRAYREMOVEの第２引数(${start})が配列${p.identifier.name}の範囲を超えています")
            if (num <= 0) num = array.size
            val temp = LongArray(array.size)
            if (start > 0) array.copyInto(temp, 0, 0, start)
            if (start + num < array.size) array.copyInto(temp, start, start + num, array.size)
            temp.copyInto(array)
        } else {
            val arrays = (if (p.identifier.isCharacterData) p.identifier.getArrayChara(p.index1.toInt()) else p.identifier.getArray()) as Array<String?>
            if (num <= 0) num = arrays.size
            val temps = arrayOfNulls<String>(arrays.size)
            if (start > 0) arrays.copyInto(temps, 0, 0, start)
            if (start + num < arrays.size) arrays.copyInto(temps, start, start + num, arrays.size)
            temps.copyInto(arrays)
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun sortArray(p: FixedVariableTerm, orderIn: SortOrder, start: Int, numIn: Int) {
        var order = orderIn
        var num = numIn
        if (order == SortOrder.UNDEF) order = SortOrder.ASCENDING
        if (p.identifier.isInteger) {
            val array = (if (p.identifier.isCharacterData) p.identifier.getArrayChara(p.index1.toInt()) else p.identifier.getArray()) as LongArray
            if (start >= array.size) throw CodeEE("命令ARRAYSORTの第３引数(${start})が配列${p.identifier.name}の範囲を超えています")
            if (start + num > array.size) throw CodeEE("命令ARRAYSORTの第３引数(${start})と第4変数(${num})の和が配列${p.identifier.name}の範囲を超えています")
            if (num <= 0) num = array.size - start
            val temp = array.copyOfRange(start, start + num)
            if (order == SortOrder.ASCENDING) temp.sort() else if (order == SortOrder.DESENDING) { temp.sort(); temp.reverse() }
            temp.copyInto(array, start)
        } else {
            val array = (if (p.identifier.isCharacterData) p.identifier.getArrayChara(p.index1.toInt()) else p.identifier.getArray()) as Array<String?>
            if (start >= array.size) throw CodeEE("命令ARRAYSORTの第３引数(${start})が配列${p.identifier.name}の範囲を超えています")
            if (start + num > array.size) throw CodeEE("命令ARRAYSORTの第３引数(${start})と第4変数(${num})の和が配列${p.identifier.name}の範囲を超えています")
            if (num <= 0) num = array.size - start
            val temp = array.copyOfRange(start, start + num)
            // .NET: null sorts first; culture compare approximated by ordinal
            val cmp = Comparator<String?> { a, b -> if (a == null) (if (b == null) 0 else -1) else if (b == null) 1 else a.compareTo(b) }
            if (order == SortOrder.ASCENDING) temp.sortWith(cmp) else if (order == SortOrder.DESENDING) temp.sortWith(cmp.reversed())
            temp.copyInto(array, start)
        }
    }

    fun copyArray(var1: VariableToken, var2: VariableToken) {
        val a1 = var1.getArray()
        val a2 = var2.getArray()
        EraArrays.copyInto(a1, a2)
    }

    fun getHavingItemsString(): String {
        val array = ITEM
        val itemnames = ITEMNAME
        val length = minOf(array.size, itemnames.size)
        var count = 0
        val b = StringBuilder(100)
        b.append("所持アイテム：")
        for (i in 0 until length) {
            if (array[i] == 0L) continue
            count++
            itemnames[i]?.let { b.append(it) }
            b.append("(").append(array[i]).append(") ")
        }
        if (count == 0) b.append("なし")
        return b.toString()
    }

    fun getCharacterDataString(target: Long, func: FunctionCode): String {
        val b = StringBuilder(100)
        if (target < 0 || target >= varData.characterList.size) throw CodeEE("存在しない登録キャラクタを参照しようとしました")
        val chara = varData.characterList[target.toInt()]
        fun loop(code: Int, nameCode: Int, f: (String, Long) -> Unit) {
            val array = chara.dataIntegerArray[L and code]
            val names = constant.getCsvNameList(nameCode)
            for (i in array.indices) {
                if (i >= names.size) break
                if (array[i] == 0L) continue
                val n = names[i]
                if (n.isNullOrEmpty()) continue
                f(n, array[i])
            }
        }
        when (func) {
            FunctionCode.PRINT_ABL -> loop(VariableCode.ABL, VariableCode.ABLNAME) { n, v -> b.append(n).append("LV").append(v).append(" ") }
            FunctionCode.PRINT_TALENT -> loop(VariableCode.TALENT, VariableCode.TALENTNAME) { n, _ -> b.append("[").append(n).append("]") }
            FunctionCode.PRINT_MARK -> loop(VariableCode.MARK, VariableCode.MARKNAME) { n, v -> b.append(n).append("LV").append(v).append(" ") }
            FunctionCode.PRINT_EXP -> loop(VariableCode.EXP, VariableCode.EXPNAME) { n, v -> b.append(n).append(v).append(" ") }
            else -> {}
        }
        return b.toString()
    }

    fun getCharacterParamString(target: Long, paramCode: Int): String? {
        if (target < 0 || target >= varData.characterList.size) throw CodeEE("存在しない登録キャラクタを参照しようとしました")
        val chara = varData.characterList[target.toInt()]
        val param = chara.dataIntegerArray[VariableCode.PALAM and L][paramCode]
        val paramlv = varData.dataIntegerArray[VariableCode.PALAMLV and L]
        var paramName = constant.getCsvNameList(VariableCode.PALAMNAME)[paramCode]
        if (param == 0L && paramName.isNullOrEmpty()) return null
        if (paramName == null) paramName = ""
        var c = '-'
        var border = paramlv[1]
        if (param >= border) { c = '='; border = paramlv[2] }
        if (param >= border) { c = '>'; border = paramlv[3] }
        if (param >= border) { c = '*'; border = paramlv[4] }
        val bar = StringBuilder(100)
        bar.append('[')
        if (border <= 0 || border <= param) repeat(10) { bar.append(c) }
        else if (param <= 0) repeat(10) { bar.append('.') }
        else {
            val count = (param * 10 / border).toInt()
            repeat(count) { bar.append(c) }
            repeat(10 - count) { bar.append('.') }
        }
        bar.append(']')
        return paramName + bar.toString() + String.format("%6d", param)
    }

    fun addCharacter(charaTmplNo: Long) {
        val tmpl = constant.getCharacterTemplate(charaTmplNo) ?: throw CodeEE("定義していないキャラクタを作成しようとしました")
        varData.characterList.add(CharacterData(constant, tmpl, varData))
    }

    fun addCharacter_UseSp(charaTmplNo: Long, isSp: Boolean) {
        val tmpl = constant.getCharacterTemplate_UseSp(charaTmplNo, isSp) ?: throw CodeEE("定義していないキャラクタを作成しようとしました")
        varData.characterList.add(CharacterData(constant, tmpl, varData))
    }

    fun addCharacterFromCsvNo(csvNo: Long) {
        val tmpl = constant.getCharacterTemplateFromCsvNo(csvNo) ?: constant.getPseudoChara()
        varData.characterList.add(CharacterData(constant, tmpl, varData))
    }

    fun addPseudoCharacter() {
        varData.characterList.add(CharacterData(constant, constant.getPseudoChara(), varData))
    }

    fun delCharacter(charaNo: Long) {
        if (charaNo < 0 || charaNo >= varData.characterList.size) throw CodeEE("存在しない登録キャラクタ(${charaNo})を削除しようとしました")
        varData.characterList.removeAt(charaNo.toInt())
    }

    fun delCharacter(charaNoList: LongArray) {
        val delList = ArrayList<CharacterData>()
        for (charaNo in charaNoList) {
            if (charaNo < 0 || charaNo >= varData.characterList.size) throw CodeEE("存在しない登録キャラクタ(${charaNo})を削除しようとしました")
            val chara = varData.characterList[charaNo.toInt()]
            if (delList.any { it === chara }) throw CodeEE("同一の登録キャラクタ番号(${charaNo})が複数回指定されました")
            delList.add(chara)
        }
        for (c in delList) varData.characterList.removeIf { it === c }
    }

    fun delAllCharacter() { varData.characterList.clear() }

    fun pickUpChara(noList: LongArray) {
        val pickList = ArrayList<Long>()
        val oldTarget = TARGET
        val oldAssi = ASSI
        val oldMaster = MASTER
        TARGET = -1; ASSI = -1; MASTER = -1
        for (n in noList) if (!pickList.contains(n) && n >= 0) pickList.add(n)
        for (i in pickList.indices) {
            if (i.toLong() != pickList[i]) {
                swapChara(pickList[i], i.toLong())
                val idx = pickList.indexOf(i.toLong())
                if (idx > i) pickList[idx] = pickList[i]
            }
            if (TARGET < 0 && pickList[i] == oldTarget) TARGET = i.toLong()
            if (ASSI < 0 && pickList[i] == oldAssi) ASSI = i.toLong()
            if (MASTER < 0 && pickList[i] == oldMaster) MASTER = i.toLong()
        }
        if (pickList.size < varData.characterList.size) {
            for (i in varData.characterList.size - 1 downTo pickList.size) delCharacter(i.toLong())
        }
    }

    fun resetData() {
        varData.setDefaultLocalValue()
        varData.setDefaultValue(constant)
        varData.characterList.clear()
    }

    fun resetGlobalData() = varData.setDefaultGlobalValue()

    fun copyChara(x: Long, y: Long) {
        if (x < 0 || x >= varData.characterList.size) throw CodeEE("コピー元のキャラクタが存在しません")
        if (y < 0 || y >= varData.characterList.size) throw CodeEE("コピー先のキャラクタが存在しません")
        varData.characterList[x.toInt()].copyTo(varData.characterList[y.toInt()], varData)
    }

    fun addCopyChara(x: Long) {
        if (x < 0 || x >= varData.characterList.size) throw CodeEE("コピー元のキャラクタが存在しません")
        addPseudoCharacter()
        varData.characterList[x.toInt()].copyTo(varData.characterList[varData.characterList.size - 1], varData)
    }

    fun swapChara(x: Long, y: Long) {
        if (x < 0 || x >= varData.characterList.size || y < 0 || y >= varData.characterList.size)
            throw CodeEE("存在しない登録キャラクタを入れ替えようとしました")
        if (x == y) return
        val data = varData.characterList[y.toInt()]
        varData.characterList[y.toInt()] = varData.characterList[x.toInt()]
        varData.characterList[x.toInt()] = data
    }

    fun sortChara(sortkeyIn: VariableToken?, elem: Long, sortorderIn: SortOrder, fixMaster: Boolean) {
        val list = varData.characterList
        if (list.size <= 1) return
        val sortorder = if (sortorderIn == SortOrder.UNDEF) SortOrder.ASCENDING else sortorderIn
        val sortkey = sortkeyIn ?: varData.getSystemVariableToken("NO")
        val masterChara = if (MASTER >= 0 && MASTER < list.size) list[MASTER.toInt()] else null
        val targetChara = if (TARGET >= 0 && TARGET < list.size) list[TARGET.toInt()] else null
        val assiChara = if (ASSI >= 0 && ASSI < list.size) list[ASSI.toInt()] else null
        for (i in list.indices) { list[i].tempCurrentOrder = i; list[i].setSortKey(sortkey, elem) }
        if (fixMaster && masterChara != null) {
            if (list.size <= 2) return
            list.remove(masterChara)
        }
        if (sortorder == SortOrder.ASCENDING) list.sortWith(CharacterData.ascComparison) else list.sortWith(CharacterData.descComparison)
        if (fixMaster && masterChara != null) list.add(MASTER.toInt(), masterChara)
        for (i in list.indices) list[i].tempCurrentOrder = i
        if (masterChara != null && !fixMaster) MASTER = masterChara.tempCurrentOrder.toLong()
        if (targetChara != null) TARGET = targetChara.tempCurrentOrder.toLong()
        if (assiChara != null) ASSI = assiChara.tempCurrentOrder.toLong()
    }

    fun findChara(varID: VariableToken, elem64: Long, word: String?, startIndex: Long, lastIndex: Long, isLast: Boolean): Long {
        if (startIndex >= lastIndex) return -1
        val fvp = FixedVariableTerm(varID)
        if (varID.isArray1D) fvp.index2 = elem64
        else if (varID.isArray2D) { fvp.index2 = elem64 shr 32; fvp.index3 = elem64 and 0x7FFFFFFF }
        if (isLast) {
            var i = lastIndex - 1
            while (i >= startIndex) { fvp.index1 = i; if (word == fvp.getStrValue(exm)) return i; i-- }
        } else {
            var i = startIndex
            while (i < lastIndex) { fvp.index1 = i; if (word == fvp.getStrValue(exm)) return i; i++ }
        }
        return -1
    }

    fun findChara(varID: VariableToken, elem64: Long, word: Long, startIndex: Long, lastIndex: Long, isLast: Boolean): Long {
        if (startIndex >= lastIndex) return -1
        val fvp = FixedVariableTerm(varID)
        if (varID.isArray1D) fvp.index2 = elem64
        else if (varID.isArray2D) { fvp.index2 = elem64 shr 32; fvp.index3 = elem64 and 0x7FFFFFFF }
        if (isLast) {
            var i = lastIndex - 1
            while (i >= startIndex) { fvp.index1 = i; if (word == fvp.getIntValue(exm)) return i; i-- }
        } else {
            var i = startIndex
            while (i < lastIndex) { fvp.index1 = i; if (word == fvp.getIntValue(exm)) return i; i++ }
        }
        return -1
    }

    fun getChara(charaNo: Long): Long {
        for (i in varData.characterList.indices) if (varData.characterList[i].NO == charaNo) return i.toLong()
        return -1
    }

    fun getChara_UseSp(charaNo: Long, getSp: Boolean): Long {
        for (i in varData.characterList.indices) {
            val c = varData.characterList[i]
            if (c.NO == charaNo && (c.cFlag[0] != 0L) == getSp) return i.toLong()
        }
        return -1
    }

    fun existCsv(charaNo: Long, getSp: Boolean): Long = if (constant.getCharacterTemplate_UseSp(charaNo, getSp) == null) 0 else 1

    fun getCharacterStrfromCSVData(charaTmplNo: Long, type: CharacterStrData, isSp: Boolean, arg2Long: Long): String {
        val tmpl = constant.getCharacterTemplate_UseSp(charaTmplNo, isSp) ?: throw CodeEE("定義していないキャラクタを参照しようとしました")
        val arg2 = arg2Long.toInt()
        return when (type) {
            CharacterStrData.CALLNAME -> tmpl.Callname ?: ""
            CharacterStrData.NAME -> tmpl.Name ?: ""
            CharacterStrData.NICKNAME -> tmpl.Nickname ?: ""
            CharacterStrData.MASTERNAME -> tmpl.Mastername ?: ""
            CharacterStrData.CSTR -> {
                if (arg2 >= tmpl.arrayStrLength(CharacterStrData.CSTR) || arg2 < 0) throw CodeEE("CSTRの参照可能範囲外を参照しました")
                tmpl.CStr[arg2] ?: ""
            }
        }
    }

    fun getCharacterIntfromCSVData(charaTmplNo: Long, type: CharacterIntData, isSp: Boolean, arg2Long: Long): Long {
        val tmpl = constant.getCharacterTemplate_UseSp(charaTmplNo, isSp) ?: throw CodeEE("定義していないキャラクタを参照しようとしました")
        if (arg2Long >= tmpl.arrayLength(type) || arg2Long < 0) throw CodeEE("参照可能範囲外を参照しました")
        val dic = when (type) {
            CharacterIntData.BASE -> tmpl.Maxbase
            CharacterIntData.MARK -> tmpl.Mark
            CharacterIntData.ABL -> tmpl.Abl
            CharacterIntData.EXP -> tmpl.Exp
            CharacterIntData.RELATION -> tmpl.Relation
            CharacterIntData.TALENT -> tmpl.Talent
            CharacterIntData.CFLAG -> tmpl.CFlag
            CharacterIntData.EQUIP -> tmpl.Equip
            CharacterIntData.JUEL -> tmpl.Juel
        }
        return dic[arg2Long.toInt()] ?: 0L
    }

    fun updateInBeginTrain() {
        ASSIPLAY = 0
        PREVCOM = -1
        NEXTCOM = -1
        varData.dataIntegerArray[VariableCode.TFLAG and L].fill(0)
        varData.dataStringArray[VariableCode.TSTR and L].fill("")
        for (chara in varData.characterList) {
            chara.dataIntegerArray[VariableCode.GOTJUEL and L].fill(0)
            chara.dataIntegerArray[VariableCode.TEQUIP and L].fill(0)
            chara.dataIntegerArray[VariableCode.EX and L].fill(0)
            setDefaultStain(chara)
            chara.dataIntegerArray[VariableCode.PALAM and L].fill(0)
            chara.dataIntegerArray[VariableCode.SOURCE and L].fill(0)
            chara.dataIntegerArray[VariableCode.TCVAR and L].fill(0)
        }
    }

    fun updateAfterShowUsercom() {
        varData.dataIntegerArray[VariableCode.UP and L].fill(0)
        varData.dataIntegerArray[VariableCode.DOWN and L].fill(0)
        varData.dataIntegerArray[VariableCode.LOSEBASE and L].fill(0)
        for (chara in varData.characterList) {
            chara.dataIntegerArray[VariableCode.DOWNBASE and L].fill(0)
            chara.dataIntegerArray[VariableCode.CUP and L].fill(0)
            chara.dataIntegerArray[VariableCode.CDOWN and L].fill(0)
        }
    }

    fun updateAfterInputCom() { for (chara in varData.characterList) chara.dataIntegerArray[VariableCode.NOWEX and L].fill(0) }

    fun updateAfterSourceCheck() { for (chara in varData.characterList) chara.dataIntegerArray[VariableCode.SOURCE and L].fill(0) }

    fun updateInUpcheck(window: EmueraConsole, skipPrint: Boolean) {
        val paramname = constant.getCsvNameList(VariableCode.PALAMNAME)
        val up = varData.dataIntegerArray[VariableCode.UP and L]
        val down = varData.dataIntegerArray[VariableCode.DOWN and L]
        val target = TARGET
        if (target >= 0 && target < varData.characterList.size) {
            val param = varData.characterList[target.toInt()].dataIntegerArray[VariableCode.PALAM and L]
            applyUp(window, skipPrint, paramname, up, down, param)
        }
        up.fill(0)
        down.fill(0)
    }

    fun cUpdateInUpcheck(window: EmueraConsole, target: Long, skipPrint: Boolean) {
        val paramname = constant.getCsvNameList(VariableCode.PALAMNAME)
        if (target < 0 || target >= varData.characterList.size) return
        val chara = varData.characterList[target.toInt()]
        val up = chara.dataIntegerArray[VariableCode.CUP and L]
        val down = chara.dataIntegerArray[VariableCode.CDOWN and L]
        val param = chara.dataIntegerArray[VariableCode.PALAM and L]
        applyUp(window, skipPrint, paramname, up, down, param)
        up.fill(0)
        down.fill(0)
    }

    private fun applyUp(window: EmueraConsole, skipPrint: Boolean, paramname: Array<String?>, up: LongArray, down: LongArray, param: LongArray) {
        var length = param.size
        if (param.size > up.size) length = up.size
        if (param.size > down.size) length = down.size
        for (i in 0 until length) {
            if (up[i] <= 0 && down[i] <= 0) continue
            val b = StringBuilder()
            if (!skipPrint) {
                b.append(paramname.getOrNull(i) ?: "").append(' ').append(param[i])
                if (up[i] > 0) b.append('+').append(up[i])
                if (down[i] > 0) b.append('-').append(down[i])
            }
            param[i] += up[i] - down[i]
            if (!skipPrint) {
                b.append('=').append(param[i])
                window.print(b.toString())
                window.newLine()
            }
        }
    }

    private fun setDefaultStain(chara: CharacterData) {
        val array = chara.dataIntegerArray[VariableCode.STAIN and L]
        val def = Config.StainDefault
        for (i in array.indices) array[i] = if (i < def.size) def[i] else 0
    }

    fun setDefaultStain(no: Long) {
        if (no < 0 || no >= varData.characterList.size) throw CodeEE("存在しないキャラクターを参照しようとしました")
        setDefaultStain(varData.characterList[no.toInt()])
    }

    fun varSize(varID: VariableToken) {
        val r = RESULT_ARRAY
        when {
            varID.isArray2D -> { r[0] = varID.getLength(0).toLong(); r[1] = varID.getLength(1).toLong() }
            varID.isArray3D -> { r[0] = varID.getLength(0).toLong(); r[1] = varID.getLength(1).toLong(); r[2] = varID.getLength(2).toLong() }
            else -> r[0] = varID.getLength().toLong()
        }
    }

    fun itemSales(itemNo: Long): Boolean {
        val sales = ITEMSALES
        val names = constant.getCsvNameList(VariableCode.ITEMNAME)
        if (itemNo < 0 || itemNo >= sales.size || itemNo >= names.size) return false
        val i = itemNo.toInt()
        return sales[i] != 0L && names[i] != null
    }

    fun buyItem(itemNo: Long): Boolean {
        if (!itemSales(itemNo)) return false
        val price = constant.ItemPrice
        if (itemNo >= price.size) return false
        val i = itemNo.toInt()
        if (MONEY < price[i]) return false
        MONEY -= price[i]
        ITEM[i]++
        BOUGHT = itemNo
        return true
    }

    fun setEncodingResult(ary: IntArray) {
        val res = varData.dataIntegerArray[VariableCode.RESULT and L]
        res[0] = ary.size.toLong()
        for (i in ary.indices) res[i + 1] = ary[i].toLong()
    }

    fun iamaMunchkin() {
        if (MASTER < 0 || MASTER >= varData.characterList.size) return
        val c = varData.characterList[MASTER.toInt()]
        c.dataString[VariableCode.NAME and L] = "イカサマ"
        c.dataString[VariableCode.CALLNAME and L] = "イカサマ"
        c.dataString[VariableCode.NICKNAME and L] = "イカサマ"
    }

    fun setResultX(values: List<Long>) {
        val r = varData.dataIntegerArray[VariableCode.RESULT and L]
        for (i in values.indices) { if (i >= r.size) return; r[i] = values[i] }
    }

    // ── File operations ──────────────────────────────────────────────────────

    private fun savePathG() = Config.SavDir + "global.sav"
    private fun savePath(index: Int) = Config.SavDir + String.format("save%02d.sav", index)
    private fun savePath(s: String) = Config.SavDir + "save$s.sav"
    private fun savePathV(index: Int) = Program.DatDir + String.format("var_%02d.dat", index)
    private fun savePathC(index: Int) = Program.DatDir + String.format("chara_%02d.dat", index)
    private fun savePathV(s: String) = Program.DatDir + "var_$s.dat"
    private fun savePathC(s: String) = Program.DatDir + "chara_$s.dat"
    private fun f(path: String): File = FileUtil.resolve(path)

    fun createDatFolder() {
        val d = f(Program.DatDir)
        if (d.isDirectory) return
        if (!d.mkdirs()) throw CodeEE("datフォルダーの作成に失敗しました")
    }

    fun getDatFiles(charadat: Boolean, pattern: String): List<String> {
        val files = ArrayList<String>()
        val dir = f(Program.DatDir)
        if (!dir.isDirectory) return files
        val prefix = if (charadat) "chara_" else "var_"
        val glob = Regex("^" + (prefix + pattern + ".dat").split("*").joinToString(".*") { part ->
            part.split("?").joinToString(".") { Regex.escape(it) }
        } + "$", RegexOption.IGNORE_CASE)
        for (fn in (dir.list() ?: emptyArray()).sortedWith { a, b -> a.compareTo(b, ignoreCase = true) }) {
            if (!glob.matches(fn) || !fn.endsWith(".dat", ignoreCase = true)) continue
            val name = fn.substring(0, fn.length - 4).substring(prefix.length)
            if (name.isEmpty()) continue
            files.add(name)
        }
        return files
    }

    fun checkDatFilename(datfilename: String?): String? {
        if (datfilename.isNullOrEmpty()) return "ファイル名が指定されていません"
        if (datfilename.any { it in "\\/:*?\"<>|" || it < ' ' }) return "ファイル名に不正な文字が含まれています"
        return null
    }

    fun checkData(savename: String, type: EraSaveFileType): EraDataResult = checkDataByFilename(
        when (type) {
            EraSaveFileType.Normal -> savePath(savename)
            EraSaveFileType.Global -> savePathG()
            EraSaveFileType.Var -> savePathV(savename)
            EraSaveFileType.CharVar -> savePathC(savename)
        }, type
    )

    fun checkData(saveIndex: Int, type: EraSaveFileType): EraDataResult = checkDataByFilename(
        when (type) {
            EraSaveFileType.Normal -> savePath(saveIndex)
            EraSaveFileType.Global -> savePathG()
            EraSaveFileType.Var -> savePathV(saveIndex)
            EraSaveFileType.CharVar -> savePathC(saveIndex)
        }, type
    )

    fun checkDataByFilename(filename: String, type: EraSaveFileType): EraDataResult {
        val result = EraDataResult()
        val file = f(filename)
        if (!file.exists()) {
            result.state = EraDataState.FILENOTFOUND
            result.dataMes = "----"
            return result
        }
        var bReader: EraBinaryDataReader? = null
        var reader: EraDataReader? = null
        try {
            bReader = EraBinaryDataReader.createReader(file)
            if (bReader == null) {
                reader = EraDataReader(file)
                if (!gamebase.uniqueCodeEqualTo(reader.readInt64())) {
                    result.state = EraDataState.GAME_ERROR; result.dataMes = "異なるゲームのセーブデータです"; return result
                }
                val version = reader.readInt64()
                if (!gamebase.checkVersion(version)) {
                    result.state = EraDataState.VIRSION_ERROR; result.dataMes = "セーブデータのバーションが異なります"; return result
                }
                result.state = EraDataState.OK
                result.dataMes = reader.readString()
                return result
            }
            val fileType = bReader.readFileType()
            if (type != fileType) { result.state = EraDataState.ETC_ERROR; result.dataMes = "セーブデータが壊れています"; return result }
            if (!gamebase.uniqueCodeEqualTo(bReader.readInt64())) { result.state = EraDataState.GAME_ERROR; result.dataMes = "異なるゲームのセーブデータです"; return result }
            val version = bReader.readInt64()
            if (!gamebase.checkVersion(version)) { result.state = EraDataState.VIRSION_ERROR; result.dataMes = "セーブデータのバーションが異なります"; return result }
            result.state = EraDataState.OK
            result.dataMes = bReader.readString()
            return result
        } catch (fee: FileEE) {
            result.state = EraDataState.ETC_ERROR
            result.dataMes = fee.message ?: ""
        } catch (e: Exception) {
            result.state = EraDataState.ETC_ERROR
            result.dataMes = "読み込み中にエラーが発生しました"
        } finally {
            reader?.close()
            bReader?.close()
        }
        return result
    }

    fun saveChara(savename: String, savMes: String, charas: IntArray) {
        createDatFolder()
        checkDatFilename(savename)
        EraBinaryDataWriter(f(savePathC(savename))).use { w ->
            w.writeHeader()
            w.writeFileType(EraSaveFileType.CharVar)
            w.writeInt64(gamebase.ScriptUniqueCode)
            w.writeInt64(gamebase.ScriptVersion)
            w.writeString(savMes)
            w.writeInt64(charas.size.toLong())
            for (c in charas) varData.characterList[c].saveToStreamBinary(w, varData)
            w.writeEOF()
        }
    }

    fun loadChara(savename: String) {
        RESULT = 0
        val file = f(savePathC(savename))
        if (!file.exists()) return
        val bReader = EraBinaryDataReader.createReader(file) ?: return
        bReader.use {
            if (it.readFileType() != EraSaveFileType.CharVar) return
            if (!gamebase.uniqueCodeEqualTo(it.readInt64())) return
            val version = it.readInt64()
            if (!gamebase.checkVersion(version)) return
            it.readString()
            val loadnum = it.readInt64()
            val add = ArrayList<CharacterData>()
            for (i in 0 until loadnum) {
                val chara = CharacterData(constant, varData)
                chara.loadFromStreamBinary(it)
                add.add(chara)
            }
            varData.characterList.addAll(add)
            RESULT = 1
        }
    }

    fun saveVariable(savename: String, savMes: String, vars: Array<VariableToken>) {
        createDatFolder()
        checkDatFilename(savename)
        EraBinaryDataWriter(f(savePathV(savename))).use { w ->
            w.writeHeader()
            w.writeFileType(EraSaveFileType.Var)
            w.writeInt64(gamebase.ScriptUniqueCode)
            w.writeInt64(gamebase.ScriptVersion)
            w.writeString(savMes)
            for (v in vars) w.writeWithKey(v.name, v.getArray())
            w.writeEOF()
        }
    }

    fun loadVariable(savename: String) {
        RESULT = 0
        val file = f(savePathV(savename))
        if (!file.exists()) return
        val bReader = EraBinaryDataReader.createReader(file) ?: return
        bReader.use {
            if (it.readFileType() != EraSaveFileType.Var) return
            if (!gamebase.uniqueCodeEqualTo(it.readInt64())) return
            val version = it.readInt64()
            if (!gamebase.checkVersion(version)) return
            it.readString()
            while (varData.loadVariableBinary(it)) {}
            RESULT = 1
        }
    }

    fun loadFromStream(reader: EraDataReader) {
        if (!gamebase.uniqueCodeEqualTo(reader.readInt64())) throw FileEE("異なるゲームのセーブデータです")
        val version = reader.readInt64()
        if (!gamebase.checkVersion(version)) throw FileEE("セーブデータのバーションが異なります")
        val text = reader.readString()
        varData.setDefaultValue(constant)
        varData.setDefaultLocalValue()
        varData.lastLoadVersion = version
        varData.lastLoadText = text
        val charaCount = reader.readInt64().toInt()
        varData.characterList.clear()
        for (i in 0 until charaCount) {
            val chara = CharacterData(constant, varData)
            varData.characterList.add(chara)
            chara.loadFromStream(reader)
        }
        varData.loadFromStream(reader)
        if (reader.seekEmuStart()) {
            if (reader.dataVersion < 1803) for (i in 0 until charaCount) varData.characterList[i].loadFromStreamExtended_Old1802(reader)
            else for (i in 0 until charaCount) varData.characterList[i].loadFromStreamExtended(reader)
            varData.loadFromStreamExtended(reader, reader.dataVersion)
        }
    }

    fun saveGlobal(): Boolean {
        try {
            Config.createSavDir()
            File(Config.SavDir).mkdirs()
            EraBinaryDataWriter(f(savePathG())).use { w ->
                w.writeHeader()
                w.writeFileType(EraSaveFileType.Global)
                w.writeInt64(gamebase.ScriptUniqueCode)
                w.writeInt64(gamebase.ScriptVersion)
                w.writeString("")
                varData.saveGlobalToStreamBinary(w)
                w.writeEOF()
            }
        } catch (e: java.io.IOException) {
            throw CodeEE("グローバルデータの保存中にエラーが発生しました")
        }
        return true
    }

    fun loadGlobal(): Boolean {
        val file = f(savePathG())
        if (!file.exists()) return false
        var reader: EraDataReader? = null
        var bReader: EraBinaryDataReader? = null
        try {
            bReader = EraBinaryDataReader.createReader(file)
            if (bReader != null) {
                if (bReader.readFileType() != EraSaveFileType.Global) return false
                if (!gamebase.uniqueCodeEqualTo(bReader.readInt64())) return false
                val version = bReader.readInt64()
                if (!gamebase.checkVersion(version)) return false
                bReader.readString()
                varData.loadFromStreamBinary(bReader)
            } else {
                reader = EraDataReader(file)
                if (!gamebase.uniqueCodeEqualTo(reader.readInt64())) return false
                val version = reader.readInt64()
                if (!gamebase.checkVersion(version)) return false
                varData.loadGlobalFromStream(reader)
                if (reader.seekEmuStart()) varData.loadGlobalFromStream1808(reader)
            }
            return true
        } catch (e: Exception) {
            return false
        } finally {
            reader?.close()
            bReader?.close()
        }
    }

    fun saveToStreamBinary(w: EraBinaryDataWriter, saveDataText: String) {
        w.writeHeader()
        w.writeFileType(EraSaveFileType.Normal)
        w.writeInt64(gamebase.ScriptUniqueCode)
        w.writeInt64(gamebase.ScriptVersion)
        w.writeString(saveDataText)
        w.writeInt64(varData.characterList.size.toLong())
        for (c in varData.characterList) c.saveToStreamBinary(w, varData)
        varData.saveToStreamBinary(w)
        w.writeEOF()
    }

    fun loadFromStreamBinary(bReader: EraBinaryDataReader) {
        if (bReader.readFileType() != EraSaveFileType.Normal) throw FileEE("セーブデータが壊れています")
        if (!gamebase.uniqueCodeEqualTo(bReader.readInt64())) throw FileEE("異なるゲームのセーブデータです")
        val version = bReader.readInt64()
        if (!gamebase.checkVersion(version)) throw FileEE("セーブデータのバーションが異なります")
        val text = bReader.readString()
        varData.setDefaultValue(constant)
        varData.setDefaultLocalValue()
        varData.lastLoadVersion = version
        varData.lastLoadText = text
        val charaCount = bReader.readInt64().toInt()
        varData.characterList.clear()
        for (i in 0 until charaCount) {
            val chara = CharacterData(constant, varData)
            varData.characterList.add(chara)
            chara.loadFromStreamBinary(bReader)
        }
        varData.loadFromStreamBinary(bReader)
    }

    fun saveTo(saveIndex: Int, saveText: String): Boolean {
        return try {
            Config.createSavDir()
            File(Config.SavDir).mkdirs()
            EraBinaryDataWriter(f(savePath(saveIndex))).use { w -> saveToStreamBinary(w, saveText) }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun loadFrom(dataIndex: Int): Boolean {
        val file = f(savePath(dataIndex))
        if (!file.exists()) throw ExeEE("存在しないパスを呼び出した")
        val bReader = EraBinaryDataReader.createReader(file)
        if (bReader != null) bReader.use { loadFromStreamBinary(it) }
        else EraDataReader(file).use { loadFromStream(it) }
        varData.lastLoadNo = dataIndex.toLong()
        return true
    }

    fun delData(dataIndex: Int) {
        val file = f(savePath(dataIndex))
        if (!file.exists()) return
        if (!file.canWrite()) throw CodeEE("指定されたファイル\"${file.path}\"は読み込み専用のため削除できません")
        file.delete()
    }

    fun dispose() = varData.dispose()

    // ── Properties ───────────────────────────────────────────────────────────
    val RESULT_ARRAY: LongArray get() = varData.dataIntegerArray[VariableCode.RESULT and L]
    var RESULT: Long
        get() = RESULT_ARRAY[0]
        set(v) { RESULT_ARRAY[0] = v }
    var COUNT: Long
        get() = varData.dataIntegerArray[VariableCode.COUNT and L][0]
        set(v) { varData.dataIntegerArray[VariableCode.COUNT and L][0] = v }
    var RESULTS: String
        get() = varData.dataStringArray[VariableCode.RESULTS and L][0] ?: ""
        set(v) { varData.dataStringArray[VariableCode.RESULTS and L][0] = v }
    val RESULTS_ARRAY: Array<String?> get() = varData.dataStringArray[VariableCode.RESULTS and L]
    var TARGET: Long
        get() = varData.dataIntegerArray[VariableCode.TARGET and L][0]
        set(v) { varData.dataIntegerArray[VariableCode.TARGET and L][0] = v }
    val SELECTCOM_ARRAY: LongArray get() = varData.dataIntegerArray[VariableCode.SELECTCOM and L]
    var SELECTCOM: Long
        get() = SELECTCOM_ARRAY[0]
        set(v) { SELECTCOM_ARRAY[0] = v }
    val ITEMNAME: Array<String?> get() = constant.getCsvNameList(VariableCode.ITEMNAME)
    val ITEMSALES: LongArray get() = varData.dataIntegerArray[VariableCode.ITEMSALES and L]
    val ITEMPRICE: LongArray get() = constant.ItemPrice
    private val ITEM: LongArray get() = varData.dataIntegerArray[VariableCode.ITEM and L]
    val RANDDATA: LongArray get() = varData.dataIntegerArray[VariableCode.RANDDATA and L]
    var SAVEDATA_TEXT: String?
        get() = varData.dataString[VariableCode.SAVEDATA_TEXT and L]
        set(v) { varData.dataString[VariableCode.SAVEDATA_TEXT and L] = v }
    val CHARANUM: Long get() = varData.characterList.size.toLong()

    private fun getCF(code: Int): Long { val a = varData.dataIntegerArray[code and L]; return if (a.isEmpty()) -1 else a[0] }
    private fun setCF(code: Int, v: Long) { val a = varData.dataIntegerArray[code and L]; if (a.isNotEmpty()) a[0] = v }

    var MASTER: Long get() = getCF(VariableCode.MASTER); set(v) = setCF(VariableCode.MASTER, v)
    var ASSI: Long get() = getCF(VariableCode.ASSI); set(v) = setCF(VariableCode.ASSI, v)
    var ASSIPLAY: Long get() = getCF(VariableCode.ASSIPLAY); set(v) = setCF(VariableCode.ASSIPLAY, v)
    var PREVCOM: Long get() = getCF(VariableCode.PREVCOM); set(v) = setCF(VariableCode.PREVCOM, v)
    var NEXTCOM: Long get() = getCF(VariableCode.NEXTCOM); set(v) = setCF(VariableCode.NEXTCOM, v)
    private var MONEY: Long get() = getCF(VariableCode.MONEY); set(v) = setCF(VariableCode.MONEY, v)
    private var BOUGHT: Long get() = getCF(VariableCode.BOUGHT); set(v) = setCF(VariableCode.BOUGHT, v)
}
