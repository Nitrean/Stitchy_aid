package io.github.nitrean.stitchyaid.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.nitrean.stitchyaid.core.model.Project
import io.github.nitrean.stitchyaid.ui.AppViewModel
import io.github.nitrean.stitchyaid.ui.common.AppScaffold
import io.github.nitrean.stitchyaid.ui.common.BigButton
import io.github.nitrean.stitchyaid.ui.common.ConfirmDialog
import io.github.nitrean.stitchyaid.ui.common.LabelButton
import io.github.nitrean.stitchyaid.ui.common.ProjectInfo
import io.github.nitrean.stitchyaid.ui.theme.Sizes

@Composable
fun HomeScreen(vm: AppViewModel) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importFile(uri)
    }
    var toBin by remember { mutableStateOf<Project?>(null) }
    val projects = vm.active
    val latest = projects.firstOrNull()

    AppScaffold(title = "Stitchy Aid", onBack = null, onHome = null) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 320.dp),
            contentPadding = PaddingValues(Sizes.ScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(Sizes.Gap),
            verticalArrangement = Arrangement.spacedBy(Sizes.Gap),
        ) {
            if (latest != null) {
                item(span = { GridItemSpan(maxLineSpan) }) { ContinueCard(latest) { vm.openChart(latest.id) } }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                BigButton(
                    text = "Import a pattern",
                    subtitle = "Choose a chart file, PDF or picture from this tablet",
                    icon = Icons.Filled.FileOpen,
                    onClick = { picker.launch(arrayOf("*/*")) },
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "My patterns",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(top = 16.dp).semantics { heading() },
                )
            }
            if (vm.loaded && projects.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "You haven't added any patterns yet. Tap \"Import a pattern\" to start.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            items(projects, key = { it.id }) { p ->
                ProjectCard(p, onOpen = { vm.openChart(p.id) }, onBin = { toBin = p })
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                val count = vm.binned.size
                Row(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalArrangement = Arrangement.Start) {
                    LabelButton(
                        text = if (count == 0) "Bin (empty)" else "Bin ($count)",
                        icon = Icons.Filled.Delete,
                        onClick = vm::openBin,
                        outlined = true,
                    )
                }
            }
        }
    }

    toBin?.let { p ->
        ConfirmDialog(
            title = "Move to the Bin?",
            text = "\"${p.pattern.title}\" will go to the Bin. You can put it back from the Bin for 30 days.",
            confirmText = "Move to Bin",
            cancelText = "Keep it",
            onConfirm = { vm.moveToBin(p.id); toBin = null },
            onCancel = { toBin = null },
        )
    }
}

@Composable
private fun ContinueCard(p: Project, onContinue: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Continue where you left off", style = MaterialTheme.typography.titleMedium)
            Text(p.pattern.title, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            ProgressLine(p)
            Spacer(Modifier.height(4.dp))
            BigButton("Continue stitching", Icons.Filled.PlayArrow, onContinue)
        }
    }
}

@Composable
private fun ProjectCard(p: Project, onOpen: () -> Unit, onBin: () -> Unit) {
    Card(
        onClick = onOpen,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(p.pattern.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(ProjectInfo.sizeText(p), style = MaterialTheme.typography.bodyMedium)
            ProgressLine(p)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                LabelButton("Open", Icons.Filled.PlayArrow, onOpen)
                LabelButton("Move to Bin", Icons.Filled.Delete, onBin, outlined = true)
            }
        }
    }
}

@Composable
private fun ProgressLine(p: Project) {
    val percent = ProjectInfo.percentDone(p)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LinearProgressIndicator(
            progress = { percent / 100f },
            modifier = Modifier.fillMaxWidth().height(14.dp),
        )
        Text("$percent% done", style = MaterialTheme.typography.bodyMedium)
    }
}
