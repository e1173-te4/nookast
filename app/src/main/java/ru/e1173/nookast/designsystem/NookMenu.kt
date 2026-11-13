package ru.e1173.nookast.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The app's menu (audit 3.6): a dropdown on the surface with radius m and a hairline outline,
 * scaled with the interface. Items are [NookMenuItem].
 */
@Composable
fun NookMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    minWidth: Dp = 220.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalNookastColors.current
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier.widthIn(min = minWidth),
        shape = NookShapes.m,
        containerColor = colors.surface,
        border = BorderStroke(0.5.dp, colors.surfaceOutline),
    ) {
        InterfaceScaled { content() }
    }
}

/**
 * One menu item (audit 3.6): an optional [icon], and [destructive] items (delete a download,
 * unsubscribe, remove from the queue) in the error colour.
 */
@Composable
fun NookMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    destructive: Boolean = false,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
) {
    val colors = LocalNookastColors.current
    // Disabled items dim as Material's do (independent review of audit 3: they looked enabled).
    val base = if (destructive) MaterialTheme.colorScheme.error else colors.textPrimary
    val color = if (enabled) base else base.copy(alpha = DISABLED_ITEM_ALPHA)
    DropdownMenuItem(
        text = { Text(text, style = NookType.body, color = color) },
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        leadingIcon = leading ?: icon?.let { { Icon(it, null, tint = if (destructive || !enabled) color else colors.accentText, modifier = Modifier.size(20.dp)) } },
    )
}

private const val DISABLED_ITEM_ALPHA = 0.38f
