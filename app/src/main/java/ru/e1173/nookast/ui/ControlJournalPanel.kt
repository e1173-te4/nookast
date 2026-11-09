package ru.e1173.nookast.ui

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.e1173.nookast.BuildConfig
import ru.e1173.nookast.R
import ru.e1173.nookast.app.appGraph
import ru.e1173.nookast.diagnostics.ControlJournal
import ru.e1173.nookast.diagnostics.ControlJournalSnapshot

import ru.e1173.nookast.diagnostics.DeviceDiagnostics

/**
 * Settings → Diagnostics (user, 2.11.2026: finish the section, drop the «temporary» note, make it
 * nicer and handier, keep the quick markers, add anonymous device data). The settings' own grouped
 * panels: the journal with its switches, markers (quick chips and one's own text), the anonymous
 * device data, the whole journal's actions and its latest records. The journal is [ControlJournal].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ControlJournalPanel(touchTarget: Dp) {
    val context = LocalContext.current
    val window = (androidx.compose.ui.platform.LocalView.current.context as? android.app.Activity)?.window
    val journal = remember(context) { context.appGraph.controlJournal }
    val recording by journal.recording.collectAsStateWithLifecycle()
    val broadcast = remember(context) { context.appGraph.legacyMetadataBroadcast }
    val broadcasting by broadcast.enabled.collectAsStateWithLifecycle()
    val revision by journal.revision.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var markText by rememberSaveable { mutableStateOf("") }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var deviceRevision by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    // In-app confirmation instead of a system toast, which ignores the car interface scale.
    var copiedMessage by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(copiedMessage) {
        if (copiedMessage != null) {
            kotlinx.coroutines.delay(4_000)
            copiedMessage = null
        }
    }

    val preview by produceState(JournalPreview.EMPTY, revision) {
        // Records can arrive in bursts (a held key repeats); re-read at most a few times a second.
        delay(PREVIEW_DEBOUNCE_MS)
        value = withContext(Dispatchers.IO) {
            JournalPreview(lines = journal.tail(PREVIEW_LINES), sizeBytes = journal.sizeBytes())
        }
    }
    val device by produceState(emptyList<DeviceDiagnostics.Section>(), deviceRevision) {
        value = withContext(Dispatchers.IO) { DeviceDiagnostics.collect(context, window) }
    }

    Text(
        text = stringResource(R.string.control_journal_description),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 12.dp),
    )

    SettingsGroupLabel(R.string.diagnostics_journal_group)
    SettingsPanel {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            JournalSwitchRow(
                title = stringResource(R.string.control_journal_record),
                subtitle = stringResource(R.string.control_journal_size, Formatter.formatShortFileSize(context, preview.sizeBytes)),
                checked = recording,
                touchTarget = touchTarget,
                onChange = { enabled ->
                    val appContext = context.applicationContext
                    journal.setRecording(enabled) { ControlJournalSnapshot.capture(appContext) }
                },
            )
            androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            JournalSwitchRow(
                title = stringResource(R.string.control_journal_broadcast),
                subtitle = stringResource(R.string.control_journal_broadcast_description),
                checked = broadcasting,
                touchTarget = touchTarget,
                onChange = broadcast::setEnabled,
            )
        }
    }

    // Steering-wheel and head-unit markers belong to the car only (user, 6.11.2026).
    val headUnit = remember(context) { ru.e1173.nookast.player.KnownHeadUnits.current(context) != null }
    SettingsGroupLabel(if (headUnit) R.string.control_journal_presets else R.string.control_journal_markers, Modifier.padding(top = 18.dp))
    SettingsPanel {
        Column(Modifier.padding(16.dp)) {
            // The quick markers the user liked, one tap each.
            if (headUnit) FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                PRESETS.forEach { preset ->
                    val label = stringResource(preset)
                    AssistChip(
                        onClick = { journal.mark(label) },
                        label = { Text(label) },
                        enabled = recording,
                    )
                }
            }
            // One's own marker: the field and its send button on one line.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(top = if (headUnit) 8.dp else 0.dp),
            ) {
                OutlinedTextField(
                    value = markText,
                    onValueChange = { markText = it.take(ControlJournal.MAX_MARK_LENGTH) },
                    label = { Text(stringResource(R.string.control_journal_mark_hint)) },
                    singleLine = true,
                    enabled = recording,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        journal.mark(markText)
                        markText = ""
                    },
                    enabled = recording && markText.isNotBlank(),
                    modifier = Modifier.heightIn(min = touchTarget.coerceAtMost(56.dp)),
                ) {
                    Text(stringResource(R.string.control_journal_mark))
                }
            }
            if (!recording) {
                Text(
                    text = stringResource(R.string.control_journal_record_first),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }

    SettingsGroupLabel(R.string.diagnostics_device_group, Modifier.padding(top = 18.dp))
    SettingsPanel {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.diagnostics_device_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { deviceRevision++ }) { Text(stringResource(R.string.diagnostics_device_refresh)) }
            }
            device.forEach { section ->
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                )
                section.lines.forEach { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }

    SettingsGroupLabel(R.string.diagnostics_actions_group, Modifier.padding(top = 18.dp))
    SettingsPanel {
        // Share on its own row, Copy and Clear under it (user, 5.11.2026: three in a row wrapped
        // their labels in Russian).
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { scope.launch { shareJournal(context, journal, window) } },
                modifier = Modifier.fillMaxWidth().heightIn(min = touchTarget),
            ) {
                Text(stringResource(R.string.control_journal_share), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedButton(
                    onClick = { scope.launch { copiedMessage = copyJournal(context, journal, window) } },
                    modifier = Modifier.weight(1f).heightIn(min = touchTarget),
                ) {
                    Text(stringResource(R.string.control_journal_copy), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(
                    onClick = { confirmClear = true },
                    modifier = Modifier.weight(1f).heightIn(min = touchTarget),
                ) {
                    Text(stringResource(R.string.control_journal_clear), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            copiedMessage?.let { message ->
                Text(
                    stringResource(message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }

    SettingsGroupLabel(R.string.control_journal_recent, Modifier.padding(top = 18.dp))
    SettingsPanel {
        Text(
            text = if (preview.lines.isEmpty()) {
                stringResource(R.string.control_journal_empty)
            } else {
                preview.lines.asReversed().joinToString("\n")
            },
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(16.dp),
        )
    }

    if (confirmClear) {
        ru.e1173.nookast.designsystem.ScaledAlertDialog(
            onDismissRequest = { confirmClear = false },
            // The platform width is in system dp: in the car it squeezed the scaled dialog.
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            text = { Text(stringResource(R.string.control_journal_clear_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    journal.clear()
                }) {
                    Text(stringResource(R.string.control_journal_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.control_journal_cancel))
                }
            },
        )
    }
}

/** A titled switch row of the journal panel, the whole row tappable. */
@Composable
private fun JournalSwitchRow(title: String, subtitle: String, checked: Boolean, touchTarget: Dp, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = touchTarget)
            .padding(vertical = 8.dp),
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private data class JournalPreview(val lines: List<String>, val sizeBytes: Long) {
    companion object {
        val EMPTY = JournalPreview(emptyList(), 0L)
    }
}

