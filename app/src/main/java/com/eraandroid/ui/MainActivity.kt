package com.eraandroid.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eraandroid.ui.theme.EraAndroidTheme
import java.io.File
import androidx.compose.foundation.text.selection.SelectionContainer

class MainActivity : ComponentActivity() {

    private val viewModel: GameViewModel by viewModels()

    private val pickDirectoryLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { loadGameFromUri(it) }
    }

    // Android 10 이하: 게임 폴더를 읽고 세이브를 쓰려면 저장소 권한이 필요
    private val legacyPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted -> if (granted.values.all { it }) pickDirectoryLauncher.launch(null) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EraAndroidTheme {
                EraGameApp(viewModel, onPickDirectory = { pickDirectory() })
            }
        }
    }

    private fun pickDirectory() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                return
            }
        } else if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            legacyPermissionLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
            return
        }
        pickDirectoryLauncher.launch(null)
    }

    private fun loadGameFromUri(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: Exception) { }
        viewModel.loadGameFromDocumentUri(uri, this)
    }

    private fun getRealPathFromUri(uri: Uri): String? {
        return try {
            val docId = androidx.documentfile.provider.DocumentFile.fromTreeUri(this, uri)
                ?.uri?.lastPathSegment ?: return null
            val parts = docId.split(":")
            if (parts.size >= 2) {
                val type = parts[0]
                val relativePath = parts[1]
                when (type) {
                    "primary" -> "${Environment.getExternalStorageDirectory()}/$relativePath"
                    else -> "/storage/$type/$relativePath"
                }
            } else null
        } catch (e: Exception) { null }
    }
}

// ─── Root App Composable ──────────────────────────────────────────────────────

@Composable
fun EraGameApp(viewModel: GameViewModel, onPickDirectory: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    var showMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("era_settings", android.content.Context.MODE_PRIVATE) }
    var fontSize by remember { mutableStateOf(prefs.getFloat("font_size", 14f)) }

    val configuration = LocalConfiguration.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()

    LaunchedEffect(configuration.screenWidthDp, fontSize) {
        val availableWidthDp = configuration.screenWidthDp - 16
        val availableWidthPx = with(density) { availableWidthDp.dp.toPx() }

        // 반각(ASCII) 문자 기준으로 측정 — 전각 문자로 재면 2배 오차 발생
        val measured = textMeasurer.measure(
            text = "aaaaaaaaaa",
            style = androidx.compose.ui.text.TextStyle(
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontSize = fontSize.sp
            )
        )
        val charWidthPx = measured.size.width.toFloat() / 10f
        viewModel.drawLineWidth = (availableWidthPx / charWidthPx).toInt().coerceIn(20, 120)
        android.util.Log.d("ERA_PRINTC", "screen: screenWidthDp=${configuration.screenWidthDp} fontSize=$fontSize charWidthPx=$charWidthPx availableWidthPx=$availableWidthPx drawLineWidth=${viewModel.drawLineWidth}")
        viewModel.applyPrintcLayout()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(uiState.bgColor)
    ) {
        // Android 15+ 는 화면이 시스템 바 뒤까지 그려지므로 아래쪽 내비게이션 바(홈/뒤로가기)와 키보드만큼 띄운다
        Column(modifier = Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
            val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
            EraTopBar(
                onMenuClick = { showMenu = true },
                statusMessage = uiState.statusMessage,
                onCopyLog = {
                    val allText = uiState.lines.joinToString("\n") { line ->
                        line.spans.joinToString("") { it.text }
                    }
                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(allText))
                }
            )

            Box(modifier = Modifier
                .weight(1f)
                .clickable { if (uiState.isWaitAnyKey) viewModel.advanceWait() }
            ) {
                if (uiState.lines.isEmpty() && !uiState.isLoading) {
                    FontPreviewScreen(fontSize = fontSize, drawLineWidth = viewModel.drawLineWidth)
                } else {
                    GameOutput(
                        lines = uiState.lines,
                        fontSize = fontSize,
                        inputActive = uiState.isWaitingInput || uiState.isWaitAnyKey,
                        activeInputLineIndex = uiState.activeInputLineIndex,
                        onButtonClick = { value -> viewModel.clickButton(value) }
                    )
                }
                if (uiState.isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xCC000000)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFF88AAFF),
                                strokeWidth = 3.dp
                            )
                            val infiniteTransition = rememberInfiniteTransition(label = "dots")
                            val dotProgress by infiniteTransition.animateFloat(
                                initialValue = 0f,
                                targetValue = 4f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1200, easing = LinearEasing)
                                ),
                                label = "dotProgress"
                            )
                            val dotCount = dotProgress.toInt().coerceIn(0, 3)
                            Text(
                                text = "로딩 중" + ".".repeat(dotCount) + " ".repeat(3 - dotCount),
                                color = Color(0xFF88AAFF),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (uiState.loadingStatus.isNotEmpty()) {
                                Text(
                                    text = uiState.loadingStatus,
                                    color = Color(0xFFAAAAAA),
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 24.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (uiState.isWaitingInput) {
                InputBar(
                    isNumber = uiState.inputIsNumber,
                    onSubmit = { viewModel.submitInput(it) }
                )
            }
        }
    }

    if (showMenu) {
        EraMenuDialog(
            onDismiss = { showMenu = false },
            onOpenGame = { showMenu = false; onPickDirectory() },
            onTitle = { showMenu = false; viewModel.goToTitle() },
            onClear = { viewModel.clearScreen(); showMenu = false },
            onMain = { showMenu = false; viewModel.goToMain() },
            fontSize = fontSize,
            onFontSizeChange = {
                fontSize = it
                prefs.edit().putFloat("font_size", it).apply()
            }
        )
    }

    uiState.errorMessage?.let { error ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissError() },
            title = { Text("Error") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissError() }) { Text("OK") }
            }
        )
    }
}

