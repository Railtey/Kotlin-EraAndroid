package com.eraandroid.emuera.gamedata.function

import com.eraandroid.emuera.gamedata.expression.*
import com.eraandroid.emuera.gameproc.CalledFunction
import com.eraandroid.emuera.gameproc.UserDefinedFunctionArgument
import com.eraandroid.emuera.gameproc.UserDefinedFunctionData
import com.eraandroid.emuera.gameproc.UserDifinedFunctionDataArgType
import com.eraandroid.emuera.gameproc.FunctionLabelLine
import com.eraandroid.emuera.sub.CodeEE

class UserDefinedRefMethod private constructor(val name: String, val retType: EType, val argTypeList: IntArray) {
    var calledFunction: CalledFunction? = null
        private set

    fun matchType(call: CalledFunction): Boolean {
        val label = call.topLabel
        if (label.isError) return false
        if (retType != label.methodType) return false
        if (argTypeList.size != label.arg.size) return false
        for (i in argTypeList.indices) {
            val vToken = label.arg[i].identifier
            if (vToken.isReference) {
                var type = UserDifinedFunctionDataArgType.__Ref + vToken.dimension
                type = type or if (vToken.isInteger) UserDifinedFunctionDataArgType.Int else UserDifinedFunctionDataArgType.Str
                if (argTypeList[i] != type) return false
            } else {
                if (vToken.isInteger && argTypeList[i] != UserDifinedFunctionDataArgType.Int) return false
                if (vToken.isString && argTypeList[i] != UserDifinedFunctionDataArgType.Str) return false
            }
        }
        return true
    }

    fun matchType(rother: UserDefinedRefMethod): Boolean =
        retType == rother.retType && argTypeList.contentEquals(rother.argTypeList)

    fun setReference(call: CalledFunction?) { calledFunction = call }

    companion object {
        fun create(funcData: UserDefinedFunctionData): UserDefinedRefMethod =
            UserDefinedRefMethod(funcData.name!!, if (funcData.typeIsStr) EType.String else EType.Int64, funcData.argList)
    }
}

abstract class SuperUserDefinedMethodTerm protected constructor(returnType: EType) : IOperandTerm(returnType) {
    abstract val argument: UserDefinedFunctionArgument
    abstract val call: CalledFunction

    override fun getIntValue(exm: ExpressionMediator): Long = exm.process.getValue(this)?.int ?: 0L
    override fun getStrValue(exm: ExpressionMediator): String = exm.process.getValue(this)?.str ?: ""
    override fun getValue(exm: ExpressionMediator): SingleTerm =
        exm.process.getValue(this) ?: if (getOperandType() == EType.Int64) SingleTerm(0L) else SingleTerm("")
}

class UserDefinedMethodTerm private constructor(
    override val argument: UserDefinedFunctionArgument, returnType: EType, override val call: CalledFunction
) : SuperUserDefinedMethodTerm(returnType) {
    override fun restructure(exm: ExpressionMediator): IOperandTerm {
        argument.restructure(exm)
        return this
    }

    companion object {
        fun create(targetLabel: FunctionLabelLine, srcArgs: Array<IOperandTerm?>, errMes: Array<String?>): UserDefinedMethodTerm? {
            val call = CalledFunction.createCalledFunctionMethod(targetLabel, targetLabel.labelName)
            val arg = call.convertArg(srcArgs, errMes) ?: return null
            return UserDefinedMethodTerm(arg, call.topLabel.methodType, call)
        }
    }
}

class UserDefinedRefMethodTerm(private val reffunc: UserDefinedRefMethod, private val srcArgs: Array<IOperandTerm?>) : SuperUserDefinedMethodTerm(reffunc.retType) {
    override val argument: UserDefinedFunctionArgument
        get() {
            val cf = reffunc.calledFunction ?: throw CodeEE("何も参照していない関数参照${reffunc.name}を呼び出しました")
            val errMes = arrayOfNulls<String>(1)
            return cf.convertArg(srcArgs, errMes) ?: throw CodeEE(errMes[0] ?: "")
        }
    override val call: CalledFunction
        get() = reffunc.calledFunction ?: throw CodeEE("何も参照していない関数参照${reffunc.name}を呼び出しました")

    override fun restructure(exm: ExpressionMediator): IOperandTerm {
        for (i in srcArgs.indices) {
            val a = srcArgs[i] ?: continue
            if ((reffunc.argTypeList[i] and UserDifinedFunctionDataArgType.__Ref) == UserDifinedFunctionDataArgType.__Ref) a.restructure(exm)
            else srcArgs[i] = a.restructure(exm)
        }
        return this
    }
}

class UserDefinedRefMethodNoArgTerm(private val reffunc: UserDefinedRefMethod) : SuperUserDefinedMethodTerm(reffunc.retType) {
    private fun err(): Nothing = throw CodeEE("引数のない関数参照${reffunc.name}を呼び出しました")
    override val argument: UserDefinedFunctionArgument get() = err()
    override val call: CalledFunction get() = err()
    fun getRefName(): String = reffunc.calledFunction?.topLabel?.labelName ?: ""
    override fun getIntValue(exm: ExpressionMediator): Long = err()
    override fun getStrValue(exm: ExpressionMediator): String = err()
    override fun getValue(exm: ExpressionMediator): SingleTerm = err()
    override fun restructure(exm: ExpressionMediator): IOperandTerm = this
}
