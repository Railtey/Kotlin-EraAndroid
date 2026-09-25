package com.eraandroid.emuera.gamedata.expression

import com.eraandroid.emuera.GlobalStatic
import com.eraandroid.emuera.Program
import com.eraandroid.emuera.config.Config
import com.eraandroid.emuera.gamedata.ParserMediator
import com.eraandroid.emuera.gamedata.StrForm
import com.eraandroid.emuera.gamedata.variable.VariableCode
import com.eraandroid.emuera.gamedata.variable.VariableParser
import com.eraandroid.emuera.gamedata.variable.VariableToken
import com.eraandroid.emuera.sub.*

enum class ArgsEndWith { None, EoL, RightParenthesis, RightBracket }

object TermEndWith {
    const val None = 0x0000
    const val EoL = 0x0001
    const val Comma = 0x0002
    const val RightParenthesis = 0x0004
    const val RightBracket = 0x0008
    const val Assignment = 0x0010
    const val RightParenthesis_Comma = RightParenthesis or Comma
    const val RightBracket_Comma = RightBracket or Comma
    const val Comma_Assignment = Comma or Assignment
    const val RightParenthesis_Comma_Assignment = RightParenthesis or Comma or Assignment
    const val RightBracket_Comma_Assignment = RightBracket or Comma or Assignment
}

fun String.eqVar(other: String): Boolean = this.equals(other, ignoreCase = Config.ICVariable)
fun String.eqFunc(other: String): Boolean = this.equals(other, ignoreCase = Config.ICFunction)

object ExpressionParser {
    /** カンマで区切られた引数を一括して取得。 */
    fun reduceArguments(wc: WordCollection?, endWith: ArgsEndWith, isDefine: Boolean): Array<IOperandTerm?> {
        if (wc == null) throw ExeEE("空のストリームを渡された")
        val terms = ArrayList<IOperandTerm?>()
        var termEndWith = TermEndWith.EoL
        when (endWith) {
            ArgsEndWith.EoL -> termEndWith = TermEndWith.Comma
            ArgsEndWith.RightParenthesis -> termEndWith = TermEndWith.RightParenthesis_Comma
            else -> {}
        }
        val termEndWithAssignment = termEndWith or TermEndWith.Assignment
        loop@ while (true) {
            val word = wc.current
            when (word.type) {
                '\u0000' -> {
                    if (endWith == ArgsEndWith.RightBracket) throw CodeEE("'['に対応する']'が見つかりません")
                    if (endWith == ArgsEndWith.RightParenthesis) throw CodeEE("'('に対応する')'が見つかりません")
                    break@loop
                }
                ')' -> {
                    if (endWith == ArgsEndWith.RightParenthesis) { wc.shiftNext(); break@loop }
                    throw CodeEE("構文解析中に予期しない')'を発見しました")
                }
                ']' -> {
                    if (endWith == ArgsEndWith.RightBracket) { wc.shiftNext(); break@loop }
                    throw CodeEE("構文解析中に予期しない']'を発見しました")
                }
            }
            if (!isDefine) {
                terms.add(reduceExpressionTerm(wc, termEndWith))
            } else {
                terms.add(reduceExpressionTerm(wc, termEndWithAssignment))
                val last = terms[terms.size - 1] ?: throw CodeEE("関数定義の引数は省略できません")
                if (wc.current is OperatorWord) {
                    wc.shiftNext()
                    val term = reduceTerm(wc, false, termEndWith, VariableCode.__NULL__) ?: throw CodeEE("'='の後に式がありません")
                    if (term.getOperandType() != last.getOperandType()) throw CodeEE("'='の前後で型が一致しません")
                    terms.add(term)
                } else {
                    if (last.getOperandType() == EType.Int64) terms.add(NullTerm(0L)) else terms.add(NullTerm(""))
                }
            }
            if (wc.current.type == ',') wc.shiftNext()
        }
        return terms.toTypedArray()
    }

    /** 数式または文字列式。nullを返すことがある。 */
    fun reduceExpressionTerm(wc: WordCollection, endWith: Int): IOperandTerm? =
        reduceTerm(wc, false, endWith, VariableCode.__NULL__)

