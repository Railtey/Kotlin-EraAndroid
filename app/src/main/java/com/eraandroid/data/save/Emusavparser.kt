package com.eraandroid.data.save

import android.util.Log
import com.eraandroid.core.interpreter.GameState
import com.eraandroid.core.vm.*

/**
 * Emuera 1.808 .sav 파일 파서 — PC 원본(EraDataStream.cs) 완전 호환
 *
 * ── 파일 구조 (VariableCode.cs 기반 확정) ─────────────────────────────
 *
 * [헤더 6줄]
 *   Line1: 게임코드
 *   Line2: 버전
 *   Line3: 세이브 코멘트
 *   Line4: 캐릭터 수
 *   Line5: MASTERNAME (주인공 이름)
 *   Line6: CALLNAME  (주인공 호칭)
 *
 * [OLD 섹션] — CharacterData.SaveToStream / VariableData.SaveToStream
 *
 *   [캐릭터0 old]
 *     dataString[0] = NAME      → 1줄 (문자열)
 *     dataString[1] = CALLNAME  → 1줄
 *     dataInteger[0] = ISASSI   → 1줄
 *     dataInteger[1] = NO       → 1줄
 *     dataIntegerArray[0x00] = BASE    → __FINISHED
 *     dataIntegerArray[0x01] = MAXBASE → __FINISHED
 *     ...
 *     dataIntegerArray[0x10] = NOWEX   → __FINISHED  (총 0x11 = 17개)
 *     (dataStringArray 없음, strArrayCount=0)
 *
 *   [캐릭터1 old] ... [캐릭터N old]
 *
 *   [글로벌 old] — VariableData.SaveToStream
 *     (dataString 없음, strCount=0)
 *     (dataInteger 없음, intCount=0)
 *     dataIntegerArray[0x00] = DAY     → __FINISHED
 *     dataIntegerArray[0x01] = MONEY   → __FINISHED
 *     dataIntegerArray[0x02] = ITEM    → __FINISHED
 *     dataIntegerArray[0x03] = FLAG    → __FINISHED
 *     dataIntegerArray[0x04] = TFLAG   → __FINISHED
 *     dataIntegerArray[0x05] = UP      → __FINISHED
 *     dataIntegerArray[0x06] = PALAMLV → __FINISHED
 *     dataIntegerArray[0x07] = EXPLV   → __FINISHED
 *     dataIntegerArray[0x08] = EJAC    → __FINISHED
 *     dataIntegerArray[0x09] = DOWN    → __FINISHED
 *     dataIntegerArray[0x0A] = RESULT  → __FINISHED
 *     dataIntegerArray[0x0B] = COUNT   → __FINISHED
 *     dataIntegerArray[0x0C] = TARGET  → __FINISHED
 *     dataIntegerArray[0x0D] = ASSI    → __FINISHED
 *     dataIntegerArray[0x0E] = MASTER  → __FINISHED
 *     dataIntegerArray[0x0F] = NOITEM  → __FINISHED
 *     dataIntegerArray[0x10] = LOSEBASE→ __FINISHED
 *     dataIntegerArray[0x11] = SELECTCOM→__FINISHED
 *     dataIntegerArray[0x12] = ASSIPLAY→ __FINISHED
 *     dataIntegerArray[0x13] = PREVCOM → __FINISHED
 *     dataIntegerArray[0x14] = (NOTUSE_14) → __FINISHED
 *     dataIntegerArray[0x15] = (NOTUSE_15) → __FINISHED
 *     dataIntegerArray[0x16] = TIME    → __FINISHED
 *     dataIntegerArray[0x17] = ITEMSALES→__FINISHED
 *     dataIntegerArray[0x18] = PLAYER  → __FINISHED
 *     dataIntegerArray[0x19] = NEXTCOM → __FINISHED
 *     dataIntegerArray[0x1A] = PBAND   → __FINISHED
 *     dataIntegerArray[0x1B] = BOUGHT  → __FINISHED
 *     dataIntegerArray[0x1C] = (NOTUSE_1C) → __FINISHED
 *     dataIntegerArray[0x1D] = (NOTUSE_1D) → __FINISHED
 *     dataIntegerArray[0x1E] = A       → __FINISHED
 *     ...
 *     dataIntegerArray[0x37] = Z       → __FINISHED
 *     dataIntegerArray[0x38~0x3B] = NOTUSE → __FINISHED
 *     dataStringArray[0x00] = SAVESTR  → __FINISHED  (strArrayCount=1)
 *
 * [__EMUERA_1808_STRAT__]
 *
 * [EXTENDED 섹션] — 키:값 포맷 / __EMU_SEPARATOR__ 구분
 *
 *   [캐릭터별 extended] × N (각 캐릭터)
 *     섹션0 (STRING):  NICKNAME:xxx \n MASTERNAME:xxx \n __EMU_SEPARATOR__
 *     섹션1 (INTEGER): (없음) \n __EMU_SEPARATOR__
 *     섹션2 (STRING_ARRAY): CSTR \n 값들 \n __FINISHED \n __EMU_SEPARATOR__
 *     섹션3 (INTEGER_ARRAY): DOWNBASE/CUP/CDOWN/TCVAR/CFLAG... \n __EMU_SEPARATOR__
 *     섹션4 (STRING_2D):  __EMU_SEPARATOR__
 *     섹션5 (INTEGER_2D): CDFLAG... \n __EMU_SEPARATOR__
 *
 *   [글로벌 extended]
 *     섹션0 (STRING_ARRAY):  TSTR... \n __EMU_SEPARATOR__
 *     섹션1 (INTEGER_ARRAY): RANDDATA... \n __EMU_SEPARATOR__
 *     섹션2 (STRING_2D):  __EMU_SEPARATOR__
 *     섹션3 (INTEGER_2D): __EMU_SEPARATOR__
 *     섹션4 (STRING_3D):  __EMU_SEPARATOR__
 *     섹션5 (INTEGER_3D): __EMU_SEPARATOR__
 *     (6개 섹션 후 유저 정의 변수 섹션 6개 더)
 */
