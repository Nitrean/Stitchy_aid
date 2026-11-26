package io.github.nitrean.stitchyaid.core.io

import io.github.nitrean.stitchyaid.core.model.Pattern
import io.github.nitrean.stitchyaid.core.model.Project
import java.util.UUID

/** What happened when a file was imported. Every message is plain language. */
sealed interface ImportResult {
    data class Success(val project: Project) : ImportResult
    data class NotYetSupported(val kind: FileKind) : ImportResult {
        val message: String get() = "Reading a ${kind.friendlyName} is coming in a later version of Stitchy Aid."
    }
    data class Failed(val message: String) : ImportResult
}

/** The single entry point behind the Import button: give it any file and it works out the rest. */
object PatternImporter {
    fun import(bytes: ByteArray, fileName: String?, now: Long = System.currentTimeMillis()): ImportResult {
        val title = fileName?.substringBeforeLast('.')?.replace('_', ' ')?.trim()?.ifBlank { null } ?: "My pattern"
        return try {
            when (val kind = FormatDetector.detect(bytes, fileName)) {
                FileKind.OXS -> ImportResult.Success(newProject(OxsFormat.read(bytes, title), now, fileName))
                FileKind.STITCHY_PROJECT -> {
                    val p = ProjectCodec.decode(bytes.toString(Charsets.UTF_8))
                    ImportResult.Success(p.copy(id = UUID.randomUUID().toString(), deletedAt = null, updatedAt = now))
                }
                FileKind.UNKNOWN -> ImportResult.Failed("Stitchy Aid doesn't recognise this file. Try a cross stitch chart, a PDF pattern or a picture.")
                else -> ImportResult.NotYetSupported(kind)
            }
        } catch (e: PatternReadException) {
            ImportResult.Failed(e.message ?: "This file could not be read.")
        } catch (e: Exception) {
            ImportResult.Failed("This file could not be read. It may be damaged.")
        }
    }

    fun newProject(pattern: Pattern, now: Long, fileName: String? = null) = Project(
        id = UUID.randomUUID().toString(),
        pattern = pattern,
        createdAt = now,
        updatedAt = now,
        sourceFileName = fileName.orEmpty(),
    )
}
