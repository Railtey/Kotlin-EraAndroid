package com.eraandroid.core.vm

import kotlin.math.min

// ─── ERA Value Types ─────────────────────────────────────────────────────────
sealed class EraValue {
    abstract fun toLong(): Long
    abstract fun toDouble(): Double
    abstract fun toEraString(): String

    data class EraInt(val value: Long) : EraValue() {
        override fun toLong() = value
        override fun toDouble() = value.toDouble()
        override fun toEraString() = value.toString()
    }

    data class EraFloat(val value: Double) : EraValue() {
        override fun toLong() = value.toLong()
        override fun toDouble() = value
        override fun toEraString() = value.toString()
    }

    data class EraString(val value: String) : EraValue() {
        override fun toLong() = value.toLongOrNull() ?: 0L
        override fun toDouble() = value.toDoubleOrNull() ?: 0.0
        override fun toEraString() = value
    }

    companion object {
        val ZERO = EraInt(0L)
        val ONE = EraInt(1L)
        val EMPTY_STRING = EraString("")

        fun of(v: Long) = EraInt(v)
        fun of(v: Double) = EraFloat(v)
        fun of(v: String) = EraString(v)
        fun of(v: Boolean) = if (v) ONE else ZERO
    }
}

// ─── Variable Storage ────────────────────────────────────────────────────────
// [변경] Array<EraValue> → HashMap<Int, EraValue> (sparse)
// 기본값(0 또는 "")은 맵에 저장하지 않으므로
// DITEMTYPE[1000×1000] 같은 거대 배열도 실제 사용한 칸만큼만 메모리를 씁니다.
open class EraVariable(
    val name: String,
    val isString: Boolean,
    dimensions: List<Int>,
    val isConst: Boolean = false
) {
    // mutable이어야 VariableSize.CSV로 재정의 가능
    val dimensions: MutableList<Int> = dimensions.toMutableList()
    val totalSize: Int get() = if (dimensions.isEmpty()) 1 else dimensions.reduce { a, b -> a * b }

    // sparse 저장소 — 기본값 칸은 저장하지 않음
    private val sparseData = HashMap<Int, EraValue>()

    private val defaultValue: EraValue
        get() = if (isString) EraValue.EMPTY_STRING else EraValue.ZERO

    open fun get(indices: List<Int>): EraValue {
        return sparseData[flatIndex(indices)] ?: defaultValue
    }

    open fun set(indices: List<Int>, value: EraValue) {
        if (isConst) throw EraRuntimeException("Cannot assign to const variable '$name'")
        val idx = flatIndex(indices)
        val coerced = coerce(value)
        // 기본값이면 맵에서 제거해서 메모리 돌려줌
        if (coerced == defaultValue) {
            sparseData.remove(idx)
        } else {
            sparseData[idx] = coerced
        }
    }

    fun setAll(value: EraValue, start: Int = 0, count: Int = -1) {
        val end = if (count < 0) totalSize else min(start + count, totalSize)
        val coerced = coerce(value)
        if (coerced == defaultValue) {
            // 해당 범위 키 전부 제거
            val keys = sparseData.keys.filter { it in start until end }
            keys.forEach { sparseData.remove(it) }
        } else {
            for (i in start until end) sparseData[i] = coerced
        }
    }

    fun getAll(): Array<EraValue> = Array(totalSize) { sparseData[it] ?: defaultValue }

    private fun flatIndex(indices: List<Int>): Int {
        if (indices.isEmpty()) return 0
        if (dimensions.isEmpty()) return 0
        var idx = 0
        var stride = 1
        for (i in dimensions.indices.reversed()) {
            val dimIndex = indices.getOrNull(i) ?: 0
            idx += dimIndex * stride
            stride *= dimensions[i]
        }
        return idx.coerceIn(0, totalSize - 1)
    }

    private fun coerce(value: EraValue): EraValue {
        return if (isString) {
            when (value) {
                is EraValue.EraString -> value
                else -> EraValue.EraString(value.toEraString())
            }
        } else {
            when (value) {
                is EraValue.EraInt -> value
                is EraValue.EraFloat -> EraValue.EraInt(value.toLong())
                is EraValue.EraString -> EraValue.EraInt(value.toLong())
            }
        }
    }

    /**
     * VariableSize.CSV에서 지정한 크기로 dimensions를 교체.
     * sparse 저장이므로 기존 데이터는 유지됨 (범위 초과 데이터는 접근 불가해질 뿐).
     */
    fun resize(newDimensions: List<Int>) {
        dimensions.clear()
        dimensions.addAll(newDimensions)
    }

    fun snapshot(): VariableSnapshot {
        // sparse 맵만 직렬화 — 기본값 칸은 저장 안 함
        return VariableSnapshot(name, isString, dimensions, sparseData.toMap())
    }

    fun restore(snapshot: VariableSnapshot) {
        sparseData.clear()
        sparseData.putAll(snapshot.sparseData)
    }
}

