package com.eraandroid.core.interpreter

import com.eraandroid.core.parser.*
import com.eraandroid.core.vm.*
import kotlinx.coroutines.*
import kotlin.random.Random
import com.eraandroid.core.interpreter.EraStringUtils

// ─── Statement 실행 / Expression 평가 (EraInterpreter extension) ─────────────
//
// executeStatement, executePrint, executeVarSet, executeAssign,
// evalBinary, evalUnary 및 관련 헬퍼를 별도 파일로 분리.

internal suspend fun EraInterpreter.executeStatement_ext(stmt: Statement) {
    when (stmt) {
        is AssignStatement -> executeAssign_ext(stmt)
        is IncrementStatement -> {
            val current = resolveVar(stmt.target)
            setVar(stmt.target, EraValue.of(current.toLong() + 1))
        }
        is DecrementStatement -> {
            val current = resolveVar(stmt.target)
            setVar(stmt.target, EraValue.of(current.toLong() - 1))
        }
        is CallStatement -> {
            val name = when (val fn = stmt.functionName) {
                is VariableRef -> fn.name
                is StringLiteral -> evalFormString(fn)
                is FunctionCall -> fn.name
                else -> evalExpr(fn).toEraString()
            }
            val callArgs = if (stmt.functionName is FunctionCall) {
                (stmt.functionName as FunctionCall).args.map { evalExpr(it) }
            } else {
                stmt.args.map { evalExpr(it) }
            }
            // Statement 형태로 호출된 빌트인 명령 직접 처리 (RESULT/RESULTS 저장)
            val nameUpper = name.uppercase()
            val builtinHandled = when (nameUpper) {
                "STRLENSU" -> {
                    val s = callArgs.getOrNull(0)?.toEraString() ?: ""
                    val len = s.codePointCount(0, s.length).toLong()
                    scope.getOrCreate("RESULT").set(listOf(0), EraValue.of(len))
                    true
                }
                "STRLENS" -> {
                    val s = callArgs.getOrNull(0)?.toEraString() ?: ""
                    scope.getOrCreate("RESULT").set(listOf(0), EraValue.of(eraByteWidth(s).toLong()))
                    true
                }
                "SUBSTRINGU" -> {
                    val s = callArgs.getOrNull(0)?.toEraString() ?: ""
                    val startCp = callArgs.getOrNull(1)?.toLong()?.toInt() ?: 0
                    val lenCp = callArgs.getOrNull(2)?.toLong()?.toInt() ?: -1
                    val codePoints = s.codePoints().toArray()
                    val sliced = if (lenCp < 0) codePoints.drop(startCp)
                    else codePoints.drop(startCp).take(lenCp)
                    val result = String(sliced.toIntArray(), 0, sliced.size)
                    scope.getOrCreate("RESULTS", true, listOf(100)).set(listOf(0), EraValue.of(result))
                    true
                }
                "SUBSTRING" -> {
                    val s = callArgs.getOrNull(0)?.toEraString() ?: ""
                    val start = callArgs.getOrNull(1)?.toLong()?.toInt() ?: 0
                    val len = callArgs.getOrNull(2)?.toLong()?.toInt() ?: -1
                    val result = if (len < 0) s.drop(start) else s.drop(start).take(len)
                    scope.getOrCreate("RESULTS", true, listOf(100)).set(listOf(0), EraValue.of(result))
                    true
                }
                "STRLENFORM" -> {
                    val s = callArgs.getOrNull(0)?.toEraString() ?: ""
                    scope.getOrCreate("RESULT").set(listOf(0), EraValue.of(s.length.toLong()))
                    true
                }
                // GETBIT VARNAME, BIT (Statement) → RESULT에 비트값 저장
                "__GETBIT__" -> {
                    val v   = callArgs.getOrNull(0)?.toLong() ?: 0L
                    val bit = callArgs.getOrNull(1)?.toLong()?.toInt() ?: 0
                    val res = (v shr bit) and 1L
                    scope.getOrCreate("RESULT").set(listOf(0), EraValue.of(res))
                    true
                }
                // CVARSET VARNAME, INDEX, VALUE → 모든 캐릭터의 해당 charaVar 인덱스에 값 설정
                "__CVARSET__" -> {
                    val vname = callArgs.getOrNull(0)?.toEraString()?.uppercase() ?: ""
                    val idx   = callArgs.getOrNull(1)?.toLong()?.toInt() ?: 0
                    val value = callArgs.getOrNull(2)?.toLong() ?: 0L
                    val isStr = vname in setOf("CSTR","NAME","CALLNAME","NICKNAME","MASTERNAME")
                    val charaStringVars = setOf("CSTR","NAME","CALLNAME","NICKNAME","MASTERNAME")
                    val charaNumVars = setOf(
                        "BASE","MAXBASE","DOWNBASE","LOSEBASE","TCVAR","SOURCE","EX","CUP","CDOWN",
                        "ABL","TALENT","EXP","MARK","PALAM","JUEL","GOTJUEL","RELATION","CFLAG"
                    )
                    if (vname in charaNumVars || vname in charaStringVars) {
                        // 모든 캐릭터에 적용
                        for (ci in 0 until charaManager.count) {
                            val chara = charaManager.getChara(ci) ?: continue
                            if (isStr) chara.setStr(vname, idx, value.toString())
                            else chara.setNum(vname, idx, value)
                        }
                    } else {
                        // 글로벌 변수에 적용
                        val v = scope.getOrCreate(vname, isStr, listOf(10000))
                        v.set(listOf(idx), if (isStr) EraValue.of(value.toString()) else EraValue.of(value))
                    }
                    true
                }
                "__ADDCOPYCHARA__" -> {
                    val no = callArgs.getOrNull(0)?.toLong()?.toInt() ?: 0
                    charaManager.addCopyChara(no)
                    scope.getOrCreate("CHARANUM").set(emptyList(), EraValue.of(charaManager.count.toLong()))
                    true
                }
                "__RESET_STAIN__" -> {
                    val charaIdx = callArgs.getOrNull(0)?.toLong()?.toInt() ?: 0
                    charaManager.getChara(charaIdx)?.let { chara ->
                        for (i in 0 until 100) chara.setNum("STAIN", i, 0L)
                    }
                    true
                }
                "__ARRAYSHIFT__" -> {
                    val shift    = callArgs.getOrNull(1)?.toLong()?.toInt() ?: 0
                    val defVal   = callArgs.getOrNull(2) ?: EraValue.ZERO
                    val start    = callArgs.getOrNull(3)?.toLong()?.toInt() ?: 0
                    val countArg = callArgs.getOrNull(4)?.toLong()?.toInt() ?: -1
                    val firstExpr = stmt.args.getOrNull(0)
                    val variable = if (firstExpr is VariableRef) resolveVarRef(firstExpr) else null
                    if (variable != null) {
                        val all = variable.getAll().toMutableList()
                        val sz = all.size
                        val s = start.coerceIn(0, sz)
                        val cnt = if (countArg < 0) sz - s else countArg.coerceAtMost(sz - s)
                        val temp = all.subList(s, (s + cnt).coerceAtMost(sz)).toMutableList()
                        when {
                            shift > 0 -> {
                                val moved = temp.drop(shift.coerceAtMost(cnt)).toMutableList()
                                repeat(shift.coerceAtMost(cnt)) { moved.add(defVal) }
                                for (i in 0 until cnt) all[s + i] = moved.getOrElse(i) { defVal }
                            }
                            shift < 0 -> {
                                val abs = (-shift).coerceAtMost(cnt)
                                val moved = MutableList(abs) { defVal }
                                moved.addAll(temp.take(cnt - abs))
                                for (i in 0 until cnt) all[s + i] = moved.getOrElse(i) { defVal }
                            }
                        }
                        all.forEachIndexed { i, v -> variable.set(listOf(i), v) }
                    }
                    true
                }
                "__ARRAYREMOVE__" -> {
                    val start    = callArgs.getOrNull(1)?.toLong()?.toInt() ?: 0
                    val countArg = callArgs.getOrNull(2)?.toLong()?.toInt() ?: -1
                    val firstExpr = stmt.args.getOrNull(0)
                    val variable = if (firstExpr is VariableRef) resolveVarRef(firstExpr) else null
                    if (variable != null) {
                        val all = variable.getAll().toMutableList()
                        val sz = all.size
                        val s = start.coerceIn(0, sz)
                        val cnt = if (countArg <= 0) sz - s else countArg.coerceAtMost(sz - s)
                        val empty = if (variable.isString) EraValue.EMPTY_STRING else EraValue.ZERO
                        for (i in s until sz - cnt) all[i] = all[i + cnt]
                        for (i in sz - cnt until sz) all[i] = empty
                        all.forEachIndexed { i, v -> variable.set(listOf(i), v) }
                    }
                    true
                }
                "GETTIME" -> {
                    val now = java.util.Calendar.getInstance()
                    var date = now.get(java.util.Calendar.YEAR).toLong()
                    date = date * 100 + (now.get(java.util.Calendar.MONTH) + 1)
                    date = date * 100 + now.get(java.util.Calendar.DAY_OF_MONTH)
                    date = date * 100 + now.get(java.util.Calendar.HOUR_OF_DAY)
                    date = date * 100 + now.get(java.util.Calendar.MINUTE)
                    date = date * 100 + now.get(java.util.Calendar.SECOND)
                    date = date * 1000 + now.get(java.util.Calendar.MILLISECOND)
                    val ds = String.format("%04d/%02d/%02d %02d:%02d:%02d",
                        now.get(java.util.Calendar.YEAR), now.get(java.util.Calendar.MONTH)+1,
                        now.get(java.util.Calendar.DAY_OF_MONTH), now.get(java.util.Calendar.HOUR_OF_DAY),
                        now.get(java.util.Calendar.MINUTE), now.get(java.util.Calendar.SECOND))
                    scope.getOrCreate("RESULT").set(listOf(0), EraValue.of(date))
                    scope.getOrCreate("RESULTS", true, listOf(100)).set(listOf(0), EraValue.of(ds))
                    true
                }
                "GETPALAMLV" -> {
                    val curVal = callArgs.getOrNull(0)?.toLong() ?: 0L
                    val maxLv  = callArgs.getOrNull(1)?.toLong()?.toInt() ?: 100
                    scope.getOrCreate("RESULT").set(listOf(0), EraValue.of(calcPalamLv(curVal, maxLv, "PALAMLV")))
                    true
                }
                "GETEXPLV" -> {
                    val curVal = callArgs.getOrNull(0)?.toLong() ?: 0L
                    val maxLv  = callArgs.getOrNull(1)?.toLong()?.toInt() ?: 100
                    scope.getOrCreate("RESULT").set(listOf(0), EraValue.of(calcPalamLv(curVal, maxLv, "EXPLV")))
                    true
                }
                "VARSIZE" -> {
                    val varName = callArgs.getOrNull(0)?.toEraString()?.uppercase() ?: ""
                    val variable = scope.get(varName)
                    if (variable != null) {
                        scope.getOrCreate("RESULT").set(listOf(0), EraValue.of(variable.dimensions.getOrElse(0){0}.toLong()))
                        scope.getOrCreate("RESULT").set(listOf(1), EraValue.of(variable.dimensions.getOrElse(1){0}.toLong()))
                    }
                    true
                }
                "GETCONFIG" -> {
                    val cfgName = callArgs.getOrNull(0)?.toEraString()?.uppercase() ?: ""
                    val v = when (cfgName) { "PRINTCPERLINE" -> PRINTC_COLS.toLong(); "PRINTCLENGTH" -> PRINTC_WIDTH.toLong(); "WINDOWX", "WINDOWWIDTH" -> 800L; "WINDOWY", "WINDOWHEIGHT" -> 600L; "FONTSIZE" -> 18L; else -> 0L }
                    scope.getOrCreate("RESULT").set(listOf(0), EraValue.of(v))
                    true
                }
                "GETCONFIGS" -> {
                    val cfgName = callArgs.getOrNull(0)?.toEraString()?.uppercase() ?: ""
                    val v = when (cfgName) { "FONTNAME" -> "MS Gothic"; else -> "" }
                    scope.getOrCreate("RESULTS", true, listOf(100)).set(listOf(0), EraValue.of(v))
                    true
                }
                "UNICODE" -> {
                    val cp = callArgs.getOrNull(0)?.toLong()?.toInt() ?: 0
                    val ch = try { String(Character.toChars(cp)) } catch (e: Exception) { "" }
                    scope.getOrCreate("RESULTS", true, listOf(100)).set(listOf(0), EraValue.of(ch))
                    true
                }
                // ── CSV계 빌트인 (캐릭터 CSV 원본 데이터 조회) ──────────────────────────
                // CSVNAME/CSVCALLNAME/CSVNICKNAME/CSVMASTERNAME:
                //   arg0 = 캐릭터 번호(NO), arg1 = 인덱스(보통 0)
                //   결과를 RESULTS:0 에 저장
                "CSVNAME" -> {
                    val no  = callArgs.getOrNull(0)?.toLong()?.toInt() ?: 0
                    val idx = callArgs.getOrNull(1)?.toLong()?.toInt() ?: 0
                    val v = charaManager.getCharaOrTemplate(no)?.getStr("NAME", idx) ?: ""
                    scope.getOrCreate("RESULTS", true, listOf(100)).set(listOf(0), EraValue.of(v))
                    true
                }
                "CSVCALLNAME" -> {
                    val no  = callArgs.getOrNull(0)?.toLong()?.toInt() ?: 0
                    val idx = callArgs.getOrNull(1)?.toLong()?.toInt() ?: 0
                    val v = charaManager.getCharaOrTemplate(no)?.getStr("CALLNAME", idx) ?: ""
                    scope.getOrCreate("RESULTS", true, listOf(100)).set(listOf(0), EraValue.of(v))
                    true
                }
                "CSVNICKNAME" -> {
                    val no  = callArgs.getOrNull(0)?.toLong()?.toInt() ?: 0
                    val idx = callArgs.getOrNull(1)?.toLong()?.toInt() ?: 0
                    val v = charaManager.getCharaOrTemplate(no)?.getStr("NICKNAME", idx) ?: ""
                    scope.getOrCreate("RESULTS", true, listOf(100)).set(listOf(0), EraValue.of(v))
                    true
                }
                "CSVMASTERNAME" -> {
                    val no  = callArgs.getOrNull(0)?.toLong()?.toInt() ?: 0
                    val idx = callArgs.getOrNull(1)?.toLong()?.toInt() ?: 0
                    val v = charaManager.getCharaOrTemplate(no)?.getStr("MASTERNAME", idx) ?: ""
                    scope.getOrCreate("RESULTS", true, listOf(100)).set(listOf(0), EraValue.of(v))
                    true
                }
                // EXISTCSV(charaNo, 0) → RESULT = 1 if template exists, 0 otherwise
                "EXISTCSV" -> {
                    val no  = callArgs.getOrNull(0)?.toLong()?.toInt() ?: 0
                    val exists = if (charaManager.getCharaOrTemplate(no) != null) 1L else 0L
                    scope.getOrCreate("RESULT").set(listOf(0), EraValue.of(exists))
                    true
                }
                else -> false
            }
            if (!builtinHandled) {
                if (stmt.isTry) {
                    // 함수 존재 여부 먼저 확인
                    if (!functions.containsKey(name.uppercase())) {
                        // TRYCCALL: 함수 없음 → CATCH 블록으로
                        throw TryCatchSignal()
                    }
                    try {
                        callFunction(name, callArgs)
                    } catch (e: EraRuntimeException) {
                        // TRYCALL: 함수 실행 중 에러 → TryCatchSignal로 CATCH 블록 점프
                        throw TryCatchSignal()
                    }
                } else {
                    callFunction(name, callArgs)
                }
            }
        }
        is ReturnStatement -> {
            if (stmt.multiValues.isNotEmpty()) {
                for (i in stmt.multiValues.indices) {
                    scope.getOrCreate("RESULT").set(listOf(i), evalExpr(stmt.multiValues[i]))
                }
                throw ReturnSignal(evalExpr(stmt.multiValues[0]))
            }
            val value = if (stmt.isFunctionReturn && stmt.value is StringLiteral) {
                // RETURNF @"..." 형식: raw 문자열을 즉시 evalFormString으로 평가
                // 그렇지 않으면 STR:2 등에 raw 텍스트가 저장되고 나중에 출력 시
                // 다른 callStack 컨텍스트에서 ARG 등이 0으로 읽히는 버그 발생
                EraValue.of(evalFormString(stmt.value))
            } else {
                stmt.value?.let { evalExpr(it) }
            }
            throw ReturnSignal(value)
        }
        is GotoStatement -> {
            if (stmt.label.uppercase() == "__RESTART__") throw RestartSignal()
            throw GotoSignal(stmt.label)
        }
        is GosubStatement -> {
            callFunction(stmt.label, emptyList())
        }
        is LabelStatement -> { /* handled by executeStatements */ }
        is IfStatement -> executeIf_ext(stmt)
        is SifStatement -> {
            if (evalExpr(stmt.condition).toLong() != 0L) executeStatement_ext(stmt.body)
        }
        is ForStatement -> executeFor_ext(stmt)
        is WhileStatement -> executeWhile_ext(stmt)
        is DoLoopStatement -> executeDoLoop_ext(stmt)
        is RepeatStatement -> executeRepeat_ext(stmt)
        is SelectCaseStatement -> executeSelectCase_ext(stmt)
        is PrintStatement -> executePrint_ext(stmt)
        is InputStatement -> executeInput_ext(stmt)
        is WaitStatement -> {
            // WAIT: 사용자 확인 대기
            if (printcBuffer.isNotEmpty()) flushPrintcBuffer_ext()
            emit(EngineEvent.WaitForInput(isNumber = false, isWait = true))
            waitForInput(false)
        }
        is TwaitStatement -> {
            val time = evalExpr(stmt.time).toLong()
            delay(minOf(time, 100L)) // 최대 100ms만 대기
        }
        is ButtonStatement -> {
            val value = evalExpr(stmt.value).toLong()
            val text = evalFormString(stmt.text)
            if (stmt.isInline) {
                printcBuffer.add(value to text)
                if (printcBuffer.size >= PRINTC_COLS) flushPrintcBuffer_ext()
            } else {
                if (printcBuffer.isNotEmpty()) flushPrintcBuffer_ext()
                emit(EngineEvent.PrintButton(value, text, currentColor))
            }
        }
        is SetColorStatement -> {
            val r = evalExpr(stmt.r).toLong().toInt()
            val g = evalExpr(stmt.g).toLong().toInt()
            val b = evalExpr(stmt.b).toLong().toInt()
            currentColor = (0xFF shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)
            emit(EngineEvent.SetColor(currentColor!!))
        }
        is ResetColorStatement -> { currentColor = null; emit(EngineEvent.SetColor(0xFFFFFFFF.toInt())) }
        is SetBgColorStatement -> {
            val r = evalExpr(stmt.r).toLong().toInt()
            val g = evalExpr(stmt.g).toLong().toInt()
            val b = evalExpr(stmt.b).toLong().toInt()
            currentBgColor = (0xFF shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)
            emit(EngineEvent.SetBgColor(currentBgColor!!))
        }
        is ResetBgColorStatement -> { currentBgColor = null; emit(EngineEvent.SetBgColor(0xFF000000.toInt())) }
        is DrawLineStatement -> {
            if (printcBuffer.isNotEmpty()) flushPrintcBuffer_ext()
            emit(EngineEvent.DrawLine())
        }
        is ClearLineStatement -> {
            val count = stmt.count?.let { evalExpr(it).toLong().toInt() } ?: 1
            emit(EngineEvent.ClearLine(count))
        }
        is AlignmentStatement -> {
            val alignStr = (stmt.alignment as? VariableRef)?.name?.uppercase()
                ?: evalExpr(stmt.alignment).toEraString().uppercase()
            alignment = when (alignStr) {
                "CENTER" -> 1
                "RIGHT" -> 2
                else -> 0  // LEFT
            }
            emit(EngineEvent.Alignment(alignment))
        }
        is FontStyleStatement -> {
            fontStyle = evalExpr(stmt.style).toLong().toInt()
            emit(EngineEvent.FontStyle(fontStyle))
        }
        is SetFontStatement -> {
            currentFont = evalExpr(stmt.fontName).toEraString()
            emit(EngineEvent.SetFont(currentFont))
        }
        is VarSetStatement -> executeVarSet_ext(stmt)
        is SaveDataStatement -> {
            val slot = evalExpr(stmt.slot).toLong().toInt()
            val comment = stmt.comment?.let { evalExpr(it).toEraString() } ?: ""
            scope.getOrCreate("SAVEDATA_SLOT").set(emptyList(), EraValue.of(slot.toLong()))
            scope.getOrCreate("SAVEDATA_COMMENT", true, listOf(1)).set(listOf(0), EraValue.of(comment))
            scope.getOrCreate("__PENDING_SAV").set(emptyList(), EraValue.of(1L))
            emit(EngineEvent.SaveRequested)
        }
        // Interpreter.kt - LoadDataStatement 처리
        is LoadDataStatement -> {
            val slot = evalExpr(stmt.slot).toLong().toInt()
            scope.getOrCreate("LOADDATA_SLOT").set(emptyList(), EraValue.of(slot.toLong()))
            val deferred = CompletableDeferred<Boolean>()
            pendingLoad = deferred
            emit(EngineEvent.LoadRequested)
            val success = deferred.await()
            if (success) throw BeginException("LOAD")  // 로드 성공 시 SHOP(메인 화면)으로 강제 전환
        }
        is DelDataStatement -> {
            val slot = evalExpr(stmt.slot).toLong().toInt()
            scope.getOrCreate("DELDATA_SLOT").set(emptyList(), EraValue.of(slot.toLong()))
            scope.getOrCreate("__PENDING_DEL").set(emptyList(), EraValue.of(1L))
            emit(EngineEvent.SaveRequested) // ViewModel에서 실제 삭제 처리
        }
        is ChkDataStatement -> {
            val slot = evalExpr(stmt.slot).toLong().toInt()
            scope.getOrCreate("CHKDATA_SLOT").set(emptyList(), EraValue.of(slot.toLong()))
            scope.getOrCreate("__PENDING_CHK").set(emptyList(), EraValue.of(1L))
            scope.getOrCreate("RESULT").set(listOf(0), EraValue.ZERO)
            val deferred = CompletableDeferred<Unit>()
            pendingSaveOp = deferred
            emit(EngineEvent.SaveRequested)
            deferred.await()  // ViewModel이 처리 완료할 때까지 대기
        }
        is SaveGlobalStatement -> emit(EngineEvent.SaveGlobalRequested)
        is LoadGlobalStatement -> emit(EngineEvent.LoadGlobalRequested)
        is AddCharaStatement -> {
            val no = evalExpr(stmt.charaNo).toLong().toInt()
            charaManager.addChara(no)
            scope.getOrCreate("CHARANUM").set(emptyList(), EraValue.of(charaManager.count.toLong()))
        }
        is DelCharaStatement -> {
            // emuera 원본: DELCHARA는 배열 인덱스 기반 (no 기반 아님)
            val index = evalExpr(stmt.charaNo).toLong().toInt()
            charaManager.delChara(index)
            scope.getOrCreate("CHARANUM").set(emptyList(), EraValue.of(charaManager.count.toLong()))
        }
        is SwapCharaStatement -> {
            val a = evalExpr(stmt.a).toLong().toInt()
            val b = evalExpr(stmt.b).toLong().toInt()
            charaManager.swapChara(a, b)
        }
        is SortCharaStatement -> {
            val varName = (stmt.variable as? VariableRef)?.name ?: "NO"
            val masterNo = charaManager.getChara(scope.getOrCreate("MASTER").get(listOf(0)).toLong().toInt())?.no
            val targetNo = charaManager.getChara(scope.getOrCreate("TARGET").get(listOf(0)).toLong().toInt())?.no
            val assiNo   = charaManager.getChara(scope.getOrCreate("ASSI").get(listOf(0)).toLong().toInt())?.no
            charaManager.sortChara(varName, ascending = stmt.ascending)
            masterNo?.let { no -> val idx = charaManager.charas.indexOfFirst { it.no == no }; if (idx >= 0) scope.getOrCreate("MASTER").set(listOf(0), EraValue.of(idx.toLong())) }
            targetNo?.let { no -> val idx = charaManager.charas.indexOfFirst { it.no == no }; if (idx >= 0) scope.getOrCreate("TARGET").set(listOf(0), EraValue.of(idx.toLong())) }
            assiNo?.let   { no -> val idx = charaManager.charas.indexOfFirst { it.no == no }; if (idx >= 0) scope.getOrCreate("ASSI").set(listOf(0), EraValue.of(idx.toLong())) }
        }
        is PickupCharaStatement -> {
            val target = evalExpr(stmt.target).toLong()
            val masterNo = charaManager.getChara(scope.getOrCreate("MASTER").get(listOf(0)).toLong().toInt())?.no
            val targetNo = charaManager.getChara(scope.getOrCreate("TARGET").get(listOf(0)).toLong().toInt())?.no
            val assiNo   = charaManager.getChara(scope.getOrCreate("ASSI").get(listOf(0)).toLong().toInt())?.no
            charaManager.pickupChara(target)
            masterNo?.let { no -> val idx = charaManager.charas.indexOfFirst { it.no == no }; if (idx >= 0) scope.getOrCreate("MASTER").set(listOf(0), EraValue.of(idx.toLong())) }
            targetNo?.let { no -> val idx = charaManager.charas.indexOfFirst { it.no == no }; if (idx >= 0) scope.getOrCreate("TARGET").set(listOf(0), EraValue.of(idx.toLong())) }
            assiNo?.let   { no -> val idx = charaManager.charas.indexOfFirst { it.no == no }; if (idx >= 0) scope.getOrCreate("ASSI").set(listOf(0), EraValue.of(idx.toLong())) }
        }
        is ThrowStatement -> {
            val msg = evalExpr(stmt.message).toEraString()
            throw EraRuntimeException("THROW: $msg", stmt.line)
        }
        is QuitStatement -> throw QuitSignal()
        is NopStatement -> { /* no-op */ }
        is ExpressionStatement -> {
            android.util.Log.d("ERA_EXPR", "ExpressionStatement: ${stmt.expr}")
            // REUSELASTLINE 등 미구현 명령어 무시
            try { evalExpr(stmt.expr) } catch (e: Exception) { }
        }
        is BeginStatement -> throw BeginException(stmt.systemFunc.uppercase())

        is CallTrainStatement -> {
            // emuera 내장 CALCTRAIN: SOURCE → PALAM 반영, DOWNBASE → BASE 감소
            val target = scope.getOrCreate("TARGET").get(listOf(0)).toLong().toInt()
            val chara = charaManager.getChara(target)
            if (chara != null) {
                // SOURCE → UP 에 누적
                val sourceVar = scope.get("SOURCE")
                val upVar = scope.getOrCreate("UP", false, listOf(1000))
                if (sourceVar != null) {
                    for (i in 0 until 100) {
                        val src = sourceVar.get(listOf(i)).toLong()
                        if (src != 0L) {
                            val cur = upVar.get(listOf(i)).toLong()
                            upVar.set(listOf(i), EraValue.of(cur + src))
                        }
                    }
                }
                // DOWNBASE → BASE 감소
                val downbaseVar = scope.get("DOWNBASE")
                if (downbaseVar != null) {
                    val hp = downbaseVar.get(listOf(0)).toLong()
                    val mp = downbaseVar.get(listOf(1)).toLong()
                    if (hp != 0L) chara.setNum("BASE", 0, maxOf(0L, chara.getNum("BASE", 0) - hp))
                    if (mp != 0L) chara.setNum("BASE", 1, maxOf(0L, chara.getNum("BASE", 1) - mp))
                }
            }
            callFunction("CALLTRAINEND", emptyList())
        }
        is DebugPrintStatement -> {
            val parts = mutableListOf<String>()
            for (arg in stmt.args) parts.add(evalExpr(arg).toEraString())
            val text = parts.joinToString(" ")
            emit(EngineEvent.Print("[DEBUG] $text", true, 0xFF888888.toInt(), false, false))
        }
        is DimStatement -> {
            val sizes = mutableListOf<Int>()
            for (sizeExpr in stmt.sizes) sizes.add(evalExpr(sizeExpr).toLong().toInt())
            val frame = callStack.lastOrNull()
            val targetScope = if (frame != null) frame.locals else scope
            if (targetScope.get(stmt.name) == null) {
                val newVar = EraVariable(stmt.name.uppercase(), stmt.isString, sizes, stmt.isConst)
                targetScope.define(stmt.name, newVar)
                if (stmt.initialValue != null) {
                    try { newVar.setAll(evalExpr(stmt.initialValue)) } catch (e: Exception) {}
                }
            }
        }
        is DefineStatement -> scope.setDefine(stmt.name, stmt.value)

        // ── 루프 제어 ──────────────────────────────────────────────────────
        is BreakStatement    -> throw BreakSignal()
        is ContinueStatement -> throw ContinueSignal()

        // ── 문자열 입력 ────────────────────────────────────────────────────
        is InputsStatement -> {
            scope.getOrCreate("RESULTS", true, listOf(100)).set(listOf(0), EraValue.EMPTY_STRING)
            val input = waitForInput(false)
            val inputVal = EraValue.of(input)
            scope.getOrCreate("RESULTS", true, listOf(100)).set(listOf(0), inputVal)
            // 명시적 변수 지정이 있으면 해당 변수에도 저장 (e.g. INPUTS LOCALS)
            if (stmt.variable != null) {
                setVar(stmt.variable, inputVal)
            } else {
                // 기본: 현재 콜 프레임의 LOCALS:0에도 저장
                val frame = callStack.lastOrNull()
                if (frame != null) {
                    frame.locals.getOrCreate("LOCALS", true, listOf(1000))
                        .set(listOf(0), inputVal)
                }
            }
        }

        // ── PRINTDATA 블록: DATALIST 중 하나를 랜덤 선택해 출력 ─────────────
        is PrintDataStatement -> {
            if (stmt.blocks.isNotEmpty()) {
                val chosen = stmt.blocks[Random.nextInt(stmt.blocks.size)]
                val sb = StringBuilder()
                for (expr in chosen) sb.append(evalFormString(expr))
                val newLine = stmt.variant in listOf(PrintVariant.LINE, PrintVariant.WAIT)
                emit(EngineEvent.Print(sb.toString(), newLine, currentColor, (fontStyle and 1) != 0, (fontStyle and 2) != 0))
            }
        }

        // ── CATCH 블록: THROW 없이 도달하면 catchBody는 건너뜀 ─────────────
        is CatchStatement -> {
            // tryBody가 비어있으므로 catchBody 실행 안 함 (THROW가 없었던 경우)
            // 실제 THROW 처리는 callFunction 내 try-catch에서 수행됨
        }

        // ── 변수 조작 ──────────────────────────────────────────────────────
        is SwapStatement -> {
            val aVal = resolveVar(stmt.a)
            val bVal = resolveVar(stmt.b)
            setVar(stmt.a, bVal)
            setVar(stmt.b, aVal)
        }
        is TimesStatement -> {
            val cur = resolveVar(stmt.target).toLong()
            val factor = evalExpr(stmt.factor).toDouble()
            // emuera 원본: 버림(truncate) 처리
            setVar(stmt.target, EraValue.of((cur * factor).toLong()))
        }
        is SetBitStatement -> {
            val cur = resolveVar(stmt.target).toLong()
            val bit = evalExpr(stmt.bit).toLong().toInt()
            setVar(stmt.target, EraValue.of(cur or (1L shl bit)))
        }
        is ClearBitStatement -> {
            val cur = resolveVar(stmt.target).toLong()
            val bit = evalExpr(stmt.bit).toLong().toInt()
            setVar(stmt.target, EraValue.of(cur and (1L shl bit).inv()))
        }
        is InvertBitStatement -> {
            val cur = resolveVar(stmt.target).toLong()
            val bit = evalExpr(stmt.bit).toLong().toInt()
            setVar(stmt.target, EraValue.of(cur xor (1L shl bit)))
        }

        // ── 캐릭터 ────────────────────────────────────────────────────────
        is FindCharaStatement -> {
            val targetVal = evalExpr(stmt.target).toLong().toInt()
            val idx = stmt.index?.let { evalExpr(it).toLong().toInt() } ?: 0
            // stmt.variable에 해당하는 변수명과 인덱스를 기준으로 검색
            val varRef = stmt.variable as? VariableRef
            val varName = varRef?.name?.uppercase() ?: "NO"
            val found = charaManager.charas.indexOfFirst { chara ->
                chara.getNum(varName, idx) == targetVal.toLong()
            }
            scope.getOrCreate("RESULT").set(listOf(0), EraValue.of(found.toLong()))
        }
        is GetCharaStatement -> {
            // GETCHARA charaNo → 해당 캐릭터의 charas 인덱스를 RESULT에 저장 (-1이면 없음)
            val charaNo = evalExpr(stmt.charaNo).toLong().toInt()
            val idx = charaManager.charas.indexOfFirst { it.no == charaNo }
            scope.getOrCreate("RESULT").set(listOf(0), EraValue.of(idx.toLong()))
        }
        is AddDefCharaStatement -> {
            val no = evalExpr(stmt.charaNo).toLong().toInt()
            if (no < 0) {
                // 인수 없는 ADDDEFCHARA: 모든 템플릿 추가 (emuera 원본 동작)
                charaManager.addAllTemplates()
            } else {
                charaManager.addChara(no)
            }
            // emuera 원본 동작: ADDDEFCHARA 후 MASTER = 0 자동 설정
            // 미설정 시 CSTR:MASTER:1("보통 집"), BASE:MASTER:0(체력) 등이 빈값으로 읽힘
            scope.getOrCreate("MASTER").set(listOf(0), EraValue.of(0L))
        }
        is IsAssiStatement -> {
            val charaIdx = evalExpr(stmt.charaIndex).toLong().toInt()
            val chara = charaManager.getChara(charaIdx)
            val isAssi = chara != null && chara.getNum("ASSI") != 0L
            setVar(stmt.result, EraValue.of(if (isAssi) 1L else 0L))
        }
        is GetPalamLvStatement -> {
            val curVal = try { resolveVar(stmt.result).toLong() } catch (e: Exception) { 0L }
            scope.getOrCreate("RESULT").set(listOf(0), EraValue.of(calcPalamLv(curVal, 100, "PALAMLV")))
        }
        is GetExpLvStatement -> {
            val curVal = try { resolveVar(stmt.result).toLong() } catch (e: Exception) { 0L }
            scope.getOrCreate("RESULT").set(listOf(0), EraValue.of(calcPalamLv(curVal, 100, "EXPLV")))
        }
        is UpCheckStatement -> {
            // UPCHECK 내장: UP/DOWN → PALAM 반영 + 변화량 출력 (emuera 원본 동작)
            val target = scope.getOrCreate("TARGET").get(listOf(0)).toLong().toInt()
            val up   = scope.get("UP")   ?: scope.getOrCreate("UP",   false, listOf(1000))
            val down = scope.get("DOWN") ?: scope.getOrCreate("DOWN", false, listOf(1000))
            val palamNameVar = scope.get("PALAMNAME")
            charaManager.getChara(target)?.let { chara ->
                for (i in 0 until 1000) {
                    val u = up.get(listOf(i)).toLong()
                    val d = down.get(listOf(i)).toLong()
                    if (u != 0L || d != 0L) {
                        val cur  = chara.getNum("PALAM", i)
                        val next = (cur + u - d).coerceAtLeast(0L)
                        chara.setNum("PALAM", i, next)
                        up.set(listOf(i), EraValue.ZERO)
                        down.set(listOf(i), EraValue.ZERO)
                        if (u > 0L) {
                            val pname = palamNameVar?.get(listOf(i))?.toEraString()?.trim() ?: ""
                            if (pname.isNotEmpty()) {
                                emit(EngineEvent.Print("$pname $cur+$u=$next", true, currentColor, false, false))
                            }
                        }
                    }
                }
            }
        }

        // ── 리셋/재시작 ────────────────────────────────────────────────────
        is ResetDataStatement -> {
            // RESETDATA 대상에서 제외할 정적 CSV 데이터 백업
            val globalBackup = scope.get("GLOBAL")?.getAll()?.toList()
            val saveStrBackup = scope.get("SAVESTR")?.getAll()?.toList()
            // PALAMLV / EXPLV: _Replace.csv에서 로드된 레벨 테이블
            // RESETDATA 후 scope 초기화로 0이 되면 COM_ABLE 조건 판정이 전부 잘못됨
            val palamLvBackup = scope.get("PALAMLV")?.getAll()?.toList()
            val expLvBackup   = scope.get("EXPLV")?.getAll()?.toList()
            // csvStaticSnapshot이 있으면 그것을 사용, 없으면 현재 scope에서 백업
            val staticBackup: Map<String, List<com.eraandroid.core.vm.EraValue>> = if (csvStaticSnapshot.isNotEmpty()) {
                android.util.Log.d("ERA_RESET", "RESETDATA: csvStaticSnapshot 사용 ITEMPRICE:0=${csvStaticSnapshot["ITEMPRICE"]?.getOrNull(0)?.toLong()} ITEMPRICE:101=${csvStaticSnapshot["ITEMPRICE"]?.getOrNull(101)?.toLong()}")
                csvStaticSnapshot
            } else {
                android.util.Log.w("ERA_RESET", "RESETDATA: csvStaticSnapshot 없음, scope에서 백업")
                staticVarNames.mapNotNull { name ->
                    scope.get(name)?.let { v -> name to v.getAll().toList() }
                }.toMap()
            }
            scope.restore(BuiltinVariables.createDefaultScope().snapshot())
            globalBackup?.forEachIndexed { i, v -> scope.getOrCreate("GLOBAL").set(listOf(i), v) }
            saveStrBackup?.forEachIndexed { i, v -> scope.getOrCreate("SAVESTR", true, listOf(1000)).set(listOf(i), v) }
            // PALAMLV / EXPLV 복구 (0으로 초기화되면 COM_ABLE 조건 판정 오작동)
            palamLvBackup?.forEachIndexed { i, v -> scope.getOrCreate("PALAMLV", false, listOf(1000)).set(listOf(i), v) }
            expLvBackup?.forEachIndexed { i, v -> scope.getOrCreate("EXPLV", false, listOf(1000)).set(listOf(i), v) }
            // 정적 CSV 데이터 복구
            staticBackup.forEach { (name, values) ->
                val isStr = name == "ITEMNAME" || name.endsWith("NAME")
                val v = scope.getOrCreate(name, isStr, listOf(values.size))
                values.forEachIndexed { i, value -> v.set(listOf(i), value) }
            }
            charaManager.charas.clear()
        }
        is RestartStatement -> throw RestartSignal()

        // ── 출력 제어 ──────────────────────────────────────────────────────
        is RedrawStatement -> { /* Android에서는 자동 갱신 — 무시 */ }
        is ReuseLastLineStatement -> {
            val text = stmt.text?.let { evalFormString(it) } ?: ""
            emit(EngineEvent.ClearLine(1))
            if (text.isNotEmpty()) emit(EngineEvent.Print(text, false, currentColor, false, false))
        }
        is PutFormStatement -> {
            val text = evalFormString(stmt.text)
            emit(EngineEvent.Print(text, false, currentColor, (fontStyle and 1) != 0, (fontStyle and 2) != 0))
        }
        is CustomDrawLineStatement -> {
            val ch = evalFormString(stmt.text).firstOrNull()?.toString() ?: "-"
            emit(EngineEvent.DrawLine(ch))
        }
        is BarStatement -> {
            val value = evalExpr(stmt.value).toLong()
            val max = evalExpr(stmt.max).toLong().coerceAtLeast(1L)
            val length = evalExpr(stmt.length).toLong().toInt().coerceAtLeast(1)
            val filled = ((value.toDouble() / max) * length).toInt().coerceIn(0, length)
            val bar = "[" + "*".repeat(filled) + ".".repeat(length - filled) + "]"
            emit(EngineEvent.Print(bar, false, currentColor, false, false))
        }

        // ── 배열 ──────────────────────────────────────────────────────────
        is ShuffleStatement -> {
            val varRef = stmt.variable as? VariableRef ?: return
            val variable = resolveVarRef(varRef) ?: return
            val all = variable.getAll().toMutableList()
            all.shuffle()
            all.forEachIndexed { i, v -> variable.set(listOf(i), v) }
        }
        is SplitCmdStatement -> {
            val str = evalExpr(stmt.str).toEraString()
            val delim = evalExpr(stmt.delim).toEraString()
            val parts = if (delim.isEmpty()) listOf(str) else str.split(delim)
            val targetRef = stmt.target as? VariableRef ?: return
            val targetVar = resolveVarRef(targetRef)
            parts.forEachIndexed { i, s ->
                targetVar?.set(listOf(i), EraValue.of(s))
            }
            scope.getOrCreate("RESULT").set(listOf(0), EraValue.of(parts.size.toLong()))
        }

        // ── JUMP 계열: CALL과 달리 현재 함수로 돌아오지 않음 ─────────────────
        is CallEventStatement -> {
            // CALLEVENT: 이벤트 함수 체인 전체 호출 (없으면 무시 — isTry와 동일)
            val name = when (val fn = stmt.functionName) {
                is VariableRef -> fn.name
                is StringLiteral -> evalFormString(fn)
                else -> evalExpr(fn).toEraString()
            }
            val callArgs = stmt.args.map { evalExpr(it) }
            if (functions.containsKey(name.uppercase())) {
                try { callFunction(name, callArgs) } catch (e: ReturnSignal) { /* 정상 종료 */ }
            }
        }
        is JumpStatement -> {
            val name = when (val n = stmt.functionName) {
                is StringLiteral -> n.value.uppercase()
                else -> evalExpr(n).toEraString().uppercase()
            }
            val args = stmt.args.map { evalExpr(it) }
            if (name.isEmpty()) return
            if (stmt.isTry && !functions.containsKey(name)) return
            throw JumpSignal(name, args)
        }
        is JumpFormStatement -> {
            val name = evalFormString(StringLiteral(stmt.rawName, stmt.line)).uppercase()
            if (stmt.isTry && !functions.containsKey(name)) return
            throw JumpSignal(name, emptyList())
        }

        // ── 기타 ──────────────────────────────────────────────────────────
        is UnicodeStatement -> {
            // erakanon 원본 동작: UNICODE는 해당 코드포인트 문자를 RESULTS:0에 저장
            val cp = evalExpr(stmt.codePoint).toLong().toInt()
            val ch = try { String(Character.toChars(cp)) } catch (e: Exception) { "" }
            scope.getOrCreate("RESULTS", true, listOf(100)).set(listOf(0), EraValue.of(ch))
        }
    }
}

