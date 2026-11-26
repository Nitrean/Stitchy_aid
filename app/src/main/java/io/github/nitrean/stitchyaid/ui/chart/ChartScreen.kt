package io.github.nitrean.stitchyaid.ui.chart

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.nitrean.stitchyaid.core.color.ColorMath
import io.github.nitrean.stitchyaid.core.model.ViewState
import io.github.nitrean.stitchyaid.core.progress.ColorProgress
import io.github.nitrean.stitchyaid.ui.AppViewModel
import io.github.nitrean.stitchyaid.ui.ChartSession
import io.github.nitrean.stitchyaid.ui.common.AppScaffold
import io.github.nitrean.stitchyaid.ui.common.LabelButton
import java.text.NumberFormat

@Composable
fun ChartScreen(vm: AppViewModel, session: ChartSession) {
    val pattern = session.project.pattern
    val density = LocalDensity.current.density
    val viewport = remember(session) { ChartViewport(pattern.width, pattern.height, density) }

    // Keep the screen awake while stitching.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val onViewChanged = {
        val c = viewport.centerCell()
        vm.updateView(ViewState(c.x, c.y, viewport.cellDp))
    }

    AppScaffold(title = "${pattern.title}: Chart", onBack = vm::back, onHome = vm::home) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 600.dp
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    ChartArea(vm, session, viewport, onViewChanged, Modifier.weight(1f).fillMaxHeight())
                    VerticalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.outline)
                    SidePanel(vm, session, viewport, onViewChanged, Modifier.width(if (maxWidth >= 840.dp) 380.dp else 320.dp).fillMaxHeight())
                }
            } else {
                // Narrow screens, such as a folded phone: chart on top, big controls below.
                Column(Modifier.fillMaxSize()) {
                    ChartArea(vm, session, viewport, onViewChanged, Modifier.weight(1f).fillMaxWidth())
                    HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.outline)
                    BottomControls(vm, session, viewport, onViewChanged)
                }
            }
        }
    }
}

@Composable
private fun ChartArea(vm: AppViewModel, session: ChartSession, viewport: ChartViewport, onViewChanged: () -> Unit, modifier: Modifier) {
    Box(modifier.background(Color(0xFFD9D4C7))) {
        ChartCanvas(session, viewport, onTapCell = vm::toggleCell, onViewChanged = onViewChanged, modifier = Modifier.fillMaxSize())
        if (session.showWelcome) WelcomeBack(session, Modifier.align(Alignment.TopCenter).padding(16.dp))
        if (session.highlight >= 0) {
            val entry = session.project.pattern.palette[session.highlight]
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                shadowElevation = 4.dp,
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Showing only ${entry.thread.label}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(12.dp))
                    LabelButton("Show all", null, { session.highlight = -1 }, outlined = true)
                }
            }
        }
    }
}

/** "Welcome back" card that reminds people which colour they were on. */
@Composable
private fun WelcomeBack(session: ChartSession, modifier: Modifier) {
    val pattern = session.project.pattern
    val color = session.project.lastColor.takeIf { it in pattern.palette.indices } ?: return
    val entry = pattern.palette[color]
    Card(modifier.widthIn(max = 560.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Welcome back!", style = MaterialTheme.typography.titleLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Swatch(entry.thread.rgb, entry.symbol, 44)
                Spacer(Modifier.width(12.dp))
                Text(
                    "Last time you were stitching ${entry.thread.label} ${entry.thread.name}. Your last stitches have an orange border.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            LabelButton("OK", null, { session.showWelcome = false })
        }
    }
}

@Composable
private fun SidePanel(vm: AppViewModel, session: ChartSession, viewport: ChartViewport, onViewChanged: () -> Unit, modifier: Modifier) {
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ProgressSummary(session)
        UndoButton(vm, session, Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ControlButton("Zoom out", Icons.Filled.ZoomOut, Modifier.weight(1f)) { viewport.zoomBy(1 / 1.5f); onViewChanged() }
            ControlButton("Zoom in", Icons.Filled.ZoomIn, Modifier.weight(1f)) { viewport.zoomBy(1.5f); onViewChanged() }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ControlButton("Whole pattern", Icons.Filled.FitScreen, Modifier.weight(1f)) { viewport.fit(); onViewChanged() }
            LockButton(session, Modifier.weight(1f))
        }
        Text("Colours", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        ColourList(session, Modifier.weight(1f))
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun BottomControls(vm: AppViewModel, session: ChartSession, viewport: ChartViewport, onViewChanged: () -> Unit) {
    var showColours by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ProgressSummary(session, compact = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            UndoButton(vm, session, Modifier.weight(1f))
            ControlButton("Colours", Icons.Filled.Palette, Modifier.weight(1f)) { showColours = true }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ControlButton("Out", Icons.Filled.ZoomOut, Modifier.weight(1f)) { viewport.zoomBy(1 / 1.5f); onViewChanged() }
            ControlButton("In", Icons.Filled.ZoomIn, Modifier.weight(1f)) { viewport.zoomBy(1.5f); onViewChanged() }
            ControlButton("All", Icons.Filled.FitScreen, Modifier.weight(1f)) { viewport.fit(); onViewChanged() }
            LockButton(session, Modifier.weight(1f), short = true)
        }
    }
    if (showColours) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { showColours = false }, sheetState = sheetState) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Colours", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    LabelButton("Close", null, { showColours = false }, outlined = true)
                }
                Spacer(Modifier.height(12.dp))
                ColourList(session, Modifier.heightIn(max = 520.dp)) { showColours = false }
            }
        }
    }
}

