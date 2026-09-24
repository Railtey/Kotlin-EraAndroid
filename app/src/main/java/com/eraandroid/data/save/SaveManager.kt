package com.eraandroid.data.save

import android.content.Context
import android.util.Log
import com.eraandroid.core.interpreter.GameState
import com.eraandroid.core.vm.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// ─── CHKDATA 반환값 (PC emuera EraDataState 완전 호환) ──────────────────────
// 0 = OK          (로드 가능)
// 1 = FILENOTFOUND (파일 없음)
// 2 = GAME_ERROR  (다른 게임의 세이브)
// 3 = VERSION_ERROR(버전 다름, 일단 로드 가능)
// 4 = ETC_ERROR   (파일 손상 등 기타)
object ChkDataResult {
    const val OK           = 0L
    const val FILENOTFOUND = 1L
    const val GAME_ERROR   = 2L
    const val VERSION_ERROR= 3L
    const val ETC_ERROR    = 4L
}

// ─── 세이브 슬롯 정보 ────────────────────────────────────────────────────────
data class SaveSlotInfo(
    val slot: Int,
    val comment: String,
    val timestamp: Long,
    val exists: Boolean,
    val chkResult: Long = ChkDataResult.FILENOTFOUND
)

// ─── SaveManager ─────────────────────────────────────────────────────────────
class SaveManager(private val context: Context) {

    companion object {
        private const val TAG = "SaveManager"
        private const val FINISHED   = "__FINISHED"
        private const val EMU_START  = "__EMUERA_1808_STRAT__"
        private const val EMU_SEP    = "__EMU_SEPARATOR__"

        // PC판 save00.sav 형식 (save{슬롯:02d}.sav)
        // global.sav 별도
        // 게임코드/버전은 GameBase.CSV에서 동적 주입 → updateGameBase() 참조

        // OLD 섹션: 글로벌 정수배열 순서 (VariableCode.cs 기반)
        val GLOBAL_INT_ARRAY_ORDER = listOf(
            "DAY", "MONEY", "ITEM", "FLAG", "TFLAG", "UP",
            "PALAMLV", "EXPLV", "EJAC", "DOWN", "RESULT", "COUNT",
            "TARGET", "ASSI", "MASTER", "NOITEM", "LOSEBASE",
            "SELECTCOM", "ASSIPLAY", "PREVCOM",
            "_NOTUSE_14", "_NOTUSE_15",
            "TIME", "ITEMSALES", "PLAYER", "NEXTCOM", "PBAND", "BOUGHT",
            "_NOTUSE_1C", "_NOTUSE_1D",
            "A","B","C","D","E","F","G","H","I","J","K","L","M",
            "N","O","P","Q","R","S","T","U","V","W","X","Y","Z",
            "_NOTUSE_38","_NOTUSE_39","_NOTUSE_3A","_NOTUSE_3B"
        )

        // OLD 섹션: 캐릭터 정수배열 순서
        val CHARA_INT_ARRAY_ORDER = listOf(
            "BASE","MAXBASE","ABL","TALENT","EXP","MARK","PALAM",
            "SOURCE","EX","CFLAG","JUEL","RELATION","EQUIP","TEQUIP",
            "STAIN","GOTJUEL","NOWEX"
        )
    }

    // ─── GameBase 동적 주입 ──────────────────────────────────────────────────
    // GameBase.CSV 로딩 후 GameViewModel에서 호출
    // 다른 게임으로 교체해도 자동으로 맞는 코드/버전으로 동작함

    private var gameUniqueCode   : Long = 0L
    private var gameVersion      : Long = 0L
    private var gameVersionMinOk : Long = 0L

    fun updateGameBase(uniqueCode: Long, version: Long, minOkVersion: Long) {
        gameUniqueCode    = uniqueCode
        gameVersion       = version
        gameVersionMinOk  = if (minOkVersion > 0) minOkVersion else version
        Log.d(TAG, "GameBase 주입: code=$uniqueCode ver=$version minOk=$gameVersionMinOk")
    }

    // ─── 세이브 디렉토리 ─────────────────────────────────────────────────────
    // PC판과 동일하게 게임 폴더 내 sav/ 에 저장
    // setGameSavDir()로 게임 로드 시 지정, 미지정 시 앱 내부 fallback

