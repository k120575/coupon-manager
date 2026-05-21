package com.kevin.coupy.notification

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kevin.coupy.MainActivity
import com.kevin.coupy.R
import com.kevin.coupy.data.CouponRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * 每日檢查即將到期的票券並發送推播。
 *
 * v1.0：固定 7 天前一次。沒有票券即將到期就不發。
 * v1.1+ Pro：自訂提前 1/3/7/30 天 + 地點觸發。
 */
@HiltWorker
class ExpiryReminderWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val couponRepository: CouponRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val today = LocalDate.now()
            val expiringSoon = couponRepository.observeExpiringSoon(today).first()

            if (expiringSoon.isEmpty()) {
                return Result.success()
            }

            val totalQuantity = expiringSoon.sumOf { it.quantity }
            val recordCount = expiringSoon.size
            val nearest = expiringSoon.minBy { it.expireDate }
            val nearestDays = java.time.temporal.ChronoUnit.DAYS
                .between(today, nearest.expireDate)

            val title = "$totalQuantity 張票券即將到期"
            val text = buildString {
                append(
                    when (nearestDays) {
                        0L -> "「${nearest.name}」今天到期"
                        1L -> "「${nearest.name}」明天到期"
                        else -> "「${nearest.name}」${nearestDays} 天後到期"
                    }
                )
                if (recordCount > 1) append("，還有 ${recordCount - 1} 張即將到期")
            }

            postNotification(title, text)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun postNotification(title: String, text: String) {
        // Android 13+ 需要 POST_NOTIFICATIONS 權限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(
            context,
            NotificationChannels.CHANNEL_EXPIRY_REMINDER
        )
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val WORK_NAME = "expiry_reminder_daily"
        private const val NOTIFICATION_ID = 1001
        private const val REQUEST_CODE = 1001
    }
}
