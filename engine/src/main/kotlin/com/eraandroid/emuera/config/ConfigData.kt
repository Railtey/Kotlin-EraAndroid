package com.eraandroid.emuera.config

import com.eraandroid.emuera.Program
import com.eraandroid.emuera.gamedata.ParserMediator
import com.eraandroid.emuera.gamedata.expression.SingleTerm
import com.eraandroid.emuera.sub.EmueraException
import com.eraandroid.emuera.sub.EraStreamReader
import com.eraandroid.emuera.sub.ScriptPosition
import com.eraandroid.emuera.sub.FileUtil

/** プログラム全体で使用される値 */
class ConfigData private constructor() {
    private val configArray = ArrayList<AConfigItem>()
    private val replaceArray = ArrayList<AConfigItem>()
    private val debugArray = ArrayList<AConfigItem>()

    init { setDefault() }

    private fun b(code: ConfigCode, text: String, v: Boolean) = ConfigItem(code, text, v, ConfigItem.Kind.BOOL)
    private fun i(code: ConfigCode, text: String, v: Int) = ConfigItem(code, text, v, ConfigItem.Kind.INT)
    private fun l(code: ConfigCode, text: String, v: Long) = ConfigItem(code, text, v, ConfigItem.Kind.LONG)
    private fun s(code: ConfigCode, text: String, v: String) = ConfigItem(code, text, v, ConfigItem.Kind.STRING)
    private fun c(code: ConfigCode, text: String, v: Char) = ConfigItem(code, text, v, ConfigItem.Kind.CHAR)
    private fun col(code: ConfigCode, text: String, r: Int, g: Int, bb: Int) = ConfigItem(code, text, EraColor.fromArgb(r, g, bb), ConfigItem.Kind.COLOR)
    private fun <E : Enum<E>> e(code: ConfigCode, text: String, v: E) = ConfigItem<Any>(code, text, v, ConfigItem.Kind.ENUM)
    private fun ll(code: ConfigCode, text: String, v: List<Long>) = ConfigItem<Any>(code, text, ArrayList(v), ConfigItem.Kind.LONG_LIST)

