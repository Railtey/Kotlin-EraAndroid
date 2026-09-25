package com.eraandroid.core.parser

// ─── Base Node ───────────────────────────────────────────────────────────────
sealed class AstNode {
    abstract val line: Int
}

// ─── Program ─────────────────────────────────────────────────────────────────
data class Program(
    val functions: List<FunctionDef>,
    val globalDefs: List<AstNode>,
    override val line: Int = 0
) : AstNode()

// ─── Definitions ─────────────────────────────────────────────────────────────
data class FunctionDef(
    val name: String,
    val params: List<ParamDef>,
    val body: List<Statement>,
    val attributes: Set<FunctionAttribute>,
    override val line: Int,
    val fileName: String = ""
) : AstNode()

data class ParamDef(val name: String, val isString: Boolean, override val line: Int) : AstNode()

enum class FunctionAttribute { SINGLE, LATER, ONLY, PRI }

data class DimStatement(
    val name: String,
    val isString: Boolean,
    val sizes: List<Expression>,
    val isConst: Boolean,
    val initialValue: Expression?,
    override val line: Int
) : Statement()

data class DefineStatement(
    val name: String,
    val value: String,
    override val line: Int
) : Statement()

// ─── Statements ──────────────────────────────────────────────────────────────
sealed class Statement : AstNode()

data class AssignStatement(
    val target: Expression,
    val op: String,
    val value: Expression,
    override val line: Int
) : Statement()

data class CallStatement(
    val functionName: Expression,
    val args: List<Expression>,
    override val line: Int,
    val isTry: Boolean = false
) : Statement()

data class ReturnStatement(
    val value: Expression?,
    val isFunctionReturn: Boolean = false,
    val multiValues: List<Expression> = emptyList(),
    override val line: Int
) : Statement()

data class GotoStatement(val label: String, override val line: Int) : Statement()
data class GosubStatement(val label: String, override val line: Int) : Statement()
data class LabelStatement(val name: String, override val line: Int) : Statement()

data class IfStatement(
    val branches: List<IfBranch>,
    val elseBranch: List<Statement>?,
    override val line: Int
) : Statement()

data class IfBranch(val condition: Expression, val body: List<Statement>)

data class SifStatement(
    val condition: Expression,
    val body: Statement,
    override val line: Int
) : Statement()

data class ForStatement(
    val variable: Expression,
    val from: Expression,
    val to: Expression,
    val step: Expression?,
    val body: List<Statement>,
    override val line: Int
) : Statement()

data class WhileStatement(
    val condition: Expression,
    val body: List<Statement>,
    override val line: Int
) : Statement()

data class DoLoopStatement(
    val body: List<Statement>,
    val condition: Expression?,
    val isUntil: Boolean,
    override val line: Int
) : Statement()

data class RepeatStatement(
    val count: Expression,
    val body: List<Statement>,
    override val line: Int
) : Statement()

data class SelectCaseStatement(
    val expr: Expression,
    val cases: List<CaseBranch>,
    val elseBody: List<Statement>?,
    override val line: Int
) : Statement()

data class CaseBranch(val conditions: List<CaseCondition>, val body: List<Statement>)
sealed class CaseCondition
data class CaseValue(val value: Expression) : CaseCondition()
data class CaseRange(val from: Expression, val to: Expression) : CaseCondition()
data class CaseIs(val op: String, val value: Expression) : CaseCondition()

// Print variants
data class PrintStatement(
    val variant: PrintVariant,
    val args: List<Expression>,
    override val line: Int
) : Statement()

enum class PrintVariant {
    PLAIN, LINE, WAIT, DATA, FORM, FORML, VALUE, VALUEL, STRING, STRINGL,
    COLUMN, COLUMN_LINE, COLUMN_FORM, COLUMN_FORML
}

data class InputStatement(
    val variable: Expression?,
    val defaultValue: Expression?,
    val timeOut: Expression?,
    val timeOutDefault: Expression?,
    val isString: Boolean,
    override val line: Int
) : Statement()

data class WaitStatement(val animate: Boolean, override val line: Int) : Statement()
data class TwaitStatement(val time: Expression, val flag: Expression, override val line: Int) : Statement()

data class ButtonStatement(
    val value: Expression,
    val text: Expression,
    override val line: Int,
    val isInline: Boolean = false
) : Statement()

data class SetColorStatement(val r: Expression, val g: Expression, val b: Expression, override val line: Int) : Statement()
data class ResetColorStatement(override val line: Int) : Statement()
data class SetBgColorStatement(val r: Expression, val g: Expression, val b: Expression, override val line: Int) : Statement()
data class ResetBgColorStatement(override val line: Int) : Statement()

data class DrawLineStatement(override val line: Int) : Statement()
data class ClearLineStatement(val count: Expression?, override val line: Int) : Statement()

data class AlignmentStatement(val alignment: Expression, override val line: Int) : Statement()
data class FontStyleStatement(val style: Expression, override val line: Int) : Statement()
data class SetFontStatement(val fontName: Expression, override val line: Int) : Statement()

data class VarSetStatement(
    val variable: Expression,
    val value: Expression?,
    val start: Expression?,
    val count: Expression?,
    override val line: Int
) : Statement()

data class SaveDataStatement(val slot: Expression, val comment: Expression?, override val line: Int) : Statement()
data class LoadDataStatement(val slot: Expression, override val line: Int) : Statement()
data class DelDataStatement(val slot: Expression, override val line: Int) : Statement()
data class ChkDataStatement(val slot: Expression, override val line: Int) : Statement()
data class SaveGlobalStatement(override val line: Int) : Statement()
data class LoadGlobalStatement(override val line: Int) : Statement()

