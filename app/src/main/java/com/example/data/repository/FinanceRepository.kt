package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.ArthikaDatabase
import com.example.data.local.DatabaseSeeder
import com.example.data.local.entity.BudgetEntity
import com.example.data.local.entity.CategoryBudgetEntity
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.FinancialProfileEntity
import com.example.data.local.entity.NoSpendChallengeEntity
import com.example.data.local.entity.NotificationEntity
import com.example.data.local.entity.RecurringBillPaymentEntity
import com.example.data.local.entity.RecurringPaymentEntity
import com.example.data.local.entity.SavingsContributionEntity
import com.example.data.local.entity.SavingsGoalEntity
import com.example.data.local.entity.SpendingRuleEntity
import com.example.data.local.entity.TransactionEntity
import com.example.data.local.entity.UserEntity
import com.example.domain.budget.BudgetAlertEvaluator
import com.example.domain.budget.FinancialAlertManager
import com.example.domain.model.PaymentMethod
import com.example.domain.model.SpendingAlert
import com.example.domain.model.TransactionSource
import com.example.domain.model.TransactionStatus
import com.example.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

data class TransactionSaveResult(
    val transactionId: Long,
    val triggeredAlerts: List<SpendingAlert> = emptyList()
)

sealed class GoalOperationResult {
    data class Success(val goalId: Long) : GoalOperationResult()
    data class ValidationError(val message: String) : GoalOperationResult()
}

sealed class BillPaymentResult {
    data class Paid(val transactionId: Long, val triggeredAlerts: List<SpendingAlert> = emptyList()) : BillPaymentResult()
    data class Rejected(val message: String) : BillPaymentResult()
}

sealed class CategoryOperationResult {
    data class Success(val categoryId: String) : CategoryOperationResult()
    data class ValidationError(val message: String) : CategoryOperationResult()
}