    private fun setDefault() {
        val a = configArray
        a += b(ConfigCode.IgnoreCase, "大文字小文字の違いを無視する", true)
        a += b(ConfigCode.UseRenameFile, "_Rename.csvを利用する", false)
        a += b(ConfigCode.UseReplaceFile, "_Replace.csvを利用する", true)
        a += b(ConfigCode.UseMouse, "マウスを使用する", true)
        a += b(ConfigCode.UseMenu, "メニューを使用する", true)
        a += b(ConfigCode.UseDebugCommand, "デバッグコマンドを使用する", false)
        a += b(ConfigCode.AllowMultipleInstances, "多重起動を許可する", true)
        a += b(ConfigCode.AutoSave, "オートセーブを行なう", true)
        a += b(ConfigCode.UseKeyMacro, "キーボードマクロを使用する", true)
        a += b(ConfigCode.SizableWindow, "ウィンドウの高さを可変にする", true)
        a += e(ConfigCode.TextDrawingMode, "描画インターフェース", TextDrawingMode.TEXTRENDERER)
        a += i(ConfigCode.WindowX, "ウィンドウ幅", 760)
        a += i(ConfigCode.WindowY, "ウィンドウ高さ", 480)
        a += i(ConfigCode.WindowPosX, "ウィンドウ位置X", 0)
        a += i(ConfigCode.WindowPosY, "ウィンドウ位置Y", 0)
        a += b(ConfigCode.SetWindowPos, "起動時のウィンドウ位置を指定する", false)
        a += b(ConfigCode.WindowMaximixed, "起動時にウィンドウを最大化する", false)
        a += i(ConfigCode.MaxLog, "履歴ログの行数", 5000)
        a += i(ConfigCode.PrintCPerLine, "PRINTCを並べる数", 3)
        a += i(ConfigCode.PrintCLength, "PRINTCの文字数", 25)
        a += s(ConfigCode.FontName, "フォント名", "ＭＳ ゴシック")
        a += i(ConfigCode.FontSize, "フォントサイズ", 18)
        a += i(ConfigCode.LineHeight, "一行の高さ", 19)
        a += col(ConfigCode.ForeColor, "文字色", 192, 192, 192)
        a += col(ConfigCode.BackColor, "背景色", 0, 0, 0)
        a += col(ConfigCode.FocusColor, "選択中文字色", 255, 255, 0)
        a += col(ConfigCode.LogColor, "履歴文字色", 192, 192, 192)
        a += i(ConfigCode.FPS, "フレーム毎秒", 5)
        a += i(ConfigCode.SkipFrame, "最大スキップフレーム数", 3)
        a += i(ConfigCode.ScrollHeight, "スクロール行数", 1)
        a += i(ConfigCode.InfiniteLoopAlertTime, "無限ループ警告までのミリ秒数", 5000)
        a += i(ConfigCode.DisplayWarningLevel, "表示する最低警告レベル", 1)
        a += b(ConfigCode.DisplayReport, "ロード時にレポートを表示する", false)
        a += e(ConfigCode.ReduceArgumentOnLoad, "ロード時に引数を解析する", ReduceArgumentOnLoadFlag.NO)
        a += b(ConfigCode.IgnoreUncalledFunction, "呼び出されなかった関数を無視する", true)
        a += e(ConfigCode.FunctionNotFoundWarning, "関数が見つからない警告の扱い", DisplayWarningFlag.IGNORE)
        a += e(ConfigCode.FunctionNotCalledWarning, "関数が呼び出されなかった警告の扱い", DisplayWarningFlag.IGNORE)
        a += b(ConfigCode.ChangeMasterNameIfDebug, "デバッグコマンドを使用した時にMASTERの名前を変更する", true)
        a += b(ConfigCode.ButtonWrap, "ボタンの途中で行を折りかえさない", false)
        a += b(ConfigCode.SearchSubdirectory, "サブディレクトリを検索する", false)
        a += b(ConfigCode.SortWithFilename, "読み込み順をファイル名順にソートする", false)
        a += l(ConfigCode.LastKey, "最終更新コード", 0)
        a += i(ConfigCode.SaveDataNos, "表示するセーブデータ数", 20)
        a += b(ConfigCode.WarnBackCompatibility, "eramaker互換性に関する警告を表示する", true)
        a += b(ConfigCode.AllowFunctionOverloading, "システム関数の上書きを許可する", true)
        a += b(ConfigCode.WarnFunctionOverloading, "システム関数が上書きされたとき警告を表示する", true)
        a += s(ConfigCode.TextEditor, "関連づけるテキストエディタ", "notepad")
        a += e(ConfigCode.EditorType, "テキストエディタコマンドライン指定", TextEditorType.USER_SETTING)
        a += s(ConfigCode.EditorArgument, "エディタに渡す行指定引数", "")
        a += b(ConfigCode.WarnNormalFunctionOverloading, "同名の非イベント関数が複数定義されたとき警告する", false)
        a += b(ConfigCode.CompatiErrorLine, "解釈不可能な行があっても実行する", false)
        a += b(ConfigCode.CompatiCALLNAME, "CALLNAMEが空文字列の時にNAMEを代入する", false)
        a += b(ConfigCode.UseSaveFolder, "セーブデータをsavフォルダ内に作成する", false)
        a += b(ConfigCode.CompatiRAND, "擬似変数RANDの仕様をeramakerに合わせる", false)
        a += b(ConfigCode.CompatiDRAWLINE, "DRAWLINEを常に新しい行で行う", false)
        a += b(ConfigCode.CompatiFunctionNoignoreCase, "関数・属性については大文字小文字を無視しない", false)
        a += b(ConfigCode.SystemAllowFullSpace, "全角スペースをホワイトスペースに含める", true)
        a += b(ConfigCode.SystemSaveInUTF8, "セーブデータをUTF-8で保存する", false)
        a += b(ConfigCode.CompatiLinefeedAs1739, "ver1739以前の非ボタン折り返しを再現する", false)
        a += e(ConfigCode.useLanguage, "内部で使用する東アジア言語", UseLanguage.JAPANESE)
        a += b(ConfigCode.AllowLongInputByMouse, "ONEINPUT系命令でマウスによる2文字以上の入力を許可する", false)
        a += b(ConfigCode.CompatiCallEvent, "イベント関数のCALLを許可する", false)
        a += b(ConfigCode.CompatiSPChara, "SPキャラを使用する", false)
        a += b(ConfigCode.SystemSaveInBinary, "セーブデータをバイナリ形式で保存する", false)
        a += b(ConfigCode.CompatiFuncArgOptional, "ユーザー関数の全ての引数の省略を許可する", false)
        a += b(ConfigCode.CompatiFuncArgAutoConvert, "ユーザー関数の引数に自動的にTOSTRを補完する", false)
        a += b(ConfigCode.SystemIgnoreTripleSymbol, "FORM中の三連記号を展開しない", false)
        a += b(ConfigCode.TimesNotRigorousCalculation, "TIMESの計算をeramakerにあわせる", false)
        a += b(ConfigCode.SystemNoTarget, "キャラクタ変数の引数を補完しない", false)
        a += b(ConfigCode.SystemIgnoreStringSet, "文字列変数の代入に文字列式を強制する", false)

        val d = debugArray
        d += b(ConfigCode.DebugShowWindow, "起動時にデバッグウインドウを表示する", true)
        d += b(ConfigCode.DebugWindowTopMost, "デバッグウインドウを最前面に表示する", true)
        d += i(ConfigCode.DebugWindowWidth, "デバッグウィンドウ幅", 400)
        d += i(ConfigCode.DebugWindowHeight, "デバッグウィンドウ高さ", 300)
        d += b(ConfigCode.DebugSetWindowPos, "デバッグウィンドウ位置を指定する", false)
        d += i(ConfigCode.DebugWindowPosX, "デバッグウィンドウ位置X", 0)
        d += i(ConfigCode.DebugWindowPosY, "デバッグウィンドウ位置Y", 0)

        val r = replaceArray
        r += s(ConfigCode.MoneyLabel, "お金の単位", "$")
        r += b(ConfigCode.MoneyFirst, "単位の位置", true)
        r += s(ConfigCode.LoadLabel, "起動時簡略表示", "Now Loading...")
        r += i(ConfigCode.MaxShopItem, "販売アイテム数", 100)
        r += s(ConfigCode.DrawLineString, "DRAWLINE文字", "-")
        r += c(ConfigCode.BarChar1, "BAR文字1", '*')
        r += c(ConfigCode.BarChar2, "BAR文字2", '.')
        r += s(ConfigCode.TitleMenuString0, "システムメニュー0", "最初からはじめる")
        r += s(ConfigCode.TitleMenuString1, "システムメニュー1", "ロードしてはじめる")
        r += i(ConfigCode.ComAbleDefault, "COM_ABLE初期値", 1)
        r += ll(ConfigCode.StainDefault, "汚れの初期値", listOf(0, 0, 2, 1, 8))
        r += s(ConfigCode.TimeupLabel, "時間切れ表示", "時間切れ")
        r += ll(ConfigCode.ExpLvDef, "EXPLVの初期値", listOf(0, 1, 4, 20, 50, 200))
        r += ll(ConfigCode.PalamLvDef, "PALAMLVの初期値", listOf(0, 100, 500, 3000, 10000, 30000, 60000, 100000, 150000, 250000))
        r += l(ConfigCode.pbandDef, "PBANDの初期値", 4)
        r += l(ConfigCode.RelationDef, "RELATIONの初期値", 0)
    }

