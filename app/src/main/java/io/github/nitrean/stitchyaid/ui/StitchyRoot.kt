package io.github.nitrean.stitchyaid.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.nitrean.stitchyaid.ui.bin.BinScreen
import io.github.nitrean.stitchyaid.ui.chart.ChartScreen
import io.github.nitrean.stitchyaid.ui.common.BusyDialog
import io.github.nitrean.stitchyaid.ui.common.LabelButton
import io.github.nitrean.stitchyaid.ui.common.NoticeDialog
import io.github.nitrean.stitchyaid.ui.home.HomeScreen

@Composable
fun StitchyRoot(vm: AppViewModel) {
    // The tablet's own Back gesture does the same as the Back button on screen.
    BackHandler(enabled = vm.backStack.size > 1) { vm.back() }

    Box(Modifier.fillMaxSize()) {
        when (val screen = vm.current) {
            Screen.Home -> HomeScreen(vm)
            Screen.Bin -> BinScreen(vm)
            is Screen.Chart -> {
                val session = vm.chart
                if (session != null && session.project.id == screen.projectId) ChartScreen(vm, session) else HomeScreen(vm)
            }
        }

        vm.lastBinned?.let { p ->
            if (vm.current == Screen.Home) {
                Surface(
                    color = MaterialTheme.colorScheme.onBackground,
                    contentColor = MaterialTheme.colorScheme.background,
                    shape = RoundedCornerShape(16.dp),
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(16.dp)
                        .fillMaxWidth(),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("\"${p.pattern.title}\" moved to the Bin.", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(12.dp))
                        LabelButton("Undo", null, vm::undoMoveToBin)
                        Spacer(Modifier.width(8.dp))
                        LabelButton("OK", null, vm::clearLastBinned)
                    }
                }
            }
        }
    }

    vm.busyText?.let { BusyDialog(it) }
    vm.notice?.let { n -> NoticeDialog(n.title, n.text) { vm.notice = null } }
}
