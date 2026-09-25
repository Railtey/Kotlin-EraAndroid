package com.eraandroid.emuera.sub

import java.io.File

/**
 * Windows filesystems are case-insensitive and eramaker games rely on that
 * (e.g. "ABL.CSV" vs "Abl.csv"). Android/Linux are case-sensitive, so resolve names manually.
 */
object FileUtil {
    private val dirCache = HashMap<String, Map<String, String>>()

    @Synchronized
    fun clearCache() = dirCache.clear()

    /** Resolve a path like "C:/game/csv/ABL.CSV" (or with '\' separators) case-insensitively. Returns the real file (may not exist). */
    fun resolve(path: String): File {
        val norm = path.replace('\\', '/')
        val f = File(norm)
        if (f.exists()) return f
        val parts = norm.split('/')
        var cur = if (norm.startsWith("/")) File("/") else File(".")
        for ((i, p) in parts.withIndex()) {
            if (p.isEmpty() || p == ".") continue
            if (p == "..") { cur = cur.parentFile ?: cur; continue }
            val direct = File(cur, p)
            if (direct.exists()) { cur = direct; continue }
            val match = listing(cur)[p.lowercase()]
            if (match == null) {
                // build the rest verbatim
                var rest = direct
                for (q in parts.drop(i + 1)) if (q.isNotEmpty()) rest = File(rest, q)
                return rest
            }
            cur = File(cur, match)
        }
        return cur
    }

    fun exists(path: String): Boolean = resolve(path).exists()
    fun isFile(path: String): Boolean = resolve(path).isFile
    fun isDirectory(path: String): Boolean = resolve(path).isDirectory

    @Synchronized
    private fun listing(dir: File): Map<String, String> {
        val key = dir.absolutePath
        dirCache[key]?.let { return it }
        val names = dir.list() ?: emptyArray()
        val m = HashMap<String, String>()
        for (n in names) m.putIfAbsent(n.lowercase(), n)
        dirCache[key] = m
        return m
    }
}