    fun getConfigNameDic(): Map<ConfigCode, String> = configArray.associate { it.code to it.text }

    @Suppress("UNCHECKED_CAST")
    fun <T> getConfigValue(code: ConfigCode): T = getItem(code)!!.anyValue as T

    fun getItem(code: ConfigCode): AConfigItem? =
        configArray.firstOrNull { it.code == code } ?: replaceArray.firstOrNull { it.code == code } ?: debugArray.firstOrNull { it.code == code }

    fun getItem(key: String): AConfigItem? = getConfigItem(key) ?: getReplaceItem(key) ?: getDebugItem(key)

    fun getConfigItem(code: ConfigCode): AConfigItem? = configArray.firstOrNull { it.code == code }
    fun getConfigItem(key: String): AConfigItem? = configArray.firstOrNull { it.name == key || it.text == key || ConfigAliases.match(it.code, key) }
    fun getReplaceItem(key: String): AConfigItem? = replaceArray.firstOrNull { it.name == key || it.text == key || ConfigAliases.match(it.code, key) }
    fun getDebugItem(key: String): AConfigItem? = debugArray.firstOrNull { it.name == key || it.text == key }

    /** GETCONFIG / GETCONFIGS */
    fun getConfigValueInERB(text: String, errMes: Array<String?>): SingleTerm? {
        val item = getItem(text)
        if (item == null) {
            errMes[0] = "文字列\"$text\"は適切なコンフィグ名ではありません"
            return null
        }
        return when (item.code) {
            ConfigCode.AutoSave, ConfigCode.MoneyFirst -> SingleTerm(if (item.anyValue as Boolean) 1L else 0L)
            ConfigCode.WindowX, ConfigCode.PrintCPerLine, ConfigCode.PrintCLength, ConfigCode.FontSize,
            ConfigCode.LineHeight, ConfigCode.SaveDataNos, ConfigCode.MaxShopItem, ConfigCode.ComAbleDefault ->
                SingleTerm((item.anyValue as Int).toLong())
            ConfigCode.ForeColor, ConfigCode.BackColor, ConfigCode.FocusColor, ConfigCode.LogColor -> {
                val color = item.anyValue as EraColor
                SingleTerm(((color.r * 256L) + color.g) * 256 + color.b)
            }
            ConfigCode.pbandDef, ConfigCode.RelationDef -> SingleTerm(item.anyValue as Long)
            ConfigCode.FontName, ConfigCode.MoneyLabel, ConfigCode.LoadLabel, ConfigCode.DrawLineString,
            ConfigCode.TitleMenuString0, ConfigCode.TitleMenuString1, ConfigCode.TimeupLabel ->
                SingleTerm(item.anyValue as String)
            ConfigCode.BarChar1, ConfigCode.BarChar2 -> SingleTerm((item.anyValue as Char).toString())
            ConfigCode.TextDrawingMode -> SingleTerm(item.anyValue.toString())
            else -> {
                errMes[0] = "コンフィグ文字列\"$text\"の値の取得は許可されていません"
                null
            }
        }
    }