    fun reduceIntegerTerm(wc: WordCollection, endwith: Int): IOperandTerm {
        val term = reduceTerm(wc, false, endwith, VariableCode.__NULL__) ?: throw CodeEE("構文を式として解釈できません")
        if (term.getOperandType() != EType.Int64) throw CodeEE("式の結果が数値ではありません")
        return term
    }

    /** 結果次第ではSingleTermを返すことがある。 */
    fun toStrFormTerm(sfw: StrFormWord): IOperandTerm {
        val strf = StrForm.fromWordToken(sfw)
        if (strf.isConst) return SingleTerm(strf.getString(null))
        return StrFormTerm(strf)
    }

    /** カンマで区切られたCASEの引数を一括して取得。行端で終わる。 */
    fun reduceCaseExpressions(wc: WordCollection): Array<CaseExpression> {
        val terms = ArrayList<CaseExpression>()
        while (!wc.eol) {
            terms.add(reduceCaseExpression(wc))
            wc.shiftNext()
        }
        return terms.toTypedArray()
    }

    fun reduceVariableArgument(wc: WordCollection, varCode: Int): IOperandTerm =
        reduceTerm(wc, false, TermEndWith.EoL, varCode) ?: throw CodeEE("変数の:の後に引数がありません")

    fun reduceVariableIdentifier(wc: WordCollection, idStr: String): VariableToken? {
        var subId: String? = null
        if (wc.current.type == '@') {
            wc.shiftNext()
            val subidWT = wc.current as? IdentifierWord ?: throw CodeEE("@の使い方が不正です")
            wc.shiftNext()
            subId = subidWT.code
        }
        return GlobalStatic.IdentifierDictionary!!.getVariableToken(idStr, subId, true)
    }

    /** 識別子一つを解決 */
    private fun reduceIdentifier(wc: WordCollection, idStr: String, varCode: Int): IOperandTerm {
        wc.shiftNext()
        val symbol = wc.current as? SymbolWord
        if (symbol != null && symbol.type == '.') {
            throw NotImplCodeEE()
        } else if (symbol != null && (symbol.type == '(' || symbol.type == '[')) {
            wc.shiftNext()
            if (symbol.type == '[') throw CodeEE("[]を使った機能はまだ実装されていません")
            val args = reduceArguments(wc, ArgsEndWith.RightParenthesis, false)
            val mToken = GlobalStatic.IdentifierDictionary!!.getFunctionMethod(GlobalStatic.LabelDictionary, idStr, args, false)
            if (mToken == null) {
                if (!Program.AnalysisMode) GlobalStatic.IdentifierDictionary!!.throwException(idStr, true)
                else {
                    GlobalStatic.tempDic[idStr] = (GlobalStatic.tempDic[idStr] ?: 0L) + 1
                    return NullTerm(0L)
                }
            }
            return mToken!!
        } else {
            val id = reduceVariableIdentifier(wc, idStr)
            if (id != null) {
                return if (varCode != VariableCode.__NULL__) VariableParser.reduceVariable(id, null, null, null)
                else VariableParser.reduceVariable(id, wc)
            }
            val refToken = GlobalStatic.IdentifierDictionary!!.getFunctionMethod(GlobalStatic.LabelDictionary, idStr, null, false)
            if (refToken != null) return refToken
            if (varCode != VariableCode.__NULL__ && GlobalStatic.ConstantData!!.isDefined(varCode, idStr))
                return SingleTerm(idStr)
            GlobalStatic.IdentifierDictionary!!.throwException(idStr, false)
        }
        throw ExeEE("エラー投げ損ねた")
    }

