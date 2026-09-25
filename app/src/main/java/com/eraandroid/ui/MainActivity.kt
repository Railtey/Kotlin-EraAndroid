package com.eraandroid.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.eraandroid.emu.ConsoleRenderer
import com.eraandroid.emuera.config.Config
import com.eraandroid.ui.theme.EraAndroidTheme
import kotlinx.coroutines.delay
import java.io.File

class MainActivity : ComponentActivity() {

    private val viewModel: GameViewModel by viewModels()

    private val pickDirectoryLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let { openTreeUri(it) } }

    private val legacyPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted -> if (granted.values.all { it }) pickDirectoryLauncher.launch(null) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EraAndroidTheme {
                EraApp(viewModel, onPickDirectory = { pickDirectory() })
            }
        }
    }

    /** 게임 폴더를 파일 경로로 직접 읽으므로 저장소 전체 접근 권한이 필요하다 */
    private fun hasStorageAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager()
        else ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    private fun pickDirectory() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Toast.makeText(this, "‘모든 파일 접근’ 권한을 켠 뒤 다시 눌러주세요", Toast.LENGTH_LONG).show()
                try {
                    startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
                } catch (e: Exception) {
                    startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
                return
            }
        } else if (!hasStorageAccess()) {
            legacyPermissionLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
            return
        }
        pickDirectoryLauncher.launch(null)
    }

    private fun openTreeUri(uri: Uri) {
        val path = realPathFromTreeUri(uri)
        if (path == null || !File(path).isDirectory) {
            Toast.makeText(this, "이 폴더는 열 수 없습니다. 내부 저장소의 폴더를 선택해주세요.", Toast.LENGTH_LONG).show()
            return
        }
        val gameDir = findGameRoot(File(path))
        if (gameDir == null) {
            Toast.makeText(this, "CSV 폴더와 ERB 폴더가 있는 게임 폴더를 선택해주세요.", Toast.LENGTH_LONG).show()
            return
        }
        viewModel.startGame(gameDir.path)
    }

    /** 선택한 폴더 또는 그 바로 아래에서 csv/ 가 있는 폴더를 찾는다 */
    private fun findGameRoot(dir: File): File? {
        fun isGame(d: File) = d.listFiles()?.any { it.isDirectory && it.name.equals("csv", true) } == true
        if (isGame(dir)) return dir
        return dir.listFiles()?.firstOrNull { it.isDirectory && isGame(it) }
    }

    private fun realPathFromTreeUri(uri: Uri): String? {
        return try {
            val docId = android.provider.DocumentsContract.getTreeDocumentId(uri)
            val parts = docId.split(":", limit = 2)
            val type = parts[0]
            val rel = if (parts.size > 1) parts[1] else ""
            val base = if (type.equals("primary", true)) Environment.getExternalStorageDirectory().path else "/storage/$type"
            if (rel.isEmpty()) base else "$base/$rel"
        } catch (e: Exception) {
            null
        }
    }
}

@Composable
fun EraApp(viewModel: GameViewModel, onPickDirectory: () -> Unit) {
    val ui by viewModel.ui.collectAsState()
    if (ui.gameDir == null) HomeScreen(viewModel, onPickDirectory)
    else GameScreen(viewModel, ui)
}

