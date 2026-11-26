package io.github.nitrean.stitchyaid.ui.chart

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import io.github.nitrean.stitchyaid.core.color.ColorMath
import io.github.nitrean.stitchyaid.core.model.KnotKind
import io.github.nitrean.stitchyaid.core.model.PartialKind
import io.github.nitrean.stitchyaid.core.model.Pattern
import io.github.nitrean.stitchyaid.core.progress.ProgressTracker
import io.github.nitrean.stitchyaid.ui.ChartSession
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Pre-computed colours for drawing one pattern quickly. */
private class ChartPalette(pattern: Pattern) {
    /** Finished stitches: the real thread colour. */
    val done = pattern.palette.map { Color(0xFF000000.toInt() or it.thread.rgb) }
    /** Not yet stitched: a pale version, so the symbol stands out. */
    val todo = pattern.palette.map { Color(0xFF000000.toInt() or ColorMath.tint(it.thread.rgb, 0.55f)) }
    val todoArgb = pattern.palette.map { 0xFF000000.toInt() or ColorMath.tint(it.thread.rgb, 0.55f) }
    val doneArgb = pattern.palette.map { 0xFF000000.toInt() or it.thread.rgb }
    /** Symbol colour that reads best on the pale background. */
    val symbolColor = pattern.palette.map {
        if (ColorMath.luminance(ColorMath.tint(it.thread.rgb, 0.55f)) > 0.35) android.graphics.Color.BLACK else android.graphics.Color.WHITE
    }
    val fabric = Color(0xFF000000.toInt() or pattern.fabricColor)
    val fabricArgb = 0xFF000000.toInt() or pattern.fabricColor
}

/** A 1-pixel-per-stitch picture of the whole chart, used when zoomed far out. */
private class Overview(val pattern: Pattern) {
    var bitmap: Bitmap? = null
    var image: ImageBitmap? = null
    var version = -1
    var highlight = -2

    fun update(tracker: ProgressTracker, palette: ChartPalette, version: Int, highlight: Int): ImageBitmap {
        if (image != null && this.version == version && this.highlight == highlight) return image!!
        val px = IntArray(pattern.width * pattern.height)
        for (i in px.indices) {
            val c = pattern.cells[i]
            px[i] = when {
                c < 0 -> palette.fabricArgb
                highlight >= 0 && c != highlight -> DIM_ARGB
                tracker.isDone(i) -> palette.doneArgb[c]
                else -> palette.todoArgb[c]
            }
        }
        val bmp = bitmap ?: Bitmap.createBitmap(pattern.width, pattern.height, Bitmap.Config.ARGB_8888).also { bitmap = it }
        bmp.setPixels(px, 0, pattern.width, 0, 0, pattern.width, pattern.height)
        this.version = version
        this.highlight = highlight
        return bmp.asImageBitmap().also { image = it }
    }

    companion object {
        val DIM_ARGB = 0xFFEDEDED.toInt()
    }
}

private val DIM = Color(0xFFEDEDED)
private val GRID_THIN = Color(0x55000000)
private val GRID_THICK = Color(0xFF222222)
private val RECENT = Color(0xFFE65100)
private val RULER_BG = Color(0xEEFFFFFF)

/**
 * The chart itself: pinch to zoom, drag to move, tap a stitch to mark it done.
 * If stitches are too small to tap accurately, a tap zooms in on that spot instead.
 */