// ─── Font Preview Screen ──────────────────────────────────────────────────────

@Composable
fun FontPreviewScreen(fontSize: Float, drawLineWidth: Int = 40) {
    val divider = "DRAWLINE_MARKER"
    val grayPrefix = "GRAY_LABEL:"
    val sampleLines = listOf(
        divider,
        "만든놈 : Railtey",
        divider,
        "키스의 고유함은 벛꽃보다 화사하게 발효된다.",
        "가느다란 몸 부수어 쥔 총칼, 터, 평화.",
        "다람쥐 헌 쳇바퀴에 타고파.",
        divider,
        "The Quick Brown Fox Jumps Over The Lazy Dog.",
        "Jackdaws love my big sphinx of quartz.",
        "Travelling Beneath the azure sky in our jolly ox-cart, we often hit bumps quite hard.",
        divider,
        "いろはにほへと ちりぬるを\n" +
                "わかよたれそ つねならむ\n" +
                "うゐのおくやま けふこえて\n" +
                "あさきゆめみし ゑひもせす",
        "とりなくこゑす ゆめさませ\n" +
                "あきよあかつき とをにから\n" +
                "ねさめておるる ゐなかのし\n" +
                "くさむにわしろ ゑひもせす",
        "あめ つち ほし そら\n" +
                "やま かわ みね たに\n" +
                "くも きり むろ こけ\n" +
                "ひと いぬ うえ すえ\n" +
                "ゆわ さる おふ せよ\n" +
                "えのえを なれゐて",
        divider,
        "ㄱㄴㄷㄹㅁㅂㅅㅇㅈㅊㅋㅌㅍㅎ",
        "ㅏㅑㅓㅕㅗㅛㅜㅠㅡㅣ",
        "abcdefghijklmnopqrstuvwxyz",
        "ABCDEFGHIJKLMNOPQRSTUVWXYZ",
        "1234567890",
        "あいうえおかきくけこさしすせそ",
        "アイウエオカキクケコサシスセソ",
        divider,
        "소지금:1만5000원(목표금액까지 985000원)",
        "체력[****************........]( 2000/ 2500)",
        divider,
        "← 좌측 메뉴(☰)에서 폰트 크기를 조절하세요",
        divider,
        "${grayPrefix}▼ 세로형 버튼 미리보기",
        "[ 0] 강하게 시작",
        "[ 1] 불러오기",
    )

    // drawLineWidth 기반으로 실제 게임과 동일한 cols 계산
    val cols = when {
        drawLineWidth >= 130 -> 4
        drawLineWidth >= 80  -> 3
        drawLineWidth >= 40  -> 2
        else                 -> 1
    }

    val allButtons = listOf(
        "[ 100] - 전투 시작", "[ 101] - 상태 확인", "[ 102] - 휴식",
        "[ 103] - 능력치 올리기", "[ 104] - 대상 변경", "[ 105] - 아이템 구입",
        "[ 106] - 캐릭터 구입", "[ 107] - 훈련소", "[ 108] - 약국",
        "[ 109] - 이벤트", "[ 200] - 저장하기", "[ 300] - 불러오기",
        "[ 400] - 캐릭터 목록", "[ 500] - 설정", "[ 999] - 메인으로",
    )
    val buttonRows = allButtons.chunked(cols)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(sampleLines) { line ->
            if (line == divider) {
                Divider(
                    color = Color(0xFF888888),
                    thickness = 1.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                )
            } else if (line.startsWith(grayPrefix)) {
                Text(
                    text = line.removePrefix(grayPrefix),
                    color = Color(0xFF888888),
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontSize = fontSize.sp,
                    lineHeight = (fontSize * 1.4f).sp
                )
            } else {
                Text(
                    text = line,
                    color = Color(0xFFCCCCCC),
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontSize = fontSize.sp,
                    lineHeight = (fontSize * 1.4f).sp
                )
            }
        }

        item {
            Divider(color = Color(0xFF888888), thickness = 1.dp,
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp))
            Text("▼ 가로형 버튼 미리보기 (현재 ${cols}열, drawLineWidth=$drawLineWidth)",
                color = Color(0xFF888888),
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontSize = fontSize.sp)
        }
        items(buttonRows) { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { label ->
                    Text(
                        text = label,
                        modifier = Modifier.weight(1f),
                        color = Color(0xFFCCCCCC),
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = fontSize.sp,
                        lineHeight = (fontSize * 1.4f).sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Clip
                    )
                }
                repeat(cols - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
        item {
            Divider(color = Color(0xFF888888), thickness = 1.dp,
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp))
        }

        item {
            Text("▼ 긴글 가독성 미리보기",
                color = Color(0xFF888888),
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontSize = fontSize.sp)
        }
        item {
            Text("푸른 꽃\n" +
                    "\n" +
                    "by Railtey\n" +
                    "\n" +
                    "제1장: 안개의 집\n" +
                    "\n" +
                    "그 집의 공기는 언제나 젖어 있었다. 유리벽 바깥의 세상은 가을의 끝자락에서 마른 낙엽들이 부서지는 비명을 지르며 뒹굴고 있었으나, 정원 안쪽은 계절의 흐름을 배반한 채 축축한 침묵을 유지했다. 습도는 늘 일정했고, 온도는 체온보다 아주 조금 낮아 피부 위에 가느다란 소름이 돋기 딱 좋은 상태를 고집스럽게 지켜냈다.\n" +
                    "\n" +
                    "창가에 멈춰 선 휠체어 위의 이는 미동도 없이 밖을 내다보고 있었다. 무릎 위에 덮인 하얀 담요는 아주 사소한 주름조차 없이 매끄럽게 정돈되어 있었다. 그 결벽증적인 정갈함은 누군가의 집요한 관리 없이는 불가능한 것이었다.\n" +
                    "\n" +
                    "\"또 창가에 너무 가까이 다가갔군요. 찬 공기가 닿으면 당신의 예민한 살결이 먼저 상한다고 몇 번이나 당부했는데.“\n" +
                    "\n" +
                    "문이 열리는 소리조차 없이 다가온 목소리는 더없이 부드러웠다. 그이는 아주 자연스럽게 휠체어의 손잡이를 잡고 창가에서 멀찍이 떼어놓았다. 그 동작은 신속하고 효율적이었으며, 동시에 상대의 의사 따위는 고려하지 않는 기계적인 배려가 섞여 있었다.\n" +
                    "\n" +
                    "그이는 무릎을 굽히고 앉아 상대의 차가워진 손을 자신의 두 손으로 감싸 쥐었다. 마치 세상에서 가장 귀한 유리 세공품을 다루듯 조심스럽게 손가락 마디마디를 주물렀다.\n" +
                    "\"손이 이렇게 차가워졌잖아요. 당신은 이제 혼자서 열을 낼 수도 없는데... 왜 자꾸 나를 걱정시키나요? 당신이 아프면 내가 얼마나 무너지는지 잘 알면서.“\n" +
                    "\n" +
                    "그이의 눈에는 진심 어린 애정과 슬픔이 서려 있었다. 그 눈빛을 보고 있노라면, 창가에 다가가 밖을 내다본 행위 자체가 그이의 헌신을 모독한 커다란 죄악처럼 느껴지기 마련이었다. 휠체어 위의 이는 입술을 달싹였으나 끝내 아무 말도 하지 못했다. 목소리가 나오지 않는 것은 아니었다. 다만 어떤 말을 내뱉어도 결국은 그이가 쳐 놓은 ‘다정한 논리’의 그물에 걸려들 뿐이라는 것을 몸이 먼저 기억하고 있었다.\n" +
                    "\n" +
                    "\"미안... 해요.“\n" +
                    "\n" +
                    "간신히 새어 나온 한 마디에 그이의 얼굴이 봄볕처럼 환해졌다. 그이는 안도한 듯 깊은 한숨을 내쉬며 상대의 손등에 가볍게 입을 맞췄다. 그리고는 테이블 위에 놓인 화병으로 시선을 돌렸다. 거기엔 아직 정체를 알 수 없는, 멍든 것처럼 짙푸른 꽃들이 가득 꽂혀 있었다.\n" +
                    "\n" +
                    "\"이 꽃들을 보세요. 색이 참 깊죠? 밖은 벌써 겨울을 준비하느라 메말라가는데, 오직 이곳에서만 이 아이들이 제 빛을 내고 있어요. 당신도 이 꽃들과 같아요. 세상 밖으로 나가는 순간, 사람들의 무관심과 차가운 시선에 당신의 이 고결한 우울함은 금방 퇴색되어 버릴 거예요.“\n" +
                    "\n" +
                    "그이는 꽃잎 한 장을 조심스럽게 만지작거리며 말을 이었다.\n" +
                    "\n" +
                    "\"당신은 슬플 때 가장 아름다워요. 그건 당신이 약해서가 아니라, 오직 나만이 이해할 수 있는 깊이를 가졌다는 뜻이죠. 그러니까 더는 밖을 보며 마음을 다치지 마세요. 당신의 모든 아픔은 내가 대신 짊어질 테니, 당신은 그저 이 안전한 정원에서 가장 편안하게 슬퍼하기만 하면 돼요.“\n" +
                    "\n" +
                    "그이는 꽃송이 하나를 따서 상대의 무릎 위에 조심스럽게 놓아주었다. 푸른 꽃잎은 마치 상대의 피부 위에 돋아난 정적인 멍처럼 보였다. 그이는 그 광경이 세상에서 가장 평화롭다는 듯 미소 지었다.\n" +
                    "\n" +
                    "상대는 자신이 지금 보살핌을 받고 있는 것인지, 아니면 서서히 자신의 의지를 거세당하고 있는 것인지 구분할 수 없었다. 다만 확실한 것은, 그이가 읊어주는 그 지독하리만큼 완벽한 다정함이 없이는 단 한 순간도 이 정적을 견딜 수 없게 되어버렸다는 사실뿐이었다.\n" +
                    "\n" +
                    "\"잠시 눈을 붙여요. 당신이 다시 세상의 소음에 휘둘리지 않도록 내가 곁에서 이 푸른 정적을 지키고 있을 테니까.“\n" +
                    "\n" +
                    "그이는 아주 천천히 휠체어를 밀어 정원의 가장 깊숙한 어둠 속으로 이끌었다. 온실 가득 퍼진 정체 모를 꽃향기가 늪처럼 발목을 잡아끌었다." ,
                color = Color(0xFFFFFFFF),
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontSize = fontSize.sp)
        }

    }
}

