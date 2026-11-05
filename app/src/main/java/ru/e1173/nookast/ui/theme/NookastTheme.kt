package ru.e1173.nookast.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.asImageBitmap
import ru.e1173.nookast.designsystem.LocalNookastColors
import ru.e1173.nookast.designsystem.LocalThemePalette
import ru.e1173.nookast.designsystem.NookastColors
import ru.e1173.nookast.designsystem.NookastTypography
import ru.e1173.nookast.designsystem.ThemePalette
import ru.e1173.nookast.designsystem.nookastColors
import ru.e1173.nookast.preferences.AppThemeMode

/**
 * The app theme: a palette of DEC-017 (Lavender, Autumn, Winter, Mint) in light or dark, with the
 * mockup typeface. Screens still read Material roles; the design tokens are filled into them so
 * every screen takes the palette, and new components read [LocalNookastColors] directly.
 */
@Composable
fun NookastTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    palette: ThemePalette = ThemePalette.SIGNATURE,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }
    // A new palette or mode crossfades from a snapshot of the old screen, as iOS changes its
    // appearance (user, 2.11.2026: the glass headers flickered for a moment at the switch).
    val requested = palette to darkTheme
    var applied by remember { mutableStateOf(requested) }
    val screenView = androidx.compose.ui.platform.LocalView.current
    var previous by remember { mutableStateOf<ImageBitmap?>(null) }
    val cover = remember { Animatable(0f) }
    LaunchedEffect(requested) {
        if (requested != applied) {
            // A switch in the middle of another keeps the old cover until the new one replaces it
            // (user, 2.11.2026: quick switching let the text under a header flash sharp at random
            // while the screen had no cover and the glass was still settling).
            runCatching { captureWindow(screenView) }.getOrNull()?.let { shot ->
                previous = shot
                cover.snapTo(1f)
            }
            applied = requested
            // The old screen stays whole until the new theme has drawn and settled, glass and the
            // controls' colour animations included; only then does it dissolve.
            kotlinx.coroutines.delay(THEME_HOLD_MS)
        }
        if (previous != null) {
            cover.animateTo(0f, tween(THEME_FADE_MS))
            previous = null
        }
    }
    val (shownPalette, shownDark) = applied
    val tokens = remember(shownPalette, shownDark) { nookastColors(shownPalette, shownDark) }
    // Status and navigation bar icons follow the app's theme, not the system's (U-08): a light
    // theme over a dark system had white icons on a light screen.
    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        androidx.compose.runtime.SideEffect {
            (view.context as? android.app.Activity)?.window?.let { window ->
                androidx.core.view.WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !shownDark
                    isAppearanceLightNavigationBars = !shownDark
                }
            }
        }
    }
    val scheme = remember(tokens) { tokens.toColorScheme() }
    CompositionLocalProvider(LocalNookastColors provides tokens, LocalThemePalette provides shownPalette) {
        Box(Modifier.fillMaxSize(), propagateMinConstraints = true) {
            MaterialTheme(colorScheme = scheme, typography = NookastTypography, content = content)
            // The old screen lies in a layer of its own above the app, so its fade redraws only that
            // layer and never the app's root (user, 2.11.2026, video: in the frame the snapshot left,
            // the header's glass and button discs were missing).
            previous?.let { image ->
                androidx.compose.foundation.layout.Spacer(
                    Modifier.fillMaxSize().graphicsLayer { alpha = cover.value }.drawBehind { drawImage(image) },
                )
            }
        }
    }
}

private const val THEME_HOLD_MS = 340L
private const val THEME_FADE_MS = 280

/**
 * What is on screen now, as the display shows it (user, 2.11.2026: a snapshot drawn from the layers
 * lost the glass blur on Android 12, so the text under a header flashed sharp during the fade).
 */
private suspend fun captureWindow(view: android.view.View): ImageBitmap? {
    if (view.isInEditMode || view.width <= 0 || view.height <= 0) return null
    var context = view.context
    while (context !is android.app.Activity) context = (context as? android.content.ContextWrapper)?.baseContext ?: return null
    val window = (context as android.app.Activity).window
    val bitmap = androidx.core.graphics.createBitmap(view.width, view.height)
    val origin = IntArray(2).also(view::getLocationInWindow)
    val area = android.graphics.Rect(origin[0], origin[1], origin[0] + view.width, origin[1] + view.height)
    return kotlinx.coroutines.suspendCancellableCoroutine { result ->
        android.view.PixelCopy.request(
            window,
            area,
            bitmap,
            { code -> result.resumeWith(Result.success(if (code == android.view.PixelCopy.SUCCESS) bitmap.asImageBitmap() else null)) },
            android.os.Handler(android.os.Looper.getMainLooper()),
        )
    }
}

/** Material roles from the design tokens; containers are derived by tinting the background. */
internal fun NookastColors.toColorScheme(): ColorScheme {
    val opaqueSurface = surface.compositeOver(background)
    val raised = accent.copy(alpha = if (isDark) 0.10f else 0.05f).compositeOver(background)
    val raisedHigh = accent.copy(alpha = if (isDark) 0.16f else 0.09f).compositeOver(background)
    val outline = accent.copy(alpha = if (isDark) 0.40f else 0.28f).compositeOver(opaqueSurface)
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = tintContainer.compositeOver(opaqueSurface),
        onPrimaryContainer = textPrimary,
        secondary = textSecondary,
        onSecondary = if (isDark) background else Color.White,
        secondaryContainer = raisedHigh,
        onSecondaryContainer = textPrimary,
        tertiary = listened,
        onTertiary = onAccent,
        tertiaryContainer = listened.copy(alpha = 0.14f).compositeOver(opaqueSurface),
        onTertiaryContainer = textPrimary,
        background = background,
        onBackground = textPrimary,
        surface = opaqueSurface,
        onSurface = textPrimary,
        surfaceVariant = raised,
        onSurfaceVariant = textSecondary,
        surfaceTint = accent,
        surfaceContainerLowest = opaqueSurface,
        surfaceContainerLow = raised,
        surfaceContainer = raised,
        surfaceContainerHigh = raisedHigh,
        surfaceContainerHighest = raisedHigh,
        outline = outline,
        outlineVariant = divider.compositeOver(opaqueSurface),
        error = if (isDark) Color(0xFFFF7082) else Color(0xFFC73E53),
        errorContainer = if (isDark) Color(0xFF47262D) else Color(0xFFFFE6EA),
        onErrorContainer = if (isDark) Color(0xFFFFE8EB) else Color(0xFF5A1722),
    )
}
