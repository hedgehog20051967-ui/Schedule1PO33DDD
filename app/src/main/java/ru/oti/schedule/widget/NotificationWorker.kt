package ru.oti.schedule.widget

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import ru.oti.schedule.data.ScheduleRepository
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.*
import java.util.concurrent.TimeUnit

class NotificationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("schedule_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("notifications_enabled", false)) return Result.success()

        val repository = ScheduleRepository(applicationContext)
        val schedule = try { repository.loadSchedule() } catch (e: Exception) { return Result.failure() }
        
        val today = LocalDate.now().dayOfWeek.getDisplayName(TextStyle.FULL, Locale("ru")).uppercase()
        val now = LocalTime.now()
        
        val nextLesson = schedule.lessons
            .filter { it.day == today }
            .mapNotNull { lesson ->
                val startTimeStr = lesson.time.split(Regex("[-–—]")).firstOrNull()?.trim() ?: return@mapNotNull null
                try {
                    val startTime = LocalTime.parse(startTimeStr.padStart(5, '0'))
                    if (startTime.isAfter(now)) lesson to startTime else null
                } catch (e: Exception) { null }
            }
            .minByOrNull { it.second }

        nextLesson?.let { (lesson, startTime) ->
            val diff = java.time.Duration.between(now, startTime).toMinutes()
            if (diff in 0..10) {
                val roomInfo = lesson.room ?: "—"
                showNotification(lesson.subject, "Начало в ${lesson.time} (через $diff мин), ауд. $roomInfo")
            }
        }

        return Result.success()
    }

    private fun showNotification(title: String, message: String) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "lesson_reminders"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Напоминания о парах", NotificationManager.IMPORTANCE_DEFAULT)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        manager.notify(1, notification)
    }

    companion object {
        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<NotificationWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "lesson_notifications",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork("lesson_notifications")
        }
    }
}
