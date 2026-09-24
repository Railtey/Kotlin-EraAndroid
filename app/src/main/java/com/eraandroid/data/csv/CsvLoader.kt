package com.eraandroid.data.csv

import android.content.Context
import com.eraandroid.core.vm.CharaData
import com.eraandroid.core.vm.CharaManager
import com.eraandroid.core.vm.VariableScope
import java.io.InputStream

data class CsvRecord(val index: Int, val name: String, val value: String)

object CsvLoader {

    /**
     * Parse a standard ERA CSV file:
     * Format per line: INDEX,NAME,VALUE  or  INDEX,VALUE
     */
    fun parseCsvFile(input: InputStream, charset: String = "Shift_JIS"): List<CsvRecord> {
        val records = mutableListOf<CsvRecord>()
        val bytes = input.readBytes()
        // BOM 및 인코딩 자동 감지
        val (cleanBytes, detectedCharset) = when {
            bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() ->
                Pair(bytes.drop(3).toByteArray(), Charsets.UTF_8)
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
                Pair(bytes.drop(2).toByteArray(), Charsets.UTF_16LE)
            else -> {
                // UTF-8 유효성 검사
                val isUtf8 = try { String(bytes, Charsets.UTF_8).toByteArray(Charsets.UTF_8).contentEquals(bytes) } catch (e: Exception) { false }
                if (isUtf8) Pair(bytes, Charsets.UTF_8) else Pair(bytes, charset(charset))
            }
        }
        val lines = String(cleanBytes, detectedCharset).lines()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith(";")) continue
            val commentIdx = trimmed.indexOf(';')
            val clean = if (commentIdx >= 0) trimmed.substring(0, commentIdx).trim() else trimmed
            if (clean.isEmpty()) continue
            val parts = clean.split(",").map { it.trim() }
            when {
                parts.size >= 3 -> {
                    val idx = parts[0].toIntOrNull()
                    if (idx != null) {
                        // 숫자,이름,값 형식
                        records.add(CsvRecord(idx, parts[1], parts[2]))
                    } else {
                        // 이름,값,... 형식 (Gamebase 등)
                        records.add(CsvRecord(records.size, parts[0], parts[1]))
                    }
                }
                parts.size == 2 -> {
                    val idx = parts[0].toIntOrNull()
                    if (idx != null) {
                        records.add(CsvRecord(idx, parts[1], ""))
                    } else {
                        // 이름,값 형식 (Gamebase)
                        records.add(CsvRecord(records.size, parts[0], parts[1]))
                    }
                }
                parts.size == 1 -> {
                    records.add(CsvRecord(records.size, parts[0], ""))
                }
            }
        }
        return records
    }

    private fun charset(name: String): java.nio.charset.Charset {
        return try { java.nio.charset.Charset.forName(name) }
        catch (e: Exception) { Charsets.UTF_8 }
    }

    /**
     * Load CHARA*.csv files into CharaManager
     * Expected format from Emuera:
     * 番号,NO (or NAME, etc.)
     */
    fun loadCharaCsv(input: InputStream, charaManager: CharaManager, charset: String = "Shift_JIS") {
        val bytes = input.readBytes()
        val (cleanBytes, detectedCharset) = when {
            bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() ->
                Pair(bytes.drop(3).toByteArray(), Charsets.UTF_8)
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
                Pair(bytes.drop(2).toByteArray(), Charsets.UTF_16LE)
            else -> {
                val isUtf8 = try { String(bytes, Charsets.UTF_8).toByteArray(Charsets.UTF_8).contentEquals(bytes) } catch (e: Exception) { false }
                if (isUtf8) Pair(bytes, Charsets.UTF_8) else Pair(bytes, charset(charset))
            }
        }
        val lines = String(cleanBytes, detectedCharset).lines()

        val chara = CharaData(0)
        var charaNo = 0

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith(";")) continue
            val commentIdx = trimmed.indexOf(';')
            val clean = if (commentIdx >= 0) trimmed.substring(0, commentIdx).trim() else trimmed
            if (clean.isEmpty()) continue

            // 후행 쉼표 제거 후 분리
            val parts = clean.trimEnd(',').split(",").map { it.trim() }
            if (parts.isEmpty()) continue

            // Chara CSV 형식:
            //   番号,0               → 캐릭터 번호
            //   名前,당신             → 이름 (키,값)
            //   基礎,0,2500          → 숫자변수 (키,인덱스,값)
            //   CSTR,1,보통 집       → 문자열변수 (키,인덱스,값)
            val keyRaw = parts[0]
            val mappedName = when (keyRaw.trim()) {
                "基礎"               -> "BASE"
                "最大基礎", "基礎最大"  -> "MAXBASE"
                "素質"               -> "TALENT"
                "能力"               -> "ABL"
                "フラグ", "旗"        -> "CFLAG"
                "経験"               -> "EXP"
                "好感度", "親密度"    -> "RELATION"
                "相性"               -> "RELATION"
                "刻印"               -> "MARK"
                "汚染", "汚れ"        -> "STAIN"
                "装備"               -> "EQUIP"
                "一時装備"           -> "TEQUIP"
                "珠"                -> "JUEL"
                "源泉"               -> "SOURCE"
                "EX"                -> "EX"
                "CSTR"              -> "CSTR"
                "番号"               -> "NO"
                "名前"               -> "NAME"
                "呼び名"             -> "CALLNAME"
                "あだ名"             -> "NICKNAME"
                "主人名"             -> "MASTERNAME"
                else                -> keyRaw.trim()
            }

            when (mappedName.uppercase()) {
                "NO" -> charaNo = parts.getOrNull(1)?.toIntOrNull() ?: 0
                "NAME"       -> chara.setStr("NAME",       0, parts.getOrNull(1) ?: "")
                "CALLNAME"   -> chara.setStr("CALLNAME",   0, parts.getOrNull(1) ?: "")
                "NICKNAME"   -> chara.setStr("NICKNAME",   0, parts.getOrNull(1) ?: "")
                "MASTERNAME" -> chara.setStr("MASTERNAME", 0, parts.getOrNull(1) ?: "")
                else -> {
                    if (parts.size >= 3) {
                        // 키,인덱스,값 형식 (BASE,0,2500 / CSTR,1,보통 집 등)
                        val idx = parts[1].toIntOrNull() ?: 0
                        val valueStr = parts[2]
                        val numVal = valueStr.toLongOrNull()
                        if (numVal != null) {
                            chara.setNum(mappedName, idx, numVal)
                            // BASE는 MAXBASE의 초기값이기도 함 (emuera 원본 동작)
                            if (mappedName.uppercase() == "BASE") {
                                chara.setNum("MAXBASE", idx, numVal)
                            }
                        } else if (valueStr.isNotEmpty()) {
                            chara.setStr(mappedName, idx, valueStr)
                        }
                    } else if (parts.size == 2) {
                        // 키,값 형식 (인덱스 없음 → 0)
                        val valueStr = parts[1]
                        val numVal = valueStr.toLongOrNull()
                        if (numVal != null) {
                            chara.setNum(mappedName, 0, numVal)
                            if (mappedName.uppercase() == "BASE") {
                                chara.setNum("MAXBASE", 0, numVal)
                            }
                        } else if (valueStr.isNotEmpty()) {
                            chara.setStr(mappedName, 0, valueStr)
                        }
                    }
                }
            }
        }

        val finalChara = CharaData(charaNo)
        for ((k, v) in chara.numVars) finalChara.numVars[k] = v
        for ((k, v) in chara.strVars) finalChara.strVars[k] = v
        charaManager.registerTemplate(finalChara)
    }

    /**
     * Load name CSV (e.g. ABL.CSV, TALENT.CSV)
     * Returns map of index -> name
     */
    fun loadNameCsv(input: InputStream, charset: String = "Shift_JIS"): Map<Int, String> {
        val records = parseCsvFile(input, charset)
        return records.associate { it.index to it.name }
    }

    /**
     * Load variable CSV (e.g. ITEM.CSV with index, name, price)
     */
    fun loadItemCsv(input: InputStream, scope: VariableScope, charset: String = "Shift_JIS") {
        val records = parseCsvFile(input, charset)
        val itemName = scope.getOrCreate("ITEMNAME", true, listOf(1000))
        val itemPrice = scope.getOrCreate("ITEMPRICE", false, listOf(1000))

        for (record in records) {
            itemName.set(listOf(record.index), com.eraandroid.core.vm.EraValue.of(record.name))
            val price = record.value.toLongOrNull() ?: 0L
            itemPrice.set(listOf(record.index), com.eraandroid.core.vm.EraValue.of(price))
        }
        android.util.Log.d("ERA_ITEM", "ITEMPRICE:50=${itemPrice.get(listOf(50))} ITEMPRICE:113=${itemPrice.get(listOf(113))} ITEMNAME:50=${itemName.get(listOf(50))}")
    }
}