// ─── Top Bar ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EraTopBar(onMenuClick: () -> Unit, statusMessage: String, onCopyLog: () -> Unit) {
    TopAppBar(
        title = { Text(statusMessage.ifBlank { "EraAndroid" }, color = Color(0xFFCCCCCC), fontSize = 16.sp) },
        navigationIcon = {
            IconButton(onClick = onMenuClick) {
                Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color(0xFFCCCCCC))
            }
        },
        actions = {
            IconButton(onClick = onCopyLog) {
                Icon(Icons.Default.ContentCopy, contentDescription = "로그 복사", tint = Color(0xFFCCCCCC))
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color(0xFF111111)
        )
    )
}

// ─── Game Output ──────────────────────────────────────────────────────────────

@Composable
fun GameOutput(
    lines: List<TextLine>,
    fontSize: Float = 14f,
    inputActive: Boolean = false,
    activeInputLineIndex: Int = 0,
    onButtonClick: (Long) -> Unit
) {
    val listState = rememberLazyListState()

    // 로그가 최대 줄 수에 도달하면 줄 수가 더 늘지 않으므로 내용이 바뀔 때마다 맨 아래로
    LaunchedEffect(lines) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.size - 1)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        itemsIndexed(lines) { index, line ->
            GameLine(
                line = line,
                fontSize = fontSize,
                inputActive = inputActive && index >= activeInputLineIndex,
                onButtonClick = onButtonClick
            )
        }
    }
}