object EmuSavParser {

    private const val TAG = "EmuSavParser"
    private const val FINISHED = "__FINISHED"
    private const val EMU_START_1808 = "__EMUERA_1808_STRAT__"
    private const val EMU_START_1803 = "__EMUERA_1803_STRAT__"
    private const val EMU_START_1729 = "__EMUERA_1729_STRAT__"
    private const val EMU_START_1708 = "__EMUERA_1708_STRAT__"
    private const val EMU_START_1700 = "__EMUERA_STRAT__"
    private const val EMU_SEP = "__EMU_SEPARATOR__"

    // ── old 섹션: 글로벌 정수배열 순서 (VariableCode.cs 0x00~0x3B) ─────────
    private val GLOBAL_INT_ARRAY_ORDER = listOf(
        "DAY",       // 0x00
        "MONEY",     // 0x01
        "ITEM",      // 0x02
        "FLAG",      // 0x03
        "TFLAG",     // 0x04
        "UP",        // 0x05
        "PALAMLV",   // 0x06
        "EXPLV",     // 0x07
        "EJAC",      // 0x08
        "DOWN",      // 0x09
        "RESULT",    // 0x0A
        "COUNT",     // 0x0B
        "TARGET",    // 0x0C
        "ASSI",      // 0x0D
        "MASTER",    // 0x0E
        "NOITEM",    // 0x0F
        "LOSEBASE",  // 0x10
        "SELECTCOM", // 0x11
        "ASSIPLAY",  // 0x12
        "PREVCOM",   // 0x13
        "_NOTUSE_14",// 0x14
        "_NOTUSE_15",// 0x15
        "TIME",      // 0x16
        "ITEMSALES", // 0x17
        "PLAYER",    // 0x18
        "NEXTCOM",   // 0x19
        "PBAND",     // 0x1A
        "BOUGHT",    // 0x1B
        "_NOTUSE_1C",// 0x1C
        "_NOTUSE_1D",// 0x1D
        "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M",
        "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z",
        "_NOTUSE_38",// 0x38
        "_NOTUSE_39",// 0x39
        "_NOTUSE_3A",// 0x3A
        "_NOTUSE_3B" // 0x3B
    )

