package ru.e1173.nookast.ui

import ru.e1173.nookast.designsystem.NookButton
import ru.e1173.nookast.designsystem.NookButtonSize
import ru.e1173.nookast.designsystem.NookButtonVariant
import android.content.Intent
import androidx.core.net.toUri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import ru.e1173.nookast.R
import ru.e1173.nookast.player.PlaybackActivity
import ru.e1173.nookast.update.ReleaseRules
import ru.e1173.nookast.update.UpdateError
import ru.e1173.nookast.update.UpdateMode
import ru.e1173.nookast.update.Updater

/**
 * Settings → About → Updates (user, 2.11.2026): the mode, «Check now», what the updater knows and
 * the download under way. The offer itself is [UpdatePrompt]'s, one for every path the user starts.
 * Release builds only.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun UpdateSettingsPanel(touchTarget: Dp) {
    if (!Updater.enabled) return
    val context = LocalContext.current
    LaunchedEffect(Unit) { Updater.loadAsync(context) }
    val state by Updater.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showNotes by remember { mutableStateOf(false) }
    val askNotifications = rememberNotificationRequest()

    SettingsGroupLabel(R.string.update_title, Modifier.padding(top = 14.dp))
    SettingsPanel {
        Column(Modifier.padding(16.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    UpdateMode.AUTOMATIC to R.string.update_mode_automatic,
                    UpdateMode.NOTIFY to R.string.update_mode_notify,
                    UpdateMode.OFF to R.string.update_mode_off,
                ).forEach { (mode, label) ->
                    FilterChip(
                        selected = state.mode == mode,
                        onClick = {
                            scope.launch { Updater.setMode(context, mode) }
                            // Both modes may have news to tell: a meaningful moment to ask (OTA review).
                            if (mode != UpdateMode.OFF) askNotifications()
                        },
                        label = { Text(stringResource(label)) },
                    )
                }
            }
            Text(
                stringResource(
                    when (state.mode) {
                        UpdateMode.AUTOMATIC -> R.string.update_mode_automatic_help
                        UpdateMode.NOTIFY -> R.string.update_mode_notify_help
                        UpdateMode.OFF -> R.string.update_mode_off_help
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            val locale = LocalConfiguration.current.locales[0]
            val version = version(state)
            val checked = java.time.Instant.ofEpochMilli(state.lastCheckMs).atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("d MMMM, HH:mm", locale))
            val status = when {
                state.progress != null -> null
                state.checking -> stringResource(R.string.update_checking)
                state.error != null -> stringResource(state.error!!.message())
                state.downloaded && state.available != null -> stringResource(R.string.update_downloaded, version)
                state.available != null -> stringResource(R.string.update_available, version)
                state.lastCheckMs > 0 -> stringResource(R.string.update_latest, checked)
                else -> stringResource(R.string.update_never_checked)
            }
            if (status != null) {
                Text(
                    status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 10.dp),
                )
            } else {
                UpdateDownloadProgress(state, Modifier.padding(top = 10.dp))
            }
            // One button per row: «Download and install» does not fit half a phone width.
            Column(Modifier.fillMaxWidth().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.available != null && state.progress == null) {
                    // The same offer as everywhere else: one consent, then download and install.
                    NookButton(size = NookButtonSize.L,
                        onClick = Updater::requestPrompt,
                        modifier = Modifier.fillMaxWidth().heightIn(min = touchTarget),
                    ) {
                        Text(stringResource(if (state.downloaded) R.string.update_install else R.string.update_download_install))
                    }
                }
                NookButton(size = NookButtonSize.L, variant = NookButtonVariant.Outline,
                    onClick = { scope.launch { Updater.check(context, manual = true) } },
                    enabled = !state.checking && state.progress == null,
                    modifier = Modifier.fillMaxWidth().heightIn(min = touchTarget),
                ) { Text(stringResource(R.string.update_check_now)) }
                if (ru.e1173.nookast.update.SavedUpdate.showInSettings(state.available, state.progress != null)) {
                    SaveToDownloads(state, touchTarget)
                }
            }
            val notes = releaseNotes(state.availableBody)
            if (state.available != null && notes.isNotBlank()) {
                NookButton(variant = NookButtonVariant.Text, onClick = { showNotes = !showNotes }) { Text(stringResource(R.string.update_whats_new)) }
                if (showNotes) {
                    Text(notes, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun version(state: ru.e1173.nookast.update.UpdateState) = state.available?.removePrefix("v").orEmpty()

/** The release text in the reader's language. */
@Composable
private fun releaseNotes(body: String): String {
    val language = LocalConfiguration.current.locales[0].language
    return remember(body, language) { ReleaseRules.notesOf(body, language) }
}