    private var gameSavDir: File? = null

    /**
     * 게임 폴더 내 sav/ 디렉토리를 세이브 경로로 지정
     * loadGame(gameDir) 또는 loadGameFromDocumentUri() 호출 시 설정
     */
    fun setGameSavDir(dir: File) {
        gameSavDir = dir.also { it.mkdirs() }
        Log.d(TAG, "세이브 경로 설정: ${dir.absolutePath}")
    }

    // ─── 경로 헬퍼 ───────────────────────────────────────────────────────────

    private val saveDir: File
        get() = gameSavDir ?: File(context.filesDir, "saves").also { it.mkdirs() }

    private fun savFile(slot: Int)  = File(saveDir, "save%02d.sav".format(slot))
    private val globalFile: File    get() = File(saveDir, "global.sav")

    // ─── CHKDATA ─────────────────────────────────────────────────────────────

    /**
     * PC emuera 완전 호환 CHKDATA
     * RESULT에 세팅할 값 반환: 0=OK, 1=없음, 2=다른게임, 3=버전다름, 4=손상
     * RESULTS:0에는 세이브 코멘트(있을 경우)를 세팅할 것
     */
    suspend fun checkData(slot: Int): Pair<Long, String> = withContext(Dispatchers.IO) {
        val file = savFile(slot)
        if (!file.exists()) return@withContext Pair(ChkDataResult.FILENOTFOUND, "----")
        try {
            val lines = readLines(file)
            if (lines.size < 3) return@withContext Pair(ChkDataResult.ETC_ERROR, "파일이 손상되었습니다")
            // Line1: 게임코드, Line2: 버전, Line3: 코멘트
            val code    = lines[0].toLongOrNull() ?: return@withContext Pair(ChkDataResult.ETC_ERROR, "파일이 손상되었습니다")
            val version = lines[1].toLongOrNull() ?: return@withContext Pair(ChkDataResult.ETC_ERROR, "파일이 손상되었습니다")
            val comment = lines[2]
            if (code != gameUniqueCode) return@withContext Pair(ChkDataResult.GAME_ERROR, "다른 타이틀의 세이브 파일입니다")
            // PC판: バージョン違い認める 이상이면 로드 가능 (VERSION_ERROR 포함 로드 허용)
            if (version < gameVersionMinOk) return@withContext Pair(ChkDataResult.VERSION_ERROR, comment)
            Pair(ChkDataResult.OK, comment)
        } catch (e: Exception) {
            Log.e(TAG, "checkData slot=$slot error: ${e.message}")
            Pair(ChkDataResult.ETC_ERROR, "파일이 손상되었습니다")
        }
    }

    // ─── SAVEDATA ────────────────────────────────────────────────────────────

    suspend fun saveData(slot: Int, state: GameState, comment: String = "") {
        withContext(Dispatchers.IO) {
            try {
                val file = savFile(slot)
                val sb = StringBuilder()
                writeSavFile(sb, state, comment)
                file.writeText(sb.toString(), Charsets.UTF_8)
                Log.d(TAG, "saveData slot=$slot OK (${file.length()} bytes)")
            } catch (e: Exception) {
                Log.e(TAG, "saveData slot=$slot error: ${e.message}", e)
            }
        }
    }

