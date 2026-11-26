package io.github.nitrean.stitchyaid.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.nitrean.stitchyaid.ui.theme.Sizes

/**
 * Every screen uses this frame: a big title saying where you are, a labelled
 * Back button in the same corner every time, and a Home button.
 */
@Composable
fun AppScaffold(
    title: String,
    onBack: (() -> Unit)?,
    onHome: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 88.dp).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) {
                    LabelButton("Back", Icons.AutoMirrored.Filled.ArrowBack, onBack, outlined = true)
                    Spacer(Modifier.width(16.dp))
                }
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                if (onHome != null) {
                    Spacer(Modifier.width(16.dp))
                    LabelButton("Home", Icons.Filled.Home, onHome, outlined = true)
                }
            }
            HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.outline)
            Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        }
    }
}

/** A button with an icon and a word on it. Icons are never shown without a label. */
@Composable
fun LabelButton(
    text: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    outlined: Boolean = false,
    enabled: Boolean = true,
) {
    val inner: @Composable () -> Unit = {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
    val padding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
    if (outlined) {
        OutlinedButton(onClick, modifier.heightIn(min = Sizes.TouchTarget), enabled = enabled, contentPadding = padding,
            border = ButtonDefaults.outlinedButtonBorder(enabled).copy(width = 2.dp)) { inner() }
    } else {
        Button(onClick, modifier.heightIn(min = Sizes.TouchTarget), enabled = enabled, contentPadding = padding) { inner() }
    }
}

/** A large, full-width main action. */
@Composable
fun BigButton(text: String, icon: ImageVector?, onClick: () -> Unit, modifier: Modifier = Modifier, subtitle: String? = null) {
    Button(
        onClick,
        modifier.fillMaxWidth().heightIn(min = Sizes.BigButtonHeight),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.titleLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Asks before anything permanent, in plain words. The safe choice is always offered. */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmText: String,
    cancelText: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    dangerous: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        text = { Text(text, style = MaterialTheme.typography.bodyLarge) },
        confirmButton = {
            Button(
                onConfirm,
                modifier = Modifier.heightIn(min = Sizes.TouchTarget),
                colors = if (dangerous) ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ) else ButtonDefaults.buttonColors(),
            ) { Text(confirmText, style = MaterialTheme.typography.labelLarge) }
        },
        dismissButton = {
            OutlinedButton(onCancel, modifier = Modifier.heightIn(min = Sizes.TouchTarget)) {
                Text(cancelText, style = MaterialTheme.typography.labelLarge)
            }
        },
    )
}

@Composable
fun NoticeDialog(title: String, text: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        text = { Text(text, style = MaterialTheme.typography.bodyLarge) },
        confirmButton = {
            Button(onDismiss, modifier = Modifier.heightIn(min = Sizes.TouchTarget).width(140.dp)) {
                Text("OK", style = MaterialTheme.typography.labelLarge)
            }
        },
    )
}

@Composable
fun BusyDialog(text: String) {
    Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
            Row(Modifier.padding(32.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                CircularProgressIndicator(Modifier.size(48.dp), strokeWidth = 5.dp)
                Text(text, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
