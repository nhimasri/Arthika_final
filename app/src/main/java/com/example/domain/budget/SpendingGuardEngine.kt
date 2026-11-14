package com.example.domain.budget

import com.example.data.local.entity.SpendingRuleEntity
import com.example.data.local.entity.TransactionEntity
import com.example.domain.model.FinancialInsight
import com.example.domain.model.FinancialSummary
import com.example.domain.model.SpendingAlert
import com.example.domain.model.SpendingGuardLevel
import com.example.domain.model.TransactionType
import com.example.utils.CurrencyFormatter
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

class SpendingGuardEngine {

    fun evaluateAlerts(
        summary: FinancialSummary,
        today: LocalDate = LocalDate.now(),
        rules: List<SpendingRuleEntity> = emptyList(),
        singlePaymentLimit: BigDecimal? = null,
        triggeringTransaction: TransactionEntity? = null,
        todayTransactionCount: Int = 0
    ): List<SpendingAlert> {
        val alerts = mutableListOf<SpendingAlert>()
        val ruleMap = rules.associateBy { it.ruleCode }

        val dailyRule = ruleMap["DAILY_75"]
        val dailyRuleEnabled = dailyRule?.isEnabled ?: true
        val dailyThreshold = ((dailyRule?.thresholdPercent ?: 75).coerceIn(10, 100)) / 100f

        val categoryRule = ruleMap["CATEGORY_85"]
        val categoryRuleEnabled = categoryRule?.isEnabled ?: true
        val categoryThreshold = ((categoryRule?.thresholdPercent ?: 85).coerceIn(10, 100)) / 100f

        val paceRule = ruleMap["PACE_115"]
        val paceRuleEnabled = paceRule?.isEnabled ?: true
        val paceMultiplier = ((paceRule?.thresholdPercent ?: 115).coerceIn(100, 300)) / 100f

        val survivalRule = ruleMap["SURVIVAL_70"]
        val survivalRuleEnabled = survivalRule?.isEnabled ?: true

        val volumeRule = ruleMap["VOLUME_10"]
        val volumeRuleEnabled = volumeRule?.isEnabled ?: true
        val volumeLimit = (volumeRule?.thresholdPercent ?: 10).coerceIn(2, 200)
        val volumeApproachingCutoff = (((volumeLimit * 8) + 9) / 10).coerceAtMost(volumeLimit - 1)

        val txnSuffix = triggeringTransaction?.let {
            " after ${CurrencyFormatter.formatInrExact(it.amount)} to ${it.title}"
        }.orEmpty()

        // 0. Optional single-payment cap check
        if (singlePaymentLimit != null &&
            singlePaymentLimit > BigDecimal.ZERO &&
            triggeringTransaction != null &&
            triggeringTransaction.type == TransactionType.DEBIT &&
            !triggeringTransaction.isTransfer &&
            triggeringTransaction.amount > singlePaymentLimit
        ) {
            alerts.add(
                SpendingAlert(
                    id = "single_payment_exceeded",
                    title = "Single payment limit exceeded",
                    message = "${triggeringTransaction.title} (${CurrencyFormatter.formatInrExact(triggeringTransaction.amount)}) exceeded your ${CurrencyFormatter.formatInr(singlePaymentLimit)} per-payment cap.",
                    level = SpendingGuardLevel.RED
                )
            )
        }

        // 1. Monthly flexible budget overrun
        if (summary.flexibleBudget > BigDecimal.ZERO && summary.remainingFlexibleBudget < BigDecimal.ZERO) {
            alerts.add(
                SpendingAlert(
                    id = "flexible_exceeded",
                    title = "Monthly budget limit exceeded",
                    message = "You are over your flexible monthly budget by ${CurrencyFormatter.formatInrExact(summary.remainingFlexibleBudget.abs())}$txnSuffix.",
                    level = SpendingGuardLevel.RED
                )
            )
        }

        // 2. Daily allowance check (only when a monthly flexible budget exists)
        if (summary.flexibleBudget > BigDecimal.ZERO && summary.spentToday > BigDecimal.ZERO) {
            if (summary.dailyUsageRatio >= 1.0f) {
                alerts.add(
                    SpendingAlert(
                        id = "daily_exceeded",
                        title = "Daily limit exceeded",
                        message = "Spent ${CurrencyFormatter.formatInrExact(summary.spentToday)} today against your ${CurrencyFormatter.formatInrExact(summary.dailyAllowance)} safe limit$txnSuffix.",
                        level = SpendingGuardLevel.RED
                    )
                )
            } else if (dailyRuleEnabled && summary.dailyUsageRatio >= dailyThreshold) {
                val pct = (summary.dailyUsageRatio * 100).toInt()
                alerts.add(
                    SpendingAlert(
                        id = "daily_75",
                        title = "Approaching daily limit ($pct%)",
                        message = "${CurrencyFormatter.formatInr(summary.remainingToday.coerceAtLeast(BigDecimal.ZERO))} left for today's safe-to-spend allowance.",
                        level = SpendingGuardLevel.ORANGE
                    )
                )
            }
        }

        // 3. Category budget alerts (exceeded always fires; approaching respects CATEGORY_85 rule)
        val sortedCategories = summary.categoryBreakdowns
            .filter { it.budgetLimit > BigDecimal.ZERO }
            .sortedWith(
                compareByDescending<com.example.domain.model.CategoryBreakdown> {
                    it.categoryId == triggeringTransaction?.categoryId
                }.thenByDescending { it.usageRatio }
            )

        sortedCategories.forEach { cat ->
            if (cat.spentAmount > cat.budgetLimit) {
                val extra = if (triggeringTransaction?.categoryId == cat.categoryId) txnSuffix else ""
                alerts.add(
                    SpendingAlert(
                        id = "cat_${cat.categoryId}_exceeded",
                        title = "${cat.categoryName} budget exceeded",
                        message = "Spent ${CurrencyFormatter.formatInr(cat.spentAmount)} of ${CurrencyFormatter.formatInr(cat.budgetLimit)} (${CurrencyFormatter.formatInr(cat.remainingAmount.abs())} over)$extra.",
                        level = SpendingGuardLevel.RED
                    )
                )
            } else if (categoryRuleEnabled && cat.usageRatio >= categoryThreshold && alerts.count { it.id.startsWith("cat_") } < 3) {
                val pct = (cat.usageRatio * 100).toInt()
                alerts.add(
                    SpendingAlert(
                        id = "cat_${cat.categoryId}_warning",
                        title = "${cat.categoryName} at $pct% of limit",
                        message = "${CurrencyFormatter.formatInr(cat.remainingAmount)} remaining in ${cat.categoryName} this month.",
                        level = SpendingGuardLevel.ORANGE
                    )
                )
            }
        }

        // 4. Monthly spending pace check
        if (paceRuleEnabled && summary.flexibleBudget > BigDecimal.ZERO && summary.totalExpensesThisMonth > BigDecimal.ZERO) {
            val elapsedRatio = today.dayOfMonth.toFloat() / summary.totalDaysInMonth.toFloat()
            val spentRatio = summary.totalExpensesThisMonth
                .divide(summary.flexibleBudget, 4, RoundingMode.HALF_UP)
                .toFloat()
            if (spentRatio > elapsedRatio * paceMultiplier && summary.remainingFlexibleBudget >= BigDecimal.ZERO) {
                alerts.add(
                    SpendingAlert(
                        id = "monthly_pace",
                        title = "Spending faster than planned",
                        message = "You've used ${(spentRatio * 100).toInt()}% of flexible budget in ${(elapsedRatio * 100).toInt()}% of the month.",
                        level = SpendingGuardLevel.ORANGE
                    )
                )
            }
        }

        // 5. Survival mode alert
        if (survivalRuleEnabled && summary.isSurvivalModeActive && summary.flexibleBudget > BigDecimal.ZERO && summary.remainingFlexibleBudget >= BigDecimal.ZERO) {
            alerts.add(
                SpendingAlert(
                    id = "survival_mode",
                    title = "End-of-Month Survival Mode Active",
                    message = "Daily safe allowance dropped to ${CurrencyFormatter.formatInrExact(summary.dailyAllowance)}/day for ${summary.daysRemainingInMonth} remaining days.",
                    level = SpendingGuardLevel.RED
                )
            )
        }

        // 6. Transaction volume threshold check (VOLUME_10 rule)
        if (volumeRuleEnabled && todayTransactionCount > 0) {
            if (todayTransactionCount >= volumeLimit) {
                alerts.add(
                    SpendingAlert(
                        id = "volume_exceeded",
                        title = "Daily transaction volume exceeded",
                        message = "$todayTransactionCount outgoing payments recorded today (limit: $volumeLimit/day)$txnSuffix.",
                        level = SpendingGuardLevel.RED
                    )
                )
            } else if (todayTransactionCount >= volumeApproachingCutoff) {
                alerts.add(
                    SpendingAlert(
                        id = "volume_approaching",
                        title = "Approaching daily transaction volume ($todayTransactionCount/$volumeLimit)",
                        message = "You've logged $todayTransactionCount of your $volumeLimit daily outgoing transaction limit.",
                        level = SpendingGuardLevel.ORANGE
                    )
                )
            }
        }

        return alerts
    }
}

