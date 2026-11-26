package io.github.nitrean.stitchyaid.ui.chart

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import kotlin.math.floor
import kotlin.math.min

/**
 * Where the chart is on screen. [scale] is the size of one cell in pixels and
 * [offsetX]/[offsetY] place the chart's top-left corner. The chart can never be
 * pushed fully off screen, so people can't lose it.
 */
class ChartViewport(val columns: Int, val rows: Int, private val density: Float) {
    var scale by mutableFloatStateOf(10f)
        private set
    var offsetX by mutableFloatStateOf(0f)
        private set
    var offsetY by mutableFloatStateOf(0f)
        private set
    var size by mutableStateOf(IntSize.Zero)
        private set

    val maxScale: Float get() = MAX_CELL_DP * density
    val minScale: Float get() = min(fitScale() * 0.6f, maxScale)

    /** Cell size in dp, which is what decides if a tap marks a stitch or zooms in. */
    val cellDp: Float get() = scale / density

    fun resize(newSize: IntSize, restoreCenterX: Float, restoreCenterY: Float, restoreCellDp: Float) {
        val first = size == IntSize.Zero
        val keepCenter = if (first) null else centerCell()
        size = newSize
        if (newSize.width == 0 || newSize.height == 0) return
        if (first) {
            if (restoreCellDp > 0f && restoreCenterX >= 0f) {
                scale = (restoreCellDp * density).coerceIn(minScale, maxScale)
                centerOn(restoreCenterX, restoreCenterY)
            } else {
                fit()
            }
        } else if (keepCenter != null) {
            // Folding or rotating: keep the same stitches in the middle.
            scale = scale.coerceIn(minScale, maxScale)
            centerOn(keepCenter.x, keepCenter.y)
        }
    }

    private fun fitScale(): Float {
        if (size.width == 0) return 10f
        return min(size.width.toFloat() / columns, size.height.toFloat() / rows) * 0.94f
    }

    /** Shows the whole pattern. */
    fun fit() {
        scale = fitScale().coerceAtMost(maxScale)
        offsetX = (size.width - columns * scale) / 2f
        offsetY = (size.height - rows * scale) / 2f
    }

    fun centerCell(): Offset = Offset((size.width / 2f - offsetX) / scale, (size.height / 2f - offsetY) / scale)

    fun centerOn(cellX: Float, cellY: Float) {
        offsetX = size.width / 2f - cellX * scale
        offsetY = size.height / 2f - cellY * scale
        clamp()
    }

    fun zoomBy(factor: Float, focus: Offset = Offset(size.width / 2f, size.height / 2f)) {
        val newScale = (scale * factor).coerceIn(minScale, maxScale)
        val k = newScale / scale
        offsetX = focus.x - (focus.x - offsetX) * k
        offsetY = focus.y - (focus.y - offsetY) * k
        scale = newScale
        clamp()
    }

    /** Zooms so one cell is [cellDp] big, centred on the given cell. */
    fun zoomToCell(cellX: Float, cellY: Float, cellDp: Float) {
        scale = (cellDp * density).coerceIn(minScale, maxScale)
        centerOn(cellX, cellY)
    }

    fun pan(delta: Offset) {
        offsetX += delta.x
        offsetY += delta.y
        clamp()
    }

    private fun clamp() {
        val w = columns * scale
        val h = rows * scale
        // At least half the screen (or the whole chart, if smaller) always shows some of the chart.
        val keepX = min(w, size.width / 2f)
        val keepY = min(h, size.height / 2f)
        offsetX = offsetX.coerceIn(keepX - w, size.width - keepX)
        offsetY = offsetY.coerceIn(keepY - h, size.height - keepY)
    }

    /** The cell under a screen position, or null if it's outside the chart. */
    fun cellAt(pos: Offset): Pair<Int, Int>? {
        val x = floor((pos.x - offsetX) / scale).toInt()
        val y = floor((pos.y - offsetY) / scale).toInt()
        return if (x in 0 until columns && y in 0 until rows) x to y else null
    }

    companion object {
        const val MAX_CELL_DP = 120f
    }
}
