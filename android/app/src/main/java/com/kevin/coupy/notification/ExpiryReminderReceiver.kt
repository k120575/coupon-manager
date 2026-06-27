package com.kevin.coupy.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 接住兩類事件：
 *  - [NotificationScheduler.ACTION_FIRE]：每日鬧鐘響了 → 跑一次到期檢查，並重排明天的鬧鐘。
 *  - 開機 / App 更新：AlarmManager 的鬧鐘在重開機（或 App 被覆蓋安裝）後會被系統清掉，
 *    這裡負責重新排定，否則使用者重開機後就再也收不到提醒。
 */
class ExpiryReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            NotificationScheduler.ACTION_FIRE -> {
                NotificationScheduler.enqueueCheck(context)
                // 一次性鬧鐘觸發後自己排下一次
                NotificationScheduler.scheduleDailyReminder(context)
            }

            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                NotificationScheduler.scheduleDailyReminder(context)
            }
        }
    }
}
