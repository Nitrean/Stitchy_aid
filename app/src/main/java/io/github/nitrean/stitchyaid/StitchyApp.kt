package io.github.nitrean.stitchyaid

import android.app.Application
import io.github.nitrean.stitchyaid.data.ProjectRepository
import java.io.File

class StitchyApp : Application() {
    val repository: ProjectRepository by lazy { ProjectRepository(File(filesDir, "projects")) }
}