/** A determinate bar, «Downloading 1.2 of 4.4 MB» and «Cancel», wherever a download shows. */
@Composable
private fun UpdateDownloadProgress(state: ru.e1173.nookast.update.UpdateState, modifier: Modifier = Modifier, cancel: Boolean = true) {
    val context = LocalContext.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        androidx.compose.material3.LinearProgressIndicator(
            progress = { (state.progress ?: 0) / 100f },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (state.totalBytes > 0) {
                    stringResource(
                        R.string.update_downloading_bytes,
                        android.text.format.Formatter.formatShortFileSize(context, state.receivedBytes),
                        android.text.format.Formatter.formatShortFileSize(context, state.totalBytes),
                    )
                } else {
                    stringResource(R.string.update_downloading, version(state), state.progress ?: 0)
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            if (cancel) NookButton(variant = NookButtonVariant.Text, onClick = Updater::cancelDownload) { Text(stringResource(R.string.control_journal_cancel)) }
        }
    }
}

/**
 * Downloads (when needed) and installs. Without the install permission it first opens Android's
 * setting for it and goes on when the user comes back having allowed it (OTA review, 3.11.2026).
 * A trimmed system shell may open nothing (user, 6.11.2026): then the offer says how to allow it
 * by hand, never silence.
 */
@Composable
private fun rememberInstallAction(): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var awaitingPermission by remember { mutableStateOf(false) }
    var requestedAtMs by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    var pausedAtMs by remember { mutableStateOf<Long?>(null) }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
        if (awaitingPermission) pausedAtMs = android.os.SystemClock.elapsedRealtime()
    }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        if (awaitingPermission && Updater.canInstall(context)) {
            awaitingPermission = false
            Updater.awaitPermission(context, false)
            Updater.permissionHelp(context, false)
            Updater.downloadAndInstall(context)
        }
    }
    return {
        if (Updater.canInstall(context)) {
            Updater.permissionHelp(context, false)
            Updater.downloadAndInstall(context)
        } else {
            awaitingPermission = true
            Updater.awaitPermission(context, true)
            pausedAtMs = null
            requestedAtMs = android.os.SystemClock.elapsedRealtime()
            val opened = listOf(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri()),
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()),
            ).firstOrNull { intent ->
                runCatching { context.startActivity(intent) }
                    .onFailure { ru.e1173.nookast.update.UpdateLog.record(context, "permission ${intent.action} failed ${it.javaClass.simpleName}") }
                    .isSuccess
            }
            ru.e1173.nookast.update.UpdateLog.record(context, "permission request action=${opened?.action ?: "none"}")
            scope.launch {
                kotlinx.coroutines.delay(PERMISSION_SCREEN_TIMEOUT_MS)
                val shown = ru.e1173.nookast.update.InstallPaths.permissionScreenOpened(requestedAtMs, pausedAtMs, PERMISSION_SCREEN_TIMEOUT_MS)
                ru.e1173.nookast.update.UpdateLog.record(context, "permission screen opened=$shown")
                if (!shown) Updater.permissionHelp(context, true)
            }
        }
    }
}

private const val PERMISSION_SCREEN_TIMEOUT_MS = 1_000L

/**
 * Starts the installer screens [Updater] asks for from this activity, for a result: the session's
 * confirmation and the classic installer (user, 6.11.2026: started from a receiver, the
 * confirmation's install button did not react on one device).
 */