internal suspend fun EraInterpreter.executeIf_ext(stmt: IfStatement) {
    for (branch in stmt.branches) {
        if (evalExpr(branch.condition).toLong() != 0L) {
            executeStatements(branch.body)
            return
        }
    }
    if (stmt.elseBranch != null) executeStatements(stmt.elseBranch)
}

internal suspend fun EraInterpreter.executeFor_ext(stmt: ForStatement) {
    val varRef = stmt.variable
    var value = evalExpr(stmt.from).toLong()
    val to = evalExpr(stmt.to).toLong()
    val step = stmt.step?.let { evalExpr(it).toLong() } ?: 1L
    setVar(varRef, EraValue.of(value))
    if (step == 0L) return  // step=0 이면 무한루프 방지

    while (if (step > 0) value < to else value > to) {
        try {
            executeStatements(stmt.body)
        } catch (e: BreakSignal) {
            return
        } catch (e: ContinueSignal) {
            // continue
        }
        value += step
        setVar(varRef, EraValue.of(value))
    }
}

internal suspend fun EraInterpreter.executeWhile_ext(stmt: WhileStatement) {
    while (evalExpr(stmt.condition).toLong() != 0L) {
        try {
            executeStatements(stmt.body)
        } catch (e: BreakSignal) { return }
        catch (e: ContinueSignal) { continue }
    }
}

