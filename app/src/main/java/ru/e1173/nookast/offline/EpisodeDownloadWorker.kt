package ru.e1173.nookast.offline

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import ru.e1173.nookast.app.appGraph
import ru.e1173.nookast.core.network.HttpClients
import ru.e1173.nookast.data.remote.MediaSourceResolver

/**
 * Downloads from the PinePods server run inside the app (finding X-01, 27.10.2026): the system
 * DownloadManager kept the `Api-Key` header in its own database and the stored copy's stream needs
 * the key in the query. Here the key is added at request time on the shared client, whose network
 * interceptor strips it from any redirect to another host, and is never written anywhere.
 *
 * Since APK-3.11 every episode download runs here, RSS included (plan step 4.4): one progress, no
 * system notification, the key only on PinePods requests.
 *
 * The file is written to `<destination>.part` and renamed only when complete. An RSS host resumes
 * a stopped transfer with `Range`; PinePods answers the whole file without it (N-13), so there a
 * stopped transfer restarts from zero. An RSS host that cannot be reached at all switches to the
 * server at once instead of retrying for minutes.
 */
class EpisodeDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val episodeId = inputData.getInt(KEY_EPISODE_ID, 0)
        val url = inputData.getString(KEY_URL)
        val destination = inputData.getString(KEY_DESTINATION)?.let(::File)
        if (episodeId == 0 || url == null || destination == null) return@withContext Result.failure()
        val manager = EpisodeDownloadManager(applicationContext)
        val part = File(destination.path + PART_SUFFIX)
        try {
            val expectedBytes = transfer(episodeId, url, destination)
            manager.completeInApp(episodeId, destination, expectedBytes)
            Result.success()
        } catch (error: IOException) {
            val unreachableOrigin = error is java.net.UnknownHostException ||
                error is java.net.ConnectException ||
                error is javax.net.ssl.SSLException
            if (runAttemptCount < MAX_ATTEMPTS && error !is PermanentDownloadException && !unreachableOrigin) {
                // The partial file stays for an RSS resume; PinePods restarts it anyway.
                Result.retry()
            } else {
                part.delete()
                manager.failInApp(episodeId)
                Result.failure()
            }
        } finally {
            progress.remove(episodeId)
        }
    }

    private suspend fun transfer(episodeId: Int, url: String, destination: File): Long? {
        val session = applicationContext.appGraph.session.load()
        val requestUrl = url.toHttpUrlOrNull() ?: throw PermanentDownloadException("bad url")
        val pinePods = session != null && MediaSourceResolver.isPinePodsMediaRequest(session.serverUrl, url)
        val withKey = if (session != null && MediaSourceResolver.isPinePodsStreamRequest(session.serverUrl, url)) {
            val streamKey = applicationContext.appGraph.serverDownloads.streamKey()
                ?: throw IOException("no stream key")
            requestUrl.newBuilder().addQueryParameter("api_key", streamKey).build()
        } else {
            requestUrl
        }
        val part = File(destination.path + PART_SUFFIX)
        destination.parentFile?.mkdirs()
        val resumeFrom = if (!pinePods && part.isFile) part.length() else 0L
        if (pinePods || resumeFrom == 0L) part.delete()
        val request = Request.Builder()
            .url(withKey)
            .apply {
                if (pinePods && session != null) header(HttpClients.API_KEY_HEADER, session.apiKey)
                if (resumeFrom > 0) header("Range", "bytes=$resumeFrom-")
            }
            .build()
        val client = if (pinePods) HttpClients.mediaSlow else HttpClients.media
        client.newCall(request).execute().use { response ->
            if (response.code == 401 || response.code == 403 || response.code == 404 || response.code == 410) {
                throw PermanentDownloadException("HTTP ${response.code}")
            }
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val append = response.code == 206 && resumeFrom > 0
            val body = response.body
            val total = body.contentLength().takeIf { it > 0 }?.let { if (append) it + resumeFrom else it }
            var written = if (append) resumeFrom else 0L
            var lastReported = -1
            progress[episodeId] = if (total != null) written.toFloat() / total else 0f
            body.byteStream().use { input ->
                java.io.FileOutputStream(part, append).use { output ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        written += read
                        if (total != null) {
                            val percent = (written * 100 / total).toInt()
                            if (percent != lastReported) {
                                lastReported = percent
                                progress[episodeId] = percent / 100f
                            }
                        }
                    }
                }
            }
            if (written <= 0 || (total != null && written < total)) throw IOException("short body")
            if (destination.exists() && !destination.delete()) throw IOException("cannot replace")
            if (!part.renameTo(destination)) throw IOException("cannot rename")
            return total ?: written
        }
    }

    private class PermanentDownloadException(message: String) : IOException(message)

    companion object {
        private const val KEY_EPISODE_ID = "episode_id"
        private const val KEY_URL = "url"
        private const val KEY_DESTINATION = "destination"
        private const val PART_SUFFIX = ".part"
        private const val BUFFER_BYTES = 64 * 1024
        private const val MAX_ATTEMPTS = 5
        const val TAG = "nookast-server-episode-download" // kept: jobs from APK-3.9/3.10

        /** Fraction per running episode; in memory only, the UI polls it with the download state. */
        internal val progress = ConcurrentHashMap<Int, Float>()

        private fun workName(episodeId: Int) = "$TAG-$episodeId"

        internal fun enqueue(
            context: Context,
            episodeId: Int,
            url: String,
            destination: File,
            allowMetered: Boolean = true,
        ) {
            val request = OneTimeWorkRequestBuilder<EpisodeDownloadWorker>()
                .setInputData(
                    workDataOf(
                        KEY_EPISODE_ID to episodeId,
                        KEY_URL to url,
                        KEY_DESTINATION to destination.absolutePath,
                    ),
                )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(if (allowMetered) NetworkType.CONNECTED else NetworkType.UNMETERED)
                        .build(),
                )
                .addTag(TAG)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(workName(episodeId), ExistingWorkPolicy.REPLACE, request)
        }

        internal fun cancel(context: Context, episodeId: Int) {
            WorkManager.getInstance(context).cancelUniqueWork(workName(episodeId))
            progress.remove(episodeId)
        }

        internal fun cancelAll(context: Context) {
            WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
            progress.clear()
        }
    }
}
