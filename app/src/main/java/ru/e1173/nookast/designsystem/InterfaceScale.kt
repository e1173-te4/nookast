package ru.e1173.nookast.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

/**
 * The interface density of the active profile (Jaecoo: ×1.5 and larger type). Popups, menus and
 * dialogs get their own window, whose root resets [LocalDensity] to the system value, so the
 * profile never reached them (user, 27–28.10.2026: popups did not scale in the car). Ordinary
 * composition locals do pass through, so this one carries the density across.
 */
val LocalInterfaceDensity = staticCompositionLocalOf<Density?> { null }

/**
 * Material's AlertDialog laid out in the profile's density, container included. Same slots as the
 * Material one that the app uses; the width follows the content up to 560 dp of the profile.
 */
@Composable
fun ScaledAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    properties: androidx.compose.ui.window.DialogProperties =
        androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
) {
    // Material's AlertDialog opens a second window (and resets the density again), so its layout
    // is rebuilt here inside one Dialog: 28 dp corners, 24 dp padding, title, text, buttons at the end.
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        InterfaceScaled {
            // The theme's own card, light and dark, not Material's tinted container (user, 5.11.2026).
            val colors = LocalNookastColors.current
            androidx.compose.material3.Surface(
                modifier = modifier
                    .padding(horizontal = 24.dp)
                    .widthIn(min = 280.dp, max = 560.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                color = colors.surface,
                contentColor = colors.textPrimary,
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.surfaceOutline),
                tonalElevation = 0.dp,
                shadowElevation = 12.dp,
            ) {
                androidx.compose.foundation.layout.Column(Modifier.padding(24.dp)) {
                    if (title != null) {
                        androidx.compose.material3.ProvideTextStyle(
                            androidx.compose.material3.MaterialTheme.typography.headlineSmall,
                        ) { title() }
                        androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))
                    }
                    if (text != null) {
                        androidx.compose.runtime.CompositionLocalProvider(
                            androidx.compose.material3.LocalContentColor provides
                                colors.textSecondary,
                        ) {
                            androidx.compose.material3.ProvideTextStyle(
                                androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                            ) { text() }
                        }
                        androidx.compose.foundation.layout.Spacer(Modifier.height(24.dp))
                    }
                    androidx.compose.foundation.layout.Row(
                        Modifier.align(androidx.compose.ui.Alignment.End),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        dismissButton?.invoke()
                        confirmButton()
                    }
                }
            }
        }
    }
}

/** Restores the profile's density inside a popup, menu or dialog. */
@Composable
fun InterfaceScaled(content: @Composable () -> Unit) {
    val density = LocalInterfaceDensity.current
    if (density == null) content() else CompositionLocalProvider(LocalDensity provides density, content = content)
}
