package com.eraandroid.emuera.gamedata.function

import com.eraandroid.emuera.GlobalStatic
import com.eraandroid.emuera.config.Config
import com.eraandroid.emuera.gamedata.CharacterIntData
import com.eraandroid.emuera.gamedata.CharacterStrData
import com.eraandroid.emuera.gamedata.ParserMediator
import com.eraandroid.emuera.gamedata.expression.*
import com.eraandroid.emuera.gamedata.variable.VariableCode
import com.eraandroid.emuera.gamedata.variable.VariableTerm
import com.eraandroid.emuera.gameview.FontStyle
import com.eraandroid.emuera.sub.*

/* Creator.Method.cs part 1: CSVデータ関係 / 汎用処理系 */

internal const val SP_CHARA_ERR = "SPキャラ関係の機能は標準では使用できません(互換性オプション「SPキャラを使用する」をONにしてください)"

internal class GetcharaMethod : FunctionMethod() {
    init { returnType = EType.Int64; argumentTypeArray = null; canRestructure = false }
    override fun checkArgumentType(name: String, arguments: Array<IOperandTerm?>): String? {
        if (arguments.isEmpty()) return name + "関数には少なくとも1つの引数が必要です"
        if (arguments.size > 2) return name + "関数の引数が多すぎます"
        val a0 = arguments[0] ?: return name + "関数の1番目の引数は省略できません"
        if (a0.getOperandType() != EType.Int64) return name + "関数の1番目の引数の型が正しくありません"
        if (arguments.size == 2 && arguments[1] != null && arguments[1]!!.getOperandType() != EType.Int64) return name + "関数の2番目の引数の型が正しくありません"
        return null
    }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long {
        val integer = arguments[0]!!.getIntValue(exm)
        if (!Config.CompatiSPChara) return exm.vEvaluator.getChara(integer)
        val checkSp = arguments.size > 1 && arguments[1] != null && arguments[1]!!.getIntValue(exm) != 0L
        if (checkSp) {
            val chara = exm.vEvaluator.getChara_UseSp(integer, false)
            return if (chara != -1L) chara else exm.vEvaluator.getChara_UseSp(integer, true)
        }
        return exm.vEvaluator.getChara_UseSp(integer, false)
    }
}

internal class GetspcharaMethod : FunctionMethod() {
    init { returnType = EType.Int64; argumentTypeArray = arrayOf(EType.Int64); canRestructure = false }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long {
        if (!Config.CompatiSPChara) throw CodeEE(SP_CHARA_ERR)
        return exm.vEvaluator.getChara_UseSp(arguments[0]!!.getIntValue(exm), true)
    }
}

internal class CsvStrDataMethod(private val charaStr: CharacterStrData = CharacterStrData.NAME) : FunctionMethod() {
    init { returnType = EType.String; argumentTypeArray = null; canRestructure = true }
    override fun checkArgumentType(name: String, arguments: Array<IOperandTerm?>): String? {
        if (arguments.isEmpty()) return name + "関数には少なくとも1つの引数が必要です"
        if (arguments.size > 2) return name + "関数の引数が多すぎます"
        val a0 = arguments[0] ?: return name + "関数の1番目の引数は省略できません"
        if (!a0.isInteger) return name + "関数の1番目の引数が数値ではありません"
        if (arguments.size == 1) return null
        if (arguments[1] != null && arguments[1]!!.getOperandType() != EType.Int64) return name + "関数の2番目の変数が数値ではありません"
        return null
    }
    override fun getStrValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): String {
        val x = arguments[0]!!.getIntValue(exm)
        val y = if (arguments.size > 1 && arguments[1] != null) arguments[1]!!.getIntValue(exm) else 0L
        if (!Config.CompatiSPChara && y != 0L) throw CodeEE(SP_CHARA_ERR)
        return exm.vEvaluator.getCharacterStrfromCSVData(x, charaStr, y != 0L, 0)
    }
}

