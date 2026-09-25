package com.eraandroid.emuera.config

import com.eraandroid.emuera.Program
import java.io.File
import java.nio.charset.Charset

/** 設定値 (Config.cs) */
object Config {
    /** Windows の Shift-JIS (CP932)。無ければ標準の Shift_JIS */
    @JvmField val SJIS: Charset = try { Charset.forName("windows-31j") } catch (e: Exception) { Charset.forName("Shift_JIS") }
    @JvmField var Encode: Charset = SJIS
    @JvmField var SaveEncode: Charset = SJIS
    /** Encoding used for byte-length string functions (STRLENS etc.) — LangManager */
    @JvmField var LangEncode: Charset = SJIS

    private var nameDic: Map<ConfigCode, String> = emptyMap()
    fun getConfigName(code: ConfigCode): String = nameDic[code] ?: code.name

    var ICFunction = true; private set
    var ICVariable = true; private set
    var UseRenameFile = false; private set
    var UseReplaceFile = true; private set
    var UseMouse = true; private set
    var UseMenu = true; private set
    var UseDebugCommand = false; private set
    var AutoSave = true; private set
    var UseKeyMacro = true; private set
    var WindowX = 760; private set
    var DrawableWidth = 760; private set
    var WindowY = 480; private set
    var MaxLog = 5000; private set
    var PrintCPerLine = 3; private set
    var PrintCLength = 25; private set
    var ForeColor = EraColor.fromArgb(192, 192, 192); private set
    var BackColor = EraColor.fromArgb(0, 0, 0); private set
    var FocusColor = EraColor.fromArgb(255, 255, 0); private set
    var LogColor = EraColor.fromArgb(192, 192, 192); private set
    var FontSize = 18; private set
    var FontName = "ＭＳ ゴシック"; private set
    var LineHeight = 19; private set
    var FPS = 5; private set
    var ScrollHeight = 1; private set
    var InfiniteLoopAlertTime = 5000; private set
    var SaveDataNos = 20; private set
    var WarnBackCompatibility = true; private set
    var WarnNormalFunctionOverloading = false; private set
    var SearchSubdirectory = false; private set
    var SortWithFilename = false; private set
    var AllowFunctionOverloading = true; private set
    var WarnFunctionOverloading = true; private set
    var DisplayWarningLevel = 1; private set
    var DisplayReport = false; private set
    var ReduceArgumentOnLoad = ReduceArgumentOnLoadFlag.NO; private set
    var IgnoreUncalledFunction = true; private set
    var FunctionNotFoundWarning = DisplayWarningFlag.IGNORE; private set
    var FunctionNotCalledWarning = DisplayWarningFlag.IGNORE; private set
    var ChangeMasterNameIfDebug = true; private set
    var LastKey = 0L; private set
    var ButtonWrap = false; private set
    var CompatiErrorLine = false; private set
    var CompatiCALLNAME = false; private set
    var UseSaveFolder = false; private set
    var CompatiRAND = false; private set
    var CompatiLinefeedAs1739 = false; private set
    var SystemAllowFullSpace = true; private set
    var SystemSaveInUTF8 = false; private set
    var SystemSaveInBinary = false; private set
    var CompatiFuncArgAutoConvert = false; private set
    var CompatiFuncArgOptional = false; private set
    var CompatiCallEvent = false; private set
    var CompatiSPChara = false; private set
    var SystemIgnoreTripleSymbol = false; private set
    var SystemNoTarget = false; private set
    var SystemIgnoreStringSet = false; private set
    var Language = 0x0411; private set
    var SavDir = ""; private set
    var ForceSavDir = ""; private set
    var NeedReduceArgumentOnLoad = false; private set
    var AllowLongInputByMouse = false; private set
    var TimesNotRigorousCalculation = false; private set
    var DrawingParam_ShapePositionShift = 0; private set

