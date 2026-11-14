package com.example.domain.budget

import com.example.data.local.DatabaseSeeder
import com.example.data.local.entity.BudgetEntity
import com.example.data.local.entity.FinancialProfileEntity
import com.example.data.local.entity.RecurringBillPaymentEntity
import com.example.data.local.entity.RecurringPaymentEntity
import com.example.data.local.entity.SavingsGoalEntity
import com.example.data.local.entity.TransactionEntity
import com.example.domain.model.SpendingGuardLevel
import com.example.domain.model.TransactionStatus
import com.example.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId

class DynamicBudgetEngineTest {

    private val engine = DynamicBudgetEngine()
    private val categories = DatabaseSeeder.defaultCategories()
    private val zone = ZoneId.systemDefault()

    private fun profile(
        income: String = "20000.00",
        fixed: String = "5000.00",
        savings: String = "3000.00"
    ) = FinancialProfileEntity(
        id = 1L,
        userId = 1L,
        monthlyIncome = BigDecimal(income),
        fixedExpenses = BigDecimal(fixed),
        protectedSavings = BigDecimal(savings)
    )

    private fun ts(date: LocalDate): Long =
        date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `initial allowance matches flexible budget divided by remaining days`() {
        // November 2026 has 30 days. Day 11 => 20 days remaining (11..30 inclusive).
        val refDate = LocalDate.of(2026, 11, 11)
        val summary = engine.calculateSummary(
            profile = profile(),
            transactions = emptyList(),
            recurringPayments = emptyList(),
            categories = categories,
            categoryBudgets = emptyList(),
            referenceDate = refDate,
            zoneId = zone
        )
        assertEquals(0, BigDecimal("12000.00").compareTo(summary.flexibleBudget))
        assertEquals(20, summary.daysRemainingInMonth)
        assertEquals(0, BigDecimal("600.00").compareTo(summary.dailyAllowance))
        assertEquals(SpendingGuardLevel.GREEN, summary.guardLevel)
    }

    @Test
    fun `recalculates revised daily allowance after prior day debit`() {
        // Day 11 had 12000 / 20 = 600. Spend 800 on Day 11 => on Day 12 (19 days left), 11200 / 19 = 589.47
        val day11 = LocalDate.of(2026, 11, 11)
        val day12 = LocalDate.of(2026, 11, 12)
        val debit = TransactionEntity(
            id = 1L,
            title = "Amazon",
            amount = BigDecimal("800.00"),
            type = TransactionType.DEBIT,
            categoryId = "shopping",
            timestamp = ts(day11)
        )
        val summary = engine.calculateSummary(
            profile = profile(),
            transactions = listOf(debit),
            recurringPayments = emptyList(),
            categories = categories,
            categoryBudgets = emptyList(),
            referenceDate = day12,
            zoneId = zone
        )
        assertEquals(0, BigDecimal("11200.00").compareTo(summary.remainingFlexibleBudget))
        assertEquals(19, summary.daysRemainingInMonth)
        assertEquals(0, BigDecimal("589.47").compareTo(summary.dailyAllowance))
    }

    @Test
    fun `zero and negative balance trigger survival mode and format actual protected savings`() {
        val refDate = LocalDate.of(2026, 11, 15)
        val zeroSpend = TransactionEntity(
            id = 1L,
            title = "Big Purchase",
            amount = BigDecimal("12000.00"),
            type = TransactionType.DEBIT,
            categoryId = "shopping",
            timestamp = ts(refDate.minusDays(1))
        )
        val zeroSummary = engine.calculateSummary(
            profile = profile(),
            transactions = listOf(zeroSpend),
            recurringPayments = emptyList(),
            categories = categories,
            categoryBudgets = emptyList(),
            referenceDate = refDate,
            zoneId = zone
        )
        assertEquals(0, BigDecimal.ZERO.compareTo(zeroSummary.remainingFlexibleBudget))
        assertEquals(0, BigDecimal.ZERO.compareTo(zeroSummary.dailyAllowance))
        assertTrue(zeroSummary.isSurvivalModeActive)
        assertTrue(zeroSummary.survivalAdvice.any { it.contains("3,000") })

        val negativeSpend = zeroSpend.copy(amount = BigDecimal("13500.00"))
        val negativeSummary = engine.calculateSummary(
            profile = profile(),
            transactions = listOf(negativeSpend),
            recurringPayments = emptyList(),
            categories = categories,
            categoryBudgets = emptyList(),
            referenceDate = refDate,
            zoneId = zone
        )
        assertTrue(negativeSummary.remainingFlexibleBudget < BigDecimal.ZERO)
        assertEquals(SpendingGuardLevel.RED, negativeSummary.guardLevel)
        assertTrue(negativeSummary.isSurvivalModeActive)
    }