/**
 * Higher-level loader that scans a game directory
 */
class GameDataLoader(private val context: Context) {

    data class CsvNameTable(
        val ablNames: Map<Int, String> = emptyMap(),
        val talentNames: Map<Int, String> = emptyMap(),
        val expNames: Map<Int, String> = emptyMap(),
        val markNames: Map<Int, String> = emptyMap(),
        val palamNames: Map<Int, String> = emptyMap(),
        val trainNames: Map<Int, String> = emptyMap(),
        val itemNames: Map<Int, String> = emptyMap(),
        val comNames: Map<Int, String> = emptyMap(),
        val flagNames: Map<Int, String> = emptyMap(),
        val tflagNames: Map<Int, String> = emptyMap(),
        val cflagNames: Map<Int, String> = emptyMap(),
    )

    private val csvCharset = "Shift_JIS"

    fun loadFromAssets(
        basePath: String,
        scope: VariableScope,
        charaManager: CharaManager
    ): CsvNameTable {
        val assetManager = context.assets
        val ablNames = loadNameMapFromAssets(assetManager, "$basePath/CSV/ABL.CSV")
        val talentNames = loadNameMapFromAssets(assetManager, "$basePath/CSV/TALENT.CSV")
        val expNames = loadNameMapFromAssets(assetManager, "$basePath/CSV/EXP.CSV")
        val markNames = loadNameMapFromAssets(assetManager, "$basePath/CSV/MARK.CSV")
        val palamNames = loadNameMapFromAssets(assetManager, "$basePath/CSV/PALAM.CSV")
        val trainNames = loadNameMapFromAssets(assetManager, "$basePath/CSV/TRAIN.CSV")
        val itemNames = loadNameMapFromAssets(assetManager, "$basePath/CSV/ITEM.CSV")
        val comNames = loadNameMapFromAssets(assetManager, "$basePath/CSV/COM.CSV")
        val flagNames = loadNameMapFromAssets(assetManager, "$basePath/CSV/FLAG.CSV")
        val tflagNames = loadNameMapFromAssets(assetManager, "$basePath/CSV/TFLAG.CSV")
        val cflagNames = loadNameMapFromAssets(assetManager, "$basePath/CSV/CFLAG.CSV")

        // Load items
        loadCsvFromAssets(assetManager, "$basePath/CSV/ITEM.CSV") { stream ->
            CsvLoader.loadItemCsv(stream, scope, csvCharset)
        }

        // Load chara files
        try {
            val charaFiles = assetManager.list("$basePath/CSV") ?: emptyArray()
            for (file in charaFiles) {
                if (file.uppercase().startsWith("CHARA") && file.uppercase().endsWith(".CSV")) {
                    loadCsvFromAssets(assetManager, "$basePath/CSV/$file") { stream ->
                        CsvLoader.loadCharaCsv(stream, charaManager, csvCharset)
                    }
                }
            }
        } catch (e: Exception) { /* no chara files */ }

        return CsvNameTable(ablNames, talentNames, expNames, markNames, palamNames,
            trainNames, itemNames, comNames, flagNames, tflagNames, cflagNames)
    }