@Composable
fun GameLine(line: TextLine, fontSize: Float = 14f, inputActive: Boolean = false, onButtonClick: (Long) -> Unit) {
    val textAlign = when (line.alignment) {
        TextAlignment.CENTER -> TextAlign.Center
        TextAlignment.RIGHT -> TextAlign.End
        else -> TextAlign.Start
    }

    val hasButtons = line.spans.any { it.isButton }

    val fullText = if (!hasButtons) line.spans.joinToString("") { it.text } else ""
    val bracketNumberRegex = Regex("""^[>\s☆★○●▲△▼▽◆◇■□]*\[\s*(\d+)\s*\]""")
    val trimmedText = fullText.trimStart()
    val bracketMatch = if (!hasButtons) bracketNumberRegex.find(trimmedText) else null

    if (line.spans.size == 1 && line.spans[0].text == "__DRAWLINE__") {
        Divider(
            color = Color(0xFF888888),
            thickness = 1.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp)
        )
        return
    }

    if (line.spans.any { it.image != null }) {
        ImageLine(line, fontSize, inputActive, onButtonClick)
        return
    }

    if (hasButtons) {
        val buttonSpans = line.spans.filter { it.isButton }
        val isMultiButton = buttonSpans.size > 1

        if (isMultiButton) {
            Row(modifier = Modifier.fillMaxWidth()) {
                buttonSpans.forEach { span ->
                    val annotatedString = buildAnnotatedString {
                        withStyle(SpanStyle(
                            color = Color(span.color),
                            fontWeight = if (span.isBold) FontWeight.Bold else FontWeight.Normal,
                            fontStyle = if (span.isItalic) FontStyle.Italic else FontStyle.Normal
                        )) { append(span.text.trimEnd()) }
                    }
                    Text(
                        text = annotatedString,
                        modifier = Modifier
                            .weight(1f)
                            .clickable(enabled = inputActive) { onButtonClick(span.buttonValue) },
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = fontSize.sp,
                        lineHeight = (fontSize * 1.4f).sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Clip
                    )
                }
            }
        } else {
            val buttonValue = buttonSpans.firstOrNull()?.buttonValue ?: 0L
            val annotatedString = buildAnnotatedString {
                line.spans.forEach { span ->
                    withStyle(SpanStyle(
                        color = Color(span.color),
                        fontWeight = if (span.isBold) FontWeight.Bold else FontWeight.Normal,
                        fontStyle = if (span.isItalic) FontStyle.Italic else FontStyle.Normal
                    )) { append(span.text) }
                }
            }
            Text(
                text = annotatedString,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = inputActive) { onButtonClick(buttonValue) },
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontSize = fontSize.sp,
                textAlign = textAlign,
                lineHeight = (fontSize * 1.4f).sp
            )
        }
    } else if (bracketMatch != null) {
        val allBracketRegex = Regex("""\[\s*(\d+)\s*\]""")
        val allMatches = allBracketRegex.findAll(trimmedText).toList()
        if (allMatches.size > 1) {
            val annotatedString = buildAnnotatedString {
                var cursor = 0
                val spanText = fullText
                allBracketRegex.findAll(spanText).forEach { m ->
                    if (m.range.first > cursor) {
                        withStyle(SpanStyle(color = Color(line.spans.firstOrNull()?.color ?: 0xFFCCCCCC.toInt()))) {
                            append(spanText.substring(cursor, m.range.first))
                        }
                    }
                    val nextMatch = allBracketRegex.find(spanText, m.range.last + 1)
                    val end = nextMatch?.range?.first ?: spanText.length
                    val clickText = spanText.substring(m.range.first, end)
                    val num = m.groupValues[1].trim()
                    pushStringAnnotation(tag = "BTN", annotation = num)
                    withStyle(SpanStyle(color = Color(line.spans.firstOrNull()?.color ?: 0xFFCCCCCC.toInt()))) {
                        append(clickText)
                    }
                    pop()
                    cursor = end
                }
                if (cursor < spanText.length) {
                    withStyle(SpanStyle(color = Color(line.spans.firstOrNull()?.color ?: 0xFFCCCCCC.toInt()))) {
                        append(spanText.substring(cursor))
                    }
                }
            }
            androidx.compose.foundation.text.ClickableText(
                text = annotatedString,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 0.dp, horizontal = 2.dp),
                style = androidx.compose.ui.text.TextStyle(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontSize = fontSize.sp,
                    textAlign = textAlign,
                    lineHeight = (fontSize * 1.4f).sp
                ),
                onClick = { offset ->
                    annotatedString.getStringAnnotations(tag = "BTN", start = offset, end = offset)
                        .firstOrNull()?.let { if (inputActive) onButtonClick(it.item.toLongOrNull() ?: 0L) }
                }
            )
        } else {
            val number = bracketMatch.groupValues[1].trim().toLongOrNull() ?: 0L
            val annotatedString = buildAnnotatedString {
                line.spans.forEach { span ->
                    withStyle(SpanStyle(
                        color = Color(span.color),
                        fontWeight = if (span.isBold) FontWeight.Bold else FontWeight.Normal,
                        fontStyle = if (span.isItalic) FontStyle.Italic else FontStyle.Normal
                    )) { append(span.text) }
                }
            }
            Text(
                text = annotatedString,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = inputActive) { onButtonClick(number) }
                    .padding(vertical = 0.dp, horizontal = 2.dp),
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontSize = fontSize.sp,
                textAlign = textAlign,
                lineHeight = (fontSize * 1.4f).sp
            )
        }
    } else {
        val annotatedString = buildAnnotatedString {
            line.spans.forEach { span ->
                withStyle(SpanStyle(
                    color = Color(span.color),
                    fontWeight = if (span.isBold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (span.isItalic) FontStyle.Italic else FontStyle.Normal
                )) { append(span.text) }
            }
        }
        SelectionContainer {
            Text(
                text = annotatedString,
                modifier = Modifier.fillMaxWidth(),
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontSize = fontSize.sp,
                textAlign = textAlign,
                lineHeight = (fontSize * 1.4f).sp
            )
        }
    }
}