data class VariableSnapshot(
    val name: String,
    val isString: Boolean,
    val dimensions: List<Int>,
    // [변경] List<EraValue> → Map<Int, EraValue> (sparse)
    val sparseData: Map<Int, EraValue> = emptyMap()
) {
    /**
     * sparse 맵을 연속 List로 변환. SaveManager 직렬화에 사용.
     * 기본값(0 또는 "")은 변환 결과에 포함되지만, 마지막 비기본값 이후는 버림.
     */
    fun <T> sparseToList(transform: (EraValue) -> T): List<T> {
        if (sparseData.isEmpty()) return emptyList()
        val maxIdx = sparseData.keys.max()
        val default: EraValue = if (isString) EraValue.EMPTY_STRING else EraValue.ZERO
        return (0..maxIdx).map { i -> transform(sparseData[i] ?: default) }
    }
}

// ─── Variable Registry ───────────────────────────────────────────────────────
class VariableScope {
    private val variables = mutableMapOf<String, EraVariable>()
    private val defines = mutableMapOf<String, String>()

    fun define(name: String, variable: EraVariable) {
        variables[name.uppercase()] = variable
    }

    fun get(name: String): EraVariable? = variables[name.uppercase()]

    fun getOrCreate(name: String, isString: Boolean = false, dimensions: List<Int> = emptyList()): EraVariable {
        return variables.getOrPut(name.uppercase()) {
            EraVariable(name.uppercase(), isString, dimensions)
        }
    }

    fun setDefine(name: String, value: String) { defines[name.uppercase()] = value }
    fun getDefine(name: String): String? = defines[name.uppercase()]

    fun allVariables(): Map<String, EraVariable> = variables.toMap()

    fun snapshot(): Map<String, VariableSnapshot> {
        return variables.mapValues { it.value.snapshot() }
    }

    fun restore(snapshot: Map<String, VariableSnapshot>) {
        for ((name, snap) in snapshot) {
            val existing = variables[name]
            if (existing != null) existing.restore(snap)
            else variables[name] = EraVariable(snap.name, snap.isString, snap.dimensions).also {
                it.restore(snap)
            }
        }
    }
}

// ─── Built-in ERA Variables ───────────────────────────────────────────────────
object BuiltinVariables {
    // sparse 방식이므로 dimensions는 범위 검사용으로만 사용됩니다.
    // 선언만 해도 메모리를 잡지 않으므로 크기는 원본 emuera 기준으로 유지합니다.
    val NUMERIC_ARRAYS = mapOf(
        // ── 일반 배열 ──────────────────────────────────────────────────────────
        "FLAG"      to listOf(20000),
        "TFLAG"     to listOf(1000),
        "UP"        to listOf(1000),
        "PALAM"     to listOf(200),
        "PARAM"     to listOf(100),
        "TPALAM"    to listOf(100),
        "STAIN"     to listOf(100),
        "GOTJUEL"   to listOf(200),
        "JUEL"      to listOf(200),
        "NOWEX"     to listOf(100),
        "DOWNBASE"  to listOf(1000),
        "DOWN"      to listOf(1000),
        "LOSEBASE"  to listOf(1000),
        "HAVE"      to listOf(100),
        "ITEM"      to listOf(1000),
        "ITEMSALES" to listOf(1000),
        "BOUGHT"    to listOf(1000),
        "NOITEM"    to listOf(1000),
        // ITEMPRICE는 Item.CSV에서 로드되지만 기본 크기를 보장
        "ITEMPRICE" to listOf(1000),
        "PBAND"     to listOf(1000),
        "ABL"       to listOf(100),
        "TALENT"    to listOf(1000),
        "EXP"       to listOf(100),
        "MARK"      to listOf(100),
        "RELATION"  to listOf(500),
        "EQUIP"     to listOf(100),
        "TEQUIP"    to listOf(100),
        "SOURCE"    to listOf(100),
        "EX"        to listOf(100),
        // CFLAG: CharaData에서 캐릭터별 관리 (10M 배열 OOM 방지)
        "GLOBAL"    to listOf(2000),
        "ASSI"      to listOf(1000),
        "ASSIPLAY"  to listOf(1000),
        "TARGET"    to listOf(1000),
        "PLAYER"    to listOf(1000),
        "MASTER"    to listOf(1000),
        "MONEY"     to listOf(1000),
        "DAY"       to listOf(1000),
        "TIME"      to listOf(1000),
        "LASTLOAD"  to listOf(1),
        "RESULT"    to listOf(1000),
        "COUNT"     to listOf(1000),
        "PALAMLV"   to listOf(1000),
        "EXPLV"     to listOf(1000),
        "EJAC"      to listOf(1000),
        "PREVCOM"   to listOf(1000),
        "NEXTCOM"   to listOf(1000),
        "SELECTCOM" to listOf(1000),
        "CUP"       to listOf(1000),
        "CDOWN"     to listOf(1000),
        // 단일 변수
        "A" to listOf(1000), "B" to listOf(1000), "C" to listOf(1000), "D" to listOf(1000),
        "E" to listOf(1000), "F" to listOf(1000), "G" to listOf(1000), "H" to listOf(1000),
        "I" to listOf(1000), "J" to listOf(1000), "K" to listOf(1000), "L" to listOf(1000),
        "M" to listOf(1000), "N" to listOf(1000), "O" to listOf(1000), "P" to listOf(1000),
        "Q" to listOf(1000), "R" to listOf(1000), "S" to listOf(1000), "T" to listOf(1000),
        "U" to listOf(1000), "V" to listOf(1000), "W" to listOf(1000), "X" to listOf(1000),
        "Y" to listOf(1000), "Z" to listOf(1000),
        // 로컬/인수
        "LOCAL"     to listOf(1000),
        "ARG"       to listOf(1000),
        // 특수
        "RAND"      to listOf(1),
        "CHARANUM"  to listOf(1),
        "PREVLABEL" to listOf(1),
        // 2차원 배열 — sparse이므로 선언만 해도 메모리 안 씀
        "DITEMTYPE" to listOf(1000, 1000),
        // 캐릭터 변수 (TCVAR 등은 CharaData에서 관리)
        "TCVAR"     to listOf(500),
        // 기타 게임 전용 변수
        "BASE"      to listOf(100),
        "MAXBASE"   to listOf(100),
    )

