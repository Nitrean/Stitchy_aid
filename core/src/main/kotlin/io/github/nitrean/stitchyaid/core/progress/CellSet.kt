package io.github.nitrean.stitchyaid.core.progress

/** A compact, growable set of cell indexes stored as bits. */
class CellSet(words: LongArray = LongArray(0)) {
    private var bits: LongArray = words.copyOf()

    operator fun contains(i: Int): Boolean {
        val w = i ushr 6
        return w < bits.size && (bits[w] and (1L shl (i and 63))) != 0L
    }

    /** Sets or clears [i]. Returns true when the value changed. */
    fun set(i: Int, value: Boolean): Boolean {
        if (contains(i) == value) return false
        val w = i ushr 6
        if (w >= bits.size) bits = bits.copyOf(maxOf(w + 1, bits.size * 2))
        bits[w] = if (value) bits[w] or (1L shl (i and 63)) else bits[w] and (1L shl (i and 63)).inv()
        return true
    }

    fun count(): Int = bits.sumOf { java.lang.Long.bitCount(it) }

    fun toWords(): LongArray {
        var last = bits.size - 1
        while (last >= 0 && bits[last] == 0L) last--
        return bits.copyOf(last + 1)
    }

    fun forEach(action: (Int) -> Unit) {
        for (w in bits.indices) {
            var word = bits[w]
            while (word != 0L) {
                val bit = java.lang.Long.numberOfTrailingZeros(word)
                action(w * 64 + bit)
                word = word and (word - 1)
            }
        }
    }
}