    // ── old 섹션: 캐릭터 정수배열 순서 (0x00~0x10, 총 17개) ────────────────
    private val CHARA_INT_ARRAY_ORDER = listOf(
        "BASE",    // 0x00
        "MAXBASE", // 0x01
        "ABL",     // 0x02
        "TALENT",  // 0x03
        "EXP",     // 0x04
        "MARK",    // 0x05
        "PALAM",   // 0x06
        "SOURCE",  // 0x07
        "EX",      // 0x08
        "CFLAG",   // 0x09
        "JUEL",    // 0x0A
        "RELATION",// 0x0B
        "EQUIP",   // 0x0C
        "TEQUIP",  // 0x0D
        "STAIN",   // 0x0E
        "GOTJUEL", // 0x0F
        "NOWEX"    // 0x10
    )

    // ── 진입점 ──────────────────────────────────────────────────────────────
    fun parse(content: String): EmuSaveData? {
        return try {
            val lines = content.lines()
                .map { it.trimEnd('\r') }
                .let { l ->
                    if (l.isNotEmpty() && l[0].startsWith("\uFEFF"))
                        listOf(l[0].removePrefix("\uFEFF")) + l.drop(1)
                    else l
                }
            parseInternal(lines)
        } catch (e: Exception) {
            Log.e(TAG, "Parse failed: ${e.message}", e)
            null
        }
    }

