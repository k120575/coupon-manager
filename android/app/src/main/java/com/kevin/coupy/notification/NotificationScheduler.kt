package com.kevin.coupy.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.getSystemService
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 排程「每日到期提醒」。
 *
 * 用 [AlarmManager] 的 `setAndAllowWhileIdle` 在每天接近上午 9:00 觸發 —— 即使裝置在
 * Doze（省電休眠）也會在維護窗口觸發，比 WorkManager 的週期任務可靠得多。
 *
 * setAndAllowWhileIdle 是「一次性」鬧鐘，所以觸發後由 [ExpiryReminderReceiver] 負責
 * 重新排下一次。App 啟動 / 開機 / 更新時也會重排，多次呼叫是冪等的（同一個 PendingIntent
 * 會覆蓋舊鬧鐘，不會疊加）。
 *
 * 不需要 SCHEDULE_EXACT_ALARM 權限：我們刻意用 inexact 版本，9 點前後幾分鐘差異無所謂，
 * 換來最省電、不會在 Play Console 觸發敏感權限審查。
 */
object NotificationScheduler {

    private const val TARGET_HOUR = 9
    private const val ALARM_REQUEST_CODE = 2001

    /** 鬧鐘觸發時 [ExpiryReminderReceiver] 收到的 action */
    const val ACTION_FIRE = "com.kevin.coupy.action.EXPIRY_REMINDER_FIRE"

    /** 排定（或重排）下一次每日提醒鬧鐘。冪等。 */
    fun scheduleDailyReminder(context: Context) {
        val alarmManager = context.getSystemService<AlarmManager>() ?: return
        val triggerAtMillis = nextTriggerMillis()

        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            alarmPendingIntent(context)
        )
    }

    /** 鬧鐘響時呼叫：丟一個 OneTimeWork 去做實際的 DB 檢查＋發推播（Hilt 注入在 Worker 裡）。 */
    fun enqueueCheck(context: Context) {
        val request = OneTimeWorkRequestBuilder<ExpiryReminderWorker>().build()
        WorkManager.getInstance(context).enqueue(request)
    }

    /** 立即執行一次到期檢查（測試用，不影響每日排程）*/
    fun runOnce(context: Context) {
        enqueueCheck(context)
    }

    /** 給開發/測試用：取消已排程的提醒 */
    fun cancel(context: Context) {
        val alarmManager = context.getSystemService<AlarmManager>() ?: return
        alarmManager.cancel(alarmPendingIntent(context))
    }

    /** 下一個上午 9:00 的 epoch millis（已過今天 9 點就排明天）。 */
    private fun nextTriggerMillis(): Long {
        val now = LocalDateTime.now()
        val today9AM = now.withHour(TARGET_HOUR).withMinute(0).withSecond(0).withNano(0)
        val next = if (now.isBefore(today9AM)) today9AM else today9AM.plusDays(1)
        return next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun alarmPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ExpiryReminderReceiver::class.java).apply {
            action = ACTION_FIRE
        }
        return PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