// 이미지가 들어 있는 줄 (PRINT_IMG, HTML_PRINT 의 <img>)
@Composable
fun ImageLine(line: TextLine, fontSize: Float, inputActive: Boolean, onButtonClick: (Long) -> Unit) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = when (line.alignment) {
            TextAlignment.CENTER -> Arrangement.Center
            TextAlignment.RIGHT -> Arrangement.End
            else -> Arrangement.Start
        }
    ) {
        line.spans.forEach { span ->
            val click = Modifier.clickable(enabled = inputActive && span.isButton) { onButtonClick(span.buttonValue) }
            val img = span.image
            if (img != null) {
                val w = with(density) { (fontSize * span.imageWidthEm).sp.toDp() }
                val h = with(density) { (fontSize * span.imageHeightEm).sp.toDp() }
                androidx.compose.foundation.Image(
                    bitmap = img,
                    contentDescription = null,
                    modifier = click.size(w, h),
                    contentScale = androidx.compose.ui.layout.ContentScale.FillBounds
                )
            } else if (span.text.isNotEmpty()) {
                Text(
                    text = span.text,
                    modifier = click,
                    color = Color(span.color),
                    fontWeight = if (span.isBold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (span.isItalic) FontStyle.Italic else FontStyle.Normal,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontSize = fontSize.sp,
                    lineHeight = (fontSize * 1.4f).sp,
                    maxLines = 1
                )
            }
        }
    }
}