    private fun parseInternal(lines: List<String>): EmuSaveData {
        var pos = 0

        // ── 헤더 6줄 ────────────────────────────────────────────────────────
        val gameCode   = lines.getOrElse(pos++) { "" }
        val version    = lines.getOrElse(pos++) { "0" }.toLongOrNull() ?: 0L
        val comment    = lines.getOrElse(pos++) { "" }
        val charaCount = lines.getOrElse(pos++) { "0" }.toIntOrNull() ?: 0
        val masterName = lines.getOrElse(pos++) { "" }
        val callName   = lines.getOrElse(pos++) { "" }

        // __EMUERA_XXXX_STRAT__ 위치 파악
        val emuStartIdx = lines.indexOfFirst {
            it == EMU_START_1808 || it == EMU_START_1803 ||
                    it == EMU_START_1729 || it == EMU_START_1708 || it == EMU_START_1700
        }.takeIf { it >= 0 } ?: lines.size

        val emuVersion = when {
            emuStartIdx < lines.size && lines[emuStartIdx] == EMU_START_1808 -> 1808
            emuStartIdx < lines.size && lines[emuStartIdx] == EMU_START_1803 -> 1803
            emuStartIdx < lines.size && lines[emuStartIdx] == EMU_START_1729 -> 1729
            emuStartIdx < lines.size && lines[emuStartIdx] == EMU_START_1708 -> 1708
            emuStartIdx < lines.size -> 1700
            else -> 0
        }
        Log.d(TAG, "emuVersion=$emuVersion, charaCount=$charaCount, emuStartIdx=$emuStartIdx")

        // ── OLD 섹션 파싱 ────────────────────────────────────────────────────
        // 캐릭터당: dataString×2, dataInteger×2, dataIntegerArray×17
        // 총 섹션 수 = 2 + 2 + 17 = 21 (단, 문자열 1개씩 + 정수 1개씩은 __FINISHED 없이 1줄)
        // 실제로는 1줄짜리는 그냥 1줄, 배열만 __FINISHED 종료

        val charaOldList = mutableListOf<CharaOldData>()
        for (i in 0 until charaCount) {
            if (pos >= emuStartIdx) break
            val result = readCharaOld(lines, pos, emuStartIdx)
            charaOldList.add(result.first)
            pos = result.second
        }

        // 글로벌 old 섹션: intArray×60, strArray×1
        val globalIntArrays = mutableMapOf<String, List<Long>>()   // 이름 → 값 목록
        val globalStrArrays = mutableMapOf<String, List<String>>()

        for (varName in GLOBAL_INT_ARRAY_ORDER) {
            if (pos >= emuStartIdx) break
            val (sec, nextPos) = readSection(lines, pos, emuStartIdx)
            pos = nextPos
            if (!varName.startsWith("_")) {
                globalIntArrays[varName] = sec.mapNotNull { it.toLongOrNull() }
            }
        }
        // SAVESTR (글로벌 strArray[0])
        if (pos < emuStartIdx) {
            val (sec, nextPos) = readSection(lines, pos, emuStartIdx)
            pos = nextPos
            globalStrArrays["SAVESTR"] = sec
        }

        Log.d(TAG, "글로벌 old 파싱 완료. pos=$pos, FLAG 크기=${globalIntArrays["FLAG"]?.size}, " +
                "FLAG[0]=${globalIntArrays["FLAG"]?.getOrNull(0)}, " +
                "MONEY[0]=${globalIntArrays["MONEY"]?.getOrNull(0)}")

        // ── EXTENDED 섹션 파싱 (__EMUERA_XXXX_STRAT__ 이후) ─────────────────
        val charaExtList = mutableListOf<CharaExtData>()
        val globalExtIntArrays = mutableMapOf<String, List<Long>>()
        val globalExtStrArrays = mutableMapOf<String, List<String>>()

        if (emuStartIdx < lines.size) {
            pos = emuStartIdx + 1
            // 캐릭터별 extended (각 6개 섹션 블록)
            for (i in 0 until charaCount) {
                if (pos >= lines.size) break
                val result = readCharaExtended(lines, pos, emuVersion)
                charaExtList.add(result.first)
                pos = result.second
            }
            // 글로벌 extended (6~12개 섹션)
            if (pos < lines.size) {
                val result = readGlobalExtended(lines, pos, emuVersion)
                globalExtStrArrays.putAll(result.first)
                globalExtIntArrays.putAll(result.second)
            }
        }

        Log.d(TAG, "Extended 파싱: charaExt=${charaExtList.size}, globalExtInt keys=${globalExtIntArrays.keys}, globalExtStr keys=${globalExtStrArrays.keys}")

        return EmuSaveData(
            gameCode        = gameCode,
            version         = version,
            comment         = comment,
            charaCount      = charaCount,
            masterName      = masterName,
            callName        = callName,
            globalIntArrays = globalIntArrays,
            globalStrArrays = globalStrArrays,
            globalExtIntArrays = globalExtIntArrays,
            globalExtStrArrays = globalExtStrArrays,
            charaOldList    = charaOldList,
            charaExtList    = charaExtList
        )
    }

    // ── 캐릭터 OLD 섹션 읽기 ─────────────────────────────────────────────────
    // CharacterData.SaveToStream:
    //   strCount=2 (NAME, CALLNAME): 각 1줄
    //   intCount=2 (ISASSI, NO): 각 1줄
    //   intArrayCount=0x11=17 (BASE~NOWEX): 각 __FINISHED 종료
    //   strArrayCount=0
    private fun readCharaOld(lines: List<String>, startPos: Int, limit: Int): Pair<CharaOldData, Int> {
        var pos = startPos

        // dataString[0] = NAME (1줄)
        val name = if (pos < limit) lines[pos++] else ""
        // dataString[1] = CALLNAME (1줄)
        val callNameOld = if (pos < limit) lines[pos++] else ""
        // dataInteger[0] = ISASSI (1줄)
        val isAssi = if (pos < limit) lines[pos++].toLongOrNull() ?: 0L else 0L
        // dataInteger[1] = NO (1줄)
        val no = if (pos < limit) lines[pos++].toLongOrNull() ?: 0 else 0

        val intArrays = mutableMapOf<String, List<Long>>()
        for (varName in CHARA_INT_ARRAY_ORDER) {
            if (pos >= limit) break
            val (sec, nextPos) = readSection(lines, pos, limit)
            pos = nextPos
            intArrays[varName] = sec.mapNotNull { it.toLongOrNull() }
        }

        return Pair(
            CharaOldData(
                name       = name,
                callName   = callNameOld,
                isAssi     = isAssi,
                no         = no.toInt(),
                intArrays  = intArrays
            ),
            pos
        )
    }

