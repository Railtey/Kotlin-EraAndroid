package com.eraandroid.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import com.eraandroid.emu.AndroidGraphics
import com.eraandroid.emuera.EmueraEngine
import com.eraandroid.emuera.config.Config
import com.eraandroid.emuera.gameproc.InputType
import com.eraandroid.emuera.gameview.ConsoleButtonString
import com.eraandroid.emuera.gameview.ConsoleHost
import com.eraandroid.emuera.gameview.ConsoleState
import com.eraandroid.emuera.gameview.EmueraConsole
import com.eraandroid.emuera.platform.Platform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** 입력창을 어떻게 보여줄지 */
enum class InputMode { NONE, ENTER, INT, STR, PRIMITIVE }

data class GameUiState(
    val gameDir: String? = null,
    val title: String = "EraAndroid",
    val loading: String? = null,
    val inputMode: InputMode = InputMode.NONE,
    val oneInput: Boolean = false,
    val defaultValue: String? = null,
    val error: Boolean = false,
    val redrawInterval: Long = 0,
)

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val _ui = MutableStateFlow(GameUiState())
    val ui: StateFlow<GameUiState> = _ui.asStateFlow()

    /** 화면을 다시 그려야 할 때마다 1씩 증가 */
    private val _frame = MutableStateFlow(0L)
    val frame: StateFlow<Long> = _frame.asStateFlow()

    private var engine: EmueraEngine? = null
    val console: EmueraConsole? get() = engine?.console

    /** 화면 크기 (Emuera 가상 픽셀) — UI 가 갱신 */
    @Volatile var virtualHeight: Int = 480

    private val prefs = application.getSharedPreferences("era", Context.MODE_PRIVATE)

    val recentGames: List<String>
        get() = (prefs.getString("recent", "") ?: "").split('\n').filter { it.isNotBlank() }

    private fun addRecent(dir: String) {
        val list = listOf(dir) + recentGames.filter { it != dir }
        prefs.edit().putString("recent", list.take(8).joinToString("\n")).apply()
    }

    fun removeRecent(dir: String) {
        prefs.edit().putString("recent", recentGames.filter { it != dir }.joinToString("\n")).apply()
    }

    private val host = object : ConsoleHost {
        override fun requestRedraw() {
            updateInputState()
            _frame.value = _frame.value + 1
        }

        override fun setWindowTitle(title: String) {
            _ui.value = _ui.value.copy(title = title)
        }

        override fun closeGame() {
            stopGame()
        }

        override fun reboot() {
            val dir = _ui.value.gameDir ?: return
            android.os.Handler(android.os.Looper.getMainLooper()).post { startGame(dir) }
        }

        override val clientWidth: Int get() = Config.WindowX
        override val clientHeight: Int get() = virtualHeight
        override val isActive: Boolean get() = true

        override fun reportLoadProgress(text: String) {
            _ui.value = _ui.value.copy(loading = text)
        }
    }

    /** 엔진 스레드에서 호출: 입력 대기 상태를 UI 상태로 옮긴다 */
    private fun updateInputState() {
        val c = engine?.console ?: return
        val req = c.currentInputRequest
        val mode = when {
            c.consoleState == ConsoleState.Error || c.consoleState == ConsoleState.Quit -> InputMode.ENTER
            req == null -> InputMode.NONE
            req.inputType == InputType.IntValue -> InputMode.INT
            req.inputType == InputType.StrValue -> InputMode.STR
            req.inputType == InputType.PrimitiveMouseKey -> InputMode.PRIMITIVE
            req.inputType == InputType.Void -> InputMode.NONE
            else -> InputMode.ENTER
        }
        val def = if (req != null && req.hasDefValue) {
            if (req.inputType == InputType.IntValue) req.defIntValue.toString() else req.defStrValue
        } else null
        _ui.value = _ui.value.copy(
            inputMode = mode,
            oneInput = req?.oneInput == true,
            defaultValue = def,
            error = c.consoleState == ConsoleState.Error,
            loading = if (c.consoleState == ConsoleState.Initializing) _ui.value.loading else null,
            redrawInterval = c.redrawInterval,
        )
    }

    fun startGame(dir: String) {
        stopEngine()
        Platform.graphics = AndroidGraphics()
        addRecent(dir)
        _ui.value = GameUiState(gameDir = dir, title = File(dir).name, loading = "Now Loading...")
        val e = EmueraEngine(dir, host)
        engine = e
        e.start()
    }

    private fun stopEngine() {
        engine?.shutdown()
        engine = null
    }

    fun stopGame() {
        stopEngine()
        _ui.value = GameUiState()
        _frame.value = _frame.value + 1
    }

    fun restartGame() {
        val dir = _ui.value.gameDir ?: return
        startGame(dir)
    }

    /** 입력창에서 보낸 값 */
    fun submit(text: String) {
        engine?.post { it.pressEnterKey(false, text, false) }
    }

    /** 화면을 탭했을 때 */
    fun tap(button: ConsoleButtonString?) {
        engine?.post { c ->
            when {
                c.isWaitingPrimitive -> c.mouseDown(0, 0, 1)
                button != null && c.canSelect(button) -> c.clickButton(button)
                c.isWaitingEnterKey || c.isError -> c.pressEnterKey(false, "", true)
            }
        }
    }

    /** WAIT 를 입력 대기까지 건너뛴다 (Emuera 의 우클릭 스킵) */
    fun skip() {
        engine?.post { c -> if (c.isWaitingEnterKey) c.pressEnterKey(true, "", false) }
    }

    fun notifyFrameTick() {
        _frame.value = _frame.value + 1
    }

    override fun onCleared() {
        stopEngine()
        super.onCleared()
    }
}
