package com.example.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.ArthikaApplication
import java.util.concurrent.TimeUnit

class DailyBudgetRecalcWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? ArthikaApplication ?: return Result.success()
        app.container.financialAlertManager.evaluateAndNotify(postSystemNotification = true)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "arthika_daily_budget_recalc"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DailyBudgetRecalcWorker>(
                24, TimeUnit.HOURS
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
