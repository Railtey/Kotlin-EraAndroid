package com.eraandroid.emuera.gamedata.function

import com.eraandroid.emuera.Resources
import com.eraandroid.emuera.gamedata.expression.EType
import com.eraandroid.emuera.gamedata.expression.ExpressionMediator
import com.eraandroid.emuera.gamedata.expression.IOperandTerm
import com.eraandroid.emuera.gamedata.expression.SingleTerm
import com.eraandroid.emuera.sub.ExeEE

abstract class FunctionMethod {
    var returnType: EType = EType.Void
        protected set
    protected var argumentTypeArray: Array<EType>? = null
    var name: String = ""
        protected set

    /** 引数の数・型が一致するかどうかのテスト。正しくない場合はエラーメッセージを返す。 */
    open fun checkArgumentType(name: String, arguments: Array<IOperandTerm?>): String? {
        val types = argumentTypeArray!!
        if (arguments.size != types.size) return Resources.fmt(Resources.SyntaxErrMesMethodDefaultArgumentNum0, name)
        for (i in types.indices) {
            val a = arguments[i] ?: return Resources.fmt(Resources.SyntaxErrMesMethodDefaultArgumentNotNullable0, name, i + 1)
            if (types[i] != a.getOperandType()) return Resources.fmt(Resources.SyntaxErrMesMethodDefaultArgumentType0, name, i + 1)
        }
        return null
    }

    /** Argumentが全て定数の時にMethodを解体してよいかどうか */
    var canRestructure = false
        protected set

    /** FunctionMethodが固有のRestructure()を持つかどうか */
    var hasUniqueRestructure = false
        protected set

    open fun getIntValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Long = throw ExeEE("戻り値の型が違う or 未実装")
    open fun getStrValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): String = throw ExeEE("戻り値の型が違う or 未実装")
    open fun getReturnValue(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): SingleTerm =
        if (returnType == EType.Int64) SingleTerm(getIntValue(exm, arguments)) else SingleTerm(getStrValue(exm, arguments))

    /** 戻り値は全体をRestructureできるかどうか */
    open fun uniqueRestructure(exm: ExpressionMediator, arguments: Array<IOperandTerm?>): Boolean = throw ExeEE("未実装？")

    fun setMethodName(name: String) { this.name = name }
}

class FunctionMethodTerm(private val method: FunctionMethod, private val arguments: Array<IOperandTerm?>) : IOperandTerm(method.returnType) {
    override fun getIntValue(exm: ExpressionMediator): Long = method.getIntValue(exm, arguments)
    override fun getStrValue(exm: ExpressionMediator): String = method.getStrValue(exm, arguments)
    override fun getValue(exm: ExpressionMediator): SingleTerm = method.getReturnValue(exm, arguments)

    override fun restructure(exm: ExpressionMediator): IOperandTerm {
        if (method.hasUniqueRestructure) {
            if (method.uniqueRestructure(exm, arguments) && method.canRestructure) return getValue(exm)
            return this
        }
        var argIsConst = true
        for (i in arguments.indices) {
            val a = arguments[i] ?: continue
            arguments[i] = a.restructure(exm)
            argIsConst = argIsConst and (arguments[i] is SingleTerm)
        }
        if (method.canRestructure && argIsConst) return getValue(exm)
        return this
    }
}
