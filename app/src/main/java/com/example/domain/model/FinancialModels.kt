package com.example.domain.model

import java.math.BigDecimal

enum class TransactionType {
    DEBIT,
    CREDIT
}

enum class TransactionSource {
    SMS,
    MANUAL
}

enum class TransactionStatus {
    CONFIRMED,
    NEEDS_REVIEW
}

enum class PaymentMethod(val label: String) {
    UPI("UPI"),
    CARD("Card"),
    BANK_TRANSFER("Bank Transfer"),
    CASH("Cash"),
    NET_BANKING("Net Banking"),
    WALLET("Wallet"),
    OTHER("Other")
}

enum class SpendingGuardLevel(val label: String) {
    GREEN("Within budget"),
    ORANGE("Approaching limit"),
    RED("Budget exceeded")
}

enum class SmsProcessDecision {
    IMPORTED,
    DUPLICATE,
    REJECTED_OTP,
    REJECTED_PROMO,
    IGNORED_INSUFFICIENT,
    IGNORED_DISABLED
}

data class CategoryBreakdown(
    val categoryId: String,
    val categoryName: String,
    val spentAmount: BigDecimal,
    val budgetLimit: BigDecimal,
    val remainingAmount: BigDecimal,
    val usageRatio: Float,
    val guardLevel: SpendingGuardLevel
)

data class FinancialSummary(
    val monthlyIncome: BigDecimal,
    val extraCreditsThisMonth: BigDecimal,
    val fixedExpenses: BigDecimal,
    val protectedSavings: BigDecimal,
    val upcomingBillsReserved: BigDecimal,
    val flexibleBudget: BigDecimal,
    val totalExpensesThisMonth: BigDecimal,
    val totalTransfersExcluded: BigDecimal,
    val remainingFlexibleBudget: BigDecimal,
    val daysRemainingInMonth: Int,
    val totalDaysInMonth: Int,
    val initialDailyAllowance: BigDecimal,
    val dailyAllowance: BigDecimal,
    val spentToday: BigDecimal,
    val remainingToday: BigDecimal,
    val dailyUsageRatio: Float,
    val guardLevel: SpendingGuardLevel,
    val isSurvivalModeActive: Boolean,
    val revisionExplanation: String,
    val survivalAdvice: List<String>,
    val categoryBreakdowns: List<CategoryBreakdown>
)

data class SpendingAlert(
    val id: String,
    val title: String,
    val message: String,
    val level: SpendingGuardLevel
)

data class FinancialInsight(
    val id: String,
    val message: String,
    val level: SpendingGuardLevel,
    val actionHint: String? = null
)

data class WhatIfSimulationResult(
    val purchaseTitle: String,
    val purchaseAmount: BigDecimal,
    val currentFlexibleBudget: BigDecimal,
    val remainingBudgetAfter: BigDecimal,
    val currentDailyAllowance: BigDecimal,
    val revisedDailyAllowance: BigDecimal,
    val dailyAllowanceDelta: BigDecimal,
    val guardLevelAfter: SpendingGuardLevel,
    val goalImpactSummary: String,
    val advisoryMessage: String
)
