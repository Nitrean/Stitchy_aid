package io.github.nitrean.stitchyaid.ui.bin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.nitrean.stitchyaid.core.model.Project
import io.github.nitrean.stitchyaid.data.ProjectRepository
import io.github.nitrean.stitchyaid.ui.AppViewModel
import io.github.nitrean.stitchyaid.ui.common.AppScaffold
import io.github.nitrean.stitchyaid.ui.common.ConfirmDialog
import io.github.nitrean.stitchyaid.ui.common.LabelButton
import io.github.nitrean.stitchyaid.ui.theme.Sizes
import java.util.concurrent.TimeUnit

@Composable
fun BinScreen(vm: AppViewModel) {
    var toDelete by remember { mutableStateOf<Project?>(null) }
    val items = vm.binned

    AppScaffold(title = "Bin", onBack = vm::back, onHome = vm::home) {
        LazyColumn(
            contentPadding = PaddingValues(Sizes.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(Sizes.Gap),
        ) {
            item {
                Text(
                    if (items.isEmpty()) "The Bin is empty." else
                        "Patterns you removed stay here for ${ProjectRepository.BIN_DAYS} days. Tap \"Put back\" to get one back.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            items(items, key = { it.id }) { p ->
                val daysLeft = p.deletedAt?.let {
                    val passed = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - it).toInt()
                    (ProjectRepository.BIN_DAYS - passed).coerceAtLeast(0)
                } ?: ProjectRepository.BIN_DAYS
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(p.pattern.title, style = MaterialTheme.typography.titleLarge)
                        Text("Deleted for good in $daysLeft ${if (daysLeft == 1) "day" else "days"}", style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            LabelButton("Put back", Icons.Filled.Restore, { vm.restore(p.id) })
                            LabelButton("Delete forever", Icons.Filled.DeleteForever, { toDelete = p }, outlined = true)
                        }
                    }
                }
            }
        }
    }

    toDelete?.let { p ->
        ConfirmDialog(
            title = "Delete forever?",
            text = "\"${p.pattern.title}\" and all its progress will be deleted. This cannot be undone.",
            confirmText = "Delete forever",
            cancelText = "Keep it",
            onConfirm = { vm.deleteForever(p.id); toDelete = null },
            onCancel = { toDelete = null },
            dangerous = true,
        )
    }
}
