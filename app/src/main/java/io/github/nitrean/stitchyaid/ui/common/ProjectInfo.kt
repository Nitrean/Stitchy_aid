package io.github.nitrean.stitchyaid.ui.common

import io.github.nitrean.stitchyaid.core.model.Project
import io.github.nitrean.stitchyaid.core.progress.CellSet

/** Plain-language facts about a project for lists and cards. */
object ProjectInfo {
    fun percentDone(p: Project): Int {
        val total = p.pattern.totalStitches
        if (total == 0) return 0
        val done = CellSet(p.doneCells).count()
        return (done * 100L / total).toInt().coerceIn(0, 100)
    }

    fun fractionDone(p: Project): Float = percentDone(p) / 100f

    fun sizeText(p: Project): String {
        val colours = p.pattern.palette.size
        return "${p.pattern.width} × ${p.pattern.height} stitches, $colours ${if (colours == 1) "colour" else "colours"}"
    }
}
