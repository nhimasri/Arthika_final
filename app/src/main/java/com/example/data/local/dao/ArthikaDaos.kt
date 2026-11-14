package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.example.data.local.entity.BudgetEntity
import com.example.data.local.entity.CategoryBudgetEntity
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.FinancialProfileEntity
import com.example.data.local.entity.NoSpendChallengeEntity
import com.example.data.local.entity.NotificationEntity
import com.example.data.local.entity.ProcessedSmsEntity
import com.example.data.local.entity.RecurringBillPaymentEntity
import com.example.data.local.entity.RecurringPaymentEntity
import com.example.data.local.entity.SavingsContributionEntity
import com.example.data.local.entity.SavingsGoalEntity
import com.example.data.local.entity.SmsSenderEntity
import com.example.data.local.entity.SpendingRuleEntity
import com.example.data.local.entity.TransactionEntity
import com.example.data.local.entity.UserEntity
import com.example.domain.model.TransactionSource
import com.example.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE id = 1 LIMIT 1")
    fun observeUser(): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE id = 1 LIMIT 1")
    suspend fun getUserOnce(): UserEntity?

    @Upsert
    suspend fun upsertUser(user: UserEntity)

    @Query("DELETE FROM users")
    suspend fun deleteAll()
}

@Dao
interface FinancialProfileDao {
    @Query("SELECT * FROM financial_profiles WHERE userId = 1 LIMIT 1")
    fun observeProfile(): Flow<FinancialProfileEntity?>

    @Query("SELECT * FROM financial_profiles WHERE userId = 1 LIMIT 1")
    suspend fun getProfileOnce(): FinancialProfileEntity?

    @Upsert
    suspend fun upsertProfile(profile: FinancialProfileEntity): Long

