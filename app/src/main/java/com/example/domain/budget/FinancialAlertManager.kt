package com.example.domain.budget

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.data.local.ArthikaDatabase
import com.example.data.local.UserPreferencesDataStore
import com.example.data.local.entity.NotificationEntity
import com.example.data.local.entity.TransactionEntity
import com.example.domain.model.SpendingAlert
import com.example.domain.model.SpendingGuardLevel
import com.example.domain.model.TransactionStatus
import com.example.domain.model.TransactionType
import com.example.utils.DateUtils
import com.example.utils.NotificationHelper
import com.example.worker.FinancialAlertWorker
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.concurrent.TimeUnit

enum class ThresholdStatus {
    SAFE,
    APPROACHING_LIMIT,
    BUDGET_EXCEEDED
}

/**
 * Coordinates WorkManager background checks and evaluates transaction volumes and
 * budget usage against thresholds defined in the Room `spending_rules` table.
 */
class FinancialAlertManager(
    private val context: Context,
    private val db: ArthikaDatabase,
    private val preferencesDataStore: UserPreferencesDataStore,
    private val dynamicBudgetEngine: DynamicBudgetEngine,
    private val spendingGuardEngine: SpendingGuardEngine
) {
    fun schedulePeriodicMonitoring() {
        runCatching {
            val request = PeriodicWorkRequestBuilder<FinancialAlertWorker>(
                6, TimeUnit.HOURS
            ).addTag(TAG_PERIODIC_MONITOR).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }

    fun scheduleImmediateCheck(transactionId: Long? = null) {
        runCatching {
            val request = OneTimeWorkRequestBuilder<FinancialAlertWorker>()
                .setInputData(
                    workDataOf(FinancialAlertWorker.KEY_TRANSACTION_ID to (transactionId ?: -1L))
                )
                .addTag(TAG_IMMEDIATE_CHECK)
                .build()

            WorkManager.getInstance(context).enqueue(request)
        }
    }

    suspend fun evaluateAndNotify(
        triggeringTransactionId: Long? = null,
        triggeringTransaction: TransactionEntity? = null,
        postSystemNotification: Boolean = true
    ): List<SpendingAlert> {
        val resolvedTxn = triggeringTransaction
            ?: triggeringTransactionId?.takeIf { it > 0L }?.let { db.transactionDao().getById(it) }

        if (resolvedTxn != null && (
                resolvedTxn.status != TransactionStatus.CONFIRMED ||
                    resolvedTxn.type != TransactionType.DEBIT ||
                    resolvedTxn.isTransfer
                )
        ) {
            return emptyList()
        }

        val prefs = preferencesDataStore.preferencesFlow.first()
        val today = LocalDate.now()
        val ym = YearMonth.from(today)
        val ymStr = ym.toString()

        val monthTxns = db.transactionDao().getTransactionsBetweenOnce(
            DateUtils.startOfMonthMillis(ym),
            DateUtils.endOfMonthMillis(ym)
        ).filter { it.status == TransactionStatus.CONFIRMED }

        val profile = db.financialProfileDao().getProfileOnce()
        val monthlyBudget = db.budgetDao().getBudgetForMonthOnce(ymStr)
        val recurring = db.recurringPaymentDao().getActiveRecurringPaymentsOnce()
        val monthBillPayments = db.recurringPaymentDao().getBillPaymentsForMonthOnce(ymStr)
        val categories = db.categoryDao().getCategoriesOnce()
        val categoryBudgets = db.budgetDao().getCategoryBudgetsForMonthOnce(ymStr)
        val rules = db.spendingRuleDao().observeRulesOnce()

        val todayTransactionCount = monthTxns.count {
            it.type == TransactionType.DEBIT &&
                !it.isTransfer &&
                DateUtils.toLocalDate(it.timestamp) == today
        }

        val summary = dynamicBudgetEngine.calculateSummary(
            profile = profile,
            transactions = monthTxns,
            recurringPayments = recurring,
            categories = categories,
            categoryBudgets = categoryBudgets,
            referenceDate = today,
            monthlyBudget = monthlyBudget,
            monthBillPayments = monthBillPayments
        )

        val evaluated = spendingGuardEngine.evaluateAlerts(
            summary = summary,
            today = today,
            rules = rules,
            singlePaymentLimit = prefs.singlePaymentLimit,
            triggeringTransaction = resolvedTxn,
            todayTransactionCount = todayTransactionCount
        )
        if (evaluated.isEmpty()) return emptyList()

        val startOfDayMillis = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val existingToday = db.notificationDao().getNotificationsSinceOnce(startOfDayMillis)
        val existingTitlesToday = existingToday.map { it.title }.toSet()

        val toNotify = evaluated.filter { alert ->
            when (classifyStatus(alert.level)) {
                ThresholdStatus.BUDGET_EXCEEDED -> {
                    if (resolvedTxn != null) {
                        // Immediate alert for an exceeding transaction unless already recorded for this txnId.
                        existingToday.none {
                            it.title == alert.title && it.relatedTransactionId == resolvedTxn.id
                        }
                    } else {
                        alert.title !in existingTitlesToday
                    }
                }
                ThresholdStatus.APPROACHING_LIMIT -> {
                    // Approaching warnings fire at most once per day per threshold title.
                    alert.title !in existingTitlesToday
                }
                ThresholdStatus.SAFE -> false
            }
        }

        val filteredForWorkerCap = if (resolvedTxn == null) toNotify.take(2) else toNotify

        filteredForWorkerCap.forEachIndexed { index, alert ->
            val rowId = db.notificationDao().insert(
                NotificationEntity(
                    title = alert.title,
                    message = alert.message,
                    level = alert.level,
                    relatedTransactionId = resolvedTxn?.id
                )
            )
            if (postSystemNotification && prefs.dailyAlertsEnabled) {
                val baseId = if (resolvedTxn != null) 100_000 else 200_000
                val notifId = baseId + (rowId.toInt() * 10) + index
                NotificationHelper.showAlert(
                    context = context,
                    notificationId = notifId,
                    title = alert.title,
                    message = alert.message,
                    level = alert.level
                )
            }
        }

        return filteredForWorkerCap
    }

    fun classifyStatus(level: SpendingGuardLevel): ThresholdStatus = when (level) {
        SpendingGuardLevel.GREEN -> ThresholdStatus.SAFE
        SpendingGuardLevel.ORANGE -> ThresholdStatus.APPROACHING_LIMIT
        SpendingGuardLevel.RED -> ThresholdStatus.BUDGET_EXCEEDED
    }

    companion object {
        const val PERIODIC_WORK_NAME = "arthika_financial_alert_monitor"
        const val TAG_PERIODIC_MONITOR = "arthika_periodic_threshold_monitor"
        const val TAG_IMMEDIATE_CHECK = "arthika_immediate_threshold_check"
    }
}