@Composable
private fun ProgressSummary(session: ChartSession, compact: Boolean = false) {
    @Suppress("UNUSED_VARIABLE") val v = session.version // redraw on change
    val t = session.tracker
    val nf = NumberFormat.getIntegerInstance()
    val percent = (t.fractionDone * 100).toInt()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LinearProgressIndicator(progress = { t.fractionDone }, modifier = Modifier.fillMaxWidth().height(14.dp))
        Text(
            if (compact) "$percent% done · ${nf.format(t.totalStitches - t.doneStitches)} stitches to go"
            else "${nf.format(t.doneStitches)} of ${nf.format(t.totalStitches)} stitches done ($percent%)",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun UndoButton(vm: AppViewModel, session: ChartSession, modifier: Modifier) {
    @Suppress("UNUSED_VARIABLE") val v = session.version
    Button(
        onClick = vm::undo,
        enabled = session.tracker.canUndo,
        modifier = modifier.heightIn(min = 72.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary),
    ) {
        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, modifier = Modifier.size(32.dp))
        Spacer(Modifier.width(10.dp))
        Text("Undo", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun LockButton(session: ChartSession, modifier: Modifier, short: Boolean = false) {
    val locked = session.locked
    ControlButton(
        text = when {
            short -> if (locked) "Locked" else "Lock"
            locked -> "Chart locked"
            else -> "Lock chart"
        },
        icon = if (locked) Icons.Filled.Lock else Icons.Filled.LockOpen,
        modifier = modifier.semantics {
            contentDescription = if (locked) "Chart is locked. Tap to let it move again." else "Lock the chart so it can't move"
        },
        highlighted = locked,
    ) { session.locked = !locked }
}

/** A tall button with the icon above its word, for rows of controls. */
@Composable
private fun ControlButton(text: String, icon: ImageVector, modifier: Modifier = Modifier, highlighted: Boolean = false, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 72.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 6.dp),
        colors = if (highlighted) ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary,
        ) else ButtonDefaults.filledTonalButtonColors(),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 1)
        }
    }
}

/** Every colour in the pattern. Tap one to show only that colour on the chart. */
@Composable
private fun ColourList(session: ChartSession, modifier: Modifier, onPicked: () -> Unit = {}) {
    @Suppress("UNUSED_VARIABLE") val v = session.version
    val pattern = session.project.pattern
    val rows = session.tracker.colorProgress().filter { it.total > 0 }
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(rows, key = { it.color }) { cp ->
            ColourRow(session, cp, selected = session.highlight == cp.color) {
                session.highlight = if (session.highlight == cp.color) -1 else cp.color
                onPicked()
            }
        }
        if (rows.isEmpty() && pattern.palette.isNotEmpty()) {
            item { Text("This pattern only has outlines.", style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun ColourRow(session: ChartSession, cp: ColorProgress, selected: Boolean, onClick: () -> Unit) {
    val entry = session.project.pattern.palette[cp.color]
    val border = if (selected) BorderStroke(4.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .border(border, RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClickLabel = if (selected) "Show all colours" else "Show only this colour", onClick = onClick)
            .semantics { this.selected = selected }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Swatch(entry.thread.rgb, entry.symbol, 48)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("${entry.thread.label}  ${entry.thread.name}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 2)
            Text(
                if (cp.isFinished) "Finished!" else "${cp.remaining} of ${cp.total} left",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** A colour square with the chart symbol on it, as it appears on the chart. */
@Composable
fun Swatch(rgb: Int, symbol: String, sizeDp: Int) {
    val dark = ColorMath.luminance(rgb) < 0.35
    Box(
        Modifier
            .size(sizeDp.dp)
            .background(Color(0xFF000000.toInt() or rgb), RoundedCornerShape(8.dp))
            .border(1.5.dp, Color(0xFF333333), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, color = if (dark) Color.White else Color.Black, fontSize = (sizeDp * 0.5f).sp, fontWeight = FontWeight.Bold)
    }
}