class FinancialInsightEngine {

    fun generateInsights(
        summary: FinancialSummary,
        pendingReviewCount: Int,
        today: LocalDate = LocalDate.now()
    ): List<FinancialInsight> {
        val insights = mutableListOf<FinancialInsight>()

        if (pendingReviewCount > 0) {
            insights.add(
                FinancialInsight(
                    id = "pending_sms_review",
                    message = "$pendingReviewCount SMS transaction${if (pendingReviewCount > 1) "s" else ""} waiting for your quick confirmation.",
                    level = SpendingGuardLevel.ORANGE,
                    actionHint = "Review now"
                )
            )
        }

        val topWarningCategory = summary.categoryBreakdowns
            .filter { it.guardLevel != SpendingGuardLevel.GREEN }
            .maxByOrNull { it.usageRatio }

        if (topWarningCategory != null) {
            val pct = (topWarningCategory.usageRatio * 100).toInt()
            val msg = if (topWarningCategory.guardLevel == SpendingGuardLevel.RED) {
                "Your ${topWarningCategory.categoryName.lowercase()} expenses exceeded the monthly limit ($pct%)."
            } else {
                "Your ${topWarningCategory.categoryName.lowercase()} expenses are approaching the monthly limit ($pct%)."
            }
            insights.add(
                FinancialInsight(
                    id = "insight_cat_${topWarningCategory.categoryId}",
                    message = msg,
                    level = topWarningCategory.guardLevel
                )
            )
        }

        if (summary.isSurvivalModeActive) {
            insights.add(
                FinancialInsight(
                    id = "insight_survival",
                    message = "Survival Mode active: stick to ${CurrencyFormatter.formatInrExact(summary.dailyAllowance)}/day for ${summary.daysRemainingInMonth} days.",
                    level = SpendingGuardLevel.RED
                )
            )
        } else if (summary.dailyUsageRatio < 0.75f && summary.dailyAllowance > BigDecimal.ZERO) {
            insights.add(
                FinancialInsight(
                    id = "insight_on_track",
                    message = "You stayed within your daily spending allowance — ${CurrencyFormatter.formatInr(summary.remainingToday)} still safe today.",
                    level = SpendingGuardLevel.GREEN
                )
            )
        }

        if (summary.totalTransfersExcluded > BigDecimal.ZERO) {
            insights.add(
                FinancialInsight(
                    id = "insight_transfers",
                    message = "${CurrencyFormatter.formatInr(summary.totalTransfersExcluded)} in self-transfers excluded to avoid double-counting.",
                    level = SpendingGuardLevel.GREEN
                )
            )
        }

        if (insights.isEmpty() && summary.protectedSavings > BigDecimal.ZERO) {
            insights.add(
                FinancialInsight(
                    id = "insight_default",
                    message = "Your ${CurrencyFormatter.formatInr(summary.protectedSavings)} savings goal is protected for ${DateUtilsMonthFallback(today)}.",
                    level = SpendingGuardLevel.GREEN
                )
            )
        }

        return insights
    }

    private fun DateUtilsMonthFallback(date: LocalDate): String =
        date.month.name.lowercase().replaceFirstChar { it.titlecase() }
}