// ─── Input Bar ────────────────────────────────────────────────────────────────

@Composable
fun InputBar(isNumber: Boolean, onSubmit: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    Surface(color = Color(0xFF111111)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(if (isNumber) "Enter number..." else "Enter text...", color = Color(0xFF666666)) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (isNumber) KeyboardType.Number else KeyboardType.Text,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        onSubmit(text)
                        text = ""
                        focusManager.clearFocus()
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color(0xFFEEEEEE),
                    unfocusedTextColor = Color(0xFFCCCCCC),
                    focusedBorderColor = Color(0xFF88AAFF),
                    unfocusedBorderColor = Color(0xFF444444),
                    cursorColor = Color(0xFF88AAFF)
                ),
                singleLine = true,
                shape = RoundedCornerShape(8.dp)
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = {
                    onSubmit(text)
                    text = ""
                    focusManager.clearFocus()
                },
                modifier = Modifier
                    .background(Color(0xFF334455), RoundedCornerShape(8.dp))
                    .size(48.dp)
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send", tint = Color(0xFF88AAFF))
            }
        }
    }
}

// ─── Menu Dialog ─────────────────────────────────────────────────────────────

@Composable
fun EraMenuDialog(
    onDismiss: () -> Unit,
    onOpenGame: () -> Unit,
    onTitle: () -> Unit,
    onClear: () -> Unit,
    onMain: () -> Unit,
    fontSize: Float = 14f,
    onFontSizeChange: (Float) -> Unit = {}
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .background(Color(0xFF1A1A2E), RoundedCornerShape(28.dp))
                .padding(top = 20.dp, start = 8.dp, end = 8.dp, bottom = 8.dp)
        ) {
            Text("메뉴", color = Color(0xFFEEEEEE), fontSize = 18.sp,
                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
            MenuItem(Icons.Default.FolderOpen, "게임 폴더 지정", onOpenGame)
            MenuItem(Icons.Default.Home, "타이틀 화면으로", onTitle)
            MenuItem(Icons.Default.ClearAll, "화면 청소", onClear)
            MenuItem(Icons.Default.ExitToApp, "메인 화면으로", onMain)
            Divider(color = Color(0xFF333355), modifier = Modifier.padding(vertical = 4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
            ) {
                Text("폰트 크기", color = Color(0xFFCCCCCC), modifier = Modifier.weight(1f))
                IconButton(
                    onClick = { if (fontSize > 8f) onFontSizeChange((fontSize - 1f).coerceAtLeast(8f)) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Text("－", color = Color(0xFF88AAFF), fontSize = 18.sp)
                }
                Text(
                    "${fontSize.toInt()}",
                    color = Color(0xFFEEEEEE),
                    fontSize = 16.sp,
                    modifier = Modifier.width(32.dp),
                    textAlign = TextAlign.Center
                )
                IconButton(
                    onClick = { if (fontSize < 24f) onFontSizeChange((fontSize + 1f).coerceAtMost(24f)) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Text("＋", color = Color(0xFF88AAFF), fontSize = 18.sp)
                }
            }
            Text(
                "기본값 14  /  범위 8 ~ 24",
                color = Color(0xFF666688),
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 12.dp)
            )
            // Close 버튼
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Close", color = Color(0xFF88AAFF))
                }
            }
        }
    }
}