    private fun writeSavFile(sb: StringBuilder, state: GameState, comment: String) {
        // ── 헤더 (4줄) ────────────────────────────────────────────────────────
        // Line1: 게임코드
        sb.appendLine(gameUniqueCode)
        // Line2: 버전
        sb.appendLine(gameVersion)
        // Line3: 코멘트
        sb.appendLine(comment)
        // Line4: 캐릭터 수
        val charas = state.charaSnapshot
        sb.appendLine(charas.size)

        // Line5: MASTERNAME (주인공 이름) — MASTER 변수의 첫 캐릭터 또는 빈값
        val masterIdx  = state.variableSnapshot["MASTER"]?.sparseData?.get(0)?.toLong()?.toInt() ?: 0
        val masterChara = charas.getOrNull(masterIdx)
        sb.appendLine(masterChara?.strVars?.get("NAME")?.getOrNull(0) ?: "")
        // Line6: CALLNAME (주인공 호칭)
        sb.appendLine(masterChara?.strVars?.get("CALLNAME")?.getOrNull(0) ?: "")

        // ── OLD 섹션: 캐릭터 ─────────────────────────────────────────────────
        for (chara in charas) {
            // dataString[0] = NAME
            sb.appendLine(chara.strVars["NAME"]?.getOrNull(0) ?: "")
            // dataString[1] = CALLNAME
            sb.appendLine(chara.strVars["CALLNAME"]?.getOrNull(0) ?: "")
            // dataInteger[0] = ISASSI (0 고정)
            sb.appendLine(0)
            // dataInteger[1] = NO
            sb.appendLine(chara.no)
            // dataIntegerArray[0..16]
            for (varName in CHARA_INT_ARRAY_ORDER) {
                val values = chara.numVars[varName] ?: emptyList()
                writeIntArray(sb, values)
            }
        }

        // ── OLD 섹션: 글로벌 변수 ────────────────────────────────────────────
        for (varName in GLOBAL_INT_ARRAY_ORDER) {
            if (varName.startsWith("_")) {
                writeIntArray(sb, emptyList())
                continue
            }
            val snap = state.variableSnapshot[varName]
            val values = snap?.sparseToList { it.toLong() } ?: emptyList()
            writeIntArray(sb, values)
        }
        // SAVESTR (글로벌 strArray[0])
        val saveStr = state.variableSnapshot["SAVESTR"]?.sparseToList { it.toEraString() } ?: emptyList()
        writeStringArray(sb, saveStr)

        // ── EXTENDED 섹션 ─────────────────────────────────────────────────────
        sb.appendLine(EMU_START)

        // 캐릭터별 extended (6섹션)
        for (chara in charas) {
            // 섹션0: CHARACTER_DATA|STRING (NICKNAME, MASTERNAME)
            val nickname   = chara.strVars["NICKNAME"]?.getOrNull(0) ?: ""
            val masterName = chara.strVars["MASTERNAME"]?.getOrNull(0) ?: ""
            if (nickname.isNotEmpty())   sb.appendLine("NICKNAME:$nickname")
            if (masterName.isNotEmpty()) sb.appendLine("MASTERNAME:$masterName")
            sb.appendLine(EMU_SEP)

            // 섹션1: CHARACTER_DATA|INTEGER (없음)
            sb.appendLine(EMU_SEP)

            // 섹션2: CHARACTER_DATA|STRING_ARRAY (CSTR)
            val cstr = chara.strVars["CSTR"] ?: emptyList()
            if (cstr.any { it.isNotEmpty() }) {
                sb.appendLine("CSTR")
                writeStringArray(sb, cstr)
            }
            sb.appendLine(EMU_SEP)

            // 섹션3: CHARACTER_DATA|INT_ARRAY (DOWNBASE, CUP, CDOWN, TCVAR)
            for (extVar in listOf("DOWNBASE","CUP","CDOWN","TCVAR")) {
                val vals = chara.numVars[extVar] ?: emptyList()
                if (vals.any { it != 0L }) {
                    sb.appendLine(extVar)
                    writeIntArray(sb, vals)
                }
            }
            sb.appendLine(EMU_SEP)

            // 섹션4: CHARACTER_DATA|STRING_2D (없음)
            sb.appendLine(EMU_SEP)

            // 섹션5: CHARACTER_DATA|INT_2D (없음)
            sb.appendLine(EMU_SEP)
        }

        // 글로벌 extended (8섹션)
        // 섹션0: STRING (없음)
        sb.appendLine(EMU_SEP)
        // 섹션1: INTEGER (없음)
        sb.appendLine(EMU_SEP)
        // 섹션2: STRING_ARRAY (TSTR 등)
        for (varName in listOf("TSTR")) {
            val vals = state.variableSnapshot[varName]?.sparseToList { it.toEraString() } ?: emptyList()
            if (vals.any { it.isNotEmpty() }) {
                sb.appendLine(varName)
                writeStringArray(sb, vals)
            }
        }
        sb.appendLine(EMU_SEP)
        // 섹션3: INT_ARRAY (확장 변수들)
        for (varName in listOf(
            "RELATION","HAVE","ABL","TALENT","EXP","MARK","PALAM","STAIN",
            "GOTJUEL","JUEL","NOWEX","EQUIP","TEQUIP","SOURCE","EX","TPALAM",
            "TFLAG","UP","DOWN","BOUGHT","CUP","CDOWN"
        )) {
            // 글로벌 스코프에 있는 경우만
            val vals = state.variableSnapshot[varName]?.sparseToList { it.toLong() } ?: continue
            if (vals.any { it != 0L }) {
                sb.appendLine(varName)
                writeIntArray(sb, vals)
            }
        }
        sb.appendLine(EMU_SEP)
        // 섹션4: STRING_2D (없음)
        sb.appendLine(EMU_SEP)
        // 섹션5: INT_2D (없음)
        sb.appendLine(EMU_SEP)
        // 섹션6: STRING_3D (없음)
        sb.appendLine(EMU_SEP)
        // 섹션7: INT_3D (없음)
        sb.appendLine(EMU_SEP)
        // 유저정의 변수 6섹션 (없음)
        repeat(6) { sb.appendLine(EMU_SEP) }
    }

