package com.eraandroid.ui

import android.app.Application
import android.graphics.Bitmap
import android.os.Environment
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import com.eraandroid.emu.AndroidGraphics
import com.eraandroid.emuera.EmueraEngine
import com.eraandroid.emuera.Program
import com.eraandroid.emuera.config.Config
import com.eraandroid.emuera.content.SpriteDrawCommand
import com.eraandroid.emuera.gameview.ConsoleHost
import com.eraandroid.emuera.gameview.ConsoleState
import com.eraandroid.emuera.gameview.EmueraConsole
import com.eraandroid.emuera.platform.Platform
import com.eraandroid.emuera.ui.UiAlign
import com.eraandroid.emuera.ui.UiConverter
import com.eraandroid.emuera.ui.UiLine
import kotlinx.coroutines.flow.*
import java.io.File
import java.util.WeakHashMap

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
    val buttonValue: Long = 0L,
    /** PRINT_IMG / HTML <img> 로 출력된 이미지 (없으면 null) */
    val image: ImageBitmap? = null,
    /** 이미지 크기 (글자 크기 배수) */
    val imageWidthEm: Float = 0f,
    val imageHeightEm: Float = 0f,
)

enum class TextAlignment { LEFT, CENTER, RIGHT }

data class GameUiState(
    val lines: List<TextLine> = emptyList(),
    val isWaitingInput: Boolean = false,
    val inputIsNumber: Boolean = true,
    val isWaitAnyKey: Boolean = false,
    val activeInputLineIndex: Int = 0,  // 이 줄 이전의 버튼은 클릭 불가 (Emuera 의 버튼 세대)
    val bgColor: Int = 0xFF000000.toInt(),
    val currentColor: Int = 0xFFFFFFFF.toInt(),
    val alignment: TextAlignment = TextAlignment.LEFT,
    val statusMessage: String = "",
    val isLoading: Boolean = false,
    val loadingStatus: String = "",
    val errorMessage: String? = null
)

data class SaveSlotInfo(val slot: Int, val exists: Boolean, val comment: String)