internal suspend fun EraInterpreter.executeDoLoop_ext(stmt: DoLoopStatement) {
    do {
        try {
            executeStatements(stmt.body)
        } catch (e: BreakSignal) { return }
        catch (e: ContinueSignal) { continue }
        val condVal = stmt.condition?.let { evalExpr(it).toLong() != 0L } ?: false
        if (stmt.isUntil && condVal) break
        if (!stmt.isUntil && !condVal) break
    } while (true)
}

internal suspend fun EraInterpreter.executeRepeat_ext(stmt: RepeatStatement) {
    val count = evalExpr(stmt.count).toLong()
    for (i in 0 until count) {
        scope.getOrCreate("COUNT").set(emptyList(), EraValue.of(i))
        try {
            executeStatements(stmt.body)
        } catch (e: BreakSignal) { return }
        catch (e: ContinueSignal) { continue }
    }
}

internal suspend fun EraInterpreter.executeSelectCase_ext(stmt: SelectCaseStatement) {
    val value = evalExpr(stmt.expr)
    for (case in stmt.cases) {
        var matched = false
        for (cond in case.conditions) {
            matched = when (cond) {
                is CaseValue -> eraEquals_ext(value, evalExpr(cond.value))
                is CaseRange -> {
                    val from = evalExpr(cond.from).toLong()
                    val to = evalExpr(cond.to).toLong()
                    value.toLong() in from..to
                }
                is CaseIs -> {
                    val cmpVal = evalExpr(cond.value).toLong()
                    when (cond.op) {
                        "==" -> value.toLong() == cmpVal
                        "!=" -> value.toLong() != cmpVal
                        "<" -> value.toLong() < cmpVal
                        "<=" -> value.toLong() <= cmpVal
                        ">" -> value.toLong() > cmpVal
                        ">=" -> value.toLong() >= cmpVal
                        else -> false
                    }
                }
            }
            if (matched) break
        }
        if (matched) {
            executeStatements(case.body)
            return
        }
    }
    if (stmt.elseBody != null) executeStatements(stmt.elseBody)
}

