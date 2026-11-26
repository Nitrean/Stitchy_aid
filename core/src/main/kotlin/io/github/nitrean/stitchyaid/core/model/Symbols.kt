package io.github.nitrean.stitchyaid.core.model

/** Chart symbols chosen to be easy to tell apart, even at small sizes and with poor eyesight. */
object Symbols {
    val DEFAULT: List<String> = (
        "● ■ ▲ ◆ ★ ♥ ✚ ✖ ○ □ △ ◇ ☆ ♡ ♣ ♠ ▼ ◀ ▶ ⬟ ⬢ ✿ ☀ ☾ ♪ ⚑ ⌂ ∞ Ω Σ" +
            " A B C D E F G H K L M N P R S T U V W X Y Z 2 3 4 5 6 7 8 9" +
            " a b d e f g h k m n p q r s t u v w x y z % & @ # = + ? ! $ ¥ £ € § ¶"
        ).split(' ').filter { it.isNotBlank() }

    /** Returns [count] distinct symbols, skipping any in [used]. */
    fun pick(count: Int, used: Set<String> = emptySet()): List<String> {
        val result = mutableListOf<String>()
        for (s in DEFAULT) {
            if (result.size == count) break
            if (s !in used) result += s
        }
        var n = 1
        while (result.size < count) result += "#${n++}"
        return result
    }
}