    @Query("DELETE FROM financial_profiles")
    suspend fun deleteAll()
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder ASC")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC")
    suspend fun getCategoriesOnce(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(category: CategoryEntity)

    @Update
    suspend fun update(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC, id DESC")
    fun observeAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE timestamp BETWEEN :startMillis AND :endMillis ORDER BY timestamp DESC, id DESC")
    fun observeTransactionsBetween(startMillis: Long, endMillis: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE timestamp BETWEEN :startMillis AND :endMillis ORDER BY timestamp DESC, id DESC")
    suspend fun getTransactionsBetweenOnce(startMillis: Long, endMillis: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE fingerprint = :fingerprint LIMIT 1")
    suspend fun findByFingerprint(fingerprint: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE referenceId = :referenceId AND amount = :amount AND type = :type LIMIT 1")
    suspend fun findByReference(referenceId: String, amount: BigDecimal, type: TransactionType): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE amount = :amount AND type = :type AND timestamp BETWEEN :minTs AND :maxTs")
    suspend fun findPotentialDuplicatesInWindow(
        amount: BigDecimal,
        type: TransactionType,
        minTs: Long,
        maxTs: Long
    ): List<TransactionEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    @Delete
    suspend fun deleteTransaction(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM transactions WHERE source = :source")
    suspend fun deleteBySource(source: TransactionSource)

    @Query("UPDATE transactions SET categoryId = :newCategoryId WHERE categoryId = :oldCategoryId")
    suspend fun reassignCategory(oldCategoryId: String, newCategoryId: String)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets WHERE yearMonth = :yearMonth LIMIT 1")
    fun observeBudgetForMonth(yearMonth: String): Flow<BudgetEntity?>

    @Query("SELECT * FROM budgets WHERE yearMonth = :yearMonth LIMIT 1")
    suspend fun getBudgetForMonthOnce(yearMonth: String): BudgetEntity?

    @Query("SELECT * FROM budgets")
    suspend fun getAllBudgetsOnce(): List<BudgetEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBudget(budget: BudgetEntity): Long

    @Update
    suspend fun updateBudget(budget: BudgetEntity)

    @Query("SELECT * FROM category_budgets WHERE budgetId = :budgetId")
    fun observeCategoryBudgets(budgetId: Long): Flow<List<CategoryBudgetEntity>>

    @Query(
        """
        SELECT cb.* FROM category_budgets cb
        INNER JOIN budgets b ON cb.budgetId = b.id
        WHERE b.yearMonth = :yearMonth
        """
    )
    fun observeCategoryBudgetsForMonth(yearMonth: String): Flow<List<CategoryBudgetEntity>>

    @Query(
        """
        SELECT cb.* FROM category_budgets cb
        INNER JOIN budgets b ON cb.budgetId = b.id
        WHERE b.yearMonth = :yearMonth
        """
    )
    suspend fun getCategoryBudgetsForMonthOnce(yearMonth: String): List<CategoryBudgetEntity>

    @Query("SELECT * FROM category_budgets WHERE budgetId = :budgetId AND categoryId = :categoryId LIMIT 1")
    suspend fun getCategoryBudget(budgetId: Long, categoryId: String): CategoryBudgetEntity?

    @Query("SELECT * FROM category_budgets")
    fun observeAllCategoryBudgets(): Flow<List<CategoryBudgetEntity>>

    @Upsert
    suspend fun upsertCategoryBudget(categoryBudget: CategoryBudgetEntity)

    @Upsert
    suspend fun insertCategoryBudgets(categoryBudgets: List<CategoryBudgetEntity>)

    @Query("DELETE FROM category_budgets WHERE budgetId = :budgetId AND categoryId = :categoryId")
    suspend fun deleteCategoryBudget(budgetId: Long, categoryId: String)

    @Query("DELETE FROM category_budgets WHERE categoryId = :categoryId")
    suspend fun deleteCategoryBudgetsByCategory(categoryId: String)

    @Query("DELETE FROM category_budgets")
    suspend fun deleteAllCategoryBudgets()

    @Query("DELETE FROM budgets")
    suspend fun deleteAllBudgets()
}

@Dao
interface SavingsGoalDao {
    @Query("SELECT * FROM savings_goals ORDER BY targetDateEpochMillis ASC")
    fun observeAllGoals(): Flow<List<SavingsGoalEntity>>

    @Query("SELECT * FROM savings_goals ORDER BY targetDateEpochMillis ASC")
    suspend fun getAllGoalsOnce(): List<SavingsGoalEntity>

    @Query("SELECT * FROM savings_goals WHERE id = :goalId LIMIT 1")
    suspend fun getGoalById(goalId: Long): SavingsGoalEntity?

    @Upsert
    suspend fun upsertGoal(goal: SavingsGoalEntity): Long

    @Query("DELETE FROM savings_goals WHERE id = :goalId")
    suspend fun deleteGoal(goalId: Long)

    @Query("DELETE FROM savings_goals")
    suspend fun deleteAllGoals()

    @Query("DELETE FROM savings_contributions")
    suspend fun deleteAllContributions()

    @Query("SELECT * FROM savings_contributions ORDER BY timestamp DESC, id DESC")
    fun observeAllContributions(): Flow<List<SavingsContributionEntity>>

    @Query("SELECT * FROM savings_contributions WHERE goalId = :goalId ORDER BY timestamp DESC, id DESC")
    fun observeContributions(goalId: Long): Flow<List<SavingsContributionEntity>>

    @Query("SELECT * FROM savings_contributions WHERE goalId = :goalId ORDER BY timestamp DESC, id DESC")
    suspend fun getContributionsForGoalOnce(goalId: Long): List<SavingsContributionEntity>

    @Query("SELECT * FROM savings_contributions WHERE id = :id LIMIT 1")
    suspend fun getContributionById(id: Long): SavingsContributionEntity?

    @Insert
    suspend fun insertContribution(contribution: SavingsContributionEntity): Long

    @Update
    suspend fun updateContribution(contribution: SavingsContributionEntity)

    @Query("DELETE FROM savings_contributions WHERE id = :id")
    suspend fun deleteContributionById(id: Long)
}

@Dao
interface RecurringPaymentDao {
    @Query("SELECT * FROM recurring_payments WHERE isActive = 1 ORDER BY dayOfMonth ASC")
    fun observeActiveRecurringPayments(): Flow<List<RecurringPaymentEntity>>

    @Query("SELECT * FROM recurring_payments WHERE isActive = 1 ORDER BY dayOfMonth ASC")
    suspend fun getActiveRecurringPaymentsOnce(): List<RecurringPaymentEntity>

    @Query("SELECT * FROM recurring_payments WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): RecurringPaymentEntity?

    @Upsert
    suspend fun upsert(payment: RecurringPaymentEntity): Long

    @Query("UPDATE recurring_payments SET isPaidForCurrentMonth = :isPaid WHERE id = :id")
    suspend fun setPaidStatus(id: Long, isPaid: Boolean)

    @Query("UPDATE recurring_payments SET isPaidForCurrentMonth = :isPaid, paidYearMonth = :paidYearMonth WHERE id = :id")
    suspend fun setMonthlyPaidStatus(id: Long, isPaid: Boolean, paidYearMonth: String?)

    @Query("UPDATE recurring_payments SET categoryId = :newCategoryId WHERE categoryId = :oldCategoryId")
    suspend fun reassignCategory(oldCategoryId: String, newCategoryId: String)

    @Query("DELETE FROM recurring_payments WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM recurring_payments")
    suspend fun deleteAll()

    @Query("SELECT * FROM recurring_bill_payments WHERE yearMonth = :yearMonth ORDER BY paidAtMillis DESC")
    fun observeBillPaymentsForMonth(yearMonth: String): Flow<List<RecurringBillPaymentEntity>>

    @Query("SELECT * FROM recurring_bill_payments WHERE yearMonth = :yearMonth ORDER BY paidAtMillis DESC")
    suspend fun getBillPaymentsForMonthOnce(yearMonth: String): List<RecurringBillPaymentEntity>

    @Query("SELECT * FROM recurring_bill_payments WHERE recurringPaymentId = :recurringPaymentId AND yearMonth = :yearMonth")
    suspend fun getBillPaymentsForBillAndMonthOnce(recurringPaymentId: Long, yearMonth: String): List<RecurringBillPaymentEntity>

    @Query("SELECT * FROM recurring_bill_payments WHERE linkedTransactionId = :transactionId LIMIT 1")
    suspend fun getBillPaymentByTransactionId(transactionId: Long): RecurringBillPaymentEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBillPayment(payment: RecurringBillPaymentEntity): Long

    @Query("DELETE FROM recurring_bill_payments WHERE recurringPaymentId = :recurringPaymentId AND yearMonth = :yearMonth")
    suspend fun deleteBillPaymentsForBillAndMonth(recurringPaymentId: Long, yearMonth: String)

    @Query("DELETE FROM recurring_bill_payments")
    suspend fun deleteAllBillPayments()
}

@Dao
interface SpendingRuleDao {
    @Query("SELECT * FROM spending_rules ORDER BY id ASC")
    fun observeRules(): Flow<List<SpendingRuleEntity>>

    @Query("SELECT * FROM spending_rules ORDER BY id ASC")
    suspend fun observeRulesOnce(): List<SpendingRuleEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(rules: List<SpendingRuleEntity>)

    @Update
    suspend fun updateRule(rule: SpendingRuleEntity)
}

@Dao
interface SmsAuditDao {
    @Query("SELECT * FROM sms_senders ORDER BY bankDisplayName ASC")
    fun observeSenders(): Flow<List<SmsSenderEntity>>

    @Query("SELECT * FROM sms_senders")
    suspend fun getSendersOnce(): List<SmsSenderEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSenders(senders: List<SmsSenderEntity>)

    @Query("SELECT * FROM processed_sms ORDER BY processedAt DESC LIMIT 50")
    fun observeRecentProcessedSms(): Flow<List<ProcessedSmsEntity>>

    @Query("SELECT * FROM processed_sms WHERE messageFingerprint = :fingerprint LIMIT 1")
    suspend fun findProcessedByFingerprint(fingerprint: String): ProcessedSmsEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertProcessedSms(entry: ProcessedSmsEntity): Long

    @Query("DELETE FROM processed_sms")
    suspend fun clearProcessedSms()
}

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications ORDER BY createdAt DESC LIMIT 40")
    fun observeNotifications(): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE createdAt >= :sinceMillis ORDER BY createdAt DESC")
    suspend fun getNotificationsSinceOnce(sinceMillis: Long): List<NotificationEntity>

    @Query("SELECT COUNT(*) FROM notifications WHERE isRead = 0")
    fun observeUnreadCount(): Flow<Int>

    @Insert
    suspend fun insert(notification: NotificationEntity): Long

    @Query("UPDATE notifications SET isRead = 1")
    suspend fun markAllRead()

    @Query("DELETE FROM notifications")
    suspend fun clearAll()
}

@Dao
interface NoSpendChallengeDao {
    @Query("SELECT * FROM no_spend_challenges ORDER BY createdAt DESC")
    fun observeChallenges(): Flow<List<NoSpendChallengeEntity>>

    @Upsert
    suspend fun upsertChallenge(challenge: NoSpendChallengeEntity): Long

    @Query("DELETE FROM no_spend_challenges WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM no_spend_challenges")
    suspend fun deleteAll()
}
