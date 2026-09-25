package com.eraandroid.emuera.gamedata

import com.eraandroid.emuera.GlobalStatic
import com.eraandroid.emuera.gamedata.expression.*
import com.eraandroid.emuera.gamedata.function.FunctionMethod
import com.eraandroid.emuera.gamedata.function.FunctionMethodTerm
import com.eraandroid.emuera.gamedata.variable.VariableTerm
import com.eraandroid.emuera.sub.*

class StrForm private constructor() {
    private var strs: Array<String> = arrayOf("")
    private var terms: Array<IOperandTerm?> = arrayOf()

    val isConst: Boolean get() = strs.size == 1

    fun getIOperandTerm(): IOperandTerm? {
        if (strs.size == 2 && strs[0].isEmpty() && strs[1].isEmpty()) return terms[0]
        return null
    }

    fun restructure(exm: ExpressionMediator) {
        if (strs.size == 1) return
        var canRestructure = false
        for (i in terms.indices) {
            terms[i] = terms[i]!!.restructure(exm)
            if (terms[i] is SingleTerm) canRestructure = true
        }
        if (!canRestructure) return
        val strList = ArrayList(strs.toList())
        val termList = ArrayList(terms.toList())
        var i = 0
        while (i < termList.size) {
            if (termList[i] is SingleTerm) {
                val str = termList[i]!!.getStrValue(exm)
                strList[i] = strList[i] + str + strList[i + 1]
                termList.removeAt(i)
                strList.removeAt(i + 1)
                i--
            }
            i++
        }
        strs = strList.toTypedArray()
        terms = termList.toTypedArray()
    }

    fun getString(exm: ExpressionMediator?): String {
        if (strs.size == 1) return strs[0]
        val b = StringBuilder(100)
        for (i in 0 until strs.size - 1) {
            b.append(strs[i])
            b.append(terms[i]!!.getStrValue(exm ?: GlobalStatic.EMediator!!))
        }
        b.append(strs[strs.size - 1])
        return b.toString()
    }