    // ─── LOADDATA ────────────────────────────────────────────────────────────

    suspend fun loadData(slot: Int): GameState? = withContext(Dispatchers.IO) {
        val file = savFile(slot)
        if (!file.exists()) {
            // JSON fallback (구버전 호환)
            val jsonFile = File(saveDir, "save_$slot.json")
            if (jsonFile.exists()) return@withContext loadLegacyJson(jsonFile)
            return@withContext null
        }
        try {
            val content = file.readText(Charsets.UTF_8)
            val saveData = EmuSavParser.parse(content) ?: return@withContext null
            EmuSavParser.toGameState(saveData)
        } catch (e: Exception) {
            Log.e(TAG, "loadData slot=$slot error: ${e.message}", e)
            null
        }
    }

    // ─── SAVEGLOBAL ──────────────────────────────────────────────────────────

    suspend fun saveGlobal(scope: VariableScope) {
        withContext(Dispatchers.IO) {
            try {
                val sb = StringBuilder()
                // global.sav: 게임코드 + 버전 + GLOBAL 배열 + GLOBALS 배열
                sb.appendLine(gameUniqueCode)
                sb.appendLine(gameVersion)

                val global = scope.get("GLOBAL")
                val globals = scope.get("GLOBALS")

                writeIntArray(sb, global?.getAll()?.map { it.toLong() } ?: emptyList())
                writeStringArray(sb, globals?.getAll()?.map { it.toEraString() } ?: emptyList())

                globalFile.writeText(sb.toString(), Charsets.UTF_8)
                Log.d(TAG, "saveGlobal OK")
            } catch (e: Exception) {
                Log.e(TAG, "saveGlobal error: ${e.message}", e)
            }
        }
    }

    // ─── LOADGLOBAL ──────────────────────────────────────────────────────────

    suspend fun loadGlobal(scope: VariableScope) {
        withContext(Dispatchers.IO) {
            if (!globalFile.exists()) return@withContext
            try {
                val lines = readLines(globalFile)
                var pos = 0
                // Line1: 게임코드 (무시해도 됨)
                pos++
                // Line2: 버전
                pos++
                // GLOBAL 배열
                val (globalVals, nextPos) = readIntSection(lines, pos)
                pos = nextPos
                val globalVar = scope.getOrCreate("GLOBAL", false, listOf(2000))
                globalVals.forEachIndexed { i, v ->
                    if (i < 2000) globalVar.set(listOf(i), EraValue.of(v))
                }
                // GLOBALS 배열
                val (globalsVals, _) = readStringSection(lines, pos)
                val globalsVar = scope.getOrCreate("GLOBALS", true, listOf(100))
                globalsVals.forEachIndexed { i, v ->
                    if (i < 100) globalsVar.set(listOf(i), EraValue.of(v))
                }
                Log.d(TAG, "loadGlobal OK GLOBAL:0=${globalVals.getOrNull(0)}")
            } catch (e: Exception) {
                Log.e(TAG, "loadGlobal error: ${e.message}", e)
            }
        }
    }

    // ─── DELDATA ─────────────────────────────────────────────────────────────

