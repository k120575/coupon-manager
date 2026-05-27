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
import com.kevin.coupy.data.notification.ExpiryReminderPreferenceRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 每日檢查即將到期的票券並發送推播。
 *
 * 觸發時機由使用者偏好決定：到期前 1 / 3 / 7 / 30 天可多選（預設 3 + 7）。
 * 沒有任何票券剛好命中啟用的天數，就不發推播。
 */
@HiltWorker
class ExpiryReminderWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val couponRepository: CouponRepository,
    private val preferenceRepository: ExpiryReminderPreferenceRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val enabledDays = preferenceRepository.enabledDays.first()
            if (enabledDays.isEmpty()) {
                return Result.success()
            }

            val today = LocalDate.now()
            // 票券到期日 - 今天 == 任一勾選天數，才命中
            val matching = couponRepository.observeActive().first()
                .mapNotNull { coupon ->
                    val daysUntil = ChronoUnit.DAYS.between(today, coupon.expireDate).toInt()
                    if (daysUntil in enabledDays) coupon to daysUntil else null
                }
                .sortedBy { it.second }

            if (matching.isEmpty()) {
                return Result.success()
            }

            val totalQuantity = matching.sumOf { it.first.quantity }
            val recordCount = matching.size
            val (mostUrgent, mostUrgentDays) = matching.first()

            val title = "$totalQuantity 張票券即將到期"
            val text = buildString {
                append(
                    when (mostUrgentDays) {
                        1 -> "「${mostUrgent.name}」明天到期"
                        else -> "「${mostUrgent.name}」$mostUrgentDays 天後到期"
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
            .setSmallIcon(R.drawable.ic_notification_ticket)
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