    @Test
    fun `month end recalculation on last day assigns full remaining budget to today`() {
        val lastDay = LocalDate.of(2026, 11, 30)
        val priorSpend = TransactionEntity(
            id = 1L,
            title = "Groceries",
            amount = BigDecimal("10500.00"),
            type = TransactionType.DEBIT,
            categoryId = "food",
            timestamp = ts(LocalDate.of(2026, 11, 10))
        )
        val summary = engine.calculateSummary(
            profile = profile(),
            transactions = listOf(priorSpend),
            recurringPayments = emptyList(),
            categories = categories,
            categoryBudgets = emptyList(),
            referenceDate = lastDay,
            zoneId = zone
        )
        assertEquals(1, summary.daysRemainingInMonth)
        assertEquals(0, BigDecimal("1500.00").compareTo(summary.dailyAllowance))
    }

    @Test
    fun `unpaid upcoming bills are reserved before safe to spend calculation`() {
        val refDate = LocalDate.of(2026, 11, 21) // 10 days left
        val unpaidBill = RecurringPaymentEntity(
            id = 1L,
            title = "Broadband",
            amount = BigDecimal("1000.00"),
            categoryId = "bills",
            dayOfMonth = 25,
            isPaidForCurrentMonth = false,
            startYearMonth = "2026-11"
        )
        val paidBill = RecurringPaymentEntity(
            id = 2L,
            title = "Water",
            amount = BigDecimal("500.00"),
            categoryId = "bills",
            dayOfMonth = 5,
            isPaidForCurrentMonth = true,
            paidYearMonth = "2026-11",
            startYearMonth = "2026-11"
        )
        val summary = engine.calculateSummary(
            profile = profile(),
            transactions = emptyList(),
            recurringPayments = listOf(unpaidBill, paidBill),
            categories = categories,
            categoryBudgets = emptyList(),
            referenceDate = refDate,
            zoneId = zone
        )
        assertEquals(0, BigDecimal("1000.00").compareTo(summary.upcomingBillsReserved))
        assertEquals(0, BigDecimal("11000.00").compareTo(summary.flexibleBudget))
        assertEquals(0, BigDecimal("1100.00").compareTo(summary.dailyAllowance))
    }

    @Test
    fun `paying a recurring bill in full generates expense without increasing remaining budget`() {
        val refDate = LocalDate.of(2026, 11, 21)
        val bill = RecurringPaymentEntity(
            id = 1L,
            title = "Electricity",
            amount = BigDecimal("1000.00"),
            categoryId = "bills",
            dayOfMonth = 25,
            isPaidForCurrentMonth = false,
            startYearMonth = "2026-11"
        )
        val beforePay = engine.calculateSummary(
            profile = profile(),
            transactions = emptyList(),
            recurringPayments = listOf(bill),
            categories = categories,
            categoryBudgets = emptyList(),
            referenceDate = refDate,
            zoneId = zone
        )
        assertEquals(0, BigDecimal("11000.00").compareTo(beforePay.remainingFlexibleBudget))

        // Paying the bill creates a CONFIRMED DEBIT for 1000 and records a RecurringBillPaymentEntity for 2026-11
        val billPaymentTxn = TransactionEntity(
            id = 10L,
            title = "Electricity",
            amount = BigDecimal("1000.00"),
            type = TransactionType.DEBIT,
            categoryId = "bills",
            timestamp = ts(refDate),
            status = TransactionStatus.CONFIRMED
        )
        val paymentRow = RecurringBillPaymentEntity(
            id = 1L,
            recurringPaymentId = 1L,
            yearMonth = "2026-11",
            paidAmount = BigDecimal("1000.00"),
            linkedTransactionId = 10L,
            paidAtMillis = ts(refDate)
        )
        val afterPay = engine.calculateSummary(
            profile = profile(),
            transactions = listOf(billPaymentTxn),
            recurringPayments = listOf(bill.copy(isPaidForCurrentMonth = true, paidYearMonth = "2026-11")),
            categories = categories,
            categoryBudgets = emptyList(),
            referenceDate = refDate,
            zoneId = zone,
            monthBillPayments = listOf(paymentRow)
        )
        assertEquals(0, BigDecimal.ZERO.compareTo(afterPay.upcomingBillsReserved))
        assertEquals(0, BigDecimal("12000.00").compareTo(afterPay.flexibleBudget))
        assertEquals(0, BigDecimal("1000.00").compareTo(afterPay.totalExpensesThisMonth))
        // Available remaining budget remains 11,000.00 (never jumps up when bill is paid!)
        assertEquals(0, BigDecimal("11000.00").compareTo(afterPay.remainingFlexibleBudget))
    }

