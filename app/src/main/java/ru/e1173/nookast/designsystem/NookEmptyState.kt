package ru.e1173.nookast.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * An empty or failed screen (design audit 5.6): the current theme's timeline thumb at 56 dp, a
 * title, a line of explanation and an optional Tonal action. [compact] (Home's sections) leaves out
 * the drawing and centres nothing.
 */
@Composable
fun NookEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    compact: Boolean = false,
) {
    val colors = LocalNookastColors.current
    Column(
        modifier.fillMaxWidth().padding(vertical = if (compact) 4.dp else 28.dp, horizontal = if (compact) 0.dp else 16.dp),
        horizontalAlignment = if (compact) Alignment.Start else Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 10.dp),
    ) {
        if (!compact) {
            val palette = LocalThemePalette.current
            NookTimelineThumb(palette, scale = EMPTY_THUMB_SIZE / thumbBox(palette))
        }
        Text(
            title,
            style = if (compact) NookType.rowTitle else NookType.barTitle,
            color = colors.textPrimary,
            textAlign = if (compact) TextAlign.Start else TextAlign.Center,
        )
        if (message != null) {
            Text(
                message,
                style = NookType.bodySmall,
                color = colors.textSecondary,
                textAlign = if (compact) TextAlign.Start else TextAlign.Center,
            )
        }
        if (actionLabel != null && onAction != null) {
            NookButton(actionLabel, onAction, Modifier.padding(top = 4.dp), variant = NookButtonVariant.Tonal)
        }
    }
}

private val EMPTY_THUMB_SIZE = 56.dp