class FinanceRepository(
    private val db: ArthikaDatabase,
    private val seeder: DatabaseSeeder,
    private val alertEvaluator: BudgetAlertEvaluator? = null,
    private val financialAlertManager: FinancialAlertManager? = null
) {
    val userFlow: Flow<UserEntity?> = db.userDao().observeUser()
    val profileFlow: Flow<FinancialProfileEntity?> = db.financialProfileDao().observeProfile()
    val categoriesFlow: Flow<List<CategoryEntity>> = db.categoryDao().observeCategories()
    val allTransactionsFlow: Flow<List<TransactionEntity>> = db.transactionDao().observeAllTransactions()
    val categoryBudgetsFlow: Flow<List<CategoryBudgetEntity>> =
        db.budgetDao().observeCategoryBudgetsForMonth(YearMonth.now().toString())
    val goalsFlow: Flow<List<SavingsGoalEntity>> = db.savingsGoalDao().observeAllGoals()
    val allContributionsFlow: Flow<List<SavingsContributionEntity>> = db.savingsGoalDao().observeAllContributions()
    val recurringPaymentsFlow: Flow<List<RecurringPaymentEntity>> = db.recurringPaymentDao().observeActiveRecurringPayments()
    val spendingRulesFlow: Flow<List<SpendingRuleEntity>> = db.spendingRuleDao().observeRules()
    val notificationsFlow: Flow<List<NotificationEntity>> = db.notificationDao().observeNotifications()
    val unreadNotificationCountFlow: Flow<Int> = db.notificationDao().observeUnreadCount()
    val challengesFlow: Flow<List<NoSpendChallengeEntity>> = db.noSpendChallengeDao().observeChallenges()

    private fun BigDecimal.money(): BigDecimal = setScale(2, RoundingMode.HALF_UP)

    fun observeCurrentMonthBudgetFlow(yearMonth: YearMonth = YearMonth.now()): Flow<BudgetEntity?> =
        db.budgetDao().observeBudgetForMonth(yearMonth.toString())

    fun observeCategoryBudgetsForMonth(yearMonth: YearMonth = YearMonth.now()): Flow<List<CategoryBudgetEntity>> =
        db.budgetDao().observeCategoryBudgetsForMonth(yearMonth.toString())

    fun observeBillPaymentsForMonth(yearMonth: YearMonth = YearMonth.now()): Flow<List<RecurringBillPaymentEntity>> =
        db.recurringPaymentDao().observeBillPaymentsForMonth(yearMonth.toString())

    suspend fun ensureCurrentMonthBudget(yearMonth: YearMonth = YearMonth.now()) {
        val currentYmStr = yearMonth.toString()
        val hasProfile = db.financialProfileDao().getProfileOnce() != null
        val existingBudget = db.budgetDao().getBudgetForMonthOnce(currentYmStr)
        if (hasProfile && existingBudget == null) {
            seeder.getOrCreateCurrentMonthBudget(yearMonth)
        }
        // Reset stale month-paid flags when transitioning to a new month while keeping historical
        // recurring_bill_payments intact for past months.
        val activeBills = db.recurringPaymentDao().getActiveRecurringPaymentsOnce()
        activeBills.forEach { bill ->
            if (bill.isPaidForCurrentMonth && bill.paidYearMonth != currentYmStr) {
                val monthPaidSum = db.recurringPaymentDao()
                    .getBillPaymentsForBillAndMonthOnce(bill.id, currentYmStr)
                    .fold(BigDecimal.ZERO) { acc, p -> acc + p.paidAmount }
                val paidThisMonth = monthPaidSum >= bill.amount
                db.recurringPaymentDao().setMonthlyPaidStatus(
                    id = bill.id,
                    isPaid = paidThisMonth,
                    paidYearMonth = if (paidThisMonth) currentYmStr else null
                )
            }
        }
    }

    suspend fun updateUserName(name: String) {
        val cleaned = name.trim()
        db.withTransaction {
            val current = db.userDao().getUserOnce()
            if (current == null) {
                db.userDao().upsertUser(UserEntity(name = cleaned))
            } else {
                db.userDao().upsertUser(current.copy(name = cleaned))
            }
        }
    }

    suspend fun completeUserSetup(
        name: String,
        monthlyIncome: BigDecimal,
        fixedExpenses: BigDecimal,
        protectedSavings: BigDecimal
    ) {
        seeder.seedSystemDefaultsIfNeeded()
        db.withTransaction {
            val cleanedName = name.trim()
            if (cleanedName.isNotEmpty()) {
                val current = db.userDao().getUserOnce()
                db.userDao().upsertUser(
                    current?.copy(name = cleanedName) ?: UserEntity(name = cleanedName)
                )
            }
            val hasBudgetInput = monthlyIncome > BigDecimal.ZERO ||
                fixedExpenses > BigDecimal.ZERO ||
                protectedSavings > BigDecimal.ZERO
            if (hasBudgetInput) {
                upsertProfileAndMonthBudgetLocked(monthlyIncome, fixedExpenses, protectedSavings)
            }
        }
        financialAlertManager?.scheduleImmediateCheck()
    }

    suspend fun updateFinancialProfile(
        monthlyIncome: BigDecimal,
        fixedExpenses: BigDecimal,
        protectedSavings: BigDecimal
    ) {
        db.withTransaction {
            upsertProfileAndMonthBudgetLocked(monthlyIncome, fixedExpenses, protectedSavings)
        }
        financialAlertManager?.scheduleImmediateCheck()
    }

    private suspend fun upsertProfileAndMonthBudgetLocked(
        monthlyIncome: BigDecimal,
        fixedExpenses: BigDecimal,
        protectedSavings: BigDecimal
    ) {
        if (db.userDao().getUserOnce() == null) {
            db.userDao().upsertUser(UserEntity(name = ""))
        }

        val safeIncome = monthlyIncome.coerceAtLeast(BigDecimal.ZERO).money()
        val safeFixed = fixedExpenses.coerceAtLeast(BigDecimal.ZERO).money()
        val safeSavings = protectedSavings.coerceAtLeast(BigDecimal.ZERO).money()

        val current = db.financialProfileDao().getProfileOnce()
        val updated = (current ?: FinancialProfileEntity(
            monthlyIncome = safeIncome,
            fixedExpenses = safeFixed,
            protectedSavings = safeSavings
        )).copy(
            monthlyIncome = safeIncome,
            fixedExpenses = safeFixed,
            protectedSavings = safeSavings,
            updatedAt = System.currentTimeMillis()
        )
        db.financialProfileDao().upsertProfile(updated)

        val currentYm = YearMonth.now().toString()
        val existingBudget = db.budgetDao().getBudgetForMonthOnce(currentYm)
        val flexible = (safeIncome - safeFixed - safeSavings).coerceAtLeast(BigDecimal.ZERO).money()
        if (existingBudget == null) {
            db.budgetDao().insertBudget(
                BudgetEntity(
                    yearMonth = currentYm,
                    plannedIncome = safeIncome,
                    fixedExpenses = safeFixed,
                    protectedSavings = safeSavings,
                    flexibleBudget = flexible
                )
            )
        } else {
            db.budgetDao().updateBudget(
                existingBudget.copy(
                    plannedIncome = safeIncome,
                    fixedExpenses = safeFixed,
                    protectedSavings = safeSavings,
                    flexibleBudget = flexible
                )
            )
        }
    }

    suspend fun updateCategoryBudget(
        categoryId: String,
        limitAmount: BigDecimal,
        yearMonth: YearMonth = YearMonth.now()
    ) {
        seeder.seedSystemDefaultsIfNeeded()
        val budget = seeder.getOrCreateCurrentMonthBudget(yearMonth)
        db.withTransaction {
            val safeLimit = limitAmount.coerceAtLeast(BigDecimal.ZERO).money()
            if (safeLimit <= BigDecimal.ZERO) {
                db.budgetDao().deleteCategoryBudget(budget.id, categoryId)
            } else {
                val existing = db.budgetDao().getCategoryBudget(budget.id, categoryId)
                db.budgetDao().upsertCategoryBudget(
                    CategoryBudgetEntity(
                        id = existing?.id ?: 0L,
                        budgetId = budget.id,
                        categoryId = categoryId,
                        limitAmount = safeLimit
                    )
                )
            }
        }
        financialAlertManager?.scheduleImmediateCheck()
    }

    suspend fun createCategory(
        name: String,
        iconKey: String = "category",
        colorHex: String = "#3B82F6"
    ): CategoryOperationResult {
        val cleaned = name.trim()
        if (cleaned.isBlank()) {
            return CategoryOperationResult.ValidationError("Category name cannot be empty.")
        }
        if (cleaned.length > 24) {
            return CategoryOperationResult.ValidationError("Category name must be 24 characters or fewer.")
        }
        return db.withTransaction {
            val existing = db.categoryDao().getCategoriesOnce()
            if (existing.any { it.name.equals(cleaned, ignoreCase = true) }) {
                return@withTransaction CategoryOperationResult.ValidationError("A category named '$cleaned' already exists.")
            }
            val slugBase = cleaned.lowercase()
                .replace(Regex("[^a-z0-9]+"), "_")
                .trim('_')
                .ifBlank { "custom" }
            val uniqueId = if (existing.none { it.id == slugBase }) {
                slugBase
            } else {
                "${slugBase}_${System.currentTimeMillis() % 10000}"
            }
            val nextSort = (existing.maxOfOrNull { it.sortOrder } ?: 0) + 1
            db.categoryDao().insert(
                CategoryEntity(
                    id = uniqueId,
                    name = cleaned,
                    iconKey = iconKey,
                    colorHex = colorHex,
                    isExpenseCategory = true,
                    sortOrder = nextSort
                )
            )
            CategoryOperationResult.Success(uniqueId)
        }
    }

    suspend fun renameCategory(categoryId: String, newName: String): CategoryOperationResult {
        val cleaned = newName.trim()
        if (cleaned.isBlank()) {
            return CategoryOperationResult.ValidationError("Category name cannot be empty.")
        }
        return db.withTransaction {
            val existing = db.categoryDao().getById(categoryId)
                ?: return@withTransaction CategoryOperationResult.ValidationError("Category not found.")
            val all = db.categoryDao().getCategoriesOnce()
            if (all.any { it.id != categoryId && it.name.equals(cleaned, ignoreCase = true) }) {
                return@withTransaction CategoryOperationResult.ValidationError("Another category already uses '$cleaned'.")
            }
            db.categoryDao().update(existing.copy(name = cleaned))
            CategoryOperationResult.Success(categoryId)
        }
    }

    suspend fun deleteCategory(categoryId: String): CategoryOperationResult {
        if (categoryId == "other") {
            return CategoryOperationResult.ValidationError("The default 'Other' category cannot be deleted.")
        }
        seeder.seedSystemDefaultsIfNeeded()
        return db.withTransaction {
            if (db.categoryDao().getById(categoryId) == null) {
                return@withTransaction CategoryOperationResult.ValidationError("Category not found.")
            }
            db.transactionDao().reassignCategory(categoryId, "other")
            db.recurringPaymentDao().reassignCategory(categoryId, "other")
            db.budgetDao().deleteCategoryBudgetsByCategory(categoryId)
            db.categoryDao().deleteById(categoryId)
            CategoryOperationResult.Success(categoryId)
        }
    }

    suspend fun addManualTransaction(
        title: String,
        amount: BigDecimal,
        type: TransactionType,
        categoryId: String,
        timestamp: Long,
        paymentMethod: PaymentMethod,
        notes: String?,
        isTransfer: Boolean = false
    ): Long {
        return saveManualTransactionWithAlerts(
            title = title,
            amount = amount,
            type = type,
            categoryId = categoryId,
            timestamp = timestamp,
            paymentMethod = paymentMethod,
            notes = notes,
            isTransfer = isTransfer
        ).transactionId
    }

    suspend fun saveManualTransactionWithAlerts(
        title: String,
        amount: BigDecimal,
        type: TransactionType,
        categoryId: String,
        timestamp: Long,
        paymentMethod: PaymentMethod,
        notes: String?,
        isTransfer: Boolean = false
    ): TransactionSaveResult {
        val safeAmount = amount.money()
        require(safeAmount > BigDecimal.ZERO) { "Transaction amount must be positive." }
        val cleanTitle = title.trim()
        require(cleanTitle.isNotEmpty()) { "Transaction title cannot be empty." }

        val saved = db.withTransaction {
            val entity = TransactionEntity(
                title = cleanTitle,
                merchantName = cleanTitle,
                amount = safeAmount,
                type = type,
                categoryId = categoryId,
                timestamp = timestamp,
                paymentMethod = paymentMethod,
                source = TransactionSource.MANUAL,
                status = TransactionStatus.CONFIRMED,
                isTransfer = isTransfer,
                notes = notes?.trim()?.takeIf { it.isNotBlank() }
            )
            val rowId = db.transactionDao().insertTransaction(entity)
            entity.copy(id = rowId)
        }
        val alerts = alertEvaluator?.evaluateAfterTransaction(saved).orEmpty()
        return TransactionSaveResult(transactionId = saved.id, triggeredAlerts = alerts)
    }

    suspend fun updateTransaction(transaction: TransactionEntity): List<SpendingAlert> {
        val normalized = transaction.copy(
            title = transaction.title.trim(),
            merchantName = transaction.merchantName?.trim()?.takeIf { it.isNotBlank() } ?: transaction.title.trim(),
            amount = transaction.amount.money(),
            notes = transaction.notes?.trim()?.takeIf { it.isNotBlank() }
        )
        require(normalized.amount > BigDecimal.ZERO) { "Transaction amount must be positive." }
        require(normalized.title.isNotBlank()) { "Transaction title cannot be empty." }

        db.withTransaction {
            db.transactionDao().updateTransaction(normalized)
        }
        return if (normalized.status == TransactionStatus.CONFIRMED) {
            alertEvaluator?.evaluateAfterTransaction(normalized).orEmpty()
        } else {
            emptyList()
        }
    }

    suspend fun confirmReviewedTransaction(
        transaction: TransactionEntity,
        updatedMerchant: String,
        updatedCategoryId: String,
        markAsTransfer: Boolean
    ): List<SpendingAlert> {
        val cleanedMerchant = updatedMerchant.trim().ifBlank { transaction.title }
        val updated = transaction.copy(
            title = cleanedMerchant,
            merchantName = cleanedMerchant,
            categoryId = updatedCategoryId,
            isTransfer = markAsTransfer,
            status = TransactionStatus.CONFIRMED
        )
        db.withTransaction {
            db.transactionDao().updateTransaction(updated)
        }
        return alertEvaluator?.evaluateAfterTransaction(updated).orEmpty()
    }

    suspend fun deleteTransaction(id: Long) {
        db.withTransaction {
            val linkedBillPayment = db.recurringPaymentDao().getBillPaymentByTransactionId(id)
            db.transactionDao().deleteById(id)
            if (linkedBillPayment != null) {
                val bill = db.recurringPaymentDao().getById(linkedBillPayment.recurringPaymentId)
                if (bill != null) {
                    val remainingPaid = db.recurringPaymentDao()
                        .getBillPaymentsForBillAndMonthOnce(bill.id, linkedBillPayment.yearMonth)
                        .fold(BigDecimal.ZERO) { acc, p -> acc + p.paidAmount }
                    val stillFullyPaid = remainingPaid >= bill.amount
                    val currentYmStr = YearMonth.now().toString()
                    if (linkedBillPayment.yearMonth == currentYmStr) {
                        db.recurringPaymentDao().setMonthlyPaidStatus(
                            id = bill.id,
                            isPaid = stillFullyPaid,
                            paidYearMonth = if (stillFullyPaid) currentYmStr else null
                        )
                    }
                }
            }
        }
    }

    suspend fun deleteAllSmsTransactions() {
        db.withTransaction {
            db.transactionDao().deleteBySource(TransactionSource.SMS)
            db.smsAuditDao().clearProcessedSms()
        }
    }

    suspend fun upsertSavingsGoalWithValidation(goal: SavingsGoalEntity): GoalOperationResult {
        val cleanedName = goal.name.trim()
        if (cleanedName.isBlank()) {
            return GoalOperationResult.ValidationError("Goal name cannot be empty.")
        }
        val safeTarget = goal.targetAmount.money()
        if (safeTarget <= BigDecimal.ZERO) {
            return GoalOperationResult.ValidationError("Target amount must be greater than zero.")
        }
        val safeInitial = goal.currentAmount.money()
        if (safeInitial < BigDecimal.ZERO) {
            return GoalOperationResult.ValidationError("Saved amount cannot be negative.")
        }
        if (safeInitial > safeTarget) {
            return GoalOperationResult.ValidationError("Initial saved amount cannot exceed the target amount.")
        }

        return db.withTransaction {
            if (goal.id == 0L) {
                val goalId = db.savingsGoalDao().upsertGoal(
                    goal.copy(
                        name = cleanedName,
                        targetAmount = safeTarget,
                        currentAmount = safeInitial
                    )
                )
                if (safeInitial > BigDecimal.ZERO) {
                    db.savingsGoalDao().insertContribution(
                        SavingsContributionEntity(
                            goalId = goalId,
                            amount = safeInitial,
                            note = "Initial savings"
                        )
                    )
                }
                GoalOperationResult.Success(goalId)
            } else {
                val existing = db.savingsGoalDao().getGoalById(goal.id)
                    ?: return@withTransaction GoalOperationResult.ValidationError("Savings goal not found.")
                if (safeTarget < existing.currentAmount) {
                    return@withTransaction GoalOperationResult.ValidationError(
                        "Target cannot be lower than your already saved balance."
                    )
                }
                db.savingsGoalDao().upsertGoal(
                    existing.copy(
                        name = cleanedName,
                        targetAmount = safeTarget,
                        targetDateEpochMillis = goal.targetDateEpochMillis,
                        iconKey = goal.iconKey
                    )
                )
                GoalOperationResult.Success(existing.id)
            }
        }
    }

    suspend fun upsertSavingsGoal(goal: SavingsGoalEntity): Long {
        return when (val result = upsertSavingsGoalWithValidation(goal)) {
            is GoalOperationResult.Success -> result.goalId
            is GoalOperationResult.ValidationError -> 0L
        }
    }

    suspend fun deleteSavingsGoal(goalId: Long) {
        db.withTransaction {
            db.savingsGoalDao().deleteGoal(goalId)
        }
    }

    suspend fun contributeToGoalWithValidation(
        goalId: Long,
        contributionAmount: BigDecimal,
        note: String? = null
    ): GoalOperationResult {
        val safeAmount = contributionAmount.money()
        if (safeAmount <= BigDecimal.ZERO) {
            return GoalOperationResult.ValidationError("Contribution must be greater than zero.")
        }

        return db.withTransaction {
            val latestGoal = db.savingsGoalDao().getGoalById(goalId)
                ?: return@withTransaction GoalOperationResult.ValidationError("Savings goal no longer exists.")

            val remaining = (latestGoal.targetAmount - latestGoal.currentAmount).coerceAtLeast(BigDecimal.ZERO).money()
            if (remaining <= BigDecimal.ZERO) {
                return@withTransaction GoalOperationResult.ValidationError("This savings goal is already 100% funded.")
            }
            if (safeAmount > remaining) {
                return@withTransaction GoalOperationResult.ValidationError(
                    "Contribution exceeds remaining goal target (₹${remaining.stripTrailingZeros().toPlainString()} left)."
                )
            }

            // Reconcile any legacy goal that had currentAmount without contribution rows before appending
            val existingContributions = db.savingsGoalDao().getContributionsForGoalOnce(goalId)
            val existingSum = existingContributions.fold(BigDecimal.ZERO) { acc, c -> acc + c.amount }.money()
            if (existingSum < latestGoal.currentAmount) {
                db.savingsGoalDao().insertContribution(
                    SavingsContributionEntity(
                        goalId = goalId,
                        amount = (latestGoal.currentAmount - existingSum).money(),
                        note = "Prior saved balance"
                    )
                )
            }

            db.savingsGoalDao().insertContribution(
                SavingsContributionEntity(
                    goalId = goalId,
                    amount = safeAmount,
                    note = note?.trim()?.takeIf { it.isNotBlank() }
                )
            )
            val reconciledTotal = db.savingsGoalDao().getContributionsForGoalOnce(goalId)
                .fold(BigDecimal.ZERO) { acc, c -> acc + c.amount }
                .money()
            db.savingsGoalDao().upsertGoal(latestGoal.copy(currentAmount = reconciledTotal))
            GoalOperationResult.Success(goalId)
        }
    }

    suspend fun contributeToGoal(goal: SavingsGoalEntity, contributionAmount: BigDecimal, note: String? = null) {
        contributeToGoalWithValidation(goal.id, contributionAmount, note)
    }

    suspend fun updateGoalContribution(
        contributionId: Long,
        newAmount: BigDecimal,
        newNote: String?
    ): GoalOperationResult {
        val safeAmount = newAmount.money()
        if (safeAmount <= BigDecimal.ZERO) {
            return GoalOperationResult.ValidationError("Contribution amount must be greater than zero.")
        }
        return db.withTransaction {
            val existing = db.savingsGoalDao().getContributionById(contributionId)
                ?: return@withTransaction GoalOperationResult.ValidationError("Contribution record not found.")
            val goal = db.savingsGoalDao().getGoalById(existing.goalId)
                ?: return@withTransaction GoalOperationResult.ValidationError("Parent savings goal not found.")

            val otherContributionsSum = db.savingsGoalDao().getContributionsForGoalOnce(goal.id)
                .filter { it.id != contributionId }
                .fold(BigDecimal.ZERO) { acc, c -> acc + c.amount }
                .money()
            val projectedTotal = (otherContributionsSum + safeAmount).money()
            if (projectedTotal > goal.targetAmount) {
                val maxAllowed = (goal.targetAmount - otherContributionsSum).coerceAtLeast(BigDecimal.ZERO).money()
                return@withTransaction GoalOperationResult.ValidationError(
                    "Edited contribution exceeds goal target. Max allowed is ₹${maxAllowed.stripTrailingZeros().toPlainString()}."
                )
            }

            db.savingsGoalDao().updateContribution(
                existing.copy(
                    amount = safeAmount,
                    note = newNote?.trim()?.takeIf { it.isNotBlank() }
                )
            )
            db.savingsGoalDao().upsertGoal(goal.copy(currentAmount = projectedTotal))
            GoalOperationResult.Success(goal.id)
        }
    }

    suspend fun deleteGoalContribution(contributionId: Long): GoalOperationResult {
        return db.withTransaction {
            val existing = db.savingsGoalDao().getContributionById(contributionId)
                ?: return@withTransaction GoalOperationResult.ValidationError("Contribution record not found.")
            val goal = db.savingsGoalDao().getGoalById(existing.goalId)
                ?: return@withTransaction GoalOperationResult.ValidationError("Parent savings goal not found.")

            db.savingsGoalDao().deleteContributionById(contributionId)
            val newTotal = db.savingsGoalDao().getContributionsForGoalOnce(goal.id)
                .fold(BigDecimal.ZERO) { acc, c -> acc + c.amount }
                .money()
            db.savingsGoalDao().upsertGoal(goal.copy(currentAmount = newTotal))
            GoalOperationResult.Success(goal.id)
        }
    }

    suspend fun addRecurringPayment(
        title: String,
        amount: BigDecimal,
        categoryId: String,
        dayOfMonth: Int,
        startYearMonth: YearMonth = YearMonth.now()
    ) {
        val safeAmount = amount.money()
        require(safeAmount > BigDecimal.ZERO) { "Recurring bill amount must be positive." }
        val cleanTitle = title.trim()
        require(cleanTitle.isNotEmpty()) { "Recurring bill title cannot be empty." }

        db.withTransaction {
            db.recurringPaymentDao().upsert(
                RecurringPaymentEntity(
                    title = cleanTitle,
                    amount = safeAmount,
                    categoryId = categoryId,
                    dayOfMonth = dayOfMonth.coerceIn(1, 31),
                    isPaidForCurrentMonth = false,
                    isActive = true,
                    paidYearMonth = null,
                    startYearMonth = startYearMonth.toString()
                )
            )
        }
    }

    suspend fun recordRecurringBillPayment(
        billId: Long,
        paymentAmount: BigDecimal? = null,
        yearMonth: YearMonth = YearMonth.now(),
        referenceDate: LocalDate = LocalDate.now()
    ): BillPaymentResult {
        val ymStr = yearMonth.toString()
        val outcome = db.withTransaction {
            val bill = db.recurringPaymentDao().getById(billId)
                ?: return@withTransaction BillPaymentResult.Rejected("Recurring bill not found.")
            val existingPayments = db.recurringPaymentDao().getBillPaymentsForBillAndMonthOnce(billId, ymStr)
            val alreadyPaid = existingPayments.fold(BigDecimal.ZERO) { acc, p -> acc + p.paidAmount }.money()
            val remainingDue = (bill.amount - alreadyPaid).coerceAtLeast(BigDecimal.ZERO).money()

            if (remainingDue <= BigDecimal.ZERO) {
                return@withTransaction BillPaymentResult.Rejected("${bill.title} is already paid for $ymStr.")
            }

            val payAmount = (paymentAmount ?: remainingDue).money()
            if (payAmount <= BigDecimal.ZERO) {
                return@withTransaction BillPaymentResult.Rejected("Payment amount must be greater than zero.")
            }
            if (payAmount > remainingDue) {
                return@withTransaction BillPaymentResult.Rejected(
                    "Payment exceeds remaining due for ${bill.title} (₹${remainingDue.stripTrailingZeros().toPlainString()})."
                )
            }

            val txnTimestamp = if (YearMonth.from(referenceDate) == yearMonth) {
                System.currentTimeMillis()
            } else {
                yearMonth.atDay(bill.dayOfMonth.coerceIn(1, yearMonth.lengthOfMonth()))
                    .atTime(LocalTime.NOON)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            }

            val txnEntity = TransactionEntity(
                title = bill.title,
                merchantName = bill.title,
                amount = payAmount,
                type = TransactionType.DEBIT,
                categoryId = bill.categoryId,
                timestamp = txnTimestamp,
                paymentMethod = PaymentMethod.BANK_TRANSFER,
                source = TransactionSource.MANUAL,
                status = TransactionStatus.CONFIRMED,
                isTransfer = false,
                notes = if (payAmount < bill.amount) {
                    "Partial recurring bill payment ($ymStr)"
                } else {
                    "Recurring bill payment ($ymStr)"
                }
            )
            val txnId = db.transactionDao().insertTransaction(txnEntity)

            db.recurringPaymentDao().insertBillPayment(
                RecurringBillPaymentEntity(
                    recurringPaymentId = billId,
                    yearMonth = ymStr,
                    paidAmount = payAmount,
                    linkedTransactionId = txnId,
                    paidAtMillis = txnTimestamp
                )
            )

            val newTotalPaid = (alreadyPaid + payAmount).money()
            val fullyPaid = newTotalPaid >= bill.amount
            if (yearMonth == YearMonth.now()) {
                db.recurringPaymentDao().setMonthlyPaidStatus(
                    id = billId,
                    isPaid = fullyPaid,
                    paidYearMonth = if (fullyPaid) ymStr else null
                )
            }

            BillPaymentResult.Paid(transactionId = txnId, triggeredAlerts = emptyList())
        }

        return if (outcome is BillPaymentResult.Paid) {
            val savedTxn = db.transactionDao().getById(outcome.transactionId)
            val alerts = if (savedTxn != null) {
                alertEvaluator?.evaluateAfterTransaction(savedTxn).orEmpty()
            } else emptyList()
            BillPaymentResult.Paid(outcome.transactionId, alerts)
        } else {
            outcome
        }
    }

    suspend fun unmarkRecurringBillPaid(billId: Long, yearMonth: YearMonth = YearMonth.now()) {
        val ymStr = yearMonth.toString()
        db.withTransaction {
            val payments = db.recurringPaymentDao().getBillPaymentsForBillAndMonthOnce(billId, ymStr)
            db.recurringPaymentDao().deleteBillPaymentsForBillAndMonth(billId, ymStr)
            payments.forEach { payment ->
                db.transactionDao().deleteById(payment.linkedTransactionId)
            }
            if (yearMonth == YearMonth.now()) {
                db.recurringPaymentDao().setMonthlyPaidStatus(billId, isPaid = false, paidYearMonth = null)
            }
        }
    }

    suspend fun toggleRecurringPaymentPaid(id: Long, isPaid: Boolean, yearMonth: YearMonth = YearMonth.now()) {
        if (isPaid) {
            recordRecurringBillPayment(billId = id, paymentAmount = null, yearMonth = yearMonth)
        } else {
            unmarkRecurringBillPaid(billId = id, yearMonth = yearMonth)
        }
    }

    suspend fun deleteRecurringPayment(id: Long) {
        db.withTransaction {
            db.recurringPaymentDao().deleteById(id)
        }
    }

    suspend fun addNoSpendChallenge(title: String, targetDays: Int) {
        val clean = title.trim()
        if (clean.isEmpty()) return
        db.noSpendChallengeDao().upsertChallenge(
            NoSpendChallengeEntity(
                title = clean,
                targetDays = targetDays.coerceIn(1, 30),
                completedDays = 0,
                isActive = true
            )
        )
    }

    suspend fun checkInNoSpendChallenge(challenge: NoSpendChallengeEntity) {
        val todayIso = LocalDate.now().toString()
        if (challenge.lastCheckedDateIso == todayIso) return
        val nextDays = (challenge.completedDays + 1).coerceAtMost(challenge.targetDays)
        db.noSpendChallengeDao().upsertChallenge(
            challenge.copy(
                completedDays = nextDays,
                isActive = nextDays < challenge.targetDays,
                lastCheckedDateIso = todayIso
            )
        )
    }

    suspend fun toggleSpendingRule(rule: SpendingRuleEntity) {
        val updated = rule.copy(isEnabled = !rule.isEnabled)
        db.spendingRuleDao().updateRule(updated)
        if (updated.isEnabled) {
            financialAlertManager?.scheduleImmediateCheck()
        }
    }

    suspend fun markAllNotificationsRead() {
        db.notificationDao().markAllRead()
    }

    suspend fun clearAllUserData() {
        db.withTransaction {
            db.recurringPaymentDao().deleteAllBillPayments()
            db.transactionDao().deleteAll()
            db.smsAuditDao().clearProcessedSms()
            db.notificationDao().clearAll()
            db.savingsGoalDao().deleteAllContributions()
            db.savingsGoalDao().deleteAllGoals()
            db.recurringPaymentDao().deleteAll()
            db.noSpendChallengeDao().deleteAll()
            db.budgetDao().deleteAllCategoryBudgets()
            db.budgetDao().deleteAllBudgets()
            db.financialProfileDao().deleteAll()
            db.userDao().deleteAll()
        }
    }
}
