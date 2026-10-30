package com.hartmannstudios.psalmwell.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.hartmannstudios.psalmwell.MainActivity
import com.hartmannstudios.psalmwell.R
import com.hartmannstudios.psalmwell.data.ScriptureRepository
import com.hartmannstudios.psalmwell.i18n.Translator
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

object ReminderScheduler {
    const val CHANNEL_ID = "daily_verse"
    private const val WORK_NAME = "psalmwell_daily_reminder"

    fun createChannel(context: Context) {
        runCatching {
            val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.notification_channel_description) }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    fun schedule(context: Context, enabled: Boolean, hour: Int) {
        runCatching {
            val wm = WorkManager.getInstance(context)
            if (!enabled) { wm.cancelUniqueWork(WORK_NAME); return }
            val now = ZonedDateTime.now()
            var next = now.withHour(hour.coerceIn(0, 23)).withMinute(0).withSecond(0).withNano(0)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val request = PeriodicWorkRequestBuilder<DailyVerseWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
                .build()
            wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}

class DailyVerseWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!ReminderScheduler.canNotify(applicationContext)) return Result.success()
        return runCatching {
            val verse = ScriptureRepository(applicationContext).today()
            // Shown in the reader's chosen language when its pack is on the phone; English otherwise.
            val translator = Translator.instance
            val title = translator?.tr("Today's verse") ?: "Today's verse"
            val reference = translator?.tr(verse.reference) ?: verse.reference
            val text = translator?.tr(verse.text) ?: verse.text
            val open = PendingIntent.getActivity(
                applicationContext, 0,
                Intent(applicationContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val notification = NotificationCompat.Builder(applicationContext, ReminderScheduler.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("$title · $reference")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            if (Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            ) {
                try {
                    NotificationManagerCompat.from(applicationContext).notify(2001, notification)
                } catch (_: SecurityException) { }
            }
            Result.success()
        }.getOrElse { Result.success() }
    }
}