// ─── ViewModel ────────────────────────────────────────────────────────────────
class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()
    var drawLineWidth: Int = 40

    // drawLineWidth 실측 후 호출 — 엔진의 줄 너비와 PRINTC 열 수/너비를 화면에 맞게 설정
    fun applyPrintcLayout() {
        val totalCols = drawLineWidth.coerceIn(20, 200)
        val cols = when {
            totalCols >= 130 -> 4
            totalCols >= 80  -> 3
            totalCols >= 40  -> 2
            else             -> 1
        }
        // 왼쪽 정렬 PRINTC 는 (길이+1) 칸을 차지하므로 1 을 뺀다
        val width = totalCols / cols - 1
        android.util.Log.d("ERA_PRINTC", "applyPrintcLayout: drawLineWidth=$drawLineWidth cols=$cols width=$width")
        val apply = { Config.setScreenOverride(totalCols, cols, width) }
        val e = engine
        if (e != null) e.post { apply() } else apply()
    }

    private val _saveSlots = MutableStateFlow<List<SaveSlotInfo>>(emptyList())
    val saveSlots: StateFlow<List<SaveSlotInfo>> = _saveSlots.asStateFlow()

    private var engine: EmueraEngine? = null
    private var gameDir: String? = null


    // ─── 엔진 → UI ────────────────────────────────────────────────────────────

    private val host = object : ConsoleHost {
        private var lastPublish = 0L

        override fun requestRedraw() {
            val c = engine?.console ?: return
            // 스크립트 실행 중에는 너무 자주 갱신하지 않는다
            val now = System.currentTimeMillis()
            if (c.consoleState == ConsoleState.Running && now - lastPublish < 50) return
            lastPublish = now
            publish(c)
        }

        override fun setWindowTitle(title: String) {
            _uiState.update { it.copy(statusMessage = title) }
        }

        override fun closeGame() {
            android.os.Handler(android.os.Looper.getMainLooper()).post { goToMain() }
        }

        override fun reboot() {
            val dir = gameDir ?: return
            android.os.Handler(android.os.Looper.getMainLooper()).post { startEngine(dir) }
        }

        override val clientWidth: Int get() = Config.WindowX
        override val clientHeight: Int get() = 480
        override val isActive: Boolean get() = true

        /** 무한 반복 의심: 입력 후 30초 넘게 계속되면 중단 (PC 판은 여기서 사용자에게 묻는다) */
        private var loopSince = 0L
        override fun confirmInfiniteLoop(message: String): Boolean {
            val now = System.currentTimeMillis()
            if (loopSince == 0L || now - loopSince > 120_000) loopSince = now
            if (now - loopSince < 30_000) return false
            loopSince = 0L
            _uiState.update { it.copy(errorMessage = "스크립트가 30초 넘게 끝나지 않아 중단했습니다.\n\n$message") }
            return true
        }

        override fun reportLoadProgress(text: String) {
            _uiState.update { it.copy(loadingStatus = text) }
        }
    }

    private val converter = UiConverter()

    /** 엔진 스레드에서 호출: 콘솔 내용을 UI 의 TextLine 목록으로 바꿔서 내보낸다 */
    private fun publish(c: EmueraConsole) {
        val ui = converter.snapshot(c)
        val lines = ui.lines.map { toTextLine(it) }
        _uiState.update {
            it.copy(
                lines = lines,
                isWaitingInput = ui.waitingValue,
                inputIsNumber = ui.waitingNumber,
                isWaitAnyKey = ui.waitingKey,
                activeInputLineIndex = ui.activeLineIndex,
                bgColor = ui.bgColor,
                currentColor = ui.foreColor,
                isLoading = ui.loading,
                loadingStatus = if (ui.loading) it.loadingStatus else "",
            )
        }
    }

    private val lineCache = WeakHashMap<UiLine, TextLine>()

    private fun toTextLine(l: UiLine): TextLine {
        lineCache[l]?.let { return it }
        val t = if (l.isDrawLine) TextLine(listOf(TextSpan("__DRAWLINE__", 0xFF888888.toInt())), TextAlignment.LEFT)
        else TextLine(
            l.spans.map { s ->
                TextSpan(
                    s.text, s.color, s.isBold, s.isItalic, s.isButton, s.buttonValue,
                    image = s.image?.let { bitmapOf(it) },
                    imageWidthEm = s.imageWidthEm, imageHeightEm = s.imageHeightEm,
                )
            },
            when (l.align) {
                UiAlign.CENTER -> TextAlignment.CENTER
                UiAlign.RIGHT -> TextAlignment.RIGHT
                else -> TextAlignment.LEFT
            }
        )
        lineCache[l] = t
        return t
    }

    private fun bitmapOf(c: SpriteDrawCommand): ImageBitmap? {
        val r = c.raster
        val sx = minOf(c.src.x, c.src.x + c.src.width).coerceIn(0, r.width)
        val sy = minOf(c.src.y, c.src.y + c.src.height).coerceIn(0, r.height)
        val sw = kotlin.math.abs(c.src.width).coerceAtMost(r.width - sx)
        val sh = kotlin.math.abs(c.src.height).coerceAtMost(r.height - sy)
        if (sw <= 0 || sh <= 0) return null
        return Bitmap.createBitmap(r.pixels, sy * r.width + sx, r.width, sw, sh, Bitmap.Config.ARGB_8888).asImageBitmap()
    }

    // ─── Game Loading ─────────────────────────────────────────────────────────

    fun loadGame(gameDir: File) {
        val root = findGameRoot(gameDir)
        if (root == null) {
            _uiState.update { it.copy(errorMessage = "CSV 폴더가 있는 게임 폴더를 선택해주세요.\n${gameDir.path}") }
            return
        }
        startEngine(root.path)
    }

    fun loadGameFromDocumentUri(uri: android.net.Uri, context: android.content.Context) {
        val path = realPathFromTreeUri(uri)
        if (path == null || !File(path).isDirectory) {
            _uiState.update { it.copy(errorMessage = "폴더를 열 수 없습니다. 내부 저장소의 폴더를 선택해주세요.") }
            return
        }
        loadGame(File(path))
    }

    private fun realPathFromTreeUri(uri: android.net.Uri): String? = try {
        val docId = android.provider.DocumentsContract.getTreeDocumentId(uri)
        when {
            // 다운로드 폴더: "raw:/storage/emulated/0/Download/..."
            docId.startsWith("raw:") -> docId.removePrefix("raw:")
            docId.startsWith("/") -> docId
            else -> {
                val parts = docId.split(":", limit = 2)
                val rel = if (parts.size > 1) parts[1] else ""
                val ext = Environment.getExternalStorageDirectory().path
                val base = when {
                    parts[0].equals("primary", true) -> ext
                    parts[0].equals("home", true) -> "$ext/Documents"
                    parts[0].equals("downloads", true) -> "$ext/Download"
                    else -> "/storage/${parts[0]}"
                }
                if (rel.isEmpty()) base else "$base/$rel"
            }
        }
    } catch (e: Exception) {
        null
    }

    /** 선택한 폴더 또는 그 바로 아래에서 CSV 폴더가 있는 곳을 찾는다 */
    private fun findGameRoot(dir: File): File? {
        fun isGame(d: File) = d.listFiles()?.any { it.isDirectory && it.name.equals("csv", true) } == true
        if (isGame(dir)) return dir
        return dir.listFiles()?.firstOrNull { it.isDirectory && isGame(it) }
    }

    private fun startEngine(dir: String) {
        engine?.shutdown()
        Platform.graphics = AndroidGraphics()
        gameDir = dir
        converter.reset()
        lineCache.clear()
        _uiState.value = GameUiState(isLoading = true, loadingStatus = "초기화 중...", statusMessage = File(dir).name)
        val e = EmueraEngine(dir, host)
        engine = e
        applyPrintcLayout()
        e.start()
    }

    fun goToTitle() {
        engine?.post { c ->
            converter.hiddenLineCount = 0
            c.gotoTitle()
        }
    }

    fun goToMain() {
        engine?.shutdown()
        engine = null
        gameDir = null
        _uiState.value = GameUiState()
    }

    // ─── Input ────────────────────────────────────────────────────────────────

    fun submitInput(input: String) {
        val e = engine ?: return
        val st = _uiState.value
        e.post { c ->
            val req = c.currentInputRequest
            // 숫자 입력 대기 중에는 빈 입력 무시 (기본값이 있으면 허용)
            if (st.inputIsNumber && input.isBlank() && req?.hasDefValue != true) return@post
            c.pressEnterKey(false, input, false)
        }
    }

    fun advanceWait() {
        engine?.post { c ->
            if (c.isWaitingPrimitive) c.mouseDown(0, 0, 1)
            else if (c.isWaitingEnterKey || c.isError) c.pressEnterKey(false, "", true)
        }
    }

    fun clickButton(value: Long) {
        engine?.post { c -> converter.clickButton(c, value) }
    }

    // ─── Save/Load ────────────────────────────────────────────────────────────

    fun saveToSlot(slot: Int) { /* 저장은 게임 안의 메뉴(SAVEGAME 등)로 한다 */ }
    fun loadFromSlot(slot: Int) { /* 불러오기는 게임 안의 메뉴(LOADGAME 등)로 한다 */ }

    fun refreshSaveSlots() {
        val dir = Program.ExeDir.ifEmpty { return }
        val list = (0..9).map { i ->
            val name = "save%02d.sav".format(i)
            val f = listOf(File(dir, "sav/$name"), File(dir, name)).firstOrNull { it.isFile }
            SaveSlotInfo(i, f != null, if (f != null) java.text.DateFormat.getDateTimeInstance().format(f.lastModified()) else "")
        }
        _saveSlots.value = list
    }

    // ─── Rendering Helpers ───────────────────────────────────────────────────

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun exportErrors(context: android.content.Context) {}

    fun appendDebug(msg: String) {}

    fun clearScreen() {
        engine?.post { c ->
            converter.hiddenLineCount = maxOf(0, c.snapshot().lines.size - 40)
            publish(c)
        }
    }

    override fun onCleared() {
        engine?.shutdown()
        super.onCleared()
    }
}
