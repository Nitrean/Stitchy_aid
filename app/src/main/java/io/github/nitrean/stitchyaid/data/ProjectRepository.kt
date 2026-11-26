package io.github.nitrean.stitchyaid.data

import io.github.nitrean.stitchyaid.core.io.ProjectCodec
import io.github.nitrean.stitchyaid.core.model.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/** Stores each project as one JSON file in the app's private storage. Patterns never leave the device. */
class ProjectRepository(private val dir: File) {
    private val lock = Mutex()

    private fun fileFor(id: String) = File(dir, "$id.json")

    suspend fun loadAll(): List<Project> = withContext(Dispatchers.IO) {
        dir.mkdirs()
        dir.listFiles { f -> f.extension == "json" }.orEmpty().mapNotNull { f ->
            runCatching { ProjectCodec.decode(f.readText()) }.getOrNull()
        }
    }

    suspend fun load(id: String): Project? = withContext(Dispatchers.IO) {
        val f = fileFor(id)
        if (!f.exists()) null else runCatching { ProjectCodec.decode(f.readText()) }.getOrNull()
    }

    /** Writes to a temporary file first, so a crash or flat battery can never leave a half-written project. */
    suspend fun save(project: Project) = withContext(Dispatchers.IO) {
        lock.withLock {
            dir.mkdirs()
            val tmp = File(dir, "${project.id}.json.tmp")
            tmp.writeText(ProjectCodec.encode(project))
            val target = fileFor(project.id)
            if (!tmp.renameTo(target)) {
                target.delete()
                tmp.renameTo(target)
            }
        }
    }

    suspend fun deleteForever(id: String) = withContext(Dispatchers.IO) {
        lock.withLock { fileFor(id).delete() }
    }

    /** Removes projects that have been in the Bin for more than 30 days. */
    suspend fun emptyOldBin(now: Long) {
        val limit = TimeUnit.DAYS.toMillis(BIN_DAYS.toLong())
        loadAll().filter { it.deletedAt != null && now - it.deletedAt!! > limit }.forEach { deleteForever(it.id) }
    }

    companion object {
        const val BIN_DAYS = 30
    }
}
