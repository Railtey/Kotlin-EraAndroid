package com.eraandroid.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eraandroid.core.interpreter.EraInterpreter
import com.eraandroid.core.interpreter.EngineEvent
import com.eraandroid.core.lexer.Lexer
import com.eraandroid.core.parser.Parser
import com.eraandroid.data.csv.GameDataLoader
import com.eraandroid.data.save.SaveManager
import com.eraandroid.data.save.SaveSlotInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import com.eraandroid.core.vm.EraValue

// ─── UI State ─────────────────────────────────────────────────────────────────
data class TextLine(
    val spans: List<TextSpan>,
    val alignment: TextAlignment = TextAlignment.LEFT
)

data class TextSpan(
    val text: String,
    val color: Int = 0xFFFFFFFF.toInt(),
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isButton: Boolean = false,
    val buttonValue: Long = 0L
)

enum class TextAlignment { LEFT, CENTER, RIGHT }

data class GameUiState(
    val lines: List<TextLine> = emptyList(),
    val isWaitingInput: Boolean = false,
    val inputIsNumber: Boolean = true,
    val isWaitAnyKey: Boolean = false,
    val activeInputLineIndex: Int = 0,  // 현재 입력 대기 시작 시점의 lines.size — 이 이전 버튼은 클릭 불가
    val bgColor: Int = 0xFF000000.toInt(),
    val currentColor: Int = 0xFFFFFFFF.toInt(),
    val alignment: TextAlignment = TextAlignment.LEFT,
    val statusMessage: String = "",
    val isLoading: Boolean = false,
    val loadingStatus: String = "",
    val errorMessage: String? = null
)

