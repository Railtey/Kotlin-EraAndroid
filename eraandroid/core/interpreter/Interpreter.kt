package com.eraandroid.core.interpreter

import com.eraandroid.core.parser.*
import com.eraandroid.core.vm.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.math.*
import kotlin.random.Random
import com.eraandroid.core.lexer.Lexer
import com.eraandroid.core.lexer.TokenType
import com.eraandroid.core.parser.Parser

// ─── Interpreter ─────────────────────────────────────────────────────────────
class EraInterpreter(
    internal val scope: VariableScope = BuiltinVariables.createDefaultScope(),
    internal val charaManager: CharaManager = CharaManager()
) {
    // CSV 로딩 완료 후 저장되는 정적 데이터 스냅샷 (RESETDATA 시 복구용)
    internal var csvStaticSnapshot: Map<String, List<com.eraandroid.core.vm.EraValue>> = emptyMap()
    private val _events = MutableSharedFlow<EngineEvent>(extraBufferCapacity = 256)
    val events: SharedFlow<EngineEvent> = _events.asSharedFlow()

    internal val functions = mutableMapOf<String, FunctionDef>()
    internal val callStack = mutableListOf<CallFrame>()
    private var state = EngineState.IDLE

    // Input/output channels
    private var pendingInput: CompletableDeferred<String>? = null
    internal var pendingSaveOp: CompletableDeferred<Unit>? = null
    internal var currentColor: Int? = null
    internal var pendingLoad: CompletableDeferred<Boolean>? = null
    internal var currentBgColor: Int? = null
    internal var fontStyle: Int = 0
    internal var alignment: Int = 0
    internal var currentFont: String = "default"

    // PRINTC 컬럼 버퍼 (_default.config 기준: 3열×25자)
    internal val printcBuffer = mutableListOf<Pair<Long, String>>()
    internal var PRINTC_COLS  = 3
    internal var PRINTC_WIDTH = 25

    data class CallFrame(
        val functionName: String,
        val locals: VariableScope = VariableScope(),
        val args: List<EraValue> = emptyList()
    )

    internal suspend fun callFunctionPublic(name: String, args: List<EraValue>, initResult: Boolean = true) =
        callFunction(name, args, initResult)

    fun completeLoad(success: Boolean) {
        pendingLoad?.complete(success)
        pendingLoad = null
    }

    fun loadProgram(program: Program) {
        // PALAMLV/EXPLV: CSV 파일 없을 때 emuera 표준 기본값 세팅
        // BuiltinVariables에서 0배열로 초기화되므로 첫값이 0이면 기본값 적용
        run {
            val palamDef = longArrayOf(0,100,500,3000,10000,30000,60000,100000,150000,250000)
            val explvDef  = longArrayOf(0,1,4,20,50,200)
            val pv = scope.getOrCreate("PALAMLV", false, listOf(1000))
            if (pv.get(listOf(0)).toLong() == 0L && pv.get(listOf(1)).toLong() == 0L) {
                palamDef.forEachIndexed { i, t -> pv.set(listOf(i), EraValue.of(t)) }
            }
            val ev = scope.getOrCreate("EXPLV", false, listOf(1000))
            if (ev.get(listOf(0)).toLong() == 0L && ev.get(listOf(1)).toLong() == 0L) {
                explvDef.forEachIndexed { i, t -> ev.set(listOf(i), EraValue.of(t)) }
            }
        }
        for (fn in program.functions) {
            val key = fn.name.uppercase()
            val existing = functions[key]
            if (existing == null) {
                functions[key] = fn
            } else {
                when {
                    fn.attributes.contains(FunctionAttribute.ONLY) -> {
                        // #ONLY: 이 함수만 실행, 기존 체인 완전 교체
                        functions[key] = fn
                    }
                    existing.attributes.contains(FunctionAttribute.ONLY) -> {
                        // 기존이 #ONLY면 새 함수 무시
                    }
                    fn.attributes.contains(FunctionAttribute.SINGLE) -> {
                        // #SINGLE: 이미 등록됐으면 무시
                    }
                    fn.attributes.contains(FunctionAttribute.PRI) -> {
                        // #PRI: 새 함수를 앞에, 기존 체인을 뒤에 (unique 키로 충돌 방지)
                        // emuera 동작: PRI 함수가 RETURN해도 뒤 체인은 반드시 실행됨
                        val baseKey = "${key}__PRI_${functions.size}"
                        functions[baseKey] = existing
                        // 체인 호출 래퍼: RETURN/ReturnSignal을 흡수하고 뒤 체인을 실행
                        val callBase = CallStatement(StringLiteral(baseKey, -1), emptyList(), -1, isTry = false)
                        // PRIBody = fn.body + [CALL baseKey], RETURN이 fn.body 중간에 있어도
                        // emuera는 그 함수를 종료한 뒤 다음 체인을 실행함.
                        // → fn.body를 별도 키로 보관하고, 체인 래퍼 함수를 만든다
                        val priBodyKey = "${key}__PRIBODY_${functions.size}"
                        functions[priBodyKey] = FunctionDef(priBodyKey, fn.params, fn.body, fn.attributes, fn.line)
                        // 체인 래퍼: PRI 바디 호출(RETURN 흡수) → 기존 체인 호출
                        val chainWrapper = listOf(
                            CallStatement(StringLiteral(priBodyKey, -1), emptyList(), -1),
                            callBase
                        )
                        functions[key] = FunctionDef(key, fn.params, chainWrapper, setOf(), fn.line)
                    }
                    fn.attributes.contains(FunctionAttribute.LATER) -> {
                        // #LATER: 기존 함수를 앞에, 새 함수를 뒤에 실행
                        // emuera 동작: 기존 함수가 RETURN해도 LATER 함수는 반드시 실행됨
                        val laterBodyKey = "${key}__LATERBODY_${functions.size}"
                        functions[laterBodyKey] = fn
                        val existingKey = "${key}__LATERBASE_${functions.size}"
                        functions[existingKey] = existing
                        // 체인 래퍼: 기존 바디 호출(RETURN 흡수) → LATER 바디 호출
                        val chainWrapper = listOf(
                            CallStatement(StringLiteral(existingKey, -1), emptyList(), -1),
                            CallStatement(StringLiteral(laterBodyKey, -1), emptyList(), -1)
                        )
                        functions[key] = FunctionDef(key, existing.params, chainWrapper, setOf(), existing.line)
                    }
                    else -> {
                        // 중복 정의: emuera처럼 뒤에 체인 연결 (LATER와 동일)
                        // 기존 함수가 RETURN해도 뒤 체인은 실행됨
                        val existingKey = "${key}__CHAINBASE_${functions.size}"
                        functions[existingKey] = existing
                        val chainKey = "${key}__CHAIN_${functions.size}"
                        functions[chainKey] = fn
                        val chainWrapper = listOf(
                            CallStatement(StringLiteral(existingKey, -1), emptyList(), -1),
                            CallStatement(StringLiteral(chainKey, -1), emptyList(), -1)
                        )
                        functions[key] = FunctionDef(
                            key, existing.params,
                            chainWrapper,
                            existing.attributes, existing.line
                        )
                    }
                }
            }
        }
        if (functions.size in 1..5) {
            println("[DEBUG] 함수 등록됨: ${functions.keys}")
        }
        for (global in program.globalDefs) {
            when (global) {
                is DimStatement -> executeGlobalDim(global)
                is DefineStatement -> scope.setDefine(global.name, global.value)
                else -> {}
            }
        }
    }

    suspend fun start(entryPoint: String = "SYSTEM_TITLE") {
        state = EngineState.RUNNING
        var nextBegin = entryPoint
        while (true) {
            try {
                when (nextBegin.uppercase()) {
                    "FIRST" -> {
                        android.util.Log.d("ERA_FIRST", "BEGIN FIRST 실행")
                        android.util.Log.d("ERA_FIRST", "EVENTFIRST 존재: ${functions.containsKey("EVENTFIRST")}")
                        android.util.Log.d("ERA_FIRST", "FIRST 존재: ${functions.containsKey("FIRST")}")
                        // FIRST 관련 함수들 전부 출력
                        functions.keys.filter { it.contains("FIRST") }.forEach {
                            android.util.Log.d("ERA_FIRST", "  FIRST 관련 함수: $it")
                        }
                        when {
                            functions.containsKey("EVENTFIRST") -> callFunction("EVENTFIRST", emptyList())
                            functions.containsKey("FIRST") -> callFunction("FIRST", emptyList())
                            else -> android.util.Log.e("ERA_FIRST", "FIRST 함수 없음!")
                        }
                    }
                    "TITLE"      -> callFunction("SYSTEM_TITLE", emptyList())
                    "TRAIN"      -> {
                        // emuera 원본 BEGIN TRAIN 흐름:
                        // 1. EVENTTRAIN (초기화, #PRI/#LATER로 여러 파일에 정의됨)
                        // 2. SHOW_STATUS (emuera 내장 → 이 게임은 SHOW_HOUSE 등으로 대체)
                        // 3. EVENTCOM (커맨드 변수 초기화)
                        // 4. SHOW_COM (emuera 내장 → 커맨드 버튼 목록 표시)
                        // 5. SHOW_USERCOM (유저 커맨드 추가 표시)
                        // 6. INPUT → USERCOM 실행 or CALLFORM COM{번호}
                        // 7. 사후처리 후 반복 or EVENTEND → AFTERTRAIN
                        if (functions.containsKey("SYSTEM_TRAIN")) {
                            callFunction("SYSTEM_TRAIN", emptyList())
                            throw BeginException("AFTERTRAIN")
                        }
                        if (functions.containsKey("TRAIN")) {
                            callFunction("TRAIN", emptyList())
                            throw BeginException("AFTERTRAIN")
                        }
                        // SYSTEM_TRAIN/TRAIN 없으면 emuera 원본 루프 직접 구현

                        // ── emuera 내장 BEGIN TRAIN 진입 시 자동 초기화 ──────
                        run {
                            val tgt = scope.getOrCreate("TARGET").get(listOf(0)).toLong().toInt()
                            val mst = scope.getOrCreate("MASTER").get(listOf(0)).toLong().toInt()
                            val asi = scope.getOrCreate("ASSI").get(listOf(0)).toLong().toInt()
                            for (v in listOf("TEQUIP","STAIN","EQUIP","NOWEX","UP","DOWN","SOURCE","EX")) {
                                scope.get(v)?.setAll(EraValue.ZERO)
                            }
                            for (ci in setOf(tgt, mst, asi).filter { it >= 0 }) {
                                charaManager.getChara(ci)?.let { ch ->
                                    for (v in listOf("TEQUIP","STAIN","EQUIP","NOWEX")) {
                                        for (i in 0 until 100) ch.setNum(v, i, 0L)
                                    }
                                }
                            }
                            scope.get("PREVCOM")?.setAll(EraValue.of(-1L))
                            scope.get("NEXTCOM")?.setAll(EraValue.of(-1L))
                        }

                        // 1. EVENTTRAIN: 조교 진입 시 1회만 호출 (TFLAG 초기화, 잔여시간 설정 등)
                        if (functions.containsKey("EVENTTRAIN")) callFunction("EVENTTRAIN", emptyList())

                        var trainRunning = true
                        while (trainRunning) {
                            // ── UpdateAfterShowUsercom: UP/DOWN/LOSEBASE/CUP/CDOWN 초기화 ──
                            scope.get("UP")?.setAll(EraValue.ZERO)
                            scope.get("DOWN")?.setAll(EraValue.ZERO)
                            scope.get("LOSEBASE")?.setAll(EraValue.ZERO)
                            for (ci in 0 until charaManager.count) {
                                charaManager.getChara(ci)?.let { ch ->
                                    for (i in 0 until 1000) { ch.setNum("CUP", i, 0L); ch.setNum("CDOWN", i, 0L) }
                                }
                            }

                            // 2. SHOW_STATUS
                            when {
                                functions.containsKey("SHOW_STATUS") ->
                                    callFunction("SHOW_STATUS", emptyList())
                                functions.containsKey("SHOW_STATUS_TRAIN") ->
                                    callFunction("SHOW_STATUS_TRAIN", emptyList())
                            }

                            // 3. EVENTCOM: 매 턴 시작 (SHOW_STATUS 직후, SHOW_COM 직전)
                            if (functions.containsKey("EVENTCOM")) callFunction("EVENTCOM", emptyList())

                            // 4. SHOW_COM
                            emit(EngineEvent.DrawLine())
                            val trainNameVar = scope.get("TRAINNAME")
                            for (comIdx in 0..999) {
                                val comName = trainNameVar?.get(listOf(comIdx))
                                    ?.toEraString()?.takeIf { it.isNotEmpty() }
                                val ablFunc = "COM_ABLE${comIdx}"
                                if (comName != null && functions.containsKey(ablFunc)) {
                                    try {
                                        callFunction(ablFunc, emptyList())
                                        val able = scope.getOrCreate("RESULT").get(listOf(0)).toLong()
                                        if (able == 1L) {
                                            val label = EraStringUtils.eraColumnPad("[$comIdx] $comName", PRINTC_WIDTH)
                                            printcBuffer.add(Pair(comIdx.toLong(), label))
                                            if (printcBuffer.size >= PRINTC_COLS) flushPrintcBuffer()
                                        }
                                    } catch (e: Exception) { /* COM_ABLE 에러 → 스킵 */ }
                                }
                            }
                            if (printcBuffer.isNotEmpty()) flushPrintcBuffer()
                            if (functions.containsKey("SHOW_USERCOM")) callFunction("SHOW_USERCOM", emptyList())
                            if (printcBuffer.isNotEmpty()) flushPrintcBuffer()
                            emit(EngineEvent.Print("", true, currentColor, false, false))

                            // 5. INPUT
                            val trainInput = waitForInput(true)
                            val comNum = trainInput.toLongOrNull() ?: -1L
                            scope.getOrCreate("RESULT").set(listOf(0), EraValue.of(comNum))
                            scope.getOrCreate("SELECTCOM").set(listOf(0), EraValue.of(comNum))

                            // ── UpdateAfterInputCom: NOWEX 초기화 (EVENTCOM 직전) ──
                            for (ci in 0 until charaManager.count) {
                                charaManager.getChara(ci)?.let { ch ->
                                    for (i in 0 until 100) ch.setNum("NOWEX", i, 0L)
                                }
                            }

                            // ── COM_NAME 자동 설정: TRAINNAME[SELECTCOM] → COM_NAME ──
                            val selectedTrainName = trainNameVar?.get(listOf(comNum.toInt()))
                                ?.toEraString()?.takeIf { it.isNotEmpty() } ?: ""
                            val prevComName = scope.getOrCreate("COM_NAME", true).get(listOf(0)).toEraString()
                            scope.getOrCreate("COM_NAME", true).set(listOf(0), EraValue.of(selectedTrainName))

                            // 6. USERCOM 처리 (700~번대 특수 커맨드)
                            if (functions.containsKey("USERCOM")) {
                                scope.getOrCreate("__USERCOM_INPUT__").set(listOf(0), EraValue.of(comNum))
                                try {
                                    callFunction("USERCOM", emptyList(), initResult = false)
                                    val r = scope.getOrCreate("RESULT").get(listOf(0)).toLong()
                                    if (r == 1L) {
                                        scope.getOrCreate("COM_NAME", true).set(listOf(0), EraValue.of(prevComName))
                                        continue
                                    }  // USERCOM이 처리함 → 루프 재시작
                                } catch (e: BeginException) {
                                    when (e.target.uppercase()) {
                                        "AFTERTRAIN", "TURNEND", "SHOP" -> { trainRunning = false; throw BeginException("AFTERTRAIN") }
                                        else -> throw e
                                    }
                                }
                            }

                            // 7. COM{N} 실행
                            val comFuncName = "COM${comNum}"
                            if (comNum >= 0 && functions.containsKey(comFuncName)) {
                                scope.getOrCreate("SELECTCOM").set(listOf(0), EraValue.of(comNum))
                                var comSucceeded = false
                                try {
                                    callFunction(comFuncName, emptyList())
                                    comSucceeded = true
                                } catch (e: BeginException) {
                                    when (e.target.uppercase()) {
                                        "AFTERTRAIN", "TURNEND", "SHOP", "ABLUP" ->
                                        { trainRunning = false; throw BeginException(e.target) }
                                        else -> throw e
                                    }
                                }

                                if (comSucceeded) {
                                    // 8. SOURCE_CHECK: 대사(KOJO_MESSAGE_COM) + 구슬/경험치 계산
                                    if (functions.containsKey("SOURCE_CHECK")) {
                                        try {
                                            callFunction("SOURCE_CHECK", emptyList())
                                        } catch (e: BeginException) {
                                            when (e.target.uppercase()) {
                                                "AFTERTRAIN", "TURNEND", "SHOP", "ABLUP" ->
                                                { trainRunning = false; throw BeginException(e.target) }
                                                else -> throw e
                                            }
                                        }
                                    }

                                    // ── UpdateAfterSourceCheck: SOURCE 초기화 ──
                                    scope.get("SOURCE")?.setAll(EraValue.ZERO)
                                    for (ci in 0 until charaManager.count) {
                                        charaManager.getChara(ci)?.let { ch ->
                                            for (i in 0 until 100) ch.setNum("SOURCE", i, 0L)
                                        }
                                    }
                                }

                                // 9. EVENTCOMEND: COM 실행 후 매 턴 종료 처리
                                if (functions.containsKey("EVENTCOMEND")) {
                                    try {
                                        callFunction("EVENTCOMEND", emptyList())
                                    } catch (e: BeginException) {
                                        when (e.target.uppercase()) {
                                            "AFTERTRAIN", "TURNEND", "SHOP", "ABLUP" ->
                                            { trainRunning = false; throw BeginException(e.target) }
                                            else -> throw e
                                        }
                                    }
                                }

                                // 10. UPCHECK: UP/DOWN → PALAM 반영
                                if (functions.containsKey("UPCHECK")) {
                                    try { callFunction("UPCHECK", emptyList()) } catch (e: Exception) {}
                                }
                                if (functions.containsKey("MASTER_FLAG_CHECK")) {
                                    try { callFunction("MASTER_FLAG_CHECK", emptyList()) } catch (e: Exception) {}
                                }
                            }

                            // 루프 계속
                        }
                        throw BeginException("AFTERTRAIN")
                    }
                    "AFTERTRAIN" -> {
                        // emuera 원본: AFTERTRAIN = EVENTEND 실행 → BEGIN ABLUP → TURNEND
                        // SYSTEM_AFTERTRAIN이 있으면 위임, 없으면 직접 EVENTEND 호출
                        var afterTarget = "TURNEND"
                        when {
                            functions.containsKey("SYSTEM_AFTERTRAIN") -> {
                                try {
                                    callFunction("SYSTEM_AFTERTRAIN", emptyList())
                                } catch (e: BeginException) {
                                    afterTarget = e.target
                                }
                            }
                            functions.containsKey("AFTERTRAINEND") -> {
                                try {
                                    callFunction("AFTERTRAINEND", emptyList())
                                } catch (e: BeginException) {
                                    afterTarget = e.target
                                }
                            }
                            else -> {
                                // 내장: EVENTEND 호출 (BEGIN ABLUP 등을 던질 수 있음)
                                if (functions.containsKey("EVENTEND")) {
                                    try {
                                        callFunction("EVENTEND", emptyList())
                                    } catch (e: BeginException) {
                                        afterTarget = e.target
                                    }
                                }
                            }
                        }
                        throw BeginException(afterTarget)
                    }
                    "LOAD" -> {
                        when {
                            functions.containsKey("SYSTEM_LOADEND") -> callFunction("SYSTEM_LOADEND", emptyList())
                            functions.containsKey("LOADEND") -> callFunction("LOADEND", emptyList())
                            else -> {}
                        }
                        throw BeginException("SHOP")
                    }
                    "TURNEND"    -> {
                        // TURNEND: 날짜 진행 후 SHOP으로
                        when {
                            functions.containsKey("SYSTEM_TURNEND") -> callFunction("SYSTEM_TURNEND", emptyList())
                            else -> {
                                // EVENTTURNEND 직접 호출 + 날짜 진행
                                if (functions.containsKey("EVENTTURNEND")) callFunction("EVENTTURNEND", emptyList())
                                // 날짜 진행
                                val time = scope.getOrCreate("TIME").get(listOf(0)).toLong()
                                if (time == 0L) {
                                    scope.getOrCreate("TIME").set(listOf(0), EraValue.of(1L))
                                } else {
                                    scope.getOrCreate("TIME").set(listOf(0), EraValue.of(0L))
                                    val day = scope.getOrCreate("DAY").get(listOf(0)).toLong()
                                    scope.getOrCreate("DAY").set(listOf(0), EraValue.of(day + 1L))
                                }
                            }
                        }
                        throw BeginException("SHOP")
                    }
                    "SHOP"       -> {
                        // BEGIN SHOP 진입 시 대사 트리거 변수 초기화
                        // TFLAG:200은 조교 커맨드 번호 — SHOP 화면에서는 0이어야 함
                        // 초기화 없으면 직전 조교 값이 남아 SELF_KOJO 오작동
                        scope.getOrCreate("TFLAG").set(listOf(200), EraValue.ZERO)
                        scope.getOrCreate("TFLAG").set(listOf(999), EraValue.ZERO)
                        // EVENTSHOP은 BEGIN SHOP 진입 시 한 번만 실행 (emuera 원본 동작)
                        callFunction("EVENTSHOP", emptyList())
                        while (true) {
                            callFunction("SHOW_SHOP", emptyList())
                            val shopInput = waitForInput(true)
                            val shopNum = shopInput.toLongOrNull() ?: -1L
                            scope.getOrCreate("RESULT").set(listOf(0), EraValue.of(shopNum))
                            try {
                                callFunction("USERSHOP", emptyList())
                            } catch (e: BeginException) {
                                throw e  // BEGIN TURNEND 등으로 전환
                            }
                            // USERSHOP이 RETURN하면 루프 계속 → 다시 SHOW_SHOP
                        }
                    }
                    "ABLUP"      -> {
                        // emuera 내장 BEGIN ABLUP 루프
                        if (functions.containsKey("SYSTEM_ABLUP")) {
                            callFunction("SYSTEM_ABLUP", emptyList())
                        } else {
                            if (functions.containsKey("EVENTABLUP")) callFunction("EVENTABLUP", emptyList())
                            var ablupRunning = true
                            while (ablupRunning) {
                                if (!functions.containsKey("SHOW_ABLUP_SELECT")) { ablupRunning = false; break }
                                callFunction("SHOW_ABLUP_SELECT", emptyList())
                                val ablupInput = waitForInput(true)
                                scope.getOrCreate("RESULT").set(listOf(0), EraValue.of(ablupInput.toLongOrNull() ?: -1L))
                                if (functions.containsKey("USERABLUP")) {
                                    try {
                                        // USERABLUP은 INPUT 결과(RESULT)를 읽어서 분기하므로 초기화 금지
                                        callFunction("USERABLUP", emptyList(), initResult = false)
                                        val r = scope.getOrCreate("RESULT").get(listOf(0)).toLong()
                                        if (r == 1L) ablupRunning = false
                                    } catch (e: BeginException) {
                                        ablupRunning = false
                                        throw e
                                    }
                                } else ablupRunning = false
                            }
                        }
                        throw BeginException("TURNEND")
                    }
                    "COMBEGIN"   -> callFunction("SYSTEM_COMBEGIN", emptyList())
                    else -> {
                        val fnName = "SYSTEM_${nextBegin.uppercase()}"
                        when {
                            functions.containsKey(fnName) -> callFunction(fnName, emptyList())
                            functions.containsKey(nextBegin.uppercase()) -> callFunction(nextBegin, emptyList())
                            else -> android.util.Log.w("ERA", "Unknown BEGIN target: $nextBegin")
                        }
                    }
                }
                break  // 정상 종료 (게임 끝)
            } catch (e: BeginException) {
                android.util.Log.d("ERA_BEGIN", "BEGIN 전환: $nextBegin -> ${e.target}")
                callStack.clear()   // BEGIN 전환 시 스택 완전 초기화
                nextBegin = e.target
            } catch (e: RestartSignal) {
                callStack.clear()
                nextBegin = "LOAD"
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: QuitSignal) {
                emit(EngineEvent.Quit)
                break
            } catch (e: ReturnSignal) {
                // 최상위까지 올라온 RETURN은 무시 (정상 종료)
                break
            } catch (e: GotoSignal) {
                android.util.Log.w("ERA", "최상위 GOTO 미처리: ${e.label}")
                nextBegin = "TITLE"
            } catch (e: EraRuntimeException) {
                android.util.Log.e("ERA_ERROR", "런타임 에러: ${e.message} line=${e.line}")
                emit(EngineEvent.Error(e.message ?: "Unknown error", e.line))
                nextBegin = "TITLE"
            } catch (e: Exception) {
                android.util.Log.e("ERA_ERROR", "예외: ${e::class.simpleName} ${e.message}")
                android.util.Log.e("ERA_ERROR", e.stackTraceToString())
                emit(EngineEvent.Error("${e::class.simpleName}: ${e.message}", -1))
                nextBegin = "TITLE"
            }
        }
        android.util.Log.d("ERA_ERROR", "start() 종료 - state=$state")
    }

    fun provideInput(input: String) {
        pendingInput?.complete(input)
        pendingInput = null
    }

    fun completeSaveOp() {       // ← 이 함수 추가
        pendingSaveOp?.complete(Unit)
        pendingSaveOp = null
    }

    fun goToTitle() {
        // 현재 실행 중인 코루틴을 타이틀로 강제 전환
        pendingInput?.complete("")   // 대기 중인 입력 해제
        pendingLoad?.complete(false)
        pendingSaveOp?.complete(Unit)
        // BeginException을 직접 던질 수 없으니 특수 입력값으로 처리
    }

    fun cancelAndRestart() {
        pendingInput?.complete("")
        pendingLoad?.complete(false)
        pendingSaveOp?.complete(Unit)
    }

    internal fun emit(event: EngineEvent) {
        _events.tryEmit(event)
    }

    internal suspend fun waitForInput(isNumber: Boolean, isWait: Boolean = false): String {
        state = EngineState.WAITING_INPUT
        emit(EngineEvent.WaitForInput(isNumber, isWait))
        val deferred = CompletableDeferred<String>()
        pendingInput = deferred
        val result = deferred.await()
        state = EngineState.RUNNING
        return result
    }

    private fun executeGlobalDim(dim: DimStatement) {
        val sizes = dim.sizes.map { expr ->
            when (expr) {
                is IntLiteral -> expr.value.toInt()
                is VariableRef -> scope.get(expr.name)?.get(listOf(0))?.toLong()?.toInt() ?: 1
                else -> 1
            }
        }
        val existing = scope.get(dim.name)
        if (existing == null) {
            scope.define(dim.name, EraVariable(dim.name.uppercase(), dim.isString, sizes, dim.isConst))
        }
    }

    internal suspend fun callFunction(name: String, args: List<EraValue>, initResult: Boolean = true): EraValue? {
        // USERSHOP / USERCOM 은 호출 전에 설정된 RESULT를 읽어서 분기하므로 초기화 안 함
        val nameUC = name.uppercase()
        if (initResult && nameUC != "USERSHOP" && nameUC != "USERCOM" && nameUC != "USERABLUP") {
            scope.getOrCreate("RESULT").set(listOf(0), EraValue.ZERO)
        }
        // UPCHECK 내장 처리: UP/DOWN → PALAM 반영
        if (nameUC == "UPCHECK") {
            val target = scope.getOrCreate("TARGET").get(listOf(0)).toLong().toInt()
            val chara = charaManager.getChara(target)
            if (chara != null) {
                val upVar   = scope.get("UP")   ?: scope.getOrCreate("UP",   false, listOf(1000))
                val downVar = scope.get("DOWN") ?: scope.getOrCreate("DOWN", false, listOf(1000))
                for (i in 0 until 1000) {
                    val u = upVar.get(listOf(i)).toLong()
                    val d = downVar.get(listOf(i)).toLong()
                    if (u != 0L || d != 0L) {
                        val cur = chara.getNum("PALAM", i)
                        chara.setNum("PALAM", i, maxOf(0L, cur + u - d))
                        upVar.set(listOf(i), EraValue.ZERO)
                        downVar.set(listOf(i), EraValue.ZERO)
                    }
                }
            }
            return null
        }
        // USERCOM: 체인 내부 호출로 RESULT가 덮여도 __USERCOM_INPUT__ 에서 복원
        if (nameUC == "USERCOM") {
            val saved = scope.getOrCreate("__USERCOM_INPUT__").get(listOf(0))
            scope.getOrCreate("RESULT").set(listOf(0), saved)
        }

        // SET_LIST / GET_LIST / CLEAR_LIST 직접 처리 (ERB 함수 경유 없이 글로벌 배열 직접 접근)
        if (name.uppercase() == "SET_LIST") {
            val idx = args.getOrNull(0)?.toLong()?.toInt() ?: 0
            val value = args.getOrNull(1)?.toLong() ?: 0L
            scope.getOrCreate("__LIST_DATA__", false, listOf(1000)).set(listOf(idx), EraValue.of(value))
            return EraValue.of(value)
        }
        if (name.uppercase() == "GET_LIST") {
            val idx = args.getOrNull(0)?.toLong()?.toInt() ?: 0
            return scope.getOrCreate("__LIST_DATA__", false, listOf(1000)).get(listOf(idx))
        }
        if (name.uppercase() == "CLEAR_LIST") {
            scope.getOrCreate("__LIST_DATA__", false, listOf(1000)).setAll(EraValue.ZERO)
            return EraValue.of(-1L)
        }

        // PRINT_COLORBAR / PRINT_COLORBAR_P 내장 처리
        if (name.uppercase() == "PRINT_COLORBAR" || name.uppercase() == "PRINT_COLORBAR_P") {
            val isP     = name.uppercase() == "PRINT_COLORBAR_P"
            val value   = args.getOrNull(0)?.toLong() ?: 0L
            val maxVal  = args.getOrNull(1)?.toLong()?.coerceAtLeast(1L) ?: 1L
            val length  = args.getOrNull(2)?.toLong()?.toInt()?.coerceAtLeast(1) ?: 3
            val fgColor = args.getOrNull(3)?.toLong()?.let { c ->
                (0xFF000000L or (c and 0xFFFFFFL)).toInt()
            } ?: currentColor ?: 0xFFFFFFFF.toInt()
            val bgColor = args.getOrNull(4)?.toLong()?.let { c ->
                val raw = (0xFF000000L or (c and 0xFFFFFFL)).toInt()
                if ((raw and 0x00FFFFFF) < 0x101010) 0xFF404040.toInt() else raw
            } ?: 0xFF404040.toInt()
            val fillCharIdx = if (isP) 6 else 5
            val bgCharIdx   = if (isP) 7 else 6
            val fillChar = args.getOrNull(fillCharIdx)?.toEraString()?.ifEmpty { "▊" } ?: "▊"
            val bgChar   = args.getOrNull(bgCharIdx)?.toEraString()?.ifEmpty { "▊" } ?: "▊"
            val filled = if (maxVal > 0) ((value.toDouble() / maxVal) * length).toInt().coerceIn(0, length) else 0
            val savedColor = currentColor
            if (filled > 0) {
                currentColor = fgColor
                emit(EngineEvent.SetColor(fgColor))
                emit(EngineEvent.Print(fillChar.repeat(filled), false, fgColor, false, false))
            }
            if (length - filled > 0) {
                currentColor = bgColor
                emit(EngineEvent.SetColor(bgColor))
                emit(EngineEvent.Print(bgChar.repeat(length - filled), false, bgColor, false, false))
            }
            currentColor = savedColor
            emit(EngineEvent.SetColor(savedColor ?: 0xFFFFFFFF.toInt()))
            return null
        }

        // CALENDAR_CALC를 내장 함수로 처리 (GOTO 루프 오류 우회)
        if (name.uppercase() == "CALENDAR_CALC") {
            val argVal = args.getOrNull(0)?.toLong() ?: 0L
            if (argVal > 0) {
                val dayVar = scope.getOrCreate("DAY", false, listOf(1000))
                val dayArr = LongArray(1000) { dayVar.get(listOf(it.toInt())).toLong() }
                val local = LongArray(1000)
                dayArr[7] = 1L + argVal % 7L
                local[0] = argVal; dayArr[4] = 1L; local[12] = 0L
                if (local[0] > 1460970000L) { local[12] = local[0] / 1460970000L; local[0] %= 1460970000L }
                dayArr[4] += 4000000L * local[12]
                local[11] = 0L
                if (local[0] > 14609700L) { local[11] = local[0] / 14609700L; local[0] %= 14609700L }
                dayArr[4] += 40000L * local[11]
                local[10] = 0L
                if (local[0] > 146097L) { local[10] = local[0] / 146097L; local[0] %= 146097L }
                dayArr[4] += 400L * local[10]
                local[4] = 0L; local[8] = 0L
                if (local[0] % 146097L == 0L && argVal < 146098L) {
                    dayArr[4] += 399L; dayArr[5] = 12L; dayArr[6] = 31L
                } else {
                    while (local[0] > 365L + local[8]) {
                        local[4]++; local[0] -= if (local[8] != 0L) 366L else 365L
                        local[8] = if (local[4] % 100L != 0L && local[4] % 4L == 0L) 1L else 0L
                    }
                    dayArr[4] += local[4]; local[5] = 1L; local[6] = 0L
                    if (local[0] <= 0L) { dayArr[4]--; dayArr[5] = 12L; dayArr[6] = 31L }
                    else {
                        dayArr[8] = if ((dayArr[4] % 4L == 0L && dayArr[4] % 100L != 0L) || dayArr[4] % 400L == 0L) 1L else 0L
                        while (local[0] > 0L) {
                            local[0]--; local[6]++
                            if (local[0] > 0L) {
                                val m = local[5]
                                when {
                                    m == 1L || m == 3L || m == 5L || m == 7L || m == 8L || m == 10L || m == 12L ->
                                        if (local[6] > 30L) { local[5]++; local[6] = 0L }
                                    m == 4L || m == 6L || m == 9L || m == 11L ->
                                        if (local[6] > 29L) { local[5]++; local[6] = 0L }
                                    dayArr[8] != 0L -> if (local[6] > 28L) { local[5]++; local[6] = 0L }
                                    else -> if (local[6] > 27L) { local[5]++; local[6] = 0L }
                                }
                            }
                        }
                        dayArr[5] = local[5]; dayArr[6] = local[6]
                    }
                }
                for (i in 0 until 1000) dayVar.set(listOf(i), com.eraandroid.core.vm.EraValue.of(dayArr[i]))
            }
            return null
        }

        if (name.uppercase() == "LIST_DATA") {
            val op = args.getOrNull(0)?.toEraString()?.uppercase() ?: ""
            val idx = args.getOrNull(1)?.toLong()?.toInt() ?: 0
            val listVar = scope.getOrCreate("__LIST_DATA__", false, listOf(1000))
            val result = when (op) {
                "SET" -> {
                    val value = args.getOrNull(2)?.toLong() ?: 0L
                    listVar.set(listOf(idx), EraValue.of(value))
                    EraValue.of(value)
                }
                "GET" -> listVar.get(listOf(idx))
                "CLEAR" -> { listVar.setAll(EraValue.ZERO); EraValue.of(-1L) }
                else -> EraValue.of(-1L)
            }
            // LIST_DATA는 RESULT를 덮어쓰지 않음 (조건식 안에서 호출되면 RESULT가 오염됨)
            return result
        }

        if (name.uppercase().startsWith("EXE_PROLOGUE") || name.uppercase().startsWith("EXIST_PROLOGUE")) {
            android.util.Log.d("ERA_PROLOGUE", "PROLOGUE 호출: $name (stackSize=${callStack.size})")
        }

        if (name.uppercase() == "SELECT_MODE_NOMOREGLAZE_TORIKOMODE") {
            val fn = functions[name.uppercase()]
            android.util.Log.d("ERA_GLAZE", "함수 body 크기: ${fn?.body?.size}")
            fn?.body?.forEachIndexed { i, stmt ->
                android.util.Log.d("ERA_GLAZE", "  [$i] ${stmt::class.simpleName}: $stmt")
            }
        }
        if (callStack.size > 500) {
            android.util.Log.e("ERA_CALL", "콜스택 오버플로우! 스택: ${callStack.takeLast(10).map { it.functionName }}")
            throw EraRuntimeException("Call stack overflow", -1)
        }
        if (name.uppercase() == "SHOW_HOUSE") {
            val master = scope.getOrCreate("MASTER").get(listOf(0)).toLong().toInt()
            val chara = charaManager.getChara(master)
            android.util.Log.d("ERA_HOUSE", "SHOW_HOUSE: MASTER=$master charaCount=${charaManager.count} CSTR1=${chara?.getStr("CSTR",1)}")
        }
        if (name.uppercase() == "EVENTFIRST" ||
            callStack.any { it.functionName == "EVENTFIRST" }) {
            android.util.Log.d("ERA_FIRST_TRACE", "CALL: $name (스택깊이=${callStack.size})")
        }
        if (name.uppercase().startsWith("EXE_PROLOGUE") || name.uppercase().startsWith("PROLOGUE_MENU")) {
            android.util.Log.d("ERA_PROLOGUE2", "호출됨: $name / args=$args")
        }
        val fn = functions[name.uppercase()] ?: run {
            val stackTrace = callStack.takeLast(5).joinToString(" → ") {
                "${it.functionName}(${functions[it.functionName]?.fileName ?: "?"})"
            }
            android.util.Log.w("ERA_MISSING_FUNC", "함수 없음(무시): '$name' 스택: $stackTrace")
            // emuera 동작: 정의 없는 함수 호출은 조용히 무시 (RESULT=0)
            scope.getOrCreate("RESULT").set(listOf(0), EraValue.ZERO)
            return null
        }
        val localScope = VariableScope().apply {
            define("LOCAL",  EraVariable("LOCAL",  false, listOf(1000)))
            define("LOCALS", EraVariable("LOCALS", true,  listOf(1000)))
            define("ARG",    EraVariable("ARG",    false, listOf(1000)))
            define("ARGS",   EraVariable("ARGS",   true,  listOf(1000)))
        }
        val frame = CallFrame(name.uppercase(), localScope, args)
        // ARG/ARGS 인수 바인딩: 위치 기반 - 숫자→ARG:i, 문자열→ARGS:i (PC 동작)
        for (i in args.indices) {
            if (i >= 1000) break
            if (args[i] is EraValue.EraString) {
                frame.locals.getOrCreate("ARGS", true, listOf(1000)).set(listOf(i), args[i])
            } else {
                frame.locals.getOrCreate("ARG", false, listOf(1000)).set(listOf(i), args[i])
                frame.locals.getOrCreate("ARGS", true, listOf(1000)).set(listOf(i), EraValue.of(args[i].toEraString()))
            }
        }
        callStack.add(frame)
        var returnValue: EraValue? = null
        try {
            var body = fn.body
            var restart = true
            while (restart) {
                restart = false
                try {
                    executeStatements(body)
                } catch (e: RestartSignal) {
                    body = fn.body
                    restart = true
                } catch (e: ReturnSignal) {
                    returnValue = e.value
                } catch (e: GotoSignal) {
                    val idx = fn.body.indexOfFirst {
                        it is LabelStatement && it.name.uppercase() == e.label.uppercase()
                    }
                    if (idx >= 0) {
                        body = fn.body.drop(idx + 1)
                        restart = true
                    } else {
                        throw e
                    }
                } catch (e: JumpSignal) {
                    // JUMP: 현재 함수를 종료하고 대상 함수를 새로 실행
                    callStack.removeLastOrNull()
                    callFunction(e.functionName, e.args)
                    return returnValue
                } catch (e: EraRuntimeException) {
                    // THROW 발생 시 현재 함수의 CATCH 블록을 찾아 실행 (tryBody 없는 것만 대상)
                    val catchIdx = fn.body.indexOfFirst { it is CatchStatement && (it as CatchStatement).tryBody.isEmpty() }
                    if (catchIdx >= 0) {
                        val catchStmt = fn.body[catchIdx] as CatchStatement
                        executeStatements(catchStmt.catchBody)
                    } else {
                        throw e  // CATCH 없으면 상위로 전파
                    }
                } catch (e: BeginException) {
                    if (name.uppercase().startsWith("EXE_PROLOGUE") || name.uppercase().startsWith("PROLOGUE_MENU")) {
                        android.util.Log.e("ERA_PROLOGUE2", "BeginException 발생: $name → ${e.target}")
                    }
                    throw e
                } catch (e: Exception) {
                    if (name.uppercase().startsWith("EXE_PROLOGUE") || name.uppercase().startsWith("PROLOGUE_MENU")) {
                        android.util.Log.e("ERA_PROLOGUE2", "예외발생: $name / ${e::class.simpleName}: ${e.message}")
                    }
                    throw e
                }
            }
        } finally {
            if (name.uppercase().startsWith("EXE_PROLOGUE") || name.uppercase().startsWith("PROLOGUE_MENU")) {
                android.util.Log.e("ERA_PROLOGUE2", "finally 진입: $name")
            }
            callStack.removeLastOrNull()
        }
        if (returnValue != null) {
            if (returnValue is EraValue.EraString) {
                scope.getOrCreate("RESULTS", true, listOf(100)).set(listOf(0), returnValue)
            } else {
                scope.getOrCreate("RESULT").set(listOf(0), returnValue)
            }
        }
        if (name.uppercase().startsWith("EXE_PROLOGUE") || name.uppercase().startsWith("PROLOGUE_MENU")) {
            android.util.Log.d("ERA_PROLOGUE2", "종료됨: $name / returnValue=$returnValue")
        }
        return returnValue
    }

    internal suspend fun executeGoto(statements: List<Statement>, label: String) {
        val idx = statements.indexOfFirst { it is LabelStatement && it.name.uppercase() == label.uppercase() }
        if (idx >= 0) {
            executeStatements(statements.drop(idx + 1))
        } else {
            throw GotoSignal(label) // propagate up
        }
    }

    internal suspend fun executeStatements(statements: List<Statement>) {
        var i = 0
        while (i < statements.size) {
            val stmt = statements[i]
            // CatchStatement: tryBody가 있으면 실행 (TRYCALL 래핑된 경우)
            // tryBody가 없으면 THROW/TRYCCALL 없이 도달한 것이므로 건너뜀
            if (stmt is CatchStatement) {
                if (stmt.tryBody.isNotEmpty()) {
                    // tryBody 실행: 내부 TryCatchSignal → catchBody 실행
                    try {
                        executeStatements(stmt.tryBody)
                    } catch (e: TryCatchSignal) {
                        executeStatements(stmt.catchBody)
                    } catch (e: EraRuntimeException) {
                        if (stmt.catchBody.isNotEmpty()) executeStatements(stmt.catchBody)
                        else throw e
                    }
                }
                // tryBody 없음 → 건너뜀 (낙하산 없이 CATCH에 도달한 경우)
                i++
                continue
            }
            try {
                executeStatement(stmt)
                i++
            } catch (e: GotoSignal) {
                val labelIdx = statements.indexOfFirst {
                    it is LabelStatement && it.name.uppercase() == e.label.uppercase()
                }
                if (labelIdx >= 0) i = labelIdx + 1
                else throw e
            } catch (e: TryCatchSignal) {
                // TRYCCALL/TRYCCALLFORM 실패 → 현재 위치 i 이후의 첫 CatchStatement로 점프
                // 단, tryBody가 없는(낡은 형식) CatchStatement만 대상
                val catchIdx = (i + 1 until statements.size).firstOrNull {
                    statements[it] is CatchStatement && (statements[it] as CatchStatement).tryBody.isEmpty()
                } ?: -1
                if (catchIdx >= 0) {
                    val catchStmt = statements[catchIdx] as CatchStatement
                    executeStatements(catchStmt.catchBody)
                    i = catchIdx + 1
                } else {
                    i++
                }
            } catch (e: EraRuntimeException) {
                // THROW 발생 → 현재 위치 i 이후의 첫 CATCH 블록 탐색
                val catchIdx = (i + 1 until statements.size).firstOrNull {
                    statements[it] is CatchStatement && (statements[it] as CatchStatement).tryBody.isEmpty()
                } ?: -1
                if (catchIdx >= 0) {
                    val catchStmt = statements[catchIdx] as CatchStatement
                    executeStatements(catchStmt.catchBody)
                    i = catchIdx + 1
                } else {
                    throw e
                }
            }
        }
    }


    // ─── 표현식 평가 / 변수 해석 (핵심 함수) ──────────────────────────────────

    internal suspend fun evalFormString(expr: Expression): String {
        val raw = when (expr) {
            is StringLiteral -> expr.value
            else -> evalExpr(expr).toEraString()
        }

        // PC 호환 특수 삼중 기호 치환
        // *** = NAME:TARGET, +++ = CALLNAME:MASTER, === = CALLNAME:PLAYER
        // /// = NAME:ASSI,   $$$ = CALLNAME:TARGET
        val preProcessed = raw
            .replace("***", run { val idx = scope.getOrCreate("TARGET").get(listOf(0)).toLong().toInt(); charaManager.getChara(idx)?.getStr("NAME") ?: "***" })
            .replace("+++", run { val idx = scope.getOrCreate("MASTER").get(listOf(0)).toLong().toInt(); charaManager.getChara(idx)?.getStr("CALLNAME") ?: "+++" })
            .replace("===", run { val idx = scope.getOrCreate("PLAYER").get(listOf(0)).toLong().toInt(); charaManager.getChara(idx)?.getStr("CALLNAME") ?: "===" })
            .replace("///", run { val idx = scope.getOrCreate("ASSI").get(listOf(0)).toLong().toInt(); charaManager.getChara(idx)?.getStr("NAME") ?: "///" })
            .replace("\$\$\$", run { val idx = scope.getOrCreate("TARGET").get(listOf(0)).toLong().toInt(); charaManager.getChara(idx)?.getStr("CALLNAME") ?: "\$\$\$" })

        // {표현식} 치환 — ERA의 {값, 너비} 및 {값, 너비, 정렬} 포맷 지원
        val result1 = StringBuilder()
        val r1 = Regex("\\{([^}]+)\\}")
        var last1 = 0
        for (match in r1.findAll(preProcessed)) {
            result1.append(preProcessed, last1, match.range.first)
            result1.append(try {
                val inner = match.groupValues[1].trim()
                var depth = 0
                val commaPos = mutableListOf<Int>()
                for (ci in inner.indices) {
                    when (inner[ci]) { '(', '[' -> depth++; ')', ']' -> depth-- }
                    if (inner[ci] == ',' && depth == 0) commaPos.add(ci)
                }
                val (exprStr, widthStr, alignStr) = when (commaPos.size) {
                    0 -> Triple(inner, null, null)
                    1 -> Triple(inner.substring(0, commaPos[0]).trim(), inner.substring(commaPos[0]+1).trim(), "RIGHT")
                    else -> Triple(inner.substring(0, commaPos[0]).trim(), inner.substring(commaPos[0]+1, commaPos[1]).trim(), inner.substring(commaPos[1]+1).trim().uppercase())
                }
                val tokens = Lexer(exprStr).tokenize().filter { it.type != TokenType.COMMENT && it.type != TokenType.EOF }
                val value = evalExpr(Parser(tokens).parseExpression())
                val str = if (value is EraValue.EraString) value.value else value.toEraString()
                if (widthStr != null) {
                    val width = widthStr.toLongOrNull()?.toInt() ?: 0
                    if (width > 0) {
                        val dw = eraDisplayWidth(str)
                        if (dw < width) { val pad = " ".repeat(width - dw); if (alignStr == "LEFT") str + pad else pad + str }
                        else str
                    } else str
                } else str
            } catch (e: Exception) {
                android.util.Log.w("ERA_FORM", "evalFormString {} 치환 실패: '${match.value}' → ${e.message}")
                ""
            })
            last1 = match.range.last + 1
        }
        result1.append(preProcessed, last1, preProcessed.length)

        // %표현식% 치환 — ERA의 %값, 너비, 정렬% 포맷 지원
        val result2 = StringBuilder()
        val r2 = Regex("%([^%]+)%")
        var last2 = 0
        val s1 = result1.toString()
        for (match in r2.findAll(s1)) {
            result2.append(s1, last2, match.range.first)
            result2.append(try {
                val inner = match.groupValues[1].trim()
                var depth = 0
                val commaPos = mutableListOf<Int>()
                for (ci in inner.indices) {
                    when (inner[ci]) { '(', '[' -> depth++; ')', ']' -> depth-- }
                    if (inner[ci] == ',' && depth == 0) commaPos.add(ci)
                }
                val (exprStr, widthStr, alignStr) = when (commaPos.size) {
                    0 -> Triple(inner, null, null)
                    1 -> Triple(inner.substring(0, commaPos[0]).trim(), inner.substring(commaPos[0]+1).trim(), "RIGHT")
                    else -> Triple(inner.substring(0, commaPos[0]).trim(), inner.substring(commaPos[0]+1, commaPos[1]).trim(), inner.substring(commaPos[1]+1).trim().uppercase())
                }
                val tokens = Lexer(exprStr).tokenize().filter { it.type != TokenType.COMMENT && it.type != TokenType.EOF }
                val value = evalExpr(Parser(tokens).parseExpression())
                val str = if (value is EraValue.EraString) value.value else value.toEraString()
                if (widthStr != null) {
                    val width = widthStr.toLongOrNull()?.toInt() ?: 0
                    if (width > 0) {
                        val dw = eraDisplayWidth(str)
                        when {
                            dw < width -> { val pad = " ".repeat(width - dw); if (alignStr == "RIGHT") pad + str else str + pad }
                            dw > width -> eraSubstringByWidth(str, width)
                            else -> str
                        }
                    } else str
                } else str
            } catch (e: Exception) {
                android.util.Log.w("ERA_FORM", "evalFormString %% 치환 실패: '${match.value}' → ${e.message}")
                ""
            })
            last2 = match.range.last + 1
        }
        result2.append(s1, last2, s1.length)
        var result = result2.toString()

        // %변수% 치환 결과에 {..} 포맷이 남아있으면 재평가
        // 예: ARGS:0 = "({LOCAL:0+LOCAL:2}/2)" → %ARGS:0% → {..} 미평가 상태
        if (result.contains('{') && result.contains('}')) {
            val reResult = StringBuilder()
            val rRe = Regex("\\{([^}]+)\\}")
            var lastRe = 0
            for (match in rRe.findAll(result)) {
                reResult.append(result, lastRe, match.range.first)
                reResult.append(try {
                    val inner = match.groupValues[1].trim()
                    var depth = 0
                    val commaPos = mutableListOf<Int>()
                    for (ci in inner.indices) {
                        when (inner[ci]) { '(', '[' -> depth++; ')', ']' -> depth-- }
                        if (inner[ci] == ',' && depth == 0) commaPos.add(ci)
                    }
                    val (exprStr, widthStr, alignStr) = when (commaPos.size) {
                        0 -> Triple(inner, null, null)
                        1 -> Triple(inner.substring(0, commaPos[0]).trim(), inner.substring(commaPos[0]+1).trim(), "RIGHT")
                        else -> Triple(inner.substring(0, commaPos[0]).trim(), inner.substring(commaPos[0]+1, commaPos[1]).trim(), inner.substring(commaPos[1]+1).trim().uppercase())
                    }
                    val tokens = com.eraandroid.core.lexer.Lexer(exprStr).tokenize().filter { it.type != com.eraandroid.core.lexer.TokenType.COMMENT && it.type != com.eraandroid.core.lexer.TokenType.EOF }
                    val value = evalExpr(com.eraandroid.core.parser.Parser(tokens).parseExpression())
                    val str = if (value is EraValue.EraString) value.value else value.toEraString()
                    if (widthStr != null) {
                        val width = widthStr.toLongOrNull()?.toInt() ?: 0
                        if (width > 0) {
                            val dw = eraDisplayWidth(str)
                            when {
                                dw < width -> { val pad = " ".repeat(width - dw); if (alignStr == "RIGHT") pad + str else str + pad }
                                dw > width -> eraSubstringByWidth(str, width)
                                else -> str
                            }
                        } else str
                    } else str
                } catch (e: Exception) { match.value })
                lastRe = match.range.last + 1
            }
            reResult.append(result, lastRe, result.length)
            result = reResult.toString()
        }
        // @조건?참값#거짓값@ 패턴 치환
        // \@ ... \@ 블록을 재귀적으로 처리. 내부에 중첩 \@...\@ 가 있어도 올바르게 처리.
        val PH = '\u0001'
        val escaped = result.replace("\\" + "@", PH.toString())
        var i = 0
        val sb2 = StringBuilder()
        while (i < escaped.length) {
            if (escaped[i] == PH) {
                // 매칭 끝 PH 탐색: () 와 PH블록 모두 depth 추적
                var depth = 0
                var j = i + 1
                while (j < escaped.length) {
                    when (escaped[j]) {
                        '(' -> depth++
                        ')' -> depth--
                        PH  -> {
                            if (depth == 0) break   // 매칭 끝
                            else depth--             // 내부 블록 닫힘
                        }
                    }
                    j++
                }
                if (j < escaped.length) {
                    val inner = escaped.substring(i + 1, j)
                    val qIdx = inner.indexOf('?')
                    // # 탐색: 최상위 레벨(내부 PH 블록 밖)의 마지막 # 만 사용
                    val hIdx = run {
                        var d = 0
                        var found = -1
                        for (ci in inner.indices) {
                            when (inner[ci]) {
                                '(' -> d++
                                ')' -> d--
                                PH  -> if (d == 0) d++ else d--
                                '#' -> if (d == 0) found = ci
                            }
                        }
                        found
                    }
                    if (qIdx >= 0 && hIdx > qIdx) {
                        val condStr  = inner.substring(0, qIdx).replace(PH, '@').trim()
                        val trueStr  = inner.substring(qIdx + 1, hIdx).replace(PH, '@').trim()
                        val falseStr = inner.substring(hIdx + 1).replace(PH, '@').trim()
                        try {
                            val tokens = Lexer(condStr).tokenize()
                                .filter { it.type != TokenType.COMMENT && it.type != TokenType.EOF }
                            val condExpr = Parser(tokens).parseExpression()
                            val chosen = if (evalExpr(condExpr).toLong() != 0L) trueStr else falseStr
                            sb2.append(evalFormString(StringLiteral(chosen, 0)))
                        } catch (e: Exception) {
                            sb2.append(inner.replace(PH, '@'))
                        }
                    } else {
                        sb2.append(inner.replace(PH, '@'))
                    }
                    i = j + 1
                } else {
                    sb2.append('@')
                    i++
                }
            } else if (escaped[i] == '@') {
                sb2.append('@')
                i++
            } else {
                sb2.append(escaped[i])
                i++
            }
        }
        return sb2.toString()
    }


    suspend fun evalExpr(expr: Expression): EraValue {
        return when (expr) {
            is IntLiteral -> EraValue.of(expr.value)
            is FloatLiteral -> EraValue.of(expr.value)
            is StringLiteral -> EraValue.of(expr.value)
            is FormString -> {
                val sb = StringBuilder()
                for (part in expr.parts) {
                    sb.append(when (part) {
                        is TextPart -> part.text
                        is EmbedPart -> evalExpr(part.expr).toEraString()
                    })
                }
                EraValue.of(sb.toString())
            }
            is VariableRef -> resolveVar(expr)
            is BinaryOp -> evalBinary(expr)
            is UnaryOp -> evalUnary(expr)
            is TernaryOp -> {
                if (evalExpr(expr.condition).toLong() != 0L) evalExpr(expr.trueBranch)
                else evalExpr(expr.falseBranch)
            }
            is FunctionCall -> evalBuiltinOrCall(expr)
        }
    }

    internal suspend fun resolveVar(expr: Expression): EraValue {
        if (expr !is VariableRef) return evalExpr(expr)
        val variable = resolveVarRef(expr) ?: return EraValue.ZERO
        val indices = expr.indices.map { evalExpr(it).toLong().toInt() }
        return variable.get(indices)
    }

    /**
     * ERA 캐릭터 인덱스 표현식을 평가.
     * MASTER/ASSI/TARGET/PLAYER → 해당 스코프 변수의 값(charaIdx)
     * ARG/LOCAL 등 함수 파라미터 → 로컬 스코프에서 값(charaIdx) 읽기
     * 그 외 → evalExpr로 직접 평가
     */

    internal suspend fun resolveCharaIdx(expr: Expression): Int {
        if (expr is VariableRef) {
            val upper = expr.name.uppercase()
            // ARG:N, LOCAL:N 형태 — 인덱스가 있으면 해당 인덱스 값을 charaIdx로 사용
            val specialMap = mapOf(
                "MASTER" to "MASTER", "ASSI" to "ASSI",
                "TARGET" to "TARGET", "PLAYER" to "PLAYER"
            )
            if (upper in specialMap) {
                return scope.getOrCreate(upper).get(listOf(0)).toLong().toInt()
            }
            // ARG, ARG:N, LOCAL, LOCAL:N → 로컬 스코프 우선 조회
            if (upper == "ARG" || upper == "LOCAL") {
                val idx = if (expr.indices.isNotEmpty()) evalExpr(expr.indices[0]).toLong().toInt() else 0
                val v = callStack.lastOrNull()?.locals?.get(upper)
                    ?: scope.getOrCreate(upper)
                return v.get(listOf(idx)).toLong().toInt()
            }
        }
        return evalExpr(expr).toLong().toInt()
    }

    internal suspend fun resolveVarRef(ref: VariableRef): EraVariable? {
        val name = ref.name.uppercase()

        // ↓ 임시 디버그 로그 추가
        if (name == "GAMEBASE_VERSION") {
            val v = scope.get(name)
        }

        // Check special built-in variables
        if (name == "RAND") {
            val maxExpr = ref.indices.firstOrNull()
            val max = if (maxExpr != null) evalExpr(maxExpr).toLong() else 100L
            return object : EraVariable("RAND", false, emptyList()) {
                override fun get(indices: List<Int>): EraValue = EraValue.of(Random.nextLong(max))
            }
        }

        // NO:charaIdx → 해당 캐릭터의 .no 번호 반환 (읽기 전용)
        if (name == "NO") {
            val charaIdx = if (ref.indices.isNotEmpty())
                evalExpr(ref.indices[0]).toLong().toInt()
            else
                scope.getOrCreate("TARGET").get(listOf(0)).toLong().toInt()
            return object : EraVariable("NO", false, emptyList()) {
                override fun get(indices: List<Int>): EraValue =
                    EraValue.of((charaManager.getChara(charaIdx)?.no ?: -1).toLong())
                override fun set(indices: List<Int>, value: EraValue) { /* 읽기 전용 */ }
            }
        }

        // CHARANUM → 현재 charaManager에 등록된 캐릭터 수
        if (name == "CHARANUM") {
            return object : EraVariable("CHARANUM", false, emptyList()) {
                override fun get(indices: List<Int>): EraValue = EraValue.of(charaManager.count.toLong())
                override fun set(indices: List<Int>, value: EraValue) { /* 읽기 전용 */ }
            }
        }

        // 캐릭터별 변수: BASE, MAXBASE, TCVAR, SOURCE, EX, CUP, CDOWN 등
        // 형태: BASE:charaIndex:varIndex 또는 BASE:varIndex (TARGET 기준)
        // TEQUIP/STAIN/EQUIP/NOWEX: 2인덱스(캐릭터 지정)만 CharaData, 1인덱스는 scope 글로벌
        val twoIdxVars = setOf("TEQUIP","STAIN","EQUIP","NOWEX","DOWNBASE","LOSEBASE")
        if (name in twoIdxVars && ref.indices.size >= 2) {
            val ci = resolveCharaIdx(ref.indices[0])
            val vi = evalExpr(ref.indices[1]).toLong().toInt()
            return object : EraVariable(name, false, emptyList()) {
                override fun get(indices: List<Int>): EraValue =
                    EraValue.of(charaManager.getChara(ci)?.getNum(name, vi) ?: 0L)
                override fun set(indices: List<Int>, value: EraValue) {
                    charaManager.getChara(ci)?.setNum(name, vi, value.toLong())
                }
            }
        }
        // TEQUIP/STAIN/EQUIP/NOWEX 1인덱스 이하 → scope 글로벌로 fallthrough

        val charaVarNames = setOf(
            "BASE","MAXBASE","DOWNBASE","LOSEBASE",
            "TCVAR","SOURCE","EX","CUP","CDOWN",
            "ABL","TALENT","EXP","MARK","PALAM","JUEL","GOTJUEL",
            "RELATION","CFLAG","CSTR","ISASSI",
            "NAME","CALLNAME","NICKNAME","MASTERNAME"
        )
        if (name in charaVarNames) {
            val isStr = name in setOf("CSTR","NAME","CALLNAME","NICKNAME","MASTERNAME")
            if (ref.indices.size >= 2) {
                // BASE:charaIdx:varIdx
                // ARG/LOCAL 등 함수 파라미터를 charaIdx로 쓰는 경우도 evalExpr가 로컬 스코프를 봐서 올바른 값을 반환
                val charaIdx = resolveCharaIdx(ref.indices[0])
                val varIdx   = evalExpr(ref.indices[1]).toLong().toInt()
                return object : EraVariable(name, isStr, emptyList()) {
                    override fun get(indices: List<Int>): EraValue {
                        val chara = charaManager.getChara(charaIdx) ?: return if (isStr) EraValue.EMPTY_STRING else EraValue.ZERO
                        return if (isStr) EraValue.of(chara.getStr(name, varIdx))
                        else EraValue.of(chara.getNum(name, varIdx))
                    }
                    override fun set(indices: List<Int>, value: EraValue) {
                        val chara = charaManager.getChara(charaIdx) ?: return
                        if (isStr) chara.setStr(name, varIdx, value.toEraString())
                        else chara.setNum(name, varIdx, value.toLong())
                    }
                }
            } else if (ref.indices.size == 1) {
                // ERA 1인덱스 규칙:
                //   NAME/CALLNAME/NICKNAME/MASTERNAME:N → charas[N] 의 해당 문자열 (N=charaIdx)
                //   CFLAG:ARG, TALENT:ARG 등 함수 파라미터를 charaIdx로 쓰는 경우 → charaIdx 모드
                //   BASE:N, ABL:N, PALAM:N 등 순수 숫자 → TARGET 캐릭터의 varIdx=N
                val rawIdx = ref.indices[0]
                // ARG, ARG:N, LOCAL, LOCAL:N, MASTER, ASSI, TARGET, PLAYER 등은 charaIdx 모드
                val isCharaRef = rawIdx is VariableRef && rawIdx.name.uppercase() in
                        setOf("MASTER", "ASSI", "TARGET", "PLAYER", "ARG", "LOCAL",
                            "MASTER_SLAVE", "PLAYER")
                val isStrVar = name in setOf("NAME","CALLNAME","NICKNAME","MASTERNAME","CSTR")
                // CSTR:3 처럼 숫자 리터럴이면 TARGET 캐릭터의 varIdx 모드
                // NAME:MASTER, CALLNAME:TARGET 등 charaRef는 항상 charaIdx 모드
                val isNumericLiteral = rawIdx is IntLiteral
                val useCstrVarIdx = name == "CSTR" && isNumericLiteral
                if ((isCharaRef || isStrVar) && !useCstrVarIdx) {
                    // charaIdx 모드: CFLAG:ARG, TALENT:ARG, NAME:MASTER 등
                    val charaIdx = resolveCharaIdx(rawIdx)
                    val varIdx = 0
                    return object : EraVariable(name, isStr, emptyList()) {
                        override fun get(indices: List<Int>): EraValue {
                            val chara = charaManager.getChara(charaIdx) ?: return if (isStr) EraValue.EMPTY_STRING else EraValue.ZERO
                            return if (isStr) EraValue.of(chara.getStr(name, varIdx))
                            else EraValue.of(chara.getNum(name, varIdx))
                        }
                        override fun set(indices: List<Int>, value: EraValue) {
                            val chara = charaManager.getChara(charaIdx) ?: return
                            if (isStr) chara.setStr(name, varIdx, value.toEraString())
                            else chara.setNum(name, varIdx, value.toLong())
                        }
                    }
                } else {
                    // varIdx 모드: BASE:2, ABL:0, PALAM:10 등 순수 숫자 → TARGET 캐릭터의 해당 인덱스
                    val varIdx = evalExpr(rawIdx).toLong().toInt()
                    val charaIdxProvider = { scope.getOrCreate("TARGET").get(listOf(0)).toLong().toInt() }
                    return object : EraVariable(name, isStr, emptyList()) {
                        override fun get(indices: List<Int>): EraValue {
                            val chara = charaManager.getChara(charaIdxProvider()) ?: return if (isStr) EraValue.EMPTY_STRING else EraValue.ZERO
                            return if (isStr) EraValue.of(chara.getStr(name, varIdx))
                            else EraValue.of(chara.getNum(name, varIdx))
                        }
                        override fun set(indices: List<Int>, value: EraValue) {
                            val chara = charaManager.getChara(charaIdxProvider()) ?: return
                            if (isStr) chara.setStr(name, varIdx, value.toEraString())
                            else chara.setNum(name, varIdx, value.toLong())
                        }
                    }
                }
            } else if (ref.indices.isEmpty() && name in setOf("MASTERNAME","CALLNAME","NICKNAME","NAME")) {
                // 인덱스 없이 단독 사용 — MASTER(0번) 기준
                val charaIdx = scope.getOrCreate("MASTER").get(listOf(0)).toLong().toInt()
                return object : EraVariable(name, isStr, emptyList()) {
                    override fun get(indices: List<Int>): EraValue {
                        val chara = charaManager.getChara(charaIdx) ?: return if (isStr) EraValue.EMPTY_STRING else EraValue.ZERO
                        return if (isStr) EraValue.of(chara.getStr(name, 0))
                        else EraValue.of(chara.getNum(name, 0))
                    }
                    override fun set(indices: List<Int>, value: EraValue) {
                        val chara = charaManager.getChara(charaIdx) ?: return
                        if (isStr) chara.setStr(name, 0, value.toEraString())
                        else chara.setNum(name, 0, value.toLong())
                    }
                }
            }
        }

        // Check call frame locals first
        val frame = callStack.lastOrNull()
        if (frame != null) {
            frame.locals.get(name)?.let { return it }
        }

        // #DEFINE 매크로 확인
        val defVal = scope.getDefine(name)
        if (defVal != null) {
            val defEra = defVal.trim().toLongOrNull()?.let { EraValue.of(it) } ?: EraValue.of(defVal.trim())
            return object : EraVariable(name, defEra is EraValue.EraString, emptyList()) {
                override fun get(indices: List<Int>): EraValue = defEra
                override fun set(indices: List<Int>, value: EraValue) {}
            }
        }

        val result = scope.get(name) ?: scope.getOrCreate(name)
        return result
    }

    // 변수가 실제로 정의되어 있는지 확인 (getOrCreate 하지 않음)
    internal fun isVarDefined(name: String): Boolean {
        val upper = name.uppercase()
        val frame = callStack.lastOrNull()
        if (frame != null && frame.locals.get(upper) != null) return true
        return scope.get(upper) != null
    }

    internal suspend fun setVar(expr: Expression, value: EraValue) {
        if (expr !is VariableRef) return
        val variable = resolveVarRef(expr) ?: return
        val indices = expr.indices.map { evalExpr(it).toLong().toInt() }
        variable.set(indices, value)
    }



    // ─── Statement / Expression 실행 → StatementExecutor.kt 위임 ──────────────
    private suspend fun executeStatement(stmt: Statement) = executeStatement_ext(stmt)
    private suspend fun executePrint(stmt: PrintStatement) = executePrint_ext(stmt)
    private suspend fun executeVarSet(stmt: VarSetStatement) = executeVarSet_ext(stmt)
    private suspend fun executeAssign(stmt: AssignStatement) = executeAssign_ext(stmt)
    private suspend fun executeIf(stmt: IfStatement) = executeIf_ext(stmt)
    private suspend fun executeFor(stmt: ForStatement) = executeFor_ext(stmt)
    private suspend fun executeWhile(stmt: WhileStatement) = executeWhile_ext(stmt)
    private suspend fun executeDoLoop(stmt: DoLoopStatement) = executeDoLoop_ext(stmt)
    private suspend fun executeRepeat(stmt: RepeatStatement) = executeRepeat_ext(stmt)
    private suspend fun executeSelectCase(stmt: SelectCaseStatement) = executeSelectCase_ext(stmt)
    private suspend fun executeInput(stmt: InputStatement) = executeInput_ext(stmt)
    internal fun eraEquals(a: EraValue, b: EraValue): Boolean = eraEquals_ext(a, b)
    private fun flushPrintcBuffer() = flushPrintcBuffer_ext()
    private suspend fun evalBinary(expr: BinaryOp): EraValue = evalBinary_ext(expr)
    private suspend fun evalUnary(expr: UnaryOp): EraValue = evalUnary_ext(expr)

    private suspend fun evalBuiltinOrCall(expr: FunctionCall): EraValue =
        evalBuiltinOrCall_impl(expr)

    // ─── Save/Load State ─────────────────────────────────────────────────────

    fun getGameState(): GameState = GameState(
        variableSnapshot = scope.snapshot(),
        charaSnapshot = charaManager.snapshot()
    )

    fun restoreGameState(state: GameState) {
        // 정적 CSV 데이터(이름 테이블, STR 등)는 세이브 복원 시 덮어쓰지 않음
        val staticVars = setOf(
            "STR", "ABLNAME", "TALENTNAME", "EXPNAME", "MARKNAME", "PALAMNAME",
            "TRAINNAME", "ITEMNAME", "FLAGNAME", "TFLAGNAME", "CFLAGNAME",
            "BASENAME", "STAINNAME", "SOURCENAME", "EXNAME", "TEQUIPNAME",
            "EQUIPNAME", "NOWEXNAME", "ITEMPRICE",
            "GAMEBASE_TITLE", "GAMEBASE_AUTHOR", "GAMEBASE_VERSION",
            "GAMEBASE_YEAR", "GAMEBASE_INFO"
        )
        val filtered = state.variableSnapshot.filterKeys { it.uppercase() !in staticVars }
        scope.restore(filtered)
        charaManager.restore(state.charaSnapshot)
    }

    fun getScope() = scope

    // CSV 로딩 완료 후 GameViewModel에서 호출 - 정적 데이터를 별도 보관
    internal val staticVarNames = listOf(
        "STR", "ITEMPRICE", "ITEMNAME", "ABLNAME", "TALENTNAME",
        "EXPNAME", "MARKNAME", "PALAMNAME", "TRAINNAME", "FLAGNAME", "TFLAGNAME",
        "CFLAGNAME", "BASENAME", "STAINNAME", "SOURCENAME", "EXNAME",
        "TEQUIPNAME", "EQUIPNAME", "NOWEXNAME",
        "GAMEBASE_TITLE", "GAMEBASE_AUTHOR", "GAMEBASE_VERSION",
        "GAMEBASE_YEAR", "GAMEBASE_INFO"
    )

    fun saveCsvStaticSnapshot() {
        csvStaticSnapshot = staticVarNames.mapNotNull { name ->
            scope.get(name)?.let { v -> name to v.getAll().toList() }
        }.toMap()
        android.util.Log.d("ERA_CSV", "csvStaticSnapshot 저장: ITEMPRICE:0=${csvStaticSnapshot["ITEMPRICE"]?.getOrNull(0)?.toLong()} ITEMPRICE:101=${csvStaticSnapshot["ITEMPRICE"]?.getOrNull(101)?.toLong()}")
    }
    fun getCharaManager() = charaManager
    fun getFunctions() = functions.toMap()


    internal fun calcPalamLv(curVal: Long, maxLv: Int, varName: String): Long {
        val lv = scope.get(varName)
        if (lv != null) {
            var result = 0L
            for (i in 0 until maxLv) {
                val t = lv.get(listOf(i + 1)).toLong()
                if (t > 0L && curVal < t) return result
                if (t > 0L) result = (i + 1).toLong()
            }
            return result
        }
        val def = longArrayOf(0,100,500,3000,10000,30000,60000,100000,150000,250000)
        var result = 0L
        for (i in 0 until def.size - 1) { if (curVal < def[i+1]) return result; result = (i+1).toLong() }
        return result
    }

    // ── 문자 너비/바이트 유틸 → EraStringUtils 위임 ─────────────────────────────
    fun eraByteWidth(s: String) = EraStringUtils.eraByteWidth(s)
    private fun eraSubstringByByte(s: String, startByte: Int, lenByte: Int) =
        EraStringUtils.eraSubstringByByte(s, startByte, lenByte)
    private fun eraByteToCharIndex(s: String, byteOffset: Int) =
        EraStringUtils.eraByteToCharIndex(s, byteOffset)
    private fun eraDisplayWidth(s: String) = EraStringUtils.eraDisplayWidth(s)
    private fun eraSubstringByWidth(s: String, maxWidth: Int) =
        EraStringUtils.eraSubstringByWidth(s, maxWidth)
}

data class GameState(
    val variableSnapshot: Map<String, VariableSnapshot>,
    val charaSnapshot: List<CharaSnapshot>
)