    // ── 캐릭터 EXTENDED 섹션 읽기 ────────────────────────────────────────────
    // CharacterData.SaveToStreamExtended:
    //   섹션0: CHARACTER_DATA|STRING       → NICKNAME:, MASTERNAME: (key:value)
    //   섹션1: CHARACTER_DATA|INTEGER      → (없음, IS_ASSI/NO는 old에서)
    //   섹션2: CHARACTER_DATA|STRING_ARRAY → CSTR 배열
    //   섹션3: CHARACTER_DATA|INT_ARRAY    → DOWNBASE, CUP, CDOWN, TCVAR, CFLAG(extended)
    //   섹션4: CHARACTER_DATA|STRING_2D    → (없음)
    //   섹션5: CHARACTER_DATA|INT_2D       → CDFLAG
    // 각 섹션은 __EMU_SEPARATOR__ 로 끝남
    private fun readCharaExtended(lines: List<String>, startPos: Int, emuVersion: Int): Pair<CharaExtData, Int> {
        var pos = startPos
        val ext = CharaExtData()

        // 섹션0: key:value 문자열 단일값
        val sec0 = readExtKeyValueSection(lines, pos)
        pos = sec0.second
        ext.nickname   = sec0.first["NICKNAME"] ?: ""
        ext.masterName = sec0.first["MASTERNAME"] ?: ""

        // 섹션1: key:long 단일값 (일반적으로 비어있음)
        val sec1 = readExtKeyValueSection(lines, pos)
        pos = sec1.second
        // 보통 비어 있음

        // 섹션2: 문자열 배열 (CSTR 등) - key\n값들\n__FINISHED 형태
        val sec2 = readExtArraySection(lines, pos)
        pos = sec2.second
        ext.cstr = sec2.first["CSTR"] ?: emptyList()

        // 섹션3: 정수 배열 (DOWNBASE, CUP, CDOWN, TCVAR 등)
        val sec3 = readExtIntArraySection(lines, pos)
        pos = sec3.second
        ext.downBase = sec3.first["DOWNBASE"] ?: emptyList()
        ext.cup      = sec3.first["CUP"] ?: emptyList()
        ext.cdown    = sec3.first["CDOWN"] ?: emptyList()
        ext.tcvar    = sec3.first["TCVAR"] ?: emptyList()

        // 섹션4: 문자열 2D (미구현, 그냥 넘김)
        pos = skipToSeparator(lines, pos)

        // 섹션5: 정수 2D (CDFLAG)
        // 1708 이전이면 없음
        if (emuVersion >= 1708) {
            pos = skipToSeparator(lines, pos)
        }

        return Pair(ext, pos)
    }

