package com.example.domain.budget

import com.example.data.local.entity.BudgetEntity
import com.example.data.local.entity.CategoryBudgetEntity
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.FinancialProfileEntity
import com.example.data.local.entity.RecurringBillPaymentEntity
import com.example.data.local.entity.RecurringPaymentEntity
import com.example.data.local.entity.SavingsGoalEntity
import com.example.data.local.entity.TransactionEntity
import com.example.domain.model.CategoryBreakdown
import com.example.domain.model.FinancialSummary
import com.example.domain.model.SpendingGuardLevel
import com.example.domain.model.TransactionStatus
import com.example.domain.model.TransactionType
import com.example.domain.model.WhatIfSimulationResult
import com.example.utils.CurrencyFormatter
import com.example.utils.DateUtils
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class DynamicBudgetEngine {

    private fun BigDecimal.money(): BigDecimal = setScale(2, RoundingMode.HALF_UP)

    fun calculateSummary(
        profile: FinancialProfileEntity?,
        transactions: List<TransactionEntity>,
        recurringPayments: List<RecurringPaymentEntity>,
        categories: List<CategoryEntity>,
        categoryBudgets: List<CategoryBudgetEntity>,
        referenceDate: LocalDate = LocalDate.now(),
        zoneId: ZoneId = ZoneId.systemDefault(),
        monthlyBudget: BudgetEntity? = null,
        monthBillPayments: List<RecurringBillPaymentEntity> = emptyList()
    ): FinancialSummary {
        val month = YearMonth.from(referenceDate)
        val monthStr = month.toString()
        val totalDaysInMonth = month.lengthOfMonth()
        val daysRemainingInMonth = (totalDaysInMonth - referenceDate.dayOfMonth + 1).coerceAtLeast(1)

        // CRITICAL: Only CONFIRMED positive transactions for the target budgeting month influence financial calculations.
        // Transactions with NEEDS_REVIEW status are isolated until explicitly confirmed or rejected.
        val monthTransactions = transactions.filter { txn ->
            txn.status == TransactionStatus.CONFIRMED &&
                txn.amount > BigDecimal.ZERO &&
                YearMonth.from(DateUtils.toLocalDate(txn.timestamp, zoneId)) == month
        }

        val nonTransferDebits = monthTransactions.filter {
            it.type == TransactionType.DEBIT && !it.isTransfer
        }
        val nonTransferCredits = monthTransactions.filter {
            it.type == TransactionType.CREDIT && !it.isTransfer
        }
        val transfersExcluded = monthTransactions
            .filter { it.isTransfer }
            .fold(BigDecimal.ZERO) { acc, t -> acc + t.amount }
            .money()

        val activeMonthlyBudget = monthlyBudget?.takeIf { it.yearMonth == monthStr }
        val baseConfiguredIncome = (activeMonthlyBudget?.plannedIncome ?: profile?.monthlyIncome ?: BigDecimal.ZERO)
            .coerceAtLeast(BigDecimal.ZERO)
            .money()
        val fixedExpenses = (activeMonthlyBudget?.fixedExpenses ?: profile?.fixedExpenses ?: BigDecimal.ZERO)
            .coerceAtLeast(BigDecimal.ZERO)
            .money()
        val protectedSavings = (activeMonthlyBudget?.protectedSavings ?: profile?.protectedSavings ?: BigDecimal.ZERO)
            .coerceAtLeast(BigDecimal.ZERO)
            .money()

        // Account for confirmed extra credits (e.g., freelance payouts, refunds, gifts) without
        // double-counting the primary monthly salary already configured in the budget profile.
        val allConfirmedCredits = nonTransferCredits
            .fold(BigDecimal.ZERO) { acc, t -> acc + t.amount }
            .money()
        val (monthlyIncome, extraCreditsThisMonth) = if (baseConfiguredIncome > BigDecimal.ZERO) {
            val nonSalary = nonTransferCredits
                .filterNot { it.title.contains("salary", ignoreCase = true) }
                .fold(BigDecimal.ZERO) { acc, t -> acc + t.amount }
                .money()
            baseConfiguredIncome to nonSalary
        } else {
            allConfirmedCredits to BigDecimal.ZERO.money()
        }

        // Month-isolated bill reservation:
        // - Skip bills scheduled to begin in a future month (startYearMonth > month).
        // - Deduct payments already made in `monthStr` (including partial payments) from bill.amount.
        // - Preserve backwards compatibility when callers pass legacy `isPaidForCurrentMonth = true` with no payment rows.
        val paymentsByBillId = monthBillPayments
            .filter { it.yearMonth == monthStr }
            .groupBy { it.recurringPaymentId }

        val upcomingBillsReserved = recurringPayments
            .filter { it.isActive }
            .filter { bill ->
                val startYm = bill.startYearMonth?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
                startYm == null || !startYm.isAfter(month)
            }
            .fold(BigDecimal.ZERO) { acc, bill ->
                val paidThisMonth = when {
                    paymentsByBillId.containsKey(bill.id) ->
                        paymentsByBillId[bill.id].orEmpty().fold(BigDecimal.ZERO) { s, p -> s + p.paidAmount }
                    bill.paidYearMonth == monthStr -> bill.amount
                    bill.paidYearMonth == null && bill.isPaidForCurrentMonth && monthBillPayments.isEmpty() -> bill.amount
                    else -> BigDecimal.ZERO
                }
                val unpaidRemaining = (bill.amount - paidThisMonth).coerceAtLeast(BigDecimal.ZERO)
                acc + unpaidRemaining
            }
            .money()

        val rawFlexible = (monthlyIncome + extraCreditsThisMonth - fixedExpenses - protectedSavings - upcomingBillsReserved)
        val flexibleBudget = (profile?.monthlyBudgetOverride ?: rawFlexible)
            .coerceAtLeast(BigDecimal.ZERO)
            .money()

        val totalExpensesThisMonth = nonTransferDebits
            .fold(BigDecimal.ZERO) { acc, t -> acc + t.amount }
            .money()
        val spentToday = nonTransferDebits
            .filter { DateUtils.toLocalDate(it.timestamp, zoneId) == referenceDate }
            .fold(BigDecimal.ZERO) { acc, t -> acc + t.amount }
            .money()
        val spentBeforeToday = (totalExpensesThisMonth - spentToday).money()

        val remainingAtStartOfToday = (flexibleBudget - spentBeforeToday).money()
        val remainingFlexibleBudget = (flexibleBudget - totalExpensesThisMonth).money()

        val initialDailyAllowance = if (flexibleBudget <= BigDecimal.ZERO) {
            BigDecimal.ZERO.money()
        } else {
            flexibleBudget.divide(BigDecimal(totalDaysInMonth), 2, RoundingMode.HALF_UP)
        }

        val dailyAllowance = if (remainingAtStartOfToday <= BigDecimal.ZERO) {
            BigDecimal.ZERO.money()
        } else {
            remainingAtStartOfToday.divide(
                BigDecimal(daysRemainingInMonth),
                2,
                RoundingMode.HALF_UP
            )
        }

        val remainingToday = (dailyAllowance - spentToday).money()

        val dailyUsageRatio = if (dailyAllowance > BigDecimal.ZERO) {
            spentToday.divide(dailyAllowance, 4, RoundingMode.HALF_UP).toFloat().coerceAtLeast(0f)
        } else if (spentToday > BigDecimal.ZERO) {
            1.5f
        } else {
            0f
        }

        val guardLevel = when {
            remainingFlexibleBudget < BigDecimal.ZERO -> SpendingGuardLevel.RED
            dailyAllowance <= BigDecimal.ZERO && spentToday > BigDecimal.ZERO -> SpendingGuardLevel.RED
            dailyUsageRatio >= 1.0f -> SpendingGuardLevel.RED
            dailyUsageRatio >= 0.75f -> SpendingGuardLevel.ORANGE
            else -> SpendingGuardLevel.GREEN
        }

        val isSurvivalMode = remainingFlexibleBudget < BigDecimal.ZERO ||
            (initialDailyAllowance > BigDecimal.ZERO &&
                dailyAllowance < initialDailyAllowance.multiply(BigDecimal("0.70")))

        val latestPriorDebit = nonTransferDebits
            .filter { DateUtils.toLocalDate(it.timestamp, zoneId).isBefore(referenceDate) }
            .maxByOrNull { it.timestamp }

        val revisionExplanation = when {
            remainingFlexibleBudget < BigDecimal.ZERO ->
                "Flexible budget exceeded by ${CurrencyFormatter.formatInrExact(remainingFlexibleBudget.abs())}"
            isSurvivalMode ->
                "Survival Mode: ${CurrencyFormatter.formatInr(remainingFlexibleBudget.coerceAtLeast(BigDecimal.ZERO))} left for $daysRemainingInMonth days"
            latestPriorDebit != null ->
                "Revised after ${CurrencyFormatter.formatInr(latestPriorDebit.amount)} ${latestPriorDebit.title} debit"
            upcomingBillsReserved > BigDecimal.ZERO ->
                "${CurrencyFormatter.formatInr(upcomingBillsReserved)} reserved for upcoming bills"
            else ->
                "On track with your daily spending plan"
        }

        val survivalAdvice = buildSurvivalAdvice(
            isSurvivalMode = isSurvivalMode,
            remainingFlexible = remainingFlexibleBudget,
            daysRemaining = daysRemainingInMonth,
            protectedSavings = protectedSavings,
            upcomingBillsReserved = upcomingBillsReserved
        )

        val budgetLimitMap = categoryBudgets.associateBy { it.categoryId }
        val categoryBreakdowns = categories
            .filter { it.isExpenseCategory }
            .map { cat ->
                val spent = nonTransferDebits
                    .filter { it.categoryId == cat.id }
                    .fold(BigDecimal.ZERO) { acc, t -> acc + t.amount }
                    .money()
                val limit = (budgetLimitMap[cat.id]?.limitAmount ?: BigDecimal.ZERO)
                    .coerceAtLeast(BigDecimal.ZERO)
                    .money()
                val remaining = if (limit > BigDecimal.ZERO) (limit - spent).money() else BigDecimal.ZERO.money()
                val ratio = if (limit > BigDecimal.ZERO) {
                    spent.divide(limit, 4, RoundingMode.HALF_UP).toFloat().coerceAtLeast(0f)
                } else 0f
                val level = when {
                    limit <= BigDecimal.ZERO -> SpendingGuardLevel.GREEN
                    ratio > 1.0f -> SpendingGuardLevel.RED
                    ratio >= 0.80f -> SpendingGuardLevel.ORANGE
                    else -> SpendingGuardLevel.GREEN
                }
                CategoryBreakdown(
                    categoryId = cat.id,
                    categoryName = cat.name,
                    spentAmount = spent,
                    budgetLimit = limit,
                    remainingAmount = remaining,
                    usageRatio = ratio,
                    guardLevel = level
                )
            }

        return FinancialSummary(
            monthlyIncome = monthlyIncome,
            extraCreditsThisMonth = extraCreditsThisMonth,
            fixedExpenses = fixedExpenses,
            protectedSavings = protectedSavings,
            upcomingBillsReserved = upcomingBillsReserved,
            flexibleBudget = flexibleBudget,
            totalExpensesThisMonth = totalExpensesThisMonth,
            totalTransfersExcluded = transfersExcluded,
            remainingFlexibleBudget = remainingFlexibleBudget,
            daysRemainingInMonth = daysRemainingInMonth,
            totalDaysInMonth = totalDaysInMonth,
            initialDailyAllowance = initialDailyAllowance,
            dailyAllowance = dailyAllowance,
            spentToday = spentToday,
            remainingToday = remainingToday,
            dailyUsageRatio = dailyUsageRatio,
            guardLevel = guardLevel,
            isSurvivalModeActive = isSurvivalMode,
            revisionExplanation = revisionExplanation,
            survivalAdvice = survivalAdvice,
            categoryBreakdowns = categoryBreakdowns
        )
    }

    fun simulateWhatIfPurchase(
        summary: FinancialSummary,
        purchaseTitle: String,
        purchaseAmount: BigDecimal,
        goals: List<SavingsGoalEntity>
    ): WhatIfSimulationResult {
        val safePurchase = purchaseAmount.coerceAtLeast(BigDecimal.ZERO).money()
        val remainingAfter = (summary.remainingFlexibleBudget - safePurchase).money()
        val daysLeft = summary.daysRemainingInMonth.coerceAtLeast(1)

        val revisedDaily = if (remainingAfter <= BigDecimal.ZERO) {
            BigDecimal.ZERO.money()
        } else {
            remainingAfter.divide(BigDecimal(daysLeft), 2, RoundingMode.HALF_UP)
        }

        val currentRemainingDaily = if (summary.remainingFlexibleBudget <= BigDecimal.ZERO) {
            BigDecimal.ZERO.money()
        } else {
            summary.remainingFlexibleBudget.divide(BigDecimal(daysLeft), 2, RoundingMode.HALF_UP)
        }

        val delta = (revisedDaily - currentRemainingDaily).money()

        val guardAfter = when {
            remainingAfter < BigDecimal.ZERO -> SpendingGuardLevel.RED
            revisedDaily < summary.initialDailyAllowance.multiply(BigDecimal("0.70")) -> SpendingGuardLevel.ORANGE
            else -> SpendingGuardLevel.GREEN
        }

        val nearestGoal = goals.minByOrNull { it.targetDateEpochMillis }
        val goalImpact = when {
            remainingAfter >= BigDecimal.ZERO -> {
                if (nearestGoal != null) {
                    "Protected savings (${CurrencyFormatter.formatInr(summary.protectedSavings)}) and '${nearestGoal.name}' stay safe."
                } else {
                    "Your protected savings remain completely untouched."
                }
            }
            else -> {
                val deficit = remainingAfter.abs()
                if (nearestGoal != null) {
                    "Overshoots flexible budget by ${CurrencyFormatter.formatInrExact(deficit)} — could delay '${nearestGoal.name}' contributions."
                } else {
                    "Eats ${CurrencyFormatter.formatInrExact(deficit)} into your protected savings buffer."
                }
            }
        }

        val advisory = when (guardAfter) {
            SpendingGuardLevel.GREEN ->
                "Safe to buy: daily allowance adjusts gently to ${CurrencyFormatter.formatInrExact(revisedDaily)}/day."
            SpendingGuardLevel.ORANGE ->
                "Proceed with caution: daily limit drops by ${CurrencyFormatter.formatInrExact(delta.abs())}/day to ${CurrencyFormatter.formatInrExact(revisedDaily)}."
            SpendingGuardLevel.RED ->
                "Exceeds flexible budget! Consider deferring this purchase or splitting across next month."
        }

        return WhatIfSimulationResult(
            purchaseTitle = purchaseTitle.ifBlank { "Hypothetical Purchase" },
            purchaseAmount = safePurchase,
            currentFlexibleBudget = summary.remainingFlexibleBudget,
            remainingBudgetAfter = remainingAfter,
            currentDailyAllowance = currentRemainingDaily,
            revisedDailyAllowance = revisedDaily,
            dailyAllowanceDelta = delta,
            guardLevelAfter = guardAfter,
            goalImpactSummary = goalImpact,
            advisoryMessage = advisory
        )
    }

    private fun buildSurvivalAdvice(
        isSurvivalMode: Boolean,
        remainingFlexible: BigDecimal,
        daysRemaining: Int,
        protectedSavings: BigDecimal,
        upcomingBillsReserved: BigDecimal
    ): List<String> {
        if (!isSurvivalMode) return emptyList()
        val advice = mutableListOf<String>()
        if (remainingFlexible <= BigDecimal.ZERO) {
            advice.add(
                "Pause non-essential discretionary spends for the next $daysRemaining days to protect your ${CurrencyFormatter.formatInr(protectedSavings)} savings floor."
            )
        } else {
            val revised = remainingFlexible.divide(BigDecimal(daysRemaining.coerceAtLeast(1)), 2, RoundingMode.HALF_UP)
            advice.add("Cap daily spending at ${CurrencyFormatter.formatInrExact(revised)} across the remaining $daysRemaining days.")
        }
        if (upcomingBillsReserved > BigDecimal.ZERO) {
            advice.add("${CurrencyFormatter.formatInr(upcomingBillsReserved)} is safely ring-fenced for unpaid recurring bills.")
        }
        advice.add("Try a 3-day No-Spend Challenge on dining out & impulse shopping to restore buffer.")
        return advice
    }
}
