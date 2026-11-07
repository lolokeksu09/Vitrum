package app.rayclient

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Shared AlertDialog shell. The frame owns typography, colors, shape and action sizing;
 * each caller keeps ownership of state, confirmation rules and side effects.
 */
@Composable
internal fun RayDialogFrame(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
    confirmButton: @Composable () -> Unit = {},
    dismissButton: @Composable () -> Unit = {}
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        tonalElevation = 0.dp,
        title = {
            Text(
                text = I18n.tr(title),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
        },
        confirmButton = confirmButton,
        dismissButton = dismissButton
    )
}

/** Shared information dialog with a single, consistently sized acknowledgement action. */
@Composable
internal fun RayInfoDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirmLabel: String = "Понятно"
) {
    RayDialogFrame(
        title = title,
        onDismiss = onDismiss,
        modifier = modifier,
        content = {
            Text(
                text = I18n.tr(message),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(I18n.tr(confirmLabel))
            }
        }
    )
}

/**
 * Shared confirmation dialog. The caller decides whether an action is destructive and
 * supplies the actual callbacks; this component never mutates AppState or dismisses itself.
 */
@Composable
internal fun RayConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String = "Отмена",
    destructive: Boolean = false,
    confirmEnabled: Boolean = true,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val confirmColor = if (destructive) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }
    RayDialogFrame(
        title = title,
        onDismiss = onDismiss,
        modifier = modifier,
        content = {
            Text(
                text = I18n.tr(message),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = confirmEnabled,
                modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = confirmColor)
            ) {
                Text(I18n.tr(confirmLabel))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(I18n.tr(dismissLabel))
            }
        }
    )
}
