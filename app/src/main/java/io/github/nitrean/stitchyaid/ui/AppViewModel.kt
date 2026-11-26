package io.github.nitrean.stitchyaid.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.nitrean.stitchyaid.StitchyApp
import io.github.nitrean.stitchyaid.core.io.ImportResult
import io.github.nitrean.stitchyaid.core.io.PatternImporter
import io.github.nitrean.stitchyaid.core.model.Project
import io.github.nitrean.stitchyaid.core.model.ViewState
import io.github.nitrean.stitchyaid.core.progress.ProgressTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The screens of the app. The back stack always starts with [Home]. */
sealed interface Screen {
    data object Home : Screen
    data class Chart(val projectId: String) : Screen
    data object Bin : Screen
}

/** A short message shown in a big, clear box that stays until the person taps OK. */
data class Notice(val title: String, val text: String)

/** An open chart: the project plus its progress and what the screen is showing. */
class ChartSession(project: Project) {
    var project: Project = project
        internal set
    val tracker = ProgressTracker(project)

    /** Goes up by one on every change, so the chart redraws. */
    var version by mutableIntStateOf(0)
        internal set

    /** Palette index of the colour shown on its own, or -1 for all colours. */
    var highlight by mutableIntStateOf(-1)

    /** When locked, resting a hand on the screen cannot move or zoom the chart. */
    var locked by mutableStateOf(false)

    /** "Welcome back" reminder of where the person left off. */
    var showWelcome by mutableStateOf(project.lastColor >= 0 && project.recentCells.isNotEmpty())

