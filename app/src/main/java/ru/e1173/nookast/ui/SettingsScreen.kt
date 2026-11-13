@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package ru.e1173.nookast.ui

import ru.e1173.nookast.designsystem.themeRevealSource
import ru.e1173.nookast.designsystem.NookSlider
import ru.e1173.nookast.designsystem.NookSwitch
import ru.e1173.nookast.designsystem.nookCard
import ru.e1173.nookast.designsystem.NookButton
import ru.e1173.nookast.designsystem.NookButtonSize
import ru.e1173.nookast.designsystem.NookButtonVariant
import ru.e1173.nookast.designsystem.focusRing
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.alpha
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import ru.e1173.nookast.designsystem.NookIcons
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Canvas
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import ru.e1173.nookast.R
import ru.e1173.nookast.data.remote.QueueLoadError
import ru.e1173.nookast.player.PlaybackPreferences
import ru.e1173.nookast.player.PlaybackUiState
import ru.e1173.nookast.player.QueueInsertionPosition
import ru.e1173.nookast.player.QueueSwipeAction
import ru.e1173.nookast.preferences.AppThemeMode

private enum class SettingsSection(
    val titleResource: Int,
    val descriptionResource: Int,
) {
    PLAYER(R.string.settings_player_title, R.string.settings_player_description),
    APPEARANCE(R.string.settings_appearance_title, R.string.settings_appearance_description),
    SERVER(R.string.settings_server_title, R.string.settings_server_description),
    DIAGNOSTICS(
        R.string.settings_diagnostics_title,
        R.string.settings_diagnostics_section_description,
    ),
    ABOUT(R.string.settings_about_title, R.string.settings_about_description),
}

@Composable
internal fun SettingsScreen(
    serverUrl: String,
    queueState: QueueUiState,
    playbackState: PlaybackUiState,
    wide: Boolean,
    largeControls: Boolean,
    touchTarget: Dp,
    themeMode: AppThemeMode,
    onClose: () -> Unit,
    onRefresh: () -> Unit,
    onRunDiagnostics: () -> Unit,
    onSignOut: () -> Unit,
    onThemeModeChanged: (AppThemeMode) -> Unit,
    onDownloadWholeEpisodeChanged: (Boolean) -> Unit,
    onAheadCacheChanged: (Int) -> Unit,
    onSeekBackSecondsChanged: (Int) -> Unit,
    onSeekForwardSecondsChanged: (Int) -> Unit,
    onPlaybackStartQueuePositionChanged: (QueueInsertionPosition) -> Unit,
    onManualQueuePositionChanged: (QueueInsertionPosition) -> Unit,
    onQueueSwipeStartActionChanged: (QueueSwipeAction) -> Unit,
    onQueueSwipeEndActionChanged: (QueueSwipeAction) -> Unit,
    onClearAheadCache: () -> Unit,
    initialSectionForPreview: String? = null,
) {
    var selectedSectionName by rememberSaveable(initialSectionForPreview) {
        mutableStateOf(initialSectionForPreview)
    }
    val selectedSection = selectedSectionName?.let { stored ->
        SettingsSection.entries.firstOrNull { it.name == stored }
    }

    BackHandler {
        if (!wide && selectedSection != null) {
            selectedSectionName = null
        } else {
            onClose()
        }
    }

    val sectionContent: @Composable (SettingsSection) -> Unit = { section ->
        SettingsSectionContent(
            section = section,
            showHeading = false,
            // Wide screens use the phone's full-width rows too (user, 29.10.2026: the side-by-side
            // panels squeezed every setting into a tall narrow column), in a centred column.
            wideLayout = false,
            serverUrl = serverUrl,
            queueState = queueState,
            playbackState = playbackState,
            largeControls = largeControls,
            touchTarget = touchTarget,
            themeMode = themeMode,
            onRefresh = onRefresh,
            onRunDiagnostics = onRunDiagnostics,
            onSignOut = onSignOut,
            onThemeModeChanged = onThemeModeChanged,
            onDownloadWholeEpisodeChanged = onDownloadWholeEpisodeChanged,
            onAheadCacheChanged = onAheadCacheChanged,
            onSeekBackSecondsChanged = onSeekBackSecondsChanged,
            onSeekForwardSecondsChanged = onSeekForwardSecondsChanged,
            onPlaybackStartQueuePositionChanged = onPlaybackStartQueuePositionChanged,
            onManualQueuePositionChanged = onManualQueuePositionChanged,
            onQueueSwipeStartActionChanged = onQueueSwipeStartActionChanged,
            onQueueSwipeEndActionChanged = onQueueSwipeEndActionChanged,
            onClearAheadCache = onClearAheadCache,
        )
    }

    if (wide) {
        // Wide (user, 27.10.2026): the phone section list on the left as is, the open section
        // on the right over the rest of the width, each pane scrolling on its own.
        // Revised the same day (user, car photo): the phone list ate half the width and left the
        // settings a narrow strip. Now a slim sidebar of the same tiles and names, without hints,
        // and the open section over all the remaining width.
        val activeSection = selectedSection ?: SettingsSection.PLAYER
        Row(Modifier.fillMaxSize()) {
            SettingsSidebar(
                selected = activeSection,
                touchTarget = touchTarget,
                onBack = onClose,
                onSectionSelected = { selectedSectionName = it.name },
                modifier = Modifier
                    .width(if (largeControls) 300.dp else 236.dp)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState(), flingBehavior = deviceFling())
                    .padding(top = LocalTopInset.current)
                    .padding(horizontal = 12.dp),
            )
            Box(
                Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .padding(vertical = 12.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            )
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState(), flingBehavior = deviceFling())
                    .wrapContentWidth(Alignment.CenterHorizontally)
                    .widthIn(max = if (largeControls) 760.dp else 680.dp)
                    .padding(top = LocalTopInset.current)
                    .padding(horizontal = 24.dp),
            ) {
                Box(Modifier.fillMaxWidth().heightIn(min = 52.dp), contentAlignment = Alignment.CenterStart) {
                    Text(
                        text = stringResource(activeSection.titleResource),
                        style = ru.e1173.nookast.designsystem.NookType.heroTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(8.dp))
                sectionContent(activeSection)
                Spacer(Modifier.height(24.dp + LocalBottomInset.current))
            }
        }
        return
    }
    // Phone: the back button and the section name stay on top as glass over the scrolled settings
    // and the status bar (user, 29.10.2026).
    val scroll = key(selectedSection) { rememberScrollState() }
    val sidePadding = if (largeControls) 18.dp else 12.dp
    GlassHeaderScreen(
        headerHeight = 52.dp,
        scrolled = scroll.value > 0,
        modifier = Modifier.fillMaxSize(),
        header = {
            Box(Modifier.fillMaxSize().padding(horizontal = sidePadding)) {
                if (selectedSection == null) {
                    // The back arrow and the title on one line, as in every section (user, 2.11.2026: the
                    // large title under the arrow looked like two separate rows).
                    SettingsHeader(
                        title = stringResource(R.string.settings_title),
                        onBack = onClose,
                        touchTarget = touchTarget,
                    )
                } else {
                    SettingsHeader(
                        title = stringResource(selectedSection.titleResource),
                        onBack = { selectedSectionName = null },
                        touchTarget = touchTarget,
                    )
                }
            }
        },
    ) { topPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll, flingBehavior = deviceFling())
                .padding(top = topPadding, bottom = 24.dp)
                .padding(horizontal = sidePadding),
        ) {
            if (selectedSection == null) {
                Spacer(Modifier.height(8.dp))
                PhoneSettingsSectionList(
                    touchTarget = touchTarget,
                    onBack = onClose,
                    onSectionSelected = { selectedSectionName = it.name },
                    showBack = false,
                    showTitle = false,
                )
            } else {
                Spacer(Modifier.height(8.dp))
                sectionContent(selectedSection)
            }
        }
    }
}

/**
 * Settings root in the iOS manner (APK-3.5 review: the screen looked unfinished and one card was
 * darkened): a round back button, a large title and one grouped card of section rows — solid
 * coloured icon tile, title, one-line hint and a chevron, hairlines inset past the icon.
 */
