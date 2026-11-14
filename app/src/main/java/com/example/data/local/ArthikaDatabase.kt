package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.BudgetDao
import com.example.data.local.dao.CategoryDao
import com.example.data.local.dao.FinancialProfileDao
import com.example.data.local.dao.NoSpendChallengeDao
import com.example.data.local.dao.NotificationDao
import com.example.data.local.dao.RecurringPaymentDao
import com.example.data.local.dao.SavingsGoalDao
import com.example.data.local.dao.SmsAuditDao
import com.example.data.local.dao.SpendingRuleDao
import com.example.data.local.dao.TransactionDao
import com.example.data.local.dao.UserDao
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

@Database(
    entities = [
        UserEntity::class,
        FinancialProfileEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        BudgetEntity::class,
        CategoryBudgetEntity::class,
        SavingsGoalEntity::class,
        SavingsContributionEntity::class,
        RecurringPaymentEntity::class,
        RecurringBillPaymentEntity::class,
        SpendingRuleEntity::class,
        SmsSenderEntity::class,
        ProcessedSmsEntity::class,
        NotificationEntity::class,
        NoSpendChallengeEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(RoomTypeConverters::class)
abstract class ArthikaDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun financialProfileDao(): FinancialProfileDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun savingsGoalDao(): SavingsGoalDao
    abstract fun recurringPaymentDao(): RecurringPaymentDao
    abstract fun spendingRuleDao(): SpendingRuleDao
    abstract fun smsAuditDao(): SmsAuditDao
    abstract fun notificationDao(): NotificationDao
    abstract fun noSpendChallengeDao(): NoSpendChallengeDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `recurring_payments` ADD COLUMN `paidYearMonth` TEXT")
                db.execSQL("ALTER TABLE `recurring_payments` ADD COLUMN `startYearMonth` TEXT")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `recurring_bill_payments` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `recurringPaymentId` INTEGER NOT NULL,
                        `yearMonth` TEXT NOT NULL,
                        `paidAmount` TEXT NOT NULL,
                        `linkedTransactionId` INTEGER NOT NULL,
                        `paidAtMillis` INTEGER NOT NULL,
                        FOREIGN KEY(`recurringPaymentId`) REFERENCES `recurring_payments`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`linkedTransactionId`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_recurring_bill_payments_recurringPaymentId_yearMonth` ON `recurring_bill_payments` (`recurringPaymentId`, `yearMonth`)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_recurring_bill_payments_linkedTransactionId` ON `recurring_bill_payments` (`linkedTransactionId`)"
                )

                // Deduplicate spending_rules before adding unique index on ruleCode
                db.execSQL(
                    "DELETE FROM `spending_rules` WHERE `id` NOT IN (SELECT MIN(`id`) FROM `spending_rules` GROUP BY `ruleCode`)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_spending_rules_ruleCode` ON `spending_rules` (`ruleCode`)"
                )
            }
        }

        fun build(context: Context): ArthikaDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                ArthikaDatabase::class.java,
                "arthika_finance.db"
            )
                .addMigrations(MIGRATION_1_2)
                .build()
        }
    }
}