internal fun EraInterpreter.eraEquals_ext(a: EraValue, b: EraValue): Boolean {
    return when {
        a is EraValue.EraString || b is EraValue.EraString -> a.toEraString() == b.toEraString()
        else -> a.toLong() == b.toLong()
    }
}

internal suspend fun EraInterpreter.executePrint_ext(stmt: PrintStatement) {
    val isColumn = stmt.variant in listOf(
        PrintVariant.COLUMN, PrintVariant.COLUMN_LINE,
        PrintVariant.COLUMN_FORM, PrintVariant.COLUMN_FORML
    )

    val text = when (stmt.variant) {
        PrintVariant.PLAIN, PrintVariant.LINE, PrintVariant.WAIT -> {
            val sb = StringBuilder()
            for (arg in stmt.args) sb.append(evalFormString(arg))
            sb.toString()
        }
        PrintVariant.FORM, PrintVariant.FORML -> {
            val sb = StringBuilder()
            for (arg in stmt.args) sb.append(evalFormString(arg))
            sb.toString()
        }
        PrintVariant.COLUMN, PrintVariant.COLUMN_LINE,
        PrintVariant.COLUMN_FORM, PrintVariant.COLUMN_FORML -> {
            val sb = StringBuilder()
            for (arg in stmt.args) sb.append(evalFormString(arg))
            sb.toString()
        }
        PrintVariant.VALUE, PrintVariant.VALUEL -> {
            val parts = mutableListOf<String>()
            for (arg in stmt.args) parts.add(evalExpr(arg).toEraString())
            parts.joinToString(", ")
        }
        PrintVariant.STRING, PrintVariant.STRINGL -> {
            val sb = StringBuilder()
            for (arg in stmt.args) sb.append(evalExpr(arg).toEraString())
            sb.toString()
        }
        PrintVariant.DATA -> {
            val sb = StringBuilder()
            for (arg in stmt.args) sb.append(evalExpr(arg).toEraString())
            sb.toString()
        }
    }

    val isBold = (fontStyle and 1) != 0
    val isItalic = (fontStyle and 2) != 0

    if (isColumn) {
        // PC 동작: PRINTC/PRINTLC/PRINTFORMC/PRINTFORMLC 모두 동일.
        // 텍스트를 printcBuffer에 누적하고, PRINTC_COLS(기본3)개 쌓이면 한 줄로 flush.
        // 남은 버퍼는 DRAWLINE/일반PRINT 등 비컬럼 출력이 올 때 flush됨.
        val bracketMatch = Regex("""\[\s*(\d+)\s*\]""").find(text)
        val btnValue = bracketMatch?.groupValues?.get(1)?.toLongOrNull() ?: -1L
        val displayText = if (bracketMatch != null) {
            val name = text.substring(0, bracketMatch.range.first).trim()
            if (name.isNotEmpty()) "[$btnValue] $name" else text
        } else text
        printcBuffer.add(btnValue to displayText)
        if (printcBuffer.size >= PRINTC_COLS) flushPrintcBuffer_ext()
        return
    }

    // 비컬럼 출력 전 버퍼 flush
    if (printcBuffer.isNotEmpty()) flushPrintcBuffer_ext()

    val newLine = stmt.variant in listOf(
        PrintVariant.LINE, PrintVariant.FORML, PrintVariant.VALUEL, PrintVariant.STRINGL, PrintVariant.WAIT
    )
    emit(EngineEvent.Print(text, newLine, currentColor, isBold, isItalic))
}