    @Test
    fun `partial bill payment and future startYearMonth bills isolate reserves accurately`() {
        val refDate = LocalDate.of(2026, 11, 15)
        val billWithPartial = RecurringPaymentEntity(
            id = 1L,
            title = "Rent Extra",
            amount = BigDecimal("1000.00"),
            categoryId = "bills",
            dayOfMonth = 20,
            startYearMonth = "2026-11"
        )
        val futureBill = RecurringPaymentEntity(
            id = 2L,
            title = "Future Gym",
            amount = BigDecimal("2000.00"),
            categoryId = "other",
            dayOfMonth = 5,
            startYearMonth = "2026-12"
        )
        val partialRow = RecurringBillPaymentEntity(
            id = 1L,
            recurringPaymentId = 1L,
            yearMonth = "2026-11",
            paidAmount = BigDecimal("400.00"),
            linkedTransactionId = 50L,
            paidAtMillis = ts(refDate)
        )
        val partialTxn = TransactionEntity(
            id = 50L,
            title = "Rent Extra",
            amount = BigDecimal("400.00"),
            type = TransactionType.DEBIT,
            categoryId = "bills",
            timestamp = ts(refDate),
            status = TransactionStatus.CONFIRMED
        )
        val summary = engine.calculateSummary(
            profile = profile(),
            transactions = listOf(partialTxn),
            recurringPayments = listOf(billWithPartial, futureBill),
            categories = categories,
            categoryBudgets = emptyList(),
            referenceDate = refDate,
            zoneId = zone,
            monthBillPayments = listOf(partialRow)
        )
        // Only remaining 600 of bill 1 is reserved; futureBill (2026-12) is not reserved in 2026-11
        assertEquals(0, BigDecimal("600.00").compareTo(summary.upcomingBillsReserved))
        assertEquals(0, BigDecimal("11000.00").compareTo(summary.remainingFlexibleBudget))
    }

    @Test
    fun `pending NEEDS_REVIEW transactions are excluded from spending and category totals`() {
        val refDate = LocalDate.of(2026, 11, 15)
        val pendingTxn = TransactionEntity(
            id = 1L,
            title = "Unconfirmed SMS Debit",
            amount = BigDecimal("2500.00"),
            type = TransactionType.DEBIT,
            categoryId = "food",
            timestamp = ts(refDate),
            status = TransactionStatus.NEEDS_REVIEW
        )
        val confirmedTxn = TransactionEntity(
            id = 2L,
            title = "Cafe",
            amount = BigDecimal("250.00"),
            type = TransactionType.DEBIT,
            categoryId = "food",
            timestamp = ts(refDate),
            status = TransactionStatus.CONFIRMED
        )

        val summary = engine.calculateSummary(
            profile = profile(),
            transactions = listOf(pendingTxn, confirmedTxn),
            recurringPayments = emptyList(),
            categories = categories,
            categoryBudgets = emptyList(),
            referenceDate = refDate,
            zoneId = zone
        )
        assertEquals(0, BigDecimal("250.00").compareTo(summary.totalExpensesThisMonth))
        assertEquals(0, BigDecimal("250.00").compareTo(summary.spentToday))
        assertEquals(0, BigDecimal("11750.00").compareTo(summary.remainingFlexibleBudget))
        val foodBreakdown = summary.categoryBreakdowns.first { it.categoryId == "food" }
        assertEquals(0, BigDecimal("250.00").compareTo(foodBreakdown.spentAmount))
    }

