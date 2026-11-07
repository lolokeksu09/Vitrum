package app.rayclient

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Shared page heading. Trailing actions stay in the same row and keep their own callbacks.
 * The title is exposed as a heading to accessibility services.
 */
@Composable
internal fun RayScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = I18n.tr(title),
                modifier = Modifier.weight(1f).semantics { heading() },
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            actions()
        }
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = I18n.tr(subtitle),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Reusable empty state for lists and catalogues. It does not own application state:
 * callers provide the exact action and loading/enabled state.
 */
@Composable
internal fun RayEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    actionEnabled: Boolean = true,
    loading: Boolean = false,
    onAction: (() -> Unit)? = null
) {
    Panel(modifier) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = I18n.tr(title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
                textAlign = TextAlign.Center
            )
            Text(
                text = I18n.tr(message),
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (actionLabel != null && onAction != null) {
                FilledTonalButton(
                    onClick = onAction,
                    enabled = actionEnabled && !loading,
                    modifier = Modifier.padding(top = 12.dp).heightIn(min = 48.dp)
                ) {
                    if (loading) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(I18n.tr(actionLabel))
                }
            }
        }
    }
}

/** Quiet inline notice for guidance that must remain visible without competing with the page title. */
@Composable
internal fun RayInlineNotice(
    message: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        tonalElevation = 0.dp
    ) {
        Text(
            text = I18n.tr(message),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodySmall
        )
    }
}
