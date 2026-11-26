package io.github.nitrean.stitchyaid.core.model

import kotlinx.serialization.Serializable

/** One embroidery thread colour, for example DMC 310 "Black". [rgb] is 0xRRGGBB. */
@Serializable
data class ThreadColor(
    val brand: String,
    val code: String,
    val name: String,
    val rgb: Int,
) {
    /** Short label people recognise, like "DMC 310". */
    val label: String get() = if (brand.isBlank()) code else "$brand $code"
}

/** A colour used in one pattern, with the symbol printed on the chart. */
@Serializable
data class PaletteEntry(
    val thread: ThreadColor,
    val symbol: String,
    val strands: Int = 2,
)

/** Which part of a cell a fractional stitch covers. */
@Serializable
enum class PartialKind {
    /** Half stitch from bottom-left to top-right ("/"). */
    HALF_FORWARD,
    /** Half stitch from top-left to bottom-right ("\"). */
    HALF_BACK,
    QUARTER_TOP_LEFT,
    QUARTER_TOP_RIGHT,
    QUARTER_BOTTOM_LEFT,
    QUARTER_BOTTOM_RIGHT,
}

@Serializable
data class PartialStitch(val x: Int, val y: Int, val kind: PartialKind, val color: Int)

/**
 * A backstitch line between two grid points. Coordinates are in cells and can be
 * halves (0.5) for lines that start in the middle of a cell.
 */
@Serializable
data class Backstitch(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val color: Int,
    val strands: Int = 1,
)

@Serializable
enum class KnotKind { FRENCH_KNOT, BEAD }

@Serializable
data class Knot(val x: Float, val y: Float, val color: Int, val kind: KnotKind = KnotKind.FRENCH_KNOT)

/**
 * A complete chart. [cells] holds one palette index per full stitch, row by row,
 * or [EMPTY] where nothing is stitched.
 */
@Serializable
class Pattern(
    val title: String,
    val width: Int,
    val height: Int,
    val palette: List<PaletteEntry>,
    val cells: IntArray,
    val partials: List<PartialStitch> = emptyList(),
    val backstitches: List<Backstitch> = emptyList(),
    val knots: List<Knot> = emptyList(),
    val designer: String = "",
    val fabricCount: Int = 14,
    val fabricColor: Int = 0xFFFFFF,
    val notes: String = "",
) {
    init {
        require(width > 0 && height > 0) { "Pattern size must be positive" }
        require(cells.size == width * height) { "Expected ${width * height} cells, got ${cells.size}" }
    }

    fun index(x: Int, y: Int): Int = y * width + x
    fun colorAt(x: Int, y: Int): Int = cells[index(x, y)]
    fun inBounds(x: Int, y: Int): Boolean = x in 0 until width && y in 0 until height

    /** Number of full stitches of each palette colour. */
    fun stitchCounts(): IntArray {
        val counts = IntArray(palette.size)
        for (c in cells) if (c >= 0) counts[c]++
        return counts
    }

    val totalStitches: Int get() = cells.count { it >= 0 }

    fun copy(
        title: String = this.title,
        palette: List<PaletteEntry> = this.palette,
        cells: IntArray = this.cells,
        notes: String = this.notes,
    ) = Pattern(title, width, height, palette, cells, partials, backstitches, knots, designer, fabricCount, fabricColor, notes)

    companion object {
        const val EMPTY = -1
    }
}
