package io.github.nitrean.stitchyaid.core.io

import io.github.nitrean.stitchyaid.core.model.Project
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Saves and loads complete projects (chart, progress and view) as JSON. */
object ProjectCodec {
    const val VERSION = 1

    @Serializable
    private class Envelope(val stitchyFormat: Int, val project: Project)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    fun encode(project: Project): String = json.encodeToString(Envelope.serializer(), Envelope(VERSION, project))

    fun decode(text: String): Project {
        val env = try {
            json.decodeFromString(Envelope.serializer(), text)
        } catch (e: Exception) {
            throw PatternReadException("This project file is damaged and could not be opened.", e)
        }
        if (env.stitchyFormat > VERSION) throw PatternReadException("This project was made by a newer version of Stitchy Aid. Please update the app.")
        return env.project
    }
}