    // ── 글로벌 EXTENDED 섹션 읽기 ────────────────────────────────────────────
    // VariableData.SaveToStreamExtended (non-character, non-global):
    //   섹션0: STRING       → SAVEDATA_TEXT 등 (보통 없음)
    //   섹션1: INTEGER      → (보통 없음)
    //   섹션2: STRING_ARRAY → TSTR 등
    //   섹션3: INT_ARRAY    → RANDDATA 등
    //   섹션4: STRING_2D    → (없음)
    //   섹션5: INT_2D       → DITEMTYPE, DA~DE
    //   섹션6: STRING_3D    → (없음)
    //   섹션7: INT_3D       → TA, TB 등
    //   + 유저정의 변수 6개 섹션
    private fun readGlobalExtended(lines: List<String>, pos0: Int, emuVersion: Int): Pair<Map<String, List<String>>, Map<String, List<Long>>> {
        var pos = pos0
        val strArrays = mutableMapOf<String, List<String>>()
        val intArrays = mutableMapOf<String, List<Long>>()

        // 섹션0: 단일 문자열 (SAVEDATA_TEXT 등)
        pos = skipToSeparator(lines, pos)
        // 섹션1: 단일 정수 (없음)
        pos = skipToSeparator(lines, pos)
        // 섹션2: 문자열 배열 (TSTR 등)
        val sec2 = readExtArraySection(lines, pos)
        pos = sec2.second
        strArrays.putAll(sec2.first)
        // 섹션3: 정수 배열 (RANDDATA 등)
        val sec3 = readExtIntArraySection(lines, pos)
        pos = sec3.second
        intArrays.putAll(sec3.first)
        // 나머지 섹션 (2D, 3D) 스킵
        // 최대 8섹션까지만 시도
        repeat(6) {
            if (pos < lines.size) pos = skipToSeparator(lines, pos)
        }

        return Pair(strArrays, intArrays)
    }

    // ── Extended 파싱 헬퍼 ──────────────────────────────────────────────────

    /** key:value 형태의 한 섹션을 읽음. __EMU_SEPARATOR__ 가 구분자 */
    private fun readExtKeyValueSection(lines: List<String>, startPos: Int): Pair<Map<String, String>, Int> {
        val result = mutableMapOf<String, String>()
        var pos = startPos
        while (pos < lines.size) {
            val line = lines[pos++]
            if (line == EMU_SEP) break
            val idx = line.indexOf(':')
            if (idx >= 0) {
                result[line.substring(0, idx)] = line.substring(idx + 1)
            }
        }
        return Pair(result, pos)
    }

    /**
     * 문자열 배열 섹션 읽기:
     *   VARNAME
     *   값1
     *   값2
     *   __FINISHED
     *   (다음 변수...)
     *   __EMU_SEPARATOR__
     */
    private fun readExtArraySection(lines: List<String>, startPos: Int): Pair<Map<String, List<String>>, Int> {
        val result = mutableMapOf<String, List<String>>()
        var pos = startPos
        while (pos < lines.size) {
            val line = lines[pos]
            if (line == EMU_SEP) { pos++; break }
            if (line == FINISHED) { pos++; continue }
            // 변수명 줄 (숫자로 파싱 불가, 비어있지 않음)
            val varName = line
            pos++
            val values = mutableListOf<String>()
            while (pos < lines.size) {
                val vLine = lines[pos++]
                if (vLine == FINISHED) break
                if (vLine == EMU_SEP) { pos--; break } // 구분자면 한 칸 되돌리기
                values.add(vLine)
            }
            if (varName.isNotEmpty() && varName != EMU_SEP) {
                result[varName] = values
            }
        }
        return Pair(result, pos)
    }

    /**
     * 정수 배열 섹션 읽기:
     *   VARNAME
     *   값1
     *   값2
     *   __FINISHED
     *   __EMU_SEPARATOR__
     */
    private fun readExtIntArraySection(lines: List<String>, startPos: Int): Pair<Map<String, List<Long>>, Int> {
        val result = mutableMapOf<String, List<Long>>()
        var pos = startPos
        while (pos < lines.size) {
            val line = lines[pos]
            if (line == EMU_SEP) { pos++; break }
            if (line == FINISHED) { pos++; continue }
            // WriteExtended(key, array): 0이 아닌 값이 하나라도 있을 때만 기록
            // 형식: varName\n값1\n값2\n__FINISHED
            val varName = line
            pos++
            val values = mutableListOf<Long>()
            while (pos < lines.size) {
                val vLine = lines[pos++]
                if (vLine == FINISHED) break
                if (vLine == EMU_SEP) { pos--; break }
                vLine.toLongOrNull()?.let { values.add(it) }
            }
            if (varName.isNotEmpty() && varName != EMU_SEP) {
                result[varName] = values
            }
        }
        return Pair(result, pos)
    }