internal fun EraInterpreter.flushPrintcBuffer_ext() {
    if (printcBuffer.isEmpty()) return
    android.util.Log.d("ERA_PRINTC", "flush: size=${printcBuffer.size} COLS=$PRINTC_COLS WIDTH=$PRINTC_WIDTH items=${printcBuffer.map{it.second.take(10)}}")
    val size = printcBuffer.size
    printcBuffer.forEachIndexed { idx, (btnVal, text) ->
        val isLast = idx == size - 1
        // 마지막 항목 포함 모두 패딩 (PC 동작과 동일)
        val paddedText = EraStringUtils.eraColumnPad(text, PRINTC_WIDTH)
        if (btnVal >= 0L) {
            // 마지막 항목: isInline=false → GameViewModel에서 flushLine() → 줄바꿈
            emit(EngineEvent.PrintButton(btnVal, paddedText, currentColor, isInline = !isLast))
        } else {
            emit(EngineEvent.Print(paddedText, isLast, currentColor, false, false))
        }
    }
    printcBuffer.clear()
}


internal suspend fun EraInterpreter.executeInput_ext(stmt: InputStatement) {
    android.util.Log.d("ERA_INPUT", "INPUT 실행: isString=${stmt.isString}, timeOut=${stmt.timeOut}, callStack=${callStack.takeLast(3).map{it.functionName}}")
    // INPUT 직전 미flush 버퍼 강제 출력 (홀수 개 PRINTLC 등)
    if (printcBuffer.isNotEmpty()) flushPrintcBuffer_ext()
    if (stmt.timeOut != null) {
        val default = stmt.defaultValue?.let { evalExpr(it).toLong() } ?: 0L
        android.util.Log.d("ERA_INPUT", "TINPUT 자동통과: default=$default")
        scope.getOrCreate("RESULT").set(listOf(0), EraValue.of(default))
        return
    }
    // 새 INPUT 시작 시 이전 RESULTS:0 초기화 (아이탬 목록 오염 방지)
    scope.getOrCreate("RESULTS", true, listOf(100)).set(listOf(0), EraValue.EMPTY_STRING)
    val input = waitForInput(!stmt.isString)
    android.util.Log.d("ERA_INPUT", "INPUT 응답: '$input'")
    if (stmt.isString) {
        scope.getOrCreate("RESULTS", true, listOf(100)).set(listOf(0), EraValue.of(input))
    } else {
        val num = input.toLongOrNull() ?: stmt.defaultValue?.let { evalExpr(it).toLong() } ?: 0L
        scope.getOrCreate("RESULT").set(listOf(0), EraValue.of(num))
    }
}

