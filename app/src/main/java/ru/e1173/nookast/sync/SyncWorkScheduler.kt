package ru.e1173.nookast.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import ru.e1173.nookast.app.appGraph
import ru.e1173.nookast.core.sync.SyncFailure

/**
 * WorkManager is only the insurance of synchronization (docs/SYNC.md §9): the in-process
 * [ru.e1173.nookast.core.sync.SyncScheduler] does the work while the UI is visible or audio plays.
 */
object SyncWorkScheduler {
    fun ensurePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<OutboxSyncWorker>(
            PERIODIC_INTERVAL_MINUTES,
            TimeUnit.MINUTES,
        )
            .setConstraints(networkConstraint)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, MIN_BACKOFF_SECONDS, TimeUnit.SECONDS)
            .addTag(WORK_TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /** After an intent: wake the in-process scheduler (one conflated trigger, no WorkManager). */
    fun enqueueImmediate(context: Context) {
        context.appGraph.syncScheduler.requestSync()
    }

    /**
     * Arms the insurance when the process stops being active with work pending. REPLACE resets a
     * long backoff of an older insurance run (review F10); delivery is idempotent, so replacing a
     * running attempt is safe.
     */
    fun armInsurance(context: Context) {
        val request = OneTimeWorkRequestBuilder<OutboxSyncWorker>()
            .setConstraints(networkConstraint)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, MIN_BACKOFF_SECONDS, TimeUnit.SECONDS)
            .addTag(WORK_TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    /** Sign-out: no background work may refill the database afterwards (S39). */
    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG)
    }

    private val networkConstraint = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    private const val WORK_TAG = "nookast-outbox-sync"
    private const val IMMEDIATE_WORK_NAME = "nookast-outbox-immediate"
    private const val PERIODIC_WORK_NAME = "nookast-outbox-periodic"
    private const val PERIODIC_INTERVAL_MINUTES = 15L
    private const val MIN_BACKOFF_SECONDS = 10L
}

/** Keeps its class name: WorkManager stores it for scheduled work on users' devices. */
class OutboxSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val graph = applicationContext.appGraph
        graph.session.load() ?: return Result.success()
        val repository = graph.syncRepository
        val pending = repository.dirtyProgress(1).isNotEmpty() || repository.queueIntents().isNotEmpty() ||
            repository.pendingSaved(1).isNotEmpty() || repository.pendingPodcastRemovals(1).isNotEmpty()
        if (!pending) return Result.success() // nothing to deliver: no network work (I13)
        return try {
            val report = graph.syncEngine.syncNow()
            val stillPending = repository.dirtyProgress(1).isNotEmpty() || repository.queueIntents().isNotEmpty()
            when (syncWorkerDecision(report.stoppedBy, stillPending, runAttemptCount)) {
                SyncWorkerDecision.SUCCESS -> Result.success()
                SyncWorkerDecision.RETRY -> Result.retry()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
        }
    }
}

internal enum class SyncWorkerDecision {
    SUCCESS,
    RETRY,
}

internal fun syncWorkerDecision(
    stoppedBy: SyncFailure?,
    pending: Boolean,
    runAttemptCount: Int,
): SyncWorkerDecision = when {
    stoppedBy == SyncFailure.AUTH -> SyncWorkerDecision.SUCCESS
    !pending -> SyncWorkerDecision.SUCCESS
    runAttemptCount < MAX_ATTEMPTS -> SyncWorkerDecision.RETRY
    else -> SyncWorkerDecision.SUCCESS
}

private const val MAX_ATTEMPTS = 8
