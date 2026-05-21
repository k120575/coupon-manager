package com.kevin.coupy.notification

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/**
 * 排程「每日到期提醒」WorkManager 任務。
 *
 * 觸發時間：每天接近上午 9:00（精確時間由 WorkManager 安排，會略有偏差）。
 * 重複呼叫 schedule 不會重新排程——用 KEEP 政策。
 */
object NotificationScheduler {

    private const val TARGET_HOUR = 9

    fun scheduleDailyReminder(context: Context) {
        val now = LocalDateTime.now()
        val next9AM = if (now.hour < TARGET_HOUR) {
            now.withHour(TARGET_HOUR).withMinute(0).withSecond(0).withNano(0)
        } else {
            now.plusDays(1).withHour(TARGET_HOUR).withMinute(0).withSecond(0).withNano(0)
        }
        val initialDelayMillis = ChronoUnit.MILLIS.between(now, next9AM)

        val request = PeriodicWorkRequestBuilder<ExpiryReminderWorker>(
            repeatInterval = 1,
            repeatIntervalTimeUnit = TimeUnit.DAYS
        )
            .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            ExpiryReminderWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /** 給開發/測試用：取消已排程的提醒 */
    fun cancel(context: Context) {
        WorkManager.getInstance(context)
            .cancelUniqueWork(ExpiryReminderWorker.WORK_NAME)
    }

    /** 立即執行一次到期檢查（測試用，不影響每日排程）*/
    fun runOnce(context: Context) {
        val request = OneTimeWorkRequestBuilder<ExpiryReminderWorker>().build()
        WorkManager.getInstance(context).enqueue(request)
    }
}