    val STRING_ARRAYS = mapOf(
        // 조교 커맨드 이름 변수 (문자열)
        "COM_NAME"          to listOf(1),
        "COM_TOOL_NAME"     to listOf(1),
        "COM_TOOL_CALLNAME" to listOf(1),
        // 캐릭터 문자열 변수
        "NAME"       to listOf(1000),
        "CALLNAME"   to listOf(1000),
        "NICKNAME"   to listOf(1000),
        "MASTERNAME" to listOf(1000),
        "CSTR"       to listOf(1000, 100),
        // 일반 문자열 배열
        "RESULTS"    to listOf(100),
        "SAVESTR"    to listOf(100),
        "TSTR"       to listOf(400),
        "GLOBALS"    to listOf(100),
        "STR"        to listOf(12000),
        // ITEMNAME은 Item.CSV에서 로드되지만 기본 크기를 보장
        "ITEMNAME"   to listOf(1000),
        // 이름 테이블 — CSV에서 로드되며 반드시 문자열 변수로 선언되어 있어야 함.
        // 미선언 시 scope.getOrCreate("ABLNAME")가 isString=false 숫자 변수로 잘못 생성되어
        // 저장된 이름이 공백(0→"")으로 읽히는 버그가 발생함.
        "ABLNAME"    to listOf(1000),
        "TALENTNAME" to listOf(1000),
        "EXPNAME"    to listOf(1000),
        "MARKNAME"   to listOf(1000),
        "PALAMNAME"  to listOf(1000),
        "TRAINNAME"  to listOf(1000),
        "FLAGNAME"   to listOf(20000),
        "TFLAGNAME"  to listOf(1000),
        "CFLAGNAME"  to listOf(1000),
        "BASENAME"   to listOf(100),
        "STAINNAME"  to listOf(100),
        "SOURCENAME" to listOf(100),
        "EXNAME"     to listOf(100),
        "TEQUIPNAME" to listOf(100),
        "EQUIPNAME"  to listOf(100),
        "NOWEXNAME"  to listOf(100),
        // 로컬/인수 문자열
        "LOCALS"     to listOf(100),
        "ARGS"       to listOf(100),
    )

    fun createDefaultScope(): VariableScope {
        val scope = VariableScope()
        for ((name, dims) in NUMERIC_ARRAYS) {
            scope.define(name, EraVariable(name, false, dims))
        }
        for ((name, dims) in STRING_ARRAYS) {
            scope.define(name, EraVariable(name, true, dims))
        }
        return scope
    }
}

class EraRuntimeException(message: String, val line: Int = -1) : Exception(message)