data class AddCharaStatement(val charaNo: Expression, override val line: Int) : Statement()
data class DelCharaStatement(val charaNo: Expression, override val line: Int) : Statement()
data class SwapCharaStatement(val a: Expression, val b: Expression, override val line: Int) : Statement()
data class SortCharaStatement(val variable: Expression, val ascending: Boolean, override val line: Int) : Statement()
data class PickupCharaStatement(val target: Expression, override val line: Int) : Statement()

data class ThrowStatement(val message: Expression, override val line: Int) : Statement()
data class QuitStatement(override val line: Int) : Statement()
data class NopStatement(override val line: Int) : Statement()

data class BeginStatement(val systemFunc: String, override val line: Int) : Statement()
data class CallTrainStatement(override val line: Int) : Statement()

data class DebugPrintStatement(val args: List<Expression>, override val line: Int) : Statement()

data class IncrementStatement(val target: Expression, override val line: Int) : Statement()
data class DecrementStatement(val target: Expression, override val line: Int) : Statement()

data class ExpressionStatement(val expr: Expression, override val line: Int) : Statement()

// ── 추가 노드 ─────────────────────────────────────────────────────────────────
data class BreakStatement(override val line: Int) : Statement()
data class ContinueStatement(override val line: Int) : Statement()

data class InputsStatement(val variable: Expression?, override val line: Int) : Statement()

/** PRINTDATA / PRINTDATAL / PRINTDATAW 블록 */
data class PrintDataStatement(
    val variant: PrintVariant,
    val blocks: List<List<Expression>>,   // DATALIST 하나당 List<Expression>
    override val line: Int
) : Statement()

/** CATCH ... ENDCATCH 블록 */
data class CatchStatement(
    val tryBody: List<Statement>,
    val catchBody: List<Statement>,
    override val line: Int
) : Statement()

data class SwapStatement(val a: Expression, val b: Expression, override val line: Int) : Statement()
data class TimesStatement(val target: Expression, val factor: Expression, override val line: Int) : Statement()

data class SetBitStatement(val target: Expression, val bit: Expression, override val line: Int) : Statement()
data class ClearBitStatement(val target: Expression, val bit: Expression, override val line: Int) : Statement()
data class InvertBitStatement(val target: Expression, val bit: Expression, override val line: Int) : Statement()

data class FindCharaStatement(val variable: Expression, val target: Expression, val index: Expression?, override val line: Int) : Statement()
data class GetCharaStatement(val variable: Expression, val charaNo: Expression, override val line: Int) : Statement()
data class AddDefCharaStatement(val charaNo: Expression, override val line: Int) : Statement()
data class IsAssiStatement(val result: Expression, val charaIndex: Expression, override val line: Int) : Statement()

data class GetPalamLvStatement(val result: Expression, val charaIndex: Expression, val palamIndex: Expression, override val line: Int) : Statement()
data class GetExpLvStatement(val result: Expression, val charaIndex: Expression, val expIndex: Expression, override val line: Int) : Statement()
data class UpCheckStatement(override val line: Int) : Statement()

data class ResetDataStatement(override val line: Int) : Statement()
data class RestartStatement(override val line: Int) : Statement()

data class RedrawStatement(val flag: Expression?, override val line: Int) : Statement()
data class ReuseLastLineStatement(val text: Expression?, override val line: Int) : Statement()
data class PutFormStatement(val text: Expression, override val line: Int) : Statement()
data class CustomDrawLineStatement(val text: Expression, override val line: Int) : Statement()
data class BarStatement(val value: Expression, val max: Expression, val length: Expression, override val line: Int) : Statement()

data class ShuffleStatement(val variable: Expression, override val line: Int) : Statement()
data class SplitCmdStatement(val str: Expression, val delim: Expression, val target: Expression, override val line: Int) : Statement()

data class JumpStatement(val functionName: Expression, val args: List<Expression>, val isTry: Boolean, override val line: Int) : Statement()

// CALLEVENT funcName[, args...] — 이벤트 함수 체인 전체 호출 (없으면 무시)
data class CallEventStatement(
    val functionName: Expression,
    val args: List<Expression>,
    override val line: Int
) : Statement()
data class JumpFormStatement(val rawName: String, val isTry: Boolean, override val line: Int) : Statement()

data class UnicodeStatement(val codePoint: Expression, override val line: Int) : Statement()

// ─── Expressions ─────────────────────────────────────────────────────────────
sealed class Expression : AstNode()

data class IntLiteral(val value: Long, override val line: Int) : Expression()
data class FloatLiteral(val value: Double, override val line: Int) : Expression()
data class StringLiteral(val value: String, override val line: Int) : Expression()
data class FormString(val parts: List<FormStringPart>, override val line: Int) : Expression()

sealed class FormStringPart
data class TextPart(val text: String) : FormStringPart()
data class EmbedPart(val expr: Expression) : FormStringPart()

data class VariableRef(
    val name: String,
    val indices: List<Expression>,
    override val line: Int
) : Expression()

data class BinaryOp(
    val op: String,
    val left: Expression,
    val right: Expression,
    override val line: Int
) : Expression()

data class UnaryOp(val op: String, val expr: Expression, override val line: Int) : Expression()

data class TernaryOp(
    val condition: Expression,
    val trueBranch: Expression,
    val falseBranch: Expression,
    override val line: Int
) : Expression()

data class FunctionCall(
    val name: String,
    val args: List<Expression>,
    override val line: Int
) : Expression()