package com.eraandroid.emuera.sub

/*
 * SFMT (SIMD-oriented Fast Mersenne Twister) MT19937 pseudo random generator.
 * Port of Emuera's _Library/SFMT.cs, which is based on Rei HOBARA's C# SFMT library
 * (Copyright (C) Rei HOBARA 2007). SFMT by Mutsuo Saito and Makoto Matsumoto.
 */
class MTRandom(seed: Long = System.currentTimeMillis()) {
    private val sfmt = IntArray(N32)
    private var idx = 0

    init { initGenRand(seed.toInt()) }

    /** maxが2^nでない大きい値であると値が偏る。 */
    fun nextInt64(max: Long): Long {
        if (max <= 0) throw IllegalArgumentException()
        return java.lang.Long.remainderUnsigned(nextUInt64(), max)
    }

    fun nextInt64(): Long = nextUInt64()

    /** Unsigned 64-bit value in a Long */
    fun nextUInt64(): Long {
        var ret = nextUInt32().toLong() and 0xFFFFFFFFL
        ret = (ret shl 32) + (nextUInt32().toLong() and 0xFFFFFFFFL)
        return ret
    }

    /** [0,1) */
    fun nextDouble(): Double = (nextUInt32().toLong() and 0xFFFFFFFFL).toDouble() * (1.0 / 4294967296.0)

    fun setRand(array: LongArray) {
        if (array.size != N32 + 1) throw IllegalArgumentException()
        for (i in 0 until N32) sfmt[i] = array[i].toInt()
        idx = array[N32].toInt()
    }

    fun getRand(array: LongArray) {
        if (array.size != N32 + 1) throw IllegalArgumentException()
        for (i in 0 until N32) array[i] = sfmt[i].toLong() and 0xFFFFFFFFL
        array[N32] = idx.toLong()
    }

    private fun nextUInt32(): Int {
        if (idx >= N32) { genRandAll(); idx = 0 }
        return sfmt[idx++]
    }

    private fun initGenRand(seed: Int) {
        sfmt[0] = seed
        for (i in 1 until N32) sfmt[i] = 1812433253 * (sfmt[i - 1] xor (sfmt[i - 1] ushr 30)) + i
        periodCertification()
        idx = N32
    }

    private fun periodCertification() {
        val parity = intArrayOf(PARITY1, PARITY2, PARITY3, PARITY4)
        var inner = 0
        for (i in 0 until 4) inner = inner xor (sfmt[i] and parity[i])
        var i = 16
        while (i > 0) { inner = inner xor (inner ushr i); i = i shr 1 }
        inner = inner and 1
        if (inner == 1) return
        for (k in 0 until 4) {
            var work = 1
            for (j in 0 until 32) {
                if ((work and parity[k]) != 0) { sfmt[k] = sfmt[k] xor work; return }
                work = work shl 1
            }
        }
    }

    private fun genRandAll() {
        val p = sfmt
        var a = 0
        var b = POS1 * 4
        var c = (N - 2) * 4
        var d = (N - 1) * 4
        do {
            p[a + 3] = p[a + 3] xor (p[a + 3] shl 8) xor (p[a + 2] ushr 24) xor (p[c + 3] ushr 8) xor ((p[b + 3] ushr SR1) and MSK4) xor (p[d + 3] shl SL1)
            p[a + 2] = p[a + 2] xor (p[a + 2] shl 8) xor (p[a + 1] ushr 24) xor (p[c + 3] shl 24) xor (p[c + 2] ushr 8) xor ((p[b + 2] ushr SR1) and MSK3) xor (p[d + 2] shl SL1)
            p[a + 1] = p[a + 1] xor (p[a + 1] shl 8) xor (p[a + 0] ushr 24) xor (p[c + 2] shl 24) xor (p[c + 1] ushr 8) xor ((p[b + 1] ushr SR1) and MSK2) xor (p[d + 1] shl SL1)
            p[a + 0] = p[a + 0] xor (p[a + 0] shl 8) xor (p[c + 1] shl 24) xor (p[c + 0] ushr 8) xor ((p[b + 0] ushr SR1) and MSK1) xor (p[d + 0] shl SL1)
            c = d; d = a; a += 4; b += 4
            if (b >= N32) b = 0
        } while (a < N32)
    }

    companion object {
        private const val MEXP = 19937
        private const val POS1 = 122
        private const val SL1 = 18
        private const val SR1 = 11
        private const val MSK1 = 0xdfffffef.toInt()
        private const val MSK2 = 0xddfecb7f.toInt()
        private const val MSK3 = 0xbffaffff.toInt()
        private const val MSK4 = 0xbffffff6.toInt()
        private const val PARITY1 = 0x00000001
        private const val PARITY2 = 0x00000000
        private const val PARITY3 = 0x00000000
        private const val PARITY4 = 0x13c9e684
        private const val N = MEXP / 128 + 1
        const val N32 = N * 4
    }
}
