package com.eraandroid.core.interpreter

import com.eraandroid.core.vm.EraValue

// ─── Engine Events ────────────────────────────────────────────────────────────
sealed class EngineEvent {
    data class Print(val text: String, val newLine: Boolean, val color: Int?, val isBold: Boolean, val isItalic: Boolean) : EngineEvent()
    data class PrintButton(val value: Long, val text: String, val color: Int?, val isInline: Boolean = false) : EngineEvent()
    data class DrawLine(val char: String = "-") : EngineEvent()
    data class ClearLine(val count: Int) : EngineEvent()
    data class WaitForInput(val isNumber: Boolean, val isWait: Boolean = false) : EngineEvent()
    data class SetColor(val color: Int) : EngineEvent()
    data class SetBgColor(val color: Int) : EngineEvent()
    data class Alignment(val align: Int) : EngineEvent() // 0=Left 1=Center 2=Right
    data class FontStyle(val style: Int) : EngineEvent()
    data class SetFont(val fontName: String) : EngineEvent()
    object SaveRequested : EngineEvent()
    object LoadRequested : EngineEvent()
    object LoadGlobalRequested : EngineEvent()
    object SaveGlobalRequested : EngineEvent()
    data class Error(val message: String, val line: Int) : EngineEvent()
    object Quit : EngineEvent()
}

// ─── Engine State ─────────────────────────────────────────────────────────────
enum class EngineState { IDLE, RUNNING, WAITING_INPUT, PAUSED, ERROR, QUIT }

// ─── Control Flow Signals ─────────────────────────────────────────────────────
class ReturnSignal(val value: EraValue?) : Exception()
class GotoSignal(val label: String) : Exception()
class RestartSignal : Exception()
class BreakSignal : Exception()
class ContinueSignal : Exception()
class QuitSignal : Exception()
class BeginException(val target: String) : Exception("BEGIN $target")
class JumpSignal(val functionName: String, val args: List<EraValue>) : Exception()
// TRYCCALL/TRYCCALLFORM 실패 시 CATCH 블록으로 점프하기 위한 신호
class TryCatchSignal : Exception()