    private fun reduceCaseExpression(wc: WordCollection): CaseExpression {
        val ret = CaseExpression()
        var id = wc.current as? IdentifierWord
        if (id != null && id.code.eqVar("IS")) {
            wc.shiftNext()
            ret.caseType = CaseExpressionType.Is
            val opWT = wc.current as? OperatorWord ?: throw CodeEE("ISキーワードの後に演算子がありません")
            val op = opWT.code
            if (!OperatorManager.isBinary(op)) throw CodeEE("ISキーワードの後の演算子が2項演算子ではありません")
            wc.shiftNext()
            ret.operator = op
            ret.leftTerm = reduceTerm(wc, false, TermEndWith.Comma, VariableCode.__NULL__)
                ?: throw CodeEE("ISキーワードの後に式がありません")
            return ret
        }
        ret.leftTerm = reduceTerm(wc, true, TermEndWith.Comma, VariableCode.__NULL__) ?: throw CodeEE("CASEの引数は省略できません")
        id = wc.current as? IdentifierWord
        if (id != null && id.code.eqVar("TO")) {
            ret.caseType = CaseExpressionType.To
            wc.shiftNext()
            ret.rightTerm = reduceTerm(wc, true, TermEndWith.Comma, VariableCode.__NULL__) ?: throw CodeEE("TOキーワードの後に式がありません")
            id = wc.current as? IdentifierWord
            if (id != null && id.code.eqVar("TO")) throw CodeEE("TOキーワードが2度使われています")
            if (ret.leftTerm!!.getOperandType() != ret.rightTerm!!.getOperandType()) throw CodeEE("TOキーワードの前後の型が一致していません")
            return ret
        }
        ret.caseType = CaseExpressionType.Normal
        return ret
    }

    private fun isComparison(op: OperatorCode) = op == OperatorCode.Equal || op == OperatorCode.Greater || op == OperatorCode.Less ||
        op == OperatorCode.GreaterEqual || op == OperatorCode.LessEqual || op == OperatorCode.NotEqual

    /** 解析器の本体 */
    private fun reduceTerm(wc: WordCollection, allowKeywordTo: Boolean, endWith: Int, varCode: Int): IOperandTerm? {
        val stack = TermStack()
        var ternaryCount = 0
        var formerOp = OperatorCode.NULL
        val varArg = varCode != VariableCode.__NULL__
        outer@ do {
            val token = wc.current
            when (token.type) {
                '\u0000' -> break@outer
                '"' -> stack.add((token as LiteralStringWord).str)
                '0' -> stack.add((token as LiteralIntegerWord).int)
                'F' -> stack.add(toStrFormTerm(token as StrFormWord))
                'A' -> {
                    val idStr = (token as IdentifierWord).code
                    if (idStr.eqVar("TO")) {
                        if (allowKeywordTo) break@outer
                        else throw CodeEE("TOキーワードはここでは使用できません")
                    } else if (idStr.eqVar("IS")) throw CodeEE("ISキーワードはここでは使用できません")
                    stack.add(reduceIdentifier(wc, idStr, varCode))
                    continue@outer
                }
                '=' -> {
                    if (varArg) throw CodeEE("変数の引数の読み取り中に予期しない演算子を発見しました")
                    val op = (token as OperatorWord).code
                    if (op == OperatorCode.Assignment) {
                        if ((endWith and TermEndWith.Assignment) == TermEndWith.Assignment) break@outer
                        throw CodeEE("式中で代入演算子'='が使われています(等価比較には'=='を使用してください)")
                    }
                    if (isComparison(formerOp) && isComparison(op))
                        ParserMediator.warn("（構文上の注意）比較演算子が連続しています。", GlobalStatic.Process?.getScaningLine(), 0, false, false)
                    stack.add(op)
                    formerOp = op
                    if (op == OperatorCode.Ternary_a) ternaryCount++
                    else if (op == OperatorCode.Ternary_b) {
                        if (ternaryCount > 0) ternaryCount--
                        else throw CodeEE("対応する'?'のない'#'です")
                    }
                }
                '(' -> {
                    wc.shiftNext()
                    val inTerm = reduceTerm(wc, false, TermEndWith.RightParenthesis, VariableCode.__NULL__)
                        ?: throw CodeEE("かっこ\"(\"～\")\"の中に式が含まれていません")
                    stack.add(inTerm)
                    if (wc.current.type != ')') throw CodeEE("対応する')'のない'('です")
                    wc.shiftNext()
                    continue@outer
                }
                ')' -> {
                    if ((endWith and TermEndWith.RightParenthesis) == TermEndWith.RightParenthesis) break@outer
                    throw CodeEE("構文解釈中に予期しない記号'" + token.type + "'を発見しました")
                }
                ']' -> {
                    if ((endWith and TermEndWith.RightBracket) == TermEndWith.RightBracket) break@outer
                    throw CodeEE("構文解釈中に予期しない記号'" + token.type + "'を発見しました")
                }
                ',' -> {
                    if ((endWith and TermEndWith.Comma) == TermEndWith.Comma) break@outer
                    throw CodeEE("構文解釈中に予期しない記号'" + token.type + "'を発見しました")
                }
                'M' -> throw ExeEE("マクロ解決失敗")
                else -> throw CodeEE("構文解釈中に予期しない記号'" + token.type + "'を発見しました")
            }
            wc.shiftNext()
        } while (!varArg)
        if (ternaryCount > 0) throw CodeEE("'?'と'#'の数が正しく対応していません")
        return stack.reduceAll()
    }

