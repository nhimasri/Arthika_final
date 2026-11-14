package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.PaymentMethod
import com.example.domain.model.SmsProcessDecision
import com.example.domain.model.SpendingGuardLevel
import com.example.domain.model.TransactionSource
import com.example.domain.model.TransactionStatus
import com.example.domain.model.TransactionType
import java.math.BigDecimal

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: Long = 1L,
    val name: String,
    val email: String = "",
    val currencyCode: String = "INR",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "financial_profiles",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["userId"], unique = true)]
)
data class FinancialProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val userId: Long = 1L,
    val monthlyIncome: BigDecimal,
    val fixedExpenses: BigDecimal,
    val protectedSavings: BigDecimal,
    val monthlyBudgetOverride: BigDecimal? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val iconKey: String,
    val colorHex: String,
    val isExpenseCategory: Boolean = true,
    val sortOrder: Int = 0
)

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["categoryId"]),
        Index(value = ["timestamp"]),
        Index(value = ["fingerprint"], unique = true),
        Index(value = ["referenceId"])
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val merchantName: String? = null,
    val amount: BigDecimal,
    val type: TransactionType,
    val categoryId: String,
    val timestamp: Long,
    val paymentMethod: PaymentMethod = PaymentMethod.UPI,
    val source: TransactionSource = TransactionSource.MANUAL,
    val status: TransactionStatus = TransactionStatus.CONFIRMED,
    val isTransfer: Boolean = false,
    val accountRef: String? = null,
    val bankSender: String? = null,
    val referenceId: String? = null,
    val fingerprint: String? = null,
    val availableBalance: BigDecimal? = null,
    val notes: String? = null
)

@Entity(
    tableName = "budgets",
    indices = [Index(value = ["yearMonth"], unique = true)]
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val yearMonth: String,
    val plannedIncome: BigDecimal,
    val fixedExpenses: BigDecimal,
    val protectedSavings: BigDecimal,
    val flexibleBudget: BigDecimal,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "category_budgets",
    foreignKeys = [
        ForeignKey(
            entity = BudgetEntity::class,
            parentColumns = ["id"],
            childColumns = ["budgetId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["budgetId", "categoryId"], unique = true),
        Index(value = ["categoryId"])
    ]
)
data class CategoryBudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val budgetId: Long,
    val categoryId: String,
    val limitAmount: BigDecimal
)

@Entity(tableName = "savings_goals")
data class SavingsGoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val targetAmount: BigDecimal,
    val currentAmount: BigDecimal = BigDecimal.ZERO,
    val targetDateEpochMillis: Long,
    val iconKey: String = "savings",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "savings_contributions",
    foreignKeys = [
        ForeignKey(
            entity = SavingsGoalEntity::class,
            parentColumns = ["id"],
            childColumns = ["goalId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["goalId"])]
)
data class SavingsContributionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val goalId: Long,
    val amount: BigDecimal,
    val note: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "recurring_payments",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index(value = ["categoryId"])]
)
data class RecurringPaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val amount: BigDecimal,
    val categoryId: String,
    val dayOfMonth: Int,
    val isPaidForCurrentMonth: Boolean = false,
    val isActive: Boolean = true,
    val paidYearMonth: String? = null,
    val startYearMonth: String? = null
)

@Entity(
    tableName = "recurring_bill_payments",
    foreignKeys = [
        ForeignKey(
            entity = RecurringPaymentEntity::class,
            parentColumns = ["id"],
            childColumns = ["recurringPaymentId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["linkedTransactionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["recurringPaymentId", "yearMonth"]),
        Index(value = ["linkedTransactionId"], unique = true)
    ]
)
data class RecurringBillPaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val recurringPaymentId: Long,
    val yearMonth: String,
    val paidAmount: BigDecimal,
    val linkedTransactionId: Long,
    val paidAtMillis: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "spending_rules",
    indices = [Index(value = ["ruleCode"], unique = true)]
)
data class SpendingRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val ruleCode: String,
    val name: String,
    val thresholdPercent: Int,
    val isEnabled: Boolean = true
)

@Entity(
    tableName = "sms_senders",
    indices = [Index(value = ["senderPattern"], unique = true)]
)
data class SmsSenderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val senderPattern: String,
    val bankDisplayName: String,
    val isTrusted: Boolean = true
)

@Entity(
    tableName = "processed_sms",
    indices = [
        Index(value = ["messageFingerprint"], unique = true),
        Index(value = ["referenceId"]),
        Index(value = ["transactionTimestamp"])
    ]
)
data class ProcessedSmsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val messageFingerprint: String,
    val senderHeader: String,
    val maskedAccount: String? = null,
    val amount: BigDecimal? = null,
    val transactionType: TransactionType? = null,
    val referenceId: String? = null,
    val transactionTimestamp: Long,
    val processedAt: Long = System.currentTimeMillis(),
    val decision: SmsProcessDecision
)

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val message: String,
    val level: SpendingGuardLevel,
    val relatedTransactionId: Long? = null,
    val isRead: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "no_spend_challenges")
data class NoSpendChallengeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val targetDays: Int,
    val completedDays: Int = 0,
    val isActive: Boolean = true,
    val lastCheckedDateIso: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