// ─── ViewModel ────────────────────────────────────────────────────────────────
class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()
    var drawLineWidth: Int = 40

    // drawLineWidth 실측 후 호출 — PRINTC 열 수/너비를 화면에 맞게 설정
    fun applyPrintcLayout() {
        val interp = interpreter ?: run {
            android.util.Log.w("ERA_PRINTC", "applyPrintcLayout: interpreter is null, drawLineWidth=$drawLineWidth")
            return
        }
        val totalCols = drawLineWidth.coerceIn(20, 200)
        // drawLineWidth = 반각 문자 수 기준
        // 한글 버튼 1개 표시 너비 ≈ 반각 26자
        // 2열: 반각 40자 이상이면 가능 (여유 있게)
        val cols = when {
            totalCols >= 130 -> 4
            totalCols >= 80  -> 3
            totalCols >= 40  -> 2
            else             -> 1
        }
        val width = totalCols / cols
        android.util.Log.d("ERA_PRINTC", "applyPrintcLayout: drawLineWidth=$drawLineWidth totalCols=$totalCols cols=$cols width=$width")
        interp.PRINTC_COLS  = cols
        interp.PRINTC_WIDTH = width
    }
    private val _saveSlots = MutableStateFlow<List<SaveSlotInfo>>(emptyList())
    val saveSlots: StateFlow<List<SaveSlotInfo>> = _saveSlots.asStateFlow()

    private var interpreter: EraInterpreter? = null
    // GameViewModel 클래스 필드에 추가
    private var gameJob: kotlinx.coroutines.Job? = null
    private val saveManager = SaveManager(application)
    private val dataLoader = GameDataLoader(application)

    // Rendering state
    private var currentLines = mutableListOf<TextLine>()
    private var currentSpans = mutableListOf<TextSpan>()
    private var currentColor = 0xFFFFFFFF.toInt()
    private var currentBgColor = 0xFF000000.toInt()
    private var currentAlignment = TextAlignment.LEFT
    private var isBold = false
    private var isItalic = false
    private var pendingSaveSlot = -1
    private var pendingLoadSlot = -1

    // ─── Game Loading ─────────────────────────────────────────────────────────

    fun loadGame(gameDir: File) {
        viewModelScope.launch {
            saveManager.importSavFiles(gameDir)  // ← 이 줄 추가
        }
        loadGameInternal(
            listErbFiles = { gameDir.walkTopDown().filter { it.isFile && it.extension.uppercase() == "ERB" }.sortedBy { it.name.uppercase() }.toList() },
            readFile = { file ->
                try { file.readText(charset("Shift_JIS")) }
                catch (e: Exception) { file.readText(Charsets.UTF_8) }
            },
            csvLoader = { scope, charaManager -> dataLoader.loadFromDirectory(gameDir, scope, charaManager) }
        )

    }

    fun goToTitle() {
        val interp = interpreter ?: return
        gameJob?.cancel()
        interp.cancelAndRestart()
        currentLines.clear()
        currentSpans.clear()
        _uiState.update { it.copy(activeInputLineIndex = 0) }
        updateUi()
        gameJob = viewModelScope.launch(Dispatchers.Default) {
            interp.start("SYSTEM_TITLE")
        }
    }

    fun goToMain() {
        gameJob?.cancel()
        gameJob = null
        interpreter?.cancelAndRestart()
        interpreter = null
        currentLines.clear()
        currentSpans.clear()
        _uiState.update { it.copy(isLoading = false, isWaitingInput = false, isWaitAnyKey = false, activeInputLineIndex = 0, statusMessage = "") }
        updateUi()
    }


    fun loadGameFromDocumentUri(uri: android.net.Uri, context: android.content.Context) {
        val rootDoc = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri) ?: run {
            appendLine("[ERROR] 폴더를 열 수 없습니다", 0xFFFF4444.toInt())
            return
        }

        viewModelScope.launch {
            saveManager.importSavFilesFromDocument(rootDoc, context)
        }

        loadGameInternal(
            listErbFiles = {
                val erbFiles = mutableListOf<android.net.Uri>()
                fun scan(doc: androidx.documentfile.provider.DocumentFile) {
                    doc.listFiles().forEach { child ->
                        if (child.isDirectory) scan(child)
                        else if (child.name?.uppercase()?.endsWith(".ERB") == true) {
                            erbFiles.add(child.uri)
                        }
                    }
                }
                // ERB 폴더 찾기
                val erbDir = rootDoc.listFiles().firstOrNull {
                    it.isDirectory && it.name?.uppercase() == "ERB"
                } ?: rootDoc
                scan(erbDir)
                erbFiles.sortBy { it.toString().substringAfterLast("/").uppercase() }
                erbFiles
            },
            readFile = { fileUri ->
                context.contentResolver.openInputStream(fileUri as android.net.Uri)
                    ?.use { stream ->
                        val bytes = stream.readBytes()
                        // BOM 제거 후 인코딩 감지
                        val (cleanBytes, charset) = when {
                            bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() ->
                                Pair(bytes.drop(3).toByteArray(), Charsets.UTF_8)
                            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
                                Pair(bytes.drop(2).toByteArray(), Charsets.UTF_16LE)
                            else ->
                                Pair(bytes, charset("Shift_JIS"))
                        }
                        String(cleanBytes, charset)
                    } ?: ""
            },
            csvLoader = { scope, charaManager ->
                // CSV 폴더 찾기
                val csvDir = rootDoc.listFiles().firstOrNull {
                    it.isDirectory && it.name?.uppercase() == "CSV"
                }
                if (csvDir != null) {
                    dataLoader.loadFromDocumentFile(csvDir, scope, charaManager, context)
                }
                null
            }
        )
    }

    private fun <T> loadGameInternal(
        listErbFiles: () -> List<T>,
        readFile: (T) -> String,
        csvLoader: suspend (com.eraandroid.core.vm.VariableScope, com.eraandroid.core.vm.CharaManager) -> Any?
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadingStatus = "초기화 중...") }
            try {
                val interp = EraInterpreter()
                _uiState.update { it.copy(loadingStatus = "CSV 데이터 로딩 중...") }
                withContext(Dispatchers.IO) { csvLoader(interp.getScope(), interp.getCharaManager()) }
                // CSV 로딩 완료 후 MASTER 캐릭터(no=0) 등록 — 템플릿이 있으면 그 값으로, 없으면 빈 캐릭터
                interp.getCharaManager().addChara(0)
                val c0 = interp.getCharaManager().getChara(0)
                android.util.Log.d("ERA_CHARA0", "addChara후: CSTR1=${c0?.getStr("CSTR",1)} BASE0=${c0?.getNum("BASE",0)}")

                val erbFiles = withContext(Dispatchers.IO) { listErbFiles() }
                val totalFiles = erbFiles.size
                for ((fileIdx, file) in erbFiles.withIndex()) {
                    val fn = if (file is File) (file as File).name else file.toString().substringAfterLast("/")
                    _uiState.update { it.copy(loadingStatus = "ERB 파싱 중 (${fileIdx + 1}/$totalFiles)\n$fn") }
                    try {
                        val source = withContext(Dispatchers.IO) { readFile(file) }
                        if (source.isEmpty()) {
                            continue
                        }
                        // 첫 번째 파일 내용 일부 출력
                        if (interp.getFunctions().isEmpty()) {
                        }
                        val lexer = com.eraandroid.core.lexer.Lexer(source)
                        val tokens = lexer.tokenize()
                            .filter { it.type != com.eraandroid.core.lexer.TokenType.COMMENT }
                        val fileName = if (file is File) (file as File).name else file.toString().substringAfterLast("/")
                        val ast = com.eraandroid.core.parser.Parser(tokens, fileName, lexer.sourceLines).parse()
                        if (ast.functions.isNotEmpty()) {
                            val fn = if (file is File) (file as File).name else file.toString().substringAfterLast("/")
                            if (fn.contains("TORIKOMODE", ignoreCase = true)) {
                                android.util.Log.d("ERA_TORIKO", "TORIKOMODE 파싱 성공! 함수 수: ${ast.functions.size}")
                                android.util.Log.d("ERA_TORIKO", "마지막 함수: ${ast.functions.lastOrNull()?.name}")  // ← 이 줄 추가
                                android.util.Log.d("ERA_TORIKO", "함수 목록: ${ast.functions.map { it.name }.take(10)}")
                            }
                            interp.loadProgram(ast)
                        } else {
                            val fn = if (file is File) (file as File).name else file.toString().substringAfterLast("/")
                            android.util.Log.w("ERA_EMPTY", "함수 없음 (ast.functions 비어있음): $fn")
                        }
                    } catch (e: Exception) {
                        val name = if (file is File) file.name else file.toString().substringAfterLast("/")
                        appendLine("[PARSE ERROR in $name]: ${e.message}", 0xFFFF4444.toInt())
                        android.util.Log.e("ERA_PARSE1", "Error in $name: ${e.message}", e)
                    }
                }
                // CSV 정적 데이터 스냅샷 저장 (RESETDATA 시 복구용)
                interp.saveCsvStaticSnapshot()
                // GameBase.CSV에서 읽은 코드/버전을 SaveManager에 동적 주입
                val gbScope = interp.getScope()
                val gbCode    = gbScope.get("GAMEBASE_CODE")?.get(listOf(0))?.toLong() ?: 0L
                val gbVersion = gbScope.get("GAMEBASE_VERSION")?.get(listOf(0))?.toLong() ?: 0L
                val gbAllow   = gbScope.get("GAMEBASE_ALLOWVERSION")?.get(listOf(0))?.toLong() ?: 0L
                saveManager.updateGameBase(gbCode, gbVersion, gbAllow)
                saveManager.loadGlobal(interp.getScope())
                // GAMEBASE 디버그
                val scope = interp.getScope()
                val title = scope.get("GAMEBASE_TITLE")?.get(listOf(0))
                val version = scope.get("GAMEBASE_VERSION")?.get(listOf(0))
                val gameTitle = title?.toEraString()?.takeIf { it.isNotBlank() } ?: "EraAndroid"
                val gameVersion = version?.toEraString()?.takeIf { it != "0" && it.isNotBlank() } ?: ""
                val displayTitle = if (gameVersion.isNotEmpty()) "$gameTitle v$gameVersion" else gameTitle
                _uiState.update { it.copy(statusMessage = displayTitle) }
                interpreter = interp
                applyPrintcLayout()
                _uiState.update { it.copy(isLoading = false, loadingStatus = "") }
                observeEngineEvents(interp)
                val entry = listOf("SYSTEM_TITLE", "SYSINIT", "TITLE", "START", "GAMESTART")
                    .firstOrNull { interp.getFunctions().containsKey(it.uppercase()) }
                val prologueFns = interp.getFunctions().keys.filter { it.contains("PROLOGUE") }.sorted()
                val existYmFns = interp.getFunctions().keys.filter { it.startsWith("EXIST_PROLOGUE_YM_") }.sorted()
                val listFns = interp.getFunctions().keys.filter { it.contains("LIST") }.sorted()
                val setListFn = interp.getFunctions()["SET_LIST"]
                val getListFn = interp.getFunctions()["GET_LIST"]
                val clearListFn = interp.getFunctions()["CLEAR_LIST"]
                if (entry == null) {
                    appendLine("[ERROR] 진입점 함수 없음. 로드된 함수: ${interp.getFunctions().keys.take(20)}", 0xFFFF4444.toInt())
                } else {
                    // 별도 코루틴으로 실행!
                    gameJob = viewModelScope.launch(Dispatchers.Default) {
                        interp.start(entry)
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }

    private fun observeEngineEvents(interp: EraInterpreter) {
        viewModelScope.launch {
            interp.events.collect { event ->
                handleEvent(event)
            }
        }
    }

    private suspend fun handleEvent(event: EngineEvent) {
        when (event) {
            is EngineEvent.Print -> {
                currentSpans.add(TextSpan(event.text, event.color ?: currentColor, event.isBold, event.isItalic))
                if (event.newLine) flushLine()
                updateUi()
            }
            is EngineEvent.PrintButton -> {
                // isInline=true: PRINTC 컬럼 버튼 → 현재 줄에 추가만 (줄바꿈 없음)
                // isInline=false: 단독 버튼 → 현재 줄에 추가 후 줄바꿈
                val span = TextSpan(event.text, event.color ?: currentColor,
                    isButton = true, buttonValue = event.value)
                currentSpans.add(span)
                if (!event.isInline) flushLine()
                updateUi()
            }
            is EngineEvent.DrawLine -> {
                flushLine()
                // 특수 마커 텍스트로 저장
                currentLines.add(TextLine(
                    listOf(TextSpan("__DRAWLINE__", 0xFF888888.toInt())),
                    TextAlignment.LEFT
                ))
                updateUi()
            }
            is EngineEvent.ClearLine -> {
                flushLine()
                val toRemove = event.count.coerceAtMost(currentLines.size)
                repeat(toRemove) { if (currentLines.isNotEmpty()) currentLines.removeAt(currentLines.size - 1) }
                updateUi()
            }
            is EngineEvent.WaitForInput -> {
                flushLine()
                updateUi()
                if (event.isWait) {
                    _uiState.update { it.copy(isWaitingInput = false, isWaitAnyKey = true) }
                } else {
                    _uiState.update { it.copy(isWaitingInput = true, isWaitAnyKey = false, inputIsNumber = event.isNumber) }
                }
            }
            is EngineEvent.SetColor -> {
                currentColor = event.color
                _uiState.update { it.copy(currentColor = currentColor) }
            }
            is EngineEvent.SetBgColor -> {
                currentBgColor = event.color
                _uiState.update { it.copy(bgColor = currentBgColor) }
            }
            is EngineEvent.Alignment -> {
                currentAlignment = when (event.align) {
                    1 -> TextAlignment.CENTER
                    2 -> TextAlignment.RIGHT
                    else -> TextAlignment.LEFT
                }
                _uiState.update { it.copy(alignment = currentAlignment) }
            }
            is EngineEvent.FontStyle -> {
                isBold = (event.style and 1) != 0
                isItalic = (event.style and 2) != 0
            }
            is EngineEvent.SetFont -> { /* handle custom fonts if needed */ }
            is EngineEvent.SaveRequested -> {
                val scope = interpreter?.getScope() ?: return
                // -1 sentinel 방식: getOrCreate로 만들어진 기본값 0과 구분하기 위해
                // 각 슬롯 변수가 실제로 set된 상태인지 별도 플래그 변수로 판단
                val chkSlotVar = scope.get("CHKDATA_SLOT")
                val delSlotVar = scope.get("DELDATA_SLOT")
                val saveSlotVar = scope.get("SAVEDATA_SLOT")
                // 플래그 변수: _PENDING_XXX = 1이면 해당 작업 대기 중
                val chkPending = scope.get("__PENDING_CHK")?.get(emptyList())?.toLong()?.toInt() ?: 0
                val delPending = scope.get("__PENDING_DEL")?.get(emptyList())?.toLong()?.toInt() ?: 0
                val savePending = scope.get("__PENDING_SAV")?.get(emptyList())?.toLong()?.toInt() ?: 0
                val chkSlot = if (chkPending != 0) chkSlotVar?.get(emptyList())?.toLong()?.toInt() ?: -1 else -1
                val delSlot = if (delPending != 0) delSlotVar?.get(emptyList())?.toLong()?.toInt() ?: -1 else -1
                val saveSlot = if (savePending != 0) saveSlotVar?.get(emptyList())?.toLong()?.toInt() ?: -1 else -1
                when {
                    chkSlot >= 0 -> {
                        scope.getOrCreate("__PENDING_CHK").set(emptyList(), EraValue.of(0L))
                        val (chkResult, chkComment) = saveManager.checkData(chkSlot)
                        scope.getOrCreate("RESULT").set(listOf(0), EraValue.of(chkResult))
                        scope.getOrCreate("RESULTS", true, listOf(100))
                            .set(listOf(0), EraValue.of(chkComment))
                    }
                    delSlot >= 0 -> {
                        scope.getOrCreate("__PENDING_DEL").set(emptyList(), EraValue.of(0L))
                        saveManager.deleteData(delSlot)
                    }
                    saveSlot >= 0 -> {
                        scope.getOrCreate("__PENDING_SAV").set(emptyList(), EraValue.of(0L))
                        val comment = scope.get("SAVEDATA_COMMENT")?.get(listOf(0))?.toEraString() ?: ""
                        val state = interpreter!!.getGameState()
                        saveManager.saveData(saveSlot, state, comment.ifEmpty { "Save $saveSlot" })
                        saveManager.saveGlobal(scope)
                        refreshSaveSlots()
                    }
                }
                interpreter?.completeSaveOp()  // ← 반드시 마지막에 호출
            }
            is EngineEvent.LoadRequested -> {
                val slot = interpreter?.getScope()?.get("LOADDATA_SLOT")?.get(emptyList())?.toLong()?.toInt() ?: 0
                performLoad(slot)
            }
            is EngineEvent.LoadGlobalRequested -> {
                saveManager.loadGlobal(interpreter?.getScope() ?: return)
            }
            is EngineEvent.SaveGlobalRequested -> {
                saveManager.saveGlobal(interpreter?.getScope() ?: return)
            }
            is EngineEvent.Error -> {
                appendLine("[ERROR L${event.line}]: ${event.message}", 0xFFFF4444.toInt())
                _uiState.update { it.copy(isWaitingInput = false) }
            }
            is EngineEvent.Quit -> {
                appendLine("-- Game ended --", 0xFF888888.toInt())
                _uiState.update { it.copy(isWaitingInput = false) }
            }
        }
    }

    // ─── Input ────────────────────────────────────────────────────────────────

    private var pendingButtonInput: String? = null  // 버튼 클릭 선입력 보관

    fun submitInput(input: String) {
        val interp = interpreter ?: return
        // 숫자 입력 대기 중에는 빈/공백 입력 무시
        if (_uiState.value.inputIsNumber && input.isBlank()) return
        // 입력 제출 즉시 activeInputLineIndex를 현재 라인 수로 갱신 → 이전 버튼 모두 차단
        _uiState.update { it.copy(isWaitingInput = false, activeInputLineIndex = it.lines.size) }
        appendLine("> $input", 0xFFCCCCCC.toInt())
        interp.provideInput(input)
    }

    fun advanceWait() {
        if (_uiState.value.isWaitAnyKey) {
            _uiState.update { it.copy(isWaitAnyKey = false, activeInputLineIndex = it.lines.size) }
            interpreter?.provideInput("")
        }
    }

    fun clickButton(value: Long) {
        val input = value.toString()
        when {
            _uiState.value.isWaitAnyKey -> {
                // WAIT 중일 때 버튼 누르면 즉시 통과
                advanceWait()
            }
            _uiState.value.isWaitingInput -> {
                submitInput(input)
            }
            else -> {
                // 입력 대기 중이 아닐 때는 무시 (이전 화면 버튼 오클릭 방지)
            }
        }
    }

    // ─── Save/Load ────────────────────────────────────────────────────────────

    private fun performSave(slot: Int, comment: String = "") {
        val interp = interpreter ?: return
        viewModelScope.launch {
            val state = interp.getGameState()
            saveManager.saveData(slot, state, comment.ifEmpty { "Save $slot" })
            saveManager.saveGlobal(interp.getScope())
            appendLine("[Saved to slot $slot]", 0xFF88FF88.toInt())
            refreshSaveSlots()
        }
    }

    private fun performLoad(slot: Int) {
        val interp = interpreter ?: return
        viewModelScope.launch {
            val state = saveManager.loadData(slot)
            if (state != null) {
                interp.restoreGameState(state)
                appendLine("[Loaded from slot $slot]", 0xFF88FF88.toInt())
                _uiState.update { it.copy(isWaitingInput = false) }
                interp.completeLoad(true)
            } else {
                appendLine("[No save data in slot $slot]", 0xFFFF8888.toInt())
                interp.completeLoad(false)
            }
        }
    }

    fun saveToSlot(slot: Int) { performSave(slot) }
    fun loadFromSlot(slot: Int) { performLoad(slot) }

    fun refreshSaveSlots() {
        viewModelScope.launch {
            _saveSlots.value = saveManager.getAllSaveSlotInfo()
        }
    }

    // ─── Rendering Helpers ───────────────────────────────────────────────────

    private fun flushLine() {
        // 빈 줄도 출력 (PRINTL "" 등)
        currentLines.add(TextLine(currentSpans.toList(), currentAlignment))
        currentSpans.clear()
        if (currentLines.size > 2000) {
            currentLines.subList(0, currentLines.size - 2000).clear()
        }
    }

    private fun appendLine(text: String, color: Int) {
        currentLines.add(TextLine(listOf(TextSpan(text, color))))
        if (currentLines.size > 2000) {
            currentLines.subList(0, currentLines.size - 2000).clear()
        }
        updateUi()
    }

    private fun updateUi() {
        val snapshot = currentLines.toList()
        _uiState.update { it.copy(lines = snapshot) }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun exportErrors(context: android.content.Context) {
        val errors = currentLines
            .filter { line -> line.spans.any { it.color == 0xFFFF4444.toInt() } }
            .joinToString("\n") { line -> line.spans.joinToString("") { it.text } }
        val file = java.io.File(context.filesDir, "era_errors.txt")
        file.writeText(errors)
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(intent, "에러 로그 공유"))
    }

    fun appendDebug(msg: String) {
    }

    fun clearScreen() {
        val keepLines = currentLines.takeLast(40)
        currentLines.clear()
        currentSpans.clear()
        currentLines.addAll(keepLines)
        _uiState.update { it.copy(activeInputLineIndex = 0) }
        updateUi()
    }
}