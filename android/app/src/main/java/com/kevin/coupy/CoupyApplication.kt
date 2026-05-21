package com.kevin.coupy

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.kevin.coupy.notification.NotificationChannels
import com.kevin.coupy.notification.NotificationScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class CoupyApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // 建立 notification channel（首次必要、之後重複呼叫無副作用）
        NotificationChannels.ensureChannels(this)
        // 排程每日到期提醒（KEEP 政策，重複呼叫不會重排）
        NotificationScheduler.scheduleDailyReminder(this)
    }
}
