package com.eraandroid.core.interpreter

import com.eraandroid.core.parser.*
import com.eraandroid.core.vm.*
import kotlin.math.*

// ─── 내장 함수 평가 (EraInterpreter extension) ───────────────────────────────
//
// evalBuiltinOrCall을 별도 파일로 분리.
// EraInterpreter의 extension function이므로 internal 멤버에 접근 가능.
// Interpreter.kt의 evalBuiltinOrCall은 이 함수를 1줄로 위임.

internal suspend fun EraInterpreter.evalBuiltinOrCall_impl(expr: FunctionCall): EraValue {
    return when (expr.name.uppercase()) {
        "ABS" -> EraValue.of(abs(evalExpr(expr.args[0]).toLong()))
        "SQRT" -> EraValue.of(sqrt(evalExpr(expr.args[0]).toDouble()).toLong())
        "SIGN" -> {
            val v = evalExpr(expr.args[0]).toLong()
            EraValue.of(if (v > 0) 1L else if (v < 0) -1L else 0L)
        }
        "MAX" -> {
            var max = Long.MIN_VALUE
            for (arg in expr.args) { val v = evalExpr(arg).toLong(); if (v > max) max = v }
            EraValue.of(max)
        }
        "MIN" -> {
            var min = Long.MAX_VALUE
            for (arg in expr.args) { val v = evalExpr(arg).toLong(); if (v < min) min = v }
            EraValue.of(min)
        }
        "LIMIT" -> {
            val v = evalExpr(expr.args[0]).toLong()
            val lo = evalExpr(expr.args[1]).toLong()
            val hi = evalExpr(expr.args[2]).toLong()
            EraValue.of(v.coerceIn(lo, hi))
        }
        "INRANGE" -> {
            val v = evalExpr(expr.args[0]).toLong()
            val lo = evalExpr(expr.args[1]).toLong()
            val hi = evalExpr(expr.args[2]).toLong()
            EraValue.of(v in lo..hi)
        }
        "TOINT" -> EraValue.of(evalExpr(expr.args[0]).toLong())
        "TOSTR" -> {
            val v = evalExpr(expr.args[0])
            val fmt = expr.args.getOrNull(1)?.let { evalExpr(it).toEraString() }
            if (fmt != null) {
                // ERA의 TOSTR 포맷: "00" → 2자리 0패딩, "000" → 3자리 0패딩
                // "%d" 같은 C포맷도 지원
                val result = when {
                    fmt.matches(Regex("0+")) -> {
                        // "00", "000" 형식 → 자릿수만큼 0패딩
                        String.format("%0${fmt.length}d", v.toLong())
                    }
                    fmt.matches(Regex("0+\\.0+")) -> {
                        // "0.000", "0.00" 형식 → ERA 버전 표기 등에 사용
                        // 소수점 이하 자릿수만큼 10^n으로 나눠서 소수 표기
                        val decimalPlaces = fmt.substringAfter('.').length
                        val divisor = Math.pow(10.0, decimalPlaces.toDouble()).toLong()
                        val intPart = v.toLong() / divisor
                        val fracPart = Math.abs(v.toLong() % divisor)
                        String.format("%d.%0${decimalPlaces}d", intPart, fracPart)
                    }
                    fmt.contains("%") -> {
                        // "%d", "%05d" 등 C포맷 형식
                        String.format(fmt, v.toLong())
                    }
                    else -> v.toEraString()
                }
                EraValue.of(result)
            } else {
                EraValue.of(v.toEraString())
            }
        }
        "ISNUMERIC" -> {
            val s = evalExpr(expr.args[0]).toEraString()
            EraValue.of(s.toLongOrNull() != null)
        }
        // KR_NAME은 ZNAME.ERB에 #FUNCTION으로 정의되어 있으므로 빌트인 처리 안 함
        // → else 브랜치의 callFunction 으로 위임됨
        "STRLENS" -> EraValue.of(EraStringUtils.eraByteWidth(evalExpr(expr.args[0]).toEraString()).toLong())
        "SUBSTRING" -> {
            val s = evalExpr(expr.args[0]).toEraString()
            val startByte = evalExpr(expr.args[1]).toLong().toInt()
            val lenByte   = expr.args.getOrNull(2)?.let { evalExpr(it).toLong().toInt() } ?: -1
            EraValue.of(EraStringUtils.eraSubstringByByte(s, startByte, lenByte))
        }
        "CHARATU" -> {
            val s = evalExpr(expr.args[0]).toEraString()
            val idx = evalExpr(expr.args[1]).toLong().toInt()
            EraValue.of(s.getOrNull(idx)?.toString() ?: "")
        }
        "STRFIND" -> {
            val s = evalExpr(expr.args[0]).toEraString()
            val pattern = evalExpr(expr.args[1]).toEraString()
            val startByte = expr.args.getOrNull(2)?.let { evalExpr(it).toLong().toInt() } ?: 0
            val startChar = EraStringUtils.eraByteToCharIndex(s, startByte)
            val charIdx = if (startChar < s.length) s.indexOf(pattern, startChar) else -1
            EraValue.of(if (charIdx >= 0) EraStringUtils.eraByteWidth(s.substring(0, charIdx)).toLong() else -1L)
        }
        "STRJOIN" -> {
            val delim = expr.args.getOrNull(1)?.let { evalExpr(it).toEraString() } ?: ","
            val startIdx = expr.args.getOrNull(2)?.let { evalExpr(it).toLong().toInt() } ?: 0
            val vr = expr.args.getOrNull(0) as? VariableRef
            if (vr != null) {
                val variable = resolveVarRef(vr)
                if (variable != null) {
                    val all = variable.getAll()
                    val cnt = expr.args.getOrNull(3)?.let { evalExpr(it).toLong().toInt() } ?: (all.size - startIdx)
                    EraValue.of((startIdx until (startIdx+cnt).coerceAtMost(all.size)).joinToString(delim) { all[it].toEraString() })
                } else EraValue.EMPTY_STRING
            } else {
                EraValue.of(expr.args.map { evalExpr(it).toEraString() }.joinToString(delim))
            }
        }
        "REPLACE" -> {
            val s    = evalExpr(expr.args[0]).toEraString()
            val pat  = evalExpr(expr.args[1]).toEraString()
            val to   = evalExpr(expr.args[2]).toEraString()
            EraValue.of(try { s.replace(Regex(pat), to) } catch (e: Exception) { s.replace(pat, to) })
        }
        "GETCOLOR" -> EraValue.of(currentColor?.toLong() ?: 0xFFFFFFFFL.toLong())
        "GETBGCOLOR" -> EraValue.of(currentBgColor?.toLong() ?: 0xFF000000L)
        "GETDEFCOLOR" -> EraValue.of(0xFFFFFFFFL.toLong())
        "GETTIME" -> {
            val now = java.util.Calendar.getInstance()
            var date = now.get(java.util.Calendar.YEAR).toLong()
            date = date * 100 + (now.get(java.util.Calendar.MONTH) + 1)
            date = date * 100 + now.get(java.util.Calendar.DAY_OF_MONTH)
            date = date * 100 + now.get(java.util.Calendar.HOUR_OF_DAY)
            date = date * 100 + now.get(java.util.Calendar.MINUTE)
            date = date * 100 + now.get(java.util.Calendar.SECOND)
            date = date * 1000 + now.get(java.util.Calendar.MILLISECOND)
            val ds = String.format("%04d/%02d/%02d %02d:%02d:%02d",
                now.get(java.util.Calendar.YEAR), now.get(java.util.Calendar.MONTH)+1,
                now.get(java.util.Calendar.DAY_OF_MONTH), now.get(java.util.Calendar.HOUR_OF_DAY),
                now.get(java.util.Calendar.MINUTE), now.get(java.util.Calendar.SECOND))
            scope.getOrCreate("RESULT").set(listOf(0), EraValue.of(date))
            scope.getOrCreate("RESULTS", true, listOf(100)).set(listOf(0), EraValue.of(ds))
            EraValue.of(date)
        }
        "GETTIMES" -> {
            val now = java.util.Calendar.getInstance()
            EraValue.of(String.format("%04d/%02d/%02d %02d:%02d:%02d",
                now.get(java.util.Calendar.YEAR), now.get(java.util.Calendar.MONTH)+1,
                now.get(java.util.Calendar.DAY_OF_MONTH), now.get(java.util.Calendar.HOUR_OF_DAY),
                now.get(java.util.Calendar.MINUTE), now.get(java.util.Calendar.SECOND)))
        }
        "GETSECOND" -> EraValue.of(System.currentTimeMillis() / 1000L + 62135596800L)
        "GETMILLISECOND" -> EraValue.of(System.currentTimeMillis() + 62135596800000L)

        // ── 비트 연산 함수 ────────────────────────────────────────────────
        "GETBIT" -> {
            val v = evalExpr(expr.args[0]).toLong()
            val bit = evalExpr(expr.args[1]).toLong().toInt()
            EraValue.of((v shr bit) and 1L)
        }

        // ── 유니코드 문자열 함수 ──────────────────────────────────────────
        "STRLENSU" -> {
            // 유니코드 코드포인트 기준 길이 (한글/한자 포함)
            val s = evalExpr(expr.args[0]).toEraString()
            EraValue.of(s.codePointCount(0, s.length).toLong())
        }
        "SUBSTRINGU" -> {
            // 유니코드 코드포인트 기준 substring
            val s = evalExpr(expr.args[0]).toEraString()
            val startCp = evalExpr(expr.args[1]).toLong().toInt()
            val lenCp = expr.args.getOrNull(2)?.let { evalExpr(it).toLong().toInt() } ?: -1
            val codePoints = s.codePoints().toArray()
            val sliced = if (lenCp < 0) codePoints.drop(startCp)
            else codePoints.drop(startCp).take(lenCp)
            EraValue.of(String(sliced.toIntArray(), 0, sliced.size))
        }
        "STRLENFORM" -> {
            val s = if (expr.args.isEmpty()) "" else evalFormString(expr.args[0])
            EraValue.of(s.length.toLong())
        }

        // ── 수학 함수 ────────────────────────────────────────────────────
        "POWER" -> {
            val base = evalExpr(expr.args[0]).toLong()
            val exp  = evalExpr(expr.args[1]).toLong()
            EraValue.of(base.toDouble().pow(exp.toDouble()).toLong())
        }
        "LOG" -> {
            val v = evalExpr(expr.args[0]).toDouble()
            EraValue.of(kotlin.math.ln(v))
        }
        "CBRT" -> {
            val v = evalExpr(expr.args[0]).toDouble()
            EraValue.of(kotlin.math.cbrt(v).toLong())
        }

        // ── 캐릭터 쿼리 함수 ─────────────────────────────────────────────
        "GETCHARA" -> {
            val no = evalExpr(expr.args[0]).toLong().toInt()
            EraValue.of(charaManager.charas.indexOfFirst { it.no == no }.toLong())
        }
        "FINDCHARA" -> {
            // FINDCHARA(varName, value) → charaManager 에서 검색
            val varName = evalExpr(expr.args[0]).toEraString().uppercase()
            val value   = evalExpr(expr.args[1]).toLong()
            val idx = charaManager.charas.indexOfFirst { it.getNum(varName) == value }
            EraValue.of(idx.toLong())
        }
        "GETPALAMLV" -> {
            val curVal = evalExpr(expr.args[0]).toLong()
            val maxLv  = expr.args.getOrNull(1)?.let { evalExpr(it).toLong().toInt() } ?: 100
            EraValue.of(calcPalamLv(curVal, maxLv, "PALAMLV"))
        }
        "GETEXPLV" -> {
            val curVal = evalExpr(expr.args[0]).toLong()
            val maxLv  = expr.args.getOrNull(1)?.let { evalExpr(it).toLong().toInt() } ?: 100
            EraValue.of(calcPalamLv(curVal, maxLv, "EXPLV"))
        }
        "EXISTCSV" -> {
            // 캐릭터 정의 존재 여부 — 항상 0(없음)으로 처리
            EraValue.ZERO
        }
        "CSVNAME" -> {
            val no = evalExpr(expr.args[0]).toLong().toInt()
            EraValue.of(charaManager.getCharaOrTemplate(no)?.getStr("NAME") ?: "")
        }
        "CSVNICKNAME" -> {
            val no = evalExpr(expr.args[0]).toLong().toInt()
            EraValue.of(charaManager.getCharaOrTemplate(no)?.getStr("NICKNAME") ?: "")
        }
        "CSVBASE" -> {
            val no  = evalExpr(expr.args[0]).toLong().toInt()
            val idx = expr.args.getOrNull(1)?.let { evalExpr(it).toLong().toInt() } ?: 0
            EraValue.of(charaManager.getCharaOrTemplate(no)?.getNum("BASE", idx) ?: 0L)
        }
        "CSVTALENT" -> {
            val no  = evalExpr(expr.args[0]).toLong().toInt()
            val idx = expr.args.getOrNull(1)?.let { evalExpr(it).toLong().toInt() } ?: 0
            EraValue.of(charaManager.getCharaOrTemplate(no)?.getNum("TALENT", idx) ?: 0L)
        }
        "CSVABL" -> {
            val no  = evalExpr(expr.args[0]).toLong().toInt()
            val idx = expr.args.getOrNull(1)?.let { evalExpr(it).toLong().toInt() } ?: 0
            EraValue.of(charaManager.getCharaOrTemplate(no)?.getNum("ABL", idx) ?: 0L)
        }
        "CSVEXP" -> {
            val no  = evalExpr(expr.args[0]).toLong().toInt()
            val idx = expr.args.getOrNull(1)?.let { evalExpr(it).toLong().toInt() } ?: 0
            EraValue.of(charaManager.getCharaOrTemplate(no)?.getNum("EXP", idx) ?: 0L)
        }
        "CSVMARK" -> {
            val no  = evalExpr(expr.args[0]).toLong().toInt()
            val idx = expr.args.getOrNull(1)?.let { evalExpr(it).toLong().toInt() } ?: 0
            EraValue.of(charaManager.getCharaOrTemplate(no)?.getNum("MARK", idx) ?: 0L)
        }
        "CSVRELATION" -> {
            val no  = evalExpr(expr.args[0]).toLong().toInt()
            val idx = expr.args.getOrNull(1)?.let { evalExpr(it).toLong().toInt() } ?: 0
            EraValue.of(charaManager.getCharaOrTemplate(no)?.getNum("RELATION", idx) ?: 0L)
        }
        "CSVJUEL" -> {
            val no  = evalExpr(expr.args[0]).toLong().toInt()
            val idx = expr.args.getOrNull(1)?.let { evalExpr(it).toLong().toInt() } ?: 0
            EraValue.of(charaManager.getCharaOrTemplate(no)?.getNum("JUEL", idx) ?: 0L)
        }
        "CSVCFLAG" -> {
            val no  = evalExpr(expr.args[0]).toLong().toInt()
            val idx = expr.args.getOrNull(1)?.let { evalExpr(it).toLong().toInt() } ?: 0
            EraValue.of(charaManager.getCharaOrTemplate(no)?.getNum("CFLAG", idx) ?: 0L)
        }
        "CSVCSTR" -> {
            val no  = evalExpr(expr.args[0]).toLong().toInt()
            val idx = expr.args.getOrNull(1)?.let { evalExpr(it).toLong().toInt() } ?: 0
            EraValue.of(charaManager.getCharaOrTemplate(no)?.getStr("CSTR", idx) ?: "")
        }
        "CSVMASTERNAME" -> {
            val no  = evalExpr(expr.args[0]).toLong().toInt()
            EraValue.of(charaManager.getCharaOrTemplate(no)?.getStr("MASTERNAME", 0) ?: "")
        }
        "CSVNO" -> {
            val no  = evalExpr(expr.args[0]).toLong().toInt()
            EraValue.of(charaManager.getCharaOrTemplate(no)?.no?.toLong() ?: -1L)
        }
        "CHARANUM" -> EraValue.of(charaManager.count.toLong())

        // ── 문자열 유틸 ──────────────────────────────────────────────────
        "ENCODETOUNI" -> {
            val s = evalExpr(expr.args[0]).toEraString()
            val cps = s.codePoints().toArray()
            scope.getOrCreate("RESULT").set(listOf(0), EraValue.of(cps.size.toLong()))
            for (i in cps.indices) scope.getOrCreate("RESULT").set(listOf(i+1), EraValue.of(cps[i].toLong()))
            EraValue.of(cps.size.toLong())
        }
        "UNICODE" -> {
            val cp = evalExpr(expr.args[0]).toLong().toInt()
            val ch = try { String(Character.toChars(cp)) } catch (e: Exception) { "" }
            EraValue.of(ch)
        }
        "TOUPPER" -> EraValue.of(evalExpr(expr.args[0]).toEraString().uppercase())
        "TOLOWER" -> EraValue.of(evalExpr(expr.args[0]).toEraString().lowercase())
        "TOHALF"  -> EraValue.of(evalExpr(expr.args[0]).toEraString()) // 전각→반각 미지원, 그대로 반환
        "TOFULL"  -> EraValue.of(evalExpr(expr.args[0]).toEraString())

        // ── 배열 함수 ────────────────────────────────────────────────────
        "INRANGEARRAY" -> {
            val vr = expr.args.getOrNull(0) as? VariableRef
            val lo = expr.args.getOrNull(1)?.let { evalExpr(it).toLong() } ?: 0L
            val hi = expr.args.getOrNull(2)?.let { evalExpr(it).toLong() } ?: 0L
            val variable = if (vr != null) resolveVarRef(vr) else null
            EraValue.of(variable?.getAll()?.count { it.toLong() in lo..hi }?.toLong() ?: 0L)
        }
        "SUMARRAY" -> {
            val vr = expr.args.getOrNull(0) as? VariableRef
            val s  = expr.args.getOrNull(1)?.let { evalExpr(it).toLong().toInt() } ?: 0
            val c  = expr.args.getOrNull(2)?.let { evalExpr(it).toLong().toInt() } ?: -1
            val variable = if (vr != null) resolveVarRef(vr) else null
            if (variable != null) {
                val all = variable.getAll(); val end = if (c < 0) all.size else (s+c).coerceAtMost(all.size)
                EraValue.of(all.slice(s until end).sumOf { it.toLong() })
            } else EraValue.ZERO
        }
        "MAXARRAY" -> {
            val vr = expr.args.getOrNull(0) as? VariableRef
            val s  = expr.args.getOrNull(1)?.let { evalExpr(it).toLong().toInt() } ?: 0
            val c  = expr.args.getOrNull(2)?.let { evalExpr(it).toLong().toInt() } ?: -1
            val variable = if (vr != null) resolveVarRef(vr) else null
            if (variable != null) {
                val all = variable.getAll(); val end = if (c < 0) all.size else (s+c).coerceAtMost(all.size)
                EraValue.of(all.slice(s until end).maxOfOrNull { it.toLong() } ?: 0L)
            } else EraValue.ZERO
        }
        "MINARRAY" -> {
            val vr = expr.args.getOrNull(0) as? VariableRef
            val s  = expr.args.getOrNull(1)?.let { evalExpr(it).toLong().toInt() } ?: 0
            val c  = expr.args.getOrNull(2)?.let { evalExpr(it).toLong().toInt() } ?: -1
            val variable = if (vr != null) resolveVarRef(vr) else null
            if (variable != null) {
                val all = variable.getAll(); val end = if (c < 0) all.size else (s+c).coerceAtMost(all.size)
                EraValue.of(all.slice(s until end).minOfOrNull { it.toLong() } ?: 0L)
            } else EraValue.ZERO
        }
        "FINDELEMENT", "FINDLASTELEMENT" -> {
            val isLast = expr.name.uppercase() == "FINDLASTELEMENT"
            val vr = expr.args.getOrNull(0) as? VariableRef
            val tgt = expr.args.getOrNull(1)?.let { evalExpr(it) } ?: EraValue.ZERO
            val s   = expr.args.getOrNull(2)?.let { evalExpr(it).toLong().toInt() } ?: 0
            val c   = expr.args.getOrNull(3)?.let { evalExpr(it).toLong().toInt() } ?: -1
            val variable = if (vr != null) resolveVarRef(vr) else null
            if (variable != null) {
                val all = variable.getAll(); val end = if (c < 0) all.size else (s+c).coerceAtMost(all.size)
                val found = if (isLast) (s until end).lastOrNull { eraEquals(all[it], tgt) }
                else       (s until end).firstOrNull { eraEquals(all[it], tgt) }
                EraValue.of((found ?: -1).toLong())
            } else EraValue.of(-1L)
        }

        else -> {
            val args = mutableListOf<EraValue>()
            for (arg in expr.args) args.add(evalExpr(arg))
            // expression 위치에서 호출 시 RESULT 초기화 안 함 (RESULT 오염 방지)
            val savedResult  = scope.getOrCreate("RESULT").get(listOf(0))
            val savedResults = scope.getOrCreate("RESULTS", true, listOf(100)).get(listOf(0))
            scope.getOrCreate("RESULTS", true, listOf(100)).set(listOf(0), EraValue.EMPTY_STRING)
            val ret = try {
                callFunctionPublic(expr.name.uppercase(), args, initResult = false)
            } catch (e: EraRuntimeException) {
                if (!e.message.orEmpty().startsWith("Function not found")) throw e
                else android.util.Log.w("ERA_CALL1", "Builtin not found (ignored): ${expr.name}")
                null
            }
            when {
                ret is EraValue.EraString -> {
                    scope.getOrCreate("RESULT").set(listOf(0), savedResult)
                    scope.getOrCreate("RESULTS", true, listOf(100)).set(listOf(0), savedResults)
                    ret
                }
                ret != null -> {
                    scope.getOrCreate("RESULT").set(listOf(0), savedResult)
                    scope.getOrCreate("RESULTS", true, listOf(100)).set(listOf(0), savedResults)
                    ret
                }
                else -> {
                    val strResult = scope.getOrCreate("RESULTS", true, listOf(100)).get(listOf(0))
                    val fnResult  = scope.getOrCreate("RESULT").get(listOf(0))
                    scope.getOrCreate("RESULT").set(listOf(0), savedResult)
                    when {
                        strResult.toEraString().isNotEmpty() -> strResult
                        fnResult.toLong() != savedResult.toLong() -> fnResult
                        else -> { scope.getOrCreate("RESULTS", true, listOf(100)).set(listOf(0), savedResults); fnResult }
                    }
                }
            }
        }
    }
}