    @Test
    fun `confirmed non-salary credit expands flexible budget and month BudgetEntity takes precedence`() {
        val refDate = LocalDate.of(2026, 11, 11) // 20 days left
        val monthlyBudget = BudgetEntity(
            id = 1L,
            yearMonth = "2026-11",
            plannedIncome = BigDecimal("20000.00"),
            fixedExpenses = BigDecimal("5000.00"),
            protectedSavings = BigDecimal("3000.00"),
            flexibleBudget = BigDecimal("12000.00")
        )
        val salaryCredit = TransactionEntity(
            id = 1L,
            title = "Monthly Salary",
            amount = BigDecimal("20000.00"),
            type = TransactionType.CREDIT,
            categoryId = "other",
            timestamp = ts(refDate),
            status = TransactionStatus.CONFIRMED
        )
        val freelanceCredit = TransactionEntity(
            id = 2L,
            title = "Freelance Bonus",
            amount = BigDecimal("4000.00"),
            type = TransactionType.CREDIT,
            categoryId = "other",
            timestamp = ts(refDate),
            status = TransactionStatus.CONFIRMED
        )

        val summary = engine.calculateSummary(
            profile = profile(income = "99999.00"), // Stale profile ignored when matching monthlyBudget exists
            transactions = listOf(salaryCredit, freelanceCredit),
            recurringPayments = emptyList(),
            categories = categories,
            categoryBudgets = emptyList(),
            referenceDate = refDate,
            zoneId = zone,
            monthlyBudget = monthlyBudget
        )
        assertEquals(0, BigDecimal("20000.00").compareTo(summary.monthlyIncome))
        assertEquals(0, BigDecimal("4000.00").compareTo(summary.extraCreditsThisMonth))
        assertEquals(0, BigDecimal("16000.00").compareTo(summary.flexibleBudget))
        assertEquals(0, BigDecimal("800.00").compareTo(summary.dailyAllowance))
    }

    @Test
    fun `transfers are excluded and edited or deleted transactions recalculate accurately`() {
        val refDate = LocalDate.of(2026, 11, 15)
        val transfer = TransactionEntity(
            id = 1L,
            title = "Self Transfer",
            amount = BigDecimal("4000.00"),
            type = TransactionType.DEBIT,
            categoryId = "other",
            timestamp = ts(refDate),
            isTransfer = true
        )
        val expense = TransactionEntity(
            id = 2L,
            title = "Dinner",
            amount = BigDecimal("500.00"),
            type = TransactionType.DEBIT,
            categoryId = "food",
            timestamp = ts(refDate)
        )

        val s1 = engine.calculateSummary(
            profile(), listOf(transfer, expense), emptyList(), categories, emptyList(), refDate, zone
        )
        assertEquals(0, BigDecimal("500.00").compareTo(s1.totalExpensesThisMonth))
        assertEquals(0, BigDecimal("4000.00").compareTo(s1.totalTransfersExcluded))

        // Edited transaction
        val edited = expense.copy(amount = BigDecimal("900.00"))
        val s2 = engine.calculateSummary(
            profile(), listOf(transfer, edited), emptyList(), categories, emptyList(), refDate, zone
        )
        assertEquals(0, BigDecimal("900.00").compareTo(s2.totalExpensesThisMonth))

        // Deleted transaction
        val s3 = engine.calculateSummary(
            profile(), listOf(transfer), emptyList(), categories, emptyList(), refDate, zone
        )
        assertEquals(0, BigDecimal.ZERO.compareTo(s3.totalExpensesThisMonth))
    }

    @Test
    fun `what if simulator calculates purchase impact without altering summary`() {
        val refDate = LocalDate.of(2026, 11, 11) // 20 days remaining, 12000 flexible
        val summary = engine.calculateSummary(
            profile(), emptyList(), emptyList(), categories, emptyList(), refDate, zone
        )
        val goals = listOf(
            SavingsGoalEntity(
                id = 1L,
                name = "Laptop",
                targetAmount = BigDecimal("50000"),
                currentAmount = BigDecimal("10000"),
                targetDateEpochMillis = ts(refDate.plusMonths(5))
            )
        )

        val sim = engine.simulateWhatIfPurchase(summary, "Shoes", BigDecimal("2000.00"), goals)
        assertEquals(0, BigDecimal("10000.00").compareTo(sim.remainingBudgetAfter))
        assertEquals(0, BigDecimal("500.00").compareTo(sim.revisedDailyAllowance))
        assertFalse(sim.guardLevelAfter == SpendingGuardLevel.RED)
        // Original summary remains unchanged
        assertEquals(0, BigDecimal("12000.00").compareTo(summary.remainingFlexibleBudget))
    }
}