@Composable
private fun InstallerLauncher(launch: Intent?, saveAs: String?) {
    val context = LocalContext.current
    // Android 9 and older save through a document screen; a trimmed shell may have none.
    val document = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/vnd.android.package-archive"),
    ) { uri -> Updater.saveToDocument(context, uri) }
    LaunchedEffect(saveAs) {
        val name = saveAs ?: return@LaunchedEffect
        runCatching { document.launch(name) }.onFailure { Updater.saveUnavailable(context) }
    }
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        @Suppress("DEPRECATION")
        val installResult = result.data?.extras?.getInt("android.intent.extra.INSTALL_RESULT")
        Updater.classicResult(context, result.resultCode, installResult)
    }
    LaunchedEffect(launch) {
        val intent = launch ?: return@LaunchedEffect
        Updater.launched()
        val multiWindow = (context as? android.app.Activity)?.isInMultiWindowMode
        ru.e1173.nookast.update.UpdateLog.record(context, "launch ${intent.action} multiWindow=$multiWindow")
        runCatching { launcher.launch(intent) }.onFailure { Updater.launchFailed(context, it) }
    }
}

/**
 * Asks for the notification permission (Android 13+) with a word on what for, once per run of the
 * app, at a moment that explains itself rather than at launch (OTA review, 3.11.2026).
 */
