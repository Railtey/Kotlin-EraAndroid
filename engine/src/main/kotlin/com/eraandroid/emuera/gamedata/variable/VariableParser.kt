package com.eraandroid.emuera.gamedata.variable

import com.eraandroid.emuera.GlobalStatic
import com.eraandroid.emuera.config.Config
import com.eraandroid.emuera.gamedata.expression.ExpressionParser
import com.eraandroid.emuera.gamedata.expression.IOperandTerm
import com.eraandroid.emuera.gamedata.expression.SingleTerm
import com.eraandroid.emuera.sub.CodeEE
import com.eraandroid.emuera.sub.WordCollection

object VariableParser {
    lateinit var ZeroTerm: SingleTerm
        private set
    lateinit var TARGET: VariableTerm
        private set

    fun initialize() {
        ZeroTerm = SingleTerm(0L)
        TARGET = VariableTerm(GlobalStatic.VariableData!!.getSystemVariableToken("TARGET"), arrayOf(ZeroTerm))
    }

    fun isVariable(ids: String?): Boolean {
        if (ids.isNullOrEmpty()) return false
        val idlist = ids.split(':')
        return GlobalStatic.IdentifierDictionary!!.getVariableToken(idlist[0], null, false) != null
    }

    /** 識別子を読み終えた状態からの解析 */
    fun reduceVariable(id: VariableToken, wc: WordCollection): VariableTerm {
        var op1: IOperandTerm? = null
        var op2: IOperandTerm? = null
        var op3: IOperandTerm? = null
        var i = 0
        while (true) {
            if (wc.current.type != ':') break
            if (i >= 3) throw CodeEE(VariableCode.toString(id.code) + "の引数が多すぎます")
            wc.shiftNext()
            val operand = ExpressionParser.reduceVariableArgument(wc, id.code)
            when (i) { 0 -> op1 = operand; 1 -> op2 = operand; 2 -> op3 = operand }
            i++
        }
        return reduceVariable(id, op1, op2, op3)
    }

    fun reduceVariable(id: VariableToken, p1: IOperandTerm?, p2: IOperandTerm?, p3: IOperandTerm?): VariableTerm {
        val terms: Array<IOperandTerm?>
        var op1 = p1
        var op2 = p2
        val op3 = p3
        if (id.isCharacterData) {
            if (id.isArray2D) {
                if (op1 == null && op2 == null && op3 == null) return VariableNoArgTerm(id)
                if (op1 == null || op2 == null || op3 == null) throw CodeEE("キャラクタ二次元配列変数${id.name}の引数は省略できません")
                terms = arrayOf(op1, op2, op3)
            } else if (id.isArray1D) {
                if (op3 != null) throw CodeEE("キャラクタ変数${id.name}の引数が多すぎます")
                if (op1 == null && op2 == null && Config.SystemNoTarget) return VariableNoArgTerm(id)
                if (op2 == null) {
                    if (Config.SystemNoTarget) throw CodeEE("キャラクタ配列変数${id.name}の引数は省略できません(コンフィグにより禁止が選択されています)")
                    op2 = op1 ?: ZeroTerm
                    op1 = TARGET
                }
                terms = arrayOf(op1, op2)
            } else {
                if (op2 != null) throw CodeEE("キャラクタ変数${id.name}の引数が多すぎます")
                if (op1 == null && op3 == null && Config.SystemNoTarget) return VariableNoArgTerm(id)
                if (op1 == null) {
                    if (Config.SystemNoTarget) throw CodeEE("キャラクタ変数${id.name}の引数は省略できません(コンフィグにより禁止が選択されています)")
                    op1 = TARGET
                }
                terms = arrayOf(op1)
            }
        } else if (id.isArray3D) {
            if (op1 == null && op2 == null && op3 == null) return VariableNoArgTerm(id)
            if (op1 == null || op2 == null || op3 == null) throw CodeEE("三次元配列変数${id.name}の引数は省略できません")
            terms = arrayOf(op1, op2, op3)
        } else if (id.isArray2D) {
            if (op1 == null && op2 == null && op3 == null) return VariableNoArgTerm(id)
            if (op1 == null || op2 == null) throw CodeEE("二次元配列変数${id.name}の引数は省略できません")
            if (op3 != null) throw CodeEE("二次元配列${id.name}の引数が多すぎます")
            terms = arrayOf(op1, op2)
        } else if (id.isArray1D) {
            if (op2 != null) throw CodeEE("一次元配列変数${id.name}の引数が多すぎます")
            if (op1 == null) {
                op1 = ZeroTerm
                if (!Config.CompatiRAND && id.code == VariableCode.RAND) throw CodeEE("RANDの引数が省略されています")
            }
            if (!Config.CompatiRAND && op1 is SingleTerm && id.code == VariableCode.RAND && op1.int == 0L)
                throw CodeEE("RANDの引数に0が与えられています")
            terms = arrayOf(op1)
        } else if (op1 != null) {
            throw CodeEE("配列でない変数${id.name}を引数付きで呼び出しています")
        } else terms = arrayOf()
        for (i in terms.indices) if (terms[i]!!.isString) terms[i] = VariableStrArgTerm(id.code, terms[i]!!, i)
        return VariableTerm(id, terms)
    }
}
