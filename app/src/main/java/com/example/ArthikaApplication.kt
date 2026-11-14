package com.example

import android.app.Application
import androidx.work.Configuration
import com.example.di.ArthikaAppContainer
import com.example.utils.NotificationHelper
import com.example.worker.DailyBudgetRecalcWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ArthikaApplication : Application(), Configuration.Provider {
    lateinit var container: ArthikaAppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        container = ArthikaAppContainer(this)
        NotificationHelper.ensureChannel(this)
        runCatching { DailyBudgetRecalcWorker.schedule(this) }
        container.financialAlertManager.schedulePeriodicMonitoring()
        appScope.launch {
            container.seeder.seedSystemDefaultsIfNeeded()
        }
    }
}