@Composable
private fun PhoneSettingsSectionList(
    touchTarget: Dp,
    onBack: () -> Unit,
    onSectionSelected: (SettingsSection) -> Unit,
    modifier: Modifier = Modifier,
    selected: SettingsSection? = null,
    showBack: Boolean = true,
    showTitle: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (showBack) {
            Box(Modifier.fillMaxWidth().heightIn(min = 52.dp), contentAlignment = Alignment.CenterStart) {
                SettingsRoundBack(onBack = onBack, touchTarget = touchTarget)
            }
        }
        if (showTitle) Text(
            text = stringResource(R.string.settings_title),
            style = ru.e1173.nookast.designsystem.NookType.display,
            modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 18.dp),
        )
        val shape = ru.e1173.nookast.designsystem.NookShapes.xl
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), shape),
        ) {
            SettingsSection.entries.forEachIndexed { index, section ->
                SettingsSectionRow(
                    section = section,
                    selected = section == selected,
                    touchTarget = touchTarget,
                    onClick = { onSectionSelected(section) },
                )
                if (index != SettingsSection.entries.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 60.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** The wide-screen section list: the same coloured tiles and names as the phone list, no hints. */
@Composable
private fun SettingsSidebar(
    selected: SettingsSection,
    touchTarget: Dp,
    onBack: () -> Unit,
    onSectionSelected: (SettingsSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        // Back and the title on one line, as on the phone (user, 3.11.2026: on wide screens the
        // title went under the button).
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SettingsRoundBack(onBack = onBack, touchTarget = touchTarget)
            Text(
                text = stringResource(R.string.settings_title),
                style = ru.e1173.nookast.designsystem.NookType.sectionTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        SettingsSection.entries.forEach { section ->
            val active = section == selected
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = touchTarget.coerceAtLeast(52.dp))
                    .clip(ru.e1173.nookast.designsystem.NookShapes.m)
                    .background(if (active) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) else Color.Transparent)
                    .clickable { onSectionSelected(section) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SettingsSectionGlyph(section, glyphSize = 30.dp, solid = true)
                Text(
                    text = stringResource(section.titleResource),
                    style = ru.e1173.nookast.designsystem.NookType.barTitle,
                    fontWeight = ru.e1173.nookast.designsystem.NookType.selectedWeight(active),
                    color = if (active) ru.e1173.nookast.designsystem.LocalNookastColors.current.accentText else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SettingsSectionRow(
    section: SettingsSection,
    selected: Boolean,
    touchTarget: Dp,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = touchTarget.coerceAtLeast(64.dp))
            .clip(ru.e1173.nookast.designsystem.NookShapes.m)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) else Color.Transparent)
            .focusRing(ru.e1173.nookast.designsystem.NookShapes.m)
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SettingsSectionGlyph(section, glyphSize = 30.dp, solid = true)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(section.titleResource),
                style = ru.e1173.nookast.designsystem.NookType.barTitle,
                color = if (selected) ru.e1173.nookast.designsystem.LocalNookastColors.current.accentText else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(section.descriptionResource),
                style = ru.e1173.nookast.designsystem.NookType.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Icon(
            NookIcons.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun SettingsRoundBack(onBack: () -> Unit, touchTarget: Dp) {
    ru.e1173.nookast.designsystem.NookHeaderDisc(ru.e1173.nookast.designsystem.NookIcons.ChevronLeft, stringResource(R.string.back), onBack)
}

/** A section screen's bar: round back button and the section name centred, 17/600 like iOS. */
@Composable
private fun SettingsHeader(
    title: String,
    onBack: () -> Unit,
    touchTarget: Dp,
) {
    Box(
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.align(Alignment.CenterStart)) {
            SettingsRoundBack(onBack = onBack, touchTarget = touchTarget)
        }
        Text(
            text = title,
            style = ru.e1173.nookast.designsystem.NookType.barTitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 64.dp),
        )
    }
}

@Composable
private fun SettingsSectionContent(
    section: SettingsSection,
    showHeading: Boolean,
    wideLayout: Boolean = showHeading,
    serverUrl: String,
    queueState: QueueUiState,
    playbackState: PlaybackUiState,
    largeControls: Boolean,
    touchTarget: Dp,
    themeMode: AppThemeMode,
    onRefresh: () -> Unit,
    onRunDiagnostics: () -> Unit,
    onSignOut: () -> Unit,
    onThemeModeChanged: (AppThemeMode) -> Unit,
    onDownloadWholeEpisodeChanged: (Boolean) -> Unit,
    onAheadCacheChanged: (Int) -> Unit,
    onSeekBackSecondsChanged: (Int) -> Unit,
    onSeekForwardSecondsChanged: (Int) -> Unit,
    onPlaybackStartQueuePositionChanged: (QueueInsertionPosition) -> Unit,
    onManualQueuePositionChanged: (QueueInsertionPosition) -> Unit,
    onQueueSwipeStartActionChanged: (QueueSwipeAction) -> Unit,
    onQueueSwipeEndActionChanged: (QueueSwipeAction) -> Unit,
    onClearAheadCache: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (showHeading) {
            Text(
                text = stringResource(section.titleResource),
                style = ru.e1173.nookast.designsystem.NookType.display,
            )
        }
        Text(
            text = stringResource(section.descriptionResource),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(
                top = if (showHeading) 4.dp else 0.dp,
                bottom = 16.dp,
            ),
        )
        when (section) {
            SettingsSection.PLAYER -> PlayerSettingsContent(
                queueState = queueState,
                playbackState = playbackState,
                wideLayout = wideLayout,
                touchTarget = touchTarget,
                onDownloadWholeEpisodeChanged = onDownloadWholeEpisodeChanged,
                onAheadCacheChanged = onAheadCacheChanged,
                onSeekBackSecondsChanged = onSeekBackSecondsChanged,
                onSeekForwardSecondsChanged = onSeekForwardSecondsChanged,
                onPlaybackStartQueuePositionChanged =
                    onPlaybackStartQueuePositionChanged,
                onManualQueuePositionChanged = onManualQueuePositionChanged,
                onQueueSwipeStartActionChanged = onQueueSwipeStartActionChanged,
                onQueueSwipeEndActionChanged = onQueueSwipeEndActionChanged,
                onClearAheadCache = onClearAheadCache,
            )

            SettingsSection.APPEARANCE -> {
                AppearanceSettingsContent(
                    themeMode = themeMode,
                    touchTarget = touchTarget,
                    onThemeModeChanged = onThemeModeChanged,
                )
                DashboardPlaylistsPanel(queueState.dashboard?.playlists.orEmpty(), touchTarget)
            }

            SettingsSection.SERVER -> ServerSettingsContent(
                serverUrl = serverUrl,
                queueState = queueState,
                playbackState = playbackState,
                largeControls = largeControls,
                touchTarget = touchTarget,
                onRefresh = onRefresh,
                onRunDiagnostics = onRunDiagnostics,
                onSignOut = onSignOut,
            )

            SettingsSection.DIAGNOSTICS -> ControlJournalPanel(touchTarget = touchTarget)

            SettingsSection.ABOUT -> {
                AboutSettingsContent(serverUrl)
                UpdateSettingsPanel(touchTarget)
                LicensesPanel()
            }
        }
    }
}

/**
 * «О приложении» (user, 27.10.2026): what the app is and which build runs where — the facts a
 * bug report from the phone, the Fold or the car needs. The server is named by its host only.
 */
@Composable
private fun AboutSettingsContent(serverUrl: String) {
    val configuration = LocalConfiguration.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val serverVersion by androidx.compose.runtime.produceState("—") {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            ru.e1173.nookast.data.local.SyncDatabase.open(context).syncV8Dao().serverSettings()?.serverVersion
                ?.trim()?.removePrefix("v")?.takeIf(String::isNotBlank)
        } ?: "—"
    }
    val rows = listOf(
        R.string.about_delivery to ru.e1173.nookast.BuildConfig.DELIVERY,
        R.string.about_version to ru.e1173.nookast.BuildConfig.VERSION_NAME,
        // The commit the build came from (plan 8.3), marked when the tree had uncommitted changes.
        R.string.about_commit to if (ru.e1173.nookast.BuildConfig.GIT_DIRTY) {
            stringResource(R.string.about_commit_dirty, ru.e1173.nookast.BuildConfig.GIT_SHA)
        } else {
            ru.e1173.nookast.BuildConfig.GIT_SHA
        },
        R.string.about_build to stringResource(
            R.string.about_build_value,
            ru.e1173.nookast.BuildConfig.VERSION_CODE,
            stringResource(if (ru.e1173.nookast.BuildConfig.BUILD_TYPE == "debug") R.string.about_build_debug else R.string.about_build_release),
        ),
        R.string.about_android to stringResource(R.string.about_android_value, android.os.Build.VERSION.RELEASE, android.os.Build.VERSION.SDK_INT),
        R.string.about_device to "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
        R.string.about_screen to stringResource(
            R.string.about_screen_value,
            configuration.screenWidthDp,
            configuration.screenHeightDp,
            configuration.densityDpi,
        ),
        R.string.about_server to (runCatching { serverUrl.toUri().host }.getOrNull() ?: "—"),
        // The version the sync last read from the server (user, 11.11.2026).
        R.string.about_server_version to serverVersion,
    )
    SettingsPanel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            // The launcher icon's two layers; painterResource cannot read an adaptive icon.
            Box(
                Modifier
                    .size(56.dp)
                    .clip(ru.e1173.nookast.designsystem.NookShapes.m)
                    .background(Brush.verticalGradient(ru.e1173.nookast.designsystem.NookFixedColors.LauncherGradient)),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.foundation.Image(
                    androidx.compose.ui.res.painterResource(R.drawable.ic_launcher_foreground_art),
                    contentDescription = null,
                    modifier = Modifier.requiredSize(84.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.app_name),
                    style = ru.e1173.nookast.designsystem.NookType.sectionTitle,
                )
                Text(
                    stringResource(R.string.about_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    SettingsPanel {
        rows.forEachIndexed { index, (label, value) ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(label),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    value,
                    style = ru.e1173.nookast.designsystem.NookType.body,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1.4f),
                )
            }
            if (index != rows.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
private fun PlayerSettingsContent(
    queueState: QueueUiState,
    playbackState: PlaybackUiState,
    wideLayout: Boolean,
    touchTarget: Dp,
    onDownloadWholeEpisodeChanged: (Boolean) -> Unit,
    onAheadCacheChanged: (Int) -> Unit,
    onSeekBackSecondsChanged: (Int) -> Unit,
    onSeekForwardSecondsChanged: (Int) -> Unit,
    onPlaybackStartQueuePositionChanged: (QueueInsertionPosition) -> Unit,
    onManualQueuePositionChanged: (QueueInsertionPosition) -> Unit,
    onQueueSwipeStartActionChanged: (QueueSwipeAction) -> Unit,
    onQueueSwipeEndActionChanged: (QueueSwipeAction) -> Unit,
    onClearAheadCache: () -> Unit,
) {
    val playerSettings by androidx.lifecycle.viewmodel.compose.viewModel<PlayerSettingsViewModel>().settings.collectAsStateWithLifecycle()
    SettingsGroupLabel(R.string.settings_seek_intervals)
    if (wideLayout) {
        SettingsPanel {
            SeekSettingBody(
                titleResource = R.string.settings_seek_back_title,
                descriptionResource = R.string.settings_seek_back_description,
                seconds = playbackState.seekBackSeconds,
                onSecondsChanged = onSeekBackSecondsChanged,
            )
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 6.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            SeekSettingBody(
                titleResource = R.string.settings_seek_forward_title,
                descriptionResource = R.string.settings_seek_forward_description,
                seconds = playbackState.seekForwardSeconds,
                onSecondsChanged = onSeekForwardSecondsChanged,
            )
        }
    } else {
        SeekSetting(
            titleResource = R.string.settings_seek_back_title,
            descriptionResource = R.string.settings_seek_back_description,
            seconds = playbackState.seekBackSeconds,
            onSecondsChanged = onSeekBackSecondsChanged,
        )
        Spacer(Modifier.height(12.dp))
        SeekSetting(
            titleResource = R.string.settings_seek_forward_title,
            descriptionResource = R.string.settings_seek_forward_description,
            seconds = playbackState.seekForwardSeconds,
            onSecondsChanged = onSeekForwardSecondsChanged,
        )
    }

    SettingsGroupLabel(
        titleResource = R.string.settings_queue_insertion,
        modifier = Modifier.padding(top = 14.dp),
    )
    if (wideLayout) {
        SettingsPanel {
            QueueInsertionSettingBody(
                title = stringResource(R.string.settings_queue_on_playback_title),
                description = stringResource(R.string.settings_queue_on_playback_description),
                selected = playerSettings.playbackStartQueuePosition,
                onSelected = onPlaybackStartQueuePositionChanged,
                wideLayout = true,
            )
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            QueueInsertionSettingBody(
                title = stringResource(R.string.settings_queue_manual_title),
                description = stringResource(R.string.settings_queue_manual_description),
                selected = playerSettings.manualQueuePosition,
                onSelected = onManualQueuePositionChanged,
                wideLayout = true,
                afterCurrent = true,
            )
        }
    } else {
        QueueInsertionSetting(
            title = stringResource(R.string.settings_queue_on_playback_title),
            description = stringResource(R.string.settings_queue_on_playback_description),
            selected = playerSettings.playbackStartQueuePosition,
            onSelected = onPlaybackStartQueuePositionChanged,
        )
        Spacer(Modifier.height(12.dp))
        QueueInsertionSetting(
            title = stringResource(R.string.settings_queue_manual_title),
            description = stringResource(R.string.settings_queue_manual_description),
            selected = playerSettings.manualQueuePosition,
            onSelected = onManualQueuePositionChanged,
            afterCurrent = true,
        )
    }

    SettingsGroupLabel(
        titleResource = R.string.settings_queue_swipe_actions,
        modifier = Modifier.padding(top = 14.dp),
    )
    if (wideLayout) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            QueueSwipeSetting(
                title = stringResource(R.string.settings_queue_swipe_start_title),
                description = stringResource(R.string.settings_queue_swipe_start_description),
                selected = playerSettings.queueSwipeStartAction,
                onSelected = onQueueSwipeStartActionChanged,
                touchTarget = touchTarget,
                modifier = Modifier.weight(1f),
            )
            QueueSwipeSetting(
                title = stringResource(R.string.settings_queue_swipe_end_title),
                description = stringResource(R.string.settings_queue_swipe_end_description),
                selected = playerSettings.queueSwipeEndAction,
                onSelected = onQueueSwipeEndActionChanged,
                touchTarget = touchTarget,
                modifier = Modifier.weight(1f),
            )
        }
    } else {
        QueueSwipeSetting(
            title = stringResource(R.string.settings_queue_swipe_start_title),
            description = stringResource(R.string.settings_queue_swipe_start_description),
            selected = playerSettings.queueSwipeStartAction,
            onSelected = onQueueSwipeStartActionChanged,
            touchTarget = touchTarget,
        )
        Spacer(Modifier.height(12.dp))
        QueueSwipeSetting(
            title = stringResource(R.string.settings_queue_swipe_end_title),
            description = stringResource(R.string.settings_queue_swipe_end_description),
            selected = playerSettings.queueSwipeEndAction,
            onSelected = onQueueSwipeEndActionChanged,
            touchTarget = touchTarget,
        )
    }

    SettingsGroupLabel(R.string.offline_and_cache, Modifier.padding(top = 14.dp))
    OfflineSettingsPanel(
        queueState = queueState,
        touchTarget = touchTarget,
        onDownloadWholeEpisodeChanged = onDownloadWholeEpisodeChanged,
        onAheadCacheChanged = onAheadCacheChanged,
        onClearAheadCache = onClearAheadCache,
    )
}

@Composable
private fun OfflineSettingsPanel(
    queueState: QueueUiState,
    touchTarget: Dp,
    onDownloadWholeEpisodeChanged: (Boolean) -> Unit,
    onAheadCacheChanged: (Int) -> Unit,
    onClearAheadCache: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playerSettings by androidx.lifecycle.viewmodel.compose.viewModel<PlayerSettingsViewModel>().settings.collectAsStateWithLifecycle()
    SettingsPanel(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.download_on_playback),
                    style = ru.e1173.nookast.designsystem.NookType.barTitle,
                )
                Text(
                    text = stringResource(R.string.download_on_playback_help),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            NookSwitch(
                checked = playerSettings.downloadWholeEpisodeOnPlayback,
                onCheckedChange = onDownloadWholeEpisodeChanged,
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_ahead_cache_title),
                    style = ru.e1173.nookast.designsystem.NookType.barTitle,
                )
                Text(
                    text = stringResource(R.string.settings_ahead_cache_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Text(
                text = stringResource(
                    R.string.settings_megabytes_value,
                    playerSettings.aheadCacheMegabytes,
                ),
                style = ru.e1173.nookast.designsystem.NookType.heroTitle,
                color = ru.e1173.nookast.designsystem.LocalNookastColors.current.accentText,
                maxLines = 1,
            )
        }
        NookSlider(
            value = playerSettings.aheadCacheMegabytes.toFloat(),
            onValueChange = { value ->
                onAheadCacheChanged(
                    (value / CACHE_STEP_MB).roundToInt() * CACHE_STEP_MB,
                )
            },
            valueRange = PlaybackPreferences.MIN_AHEAD_CACHE_MB.toFloat()..PlaybackPreferences.MAX_AHEAD_CACHE_MB.toFloat(),
            modifier = Modifier.padding(top = 8.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(
                    R.string.settings_megabytes_value,
                    PlaybackPreferences.MIN_AHEAD_CACHE_MB,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(
                    R.string.settings_megabytes_value,
                    PlaybackPreferences.MAX_AHEAD_CACHE_MB,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(
                R.string.storage_usage,
                formatStorageBytes(queueState.aheadCacheBytes),
                formatStorageBytes(queueState.offlineDownloadBytes),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp),
        )
        // Only a disk cache left by an older version can be cleared; streams now live in RAM.
        if (queueState.aheadCacheBytes > 0L || queueState.aheadCacheClearPending) NookButton(size = NookButtonSize.L, variant = NookButtonVariant.Outline,
            onClick = onClearAheadCache,
            enabled = !queueState.aheadCacheClearPending,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = touchTarget.compactRow())
                .padding(top = 10.dp),
        ) {
            Text(
                stringResource(
                    if (queueState.aheadCacheClearPending) {
                        R.string.cache_clear_pending
                    } else {
                        R.string.clear_ahead_cache
                    },
                ),
            )
        }
    }
}

@Composable
private fun QueueSwipeSetting(
    title: String,
    description: String,
    selected: QueueSwipeAction,
    onSelected: (QueueSwipeAction) -> Unit,
    touchTarget: Dp,
    modifier: Modifier = Modifier,
) {
    SettingsPanel(modifier = modifier) {
        Text(title, style = ru.e1173.nookast.designsystem.NookType.barTitle)
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 3.dp, bottom = 8.dp),
        )
        QueueSwipeAction.entries.forEachIndexed { index, action ->
            ChoiceRow(
                title = stringResource(action.labelResource()),
                description = stringResource(action.descriptionResource()),
                selected = selected == action,
                onClick = { onSelected(action) },
                touchTarget = touchTarget,
            )
            if (index != QueueSwipeAction.entries.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun QueueInsertionSetting(
    title: String,
    description: String,
    selected: QueueInsertionPosition,
    onSelected: (QueueInsertionPosition) -> Unit,
    afterCurrent: Boolean = false,
) {
    SettingsPanel {
        QueueInsertionSettingBody(
            title = title,
            description = description,
            selected = selected,
            onSelected = onSelected,
            wideLayout = false,
            afterCurrent = afterCurrent,
        )
    }
}

@Composable
private fun QueueInsertionSettingBody(
    title: String,
    description: String,
    selected: QueueInsertionPosition,
    onSelected: (QueueInsertionPosition) -> Unit,
    wideLayout: Boolean,
    afterCurrent: Boolean = false,
) {
    if (wideLayout) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = ru.e1173.nookast.designsystem.NookType.barTitle,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            QueuePositionChoices(
                selected = selected,
                onSelected = onSelected,
                afterCurrent = afterCurrent,
                modifier = Modifier.width(if (afterCurrent) 380.dp else 250.dp),
            )
        }
    } else {
        Text(
            text = title,
            style = ru.e1173.nookast.designsystem.NookType.barTitle,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 3.dp, bottom = 10.dp),
        )
        QueuePositionChoices(selected = selected, onSelected = onSelected, afterCurrent = afterCurrent)
    }
}

@Composable
private fun QueuePositionChoices(
    selected: QueueInsertionPosition,
    onSelected: (QueueInsertionPosition) -> Unit,
    modifier: Modifier = Modifier,
    afterCurrent: Boolean = false,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (afterCurrent) {
            QueuePositionChip(
                title = stringResource(R.string.settings_queue_after_current),
                selected = selected == QueueInsertionPosition.AFTER_CURRENT,
                onClick = { onSelected(QueueInsertionPosition.AFTER_CURRENT) },
                modifier = Modifier.weight(1f),
            )
        }
        QueuePositionChip(
            title = stringResource(R.string.settings_queue_first),
            selected = selected == QueueInsertionPosition.FIRST,
            onClick = { onSelected(QueueInsertionPosition.FIRST) },
            modifier = Modifier.weight(1f),
        )
        QueuePositionChip(
            title = stringResource(R.string.settings_queue_last),
            selected = selected == QueueInsertionPosition.LAST,
            onClick = { onSelected(QueueInsertionPosition.LAST) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun QueuePositionChip(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 44.dp),
        shape = ru.e1173.nookast.designsystem.NookShapes.m,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = if (selected) {
            null
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = title,
                style = ru.e1173.nookast.designsystem.NookType.rowTitle,
                fontWeight = ru.e1173.nookast.designsystem.NookType.selectedWeight(selected),
                color = if (selected) ru.e1173.nookast.designsystem.LocalNookastColors.current.accentText
                else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun SeekSetting(
    titleResource: Int,
    descriptionResource: Int,
    seconds: Int,
    onSecondsChanged: (Int) -> Unit,
) {
    SettingsPanel {
        SeekSettingBody(
            titleResource = titleResource,
            descriptionResource = descriptionResource,
            seconds = seconds,
            onSecondsChanged = onSecondsChanged,
        )
    }
}

@Composable
private fun SeekSettingBody(
    titleResource: Int,
    descriptionResource: Int,
    seconds: Int,
    onSecondsChanged: (Int) -> Unit,
) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(titleResource),
                    style = ru.e1173.nookast.designsystem.NookType.barTitle,
                )
                Text(
                    text = stringResource(descriptionResource),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Text(
                text = pluralStringResource(
                    R.plurals.settings_seconds_value,
                    seconds,
                    seconds,
                ),
                style = ru.e1173.nookast.designsystem.NookType.heroTitle,
                color = ru.e1173.nookast.designsystem.LocalNookastColors.current.accentText,
                maxLines = 1,
            )
        }
        NookSlider(
            value = seconds.toFloat(),
            onValueChange = { onSecondsChanged(it.roundToInt()) },
            valueRange = PlaybackPreferences.MIN_SEEK_SECONDS.toFloat()..PlaybackPreferences.MAX_SEEK_SECONDS.toFloat(),
            modifier = Modifier.padding(top = 8.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = pluralStringResource(
                    R.plurals.settings_seconds_value,
                    PlaybackPreferences.MIN_SEEK_SECONDS,
                    PlaybackPreferences.MIN_SEEK_SECONDS,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = pluralStringResource(
                    R.plurals.settings_seconds_value,
                    PlaybackPreferences.MAX_SEEK_SECONDS,
                    PlaybackPreferences.MAX_SEEK_SECONDS,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
}

@Composable
private fun AppearanceSettingsContent(
    themeMode: AppThemeMode,
    touchTarget: Dp,
    onThemeModeChanged: (AppThemeMode) -> Unit,
) {
    SettingsGroupLabel(R.string.theme_mode)
    SettingsPanel {
        AppThemeMode.entries.forEachIndexed { index, mode ->
            ChoiceRow(
                title = stringResource(mode.labelResource()),
                description = stringResource(mode.descriptionResource()),
                selected = themeMode == mode,
                onClick = { onThemeModeChanged(mode) },
                touchTarget = touchTarget,
            )
            if (index != AppThemeMode.entries.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }

    SettingsGroupLabel(
        titleResource = R.string.settings_color_palette,
        modifier = Modifier.padding(top = 14.dp),
    )
    PalettePanel(touchTarget)

    SettingsGroupLabel(
        titleResource = R.string.feed_title,
        modifier = Modifier.padding(top = 14.dp),
    )
    FeedCountPanel()
}

/**
 * Smart playlists on the Dashboard (user, 27.10.2026): every playlist is shown unless unticked
 * here; the choice is a device preference, not a server one.
 */
@Composable
private fun DashboardPlaylistsPanel(playlists: List<ru.e1173.nookast.data.remote.DashboardPlaylist>, touchTarget: Dp) {
    if (playlists.isEmpty()) return
    val preferences: ru.e1173.nookast.preferences.AppPreferencesViewModel = viewModel()
    val display by preferences.state.collectAsStateWithLifecycle()
    SettingsGroupLabel(
        titleResource = R.string.settings_dashboard_playlists,
        modifier = Modifier.padding(top = 14.dp),
    )
    SettingsPanel {
        playlists.forEachIndexed { index, playlist ->
            val shown = playlist.playlistId !in display.hiddenDashboardPlaylists
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = touchTarget.compactRow())
                    .clip(ru.e1173.nookast.designsystem.NookShapes.m)
                    .clickable { preferences.setDashboardPlaylistHidden(playlist.playlistId, shown) }
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PlaylistGlyph(playlist.iconName, ru.e1173.nookast.designsystem.LocalNookastColors.current.accentText, Modifier.size(22.dp))
                Text(playlist.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                androidx.compose.material3.Checkbox(
                    checked = shown,
                    onCheckedChange = { preferences.setDashboardPlaylistHidden(playlist.playlistId, !it) },
                )
            }
            if (index != playlists.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

/** How many of the newest episodes the Feed shows (5…100, 23 by default; user, 26.10.2026). */
@Composable
private fun FeedCountPanel() {
    val preferences: ru.e1173.nookast.preferences.AppPreferencesViewModel = viewModel()
    val display by preferences.state.collectAsStateWithLifecycle()
    var count by remember(display.feedEpisodeCount) { mutableStateOf(display.feedEpisodeCount) }
    SettingsPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.feed_count_title),
                    style = ru.e1173.nookast.designsystem.NookType.barTitle,
                )
                Text(
                    text = stringResource(R.string.feed_count_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Text(
                text = count.toString(),
                style = ru.e1173.nookast.designsystem.NookType.heroTitle,
                color = ru.e1173.nookast.designsystem.LocalNookastColors.current.accentText,
            )
        }
        NookSlider(
            value = count.toFloat(),
            onValueChange = { count = it.roundToInt() },
            onValueChangeFinished = { preferences.setFeedEpisodeCount(count) },
            valueRange = ru.e1173.nookast.data.remote.MIN_FEED_EPISODE_COUNT.toFloat()..ru.e1173.nookast.data.remote.MAX_FEED_EPISODE_COUNT.toFloat(),
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun PalettePanel(touchTarget: Dp) {
    // DEC-017 trial (D2): the four approved colour themes; geometry and type are shared.
    val preferences: ru.e1173.nookast.preferences.AppPreferencesViewModel = viewModel()
    val display by preferences.state.collectAsStateWithLifecycle()
    SettingsPanel {
        Text(
            text = stringResource(R.string.settings_color_palette_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        ru.e1173.nookast.designsystem.ThemePalette.entries.forEachIndexed { index, palette ->
            val light = ru.e1173.nookast.designsystem.nookastColors(palette, dark = false)
            val dark = ru.e1173.nookast.designsystem.nookastColors(palette, dark = true)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = touchTarget.compactRow())
                    .clip(ru.e1173.nookast.designsystem.NookShapes.l)
                    // The new theme opens as a circle from this touch (design audit 5.7).
                    .themeRevealSource()
                    .clickable { preferences.setPalette(palette) }
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val chosen = display.palette == palette
                Box(
                    Modifier
                        .size(20.dp)
                        .border(
                            width = if (chosen) 6.dp else 2.dp,
                            color = if (chosen) ru.e1173.nookast.designsystem.LocalNookastColors.current.accentText else MaterialTheme.colorScheme.outline,
                            shape = CircleShape,
                        ),
                )
                Text(
                    text = stringResource(palette.labelResource()),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                listOf(light.background, light.accent, dark.background, dark.accent).forEach { swatch ->
                    Box(
                        Modifier
                            .size(22.dp)
                            .background(swatch, CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                    )
                }
            }
            if (index != ru.e1173.nookast.designsystem.ThemePalette.entries.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 2.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = touchTarget.compactRow())
                .clip(ru.e1173.nookast.designsystem.NookShapes.s)
                .clickable { preferences.setArtworkTint(!display.artworkTint) }
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_artwork_tint),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(R.string.settings_artwork_tint_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            NookSwitch(checked = display.artworkTint, onCheckedChange = preferences::setArtworkTint)
        }
        // The panel's own divider between the two switches (design audit, 12.11.2026).
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 2.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        // «Light follows the voice» (design audit 5.8, DEC-058): off by default.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = touchTarget.compactRow())
                .clip(ru.e1173.nookast.designsystem.NookShapes.s)
                .clickable { preferences.setVoiceLight(!display.voiceLight) }
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_voice_light),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(R.string.settings_voice_light_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
                // Said here too: a switch that is on and does nothing must say so (audit of З, R7).
                val soundContext = androidx.compose.ui.platform.LocalContext.current
                val pausesShortened = remember(soundContext) { ru.e1173.nookast.player.SoundSettingsStore.open(soundContext).global().shortenPauses }
                if (pausesShortened) {
                    Text(
                        text = stringResource(R.string.settings_voice_light_paused),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            NookSwitch(checked = display.voiceLight, onCheckedChange = preferences::setVoiceLight)
        }
    }

    // «Immersion» (user, 29.10.2026): each sky and the New Year mood switch on their own.
    SettingsGroupLabel(
        titleResource = R.string.settings_immersion,
        modifier = Modifier.padding(top = 14.dp),
    )
    SettingsPanel {
        Text(
            text = stringResource(R.string.settings_immersion_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 4.dp),
        )
        SkyPlaceSettings(display, preferences)
        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp), color = MaterialTheme.colorScheme.outlineVariant)
        // The skies need the place: without it their switches cannot be turned on (user, 2.11.2026).
        val placeKnown = display.skyPlace != null
        listOf(
            Triple(ru.e1173.nookast.designsystem.SkyMood.MORNING, R.string.settings_sky_morning, display.skyMorning),
            Triple(ru.e1173.nookast.designsystem.SkyMood.WHITE_NIGHT, R.string.settings_sky_white_nights, display.skyWhiteNights),
            Triple(ru.e1173.nookast.designsystem.SkyMood.EVENING, R.string.settings_sky_evening, display.skyEvening),
        ).forEach { (mood, label, enabled) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = touchTarget.compactRow())
                    .clip(ru.e1173.nookast.designsystem.NookShapes.s)
                    .clickable(enabled = placeKnown) { preferences.setSkyMood(mood, !enabled) }
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(label),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (placeKnown) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                NookSwitch(checked = enabled && placeKnown, enabled = placeKnown, onCheckedChange = { preferences.setSkyMood(mood, it) })
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 2.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
        // New Year mood (DEC-018): off, always in any theme, or by dates in the winter theme only.
        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(stringResource(R.string.settings_new_year), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.settings_new_year_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    ru.e1173.nookast.designsystem.NewYearMode.OFF to R.string.settings_new_year_off,
                    ru.e1173.nookast.designsystem.NewYearMode.ALWAYS to R.string.settings_new_year_always,
                    ru.e1173.nookast.designsystem.NewYearMode.DATES to R.string.settings_new_year_dates,
                ).forEach { (mode, label) ->
                    androidx.compose.material3.FilterChip(
                        selected = display.newYearMode == mode,
                        onClick = { preferences.setNewYearMode(mode) },
                        label = { Text(stringResource(label)) },
                    )
                }
            }
        }
    }
}

private fun ru.e1173.nookast.designsystem.ThemePalette.labelResource(): Int = when (this) {
    ru.e1173.nookast.designsystem.ThemePalette.LAVENDER -> R.string.settings_palette_lavender
    ru.e1173.nookast.designsystem.ThemePalette.AUTUMN -> R.string.settings_palette_autumn
    ru.e1173.nookast.designsystem.ThemePalette.WINTER -> R.string.settings_palette_winter
    ru.e1173.nookast.designsystem.ThemePalette.MINT -> R.string.settings_palette_mint
    ru.e1173.nookast.designsystem.ThemePalette.SIGNATURE -> R.string.settings_palette_signature
}

@Composable
private fun ServerSettingsContent(
    serverUrl: String,
    queueState: QueueUiState,
    playbackState: PlaybackUiState,
    largeControls: Boolean,
    touchTarget: Dp,
    onRefresh: () -> Unit,
    onRunDiagnostics: () -> Unit,
    onSignOut: () -> Unit,
) {
    val pendingOperations = queueState.pendingProgressCount + queueState.pendingQueueCount +
        queueState.pendingPodcastCount + queueState.pendingSavedEpisodeCount
    val syncAccount: SyncAccountViewModel = viewModel()
    val syncStatus by syncAccount.status.collectAsStateWithLifecycle()
    val deadLetters by syncAccount.deadLetters.collectAsStateWithLifecycle()
    SettingsGroupLabel(R.string.settings_connection)
    SettingsPanel {
        Text(
            text = serverUrl,
            style = ru.e1173.nookast.designsystem.NookType.sectionTitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = serverAvailabilityText(queueState),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            text = synchronizationText(queueState, pendingOperations),
            style = ru.e1173.nookast.designsystem.NookType.barTitle,
            color = if (pendingOperations == 0 && queueState.syncError == null) {
                ru.e1173.nookast.designsystem.NookFixedColors.SyncedGreen
            } else {
                MaterialTheme.colorScheme.tertiary
            },
            modifier = Modifier.padding(top = 18.dp),
        )
        Text(
            text = pluralStringResource(
                R.plurals.settings_pending_operations,
                pendingOperations,
                pendingOperations,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        NookButton(size = NookButtonSize.L,
            onClick = onRefresh,
            enabled = !queueState.isRefreshing,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = touchTarget.compactRow())
                .padding(top = 16.dp),
        ) {
            if (queueState.isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = androidx.compose.material3.LocalContentColor.current,
                )
            } else {
                Text(stringResource(R.string.refresh))
            }
        }
    }

    ServerAccountPanel(touchTarget)

    TransferPanel(touchTarget)

    if (syncStatus.needsSignIn || deadLetters.isNotEmpty()) {
        SettingsGroupLabel(
            titleResource = R.string.sync_attention_title,
            modifier = Modifier.padding(top = 14.dp),
        )
        SettingsPanel {
            SyncAttentionContent(
                needsSignIn = syncStatus.needsSignIn,
                deadLetters = deadLetters,
                // Signing in again goes through the sign-out barrier; "keep" preserves the data.
                onSignInAgain = onSignOut,
                onRetry = syncAccount::retry,
                onDiscard = syncAccount::discard,
            )
        }
    }

    SettingsGroupLabel(
        titleResource = R.string.settings_status,
        modifier = Modifier.padding(top = 14.dp),
    )
    SettingsPanel {
        StatusGrid(
            twoColumns = largeControls,
            items = listOf(
                stringResource(R.string.settings_last_sync) to
                    lastSynchronizationText(queueState.lastUpdatedEpochMs),
                stringResource(R.string.settings_last_error) to
                    (queueState.error?.let { stringResource(it.stringResource()) }
                        ?: if (queueState.syncError == null) {
                            stringResource(R.string.settings_none)
                        } else {
                            stringResource(R.string.settings_attention_required)
                        }),
                stringResource(R.string.settings_pending_operations_label) to
                    pendingOperations.toString(),
                stringResource(R.string.settings_playback_source) to
                    playbackSourceText(playbackState),
            ),
        )
    }

    SettingsGroupLabel(
        titleResource = R.string.settings_actions,
        modifier = Modifier.padding(top = 14.dp),
    )
    SettingsPanel {
        ActionRow(
            title = stringResource(R.string.extended_diagnostics),
            description = stringResource(R.string.settings_diagnostics_description),
            actionLabel = stringResource(
                if (playbackState.diagnosticsRunning) {
                    R.string.diagnostics_running
                } else {
                    R.string.settings_open
                },
            ),
            enabled = !playbackState.diagnosticsRunning,
            touchTarget = touchTarget,
            onClick = onRunDiagnostics,
        )
        playbackState.diagnosticResults.forEach { result ->
            val host = result.requestedHost.ifBlank { result.resolvedHost }
            Text(
                text = if (result.isAvailable) {
                    stringResource(R.string.diagnostic_available, host, result.latencyMs)
                } else {
                    stringResource(R.string.diagnostic_unavailable, host)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        ActionRow(
            title = stringResource(R.string.settings_sign_out_title),
            description = stringResource(R.string.settings_sign_out_description),
            actionLabel = stringResource(R.string.settings_sign_out_action),
            enabled = true,
            destructive = true,
            touchTarget = touchTarget,
            onClick = onSignOut,
        )
    }
}

@Composable
private fun StatusGrid(
    items: List<Pair<String, String>>,
    twoColumns: Boolean,
) {
    if (twoColumns) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items.chunked(2).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowItems.forEach { item ->
                        StatusTile(
                            label = item.first,
                            value = item.second,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    } else {
        Column {
            items.forEachIndexed { index, item ->
                StatusRow(label = item.first, value = item.second)
                if (index != items.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun StatusTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .nookCard()
            .padding(horizontal = 13.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = ru.e1173.nookast.designsystem.NookType.body,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun StatusRow(
    label: String,
    value: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = ru.e1173.nookast.designsystem.NookType.body,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun ActionRow(
    title: String,
    description: String,
    actionLabel: String,
    enabled: Boolean,
    touchTarget: Dp,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
    ) {
        Text(
            text = title,
            style = ru.e1173.nookast.designsystem.NookType.barTitle,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
        )
        if (destructive) {
            NookButton(size = NookButtonSize.L,
                variant = NookButtonVariant.Destructive,
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = touchTarget.compactRow()),
            ) {
                Text(actionLabel)
            }
        } else {
            NookButton(size = NookButtonSize.L,
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = touchTarget.compactRow()),
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun ChoiceRow(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    touchTarget: Dp,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = touchTarget.compactRow())
            .clip(ru.e1173.nookast.designsystem.NookShapes.l)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
                } else {
                    Color.Transparent
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .border(
                    width = if (selected) 7.dp else 2.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                    shape = CircleShape,
                ),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = ru.e1173.nookast.designsystem.NookType.barTitle,
                fontWeight = ru.e1173.nookast.designsystem.NookType.selectedWeight(selected),
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

@Composable
internal fun SettingsGroupLabel(
    titleResource: Int,
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(titleResource),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 8.dp, bottom = 6.dp).sectionHeading(),
    )
}

@Composable
internal fun SettingsPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // The one card of the app (audit 3.2): settings moved off their tinted panels and decorations.
    Column(modifier.fillMaxWidth().nookCard().padding(13.dp)) { content() }
}

/** iOS-style tile colours of the settings list: one hue per section, only the player in the accent. */
@Composable
private fun sectionTile(section: SettingsSection): Color = when (section) {
    SettingsSection.PLAYER -> MaterialTheme.colorScheme.primary
    SettingsSection.APPEARANCE -> ru.e1173.nookast.designsystem.NookFixedColors.TileAppearance
    SettingsSection.SERVER -> ru.e1173.nookast.designsystem.NookFixedColors.TileServer
    SettingsSection.DIAGNOSTICS -> ru.e1173.nookast.designsystem.NookFixedColors.TileDiagnostics
    SettingsSection.ABOUT -> ru.e1173.nookast.designsystem.NookFixedColors.TileAbout
}

@Composable
private fun sectionAccent(section: SettingsSection): Color = when (section) {
    SettingsSection.PLAYER -> MaterialTheme.colorScheme.primary
    SettingsSection.APPEARANCE -> MaterialTheme.colorScheme.tertiary
    SettingsSection.SERVER -> ru.e1173.nookast.designsystem.NookFixedColors.serverAccent(MaterialTheme.colorScheme.background.luminance() < 0.5f)
    SettingsSection.DIAGNOSTICS -> MaterialTheme.colorScheme.secondary
    SettingsSection.ABOUT -> ru.e1173.nookast.designsystem.NookFixedColors.AboutAccent
}

@Composable
private fun SettingsSectionGlyph(
    section: SettingsSection,
    glyphSize: Dp = 38.dp,
    solid: Boolean = false,
) {
    val tile = if (solid) sectionTile(section) else sectionAccent(section)
    // Solid: an iOS-style coloured tile with a light glyph; otherwise a tinted tile.
    val accent = if (solid) Color.White else tile
    Box(
        modifier = Modifier
            .size(glyphSize)
            .background(
                if (solid) tile else tile.copy(alpha = 0.13f),
                RoundedCornerShape(if (solid) glyphSize * 0.27f else ru.e1173.nookast.designsystem.NookShapes.mRadius),
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Section icons from NookIcons (design audit 3.7).
        val icon = when (section) {
            SettingsSection.PLAYER -> ru.e1173.nookast.designsystem.NookIcons.CirclePause
            SettingsSection.APPEARANCE -> ru.e1173.nookast.designsystem.NookIcons.Sun
            SettingsSection.SERVER -> ru.e1173.nookast.designsystem.NookIcons.Wifi
            SettingsSection.DIAGNOSTICS -> ru.e1173.nookast.designsystem.NookIcons.Activity
            SettingsSection.ABOUT -> ru.e1173.nookast.designsystem.NookIcons.Info
        }
        androidx.compose.material3.Icon(icon, null, tint = accent, modifier = Modifier.size(if (solid) glyphSize * 0.6f else 20.dp))
    }
}

@Composable
private fun serverAvailabilityText(state: QueueUiState): String = when {
    state.isRefreshing -> stringResource(R.string.settings_connection_checking)
    state.error == null -> stringResource(R.string.settings_server_available)
    else -> stringResource(R.string.settings_attention_required)
}

@Composable
private fun synchronizationText(state: QueueUiState, pendingOperations: Int): String = when {
    state.syncError != null -> stringResource(R.string.settings_attention_required)
    pendingOperations > 0 -> stringResource(R.string.settings_sync_pending)
    else -> stringResource(R.string.settings_synchronized)
}

@Composable
private fun lastSynchronizationText(epochMs: Long?): String {
    if (epochMs == null) return stringResource(R.string.settings_not_yet)
    val ageMs = (System.currentTimeMillis() - epochMs).coerceAtLeast(0)
    if (ageMs < 60_000L) return stringResource(R.string.settings_just_now)
    val formatter = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
    return formatter.format(Date(epochMs))
}

@Composable
private fun playbackSourceText(state: PlaybackUiState): String = when {
    state.currentEpisodeId == null -> stringResource(R.string.settings_none)
    state.isLocalSource -> stringResource(R.string.settings_source_downloaded)
    state.isServerCopySource -> stringResource(R.string.source_pinepods_copy)
    state.isUsingPinePodsFallback -> stringResource(R.string.settings_source_pinepods)
    state.streamHost.isNotBlank() -> stringResource(R.string.source_rss_host, state.streamHost)
    else -> stringResource(R.string.settings_source_rss)
}

private fun formatStorageBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> String.format(
        Locale.getDefault(),
        "%.1f GB",
        bytes / (1024.0 * 1024.0 * 1024.0),
    )
    bytes >= 1024L * 1024L -> String.format(
        Locale.getDefault(),
        "%.1f MB",
        bytes / (1024.0 * 1024.0),
    )
    bytes >= 1024L -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}

private fun QueueSwipeAction.labelResource(): Int = when (this) {
    QueueSwipeAction.NONE -> R.string.settings_queue_swipe_none
    QueueSwipeAction.DOWNLOAD_OR_DELETE -> R.string.settings_queue_swipe_download
    QueueSwipeAction.MARK_PLAYED -> R.string.settings_queue_swipe_mark_played
    QueueSwipeAction.REMOVE_FROM_QUEUE -> R.string.settings_queue_swipe_remove
}

private fun QueueSwipeAction.descriptionResource(): Int = when (this) {
    QueueSwipeAction.NONE -> R.string.settings_queue_swipe_none_description
    QueueSwipeAction.DOWNLOAD_OR_DELETE -> R.string.settings_queue_swipe_download_description
    QueueSwipeAction.MARK_PLAYED -> R.string.settings_queue_swipe_mark_played_description
    QueueSwipeAction.REMOVE_FROM_QUEUE -> R.string.settings_queue_swipe_remove_description
}

private fun AppThemeMode.labelResource(): Int = when (this) {
    AppThemeMode.SYSTEM -> R.string.theme_system
    AppThemeMode.LIGHT -> R.string.theme_light
    AppThemeMode.DARK -> R.string.theme_dark
}

private fun AppThemeMode.descriptionResource(): Int = when (this) {
    AppThemeMode.SYSTEM -> R.string.settings_theme_system_description
    AppThemeMode.LIGHT -> R.string.settings_theme_light_description
    AppThemeMode.DARK -> R.string.settings_theme_dark_description
}

private fun QueueLoadError.stringResource(): Int = when (this) {
    QueueLoadError.AUTHENTICATION -> R.string.queue_error_authentication
    QueueLoadError.NETWORK -> R.string.queue_error_network
    QueueLoadError.SERVER -> R.string.queue_error_server
    QueueLoadError.INVALID_RESPONSE -> R.string.queue_error_invalid_response
}

private const val CACHE_STEP_MB = 8

/** Settings PinePods stores on the server for the account (user, 27.10.2026); see DEC-036. */
@Composable
private fun ServerAccountPanel(touchTarget: Dp) {
    val model: ServerSettingsViewModel = viewModel()
    val state by model.state.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(Unit) { model.load() }
    SettingsGroupLabel(
        titleResource = R.string.server_settings_title,
        modifier = Modifier.padding(top = 14.dp),
    )
    SettingsPanel {
        val settings = state.settings
        if (settings == null) {
            Text(
                text = stringResource(if (state.loading) R.string.server_settings_loading else R.string.server_settings_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!state.loading) {
                NookButton(variant = NookButtonVariant.Text, onClick = model::load, modifier = Modifier.heightIn(min = touchTarget)) {
                    Text(stringResource(R.string.refresh))
                }
            }
            return@SettingsPanel
        }
        ServerSettingTitle(R.string.server_settings_speed, R.string.server_settings_speed_description)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            val speed = settings.playbackSpeed
            QueuePositionChip(
                title = "\u2212",
                selected = false,
                onClick = { model.setPlaybackSpeed(((speed - 0.1) * 10).roundToInt() / 10.0) },
                modifier = Modifier.width(56.dp).alpha(if (speed > 0.55) 1f else 0.4f),
            )
            Text(
                text = String.format(java.util.Locale.ROOT, "%.1f\u00d7", speed),
                style = ru.e1173.nookast.designsystem.NookType.heroTitle,
                color = ru.e1173.nookast.designsystem.LocalNookastColors.current.accentText,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            QueuePositionChip(
                title = "+",
                selected = false,
                onClick = { model.setPlaybackSpeed(((speed + 0.1) * 10).roundToInt() / 10.0) },
                modifier = Modifier.width(56.dp).alpha(if (speed < 2.95) 1f else 0.4f),
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 4.dp))
        ServerSettingTitle(R.string.server_settings_auto_complete, R.string.server_settings_auto_complete_description)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            listOf(0, 30, 60, 120, 300).forEach { seconds ->
                QueuePositionChip(
                    title = if (seconds == 0) stringResource(R.string.server_settings_off) else stringResource(R.string.server_settings_seconds_short, seconds),
                    selected = settings.autoCompleteSeconds == seconds,
                    onClick = { model.setAutoCompleteSeconds(seconds) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 4.dp))
        ServerSettingTitle(R.string.server_settings_clock, null)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            listOf(24 to "14:30", 12 to "2:30 PM").forEach { (pref, sample) ->
                QueuePositionChip(sample, settings.hourPref == pref, { model.setHourPref(pref) }, Modifier.weight(1f))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            listOf("DMY" to "27.10.2026", "MDY" to "10/27/2026", "YMD" to "2026-10-27").forEach { (format, sample) ->
                QueuePositionChip(sample, settings.dateFormat == format, { model.setDateFormat(format) }, Modifier.weight(1f))
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 4.dp))
        val deviceZone = remember { java.util.TimeZone.getDefault().id }
        ServerSettingTitle(R.string.server_settings_timezone, R.string.server_settings_timezone_description)
        Text(
            text = settings.timezone,
            style = MaterialTheme.typography.titleMedium,
            color = ru.e1173.nookast.designsystem.LocalNookastColors.current.accentText,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (deviceZone != settings.timezone) {
            NookButton(variant = NookButtonVariant.Text,
                onClick = { model.setTimezone(deviceZone) },
                modifier = Modifier.heightIn(min = touchTarget.compactRow()),
                contentPadding = PaddingValues(0.dp),
            ) {
                Text(stringResource(R.string.server_settings_use_device_zone, deviceZone))
            }
        }
        if (state.failed) {
            Text(
                text = stringResource(R.string.server_settings_save_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
    Text(
        text = stringResource(R.string.server_settings_footer),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
    )
}

@Composable
private fun ServerSettingTitle(title: Int, description: Int?) {
    Text(stringResource(title), style = ru.e1173.nookast.designsystem.NookType.barTitle)
    description?.let {
        Text(
            text = stringResource(it),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** Rows keep the car's large target; elsewhere they are compact (user, 29.10.2026: too much air). */
private fun Dp.compactRow(): Dp = if (this > 60.dp) this else 40.dp

/**
 * Moving to the release package (plan 8.2): export here in the debug app, import there after
 * signing in. The export waits until nothing is pending, so no intent is left behind.
 */
@Composable
private fun TransferPanel(touchTarget: Dp) {
    val transfer: ru.e1173.nookast.transfer.TransferViewModel = viewModel()
    val state by transfer.state.collectAsStateWithLifecycle()
    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> if (uri != null) transfer.export(uri) }
    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) transfer.import(uri) }
    SettingsGroupLabel(R.string.settings_transfer, modifier = Modifier.padding(top = 14.dp))
    SettingsPanel {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                stringResource(R.string.settings_transfer_help),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val pending = state.pending
            if (pending != null) {
                Text(
                    when {
                        pending > 0 -> stringResource(R.string.settings_transfer_pending, pending)
                        state.deferred > 0 -> stringResource(R.string.settings_transfer_ready_deferred, state.deferred)
                        else -> stringResource(R.string.settings_transfer_ready)
                    },
                    style = ru.e1173.nookast.designsystem.NookType.rowTitle,
                    color = if (pending > 0) MaterialTheme.colorScheme.tertiary else ru.e1173.nookast.designsystem.NookFixedColors.SyncedGreen,
                )
            }
            state.message?.let { message ->
                val text = when (message) {
                    ru.e1173.nookast.transfer.TransferMessage.Exported -> stringResource(R.string.settings_transfer_exported)
                    ru.e1173.nookast.transfer.TransferMessage.PendingFirst -> stringResource(R.string.settings_transfer_pending_first)
                    is ru.e1173.nookast.transfer.TransferMessage.Imported -> listOfNotNull(
                        stringResource(R.string.settings_transfer_imported, message.settings, message.intents),
                        if (message.otherAccount) stringResource(R.string.settings_transfer_other_account) else null,
                    ).joinToString(" ")
                    is ru.e1173.nookast.transfer.TransferMessage.Failed -> stringResource(
                        when (message.error) {
                            ru.e1173.nookast.transfer.TransferError.NOT_A_TRANSFER -> R.string.settings_transfer_not_file
                            ru.e1173.nookast.transfer.TransferError.NEWER_FORMAT -> R.string.settings_transfer_newer
                            else -> R.string.settings_transfer_failed
                        },
                    )
                }
                Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NookButton(
                    onClick = { exportLauncher.launch("nookast-transfer.json") },
                    enabled = !state.busy && state.pending == 0,
                    modifier = Modifier.weight(1f).heightIn(min = touchTarget.compactRow()),
                ) { Text(stringResource(R.string.settings_transfer_export)) }
                NookButton(variant = NookButtonVariant.Outline,
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                    enabled = !state.busy,
                    modifier = Modifier.weight(1f).heightIn(min = touchTarget.compactRow()),
                ) { Text(stringResource(R.string.settings_transfer_import)) }
            }
        }
    }
}

/**
 * Where the sky's sun is worked out (user, 2.11.2026): not at all, once, or by itself now and then.
 * Choosing to detect asks Android for approximate location first; refused, the skies stay off.
 */
@Composable
private fun SkyPlaceSettings(
    display: ru.e1173.nookast.preferences.DisplayPreferencesState,
    preferences: ru.e1173.nookast.preferences.AppPreferencesViewModel,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var locating by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<Int?>(null) }
    var pendingMode by remember { mutableStateOf(ru.e1173.nookast.preferences.SkyPlaceMode.OFF) }
    val locateNow = {
        scope.launch {
            locating = true
            problem = null
            val place = ru.e1173.nookast.preferences.SkyLocator.locate(context)
            locating = false
            if (place != null) preferences.setSkyPlace(place) else problem = R.string.settings_sky_place_failed
        }
        Unit
    }
    val permission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        if (results.values.any { it }) {
            preferences.setSkyPlaceMode(pendingMode)
            locateNow()
        } else {
            preferences.setSkyPlaceMode(ru.e1173.nookast.preferences.SkyPlaceMode.OFF)
            problem = R.string.settings_sky_place_denied
        }
    }
    val choose = { mode: ru.e1173.nookast.preferences.SkyPlaceMode ->
        problem = null
        when {
            mode == ru.e1173.nookast.preferences.SkyPlaceMode.OFF -> preferences.setSkyPlaceMode(mode)
            ru.e1173.nookast.preferences.SkyLocator.hasPermission(context) -> {
                preferences.setSkyPlaceMode(mode)
                if (display.skyPlace == null || mode == ru.e1173.nookast.preferences.SkyPlaceMode.ONCE) locateNow()
            }
            else -> {
                pendingMode = mode
                permission.launch(ru.e1173.nookast.preferences.SkyLocator.PERMISSIONS)
            }
        }
    }
    Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
        Text(stringResource(R.string.settings_sky_place), style = MaterialTheme.typography.titleSmall)
        androidx.compose.foundation.layout.FlowRow(
            modifier = Modifier.padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                ru.e1173.nookast.preferences.SkyPlaceMode.OFF to R.string.settings_sky_place_off,
                ru.e1173.nookast.preferences.SkyPlaceMode.ONCE to R.string.settings_sky_place_once,
                ru.e1173.nookast.preferences.SkyPlaceMode.AUTO to R.string.settings_sky_place_auto,
            ).forEach { (mode, label) ->
                androidx.compose.material3.FilterChip(
                    selected = display.skyPlaceMode == mode,
                    onClick = { choose(mode) },
                    label = { Text(stringResource(label)) },
                )
            }
        }
        Text(
            stringResource(
                when (display.skyPlaceMode) {
                    ru.e1173.nookast.preferences.SkyPlaceMode.OFF -> R.string.settings_sky_place_off_help
                    ru.e1173.nookast.preferences.SkyPlaceMode.ONCE -> R.string.settings_sky_place_once_help
                    ru.e1173.nookast.preferences.SkyPlaceMode.AUTO -> R.string.settings_sky_place_auto_help
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (display.skyPlaceMode != ru.e1173.nookast.preferences.SkyPlaceMode.OFF) {
            val place = display.skyPlace
            val status = when {
                locating -> stringResource(R.string.settings_sky_place_locating)
                place != null -> stringResource(
                    R.string.settings_sky_place_known,
                    java.time.Instant.ofEpochMilli(place.fixedAtEpochMs).atZone(java.time.ZoneId.systemDefault())
                        .format(java.time.format.DateTimeFormatter.ofPattern("d MMMM, HH:mm", androidx.compose.ui.platform.LocalConfiguration.current.locales[0])),
                )
                else -> stringResource(R.string.settings_sky_place_unknown)
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(status, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                NookButton(variant = NookButtonVariant.Text, onClick = { choose(display.skyPlaceMode) }, enabled = !locating) {
                    Text(stringResource(R.string.settings_sky_place_detect))
                }
            }
        }
        val note = problem ?: if (display.skyPlace == null && display.skyPlaceMode == ru.e1173.nookast.preferences.SkyPlaceMode.OFF) R.string.settings_sky_needs_place else null
        note?.let {
            Text(
                stringResource(it),
                style = MaterialTheme.typography.bodySmall,
                color = if (problem != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