    private fun loadNameMapFromAssets(
        assetManager: android.content.res.AssetManager,
        path: String
    ): Map<Int, String> {
        return try {
            assetManager.open(path).use { CsvLoader.loadNameCsv(it, csvCharset) }
        } catch (e: Exception) { emptyMap() }
    }

    private fun loadCsvFromAssets(
        assetManager: android.content.res.AssetManager,
        path: String,
        block: (InputStream) -> Unit
    ) {
        try { assetManager.open(path).use { block(it) } }
        catch (e: Exception) { /* file not found */ }
    }

    /**
     * Load from external storage (user-provided game folder)
     */
    fun loadFromDirectory(
        dir: java.io.File,
        scope: VariableScope,
        charaManager: CharaManager
    ): CsvNameTable {
        val csvDir = java.io.File(dir, "CSV")
        if (!csvDir.exists()) return CsvNameTable()

        // 대소문자 무시로 파일 찾기 (Linux/Android는 대소문자 구분하므로 필수)
        fun findFile(name: String): java.io.File? {
            val exact = java.io.File(csvDir, name)
            if (exact.exists()) return exact
            return csvDir.listFiles()?.firstOrNull { it.name.uppercase() == name.uppercase() }
        }

        fun loadMap(name: String): Map<Int, String> {
            val file = findFile(name) ?: return emptyMap()
            return file.inputStream().use { CsvLoader.loadNameCsv(it, csvCharset) }
        }

        // Load chara CSVs - CSV/CHARA/ 서브폴더 재귀 탐색
        fun scanCharaFiles(dir: java.io.File) {
            dir.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    scanCharaFiles(file)
                } else if (file.name.uppercase().startsWith("CHARA") && file.name.uppercase().endsWith(".CSV")) {
                    file.inputStream().use { CsvLoader.loadCharaCsv(it, charaManager, csvCharset) }
                }
            }
        }
        scanCharaFiles(csvDir)


        // _Gamebase.csv 로딩 - 여러 파일명 시도 (대소문자 무시)
        listOf("_Gamebase.csv", "Gamebase.csv", "GAMEBASE.CSV", "_GAMEBASE.CSV").forEach { fileName ->
            findFile(fileName)?.let { file ->
                file.inputStream().use { stream ->
                    val records = CsvLoader.parseCsvFile(stream, csvCharset)
                    for (record in records) {
                        when (record.name.trim()) {
                            "GAMEBASE_TITLE", "タイトル" -> scope.getOrCreate("GAMEBASE_TITLE", true, listOf(1)).set(listOf(0), com.eraandroid.core.vm.EraValue.of(record.value))
                            "GAMEBASE_AUTHOR", "作者" -> scope.getOrCreate("GAMEBASE_AUTHOR", true, listOf(1)).set(listOf(0), com.eraandroid.core.vm.EraValue.of(record.value))
                            "GAMEBASE_YEAR", "製作年" -> scope.getOrCreate("GAMEBASE_YEAR", true, listOf(1)).set(listOf(0), com.eraandroid.core.vm.EraValue.of(record.value))
                            "GAMEBASE_INFO", "追加情報" -> scope.getOrCreate("GAMEBASE_INFO", true, listOf(1)).set(listOf(0), com.eraandroid.core.vm.EraValue.of(record.value))
                            "GAMEBASE_VERSION", "バージョン" -> scope.getOrCreate("GAMEBASE_VERSION", false, listOf(1)).set(listOf(0), com.eraandroid.core.vm.EraValue.of(record.value.trim().toLongOrNull() ?: 0L))
                            "コード" -> scope.getOrCreate("GAMEBASE_CODE", false, listOf(1)).set(listOf(0), com.eraandroid.core.vm.EraValue.of(record.value.trim().toLongOrNull() ?: 0L))
                            "バージョン違い認める" -> scope.getOrCreate("GAMEBASE_ALLOWVERSION", false, listOf(1)).set(listOf(0), com.eraandroid.core.vm.EraValue.of(record.value.trim().toLongOrNull() ?: 0L))
                        }
                    }
                }
            }
        }

        // Load items
        findFile("ITEM.CSV")?.let {
            it.inputStream().use { stream -> CsvLoader.loadItemCsv(stream, scope, csvCharset) }
        }

        // 이름 테이블을 scope에 저장 (ERB에서 ABLNAME:N, PALAMNAME:N 등으로 참조)
        fun saveNameTable(csvName: String, scopeVarName: String, maxSize: Int = 1000) {
            val map = loadMap(csvName)
            if (map.isEmpty()) return
            val v = scope.getOrCreate(scopeVarName, true, listOf(maxSize))
            map.forEach { (idx, name) -> if (idx < maxSize) v.set(listOf(idx), com.eraandroid.core.vm.EraValue.of(name)) }
        }
        saveNameTable("ABL.CSV",    "ABLNAME")
        saveNameTable("TALENT.CSV", "TALENTNAME")
        saveNameTable("EXP.CSV",    "EXPNAME")
        saveNameTable("MARK.CSV",   "MARKNAME")
        saveNameTable("PALAM.CSV",  "PALAMNAME")
        saveNameTable("TRAIN.CSV",  "TRAINNAME")
        saveNameTable("ITEM.CSV",   "ITEMNAME")
        saveNameTable("FLAG.CSV",   "FLAGNAME")
        saveNameTable("TFLAG.CSV",  "TFLAGNAME")
        saveNameTable("CFLAG.CSV",  "CFLAGNAME")
        saveNameTable("BASE.CSV",   "BASENAME")
        saveNameTable("STAIN.CSV",  "STAINNAME")
        saveNameTable("SOURCE.CSV", "SOURCENAME")
        saveNameTable("EX.CSV",     "EXNAME")
        saveNameTable("TEQUIP.CSV", "TEQUIPNAME")
        saveNameTable("EQUIP.CSV",  "EQUIPNAME")
        saveNameTable("NOWEX.CSV",  "NOWEXNAME")

        // PALAMLV / EXPLV 레벨 임계값 테이블 (숫자 배열로 scope에 저장)
        fun loadLvTable(vararg names: String, scopeVar: String) {
            for (csvName in names) {
                val f = findFile(csvName) ?: continue
                val v = scope.getOrCreate(scopeVar, false, listOf(100))
                f.inputStream().use { s ->
                    val recs = CsvLoader.parseCsvFile(s, csvCharset)
                    for (r in recs) {
                        // 2컬럼(index,threshold) 또는 3컬럼(index,name,threshold) 지원
                        val threshold = when {
                            r.value.trim().isNotEmpty() -> r.value.trim().toLongOrNull()
                            else -> r.name.trim().toLongOrNull()
                        } ?: continue
                        if (r.index < 100) v.set(listOf(r.index), com.eraandroid.core.vm.EraValue.of(threshold))
                    }
                }
                return
            }
        }
        loadLvTable("PalamLv.csv","PALAMLV.CSV","palamlv.csv", scopeVar = "PALAMLV")
        loadLvTable("ExpLv.csv","EXPLV.CSV","explv.csv",       scopeVar = "EXPLV")

        // STR.CSV → STR 변수에 문자열 데이터로 직접 저장 (목욕탕 이름 등)
        run {
            val strCsv = findFile("Str.CSV") ?: findFile("STR.CSV")
            strCsv?.inputStream()?.use { stream ->
                val strVar = scope.getOrCreate("STR", true, listOf(1000))
                val records = CsvLoader.parseCsvFile(stream, csvCharset)
                for (rec in records) {
                    if (rec.index < 1000) {
                        strVar.set(listOf(rec.index), com.eraandroid.core.vm.EraValue.of(rec.name))
                    }
                }
            }
        }

        return CsvNameTable(
            ablNames = loadMap("ABL.CSV"),
            talentNames = loadMap("TALENT.CSV"),
            expNames = loadMap("EXP.CSV"),
            markNames = loadMap("MARK.CSV"),
            palamNames = loadMap("PALAM.CSV"),
            trainNames = loadMap("TRAIN.CSV"),
            itemNames = loadMap("ITEM.CSV"),
            comNames = loadMap("COM.CSV"),
            flagNames = loadMap("FLAG.CSV"),
            tflagNames = loadMap("TFLAG.CSV"),
            cflagNames = loadMap("CFLAG.CSV"),
        )
    }

    fun loadFromDocumentFile(
        csvDir: androidx.documentfile.provider.DocumentFile,
        scope: VariableScope,
        charaManager: CharaManager,
        context: android.content.Context
    ) {
        // CSV 폴더 내 파일 목록 디버그
        android.util.Log.d("CsvLoader", "CSV 폴더: ${csvDir.name}, 파일 수: ${csvDir.listFiles().size}")
        csvDir.listFiles().forEach { android.util.Log.d("CsvLoader", "  파일: ${it.name}") }

        fun readDoc(name: String): java.io.InputStream? {
            return csvDir.listFiles()
                .firstOrNull { it.name?.uppercase() == name.uppercase() }
                ?.uri
                ?.let { context.contentResolver.openInputStream(it) }
        }

        fun loadMapFromDoc(name: String): Map<Int, String> {
            return readDoc(name)?.use { CsvLoader.loadNameCsv(it, csvCharset) } ?: emptyMap()
        }

        // 아이템 로딩
        readDoc("ITEM.CSV")?.use { CsvLoader.loadItemCsv(it, scope, csvCharset) }

        // 캐릭터 로딩 - CHARA 서브폴더 재귀 탐색
        fun scanDocCharaFiles(dir: androidx.documentfile.provider.DocumentFile) {
            dir.listFiles().forEach { doc ->
                when {
                    doc.isDirectory -> scanDocCharaFiles(doc)
                    doc.name?.uppercase()?.startsWith("CHARA") == true &&
                            doc.name?.uppercase()?.endsWith(".CSV") == true -> {
                        context.contentResolver.openInputStream(doc.uri)?.use {
                            CsvLoader.loadCharaCsv(it, charaManager, csvCharset)
                        }
                    }
                }
            }
        }
        scanDocCharaFiles(csvDir)

        // 이름 테이블을 scope에 저장
        fun saveNameTableDoc(name: String, scopeVarName: String, maxSize: Int = 1000) {
            val map = loadMapFromDoc(name)
            if (map.isEmpty()) return
            val v = scope.getOrCreate(scopeVarName, true, listOf(maxSize))
            map.forEach { (idx, n) -> if (idx < maxSize) v.set(listOf(idx), com.eraandroid.core.vm.EraValue.of(n)) }
        }
        saveNameTableDoc("ABL.CSV",    "ABLNAME")
        saveNameTableDoc("TALENT.CSV", "TALENTNAME")
        saveNameTableDoc("EXP.CSV",    "EXPNAME")
        saveNameTableDoc("MARK.CSV",   "MARKNAME")
        saveNameTableDoc("PALAM.CSV",  "PALAMNAME")
        saveNameTableDoc("TRAIN.CSV",  "TRAINNAME")
        saveNameTableDoc("ITEM.CSV",   "ITEMNAME")
        saveNameTableDoc("FLAG.CSV",   "FLAGNAME")
        saveNameTableDoc("TFLAG.CSV",  "TFLAGNAME")
        saveNameTableDoc("CFLAG.CSV",  "CFLAGNAME")
        saveNameTableDoc("BASE.CSV",   "BASENAME")
        saveNameTableDoc("STAIN.CSV",  "STAINNAME")
        saveNameTableDoc("SOURCE.CSV", "SOURCENAME")
        saveNameTableDoc("EX.CSV",     "EXNAME")
        saveNameTableDoc("TEQUIP.CSV", "TEQUIPNAME")
        saveNameTableDoc("EQUIP.CSV",  "EQUIPNAME")
        saveNameTableDoc("NOWEX.CSV",  "NOWEXNAME")

        // STR.CSV → STR 변수에 문자열 데이터로 직접 저장
        run {
            val strDoc = csvDir.listFiles().firstOrNull {
                it.name?.uppercase() == "STR.CSV"
            }
            strDoc?.let { doc ->
                context.contentResolver.openInputStream(doc.uri)?.use { stream ->
                    val strVar = scope.getOrCreate("STR", true, listOf(1000))
                    val records = CsvLoader.parseCsvFile(stream, csvCharset)
                    for (rec in records) {
                        if (rec.index < 1000) {
                            strVar.set(listOf(rec.index), com.eraandroid.core.vm.EraValue.of(rec.name))
                        }
                    }
                }
            }
        }

        // Gamebase 로딩
        listOf("_Gamebase.csv", "Gamebase.csv", "GAMEBASE.CSV", "_GAMEBASE.CSV").forEach { fileName ->
            val found = readDoc(fileName)
            android.util.Log.d("CsvLoader", "Gamebase 검색: $fileName -> ${if (found != null) "찾음" else "없음"}")
            found?.use { stream ->
                val records = CsvLoader.parseCsvFile(stream, csvCharset)
                android.util.Log.d("CsvLoader", "Gamebase 레코드 수: ${records.size}")
                records.forEach { r -> android.util.Log.d("CsvLoader", "  레코드: name='${r.name}' value='${r.value}'") }
                for (record in records) {
                    when (record.name.trim()) {
                        "GAMEBASE_TITLE", "タイトル" -> scope.getOrCreate("GAMEBASE_TITLE", true, listOf(1)).set(listOf(0), com.eraandroid.core.vm.EraValue.of(record.value))
                        "GAMEBASE_AUTHOR", "作者" -> scope.getOrCreate("GAMEBASE_AUTHOR", true, listOf(1)).set(listOf(0), com.eraandroid.core.vm.EraValue.of(record.value))
                        "GAMEBASE_YEAR", "製作年" -> scope.getOrCreate("GAMEBASE_YEAR", true, listOf(1)).set(listOf(0), com.eraandroid.core.vm.EraValue.of(record.value))
                        "GAMEBASE_INFO", "追加情報" -> scope.getOrCreate("GAMEBASE_INFO", true, listOf(1)).set(listOf(0), com.eraandroid.core.vm.EraValue.of(record.value))
                        "GAMEBASE_VERSION", "バージョン" -> scope.getOrCreate("GAMEBASE_VERSION", false, listOf(1)).set(listOf(0), com.eraandroid.core.vm.EraValue.of(record.value.trim().toLongOrNull() ?: 0L))
                        "コード" -> scope.getOrCreate("GAMEBASE_CODE", false, listOf(1)).set(listOf(0), com.eraandroid.core.vm.EraValue.of(record.value.trim().toLongOrNull() ?: 0L))
                        "バージョン違い認める" -> scope.getOrCreate("GAMEBASE_ALLOWVERSION", false, listOf(1)).set(listOf(0), com.eraandroid.core.vm.EraValue.of(record.value.trim().toLongOrNull() ?: 0L))
                    }
                }
            }
        }
    }

    /**
     * Collect all ERB script files from a directory
     */
    fun collectErbFiles(dir: java.io.File): List<java.io.File> {
        val erbDir = java.io.File(dir, "ERB")
        if (!erbDir.exists()) return emptyList()
        return erbDir.walkTopDown()
            .filter { it.isFile && it.extension.uppercase() == "ERB" }
            .toList()
            .sortedBy { it.name.uppercase() }
    }
}