    fun setConfig(instance: ConfigData) {
        nameDic = instance.getConfigNameDic()
        val ignoreCase = instance.getConfigValue<Boolean>(ConfigCode.IgnoreCase)
        val compatiFunctionNoignoreCase = instance.getConfigValue<Boolean>(ConfigCode.CompatiFunctionNoignoreCase)
        ICFunction = ignoreCase && !compatiFunctionNoignoreCase
        ICVariable = ignoreCase
        UseRenameFile = instance.getConfigValue(ConfigCode.UseRenameFile)
        UseReplaceFile = instance.getConfigValue(ConfigCode.UseReplaceFile)
        UseMouse = instance.getConfigValue(ConfigCode.UseMouse)
        UseMenu = instance.getConfigValue(ConfigCode.UseMenu)
        UseDebugCommand = instance.getConfigValue(ConfigCode.UseDebugCommand)
        AutoSave = instance.getConfigValue(ConfigCode.AutoSave)
        UseKeyMacro = instance.getConfigValue(ConfigCode.UseKeyMacro)
        WindowX = instance.getConfigValue(ConfigCode.WindowX)
        WindowY = instance.getConfigValue(ConfigCode.WindowY)
        MaxLog = instance.getConfigValue(ConfigCode.MaxLog)
        PrintCPerLine = instance.getConfigValue(ConfigCode.PrintCPerLine)
        PrintCLength = instance.getConfigValue(ConfigCode.PrintCLength)
        ForeColor = instance.getConfigValue(ConfigCode.ForeColor)
        BackColor = instance.getConfigValue(ConfigCode.BackColor)
        FocusColor = instance.getConfigValue(ConfigCode.FocusColor)
        LogColor = instance.getConfigValue(ConfigCode.LogColor)
        FontSize = instance.getConfigValue(ConfigCode.FontSize)
        FontName = instance.getConfigValue(ConfigCode.FontName)
        LineHeight = instance.getConfigValue(ConfigCode.LineHeight)
        FPS = instance.getConfigValue(ConfigCode.FPS)
        ScrollHeight = instance.getConfigValue(ConfigCode.ScrollHeight)
        InfiniteLoopAlertTime = instance.getConfigValue(ConfigCode.InfiniteLoopAlertTime)
        SaveDataNos = instance.getConfigValue(ConfigCode.SaveDataNos)
        WarnBackCompatibility = instance.getConfigValue(ConfigCode.WarnBackCompatibility)
        WarnNormalFunctionOverloading = instance.getConfigValue(ConfigCode.WarnNormalFunctionOverloading)
        SearchSubdirectory = instance.getConfigValue(ConfigCode.SearchSubdirectory)
        SortWithFilename = instance.getConfigValue(ConfigCode.SortWithFilename)
        AllowFunctionOverloading = instance.getConfigValue(ConfigCode.AllowFunctionOverloading)
        WarnFunctionOverloading = if (!AllowFunctionOverloading) true else instance.getConfigValue(ConfigCode.WarnFunctionOverloading)
        DisplayWarningLevel = instance.getConfigValue(ConfigCode.DisplayWarningLevel)
        DisplayReport = instance.getConfigValue(ConfigCode.DisplayReport)
        ReduceArgumentOnLoad = instance.getConfigValue(ConfigCode.ReduceArgumentOnLoad)
        IgnoreUncalledFunction = instance.getConfigValue(ConfigCode.IgnoreUncalledFunction)
        FunctionNotFoundWarning = instance.getConfigValue(ConfigCode.FunctionNotFoundWarning)
        FunctionNotCalledWarning = instance.getConfigValue(ConfigCode.FunctionNotCalledWarning)
        ChangeMasterNameIfDebug = instance.getConfigValue(ConfigCode.ChangeMasterNameIfDebug)
        LastKey = instance.getConfigValue(ConfigCode.LastKey)
        ButtonWrap = instance.getConfigValue(ConfigCode.ButtonWrap)
        CompatiErrorLine = instance.getConfigValue(ConfigCode.CompatiErrorLine)
        CompatiCALLNAME = instance.getConfigValue(ConfigCode.CompatiCALLNAME)
        UseSaveFolder = instance.getConfigValue(ConfigCode.UseSaveFolder)
        CompatiRAND = instance.getConfigValue(ConfigCode.CompatiRAND)
        CompatiLinefeedAs1739 = instance.getConfigValue(ConfigCode.CompatiLinefeedAs1739)
        SystemAllowFullSpace = instance.getConfigValue(ConfigCode.SystemAllowFullSpace)
        SystemSaveInUTF8 = instance.getConfigValue(ConfigCode.SystemSaveInUTF8)
        SaveEncode = if (SystemSaveInUTF8) Charsets.UTF_8 else SJIS
        SystemSaveInBinary = instance.getConfigValue(ConfigCode.SystemSaveInBinary)
        SystemIgnoreTripleSymbol = instance.getConfigValue(ConfigCode.SystemIgnoreTripleSymbol)
        SystemIgnoreStringSet = instance.getConfigValue(ConfigCode.SystemIgnoreStringSet)
        CompatiFuncArgAutoConvert = instance.getConfigValue(ConfigCode.CompatiFuncArgAutoConvert)
        CompatiFuncArgOptional = instance.getConfigValue(ConfigCode.CompatiFuncArgOptional)
        CompatiCallEvent = instance.getConfigValue(ConfigCode.CompatiCallEvent)
        CompatiSPChara = instance.getConfigValue(ConfigCode.CompatiSPChara)
        AllowLongInputByMouse = instance.getConfigValue(ConfigCode.AllowLongInputByMouse)
        TimesNotRigorousCalculation = instance.getConfigValue(ConfigCode.TimesNotRigorousCalculation)
        SystemNoTarget = instance.getConfigValue(ConfigCode.SystemNoTarget)

        when (instance.getConfigValue<UseLanguage>(ConfigCode.useLanguage)) {
            UseLanguage.JAPANESE -> { Language = 0x0411; LangEncode = SJIS }
            UseLanguage.KOREAN -> { Language = 0x0412; LangEncode = charset("x-windows-949", "EUC-KR") }
            UseLanguage.CHINESE_HANS -> { Language = 0x0804; LangEncode = charset("GBK", "GB2312") }
            UseLanguage.CHINESE_HANT -> { Language = 0x0404; LangEncode = charset("Big5", "Big5") }
        }

        if (FontSize < 8) FontSize = 8
        if (LineHeight < FontSize) LineHeight = FontSize
        if (SaveDataNos < 20) SaveDataNos = 20
        if (SaveDataNos > 80) SaveDataNos = 80
        if (MaxLog < 500) MaxLog = 500

        DrawingParam_ShapePositionShift = maxOf(2, FontSize / 6)
        applyScreenOverride()
        ForceSavDir = Program.ExeDir + "sav/"
        SavDir = if (UseSaveFolder) Program.ExeDir + "sav/" else Program.ExeDir
        if (UseSaveFolder) File(SavDir).mkdirs()

        // ReduceArgumentOnLoad
        when (ReduceArgumentOnLoad) {
            ReduceArgumentOnLoadFlag.YES -> NeedReduceArgumentOnLoad = true
            ReduceArgumentOnLoadFlag.NO -> NeedReduceArgumentOnLoad = false
            ReduceArgumentOnLoadFlag.ONCE -> NeedReduceArgumentOnLoad = true
        }
    }