@Composable
private fun rememberNotificationRequest(): () -> Unit {
    val context = LocalContext.current
    var explain by remember { mutableStateOf(false) }
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { }
    if (explain) {
        ru.e1173.nookast.designsystem.ScaledAlertDialog(
            onDismissRequest = { explain = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            text = { Text(stringResource(R.string.update_notifications_rationale)) },
            confirmButton = {
                TextButton(onClick = {
                    explain = false
                    launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }) { Text(stringResource(R.string.update_notifications_allow)) }
            },
            dismissButton = {
                TextButton(onClick = { explain = false }) { Text(stringResource(R.string.update_not_now)) }
            },
        )
    }
    return {
        if (android.os.Build.VERSION.SDK_INT >= 33 && !notificationsAsked &&
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationsAsked = true
            explain = true
        }
    }
}

@Volatile private var notificationsAsked = false

/**
 * The one update offer (user, 5.11.2026): «Version X is available · 4.4 MB», what's new, a note on
 * mobile data and on playback, and «Download and install» / «Later». It shows after «Check now»,
 * when the app opens and nothing plays for an update that waits for the user (Android would confirm
 * it, or the mode only tells), and from its notification. After consent the same dialog shows the
 * download and goes on to the install.
 */
@Composable
internal fun UpdatePrompt() {
    if (!Updater.enabled) return
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        Updater.loadAsync(context)
        Updater.resumeAfterPermission(context)
    }
    val state by Updater.state.collectAsStateWithLifecycle()
    val install = rememberInstallAction()
    val askNotifications = rememberNotificationRequest()
    val downloading = state.progress != null
    InstallerLauncher(state.launch, state.saveAs)
    val waiting = state.available != null &&
        (state.needsConfirmation || state.mode == UpdateMode.NOTIFY || state.promptRequested)
    val shown = downloading || (waiting && !state.promptPostponed && (!PlaybackActivity.playing || state.promptRequested))
    if (!shown) return
    val offer = remember(state.available, state.availableSize, state.downloaded) {
        ru.e1173.nookast.update.UpdateFlow.offer(
            tag = state.available,
            sizeBytes = state.availableSize,
            downloaded = state.downloaded,
            metered = Updater.isMetered(context),
            playing = PlaybackActivity.playing,
        )
    } ?: return
    val notes = releaseNotes(state.availableBody)
    val later = {
        Updater.postponePrompt()
        askNotifications()
    }
    val size = offer.sizeBytes.takeIf { it > 0 }?.let { android.text.format.Formatter.formatShortFileSize(context, it) }
    ru.e1173.nookast.designsystem.ScaledAlertDialog(
        onDismissRequest = { if (!downloading) later() },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Text(
                if (size != null) {
                    stringResource(R.string.update_offer_title, version(state), size)
                } else {
                    stringResource(R.string.update_available, version(state))
                },
            )
        },
        text = {
            Column(
                Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState(), flingBehavior = deviceFling()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (downloading) UpdateDownloadProgress(state, cancel = false)
                if (offer.metered && !downloading) {
                    Text(stringResource(R.string.update_offer_metered), style = MaterialTheme.typography.bodyMedium)
                }
                if (offer.stopsPlayback) {
                    Text(stringResource(R.string.update_offer_stops_playback), style = MaterialTheme.typography.bodyMedium)
                }
                state.error?.let { error ->
                    Text(stringResource(error.message()), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
                if (state.permissionHelp) {
                    Text(stringResource(R.string.update_permission_manual), style = MaterialTheme.typography.bodyMedium)
                }
                if (ru.e1173.nookast.update.SavedUpdate.showInOffer(state.available, state.classicFailed, downloading)) {
                    SaveToDownloads(state)
                }
                if (notes.isNotBlank()) {
                    Text(notes, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            if (downloading) {
                TextButton(onClick = Updater::cancelDownload) { Text(stringResource(R.string.control_journal_cancel)) }
            } else {
                TextButton(onClick = install) {
                    Text(
                        stringResource(
                            when {
                                state.permissionHelp -> R.string.update_check_again
                                state.sessionFailed -> R.string.update_install_other_way
                                offer.action == ru.e1173.nookast.update.OfferAction.INSTALL -> R.string.update_install
                                else -> R.string.update_download_install
                            },
                        ),
                    )
                }
            }
        },
        dismissButton = if (downloading) null else {
            { TextButton(onClick = later) { Text(stringResource(R.string.update_later)) } }
        },
    )
}

private fun UpdateError.message(): Int = when (this) {
    UpdateError.NETWORK -> R.string.update_error_network
    UpdateError.NO_RELEASE -> R.string.update_error_no_release
    UpdateError.CHECKSUM -> R.string.update_error_checksum
    UpdateError.FOREIGN_SIGNATURE -> R.string.update_error_signature
    UpdateError.NOT_NEWER -> R.string.update_error_not_newer
    UpdateError.INSTALL -> R.string.update_error_install
    UpdateError.PERMISSION -> R.string.update_error_permission
}

/** «Updated to X» with what changed, once after the app replaced itself (user, 2.11.2026). */
@Composable
internal fun UpdatedNotice() {
    if (!Updater.enabled) return
    val context = LocalContext.current
    var updatedTo by remember { mutableStateOf<String?>(null) }
    var body by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            Updater.takeUpdatedTo(context)?.let { tag ->
                body = Updater.notesFor(context, tag)
                updatedTo = tag
            }
        }
    }
    val tag = updatedTo ?: return
    val notes = releaseNotes(body)
    ru.e1173.nookast.designsystem.ScaledAlertDialog(
        onDismissRequest = { updatedTo = null },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        title = { Text(stringResource(R.string.update_done_title, tag.removePrefix("v"))) },
        text = if (notes.isBlank()) {
            null
        } else {
            { Text(notes, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState(), flingBehavior = deviceFling())) }
        },
        confirmButton = { TextButton(onClick = { updatedTo = null }) { Text(stringResource(R.string.update_done_ok)) } },
    )
}

/**
 * «Save to Downloads», the last way (user, 6.11.2026): the checked update as a file to open in a
 * file manager or carry to another device; then where it is and the certificate to compare.
 */
@Composable
private fun SaveToDownloads(state: ru.e1173.nookast.update.UpdateState, touchTarget: Dp? = null) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val saved = state.savedName
        if (saved != null) {
            Text(stringResource(R.string.update_saved, saved), style = MaterialTheme.typography.bodyMedium)
            val fingerprint = remember { Updater.certificateFingerprint(context) }
            if (fingerprint.isNotEmpty()) {
                Text(
                    stringResource(R.string.update_saved_certificate, fingerprint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            if (state.saveFailed) {
                Text(stringResource(R.string.update_save_failed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
            NookButton(size = NookButtonSize.L, variant = NookButtonVariant.Outline,
                onClick = { Updater.saveToDownloads(context) },
                modifier = Modifier.fillMaxWidth().let { if (touchTarget != null) it.heightIn(min = touchTarget) else it },
            ) { Text(stringResource(R.string.update_save_to_downloads)) }
        }
    }
}