    fun reLoadConfig(): Boolean {
        for (item in configArray) item.fixed = false
        loadConfig()
        return true
    }

    fun loadConfig(): Boolean {
        var defaultConfigPath = Program.CsvDir + "_default.config"
        var fixedConfigPath = Program.CsvDir + "_fixed.config"
        if (!FileUtil.exists(defaultConfigPath)) defaultConfigPath = Program.CsvDir + "default.config"
        if (!FileUtil.exists(fixedConfigPath)) fixedConfigPath = Program.CsvDir + "fixed.config"
        loadConfig(defaultConfigPath, false)
        loadConfig(Program.ExeDir + "emuera.config", false)
        loadConfig(fixedConfigPath, true)
        Config.setConfig(this)
        Config.setReplace(this)
        return true
    }

    private fun loadConfig(confPath: String, fix: Boolean): Boolean {
        if (!FileUtil.exists(confPath)) return false
        val eReader = EraStreamReader(false)
        if (!eReader.open(confPath)) return false
        var pos: ScriptPosition? = null
        try {
            while (true) {
                val line = eReader.readLine() ?: break
                if (line.isEmpty() || line[0] == ';') continue
                pos = ScriptPosition(eReader.fileName, eReader.lineNo)
                val tokens = line.split(':').toMutableList()
                if (tokens.size < 2) continue
                var item = getConfigItem(tokens[0].trim()) ?: continue
                if (item.code == ConfigCode.CompatiDRAWLINE) item = getConfigItem(ConfigCode.CompatiLinefeedAs1739)!!
                if (item.code == ConfigCode.TextEditor && tokens.size > 2) {
                    if (tokens[2].startsWith("\\")) tokens[1] += ":" + tokens[2]
                    for (k in 3 until tokens.size) tokens[1] += ":" + tokens[k]
                }
                if (item.code == ConfigCode.EditorArgument) {
                    item.setAnyValue(tokens[1])
                    continue
                }
                try {
                    if (item.tryParse(tokens[1]) && fix) item.fixed = true
                } catch (ee: EmueraException) {
                    ParserMediator.configWarn(ee.message ?: "", pos, 1, null)
                }
            }
        } catch (ee: EmueraException) {
            ParserMediator.configWarn(ee.message ?: "", pos, 1, null)
        } catch (exc: Exception) {
            ParserMediator.configWarn(exc.javaClass.simpleName + ":" + exc.message, pos, 1, exc.stackTraceToString())
        } finally {
            eReader.close()
        }
        return true
    }

    /** _Replace.csv (単位の差し替えおよび前置、後置のためのコンフィグ処理) */
    fun loadReplaceFile(filename: String) {
        val eReader = EraStreamReader(false)
        if (!eReader.open(filename)) return
        var pos: ScriptPosition? = null
        try {
            while (true) {
                val line = eReader.readLine() ?: break
                if (line.isEmpty() || line[0] == ';') continue
                pos = ScriptPosition(eReader.fileName, eReader.lineNo)
                val tokens = line.split(',', ':')
                if (tokens.size < 2) continue
                val itemName = tokens[0].trim()
                val value = line.substring(tokens[0].length + 1)
                if (value.trim().isEmpty()) continue
                getReplaceItem(itemName)?.tryParse(value)
            }
        } catch (ee: EmueraException) {
            ParserMediator.warn(ee.message ?: "", pos, 1)
        } catch (exc: Exception) {
            ParserMediator.warn(exc.javaClass.simpleName + ":" + exc.message, pos, 1, exc.stackTraceToString())
        } finally {
            eReader.close()
        }
        Config.setReplace(this)
    }

