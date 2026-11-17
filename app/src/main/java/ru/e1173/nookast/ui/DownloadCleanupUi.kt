package ru.e1173.nookast.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import ru.e1173.nookast.R
import ru.e1173.nookast.designsystem.LocalNookastColors
import ru.e1173.nookast.designsystem.NookButton
import ru.e1173.nookast.designsystem.NookButtonVariant
import ru.e1173.nookast.designsystem.NookMenu
import ru.e1173.nookast.designsystem.NookMenuItem
import ru.e1173.nookast.designsystem.NookRowDivider
import ru.e1173.nookast.designsystem.NookSettingRow
import ru.e1173.nookast.designsystem.NookSpacing
import ru.e1173.nookast.designsystem.NookSwitch
import ru.e1173.nookast.designsystem.NookType
import ru.e1173.nookast.offline.DELETE_AFTER_DAY_CHOICES

/** «Never» or «N days». */
@Composable
private fun daysLabel(days: Int): String =
    if (days <= 0) stringResource(R.string.device_cleanup_never) else pluralStringResource(R.plurals.device_cleanup_days, days, days)

/** The defaults in Settings (DEC-064): only files on this phone. */
@Composable
internal fun DeviceDownloadCleanupSettings(
    deletePlayed: Boolean,
    deleteAfterDays: Int,
    onDeletePlayedChanged: (Boolean) -> Unit,
    onDeleteAfterDaysChanged: (Int) -> Unit,
) {
    NookSettingRow(
        title = stringResource(R.string.device_cleanup_delete_played),
        description = stringResource(R.string.device_cleanup_delete_played_help),
    ) {
        NookSwitch(checked = deletePlayed, onCheckedChange = onDeletePlayedChanged)
    }
    NookRowDivider()
    CleanupChoice(
        title = stringResource(R.string.device_cleanup_delete_after),
        description = stringResource(R.string.device_cleanup_delete_after_help),
        value = daysLabel(deleteAfterDays),
        options = DELETE_AFTER_DAY_CHOICES.map { days -> days to daysLabel(days) },
        onSelect = { days -> onDeleteAfterDaysChanged(days ?: 0) },
    )
}

/** One podcast's own choices; null follows Settings. Opened from the podcast's gear. */
@Composable
internal fun PodcastDownloadCleanupDialog(
    defaultDeletePlayed: Boolean,
    defaultDeleteAfterDays: Int,
    deletePlayed: Boolean?,
    deleteAfterDays: Int?,
    onChange: (deletePlayed: Boolean?, deleteAfterDays: Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    var played by remember { mutableStateOf(deletePlayed) }
    var days by remember { mutableStateOf(deleteAfterDays) }
    val onLabel = stringResource(R.string.device_cleanup_on)
    val offLabel = stringResource(R.string.device_cleanup_off)
    ru.e1173.nookast.designsystem.ScaledAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.device_cleanup_podcast)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(NookSpacing.s)) {
                Text(stringResource(R.string.device_cleanup_podcast_help), style = MaterialTheme.typography.bodyMedium)
                val playedDefault = stringResource(R.string.device_cleanup_default_value, if (defaultDeletePlayed) onLabel else offLabel)
                CleanupChoice(
                    title = stringResource(R.string.device_cleanup_delete_played),
                    description = null,
                    value = when (played) {
                        null -> playedDefault
                        true -> onLabel
                        false -> offLabel
                    },
                    options = listOf(null to playedDefault, 1 to onLabel, 0 to offLabel),
                    onSelect = { choice ->
                        played = choice?.let { it == 1 }
                        onChange(played, days)
                    },
                )
                val daysDefault = stringResource(R.string.device_cleanup_default_value, daysLabel(defaultDeleteAfterDays))
                CleanupChoice(
                    title = stringResource(R.string.device_cleanup_delete_after),
                    description = null,
                    value = days?.let { daysLabel(it) } ?: daysDefault,
                    options = listOf<Pair<Int?, String>>(null to daysDefault) + DELETE_AFTER_DAY_CHOICES.map { it to daysLabel(it) },
                    onSelect = { choice ->
                        days = choice
                        onChange(played, days)
                    },
                )
            }
        },
        confirmButton = {
            NookButton(text = stringResource(R.string.device_cleanup_done), onClick = onDismiss, variant = NookButtonVariant.Text)
        },
    )
}

/** A setting showing its current [value]; a tap opens the [options] menu (value to label). */
@Composable
private fun CleanupChoice(
    title: String,
    description: String?,
    value: String,
    options: List<Pair<Int?, String>>,
    onSelect: (Int?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    NookSettingRow(title = title, description = description, onClick = { expanded = true }) {
        Box {
            Text(
                text = value,
                style = NookType.barTitle,
                color = LocalNookastColors.current.accentText,
                maxLines = 2,
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )
            NookMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (option, label) ->
                    NookMenuItem(
                        text = label,
                        onClick = {
                            expanded = false
                            onSelect(option)
                        },
                    )
                }
            }
        }
    }
}
