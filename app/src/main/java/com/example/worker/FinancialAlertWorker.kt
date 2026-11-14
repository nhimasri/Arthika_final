package com.example.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.ArthikaApplication

/**
 * WorkManager worker executed either periodically or immediately after transaction updates
 * to inspect Room transaction volumes and budget usage against Spending Guard thresholds.
 */
class FinancialAlertWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? ArthikaApplication ?: return Result.success()
        val txnId = inputData.getLong(KEY_TRANSACTION_ID, -1L).takeIf { it > 0L }
        app.container.financialAlertManager.evaluateAndNotify(
            triggeringTransactionId = txnId,
            postSystemNotification = true
        )
        return Result.success()
    }

    companion object {
        const val KEY_TRANSACTION_ID = "triggering_transaction_id"
    }
}
