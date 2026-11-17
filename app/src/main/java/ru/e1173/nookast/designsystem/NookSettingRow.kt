package ru.e1173.nookast.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * One setting in a settings card: a title, an optional description under it and a [trailing]
 * control (a switch, a value). The geometry of the existing settings rows: 16 dp to the control,
 * the description 4 dp under the title. [onClick] makes the whole row a tap target.
 */
@Composable
fun NookSettingRow(
    title: String,
    description: String?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.fillMaxWidth()) {
    val half = maxWidth / 2
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick).padding(vertical = NookSpacing.xs) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NookSpacing.l),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = NookType.barTitle)
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = NookSpacing.xs),
                )
            }
        }
        // The control takes at most half the row, so a long value never squeezes the title into
        // broken words in a narrow dialog (merge check, 14.11.2026).
        Box(Modifier.widthIn(max = half), contentAlignment = Alignment.CenterEnd) { trailing() }
    }
    }
}

/** The line between settings in one card, as the existing cards draw it. */
@Composable
fun NookRowDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier.padding(vertical = NookSpacing.s), color = MaterialTheme.colorScheme.outlineVariant)
}

/**
 * A menu item whose glyph sits in a tinted disc (the podcast gear menu): disc 30 dp, 38 in large
 * controls; glyph 17 / 21; row at least 48 / 72 dp tall. [glyph] defaults to [icon] in the accent.
 */
@Composable
fun NookDiscMenuItem(
    text: String,
    onClick: () -> Unit,
    largeControls: Boolean,
    icon: ImageVector? = null,
    glyph: (@Composable () -> Unit)? = null,
) {
    val disc = if (largeControls) DISC_LARGE else DISC
    DropdownMenuItem(
        text = { Text(text, style = NookType.rowTitle) },
        leadingIcon = {
            Surface(modifier = Modifier.size(disc), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Box(contentAlignment = Alignment.Center) {
                    when {
                        glyph != null -> glyph()
                        icon != null -> Icon(
                            icon,
                            contentDescription = null,
                            modifier = Modifier.size(if (largeControls) GLYPH_LARGE else GLYPH),
                            tint = LocalNookastColors.current.accentText,
                        )
                    }
                }
            }
        },
        onClick = onClick,
        modifier = Modifier.heightIn(min = if (largeControls) ROW_LARGE else ROW),
    )
}

private val DISC = 30.dp
private val DISC_LARGE = 38.dp
private val GLYPH = 17.dp
private val GLYPH_LARGE = 21.dp
private val ROW = 48.dp
private val ROW_LARGE = 72.dp
