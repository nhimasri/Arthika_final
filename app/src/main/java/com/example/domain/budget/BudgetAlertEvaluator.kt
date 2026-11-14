package com.example.domain.budget

import android.content.Context
import com.example.data.local.ArthikaDatabase
import com.example.data.local.UserPreferencesDataStore
import com.example.data.local.entity.TransactionEntity
import com.example.domain.model.SpendingAlert

class BudgetAlertEvaluator(
    context: Context,
    db: ArthikaDatabase,
    preferencesDataStore: UserPreferencesDataStore,
    dynamicBudgetEngine: DynamicBudgetEngine,
    spendingGuardEngine: SpendingGuardEngine,
    private val financialAlertManager: FinancialAlertManager = FinancialAlertManager(
        context = context,
        db = db,
        preferencesDataStore = preferencesDataStore,
        dynamicBudgetEngine = dynamicBudgetEngine,
        spendingGuardEngine = spendingGuardEngine
    )
) {
    suspend fun evaluateAfterTransaction(
        transaction: TransactionEntity,
        postSystemNotification: Boolean = true
    ): List<SpendingAlert> {
        val alerts = financialAlertManager.evaluateAndNotify(
            triggeringTransactionId = transaction.id,
            triggeringTransaction = transaction,
            postSystemNotification = postSystemNotification
        )
        financialAlertManager.scheduleImmediateCheck(transaction.id)
        return alerts
    }

    suspend fun evaluatePeriodicSnapshot(): List<SpendingAlert> {
        return financialAlertManager.evaluateAndNotify(
            triggeringTransactionId = null,
            postSystemNotification = true
        )
    }
}