    /** __EMU_SEPARATOR__ 가 나올 때까지 스킵 */
    private fun skipToSeparator(lines: List<String>, startPos: Int): Int {
        var pos = startPos
        while (pos < lines.size) {
            if (lines[pos++] == EMU_SEP) break
        }
        return pos
    }

    // ── 기본 섹션 읽기 (__FINISHED 종료) ─────────────────────────────────────
    private fun readSection(lines: List<String>, startPos: Int, limit: Int): Pair<List<String>, Int> {
        val data = mutableListOf<String>()
        var pos = startPos
        while (pos < limit) {
            val line = lines[pos++]
            if (line == FINISHED) break
            data.add(line)
        }
        return Pair(data, pos)
    }

    // ── GameState 변환 ───────────────────────────────────────────────────────
    fun toGameState(saveData: EmuSaveData): GameState {
        val varSnapshots = mutableMapOf<String, VariableSnapshot>()

        // 글로벌 정수 배열 (old 섹션)
        for ((name, values) in saveData.globalIntArrays) {
            if (values.isEmpty()) continue
            val sparseData = mutableMapOf<Int, EraValue>()
            values.forEachIndexed { i, v -> if (v != 0L) sparseData[i] = EraValue.of(v) }
            varSnapshots[name] = VariableSnapshot(
                name       = name,
                isString   = false,
                dimensions = listOf(values.size),
                sparseData = sparseData
            )
        }

        // 글로벌 문자열 배열 (old 섹션 - SAVESTR)
        for ((name, values) in saveData.globalStrArrays) {
            if (values.isEmpty()) continue
            val sparseData = mutableMapOf<Int, EraValue>()
            values.forEachIndexed { i, v -> if (v.isNotEmpty()) sparseData[i] = EraValue.of(v) }
            varSnapshots[name] = VariableSnapshot(
                name       = name,
                isString   = true,
                dimensions = listOf(values.size),
                sparseData = sparseData
            )
        }

        // 글로벌 extended 정수배열 (RANDDATA 등)
        for ((name, values) in saveData.globalExtIntArrays) {
            if (values.isEmpty()) continue
            val sparseData = mutableMapOf<Int, EraValue>()
            values.forEachIndexed { i, v -> if (v != 0L) sparseData[i] = EraValue.of(v) }
            // old 섹션에 없는 경우만 추가 (extended는 덮어쓰기 우선)
            varSnapshots[name] = VariableSnapshot(
                name       = name,
                isString   = false,
                dimensions = listOf(values.size),
                sparseData = sparseData
            )
        }

        // 글로벌 extended 문자열배열 (TSTR 등)
        for ((name, values) in saveData.globalExtStrArrays) {
            if (values.isEmpty()) continue
            val sparseData = mutableMapOf<Int, EraValue>()
            values.forEachIndexed { i, v -> if (v.isNotEmpty()) sparseData[i] = EraValue.of(v) }
            varSnapshots[name] = VariableSnapshot(
                name       = name,
                isString   = true,
                dimensions = listOf(values.size),
                sparseData = sparseData
            )
        }

        // 캐릭터 데이터
        val charaSnapshots = saveData.charaOldList.mapIndexed { idx, old ->
            val ext = saveData.charaExtList.getOrNull(idx) ?: CharaExtData()
            buildCharaSnapshot(old, ext)
        }

        Log.d(TAG, "로드 완료: 캐릭터=${charaSnapshots.size}, 변수=${varSnapshots.size}")
        Log.d(TAG, "  FLAG[0]=${(varSnapshots["FLAG"]?.sparseData?.get(0) as? EraValue.EraInt)?.value}")
        Log.d(TAG, "  MONEY[0]=${(varSnapshots["MONEY"]?.sparseData?.get(0) as? EraValue.EraInt)?.value}")
        Log.d(TAG, "  TARGET[0]=${(varSnapshots["TARGET"]?.sparseData?.get(0) as? EraValue.EraInt)?.value}")
        Log.d(TAG, "  ASSI[0]=${(varSnapshots["ASSI"]?.sparseData?.get(0) as? EraValue.EraInt)?.value}")
        charaSnapshots.forEach { snap ->
            Log.d(TAG, "  캐릭터NO=${snap.no} NAME=${snap.strVars["NAME"]?.firstOrNull()} " +
                    "BASE[0]=${snap.numVars["BASE"]?.getOrNull(0)} " +
                    "TALENT[3]=${snap.numVars["TALENT"]?.getOrNull(3)}")
        }

        return GameState(varSnapshots, charaSnapshots)
    }