private fun check2or3Int(name: String, arguments: Array<IOperandTerm?>): String? {
    if (arguments.size < 2) return name + "関数には少なくとも2つの引数が必要です"
    if (arguments.size > 3) return name + "関数の引数が多すぎます"
    val a0 = arguments[0] ?: return name + "関数の1番目の引数は省略できません"
    if (!a0.isInteger) return name + "関数の1番目の引数が数値ではありません"
    val a1 = arguments[1] ?: return name + "関数の2番目の引数は省略できません"
    if (a1.getOperandType() != EType.Int64) return name + "関数の2番目の変数が数値ではありません"
    if (arguments.size == 2) return null
    if (arguments[2] != null && arguments[2]!!.getOperandType() != EType.Int64) return name + "関数の3番目の変数が数値ではありません"
    return null
}

internal class CsvcstrMethod : FunctionMethod() {
    init { returnType = EType.String; argumentTypeArray = null; canRestructure = true }
    override fun checkArgumentType(name: String, arguments: Array<IOperandTerm?>): String? = check2or3Int(name, arguments)
    override fun getStrValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): String {
        val x = arguments[0]!!.getIntValue(exm)
        val y = arguments[1]!!.getIntValue(exm)
        val z = if (arguments.size == 3 && arguments[2] != null) arguments[2]!!.getIntValue(exm) else 0L
        if (!Config.CompatiSPChara && z != 0L) throw CodeEE(SP_CHARA_ERR)
        return exm.vEvaluator.getCharacterStrfromCSVData(x, CharacterStrData.CSTR, z != 0L, y)
    }
}

internal class CsvDataMethod(private val charaInt: CharacterIntData = CharacterIntData.BASE) : FunctionMethod() {
    init { returnType = EType.Int64; argumentTypeArray = null; canRestructure = true }
    override fun checkArgumentType(name: String, arguments: Array<IOperandTerm?>): String? = check2or3Int(name, arguments)
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long {
        val x = arguments[0]!!.getIntValue(exm)
        val y = arguments[1]!!.getIntValue(exm)
        val z = if (arguments.size == 3 && arguments[2] != null) arguments[2]!!.getIntValue(exm) else 0L
        if (!Config.CompatiSPChara && z != 0L) throw CodeEE(SP_CHARA_ERR)
        return exm.vEvaluator.getCharacterIntfromCSVData(x, charaInt, z != 0L, y)
    }
}

internal class FindcharaMethod(private val isLast: Boolean) : FunctionMethod() {
    init { returnType = EType.Int64; argumentTypeArray = null; canRestructure = false }
    override fun checkArgumentType(name: String, arguments: Array<IOperandTerm?>): String? {
        if (arguments.size < 2) return name + "関数には少なくとも2つの引数が必要です"
        if (arguments.size > 4) return name + "関数の引数が多すぎます"
        val a0 = arguments[0] ?: return name + "関数の1番目の引数は省略できません"
        if (a0 !is VariableTerm) return name + "関数の1番目の引数の型が正しくありません"
        if (!a0.identifier.isCharacterData) return name + "関数の1番目の引数の変数がキャラクタ変数ではありません"
        val a1 = arguments[1] ?: return name + "関数の2番目の引数は省略できません"
        if (a1.getOperandType() != a0.getOperandType()) return name + "関数の2番目の引数の型が正しくありません"
        if (arguments.size >= 3 && arguments[2] != null && arguments[2]!!.getOperandType() != EType.Int64) return name + "関数の3番目の引数の型が正しくありません"
        if (arguments.size >= 4 && arguments[3] != null && arguments[3]!!.getOperandType() != EType.Int64) return name + "関数の4番目の引数の型が正しくありません"
        return null
    }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long {
        val vTerm = arguments[0] as VariableTerm
        val varID = vTerm.identifier
        var elem = 0L
        if (varID.isArray1D) elem = vTerm.getElementInt(1, exm)
        else if (varID.isArray2D) {
            elem = vTerm.getElementInt(1, exm) shl 32
            elem += vTerm.getElementInt(2, exm)
        }
        var startindex = 0L
        var lastindex = exm.vEvaluator.CHARANUM
        if (arguments.size >= 3 && arguments[2] != null) startindex = arguments[2]!!.getIntValue(exm)
        if (arguments.size >= 4 && arguments[3] != null) lastindex = arguments[3]!!.getIntValue(exm)
        if (startindex < 0 || startindex >= exm.vEvaluator.CHARANUM) throw CodeEE("関数の第3引数($startindex)はキャラクタ位置の範囲外です")
        if (lastindex < 0 || lastindex > exm.vEvaluator.CHARANUM) throw CodeEE("関数の第4引数($lastindex)はキャラクタ位置の範囲外です")
        return if (varID.isString) exm.vEvaluator.findChara(varID, elem, arguments[1]!!.getStrValue(exm), startindex, lastindex, isLast)
        else exm.vEvaluator.findChara(varID, elem, arguments[1]!!.getIntValue(exm), startindex, lastindex, isLast)
    }
}