    private fun charset(name: String, fallback: String): Charset =
        try { Charset.forName(name) } catch (e: Exception) { try { Charset.forName(fallback) } catch (e2: Exception) { Charsets.UTF_8 } }

    // ── Android 版: 画面幅に合わせた上書き (UI の文字数に合わせる) ──
    private var overrideCols = 0
    private var overridePrintCPerLine = 0
    private var overridePrintCLength = 0

    /** cols: 1行に入る半角文字数。0 で上書きなし */
    fun setScreenOverride(cols: Int, printCPerLine: Int, printCLength: Int) {
        overrideCols = cols
        overridePrintCPerLine = printCPerLine
        overridePrintCLength = printCLength
        applyScreenOverride()
    }

    private fun applyScreenOverride() {
        if (overrideCols > 0) WindowX = overrideCols * FontSize / 2 + DrawingParam_ShapePositionShift
        if (overridePrintCPerLine > 0) PrintCPerLine = overridePrintCPerLine
        if (overridePrintCLength > 0) PrintCLength = overridePrintCLength
        DrawableWidth = WindowX - DrawingParam_ShapePositionShift
    }

    fun forceCreateSavDir() { File(ForceSavDir).mkdirs() }
    fun createSavDir() { if (UseSaveFolder) File(SavDir).mkdirs() }

