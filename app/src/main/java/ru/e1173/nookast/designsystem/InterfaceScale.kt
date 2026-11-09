package ru.e1173.nookast.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
 * The app's alert, the same slots as Material's AlertDialog (user, 9.11.2026: a white update
 * message in the dark theme, dialogs must follow the app's colours and the Apple way). A narrow
 * card in the theme's own surface, light and dark, a centred title, the message, and the buttons
 * as a full-width row of equal cells under a hairline, the confirming one last and in the accent.
 * The dimming behind is light, as on iOS. Laid out in the profile's density.
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
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        // iOS dims the screen lightly behind an alert; the platform default is a heavy 60 %.
        val view = androidx.compose.ui.platform.LocalView.current
        androidx.compose.runtime.SideEffect {
            (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.setDimAmount(0.32f)
        }
        InterfaceScaled {
            val colors = LocalNookastColors.current
            androidx.compose.material3.Surface(
                modifier = modifier
                    .padding(horizontal = 28.dp)
                    .widthIn(min = 270.dp, max = 400.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
                color = colors.surface,
                contentColor = colors.textPrimary,
                border = androidx.compose.foundation.BorderStroke(0.5.dp, colors.surfaceOutline),
                tonalElevation = 0.dp,
                shadowElevation = 18.dp,
            ) {
                androidx.compose.foundation.layout.Column {
                    androidx.compose.foundation.layout.Column(
                        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 16.dp),
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    ) {
                        if (title != null) {
                            androidx.compose.material3.ProvideTextStyle(
                                androidx.compose.material3.MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                ),
                            ) { title() }
                        }
                        if (text != null) {
                            if (title != null) androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
                            androidx.compose.runtime.CompositionLocalProvider(
                                androidx.compose.material3.LocalContentColor provides colors.textSecondary,
                            ) {
                                androidx.compose.material3.ProvideTextStyle(
                                    androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    ),
                                ) {
                                    androidx.compose.foundation.layout.Box(
                                        Modifier.fillMaxWidth(),
                                        contentAlignment = androidx.compose.ui.Alignment.TopCenter,
                                    ) { text() }
                                }
                            }
                        }
                    }
                    androidx.compose.material3.HorizontalDivider(thickness = 0.5.dp, color = colors.divider)
                    androidx.compose.foundation.layout.Row(
                        Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min),
                    ) {
                        if (dismissButton != null) {
                            AlertCell(Modifier.weight(1f), colors.textPrimary, emphasis = false, content = dismissButton)
                            androidx.compose.material3.VerticalDivider(thickness = 0.5.dp, color = colors.divider)
                        }
                        AlertCell(Modifier.weight(1f), colors.accent, emphasis = true, content = confirmButton)
                    }
                }
            }
        }
    }
}

/** One button cell of the alert: the caller's button, centred, in the cell's colour and weight. */
@Composable
private fun AlertCell(
    modifier: Modifier,
    color: androidx.compose.ui.graphics.Color,
    emphasis: Boolean,
    content: @Composable () -> Unit,
) {
    androidx.compose.foundation.layout.Box(
        modifier.heightIn(min = 52.dp).padding(horizontal = 4.dp, vertical = 2.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        androidx.compose.material3.MaterialTheme(
            colorScheme = androidx.compose.material3.MaterialTheme.colorScheme.copy(primary = color),
            typography = androidx.compose.material3.MaterialTheme.typography.copy(
                labelLarge = androidx.compose.material3.MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (emphasis) {
                        androidx.compose.ui.text.font.FontWeight.SemiBold
                    } else {
                        androidx.compose.ui.text.font.FontWeight.Normal
                    },
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                ),
            ),
            content = content,
        )
    }
}

/** Restores the profile's density inside a popup, menu or dialog. */
@Composable
fun InterfaceScaled(content: @Composable () -> Unit) {
    val density = LocalInterfaceDensity.current
    if (density == null) content() else CompositionLocalProvider(LocalDensity provides density, content = content)
}