internal suspend fun EraInterpreter.executeVarSet_ext(stmt: VarSetStatement) {
    val value = stmt.value?.let { evalExpr(it) } ?: EraValue.ZERO
    val start = stmt.start?.let { evalExpr(it).toLong().toInt() } ?: 0
    // PC 스펙: 4번째 인수는 end(절대 인덱스), count = end - start
    val rawCount = stmt.count?.let { evalExpr(it).toLong().toInt() } ?: -1
    val count = if (rawCount >= 0 && start > 0) rawCount - start else rawCount
    val varRef = stmt.variable as? VariableRef ?: return
    val vname = varRef.name.uppercase()

    // TCVAR:TARGET:0, 0, 0, 100 같은 charaVar 2인덱스 VARSET
    val charaVarSet = setOf(
        "BASE","MAXBASE","DOWNBASE","LOSEBASE","TCVAR","SOURCE","EX","CUP","CDOWN",
        "ABL","TALENT","EXP","MARK","PALAM","JUEL","GOTJUEL","RELATION","CFLAG","CSTR"
    )
    if (vname in charaVarSet && varRef.indices.size >= 2) {
        val rawFirst = varRef.indices[0]
        val ci = when {
            rawFirst is VariableRef -> when (rawFirst.name.uppercase()) {
                "MASTER" -> scope.getOrCreate("MASTER").get(listOf(0)).toLong().toInt()
                "ASSI"   -> scope.getOrCreate("ASSI").get(listOf(0)).toLong().toInt()
                "TARGET" -> scope.getOrCreate("TARGET").get(listOf(0)).toLong().toInt()
                "PLAYER" -> scope.getOrCreate("PLAYER").get(listOf(0)).toLong().toInt()
                else     -> evalExpr(rawFirst).toLong().toInt()
            }
            else -> evalExpr(rawFirst).toLong().toInt()
        }
        val baseIdx = evalExpr(varRef.indices[1]).toLong().toInt()
        val chara = charaManager.getChara(ci)
        if (chara != null) {
            val isStr = vname == "CSTR"
            val realStart = baseIdx + start
            val end = if (count < 0) realStart + 1 else realStart + count
            for (i in realStart until end) {
                if (isStr) chara.setStr(vname, i, value.toEraString())
                else chara.setNum(vname, i, value.toLong())
            }
            return
        }
    }

    val variable = resolveVarRef(varRef)
    variable?.setAll(value, start, count)
}

