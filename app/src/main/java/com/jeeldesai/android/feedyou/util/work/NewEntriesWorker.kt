package com.jeeldesai.android.feedyou.util.work

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.text.HtmlCompat
import androidx.work.*
import com.jeeldesai.android.feedyou.FeedYouApplication.Companion.NOTIFICATION_CHANNEL_ID
import com.jeeldesai.android.feedyou.data.local.FeedPreferences
import com.jeeldesai.android.feedyou.data.model.cross.FeedWithEntries
import com.jeeldesai.android.feedyou.data.model.entry.Entry
import com.jeeldesai.android.feedyou.data.remote.FeedFetcher
import com.jeeldesai.android.feedyou.ui.activity.MainActivity
import com.jeeldesai.android.feedyou.util.extensions.sortedByDate
import java.util.concurrent.TimeUnit

class NewEntriesWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : BackgroundSyncWorker(context, workerParams) {

    private val resources = context.resources
    private val fetcher = FeedFetcher()

    override suspend fun doWork(): Result {
        val feedUrls = repo.getFeedUrlsSynchronously()
        if (feedUrls.isEmpty()) return Result.success()
        val lastIndex = FeedPreferences.lastPolledIndex
        val newIndex = if (lastIndex + 1 >= feedUrls.size) 0 else lastIndex + 1
        val url = feedUrls[newIndex]

        val feedData = repo.getFeedTitleWithEntriesToggleableSynchronously(url)
        val title = feedData.feedTitle // Need user-set title saved in DB
        val storedEntries = feedData.entriesToggleable
        val storedEntryIds: List<String> = storedEntries.map { it.url }
        val feedWithEntries: FeedWithEntries? = fetcher.requestSynchronously(url)

        feedWithEntries?.let { fwe ->
            val newEntries = fwe.entries.filterNot { storedEntryIds.contains(it.url) }
            handleRetrievedData(fwe, storedEntries, newEntries)

            if (newEntries.isNotEmpty()) {
                val notification = createNotification(url, title, newEntries)
                Intent(ACTION_SHOW_NOTIFICATION).apply {
                    putExtra(EXTRA_REQUEST_CODE, NOTIFICATION_ID)
                    putExtra(EXTRA_NOTIFICATION, notification)
                }.also { intent -> context.sendOrderedBroadcast(intent, PERM_PRIVATE) }
            }
        }

        FeedPreferences.lastPolledIndex = newIndex
        return Result.success()
    }

    private fun createNotification(
        feedId: String,
        feedTitle: String,
        entries: List<Entry>
    ): Notification {
        val latestEntry = entries.sortedByDate().first()
        val intent = MainActivity.newIntent(context, feedId, latestEntry.url)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = if (entries.size > 1) {
            resources.getString(com.jeeldesai.android.feedyou.R.string.and_more, latestEntry.title)
        } else {
            latestEntry.title
        }.also { text -> HtmlCompat.fromHtml(text, 0) }

        return NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setTicker(resources.getString(com.jeeldesai.android.feedyou.R.string.new_entries_notification_title, feedTitle))
            .setSmallIcon(com.jeeldesai.android.feedyou.R.drawable.feed_icon_small)
            .setContentTitle(resources.getString(com.jeeldesai.android.feedyou.R.string.new_entries_notification_title, feedTitle))
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
    }

    companion object {

        private const val WORK_NAME = "com.jeeldesai.android.feedyou.utils.work.NewEntriesWorker"
        const val ACTION_SHOW_NOTIFICATION = "com.jeeldesai.android.feedyou.utils.work.SHOW_NOTIFICATION"
        const val NOTIFICATION_ID = 1
        const val PERM_PRIVATE = "com.jeeldesai.android.feedyou.PRIVATE"
        const val EXTRA_REQUEST_CODE = "REQUEST_CODE"
        const val EXTRA_NOTIFICATION = "NOTIFICATION"

        fun start(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .setRequiresBatteryNotLow(true)
                .setRequiresStorageNotLow(true)
                .build()
            val request = PeriodicWorkRequest.Builder(
                NewEntriesWorker::class.java, 20, TimeUnit.MINUTES
            ).setConstraints(constraints).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}