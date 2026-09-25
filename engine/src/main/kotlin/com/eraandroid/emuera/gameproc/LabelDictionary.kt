package com.eraandroid.emuera.gameproc

import com.eraandroid.emuera.GlobalStatic
import com.eraandroid.emuera.config.Config

class LabelDictionary {
    private val labelAtDic = LinkedHashMap<String, MutableList<FunctionLabelLine>>()
    private val invalidList = ArrayList<FunctionLabelLine>()
    private val labelDollarList = ArrayList<GotoLabelLine>()
    private val labelDollarIndex = HashMap<Pair<FunctionLabelLine?, String>, GotoLabelLine>()
    var count = 0
        private set
    private val loadedFileDic = HashMap<String, Int>()
    private var currentFileCount = 0
    private var totalFileCount = 0
    var initialized = false

    fun getSameNameLabel(point: FunctionLabelLine): FunctionLabelLine? {
        val labelList = labelAtDic[point.labelName] ?: return null
        if (point.isError) return null
        if (labelList.size <= 1) return null
        return labelList[0]
    }

    private val eventLabelDic = HashMap<String, Array<MutableList<FunctionLabelLine>>>()
    private val noneventLabelDic = HashMap<String, FunctionLabelLine>()

    fun sortLabels() {
        eventLabelDic.clear()
        noneventLabelDic.clear()
        val dic = GlobalStatic.IdentifierDictionary!!
        for ((key, list) in labelAtDic) {
            if (list.size > 1) list.sort()
            if (!list[0].isEvent) {
                noneventLabelDic[key] = list[0]
                dic.resizeLocalVars("ARG", list[0].labelName, list[0].argLength)
                dic.resizeLocalVars("ARGS", list[0].labelName, list[0].argsLength)
                continue
            }
            if (Config.CompatiCallEvent) noneventLabelDic[key] = list[0]
            val onlylist = ArrayList<FunctionLabelLine>()
            val prilist = ArrayList<FunctionLabelLine>()
            val normallist = ArrayList<FunctionLabelLine>()
            val laterlist = ArrayList<FunctionLabelLine>()
            var localMax = 0
            var localsMax = 0
            for (l in list) {
                if (l.localLength > localMax) localMax = l.localLength
                if (l.localsLength > localsMax) localsMax = l.localsLength
                if (l.isOnly) onlylist.add(l)
                if (l.isPri) prilist.add(l)
                if (l.isLater) laterlist.add(l)
                if (!l.isPri && !l.isLater) normallist.add(l)
            }
            if (localMax < dic.getLocalDefaultSize("LOCAL")) localMax = dic.getLocalDefaultSize("LOCAL")
            if (localsMax < dic.getLocalDefaultSize("LOCALS")) localsMax = dic.getLocalDefaultSize("LOCALS")
            val eventLabels: Array<MutableList<FunctionLabelLine>> = arrayOf(onlylist, prilist, normallist, laterlist)
            for (el in eventLabels) for (l in el) { l.localLength = localMax; l.localsLength = localsMax }
            eventLabelDic[key] = eventLabels
        }
    }

    fun removeAll() {
        initialized = false
        count = 0
        eventLabelDic.clear()
        noneventLabelDic.clear()
        labelAtDic.clear()
        labelDollarList.clear()
        labelDollarIndex.clear()
        loadedFileDic.clear()
        invalidList.clear()
        currentFileCount = 0
        totalFileCount = 0
    }

    fun removeLabelWithPath(fname: String) {
        val removeKey = ArrayList<String>()
        for ((key, labelLines) in labelAtDic) {
            labelLines.removeAll { it.position?.filename.equals(fname, ignoreCase = true) }
            if (labelLines.isEmpty()) removeKey.add(key)
        }
        for (k in removeKey) labelAtDic.remove(k)
        invalidList.removeAll { it.position?.filename.equals(fname, ignoreCase = true) }
    }

    fun addFilename(filename: String) {
        val cur = loadedFileDic[filename]
        if (cur != null) {
            currentFileCount = cur
            removeLabelWithPath(filename)
            return
        }
        totalFileCount++
        currentFileCount = totalFileCount
        loadedFileDic[filename] = totalFileCount
    }

    fun addLabel(point: FunctionLabelLine) {
        point.index = count
        point.fileIndex = currentFileCount
        count++
        labelAtDic.getOrPut(point.labelName) { ArrayList() }.add(point)
    }

    fun addLabelDollar(point: GotoLabelLine): Boolean {
        val key = Pair(point.parentLabelLine, point.labelName)
        if (labelDollarIndex.containsKey(key)) return false
        labelDollarList.add(point)
        labelDollarIndex[key] = point
        return true
    }

    fun getEventLabels(key: String): Array<MutableList<FunctionLabelLine>>? = eventLabelDic[key]
    fun getNonEventLabel(key: String): FunctionLabelLine? = noneventLabelDic[key]

    fun getAllLabels(getInvalidList: Boolean): List<FunctionLabelLine> {
        val ret = ArrayList<FunctionLabelLine>()
        for (list in labelAtDic.values) ret.addAll(list)
        if (getInvalidList) ret.addAll(invalidList)
        return ret
    }

    fun getLabelDollar(key: String, labelAtLine: FunctionLabelLine?): GotoLabelLine? = labelDollarIndex[Pair(labelAtLine, key)]

    fun addInvalidLabel(invalidLabelLine: FunctionLabelLine) { invalidList.add(invalidLabelLine) }
}
