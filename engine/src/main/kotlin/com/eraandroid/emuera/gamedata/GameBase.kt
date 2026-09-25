package com.eraandroid.emuera.gamedata

import com.eraandroid.emuera.sub.EraStreamReader
import com.eraandroid.emuera.sub.FileUtil
import com.eraandroid.emuera.sub.ScriptPosition
import java.io.File

class GameBase {
    var ScriptAutherName = ""
    var ScriptDetail = ""
    var ScriptYear = ""
    var ScriptTitle = ""
    var ScriptUniqueCode = 0L
    var ScriptVersion = 0L
    var ScriptVersionDefined = false
    var ScriptCompatibleMinVersion = -1L
    var Compatible_EmueraVer = "0.000.0.0"
    var ScriptWindowTitle: String? = null
    var DefaultCharacter = -1L
    var DefaultNoItem = 0L

    val ScriptVersionText: String
        get() {
            val sb = StringBuilder()
            sb.append(ScriptVersion / 1000)
            sb.append(".")
            if (ScriptVersion % 10 != 0L) sb.append(String.format("%03d", ScriptVersion % 1000))
            else sb.append(String.format("%02d", ScriptVersion % 1000 / 10))
            return sb.toString()
        }

    fun uniqueCodeEqualTo(target: Long): Boolean {
        if (target == 0L) return true
        return target == ScriptUniqueCode
    }

    fun checkVersion(target: Long): Boolean {
        if (!ScriptVersionDefined && target != 1000L) return true
        if (ScriptCompatibleMinVersion <= target) return true
        return ScriptVersion == target
    }

    private fun tryatoi(str: String): Long? {
        str.trim().toLongOrNull()?.let { return it }
        val sb = StringBuilder()
        for (c in str) { if (!Character.isDigit(c)) break; sb.append(c) }
        if (sb.isNotEmpty()) return sb.toString().toLongOrNull()
        return null
    }

    /** GAMEBASE読み込み。読み込み続行するなら真 */
    fun loadGameBaseCsv(basePath: String): Boolean {
        if (!FileUtil.exists(basePath)) return true
        var pos: ScriptPosition? = null
        val eReader = EraStreamReader(false)
        if (!eReader.open(basePath)) return true
        try {
            while (true) {
                val st = eReader.readEnabledLine() ?: break
                val tokens = st.substring().split(',')
                if (tokens.size < 2) continue
                pos = ScriptPosition(eReader.fileName, eReader.lineNo)
                when (tokens[0].trim()) {
                    "コード", "코드" -> tryatoi(tokens[1])?.let {
                        ScriptUniqueCode = it
                        if (it == 0L) ParserMediator.warn("コード:0のセーブデータはいかなるコードのスクリプトからも読めるデータとして扱われます", pos, 0)
                    }
                    "バージョン", "버전" -> { val v = tryatoi(tokens[1]); ScriptVersionDefined = v != null; if (v != null) ScriptVersion = v }
                    "バージョン違い認める", "버전 차이 인정", "버전차이인정" -> tryatoi(tokens[1])?.let { ScriptCompatibleMinVersion = it }
                    "最初からいるキャラ", "처음부터 있는 캐릭터", "최초부터 있는 캐릭터" -> tryatoi(tokens[1])?.let { DefaultCharacter = it }
                    "アイテムなし", "아이템 없음" -> tryatoi(tokens[1])?.let { DefaultNoItem = it }
                    "タイトル", "타이틀", "제목" -> ScriptTitle = tokens[1]
                    "作者", "작자", "제작자", "작가" -> ScriptAutherName = tokens[1]
                    "製作年", "제작년", "제작년도" -> ScriptYear = tokens[1]
                    "追加情報", "추가 정보", "추가정보" -> ScriptDetail = tokens[1]
                    "ウィンドウタイトル", "윈도우 타이틀", "창 제목" -> ScriptWindowTitle = tokens[1]
                    "動作に必要なEmueraのバージョン" -> Compatible_EmueraVer = tokens[1]
                }
            }
        } catch (e: Exception) {
            ParserMediator.warn("GAMEBASE.CSVの読み込み中にエラーが発生したため、読みこみを中断します", pos, 1)
            return true
        } finally {
            eReader.close()
        }
        if (ScriptWindowTitle == null) {
            ScriptWindowTitle = if (ScriptTitle.isEmpty()) "Emuera" else "$ScriptTitle $ScriptVersionText"
        }
        return true
    }
}