@Composable
fun HomeScreen(viewModel: GameViewModel, onPickDirectory: () -> Unit) {
    var recent by remember { mutableStateOf(viewModel.recentGames) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text("EraAndroid", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("Emuera 1824 호환 엔진", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(24.dp))
            Button(onClick = onPickDirectory, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Icon(Icons.Default.FolderOpen, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("게임 폴더 열기", fontSize = 18.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "CSV, ERB 폴더가 들어 있는 게임 폴더를 선택하세요. 이미지는 resources 폴더에서 읽습니다.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface,
            )
            if (recent.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                Text("최근 게임", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.height(8.dp))
                for (dir in recent) {
                    Card(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { viewModel.startGame(dir) },
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(File(dir).name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(dir, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = { viewModel.removeRecent(dir); recent = viewModel.recentGames }) {
                                Icon(Icons.Default.Close, contentDescription = "목록에서 지우기")
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(viewModel: GameViewModel, ui: GameUiState) {
    val frame by viewModel.frame.collectAsState()
    val renderer = remember { ConsoleRenderer() }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var scrollBack by remember { mutableIntStateOf(0) }
    var dragAccum by remember { mutableFloatStateOf(0f) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }

    BackHandler { confirmExit = true }

    // 애니메이션 스프라이트 / REDRAW 타이머가 있으면 주기적으로 다시 그린다
    LaunchedEffect(ui.redrawInterval) {
        if (ui.redrawInterval > 0) {
            while (true) {
                delay(ui.redrawInterval.coerceAtLeast(16))
                viewModel.notifyFrameTick()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(ui.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 16.sp) },
                actions = {
                    IconButton(onClick = { viewModel.skip() }) { Icon(Icons.Default.FastForward, contentDescription = "스킵") }
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = "메뉴") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("처음부터 다시 읽기") }, onClick = { menuOpen = false; viewModel.restartGame() })
                        DropdownMenuItem(text = { Text("게임 닫기") }, onClick = { menuOpen = false; confirmExit = true })
                    }
                },
            )
        },
        bottomBar = { InputBar(ui, viewModel) },
    ) { pad ->
        Box(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .onSizeChanged {
                    size = it
                    if (it.width > 0) viewModel.virtualHeight = (it.height / renderer.scaleFor(it.width)).toInt()
                }
                .pointerInput(Unit) {
                    detectTapGestures { pos ->
                        val c = viewModel.console ?: return@detectTapGestures
                        if (scrollBack > 0) { scrollBack = 0; viewModel.notifyFrameTick(); return@detectTapGestures }
                        val b = renderer.hitTest(c.snapshot(), pos.x, pos.y, size.width, size.height, scrollBack)
                        viewModel.tap(b)
                    }
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(onDragEnd = { dragAccum = 0f }) { _, dy ->
                        val c = viewModel.console ?: return@detectVerticalDragGestures
                        val lineH = Config.LineHeight * renderer.scaleFor(size.width)
                        dragAccum += dy
                        val lines = (dragAccum / lineH).toInt()
                        if (lines != 0) {
                            dragAccum -= lines * lineH
                            val max = maxOf(0, c.snapshot().lines.size - 1)
                            scrollBack = (scrollBack + lines).coerceIn(0, max)
                        }
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                if (frame < 0) return@Canvas // frame 을 읽어서 갱신될 때마다 다시 그리게 한다
                val c = viewModel.console ?: return@Canvas
                renderer.draw(drawContext.canvas.nativeCanvas, c.snapshot(), size.width, size.height, scrollBack)
            }
            val loading = ui.loading
            if (loading != null) {
                Text(
                    loading,
                    Modifier
                        .align(Alignment.BottomStart)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                        .padding(6.dp),
                    fontSize = 12.sp,
                )
            }
            if (scrollBack > 0) {
                SmallFloatingActionButton(
                    onClick = { scrollBack = 0 },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                ) { Icon(Icons.Default.KeyboardArrowDown, contentDescription = "맨 아래로") }
            }
        }
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("게임을 닫을까요?") },
            text = { Text("저장하지 않은 진행 상황은 사라집니다.") },
            confirmButton = { TextButton(onClick = { confirmExit = false; viewModel.stopGame() }) { Text("닫기") } },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("취소") } },
        )
    }
}

@Composable
fun InputBar(ui: GameUiState, viewModel: GameViewModel) {
    var text by remember { mutableStateOf("") }
    val needValue = ui.inputMode == InputMode.INT || ui.inputMode == InputMode.STR
    Surface(tonalElevation = 3.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (needValue) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text(if (ui.inputMode == InputMode.INT) "숫자 입력" else "문자 입력") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (ui.inputMode == InputMode.INT) KeyboardType.Number else KeyboardType.Text,
                        imeAction = ImeAction.Send,
                    ),
                    keyboardActions = KeyboardActions(onSend = { viewModel.submit(text); text = "" }),
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = { viewModel.submit(text); text = "" }) { Text("확인") }
            } else {
                Text(
                    when (ui.inputMode) {
                        InputMode.ENTER -> if (ui.error) "오류가 발생했습니다. 화면을 누르면 종료합니다." else "화면을 누르면 계속합니다"
                        InputMode.PRIMITIVE -> "화면을 눌러주세요"
                        else -> "진행 중…"
                    },
                    Modifier
                        .weight(1f)
                        .padding(vertical = 14.dp),
                    fontSize = 13.sp,
                )
                if (ui.inputMode == InputMode.ENTER) {
                    Button(onClick = { viewModel.tap(null) }) { Text("계속") }
                }
            }
        }
    }
}
