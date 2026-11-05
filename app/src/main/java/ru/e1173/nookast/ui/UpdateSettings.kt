package ru.e1173.nookast.ui

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
 * what the newer version brings. Release builds only.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun UpdateSettingsPanel(touchTarget: Dp) {
    if (!Updater.enabled) return
    val context = LocalContext.current
    LaunchedEffect(Unit) { Updater.loadAsync(context) }
    val state by Updater.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var confirmStop by remember { mutableStateOf(false) }
    var showNotes by remember { mutableStateOf(false) }
    val install = rememberInstallAction()
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
            val version = state.available?.removePrefix("v").orEmpty()
            val status = when {
                state.progress != null -> stringResource(R.string.update_downloading, version, state.progress!!)
                state.checking -> stringResource(R.string.update_checking)
                state.error != null -> stringResource(state.error!!.message())
                state.downloaded && state.available != null -> stringResource(R.string.update_downloaded, version)
                state.available != null -> stringResource(R.string.update_available, version)
                state.lastCheckMs > 0 -> stringResource(
                    R.string.update_latest,
                    java.time.Instant.ofEpochMilli(state.lastCheckMs).atZone(java.time.ZoneId.systemDefault())
                        .format(java.time.format.DateTimeFormatter.ofPattern("d MMMM, HH:mm", locale)),
                )
                else -> stringResource(R.string.update_never_checked)
            }
            Text(
                status,
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 10.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = { scope.launch { Updater.check(context, manual = true) } },
                    enabled = !state.checking,
                    modifier = Modifier.weight(1f).heightIn(min = touchTarget),
                ) { Text(stringResource(R.string.update_check_now)) }
                if (state.downloaded) {
                    Button(
                        onClick = { if (PlaybackActivity.playing) confirmStop = true else install() },
                        modifier = Modifier.weight(1f).heightIn(min = touchTarget),
                    ) { Text(stringResource(R.string.update_install)) }
                }
            }
            val notes = releaseNotes(state.availableBody)
            if (state.available != null && notes.isNotBlank()) {
                TextButton(onClick = { showNotes = !showNotes }) { Text(stringResource(R.string.update_whats_new)) }
                if (showNotes) {
                    Text(notes, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    state.meteredSize?.let { size ->
        ru.e1173.nookast.designsystem.ScaledAlertDialog(
            onDismissRequest = Updater::dismissMetered,
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            text = {
                Text(
                    if (size > 0) {
                        stringResource(R.string.update_metered_text, version(state), android.text.format.Formatter.formatShortFileSize(context, size))
                    } else {
                        stringResource(R.string.update_metered_text_unknown, version(state))
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    Updater.dismissMetered()
                    scope.launch { Updater.check(context, manual = true, allowMetered = true) }
                }) { Text(stringResource(R.string.update_download)) }
            },
            dismissButton = {
                TextButton(onClick = Updater::dismissMetered) { Text(stringResource(R.string.control_journal_cancel)) }
            },
        )
    }

    if (confirmStop) {
        ru.e1173.nookast.designsystem.ScaledAlertDialog(
            onDismissRequest = { confirmStop = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            text = { Text(stringResource(R.string.update_stops_playback)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmStop = false
                    install()
                }) { Text(stringResource(R.string.update_install)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmStop = false }) { Text(stringResource(R.string.control_journal_cancel)) }
            },
        )
    }
}

private fun version(state: ru.e1173.nookast.update.UpdateState) = state.available?.removePrefix("v").orEmpty()

/** The release text in the reader's language. */
@Composable
private fun releaseNotes(body: String): String {
    val language = LocalConfiguration.current.locales[0].language
    return remember(body, language) { ReleaseRules.notesOf(body, language) }
}

/**
 * Installs the downloaded update. Without the install permission it opens Android's setting for it
 * and goes on when the user comes back having allowed it (OTA review, 3.11.2026).
 */
@Composable
private fun rememberInstallAction(): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var awaitingPermission by remember { mutableStateOf(false) }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        if (awaitingPermission && Updater.canInstall(context)) {
            awaitingPermission = false
            scope.launch { Updater.install(context) }
        }
    }
    return {
        if (!Updater.canInstall(context)) {
            awaitingPermission = true
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri()),
            )
        } else {
            scope.launch { Updater.install(context) }
        }
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
 * «Update X is ready — Install / Later» when the app opens and nothing plays, for an update that
 * waits for the user: Android would confirm it, the mode only tells, or its notification was tapped
 * (OTA review, 3.11.2026).
 */
@Composable
internal fun UpdatePrompt() {
    if (!Updater.enabled) return
    val context = LocalContext.current
    LaunchedEffect(Unit) { Updater.loadAsync(context) }
    val state by Updater.state.collectAsStateWithLifecycle()
    val install = rememberInstallAction()
    val askNotifications = rememberNotificationRequest()
    val waiting = state.downloaded && state.available != null &&
        (state.needsConfirmation || state.mode == UpdateMode.NOTIFY || state.promptRequested)
    if (!waiting || state.promptPostponed || (PlaybackActivity.playing && !state.promptRequested)) return
    val notes = releaseNotes(state.availableBody)
    val later = {
        Updater.postponePrompt()
        askNotifications()
    }
    ru.e1173.nookast.designsystem.ScaledAlertDialog(
        onDismissRequest = later,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        title = { Text(stringResource(R.string.update_prompt_title, version(state))) },
        text = {
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState(), flingBehavior = deviceFling())) {
                if (PlaybackActivity.playing) {
                    Text(stringResource(R.string.update_stops_playback), style = MaterialTheme.typography.bodyMedium)
                }
                if (notes.isNotBlank()) {
                    Text(notes, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                Updater.postponePrompt()
                install()
            }) { Text(stringResource(R.string.update_install)) }
        },
        dismissButton = { TextButton(onClick = later) { Text(stringResource(R.string.update_later)) } },
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
        text = {
            if (notes.isNotBlank()) {
                Text(notes, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState(), flingBehavior = deviceFling()))
            }
        },
        confirmButton = { TextButton(onClick = { updatedTo = null }) { Text(stringResource(R.string.update_done_ok)) } },
    )
}
