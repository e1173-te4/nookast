package ru.e1173.nookast.ui

import ru.e1173.nookast.designsystem.NookButton
import ru.e1173.nookast.designsystem.NookButtonVariant
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import ru.e1173.nookast.R
import ru.e1173.nookast.core.sync.SyncRepository.PendingSummary

/*
 * Functional synchronization UI on the current components (EXECUTION_PLAN 3.5.9); styled in D5.
 */

@Composable
internal fun pendingSummaryText(summary: PendingSummary): String {
    val parts = buildList {
        if (summary.progress > 0) add(pluralStringResource(R.plurals.sync_pending_progress, summary.progress, summary.progress))
        if (summary.queue > 0) add(pluralStringResource(R.plurals.sync_pending_queue, summary.queue, summary.queue))
        if (summary.saved > 0) add(pluralStringResource(R.plurals.sync_pending_saved, summary.saved, summary.saved))
        if (summary.podcasts > 0) add(pluralStringResource(R.plurals.sync_pending_podcasts, summary.podcasts, summary.podcasts))
        if (summary.deadLetters > 0) add(pluralStringResource(R.plurals.sync_pending_dead, summary.deadLetters, summary.deadLetters))
    }
    return if (parts.isEmpty()) stringResource(R.string.sync_nothing_pending) else parts.joinToString(", ")
}

/** Sign-out barrier (SYNC.md §10.2): final delivery, then keep / discard / cancel. */
@Composable
internal fun SignOutDialog(
    step: SignOutStep,
    onSkipWait: () -> Unit,
    onKeep: () -> Unit,
    onDiscard: () -> Unit,
    onCancel: () -> Unit,
) {
    when (step) {
        SignOutStep.Flushing -> ru.e1173.nookast.designsystem.ScaledAlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnClickOutside = false, usePlatformDefaultWidth = false),
            title = { Text(stringResource(R.string.sign_out_flushing_title)) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.sign_out_flushing_body))
                }
            },
            confirmButton = { TextButton(onClick = onSkipWait) { Text(stringResource(R.string.sign_out_dont_wait)) } },
        )
        is SignOutStep.Pending -> ru.e1173.nookast.designsystem.ScaledAlertDialog(
            onDismissRequest = onCancel,
            properties = DialogProperties(usePlatformDefaultWidth = false),
            title = { Text(stringResource(R.string.sign_out_pending_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.sign_out_pending_body, pendingSummaryText(step.summary)))
                    Text(
                        stringResource(R.string.sign_out_keep_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    Button(onClick = onKeep) { Text(stringResource(R.string.sign_out_keep)) }
                    TextButton(onClick = onDiscard) {
                        Text(stringResource(R.string.sign_out_discard), color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.sync_cancel)) }
                }
            },
        )
        SignOutStep.Idle, SignOutStep.Clean -> Unit
    }
}

/** Another account's data (SYNC.md §10.3). The engine is stopped while this is open. */
@Composable
internal fun AccountQuarantineDialog(
    quarantine: QuarantineUi,
    onSameServer: () -> Unit,
    onBackToPrevious: () -> Unit,
    onDeletePrevious: () -> Unit,
) {
    ru.e1173.nookast.designsystem.ScaledAlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false),
        title = { Text(stringResource(R.string.quarantine_title)) },
        text = {
            Text(
                stringResource(
                    R.string.quarantine_body,
                    quarantine.previousOrigin,
                    quarantine.previousUserId,
                    quarantine.currentOrigin,
                    quarantine.currentUserId,
                    pendingSummaryText(quarantine.pending),
                ),
            )
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                if (quarantine.sameUser) {
                    Button(onClick = onSameServer) { Text(stringResource(R.string.quarantine_same_server)) }
                }
                TextButton(onClick = onBackToPrevious) { Text(stringResource(R.string.quarantine_back)) }
                TextButton(onClick = onDeletePrevious) {
                    Text(stringResource(R.string.quarantine_delete), color = MaterialTheme.colorScheme.error)
                }
            }
        },
    )
}

/** "Needs attention" in Settings: sign in again, and intents that could not be sent (§8.1). */
@Composable
internal fun SyncAttentionContent(
    needsSignIn: Boolean,
    deadLetters: List<DeadLetterUi>,
    onSignInAgain: () -> Unit,
    onRetry: (Long) -> Unit,
    onDiscard: (Long) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (needsSignIn) {
            Text(stringResource(R.string.sync_needs_sign_in), style = MaterialTheme.typography.bodyMedium)
            NookButton(onClick = onSignInAgain) { Text(stringResource(R.string.sync_sign_in_again)) }
        }
        if (deadLetters.isNotEmpty()) {
            Text(
                stringResource(R.string.sync_dead_letters_title),
                style = ru.e1173.nookast.designsystem.NookType.rowTitle,
            )
            deadLetters.forEach { letter ->
                Column(Modifier.fillMaxWidth()) {
                    val title = letter.title ?: stringResource(R.string.sync_dead_letter_unknown_episode)
                    Text(
                        when (letter.aggregate) {
                            "progress" -> stringResource(R.string.sync_dead_letter_progress, title)
                            "queue" -> stringResource(R.string.sync_dead_letter_queue)
                            "saved" -> stringResource(R.string.sync_dead_letter_saved, title)
                            else -> stringResource(R.string.sync_dead_letter_podcast)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        stringResource(R.string.sync_dead_letter_reason, letter.reason),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                        if (letter.retryable) {
                            NookButton(variant = NookButtonVariant.Outline, onClick = { onRetry(letter.id) }) { Text(stringResource(R.string.sync_retry)) }
                        }
                        NookButton(variant = NookButtonVariant.Text, onClick = { onDiscard(letter.id) }) { Text(stringResource(R.string.sync_discard)) }
                    }
                }
            }
        }
    }
}
