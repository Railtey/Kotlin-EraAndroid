package com.eraandroid.core.vm

import kotlin.math.min

data class CharaData(
    val no: Int,
    val numVars: MutableMap<String, MutableList<Long>> = mutableMapOf(),
    val strVars: MutableMap<String, MutableList<String>> = mutableMapOf()
) {
    fun getNum(name: String, index: Int = 0): Long =
        numVars[name.uppercase()]?.getOrElse(index) { 0L } ?: 0L

    fun setNum(name: String, index: Int = 0, value: Long) {
        // [변경] MutableList(1000) { 0L } 제거 → 빈 리스트로 시작해서 필요한 만큼만 늘어남
        val list = numVars.getOrPut(name.uppercase()) { mutableListOf() }
        if (index >= list.size) {
            repeat(index - list.size + 1) { list.add(0L) }
        }
        list[index] = value
    }

    fun getStr(name: String, index: Int = 0): String =
        strVars[name.uppercase()]?.getOrElse(index) { "" } ?: ""

    fun setStr(name: String, index: Int = 0, value: String) {
        // [변경] MutableList(100) { "" } 제거 → 빈 리스트로 시작해서 필요한 만큼만 늘어남
        val list = strVars.getOrPut(name.uppercase()) { mutableListOf() }
        if (index >= list.size) {
            repeat(index - list.size + 1) { list.add("") }
        }
        list[index] = value
    }

    fun copy(): CharaData {
        val newChara = CharaData(no)
        for ((k, v) in numVars) newChara.numVars[k] = v.toMutableList()
        for ((k, v) in strVars) newChara.strVars[k] = v.toMutableList()
        return newChara
    }

    fun snapshot(): CharaSnapshot = CharaSnapshot(no, numVars.toMap(), strVars.toMap())
}

data class CharaSnapshot(
    val no: Int,
    val numVars: Map<String, List<Long>>,
    val strVars: Map<String, List<String>>
)

class CharaManager {
    val charas = mutableListOf<CharaData>()
    private val charaTemplates = mutableMapOf<Int, CharaData>()

    // _Replace.csv의 「汚れの初期値」에서 로드 — 캐릭터 생성 시 STAIN 초기값으로 사용
    var stainDefaults: List<Long> = emptyList()

    fun registerTemplate(chara: CharaData) {
        charaTemplates[chara.no] = chara
    }

    fun addChara(no: Int): Boolean {
        if (charas.any { it.no == no }) return false
        val template = charaTemplates[no] ?: CharaData(no)
        val newChara = template.copy()
        // _Replace.csv의 STAIN 초기값 적용 (템플릿에 명시된 값이 없는 인덱스만)
        stainDefaults.forEachIndexed { idx, v ->
            if (v != 0L && newChara.getNum("STAIN", idx) == 0L) {
                newChara.setNum("STAIN", idx, v)
            }
        }
        charas.add(newChara)
        return true
    }

    fun addAllTemplates() {
        // ADDDEFCHARA 인수 없을 때: no=0 주인 캐릭터만 추가 (게임 시작 시 필요한 것만)
        addChara(0)
    }

    fun addCopyChara(no: Int): Boolean {
        val existing = charas.firstOrNull { it.no == no } ?: return false
        charas.add(existing.copy())
        return true
    }

    // emuera 원본: DELCHARA는 배열 인덱스 기반
    fun delChara(index: Int) {
        if (index in charas.indices) charas.removeAt(index)
    }

    // 번호(no) 기반 삭제 - 내부/특수 용도
    fun delCharaByNo(no: Int) {
        charas.removeAll { it.no == no }
    }

    fun swapChara(indexA: Int, indexB: Int) {
        if (indexA in charas.indices && indexB in charas.indices) {
            val tmp = charas[indexA]
            charas[indexA] = charas[indexB]
            charas[indexB] = tmp
        }
    }

    fun sortChara(varName: String, index: Int = 0, ascending: Boolean = true) {
        if (ascending) {
            charas.sortBy { it.getNum(varName, index) }
        } else {
            charas.sortByDescending { it.getNum(varName, index) }
        }
    }

    fun pickupChara(target: Long) {
        val targetChara = charas.firstOrNull { it.no == target.toInt() } ?: return
        charas.remove(targetChara)
        charas.add(0, targetChara)
    }

    fun getChara(index: Int): CharaData? = charas.getOrNull(index)
    fun getCharaByNo(no: Int): CharaData? = charas.firstOrNull { it.no == no }
    // CSV 데이터 조회 - charas에 없으면 template에서 (구입 전 캐릭터 정보)
    fun getCharaOrTemplate(no: Int): CharaData? = charas.firstOrNull { it.no == no } ?: charaTemplates[no]
    val count: Int get() = charas.size

    // Chara variable access for CFLAG:chara:index style
    fun getNum(charaIndex: Int, varName: String, index: Int = 0): Long =
        charas.getOrNull(charaIndex)?.getNum(varName, index) ?: 0L

    fun setNum(charaIndex: Int, varName: String, index: Int = 0, value: Long) {
        charas.getOrNull(charaIndex)?.setNum(varName, index, value)
    }

    fun getStr(charaIndex: Int, varName: String, index: Int = 0): String =
        charas.getOrNull(charaIndex)?.getStr(varName, index) ?: ""

    fun setStr(charaIndex: Int, varName: String, index: Int = 0, value: String) {
        charas.getOrNull(charaIndex)?.setStr(varName, index, value)
    }

    fun snapshot(): List<CharaSnapshot> = charas.map { it.snapshot() }

    fun restore(snapshots: List<CharaSnapshot>) {
        charas.clear()
        for (snap in snapshots) {
            val chara = CharaData(snap.no)
            for ((k, v) in snap.numVars) chara.numVars[k] = v.toMutableList()
            for ((k, v) in snap.strVars) chara.strVars[k] = v.toMutableList()
            charas.add(chara)
        }
    }
}