    // replace
    var MoneyLabel = "$"; private set
    var MoneyFirst = true; private set
    var LoadLabel = "Now Loading..."; private set
    var MaxShopItem = 100; private set
    var DrawLineString = "-"; private set
    var BarChar1 = '*'; private set
    var BarChar2 = '.'; private set
    var TitleMenuString0 = "最初からはじめる"; private set
    var TitleMenuString1 = "ロードしてはじめる"; private set
    var ComAbleDefault = 1; private set
    var StainDefault: List<Long> = listOf(0, 0, 2, 1, 8); private set
    var TimeupLabel = "時間切れ"; private set
    var ExpLvDef: List<Long> = listOf(0, 1, 4, 20, 50, 200); private set
    var PalamLvDef: List<Long> = listOf(0, 100, 500, 3000, 10000, 30000, 60000, 100000, 150000, 250000); private set
    var PbandDef = 4L; private set
    var RelationDef = 0L; private set

    fun setReplace(instance: ConfigData) {
        MoneyLabel = instance.getConfigValue(ConfigCode.MoneyLabel)
        MoneyFirst = instance.getConfigValue(ConfigCode.MoneyFirst)
        LoadLabel = instance.getConfigValue(ConfigCode.LoadLabel)
        MaxShopItem = instance.getConfigValue(ConfigCode.MaxShopItem)
        DrawLineString = instance.getConfigValue(ConfigCode.DrawLineString)
        if (DrawLineString.isEmpty()) DrawLineString = "-"
        BarChar1 = instance.getConfigValue(ConfigCode.BarChar1)
        BarChar2 = instance.getConfigValue(ConfigCode.BarChar2)
        TitleMenuString0 = instance.getConfigValue(ConfigCode.TitleMenuString0)
        TitleMenuString1 = instance.getConfigValue(ConfigCode.TitleMenuString1)
        ComAbleDefault = instance.getConfigValue(ConfigCode.ComAbleDefault)
        StainDefault = instance.getConfigValue(ConfigCode.StainDefault)
        TimeupLabel = instance.getConfigValue(ConfigCode.TimeupLabel)
        ExpLvDef = instance.getConfigValue(ConfigCode.ExpLvDef)
        PalamLvDef = instance.getConfigValue(ConfigCode.PalamLvDef)
        PbandDef = instance.getConfigValue(ConfigCode.pbandDef)
        RelationDef = instance.getConfigValue(ConfigCode.RelationDef)
    }

    /** KeyValuePair<相対パス, 完全パス>のリストを返す。 */
    fun getFiles(rootdir: String, pattern: String): List<Pair<String, String>> =
        getFiles(File(rootdir), File(rootdir), pattern, !SearchSubdirectory, SortWithFilename)

    private fun matches(name: String, pattern: String): Boolean {
        // pattern like "*.ERB" / "*.CSV" / "*.ERH" — case-insensitive extension match
        if (pattern.startsWith("*.")) {
            val ext = pattern.substring(1)
            return name.endsWith(ext, ignoreCase = true)
        }
        val sb = StringBuilder("^")
        for (ch in pattern) when (ch) {
            '*' -> sb.append(".*")
            '?' -> sb.append('.')
            else -> sb.append(Regex.escape(ch.toString()))
        }
        sb.append('$')
        val regex = Regex(sb.toString(), RegexOption.IGNORE_CASE)
        return regex.matches(name)
    }

    private fun getFiles(dir: File, rootdir: File, pattern: String, toponly: Boolean, sort: Boolean): List<Pair<String, String>> {
        val retList = ArrayList<Pair<String, String>>()
        if (!toponly) {
            var dirList = dir.listFiles { f -> f.isDirectory }?.toList() ?: emptyList()
            if (sort) dirList = dirList.sortedWith { a, b -> a.path.compareTo(b.path, ignoreCase = true) }
            for (d in dirList) retList.addAll(getFiles(d, rootdir, pattern, toponly, sort))
        }
        var relative = ""
        if (dir.absolutePath != rootdir.absolutePath) {
            val rp = rootdir.absolutePath
            relative = if (dir.absolutePath.startsWith(rp)) dir.absolutePath.substring(rp.length).trimStart('/', '\\') else dir.absolutePath
            if (!relative.endsWith("\\") && !relative.endsWith("/")) relative += "\\"
        }
        var files = dir.listFiles { f -> f.isFile && matches(f.name, pattern) }?.toList() ?: emptyList()
        // Windows' Directory.GetFiles returns entries in NTFS order (≈ case-insensitive alphabetical)
        files = files.sortedWith { a, b -> a.name.compareTo(b.name, ignoreCase = true) }
        for (f in files) {
            val ext = f.extension
            if (ext.length + 1 <= 4) retList.add(Pair(relative + f.name, f.path))
        }
        return retList
    }
}