// ─── Expression Evaluation ────────────────────────────────────────────────

/**
 * ERA 캐릭터 인덱스 표현식을 평가.
 * MASTER/ASSI/TARGET/PLAYER → 해당 스코프 변수의 값(charaIdx)
 * ARG/LOCAL 등 함수 파라미터 → 로컬 스코프에서 값(charaIdx) 읽기
 * 그 외 → evalExpr로 직접 평가
 */

// 변수가 실제로 정의되어 있는지 확인 (getOrCreate 하지 않음)

internal suspend fun EraInterpreter.executeAssign_ext(stmt: AssignStatement) {
    val targetVar = if (stmt.target is VariableRef) resolveVarRef(stmt.target) else null

    val value = if (targetVar?.isString == true) {
        when {
            stmt.value is StringLiteral || stmt.value is FormString ->
                EraValue.of(evalFormString(stmt.value))
            stmt.value is VariableRef && !isVarDefined(stmt.value.name) ->
                // 미정의 변수명은 문자열 리터럴로 취급 (ERA 관습: CALLNAME = 당신)
                EraValue.of(stmt.value.name)
            else -> evalExpr(stmt.value)
        }
    } else {
        evalExpr(stmt.value)
    }

    val targetName = (stmt.target as? VariableRef)?.name
    if (targetName?.uppercase() in listOf("LOCAL", "LOCALS")) {
    }

    when (stmt.op) {
        "=" -> {
            setVar(stmt.target, value)
            val n = (stmt.target as? VariableRef)?.name
            if (n?.uppercase() == "LOCALS") {
                val idx = (stmt.target as VariableRef).indices.map { evalExpr(it).toLong().toInt() }
                val stored = resolveVarRef(stmt.target as VariableRef)?.get(idx)
            }
        }
        "+=" -> setVar(stmt.target, EraValue.of(resolveVar(stmt.target).toLong() + value.toLong()))
        "-=" -> setVar(stmt.target, EraValue.of(resolveVar(stmt.target).toLong() - value.toLong()))
        "*=" -> setVar(stmt.target, EraValue.of(resolveVar(stmt.target).toLong() * value.toLong()))
        "/=" -> {
            val divisor = value.toLong()
            if (divisor != 0L) setVar(stmt.target, EraValue.of(resolveVar(stmt.target).toLong() / divisor))
        }
        "%=" -> {
            val divisor = value.toLong()
            if (divisor != 0L) setVar(stmt.target, EraValue.of(resolveVar(stmt.target).toLong() % divisor))
        }
        "|=" -> setVar(stmt.target, EraValue.of(resolveVar(stmt.target).toLong() or value.toLong()))
        "&=" -> setVar(stmt.target, EraValue.of(resolveVar(stmt.target).toLong() and value.toLong()))
        "^=" -> setVar(stmt.target, EraValue.of(resolveVar(stmt.target).toLong() xor value.toLong()))
    }
}