    suspend fun deleteData(slot: Int) {
        withContext(Dispatchers.IO) {
            savFile(slot).delete()
            // JSON 구버전도 함께 삭제
            File(saveDir, "save_$slot.json").delete()
        }
    }

    // ─── 슬롯 정보 ───────────────────────────────────────────────────────────

    suspend fun getSaveSlotInfo(slot: Int): SaveSlotInfo {
        return withContext(Dispatchers.IO) {
            val (chkResult, comment) = checkData(slot)
            val file = savFile(slot)
            SaveSlotInfo(
                slot      = slot,
                comment   = comment,
                timestamp = if (file.exists()) file.lastModified() else 0L,
                exists    = chkResult != ChkDataResult.FILENOTFOUND,
                chkResult = chkResult
            )
        }
    }

    suspend fun getAllSaveSlotInfo(maxSlots: Int = 100): List<SaveSlotInfo> {
        return (0 until maxSlots).map { getSaveSlotInfo(it) }
    }

    // ─── PC .sav 파일 임포트 ─────────────────────────────────────────────────

    suspend fun importSavFiles(gameDir: File) {
        withContext(Dispatchers.IO) {
            val pattern = Regex("save(\\d{2,3})\\.sav", RegexOption.IGNORE_CASE)
            gameDir.listFiles()?.forEach { file ->
                val match = pattern.matchEntire(file.name) ?: return@forEach
                val slot  = match.groupValues[1].toIntOrNull() ?: return@forEach
                val dest  = savFile(slot)
                if (!dest.exists()) {
                    file.copyTo(dest, overwrite = false)
                    Log.d(TAG, "Imported ${file.name} → slot $slot")
                }
            }
            // global.sav 임포트
            val globalSrc = File(gameDir, "global.sav")
            if (globalSrc.exists() && !globalFile.exists()) {
                globalSrc.copyTo(globalFile, overwrite = false)
                Log.d(TAG, "Imported global.sav")
            }
        }
    }

    suspend fun importSavFilesFromDocument(
        rootDoc: androidx.documentfile.provider.DocumentFile,
        context: Context
    ) {
        withContext(Dispatchers.IO) {
            val savDir = rootDoc.listFiles().firstOrNull {
                it.isDirectory && it.name?.uppercase() == "SAV"
            } ?: rootDoc

            val pattern = Regex("save(\\d+)\\.sav", RegexOption.IGNORE_CASE)
            savDir.listFiles().forEach { file ->
                val name = file.name ?: return@forEach
                val match = pattern.matchEntire(name) ?: run {
                    // global.sav 처리
                    if (name.equals("global.sav", ignoreCase = true) && !globalFile.exists()) {
                        try {
                            context.contentResolver.openInputStream(file.uri)?.use { input ->
                                globalFile.outputStream().use { output -> input.copyTo(output) }
                            }
                            Log.d(TAG, "Imported global.sav")
                        } catch (e: Exception) {
                            Log.e(TAG, "Import global failed", e)
                        }
                    }
                    return@forEach
                }
                val slot = match.groupValues[1].toIntOrNull() ?: return@forEach
                val dest = savFile(slot)
                if (!dest.exists()) {
                    try {
                        context.contentResolver.openInputStream(file.uri)?.use { input ->
                            dest.outputStream().use { output -> input.copyTo(output) }
                        }
                        Log.d(TAG, "Imported $name → slot $slot")
                    } catch (e: Exception) {
                        Log.e(TAG, "Import failed: $name", e)
                    }
                }
            }
        }
    }

    // ─── 내부 유틸리티 ───────────────────────────────────────────────────────

    /**
     * 정수 배열을 PC .sav 포맷으로 직렬화
     * 마지막 비0값 이후는 저장하지 않음, __FINISHED 로 종료
     */
    private fun writeIntArray(sb: StringBuilder, values: List<Long>) {
        var lastNonZero = -1
        for (i in values.indices) if (values[i] != 0L) lastNonZero = i
        for (i in 0..lastNonZero) sb.appendLine(values[i])
        sb.appendLine(FINISHED)
    }

