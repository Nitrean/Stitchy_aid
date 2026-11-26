package io.github.nitrean.stitchyaid.core.threads

import io.github.nitrean.stitchyaid.core.color.ColorMath
import io.github.nitrean.stitchyaid.core.color.Lab
import io.github.nitrean.stitchyaid.core.model.ThreadColor

/** A list of threads from one brand, with nearest-colour lookup. */
class ThreadCatalog(val brand: String, val threads: List<ThreadColor>) {
    private val labs: List<Lab> = threads.map { ColorMath.toLab(it.rgb) }
    private val byCode: Map<String, ThreadColor> = threads.associateBy { normalize(it.code) }

    fun find(code: String): ThreadColor? = byCode[normalize(code)]

    /** The thread that looks closest to [rgb]. */
    fun nearest(rgb: Int): ThreadColor = threads[nearestIndex(rgb)]

    fun nearestIndex(rgb: Int, allowed: Collection<Int>? = null): Int {
        val target = ColorMath.toLab(rgb)
        var best = -1
        var bestD = Double.MAX_VALUE
        val candidates = allowed ?: threads.indices
        for (i in candidates) {
            val d = ColorMath.deltaE2000(target, labs[i])
            if (d < bestD) { bestD = d; best = i }
        }
        return best
    }

    companion object {
        private fun normalize(code: String) = code.trim().lowercase().removePrefix("dmc").trim()

        /** DMC six-strand embroidery floss (454 colours). */
        val DMC: ThreadCatalog by lazy { loadCsv("DMC", "/threads/dmc.csv") }

        private fun loadCsv(brand: String, resource: String): ThreadCatalog {
            val stream = ThreadCatalog::class.java.getResourceAsStream(resource)
                ?: error("Missing thread list $resource")
            val threads = stream.bufferedReader().useLines { lines ->
                lines.drop(1).mapNotNull { line ->
                    // Floss#,Description,Red,Green,Blue,RGB code,Row
                    val parts = line.split(',')
                    if (parts.size < 5) return@mapNotNull null
                    val r = parts[2].trim().toIntOrNull() ?: return@mapNotNull null
                    val g = parts[3].trim().toIntOrNull() ?: return@mapNotNull null
                    val b = parts[4].trim().toIntOrNull() ?: return@mapNotNull null
                    ThreadColor(brand, parts[0].trim(), parts[1].trim(), ColorMath.rgb(r, g, b))
                }.toList()
            }
            return ThreadCatalog(brand, threads)
        }
    }
}