    companion object {
        private lateinit var formatCurlyBrace: FunctionMethod
        private lateinit var formatPercent: FunctionMethod
        private lateinit var formatYenAt: FunctionMethod
        private lateinit var nameTarget: FunctionMethodTerm
        private lateinit var callnameMaster: FunctionMethodTerm
        private lateinit var callnamePlayer: FunctionMethodTerm
        private lateinit var nameAssi: FunctionMethodTerm
        private lateinit var callnameTarget: FunctionMethodTerm

        fun initialize() {
            formatCurlyBrace = FormatCurlyBrace()
            formatPercent = FormatPercent()
            formatYenAt = FormatYenAt()
            val vd = GlobalStatic.VariableData!!
            val nameID = vd.getSystemVariableToken("NAME")
            val callnameID = vd.getSystemVariableToken("CALLNAME")
            fun zero(): Array<IOperandTerm?> = arrayOf(SingleTerm(0L))
            val target = VariableTerm(vd.getSystemVariableToken("TARGET"), zero())
            val master = VariableTerm(vd.getSystemVariableToken("MASTER"), zero())
            val player = VariableTerm(vd.getSystemVariableToken("PLAYER"), zero())
            val assi = VariableTerm(vd.getSystemVariableToken("ASSI"), zero())
            nameTarget = FunctionMethodTerm(formatPercent, arrayOf(VariableTerm(nameID, arrayOf(target)), null, null))
            callnameMaster = FunctionMethodTerm(formatPercent, arrayOf(VariableTerm(callnameID, arrayOf(master)), null, null))
            callnamePlayer = FunctionMethodTerm(formatPercent, arrayOf(VariableTerm(callnameID, arrayOf(player)), null, null))
            nameAssi = FunctionMethodTerm(formatPercent, arrayOf(VariableTerm(nameID, arrayOf(assi)), null, null))
            callnameTarget = FunctionMethodTerm(formatPercent, arrayOf(VariableTerm(callnameID, arrayOf(target)), null, null))
        }

        fun fromWordToken(wt: StrFormWord): StrForm {
            val ret = StrForm()
            ret.strs = wt.strs
            val termArray = arrayOfNulls<IOperandTerm>(wt.subWords.size)
            for (i in wt.subWords.indices) {
                val swt = wt.subWords[i]
                if (swt is TripleSymbolSubWord) {
                    termArray[i] = when (swt.code) {
                        '*' -> nameTarget
                        '+' -> callnameMaster
                        '=' -> callnamePlayer
                        '/' -> nameAssi
                        '$' -> callnameTarget
                        else -> throw ExeEE("何かおかしい")
                    }
                    continue
                }
                if (swt is YenAtSubWord) {
                    val wc = swt.words
                    val operand: IOperandTerm = if (wc != null) {
                        val o = ExpressionParser.reduceIntegerTerm(wc, TermEndWith.EoL)
                        if (!wc.eol) throw CodeEE("三項演算子\\@の第一オペランドが異常です")
                        o
                    } else SingleTerm(0L)
                    val left: IOperandTerm = StrFormTerm(fromWordToken(swt.left))
                    val right: IOperandTerm = if (swt.right == null) SingleTerm("") else StrFormTerm(fromWordToken(swt.right))
                    termArray[i] = FunctionMethodTerm(formatYenAt, arrayOf(operand, left, right))
                    continue
                }
                val wc = swt.words!!
                val operand = ExpressionParser.reduceExpressionTerm(wc, TermEndWith.Comma)
                    ?: if (swt is CurlyBraceSubWord) throw CodeEE("{}の中に式が存在しません") else throw CodeEE("%%の中に式が存在しません")
                var second: IOperandTerm? = null
                var third: SingleTerm? = null
                wc.shiftNext()
                if (!wc.eol) {
                    second = ExpressionParser.reduceIntegerTerm(wc, TermEndWith.Comma)
                    wc.shiftNext()
                    if (!wc.eol) {
                        val id = wc.current as? IdentifierWord ?: throw CodeEE("','の後にRIGHT又はLEFTがありません")
                        if (id.code.eqVar("LEFT")) third = SingleTerm(1L)
                        else if (!id.code.eqVar("RIGHT")) throw CodeEE("','の後にRIGHT又はLEFT以外の単語があります")
                        wc.shiftNext()
                    }
                    if (!wc.eol) throw CodeEE("RIGHT又はLEFTの後に余分な文字があります")
                }
                if (swt is CurlyBraceSubWord) {
                    if (operand.getOperandType() != EType.Int64) throw CodeEE("{}の中の式が数式ではありません")
                    termArray[i] = FunctionMethodTerm(formatCurlyBrace, arrayOf(operand, second, third))
                    continue
                }
                if (operand.getOperandType() != EType.String) throw CodeEE("%%の中の式が文字列式ではありません")
                termArray[i] = FunctionMethodTerm(formatPercent, arrayOf(operand, second, third))
            }
            ret.terms = termArray
            return ret
        }
    }

    private abstract class FormattedStringMethod : FunctionMethod() {
        init {
            canRestructure = true
            returnType = EType.String
            argumentTypeArray = null
        }
        override fun checkArgumentType(name: String, arguments: Array<IOperandTerm?>): String? = throw ExeEE("型チェックは呼び出し元が行うこと")
        override fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long = throw ExeEE("戻り値の型が違う")
        override fun getReturnValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): SingleTerm = SingleTerm(getStrValue(exm, arguments))
    }

    private class FormatCurlyBrace : FormattedStringMethod() {
        override fun getStrValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): String {
            var ret = arguments[0]!!.getIntValue(exm).toString()
            val a1 = arguments[1] ?: return ret
            val w = a1.getIntValue(exm).toInt()
            if (w < 0) throw CodeEE("書式指定の幅に負の値が指定されました")
            ret = if (arguments[2] != null) ret.padEnd(w, ' ') else ret.padStart(w, ' ')
            return ret
        }
    }

    private class FormatPercent : FormattedStringMethod() {
        override fun getStrValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): String {
            var ret = arguments[0]!!.getStrValue(exm)
            val a1 = arguments[1] ?: return ret
            var totalLength = a1.getIntValue(exm).toInt()
            val currentLength = LangManager.getStrlenLang(ret)
            totalLength -= currentLength - ret.length
            if (totalLength < ret.length) return ret
            ret = if (arguments[2] != null) ret.padEnd(totalLength, ' ') else ret.padStart(totalLength, ' ')
            return ret
        }
    }

    private class FormatYenAt : FormattedStringMethod() {
        override fun getStrValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): String =
            if (arguments[0]!!.getIntValue(exm) != 0L) arguments[1]!!.getStrValue(exm) else arguments[2]!!.getStrValue(exm)
    }
}