    /** 式解決用クラス */
    private class TermStack {
        var state = 0
        var hasBefore = false
        var hasAfter = false
        var waitAfter = false
        val stack = ArrayDeque<Any>()

        fun add(op: OperatorCode) {
            if (state == 2 || state == 3) throw CodeEE("式が異常です")
            if (state == 0) {
                if (!OperatorManager.isUnary(op)) throw CodeEE("式が異常です")
                stack.addLast(op)
                state = if (op == OperatorCode.Plus || op == OperatorCode.Minus || op == OperatorCode.BitNot) 2 else 3
                return
            }
            if (state == 1) {
                if (OperatorManager.isUnaryAfter(op)) {
                    if (hasAfter) { hasAfter = false; throw CodeEE("後置の単項演算子が複数存在しています") }
                    if (hasBefore) { hasBefore = false; throw CodeEE("インクリメント・デクリメントを前置・後置両方同時に使うことはできません") }
                    stack.addLast(op)
                    reduceUnaryAfter()
                    if (waitAfter) reduceUnary()
                    hasBefore = false
                    hasAfter = true
                    waitAfter = false
                    return
                }
                if (!OperatorManager.isBinary(op) && !OperatorManager.isTernary(op)) throw CodeEE("式が異常です")
                if (waitAfter) reduceUnary()
                val priority = OperatorManager.getPriority(op)
                while (lastPriority() >= priority) reduceLastThree()
                stack.addLast(op)
                state = 0
                waitAfter = false
                hasBefore = false
                hasAfter = false
                return
            }
            throw CodeEE("式が異常です")
        }

        fun add(i: Long) = add(SingleTerm(i))
        fun add(s: String) = add(SingleTerm(s))
        fun add(term: IOperandTerm) {
            stack.addLast(term)
            if (state == 1) throw CodeEE("式が異常です")
            if (state == 2) waitAfter = true
            if (state == 3) { reduceUnary(); hasBefore = true }
            state = 1
        }

        private fun lastPriority(): Int {
            if (stack.size < 3) return -1
            val op = stack[stack.size - 2] as OperatorCode
            return OperatorManager.getPriority(op)
        }

        fun reduceAll(): IOperandTerm? {
            if (stack.isEmpty()) return null
            if (state != 1) throw CodeEE("式が異常です")
            if (waitAfter) reduceUnary()
            waitAfter = false
            hasBefore = false
            hasAfter = false
            while (stack.size > 1) reduceLastThree()
            return stack.removeLast() as IOperandTerm
        }

        private fun reduceUnary() {
            val operand = stack.removeLast() as IOperandTerm
            val op = stack.removeLast() as OperatorCode
            stack.addLast(OperatorMethodManager.reduceUnaryTerm(op, operand))
        }

        private fun reduceUnaryAfter() {
            val op = stack.removeLast() as OperatorCode
            val operand = stack.removeLast() as IOperandTerm
            stack.addLast(OperatorMethodManager.reduceUnaryAfterTerm(op, operand))
        }

        private fun reduceLastThree() {
            val right = stack.removeLast() as IOperandTerm
            val op = stack.removeLast() as OperatorCode
            val left = stack.removeLast() as IOperandTerm
            if (OperatorManager.isTernary(op)) {
                if (stack.size > 1) { reduceTernary(left, right); return }
                throw CodeEE("式の数が不足しています")
            }
            stack.addLast(OperatorMethodManager.reduceBinaryTerm(op, left, right))
        }

        private fun reduceTernary(left: IOperandTerm, right: IOperandTerm) {
            stack.removeLast()
            val newLeft = stack.removeLast() as IOperandTerm
            stack.addLast(OperatorMethodManager.reduceTernaryTerm(newLeft, left, right))
        }
    }
}