private suspend fun shareJournal(context: Context, journal: ControlJournal, window: android.view.Window?) {
    val file = withContext(Dispatchers.IO) {
        journal.exportTo(File(context.cacheDir, EXPORT_DIRECTORY)).also { exported ->
            // The anonymous device data goes on top, as it stands now.
            exported.writeText(DeviceDiagnostics.format(DeviceDiagnostics.collect(context, window)) + "\n" + exported.readText())
        }
    }
    val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.diagnostics", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.control_journal_share_subject))
        clipData = ClipData.newRawUri(file.name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        Intent.createChooser(send, context.getString(R.string.control_journal_share))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

private suspend fun copyJournal(context: Context, journal: ControlJournal, window: android.view.Window?): Int {
    val text = withContext(Dispatchers.IO) {
        DeviceDiagnostics.format(DeviceDiagnostics.collect(context, window)) + "\n" + journal.readAll()
    }
    // A clip travels through a binder transaction (about 1 MB); keep the newest records.
    val truncated = text.length > MAX_CLIP_CHARS
    val clipText = if (truncated) text.takeLast(MAX_CLIP_CHARS) else text
    val clip = ClipData.newPlainText(
        context.getString(R.string.control_journal_share_subject),
        clipText,
    )
    // Audio device names can include a name the user gave a Bluetooth device.
    if (Build.VERSION.SDK_INT >= 33) {
        clip.description.extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
    }
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
    return if (truncated) R.string.control_journal_copied_tail else R.string.control_journal_copied
}

private const val PREVIEW_LINES = 40
private const val PREVIEW_DEBOUNCE_MS = 300L
private const val MAX_CLIP_CHARS = 200_000
private const val EXPORT_DIRECTORY = "diagnostics-export"

private val PRESETS = listOf(
    R.string.control_journal_preset_wheel_next,
    R.string.control_journal_preset_wheel_previous,
    R.string.control_journal_preset_wheel_play_pause,
    R.string.control_journal_preset_screen_next,
    R.string.control_journal_preset_screen_previous,
    R.string.control_journal_preset_screen_play_pause,
    R.string.control_journal_preset_voice,
)