    companion object {
        @JvmStatic var instance = ConfigData()
            private set

        /** Reset to defaults (new game load). */
        fun reset() { instance = ConfigData() }
    }
}

/**
 * Korean/English config key aliases. Korean-translated Emuera builds write emuera.config
 * with translated item names; accept the common ones so those settings still apply.
 */
object ConfigAliases {
    private val map: Map<ConfigCode, List<String>> = mapOf(
        ConfigCode.IgnoreCase to listOf("대문자 소문자의 차이를 무시한다", "대소문자의 차이를 무시한다"),
        ConfigCode.UseRenameFile to listOf("_Rename.csv를 이용한다", "_Rename.csv를 사용한다"),
        ConfigCode.UseReplaceFile to listOf("_Replace.csv를 이용한다", "_Replace.csv를 사용한다"),
        ConfigCode.FontName to listOf("폰트 이름", "폰트명"),
        ConfigCode.FontSize to listOf("폰트 크기", "폰트 사이즈"),
        ConfigCode.LineHeight to listOf("한 줄의 높이", "1행의 높이"),
        ConfigCode.ForeColor to listOf("문자색", "글자색"),
        ConfigCode.BackColor to listOf("배경색"),
        ConfigCode.FocusColor to listOf("선택중 문자색", "선택 중 문자색"),
        ConfigCode.LogColor to listOf("이력 문자색"),
        ConfigCode.PrintCPerLine to listOf("PRINTC를 늘어놓는 수"),
        ConfigCode.PrintCLength to listOf("PRINTC의 문자수"),
        ConfigCode.WindowX to listOf("창 너비", "윈도우 폭", "윈도우 너비"),
        ConfigCode.SystemAllowFullSpace to listOf("전각 스페이스를 화이트 스페이스에 포함한다"),
        ConfigCode.SystemSaveInUTF8 to listOf("세이브 데이터를 UTF-8로 저장한다"),
        ConfigCode.SystemSaveInBinary to listOf("세이브 데이터를 바이너리 형식으로 저장한다"),
        ConfigCode.useLanguage to listOf("내부에서 사용하는 동아시아 언어"),
        ConfigCode.CompatiRAND to listOf("의사변수 RAND의 사양을 eramaker에 맞춘다"),
        ConfigCode.CompatiCALLNAME to listOf("CALLNAME이 빈 문자열일 때 NAME을 대입한다"),
        ConfigCode.CompatiFuncArgOptional to listOf("사용자 함수의 모든 인수의 생략을 허가한다"),
        ConfigCode.CompatiFuncArgAutoConvert to listOf("사용자 함수의 인수에 자동적으로 TOSTR을 보완한다"),
        ConfigCode.SystemIgnoreTripleSymbol to listOf("FORM 중의 삼연 기호를 전개하지 않는다"),
        ConfigCode.CompatiSPChara to listOf("SP 캐릭터를 사용한다"),
        ConfigCode.CompatiCallEvent to listOf("이벤트 함수의 CALL을 허가한다"),
        ConfigCode.SystemNoTarget to listOf("캐릭터 변수의 인수를 보완하지 않는다"),
        ConfigCode.MoneyLabel to listOf("돈의 단위"),
        ConfigCode.MoneyFirst to listOf("단위의 위치"),
        ConfigCode.DrawLineString to listOf("DRAWLINE 문자"),
        ConfigCode.TitleMenuString0 to listOf("시스템 메뉴0", "시스템메뉴0"),
        ConfigCode.TitleMenuString1 to listOf("시스템 메뉴1", "시스템메뉴1"),
        ConfigCode.TimeupLabel to listOf("시간초과 표시", "시간 초과 표시"),
        ConfigCode.MaxShopItem to listOf("판매 아이템 수"),
        ConfigCode.ComAbleDefault to listOf("COM_ABLE 초기값"),
    )

    fun match(code: ConfigCode, key: String): Boolean = map[code]?.any { it == key } == true
}
