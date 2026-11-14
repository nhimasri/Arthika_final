package com.example.data.local

import androidx.room.withTransaction
import com.example.data.local.entity.BudgetEntity
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.SmsSenderEntity
import com.example.data.local.entity.SpendingRuleEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.YearMonth

/**
 * Seeds only structural system reference data (categories, Indian bank SMS sender headers,
 * and Spending Guard rules). Never inserts dummy user profiles, budgets, goals, or transactions.
 */
class DatabaseSeeder(private val db: ArthikaDatabase) {

    private val seedMutex = Mutex()

    suspend fun seedSystemDefaultsIfNeeded() {
        seedMutex.withLock {
            db.withTransaction {
                if (db.categoryDao().getCategoriesOnce().isEmpty()) {
                    db.categoryDao().insertAll(defaultCategories())
                }
                if (db.smsAuditDao().getSendersOnce().isEmpty()) {
                    db.smsAuditDao().insertSenders(defaultSmsSenders())
                }
                val existingRules = db.spendingRuleDao().observeRulesOnce()
                val existingCodes = existingRules.map { it.ruleCode }.toSet()
                val missingRules = defaultSpendingRules().filter { it.ruleCode !in existingCodes }
                if (missingRules.isNotEmpty()) {
                    db.spendingRuleDao().insertAll(missingRules)
                }
            }
        }
    }

    suspend fun getOrCreateCurrentMonthBudget(yearMonth: YearMonth = YearMonth.now()): BudgetEntity {
        val currentYm = yearMonth.toString()
        val budgetDao = db.budgetDao()
        budgetDao.getBudgetForMonthOnce(currentYm)?.let { return it }

        return db.withTransaction {
            budgetDao.getBudgetForMonthOnce(currentYm)?.let { return@withTransaction it }
            val profile = db.financialProfileDao().getProfileOnce()
            val income = (profile?.monthlyIncome ?: BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)
            val fixed = (profile?.fixedExpenses ?: BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)
            val savings = (profile?.protectedSavings ?: BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)
            val flexible = (income - fixed - savings).coerceAtLeast(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)

            val newBudget = BudgetEntity(
                yearMonth = currentYm,
                plannedIncome = income,
                fixedExpenses = fixed,
                protectedSavings = savings,
                flexibleBudget = flexible
            )
            val budgetId = budgetDao.insertBudget(newBudget)
            newBudget.copy(id = budgetId)
        }
    }

    companion object {
        fun defaultCategories(): List<CategoryEntity> = listOf(
            CategoryEntity("food", "Food", "restaurant", "#E76F51", true, 1),
            CategoryEntity("transportation", "Transportation", "directions_car", "#4F7CAC", true, 2),
            CategoryEntity("shopping", "Shopping", "shopping_bag", "#36B37E", true, 3),
            CategoryEntity("entertainment", "Entertainment", "movie", "#8B5CF6", true, 4),
            CategoryEntity("education", "Education", "school", "#0EA5E9", true, 5),
            CategoryEntity("healthcare", "Healthcare", "local_hospital", "#EC4899", true, 6),
            CategoryEntity("bills", "Bills", "bolt", "#F4A261", true, 7),
            CategoryEntity("other", "Other", "category", "#64748B", true, 8)
        )

        fun defaultSmsSenders(): List<SmsSenderEntity> = listOf(
            SmsSenderEntity(senderPattern = "HDFCBK", bankDisplayName = "HDFC Bank"),
            SmsSenderEntity(senderPattern = "ICICIB", bankDisplayName = "ICICI Bank"),
            SmsSenderEntity(senderPattern = "SBIINB", bankDisplayName = "State Bank of India"),
            SmsSenderEntity(senderPattern = "SBIPSG", bankDisplayName = "SBI UPI"),
            SmsSenderEntity(senderPattern = "AXISBK", bankDisplayName = "Axis Bank"),
            SmsSenderEntity(senderPattern = "KOTAKB", bankDisplayName = "Kotak Mahindra Bank"),
            SmsSenderEntity(senderPattern = "INDUSB", bankDisplayName = "IndusInd Bank"),
            SmsSenderEntity(senderPattern = "YESBNK", bankDisplayName = "Yes Bank"),
            SmsSenderEntity(senderPattern = "IDFCFB", bankDisplayName = "IDFC First Bank"),
            SmsSenderEntity(senderPattern = "PNBSMS", bankDisplayName = "Punjab National Bank"),
            SmsSenderEntity(senderPattern = "BOBSMS", bankDisplayName = "Bank of Baroda"),
            SmsSenderEntity(senderPattern = "FEDBNK", bankDisplayName = "Federal Bank"),
            SmsSenderEntity(senderPattern = "CANBNK", bankDisplayName = "Canara Bank"),
            SmsSenderEntity(senderPattern = "PAYTM", bankDisplayName = "Paytm Payments"),
            SmsSenderEntity(senderPattern = "GPAY", bankDisplayName = "Google Pay"),
            SmsSenderEntity(senderPattern = "PHONEPE", bankDisplayName = "PhonePe")
        )

        fun defaultSpendingRules(): List<SpendingRuleEntity> = listOf(
            SpendingRuleEntity(ruleCode = "DAILY_75", name = "Daily Allowance 75% Guard", thresholdPercent = 75),
            SpendingRuleEntity(ruleCode = "CATEGORY_85", name = "Category Budget 85% Warning", thresholdPercent = 85),
            SpendingRuleEntity(ruleCode = "PACE_115", name = "Monthly Burn Rate Guard", thresholdPercent = 115),
            SpendingRuleEntity(ruleCode = "SURVIVAL_70", name = "Survival Mode Auto-Trigger", thresholdPercent = 70),
            SpendingRuleEntity(ruleCode = "VOLUME_10", name = "Daily Transaction Volume Limit", thresholdPercent = 10)
        )
    }
}