    var view: ViewState = project.view
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as StitchyApp).repository
    private val prefs = app.getSharedPreferences("navigation", Context.MODE_PRIVATE)

    val backStack = mutableStateListOf<Screen>(Screen.Home)
    val current: Screen get() = backStack.last()

    var projects by mutableStateOf<List<Project>>(emptyList())
        private set
    var loaded by mutableStateOf(false)
        private set
    var busyText by mutableStateOf<String?>(null)
        private set
    var notice by mutableStateOf<Notice?>(null)
    var chart by mutableStateOf<ChartSession?>(null)
        private set

    /** The last project moved to the Bin, so the person can undo it right away. */
    var lastBinned by mutableStateOf<Project?>(null)
        private set

    private var saveJob: Job? = null
    private var dirty = false

    val active: List<Project> get() = projects.filter { it.deletedAt == null }.sortedByDescending { it.updatedAt }
    val binned: List<Project> get() = projects.filter { it.deletedAt != null }.sortedByDescending { it.deletedAt }

    init {
        viewModelScope.launch {
            repo.emptyOldBin(System.currentTimeMillis())
            projects = repo.loadAll()
            loaded = true
            // Reopen on the same screen as last time.
            val last = prefs.getString(KEY_SCREEN, null)
            when {
                last == "bin" -> navigate(Screen.Bin)
                last != null && last.startsWith("chart:") -> {
                    val id = last.removePrefix("chart:")
                    if (projects.any { it.id == id && it.deletedAt == null }) openChart(id)
                }
            }
        }
    }

    // ---- Navigation -------------------------------------------------------

    private fun navigate(screen: Screen) {
        backStack.add(screen)
        rememberScreen(screen)
    }

    fun back() {
        if (backStack.size > 1) {
            leaving(backStack.last())
            backStack.removeAt(backStack.lastIndex)
            rememberScreen(backStack.last())
        }
    }

    fun home() {
        while (backStack.size > 1) {
            leaving(backStack.last())
            backStack.removeAt(backStack.lastIndex)
        }
        rememberScreen(Screen.Home)
    }

    fun openBin() = navigate(Screen.Bin)

    private fun leaving(screen: Screen) {
        if (screen is Screen.Chart) {
            saveNow()
            chart = null
        }
    }

    private fun rememberScreen(screen: Screen) {
        val value = when (screen) {
            Screen.Home -> "home"
            Screen.Bin -> "bin"
            is Screen.Chart -> "chart:${screen.projectId}"
        }
        prefs.edit().putString(KEY_SCREEN, value).apply()
    }

    fun openChart(id: String) {
        val project = projects.firstOrNull { it.id == id } ?: return
        if (current is Screen.Chart) back()
        chart = ChartSession(project)
        navigate(Screen.Chart(id))
    }

    // ---- Import -----------------------------------------------------------

    fun importFile(uri: Uri) {
        viewModelScope.launch {
            busyText = "Opening your pattern…"
            try {
                val ctx = getApplication<Application>()
                val (bytes, name) = withContext(Dispatchers.IO) {
                    val name = runCatching {
                        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                            if (c.moveToFirst()) c.getString(0) else null
                        }
                    }.getOrNull() ?: uri.lastPathSegment
                    val bytes = ctx.contentResolver.openInputStream(uri)?.use { input ->
                        val data = input.readBytes()
                        if (data.size > MAX_FILE_BYTES) null else data
                    }
                    bytes to name
                }
                if (bytes == null) {
                    notice = Notice("Couldn't open the file", "This file could not be opened. It may be too large, or it was moved.")
                    return@launch
                }
                when (val result = withContext(Dispatchers.Default) { PatternImporter.import(bytes, name) }) {
                    is ImportResult.Success -> {
                        repo.save(result.project)
                        projects = projects + result.project
                        openChart(result.project.id)
                    }
                    is ImportResult.NotYetSupported -> notice = Notice("Not ready yet", result.message)
                    is ImportResult.Failed -> notice = Notice("Couldn't open the pattern", result.message)
                }
            } catch (e: Exception) {
                notice = Notice("Couldn't open the file", "Something went wrong while opening this file. Please try again.")
            } finally {
                busyText = null
            }
        }
    }

    // ---- Bin --------------------------------------------------------------

    fun moveToBin(id: String) = updateProject(id) { it.copy(deletedAt = System.currentTimeMillis()) }?.also { lastBinned = it }

    fun undoMoveToBin() {
        val p = lastBinned ?: return
        restore(p.id)
        lastBinned = null
    }

    fun clearLastBinned() { lastBinned = null }

    fun restore(id: String) { updateProject(id) { it.copy(deletedAt = null) } }

    fun deleteForever(id: String) {
        projects = projects.filterNot { it.id == id }
        viewModelScope.launch { repo.deleteForever(id) }
    }

    private fun updateProject(id: String, change: (Project) -> Project): Project? {
        val old = projects.firstOrNull { it.id == id } ?: return null
        val updated = change(old)
        projects = projects.map { if (it.id == id) updated else it }
        viewModelScope.launch { repo.save(updated) }
        return updated
    }

    // ---- Stitching --------------------------------------------------------

    fun toggleCell(cell: Int) {
        val c = chart ?: return
        if (c.tracker.toggle(cell)) changed(c)
    }

    fun undo() {
        val c = chart ?: return
        if (c.tracker.undo()) changed(c)
    }

    fun updateView(view: ViewState) {
        val c = chart ?: return
        c.view = view
        dirty = true
        scheduleSave()
    }

    private fun changed(c: ChartSession) {
        c.version++
        c.showWelcome = false
        dirty = true
        scheduleSave()
    }

    /** Saves shortly after the last change, so there is never a Save button to forget. */
    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(SAVE_DELAY_MS)
            saveNow()
        }
    }

    fun saveNow() {
        val c = chart ?: return
        if (!dirty) return
        dirty = false
        val updated = c.tracker.writeTo(c.project.copy(view = c.view), System.currentTimeMillis())
        c.project = updated
        projects = projects.map { if (it.id == updated.id) updated else it }
        viewModelScope.launch(NonCancellable) { repo.save(updated) }
    }

    override fun onCleared() {
        saveNow()
        super.onCleared()
    }

    companion object {
        private const val KEY_SCREEN = "screen"
        private const val SAVE_DELAY_MS = 800L
        private const val MAX_FILE_BYTES = 60 * 1024 * 1024
    }
}