internal class ExistCsvMethod : FunctionMethod() {
    init { returnType = EType.Int64; argumentTypeArray = null; canRestructure = true }
    override fun checkArgumentType(name: String, arguments: Array<IOperandTerm?>): String? {
        if (arguments.isEmpty()) return name + "関数には少なくとも1つの引数が必要です"
        if (arguments.size > 2) return name + "関数の引数が多すぎます"
        val a0 = arguments[0] ?: return name + "関数の1番目の引数は省略できません"
        if (!a0.isInteger) return name + "関数の1番目の引数が数値ではありません"
        if (arguments.size == 1) return null
        if (arguments[1] != null && arguments[1]!!.getOperandType() != EType.Int64) return name + "関数の2番目の変数が数値ではありません"
        return null
    }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long {
        val no = arguments[0]!!.getIntValue(exm)
        val isSp = if (arguments.size == 2 && arguments[1] != null) arguments[1]!!.getIntValue(exm) != 0L else false
        if (!Config.CompatiSPChara && isSp) throw CodeEE(SP_CHARA_ERR)
        return exm.vEvaluator.existCsv(no, isSp)
    }
}

internal class VarsizeMethod : FunctionMethod() {
    init { returnType = EType.Int64; argumentTypeArray = null; canRestructure = true; hasUniqueRestructure = true }
    override fun checkArgumentType(name: String, arguments: Array<IOperandTerm?>): String? {
        if (arguments.isEmpty()) return name + "関数には少なくとも1つの引数が必要です"
        if (arguments.size > 2) return name + "関数の引数が多すぎます"
        val a0 = arguments[0] ?: return name + "関数の1番目の引数は省略できません"
        if (!a0.isString) return name + "関数の1番目の引数が文字列ではありません"
        if (a0 is SingleTerm) {
            if (GlobalStatic.IdentifierDictionary!!.getVariableToken(a0.str, null, true) == null) return name + "関数の1番目の引数が変数名ではありません"
        }
        if (arguments.size == 1) return null
        if (arguments[1] != null && arguments[1]!!.getOperandType() != EType.Int64) return name + "関数の2番目の変数が数値ではありません"
        return null
    }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long {
        val s = arguments[0]!!.getStrValue(exm)
        val v = GlobalStatic.IdentifierDictionary!!.getVariableToken(s, null, true) ?: throw CodeEE("VARSIZEの1番目の引数(\"$s\")が変数名ではありません")
        var dim = 0
        if (arguments.size == 2 && arguments[1] != null) dim = arguments[1]!!.getIntValue(exm).toInt()
        return v.getLength(dim).toLong()
    }
    override fun uniqueRestructure(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Boolean {
        arguments[0]!!.restructure(exm)
        if (arguments.size > 1) arguments[1]?.restructure(exm)
        if (arguments[0] is SingleTerm && (arguments.size == 1 || arguments[1] is SingleTerm)) {
            val v = GlobalStatic.IdentifierDictionary!!.getVariableToken(arguments[0]!!.getStrValue(exm), null, true)
            if (v == null || v.isReference) return false
            return true
        }
        return false
    }
}

internal class CheckfontMethod : FunctionMethod() {
    init { returnType = EType.Int64; argumentTypeArray = arrayOf(EType.String); canRestructure = true }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long =
        if (com.eraandroid.emuera.platform.Platform.graphics.isFontInstalled(arguments[0]!!.getStrValue(exm))) 1L else 0L
}

internal class CheckdataMethod(private val type: EraSaveFileType) : FunctionMethod() {
    init { returnType = EType.Int64; argumentTypeArray = arrayOf(EType.Int64); canRestructure = false }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long {
        val target = arguments[0]!!.getIntValue(exm)
        if (target < 0) throw CodeEE("${name}の引数に負の値(${target})が指定されました")
        else if (target > Int.MAX_VALUE) throw CodeEE("${name}の引数(${target})が大きすぎます")
        val result = exm.vEvaluator.checkData(target.toInt(), type)
        exm.vEvaluator.RESULTS = result.dataMes
        return result.state.ordinal.toLong()
    }
}

internal class CheckdataStrMethod(private val type: EraSaveFileType) : FunctionMethod() {
    init { returnType = EType.Int64; argumentTypeArray = arrayOf(EType.String); canRestructure = false }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long {
        val result = exm.vEvaluator.checkData(arguments[0]!!.getStrValue(exm), type)
        exm.vEvaluator.RESULTS = result.dataMes
        return result.state.ordinal.toLong()
    }
}

internal class FindFilesMethod(private val type: EraSaveFileType) : FunctionMethod() {
    init { returnType = EType.Int64; argumentTypeArray = null; canRestructure = false }
    override fun checkArgumentType(name: String, arguments: Array<IOperandTerm?>): String? {
        if (arguments.size > 1) return name + "関数の引数が多すぎます"
        if (arguments.isEmpty() || arguments[0] == null) return null
        if (!arguments[0]!!.isString) return name + "関数の1番目の引数が文字列ではありません"
        return null
    }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long {
        var pattern = "*"
        if (arguments.isNotEmpty() && arguments[0] != null) pattern = arguments[0]!!.getStrValue(exm)
        val filepathes = exm.vEvaluator.getDatFiles(type == EraSaveFileType.CharVar, pattern)
        val results = exm.vEvaluator.variableData.dataStringArray[VariableCode.RESULTS and VariableCode.__LOWERCASE__]
        for (i in 0 until minOf(filepathes.size, results.size)) results[i] = filepathes[i]
        return filepathes.size.toLong()
    }
}

internal class IsSkipMethod : FunctionMethod() {
    init { returnType = EType.Int64; argumentTypeArray = arrayOf(); canRestructure = false }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long = if (exm.process.skipPrint) 1L else 0L
}

internal class MesSkipMethod(private val warn: Boolean) : FunctionMethod() {
    init { returnType = EType.Int64; argumentTypeArray = null; canRestructure = false }
    override fun checkArgumentType(name: String, arguments: Array<IOperandTerm?>): String? {
        if (arguments.isNotEmpty()) return name + "関数の引数が多すぎます"
        if (warn) ParserMediator.warn("関数MOUSESKIP()は推奨されません。代わりに関数MESSKIP()を使用してください", GlobalStatic.Process?.getScaningLine(), 1, false, false, null)
        return null
    }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long = if (GlobalStatic.Console!!.mesSkip) 1L else 0L
}

internal class GetColorMethod(private val defaultColor: Boolean) : FunctionMethod() {
    init { returnType = EType.Int64; argumentTypeArray = arrayOf(); canRestructure = defaultColor }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long {
        val color = if (defaultColor) Config.ForeColor else GlobalStatic.Console!!.stringStyle.color
        return (color.rgb and 0xFFFFFF).toLong()
    }
}

internal class GetFocusColorMethod : FunctionMethod() {
    init { returnType = EType.Int64; argumentTypeArray = arrayOf(); canRestructure = true }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long = (Config.FocusColor.rgb and 0xFFFFFF).toLong()
}

internal class GetBGColorMethod(private val defaultColor: Boolean) : FunctionMethod() {
    init { returnType = EType.Int64; argumentTypeArray = arrayOf(); canRestructure = defaultColor }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long {
        val color = if (defaultColor) Config.BackColor else GlobalStatic.Console!!.bgColor
        return (color.rgb and 0xFFFFFF).toLong()
    }
}

internal class GetStyleMethod : FunctionMethod() {
    init { returnType = EType.Int64; argumentTypeArray = arrayOf(); canRestructure = false }
    override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long {
        val fs = GlobalStatic.Console!!.stringStyle.fontStyle
        var ret = 0L
        if ((fs and FontStyle.Bold) != 0) ret = ret or 1
        if ((fs and FontStyle.Italic) != 0) ret = ret or 2
        if ((fs and FontStyle.Strikeout) != 0) ret = ret or 4
        if ((fs and FontStyle.Underline) != 0) ret = ret or 8
        return ret
    }
}

internal class GetFontMethod : FunctionMethod() {
    init { returnType = EType.String; argumentTypeArray = arrayOf(); canRestructure = false }
    override fun getStrValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): String = GlobalStatic.Console!!.stringStyle.fontname
}