@Composable
fun MenuItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(icon, contentDescription = null, tint = Color(0xFF88AAFF), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = Color(0xFFCCCCCC), modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
    }
}

// ─── Save/Load Dialog ────────────────────────────────────────────────────────

@Composable
fun SaveLoadDialog(
    title: String,
    viewModel: GameViewModel,
    isSave: Boolean,
    onDismiss: () -> Unit,
    onSlotSelected: (Int) -> Unit
) {
    val slots by viewModel.saveSlots.collectAsState()

    LaunchedEffect(Unit) { viewModel.refreshSaveSlots() }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A2E),
        title = { Text(title, color = Color(0xFFEEEEEE)) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                // 세이브 슬롯 1~9번 표시 (0번은 내부용으로 숨김)
                items(9) { idx ->
                    val slot = idx + 1  // 슬롯 번호 1~9
                    val info = slots.getOrNull(slot)
                    val label = when {
                        info == null -> "Slot $slot — Empty"
                        info.exists -> "Slot $slot — ${info.comment}"
                        else -> "Slot $slot — Empty"
                    }
                    TextButton(
                        onClick = { onSlotSelected(slot) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            label,
                            color = if (info?.exists == true) Color(0xFFCCCCCC) else Color(0xFF666666),
                            textAlign = TextAlign.Start,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Divider(color = Color(0xFF333333))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color(0xFF88AAFF)) }
        }
    )
}