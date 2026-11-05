package ru.e1173.nookast.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import ru.e1173.nookast.app.appGraph
import ru.e1173.nookast.core.session.SessionRepository
import ru.e1173.nookast.data.local.SyncDatabase
import ru.e1173.nookast.data.remote.QueueLoadError
import ru.e1173.nookast.data.remote.QueueLoadException
import ru.e1173.nookast.data.remote.SyncErrorCategory

object ReadCacheWorkScheduler {
    fun ensurePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<ReadCacheRefreshWorker>(
            READ_CACHE_INTERVAL_HOURS,
            TimeUnit.HOURS,
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, MIN_BACKOFF_SECONDS, TimeUnit.SECONDS)
            .addTag(WORK_TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    internal const val READ_CACHE_INTERVAL_HOURS = 12L
    private const val MIN_BACKOFF_SECONDS = 30L
    private const val WORK_TAG = "nookast-read-cache"
    private const val PERIODIC_WORK_NAME = "nookast-read-cache-periodic"
}

class ReadCacheRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val session = applicationContext.appGraph.session.load() ?: return Result.success()
        val database = SyncDatabase.open(applicationContext)
        return try {
            val refresher = applicationContext.appGraph.libraryRefresher
            val outbox = drainOutboxBeforeRead(database, session)
            when (readCacheWorkerDecision(outbox.stoppedBy, outbox.pendingCount, null)) {
                ReadCacheWorkerDecision.SUCCESS -> {
                    refresher.refresh(session, forceRss = false)
                    // A read refresh can resolve RSS-only identities and promote durable deferred
                    // intents into the ordinary outbox. Drain those new records in the same run;
                    // otherwise a background-only client could wait until the next 12-hour pass.
                    val promotedOutbox = drainOutboxBeforeRead(database, session)
                    when (readCacheWorkerDecision(
                        promotedOutbox.stoppedBy,
                        promotedOutbox.pendingCount,
                        null,
                    )) {
                        ReadCacheWorkerDecision.SUCCESS -> Result.success()
                        ReadCacheWorkerDecision.RETRY -> Result.retry()
                        ReadCacheWorkerDecision.STOP_FOR_AUTH -> Result.success()
                    }
                }
                ReadCacheWorkerDecision.RETRY -> {
                    // Public feeds are independent of PinePods availability and never confirm or
                    // overwrite user outbox state.
                    refresher.refreshPublicRssOnly(force = false)
                    Result.retry()
                }
                ReadCacheWorkerDecision.STOP_FOR_AUTH -> {
                    refresher.refreshPublicRssOnly(force = false)
                    Result.success()
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: QueueLoadException) {
            when (readCacheWorkerDecision(null, 0, error.reason)) {
                ReadCacheWorkerDecision.SUCCESS -> Result.success()
                ReadCacheWorkerDecision.RETRY -> Result.retry()
                ReadCacheWorkerDecision.STOP_FOR_AUTH -> Result.success()
            }
        } catch (_: Exception) {
            Result.retry()
        }
    }

    /** Delivers pending intents with the sync engine before reading (docs/SYNC.md §9). */
    @Suppress("UNUSED_PARAMETER")
    private suspend fun drainOutboxBeforeRead(
        database: SyncDatabase,
        session: ru.e1173.nookast.auth.AuthSession,
    ): OutboxDrainSnapshot {
        val graph = applicationContext.appGraph
        val report = graph.syncEngine.syncNow()
        val repository = graph.syncRepository
        val pending = repository.dirtyProgress(Int.MAX_VALUE).size + repository.queueIntents().size +
            repository.pendingSaved(Int.MAX_VALUE).size + repository.pendingPodcastRemovals(Int.MAX_VALUE).size
        val stoppedBy = when {
            report.stoppedBy == ru.e1173.nookast.core.sync.SyncFailure.AUTH -> SyncErrorCategory.AUTHENTICATION
            report.failures.isNotEmpty() -> SyncErrorCategory.SERVER
            else -> null
        }
        return OutboxDrainSnapshot(stoppedBy, pending)
    }
}

internal data class OutboxDrainSnapshot(
    val stoppedBy: SyncErrorCategory?,
    val pendingCount: Int,
)

internal enum class ReadCacheWorkerDecision {
    SUCCESS,
    RETRY,
    STOP_FOR_AUTH,
}

internal fun readCacheWorkerDecision(
    stoppedBy: SyncErrorCategory?,
    pendingCount: Int,
    readError: QueueLoadError?,
): ReadCacheWorkerDecision = when {
    stoppedBy == SyncErrorCategory.AUTHENTICATION || readError == QueueLoadError.AUTHENTICATION ->
        ReadCacheWorkerDecision.STOP_FOR_AUTH
    stoppedBy != null || pendingCount > 0 || readError != null -> ReadCacheWorkerDecision.RETRY
    else -> ReadCacheWorkerDecision.SUCCESS
}