    private fun buildCharaSnapshot(old: CharaOldData, ext: CharaExtData): CharaSnapshot {
        val numVars = mutableMapOf<String, List<Long>>()
        val strVars = mutableMapOf<String, List<String>>()

        // old 섹션 정수 배열
        for ((varName, values) in old.intArrays) {
            numVars[varName] = values
        }

        // extended 정수 배열 (DOWNBASE, CUP, CDOWN, TCVAR)
        if (ext.downBase.isNotEmpty()) numVars["DOWNBASE"] = ext.downBase
        if (ext.cup.isNotEmpty())      numVars["CUP"]      = ext.cup
        if (ext.cdown.isNotEmpty())    numVars["CDOWN"]    = ext.cdown
        if (ext.tcvar.isNotEmpty())    numVars["TCVAR"]    = ext.tcvar

        // 문자열 변수
        strVars["NAME"]       = listOf(old.name)
        strVars["CALLNAME"]   = listOf(old.callName)
        strVars["NICKNAME"]   = listOf(ext.nickname)
        strVars["MASTERNAME"] = listOf(ext.masterName)
        if (ext.cstr.isNotEmpty()) {
            strVars["CSTR"] = ext.cstr
        }

        return CharaSnapshot(no = old.no, numVars = numVars, strVars = strVars)
    }

    // ── 디버그 모드 감지 ─────────────────────────────────────────────────────
    fun isDebugMode(saveData: EmuSaveData): Boolean {
        val masterTalent = saveData.charaOldList.firstOrNull()?.intArrays?.get("TALENT")
        if (masterTalent?.getOrNull(998) == 1L) return true
        return saveData.comment.contains("DEBUG")
    }
}

// ── 데이터 클래스 ─────────────────────────────────────────────────────────────

data class EmuSaveData(
    val gameCode: String,
    val version: Long,
    val comment: String,
    val charaCount: Int,
    val masterName: String,
    val callName: String,
    // old 섹션
    val globalIntArrays: Map<String, List<Long>>,
    val globalStrArrays: Map<String, List<String>>,
    // extended 섹션
    val globalExtIntArrays: Map<String, List<Long>>,
    val globalExtStrArrays: Map<String, List<String>>,
    val charaOldList: List<CharaOldData>,
    val charaExtList: List<CharaExtData>
)

data class CharaOldData(
    val name: String,
    val callName: String,
    val isAssi: Long,
    val no: Int,
    val intArrays: Map<String, List<Long>>
)

class CharaExtData {
    var nickname: String = ""
    var masterName: String = ""
    var cstr: List<String> = emptyList()
    var downBase: List<Long> = emptyList()
    var cup: List<Long> = emptyList()
    var cdown: List<Long> = emptyList()
    var tcvar: List<Long> = emptyList()
}