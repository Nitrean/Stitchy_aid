package io.github.nitrean.stitchyaid.core.threads

import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * Estimates how many skeins of thread a pattern needs.
 *
 * A full cross uses two diagonals on the front and roughly two cell sides on the
 * back, about 4.83 cell widths of thread per strand. A standard skein is 8 m of
 * six strands, so 48 m of single strand.
 */
object SkeinCalculator {
    private const val SKEIN_SINGLE_STRAND_CM = 8_00.0 * 6
    private val THREAD_PER_STITCH = 2 * sqrt(2.0) + 2

    /**
     * @param stitches number of full stitches in this colour
     * @param fabricCount stitches per inch (14 for 14-count Aida)
     * @param strands strands used together
     * @param overTwo true when stitching over two threads of evenweave/linen
     * @param waste extra for starting, ending and travelling, 1.25 = 25 % extra
     */
    fun skeins(stitches: Int, fabricCount: Int, strands: Int, overTwo: Boolean = false, waste: Double = 1.25): Int {
        if (stitches <= 0) return 0
        val stitchesPerInch = if (overTwo) fabricCount / 2.0 else fabricCount.toDouble()
        val cellCm = 2.54 / stitchesPerInch
        val cm = stitches * THREAD_PER_STITCH * cellCm * strands * waste
        return ceil(cm / SKEIN_SINGLE_STRAND_CM).toInt().coerceAtLeast(1)
    }

    /** Finished size in centimetres for a chart of [stitchesWide] x [stitchesHigh]. */
    fun finishedSizeCm(stitchesWide: Int, stitchesHigh: Int, fabricCount: Int, overTwo: Boolean = false): Pair<Double, Double> {
        val perInch = if (overTwo) fabricCount / 2.0 else fabricCount.toDouble()
        return stitchesWide / perInch * 2.54 to stitchesHigh / perInch * 2.54
    }
}