internal suspend fun EraInterpreter.evalBinary_ext(expr: BinaryOp): EraValue {
    // Short-circuit
    if (expr.op == "&&") {
        if (evalExpr(expr.left).toLong() == 0L) return EraValue.ZERO
        return EraValue.of(evalExpr(expr.right).toLong() != 0L)
    }
    if (expr.op == "||") {
        if (evalExpr(expr.left).toLong() != 0L) return EraValue.ONE
        return EraValue.of(evalExpr(expr.right).toLong() != 0L)
    }

    val left = evalExpr(expr.left)
    val right = evalExpr(expr.right)

    // String concat for +
    if (expr.op == "+" && (left is EraValue.EraString || right is EraValue.EraString)) {
        return EraValue.of(left.toEraString() + right.toEraString())
    }

    val l = left.toLong()
    val r = right.toLong()

    if (expr.op == "*" && left is EraValue.EraString) {
        return EraValue.of(left.value.repeat(r.toInt().coerceAtLeast(0)))
    }
    if (expr.op == "*" && right is EraValue.EraString) {
        return EraValue.of(right.value.repeat(l.toInt().coerceAtLeast(0)))
    }

    return when (expr.op) {
        "+" -> EraValue.of(l + r)
        "-" -> EraValue.of(l - r)
        "*" -> EraValue.of(l * r)
        "/" -> if (r != 0L) EraValue.of(l / r) else EraValue.ZERO
        "%" -> if (r != 0L) EraValue.of(l % r) else EraValue.ZERO
        "^" -> EraValue.of(l xor r)          // ^ = 비트 XOR (ERA 스펙)
        "^^" -> EraValue.of(if ((l != 0L) xor (r != 0L)) 1L else 0L)
        "==" -> EraValue.of(eraEquals_ext(left, right))
        "!=" -> EraValue.of(!eraEquals_ext(left, right))
        "<" -> EraValue.of(l < r)
        "<=" -> EraValue.of(l <= r)
        ">" -> EraValue.of(l > r)
        ">=" -> EraValue.of(l >= r)
        "&" -> EraValue.of(l and r)
        "|" -> EraValue.of(l or r)
        "<<" -> EraValue.of(l shl r.toInt())
        ">>" -> EraValue.of(l shr r.toInt())
        else -> EraValue.ZERO
    }
}

internal suspend fun EraInterpreter.evalUnary_ext(expr: UnaryOp): EraValue {
    return when (expr.op) {
        "-" -> EraValue.of(-evalExpr(expr.expr).toLong())
        "!" -> EraValue.of(evalExpr(expr.expr).toLong() == 0L)
        "~" -> EraValue.of(evalExpr(expr.expr).toLong().inv())
        "++pre" -> {
            val v = evalExpr(expr.expr).toLong() + 1
            setVar(expr.expr, EraValue.of(v))
            EraValue.of(v)
        }
        "--pre" -> {
            val v = evalExpr(expr.expr).toLong() - 1
            setVar(expr.expr, EraValue.of(v))
            EraValue.of(v)
        }
        "++post" -> {
            val v = evalExpr(expr.expr).toLong()
            setVar(expr.expr, EraValue.of(v + 1))
            EraValue.of(v)
        }
        "--post" -> {
            val v = evalExpr(expr.expr).toLong()
            setVar(expr.expr, EraValue.of(v - 1))
            EraValue.of(v)
        }
        else -> EraValue.ZERO
    }
}