@Composable
fun ChartCanvas(
    session: ChartSession,
    viewport: ChartViewport,
    onTapCell: (Int) -> Unit,
    onViewChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pattern = session.project.pattern
    val palette = remember(pattern) { ChartPalette(pattern) }
    val overview = remember(pattern) { Overview(pattern) }
    val symbolPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD } }
    val rulerPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = android.graphics.Color.BLACK; typeface = Typeface.DEFAULT_BOLD } }
    val locked = session.locked

    Canvas(
        modifier
            .onSizeChanged { size ->
                val v = session.view
                viewport.resize(size, v.centerX, v.centerY, v.cellSizeDp)
            }
            .pointerInput(locked) {
                if (!locked) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        if (zoom != 1f) viewport.zoomBy(zoom, centroid)
                        viewport.pan(pan)
                        onViewChanged()
                    }
                }
            }
            .pointerInput(pattern) {
                detectTapGestures { pos ->
                    val cell = viewport.cellAt(pos) ?: return@detectTapGestures
                    if (viewport.cellDp < MIN_TAP_CELL_DP) {
                        // Too small to hit the right stitch with a finger: bring it closer first.
                        viewport.zoomToCell(cell.first + 0.5f, cell.second + 0.5f, COMFORTABLE_CELL_DP)
                        onViewChanged()
                    } else {
                        onTapCell(pattern.index(cell.first, cell.second))
                    }
                }
            }
            .semantics { contentDescription = "Pattern chart. Tap a stitch to mark it done. Pinch to zoom." },
    ) {
        // Reading these makes the canvas redraw when they change.
        val version = session.version
        val highlight = session.highlight
        val scale = viewport.scale
        val ox = viewport.offsetX
        val oy = viewport.offsetY
        val tracker = session.tracker

        val x0 = max(0, floor(-ox / scale).toInt())
        val y0 = max(0, floor(-oy / scale).toInt())
        val x1 = min(pattern.width - 1, ceil((size.width - ox) / scale).toInt())
        val y1 = min(pattern.height - 1, ceil((size.height - oy) / scale).toInt())
        if (x1 < x0 || y1 < y0) return@Canvas

        // Fabric behind the chart.
        drawRect(palette.fabric, Offset(ox, oy), Size(pattern.width * scale, pattern.height * scale))

        val visible = (x1 - x0 + 1).toLong() * (y1 - y0 + 1)
        if (visible > MAX_RECTS || scale < 3f) {
            val img = overview.update(tracker, palette, version, highlight)
            drawImage(
                img,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(pattern.width, pattern.height),
                dstOffset = IntOffset(ox.toInt(), oy.toInt()),
                dstSize = IntSize((pattern.width * scale).toInt(), (pattern.height * scale).toInt()),
                filterQuality = FilterQuality.None,
            )
        } else {
            drawCells(pattern, tracker, palette, highlight, scale, ox, oy, x0, y0, x1, y1)
            drawPartials(pattern, palette, highlight, scale, ox, oy)
        }

        if (scale >= 6f) drawGrid(pattern, scale, ox, oy, x0, y0, x1, y1)
        drawBackstitches(pattern, palette, highlight, scale, ox, oy)

        val showSymbols = scale / density >= SYMBOL_CELL_DP && visible <= MAX_RECTS
        if (showSymbols) {
            symbolPaint.textSize = scale * 0.72f
            val canvas = drawContext.canvas.nativeCanvas
            val baseline = -(symbolPaint.ascent() + symbolPaint.descent()) / 2f
            for (y in y0..y1) for (x in x0..x1) {
                val i = y * pattern.width + x
                val c = pattern.cells[i]
                if (c < 0 || tracker.isDone(i) || (highlight >= 0 && c != highlight)) continue
                symbolPaint.color = palette.symbolColor[c]
                canvas.drawText(pattern.palette[c].symbol, ox + (x + 0.5f) * scale, oy + (y + 0.5f) * scale + baseline, symbolPaint)
            }
        }

        // The last few stitches marked get an orange outline so people can find their place.
        val recent = tracker.recentCells.takeLast(RECENT_OUTLINED)
        if (recent.isNotEmpty()) {
            val stroke = Stroke(width = max(3f, scale * 0.12f))
            for (i in recent) {
                val x = i % pattern.width
                val y = i / pattern.width
                drawRect(RECENT, Offset(ox + x * scale, oy + y * scale), Size(scale, scale), style = stroke)
            }
        }

        if (scale / density >= 10f) drawRulers(pattern, scale, ox, oy, x0, y0, x1, y1, rulerPaint)
    }
}

private fun DrawScope.drawCells(
    pattern: Pattern, tracker: ProgressTracker, palette: ChartPalette, highlight: Int,
    scale: Float, ox: Float, oy: Float, x0: Int, y0: Int, x1: Int, y1: Int,
) {
    // Slight overlap hides hairline gaps between cells.
    val cell = Size(scale + 0.5f, scale + 0.5f)
    for (y in y0..y1) {
        val top = oy + y * scale
        for (x in x0..x1) {
            val i = y * pattern.width + x
            val c = pattern.cells[i]
            if (c < 0) continue
            val color = when {
                highlight >= 0 && c != highlight -> DIM
                tracker.isDone(i) -> palette.done[c]
                else -> palette.todo[c]
            }
            drawRect(color, Offset(ox + x * scale, top), cell)
        }
    }
}

private fun DrawScope.drawPartials(pattern: Pattern, palette: ChartPalette, highlight: Int, scale: Float, ox: Float, oy: Float) {
    if (pattern.partials.isEmpty()) return
    val half = scale / 2f
    for (p in pattern.partials) {
        val color = if (highlight >= 0 && p.color != highlight) DIM else palette.done[p.color]
        val left = ox + p.x * scale
        val top = oy + p.y * scale
        when (p.kind) {
            PartialKind.HALF_FORWARD -> drawLine(color, Offset(left, top + scale), Offset(left + scale, top), strokeWidth = scale * 0.28f, cap = StrokeCap.Round)
            PartialKind.HALF_BACK -> drawLine(color, Offset(left, top), Offset(left + scale, top + scale), strokeWidth = scale * 0.28f, cap = StrokeCap.Round)
            PartialKind.QUARTER_TOP_LEFT -> drawRect(color, Offset(left, top), Size(half, half))
            PartialKind.QUARTER_TOP_RIGHT -> drawRect(color, Offset(left + half, top), Size(half, half))
            PartialKind.QUARTER_BOTTOM_LEFT -> drawRect(color, Offset(left, top + half), Size(half, half))
            PartialKind.QUARTER_BOTTOM_RIGHT -> drawRect(color, Offset(left + half, top + half), Size(half, half))
        }
    }
}

