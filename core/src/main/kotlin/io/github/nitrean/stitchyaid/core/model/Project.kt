package io.github.nitrean.stitchyaid.core.model

import kotlinx.serialization.Serializable

/** Everything saved about one pattern the person is stitching. */
@Serializable
data class Project(
    val id: String,
    val pattern: Pattern,
    /** Finished full stitches, as a packed bit set (see [io.github.nitrean.stitchyaid.core.progress.CellSet]). */
    val doneCells: LongArray = LongArray(0),
    /** Finished backstitch lines, by index into [Pattern.backstitches]. */
    val doneBackstitches: LongArray = LongArray(0),
    val createdAt: Long,
    val updatedAt: Long,
    val view: ViewState = ViewState(),
    /** Palette index of the colour last worked on, or -1. */
    val lastColor: Int = -1,
    /** Cell indexes marked in the most recent session, newest last, to help people find their place. */
    val recentCells: List<Int> = emptyList(),
    /** Set when the project is in the Bin. */
    val deletedAt: Long? = null,
    val sourceFileName: String = "",
) {
    override fun equals(other: Any?): Boolean = other is Project && other.id == id && other.updatedAt == updatedAt
    override fun hashCode(): Int = id.hashCode() * 31 + updatedAt.hashCode()
}

/** Where the chart was scrolled and zoomed to, so the app reopens at the same place. */
@Serializable
data class ViewState(
    val centerX: Float = -1f,
    val centerY: Float = -1f,
    /** Cell size in density-independent pixels; 0 means "fit the whole chart". */
    val cellSizeDp: Float = 0f,
)
