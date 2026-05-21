package com.kevin.coupy.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService

/**
 * Notification channel 設定。
 *
 * App 啟動時呼叫 [ensureChannels] 確保 channel 已建立（重複呼叫無副作用）。
 */
object NotificationChannels {

    const val CHANNEL_EXPIRY_REMINDER = "expiry_reminder"

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService<NotificationManager>() ?: return

        val expiryChannel = NotificationChannel(
            CHANNEL_EXPIRY_REMINDER,
            "到期提醒",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "提醒你即將到期的票券"
            setShowBadge(true)
        }

        manager.createNotificationChannel(expiryChannel)
    }
}