    /**
     * 문자열 배열을 PC .sav 포맷으로 직렬화
     */
    private fun writeStringArray(sb: StringBuilder, values: List<String>) {
        var lastNonEmpty = -1
        for (i in values.indices) if (values[i].isNotEmpty()) lastNonEmpty = i
        for (i in 0..lastNonEmpty) sb.appendLine(values[i])
        sb.appendLine(FINISHED)
    }

    /**
     * 파일에서 줄 목록 읽기 (BOM, CR 처리)
     */
    private fun readLines(file: File): List<String> {
        return file.readText(Charsets.UTF_8)
            .removePrefix("\uFEFF")
            .lines()
            .map { it.trimEnd('\r') }
    }

    /**
     * __FINISHED 종료 정수 배열 섹션 읽기
     */
    private fun readIntSection(lines: List<String>, startPos: Int): Pair<List<Long>, Int> {
        val result = mutableListOf<Long>()
        var pos = startPos
        while (pos < lines.size) {
            val line = lines[pos++]
            if (line == FINISHED) break
            line.toLongOrNull()?.let { result.add(it) }
        }
        return Pair(result, pos)
    }

    /**
     * __FINISHED 종료 문자열 배열 섹션 읽기
     */
    private fun readStringSection(lines: List<String>, startPos: Int): Pair<List<String>, Int> {
        val result = mutableListOf<String>()
        var pos = startPos
        while (pos < lines.size) {
            val line = lines[pos++]
            if (line == FINISHED) break
            result.add(line)
        }
        return Pair(result, pos)
    }

    // ─── 구버전 JSON fallback ────────────────────────────────────────────────

    private fun loadLegacyJson(file: File): GameState? {
        return try {
            val json = org.json.JSONObject(file.readText())
            val varSnapshots = mutableMapOf<String, VariableSnapshot>()
            val varsJson = json.optJSONObject("variables") ?: org.json.JSONObject()
            val varKeys = varsJson.keys()
            while (varKeys.hasNext()) {
                val name = varKeys.next()
                val varJson = varsJson.getJSONObject(name)
                val isString   = varJson.optBoolean("isString", false)
                val dimsArr    = varJson.optJSONArray("dimensions") ?: org.json.JSONArray()
                val dimensions = (0 until dimsArr.length()).map { dimsArr.getInt(it) }
                val dataArr    = varJson.optJSONArray("data") ?: org.json.JSONArray()
                val sparseData = mutableMapOf<Int, EraValue>()
                for (i in 0 until dataArr.length()) {
                    val v = if (isString) EraValue.of(dataArr.getString(i))
                    else EraValue.of(dataArr.getLong(i))
                    val default = if (isString) EraValue.EMPTY_STRING else EraValue.ZERO
                    if (v != default) sparseData[i] = v
                }
                varSnapshots[name] = VariableSnapshot(name, isString, dimensions, sparseData)
            }
            val charaSnapshots = mutableListOf<CharaSnapshot>()
            val charasJson = json.optJSONArray("charas") ?: org.json.JSONArray()
            for (i in 0 until charasJson.length()) {
                val charaJson = charasJson.getJSONObject(i)
                val no = charaJson.getInt("no")
                val numVars = mutableMapOf<String, List<Long>>()
                val numVarsJson = charaJson.optJSONObject("numVars") ?: org.json.JSONObject()
                val numKeys = numVarsJson.keys()
                while (numKeys.hasNext()) {
                    val k = numKeys.next()
                    val arr = numVarsJson.getJSONArray(k)
                    numVars[k] = (0 until arr.length()).map { arr.getLong(it) }
                }
                val strVars = mutableMapOf<String, List<String>>()
                val strVarsJson = charaJson.optJSONObject("strVars") ?: org.json.JSONObject()
                val strKeys = strVarsJson.keys()
                while (strKeys.hasNext()) {
                    val k = strKeys.next()
                    val arr = strVarsJson.getJSONArray(k)
                    strVars[k] = (0 until arr.length()).map { arr.getString(it) }
                }
                charaSnapshots.add(CharaSnapshot(no, numVars, strVars))
            }
            GameState(varSnapshots, charaSnapshots)
        } catch (e: Exception) {
            Log.e(TAG, "loadLegacyJson error: ${e.message}", e)
            null
        }
    }
}