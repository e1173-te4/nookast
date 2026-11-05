package ru.e1173.nookast.update

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import ru.e1173.nookast.MainActivity
import ru.e1173.nookast.R

/**
 * The daily check (user, 2.11.2026): once a day on any network, it asks for the latest release,
 * downloads a newer one on an unmetered network and, in the automatic mode, installs it when nothing
 * is in the way, or tries again in half an hour. An install Android would confirm waits for the user
 * in the app; the off mode asks nothing of the network.
 */
class UpdateWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        if (!Updater.enabled) return Result.success()
        val context = applicationContext
        Updater.load(context)
        if (Updater.state.value.mode == UpdateMode.OFF) return Result.success()
        // The start of the app asks only when the daily check has not run for a day.
        if (inputData.getBoolean(KEY_STALE_ONLY, false) &&
            System.currentTimeMillis() - Updater.state.value.lastCheckMs < TimeUnit.DAYS.toMillis(1)
        ) {
            return Result.success()
        }
        val state = Updater.check(context, manual = false)
        if (!state.downloaded) return Result.success()
        when (InstallPolicy.decide(state.mode, Updater.conditions(context))) {
            InstallDecision.INSTALL_NOW -> if (!Updater.install(context)) {
                Updater.markNeedsConfirmation(context)
                UpdateNotifications.ready(context, state.available)
            }
            InstallDecision.LATER -> UpdateScheduler.retryLater(context)
            InstallDecision.NEEDS_CONFIRMATION -> {
                Updater.markNeedsConfirmation(context)
                UpdateNotifications.ready(context, state.available)
            }
            InstallDecision.NOTIFY -> UpdateNotifications.ready(context, state.available)
            InstallDecision.NOTHING -> Unit
        }
        return Result.success()
    }

    companion object {
        const val KEY_STALE_ONLY = "stale_only"
    }
}

object UpdateScheduler {
    private const val DAILY = "nookast-update-daily"
    private const val RETRY = "nookast-update-retry"
    private const val SOON = "nookast-update-soon"

    private val connected get() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun ensure(context: Context) {
        if (!Updater.enabled) return
        val request = PeriodicWorkRequestBuilder<UpdateWorker>(24, TimeUnit.HOURS)
            .setConstraints(connected)
            .setInitialDelay(1, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(DAILY, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** The off mode: no daily check and no pending retry. */
    fun cancel(context: Context) {
        WorkManager.getInstance(context).apply {
            cancelUniqueWork(DAILY)
            cancelUniqueWork(RETRY)
            cancelUniqueWork(SOON)
        }
    }

    /** At the start of the app: a check in the background when the last one is more than a day old. */
    fun checkIfStale(context: Context) {
        if (!Updater.enabled) return
        enqueueSoon(context, staleOnly = true)
    }

    /** A check as soon as there is a network, for a downloaded file that went missing. */
    fun checkSoon(context: Context) {
        if (!Updater.enabled) return
        enqueueSoon(context, staleOnly = false)
    }

    private fun enqueueSoon(context: Context, staleOnly: Boolean) {
        val request = OneTimeWorkRequestBuilder<UpdateWorker>()
            .setConstraints(connected)
            .setInputData(Data.Builder().putBoolean(UpdateWorker.KEY_STALE_ONLY, staleOnly).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(SOON, ExistingWorkPolicy.KEEP, request)
    }

    fun retryLater(context: Context) {
        val request = OneTimeWorkRequestBuilder<UpdateWorker>()
            .setInitialDelay(30, TimeUnit.MINUTES)
            .setConstraints(connected)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(RETRY, ExistingWorkPolicy.REPLACE, request)
    }
}

/** The updates channel: «ready to install» and «confirm the install». */
object UpdateNotifications {
    private const val CHANNEL = "updates"
    private const val ID = 7_301

    private fun channel(context: Context) {
        NotificationManagerCompat.from(context).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.update_channel))
                .build(),
        )
    }

    private fun allowed(context: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /**
     * An update is downloaded: a tap opens the app with its «Install / Later» prompt, which asks for
     * the install permission when Android needs it and goes on once the user is back (OTA review).
     */
    fun ready(context: Context, tag: String?) {
        if (!allowed(context)) return
        channel(context)
        val open = PendingIntent.getActivity(
            context,
            1,
            openPrompt(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        post(context, context.getString(R.string.update_ready_title, tag?.removePrefix("v").orEmpty()), context.getString(R.string.update_ready_text), open)
    }

    fun openPrompt(context: Context): Intent = Intent(context, MainActivity::class.java)
        .setAction(MainActivity.ACTION_SHOW_UPDATE)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    /** Android asks the user to confirm: the confirmation opens from the notification, not from the background. */
    fun confirm(context: Context, confirmation: Intent) {
        if (!allowed(context)) return
        channel(context)
        val open = PendingIntent.getActivity(
            context,
            2,
            confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        post(context, context.getString(R.string.update_confirm_title), context.getString(R.string.update_ready_text), open)
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun post(context: Context, title: String, text: String, tap: PendingIntent) {
        NotificationManagerCompat.from(context).notify(
            ID,
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification_nookast)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(tap)
                .setAutoCancel(true)
                .build(),
        )
    }

    fun cancel(context: Context) = NotificationManagerCompat.from(context).cancel(ID)
}

/** The install session's result, and the «install» tap of the ready notification. */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_INSTALL -> {
                // A notification of an earlier version: the app's prompt takes it from here.
                UpdateNotifications.cancel(context)
                context.startActivity(UpdateNotifications.openPrompt(context))
            }
            ACTION_RESULT -> when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    @Suppress("DEPRECATION")
                    val confirmation = if (Build.VERSION.SDK_INT >= 33) {
                        intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                    } else {
                        intent.getParcelableExtra(Intent.EXTRA_INTENT)
                    }
                    // On screen the confirmation opens at once; from the background only by notification.
                    val onScreen = android.app.ActivityManager.RunningAppProcessInfo()
                        .also(android.app.ActivityManager::getMyMemoryState)
                        .importance <= android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                    confirmation?.let {
                        if (onScreen) {
                            runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                                .onFailure { _ -> UpdateNotifications.confirm(context, it) }
                        } else {
                            // Never a confirmation out of the background: it waits for the user.
                            Updater.markNeedsConfirmation(context)
                            UpdateNotifications.confirm(context, it)
                        }
                    }
                }
                PackageInstaller.STATUS_SUCCESS -> UpdateNotifications.cancel(context)
                else -> Updater.reportInstallFailure()
            }
        }
    }

    companion object {
        const val ACTION_RESULT = "ru.e1173.nookast.update.RESULT"
        const val ACTION_INSTALL = "ru.e1173.nookast.update.INSTALL"
    }
}

/** After the app replaced itself the old notification goes; «Updated to X» shows on the next start. */
class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) UpdateNotifications.cancel(context)
    }
}