private fun DrawScope.drawGrid(pattern: Pattern, scale: Float, ox: Float, oy: Float, x0: Int, y0: Int, x1: Int, y1: Int) {
    val top = oy + y0 * scale
    val bottom = oy + (y1 + 1) * scale
    val left = ox + x0 * scale
    val right = ox + (x1 + 1) * scale
    val thin = 1f
    val thick = max(2.5f, density * 1.5f)
    for (x in x0..x1 + 1) {
        val px = ox + x * scale
        val major = x % 10 == 0 || x == pattern.width
        drawLine(if (major) GRID_THICK else GRID_THIN, Offset(px, top), Offset(px, bottom), strokeWidth = if (major) thick else thin)
    }
    for (y in y0..y1 + 1) {
        val py = oy + y * scale
        val major = y % 10 == 0 || y == pattern.height
        drawLine(if (major) GRID_THICK else GRID_THIN, Offset(left, py), Offset(right, py), strokeWidth = if (major) thick else thin)
    }
    // A small mark at the centre of the pattern, as on paper charts.
    val cx = ox + pattern.width / 2f * scale
    val cy = oy + pattern.height / 2f * scale
    val r = max(6f, scale * 0.35f)
    drawLine(RECENT, Offset(cx - r, cy), Offset(cx + r, cy), strokeWidth = thick)
    drawLine(RECENT, Offset(cx, cy - r), Offset(cx, cy + r), strokeWidth = thick)
}

private fun DrawScope.drawBackstitches(pattern: Pattern, palette: ChartPalette, highlight: Int, scale: Float, ox: Float, oy: Float) {
    if (pattern.backstitches.isEmpty() && pattern.knots.isEmpty()) return
    clipRect {
        val width = max(3f, scale * 0.14f)
        for (b in pattern.backstitches) {
            val color = if (highlight >= 0 && b.color != highlight) DIM else palette.done[b.color]
            drawLine(color, Offset(ox + b.x1 * scale, oy + b.y1 * scale), Offset(ox + b.x2 * scale, oy + b.y2 * scale), strokeWidth = width, cap = StrokeCap.Round)
        }
        for (k in pattern.knots) {
            val color = if (highlight >= 0 && k.color != highlight) DIM else palette.done[k.color]
            val center = Offset(ox + k.x * scale, oy + k.y * scale)
            val radius = max(3f, scale * if (k.kind == KnotKind.BEAD) 0.3f else 0.22f)
            drawCircle(color, radius, center)
            drawCircle(Color.Black, radius, center, style = Stroke(1.5f))
        }
    }
}

/** Row and column numbers every 10 stitches, along the top and left edges of the screen. */
private fun DrawScope.drawRulers(pattern: Pattern, scale: Float, ox: Float, oy: Float, x0: Int, y0: Int, x1: Int, y1: Int, paint: Paint) {
    val band = 30f * density
    paint.textSize = 16f * density
    drawRect(RULER_BG, Offset.Zero, Size(size.width, band))
    drawRect(RULER_BG, Offset.Zero, Size(band, size.height))
    val canvas = drawContext.canvas.nativeCanvas
    val baseline = band / 2f - (paint.ascent() + paint.descent()) / 2f
    var x = ((x0 + 9) / 10) * 10
    while (x <= x1 + 1) {
        val px = ox + x * scale
        if (px > band) canvas.drawText(x.toString(), px, baseline, paint)
        x += 10
    }
    var y = ((y0 + 9) / 10) * 10
    while (y <= y1 + 1) {
        val py = oy + y * scale
        if (py > band) {
            canvas.save()
            canvas.rotate(-90f, band / 2f, py)
            canvas.drawText(y.toString(), band / 2f, py - (paint.ascent() + paint.descent()) / 2f, paint)
            canvas.restore()
        }
        y += 10
    }
}

/** Cells smaller than this are hard to hit with a finger, so a tap zooms in instead of marking. */
const val MIN_TAP_CELL_DP = 18f
const val COMFORTABLE_CELL_DP = 40f
private const val SYMBOL_CELL_DP = 16f
private const val MAX_RECTS = 40_000L
private const val RECENT_OUTLINED = 8

