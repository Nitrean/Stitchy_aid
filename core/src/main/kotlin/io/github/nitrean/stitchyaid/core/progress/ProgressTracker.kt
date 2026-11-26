package io.github.nitrean.stitchyaid.core.progress

import io.github.nitrean.stitchyaid.core.model.Pattern
import io.github.nitrean.stitchyaid.core.model.Project

/** Counts for one colour: how many stitches in total and how many are done. */
data class ColorProgress(val color: Int, val total: Int, val done: Int) {
    val remaining: Int get() = total - done
    val isFinished: Boolean get() = total > 0 && done >= total
}

/**
 * Keeps track of which stitches are finished, with unlimited undo.
 * Every change is one step that Undo reverses as a whole.
 */
class ProgressTracker(val pattern: Pattern, doneCells: LongArray = LongArray(0), doneBackstitches: LongArray = LongArray(0)) {
    private val done = CellSet(doneCells)
    private val doneBack = CellSet(doneBackstitches)
    private val totals = pattern.stitchCounts()
    private val doneCounts = IntArray(pattern.palette.size)
    private val undoStack = ArrayDeque<Step>()
    private val redoStack = ArrayDeque<Step>()
    private val recent = ArrayDeque<Int>()

    /** One user action: the cells it changed and the value each had before. */
    private class Step(val cells: IntArray, val before: BooleanArray, val backstitch: Boolean)

    init {
        done.forEach { i ->
            val c = pattern.cells.getOrElse(i) { Pattern.EMPTY }
            if (c >= 0) doneCounts[c]++
        }
    }

    constructor(project: Project) : this(project.pattern, project.doneCells, project.doneBackstitches) {
        recent.addAll(project.recentCells)
    }

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun isDone(cell: Int): Boolean = cell in done
    fun isDone(x: Int, y: Int): Boolean = pattern.index(x, y) in done
    fun isBackstitchDone(i: Int): Boolean = i in doneBack

    /** Marks or unmarks [cells] as one undoable step. Empty cells are ignored. Returns true if anything changed. */
    fun mark(cells: IntArray, value: Boolean): Boolean {
        val changed = cells.filter { pattern.cells.getOrElse(it) { Pattern.EMPTY } >= 0 && isDone(it) != value }.distinct()
        if (changed.isEmpty()) return false
        val step = Step(changed.toIntArray(), BooleanArray(changed.size) { !value }, backstitch = false)
        apply(step, forward = true)
        undoStack.addLast(step)
        redoStack.clear()
        return true
    }

    /** Flips one cell: done becomes not done and the other way round. */
    fun toggle(cell: Int): Boolean = mark(intArrayOf(cell), !isDone(cell))

    fun toggleBackstitch(i: Int): Boolean {
        if (i !in pattern.backstitches.indices) return false
        val step = Step(intArrayOf(i), booleanArrayOf(isBackstitchDone(i)), backstitch = true)
        apply(step, forward = true)
        undoStack.addLast(step)
        redoStack.clear()
        return true
    }

    fun undo(): Boolean {
        val step = undoStack.removeLastOrNull() ?: return false
        apply(step, forward = false)
        redoStack.addLast(step)
        return true
    }

    fun redo(): Boolean {
        val step = redoStack.removeLastOrNull() ?: return false
        apply(step, forward = true)
        undoStack.addLast(step)
        return true
    }

    private fun apply(step: Step, forward: Boolean) {
        for (k in step.cells.indices) {
            val i = step.cells[k]
            val value = if (forward) !step.before[k] else step.before[k]
            if (step.backstitch) {
                doneBack.set(i, value)
                continue
            }
            if (done.set(i, value)) {
                val c = pattern.cells[i]
                doneCounts[c] += if (value) 1 else -1
            }
            if (value && forward) {
                recent.remove(i)
                recent.addLast(i)
                while (recent.size > MAX_RECENT) recent.removeFirst()
            } else if (!value) {
                recent.remove(i)
            }
        }
    }

    val totalStitches: Int get() = totals.sum()
    val doneStitches: Int get() = doneCounts.sum()
    val fractionDone: Float get() = if (totalStitches == 0) 0f else doneStitches.toFloat() / totalStitches

    fun colorProgress(): List<ColorProgress> = totals.indices.map { ColorProgress(it, totals[it], doneCounts[it]) }

    fun colorProgress(color: Int): ColorProgress = ColorProgress(color, totals[color], doneCounts[color])

    /** Cells finished most recently, newest last. */
    val recentCells: List<Int> get() = recent.toList()

    /** The colour of the most recently finished stitch, or -1. */
    val lastColor: Int get() = recent.lastOrNull()?.let { pattern.cells[it] } ?: -1

    fun doneWords(): LongArray = done.toWords()
    fun doneBackstitchWords(): LongArray = doneBack.toWords()

    fun forEachDone(action: (Int) -> Unit) = done.forEach(action)

    /** Copies the current progress into [project]. */
    fun writeTo(project: Project, now: Long): Project = project.copy(
        doneCells = doneWords(),
        doneBackstitches = doneBackstitchWords(),
        recentCells = recentCells,
        lastColor = if (lastColor >= 0) lastColor else project.lastColor,
        updatedAt = now,
    )

    companion object {
        const val MAX_RECENT = 50
    }
}
