package com.example

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.ArthikaDatabase
import com.example.data.local.DatabaseSeeder
import com.example.data.local.UserPreferencesDataStore
import com.example.data.local.entity.FinancialProfileEntity
import com.example.data.local.entity.TransactionEntity
import com.example.data.repository.BillPaymentResult
import com.example.data.repository.CategoryOperationResult
import com.example.data.repository.FinanceRepository
import com.example.data.repository.GoalOperationResult
import com.example.data.repository.SmsImportOutcome
import com.example.data.repository.SmsTransactionRepository
import com.example.domain.budget.BudgetAlertEvaluator
import com.example.domain.budget.DynamicBudgetEngine
import com.example.domain.budget.FinancialAlertManager
import com.example.domain.budget.SpendingGuardEngine
import com.example.domain.budget.ThresholdStatus
import com.example.domain.model.PaymentMethod
import com.example.domain.model.SmsProcessDecision
import com.example.domain.model.TransactionStatus
import com.example.domain.model.TransactionType
import com.example.domain.sms.TransactionDeduplicationEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ArthikaRobolectricTest {

    private lateinit var context: Context
    private lateinit var db: ArthikaDatabase
    private lateinit var prefs: UserPreferencesDataStore
    private lateinit var financeRepo: FinanceRepository
    private lateinit var smsRepo: SmsTransactionRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, ArthikaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val seeder = DatabaseSeeder(db)
        prefs = UserPreferencesDataStore(context)
        val evaluator = BudgetAlertEvaluator(
            context = context,
            db = db,
            preferencesDataStore = prefs,
            dynamicBudgetEngine = DynamicBudgetEngine(),
            spendingGuardEngine = SpendingGuardEngine()
        )
        financeRepo = FinanceRepository(db, seeder, evaluator)
        smsRepo = SmsTransactionRepository(db, prefs, TransactionDeduplicationEngine(), evaluator)
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `disabled SMS detection ignores incoming background SMS`() = runTest {
        db.categoryDao().insertAll(DatabaseSeeder.defaultCategories())
        prefs.setSmsDetectionEnabled(false)

        val outcome = smsRepo.processIncomingBroadcastSms(
            rawSender = "AD-HDFCBK",
            messageBody = "Your A/c XX1234 debited by Rs. 500.00 at Swiggy.",
            receivedAtMillis = System.currentTimeMillis()
        )

        assertTrue(outcome is SmsImportOutcome.Rejected)
        assertEquals(
            SmsProcessDecision.IGNORED_DISABLED,
            (outcome as SmsImportOutcome.Rejected).decision
        )
        assertEquals(0, db.transactionDao().observeAllTransactions().first().size)
    }

    @Test
    fun `database persistence and duplicate SMS prevention work end to end`() = runTest {
        db.categoryDao().insertAll(DatabaseSeeder.defaultCategories())
        prefs.setSmsDetectionEnabled(true)

        val ts = System.currentTimeMillis()
        val msg = "Your A/c XX1234 debited by Rs. 800.00 at Amazon. UPI Ref No 7766554433."

        val first = smsRepo.processIncomingBroadcastSms("AD-HDFCBK", msg, ts)
        val second = smsRepo.processIncomingBroadcastSms("AD-HDFCBK", msg, ts + 15_000L)

        assertTrue(first is SmsImportOutcome.Imported)
        assertTrue(second is SmsImportOutcome.Duplicate)
        assertEquals(1, db.transactionDao().observeAllTransactions().first().size)
    }

    @Test
    fun `pending NEEDS_REVIEW SMS does not affect summary or alerts until confirmed, and rejection leaves totals clean`() = runTest {
        val seeder = DatabaseSeeder(db)
        seeder.seedSystemDefaultsIfNeeded()
        financeRepo.completeUserSetup(
            name = "Priya",
            monthlyIncome = BigDecimal("30000.00"),
            fixedExpenses = BigDecimal("10000.00"),
            protectedSavings = BigDecimal("5000.00")
        )
        prefs.setSinglePaymentLimit(BigDecimal("1000.00"))

        val pendingOutcome = smsRepo.importSmsManuallyOrFromInbox(
            rawSender = "VK-NEWBNK",
            messageBody = "Your A/c XX4321 debited by Rs. 2,500.00 on 14-Nov-2026.",
            receivedAtMillis = System.currentTimeMillis(),
            postNotification = false
        )
        assertTrue(pendingOutcome is SmsImportOutcome.Imported)
        val pendingImported = pendingOutcome as SmsImportOutcome.Imported
        assertEquals(TransactionStatus.NEEDS_REVIEW, pendingImported.transaction.status)
        assertTrue("Pending txn must not trigger spending alerts", pendingImported.triggeredAlerts.isEmpty())

        val alertManager = FinancialAlertManager(
            context = context,
            db = db,
            preferencesDataStore = prefs,
            dynamicBudgetEngine = DynamicBudgetEngine(),
            spendingGuardEngine = SpendingGuardEngine()
        )
        assertTrue(
            "Alert manager must exclude NEEDS_REVIEW transactions",
            alertManager.evaluateAndNotify(postSystemNotification = false).isEmpty()
        )

        val summaryWhilePending = DynamicBudgetEngine().calculateSummary(
            profile = db.financialProfileDao().getProfileOnce(),
            transactions = db.transactionDao().observeAllTransactions().first(),
            recurringPayments = emptyList(),
            categories = db.categoryDao().getCategoriesOnce(),
            categoryBudgets = emptyList()
        )
        assertEquals(0, BigDecimal.ZERO.compareTo(summaryWhilePending.totalExpensesThisMonth))

        // Confirm pending txn => immediately recalculates and triggers single-payment alert
        val confirmAlerts = financeRepo.confirmReviewedTransaction(
            transaction = pendingImported.transaction,
            updatedMerchant = "Electronics Store",
            updatedCategoryId = "shopping",
            markAsTransfer = false
        )
        assertTrue(confirmAlerts.any { it.id == "single_payment_exceeded" })

        val summaryAfterConfirm = DynamicBudgetEngine().calculateSummary(
            profile = db.financialProfileDao().getProfileOnce(),
            transactions = db.transactionDao().observeAllTransactions().first(),
            recurringPayments = emptyList(),
            categories = db.categoryDao().getCategoriesOnce(),
            categoryBudgets = emptyList()
        )
        assertEquals(0, BigDecimal("2500.00").compareTo(summaryAfterConfirm.totalExpensesThisMonth))

        // Import another pending transaction and reject (delete) it -> summary stays 2500
        val secondPending = smsRepo.importSmsManuallyOrFromInbox(
            rawSender = "VK-NEWBNK",
            messageBody = "Your A/c XX4321 debited by Rs. 4,000.00 Ref 88776655.",
            receivedAtMillis = System.currentTimeMillis(),
            postNotification = false
        ) as SmsImportOutcome.Imported
        financeRepo.deleteTransaction(secondPending.transaction.id)

        val summaryAfterReject = DynamicBudgetEngine().calculateSummary(
            profile = db.financialProfileDao().getProfileOnce(),
            transactions = db.transactionDao().observeAllTransactions().first(),
            recurringPayments = emptyList(),
            categories = db.categoryDao().getCategoriesOnce(),
            categoryBudgets = emptyList()
        )
        assertEquals(0, BigDecimal("2500.00").compareTo(summaryAfterReject.totalExpensesThisMonth))
    }

    @Test
    fun `confirmed transaction supports repeated edits preserving ID and recalculates totals`() = runTest {
        val seeder = DatabaseSeeder(db)
        seeder.seedSystemDefaultsIfNeeded()
        financeRepo.completeUserSetup("Arjun", BigDecimal("20000"), BigDecimal("5000"), BigDecimal("3000"))

        val id = financeRepo.addManualTransaction(
            title = "Lunch",
            amount = BigDecimal("300.00"),
            type = TransactionType.DEBIT,
            categoryId = "food",
            timestamp = System.currentTimeMillis(),
            paymentMethod = PaymentMethod.UPI,
            notes = null
        )

        val inserted = db.transactionDao().getById(id)!!
        financeRepo.updateTransaction(inserted.copy(amount = BigDecimal("450.00"), title = "Team Lunch"))
        val afterFirstEdit = db.transactionDao().getById(id)!!
        assertEquals(id, afterFirstEdit.id)
        assertEquals(0, BigDecimal("450.00").compareTo(afterFirstEdit.amount))

        financeRepo.updateTransaction(afterFirstEdit.copy(amount = BigDecimal("600.00")))
        val afterSecondEdit = db.transactionDao().getById(id)!!
        assertEquals(id, afterSecondEdit.id)
        assertEquals(0, BigDecimal("600.00").compareTo(afterSecondEdit.amount))

        financeRepo.deleteTransaction(id)
        assertEquals(null, db.transactionDao().getById(id))
    }

    @Test
    fun `recurring bill monthly payment creates single expense blocks duplicates and handles month rollover`() = runTest {
        val seeder = DatabaseSeeder(db)
        seeder.seedSystemDefaultsIfNeeded()
        financeRepo.completeUserSetup("Priya", BigDecimal("30000"), BigDecimal("5000"), BigDecimal("5000"))

        val currentYm = YearMonth.now()
        val nextYm = currentYm.plusMonths(1)

        financeRepo.addRecurringPayment(
            title = "Broadband",
            amount = BigDecimal("999.00"),
            categoryId = "bills",
            dayOfMonth = 20,
            startYearMonth = currentYm
        )
        val bill = db.recurringPaymentDao().getActiveRecurringPaymentsOnce().first()

        // Partial payment 400
        val p1 = financeRepo.recordRecurringBillPayment(bill.id, BigDecimal("400.00"), currentYm)
        assertTrue(p1 is BillPaymentResult.Paid)

        // Attempt to overpay remaining (700 > 599) -> rejected
        val overpay = financeRepo.recordRecurringBillPayment(bill.id, BigDecimal("700.00"), currentYm)
        assertTrue(overpay is BillPaymentResult.Rejected)

        // Pay remaining 599
        val p2 = financeRepo.recordRecurringBillPayment(bill.id, null, currentYm)
        assertTrue(p2 is BillPaymentResult.Paid)

        // Duplicate payment attempt when already fully paid -> rejected
        val dup = financeRepo.recordRecurringBillPayment(bill.id, null, currentYm)
        assertTrue(dup is BillPaymentResult.Rejected)

        val billPaidState = db.recurringPaymentDao().getById(bill.id)!!
        assertTrue(billPaidState.isPaidForCurrentMonth)
        assertEquals(currentYm.toString(), billPaidState.paidYearMonth)
        assertEquals(2, db.transactionDao().observeAllTransactions().first().size)

        // Transition to next month resets paid flag for next month while keeping current month's history
        financeRepo.ensureCurrentMonthBudget(nextYm)
        val nextMonthBill = db.recurringPaymentDao().getById(bill.id)!!
        assertFalse(nextMonthBill.isPaidForCurrentMonth)
        assertEquals(2, db.recurringPaymentDao().getBillPaymentsForBillAndMonthOnce(bill.id, currentYm.toString()).size)

        // Reversing current month removes linked transactions
        financeRepo.unmarkRecurringBillPaid(bill.id, currentYm)
        assertEquals(0, db.transactionDao().observeAllTransactions().first().size)
        assertEquals(0, db.recurringPaymentDao().getBillPaymentsForBillAndMonthOnce(bill.id, currentYm.toString()).size)
    }

    @Test
    fun `category budgets stay isolated by month and deleting category reassigns items to other`() = runTest {
        val seeder = DatabaseSeeder(db)
        seeder.seedSystemDefaultsIfNeeded()

        val created = financeRepo.createCategory("Coffee Clubs")
        assertTrue(created is CategoryOperationResult.Success)
        val newCatId = (created as CategoryOperationResult.Success).categoryId

        val currentYm = YearMonth.now()
        val prevYm = currentYm.minusMonths(1)

        financeRepo.updateCategoryBudget(newCatId, BigDecimal("1500.00"), prevYm)
        financeRepo.updateCategoryBudget(newCatId, BigDecimal("2500.00"), currentYm)

        val prevBudgets = db.budgetDao().observeCategoryBudgetsForMonth(prevYm.toString()).first()
        val currBudgets = db.budgetDao().observeCategoryBudgetsForMonth(currentYm.toString()).first()
        assertEquals(1, prevBudgets.size)
        assertEquals(1, currBudgets.size)
        assertEquals(0, BigDecimal("1500.00").compareTo(prevBudgets.first().limitAmount))
        assertEquals(0, BigDecimal("2500.00").compareTo(currBudgets.first().limitAmount))

        val txnId = financeRepo.addManualTransaction(
            title = "Espresso",
            amount = BigDecimal("200.00"),
            type = TransactionType.DEBIT,
            categoryId = newCatId,
            timestamp = System.currentTimeMillis(),
            paymentMethod = PaymentMethod.UPI,
            notes = null
        )

        val delRes = financeRepo.deleteCategory(newCatId)
        assertTrue(delRes is CategoryOperationResult.Success)
        assertEquals("other", db.transactionDao().getById(txnId)!!.categoryId)
        assertTrue(db.budgetDao().observeCategoryBudgetsForMonth(currentYm.toString()).first().isEmpty())
    }

    @Test
    fun `savings goal prevents overfunding and keeps contributions synchronized across edit and delete`() = runTest {
        val createRes = financeRepo.upsertSavingsGoalWithValidation(
            com.example.data.local.entity.SavingsGoalEntity(
                name = "Emergency Fund",
                targetAmount = BigDecimal("1000.00"),
                currentAmount = BigDecimal("1500.00"),
                targetDateEpochMillis = System.currentTimeMillis() + 86_400_000L * 30
            )
        )
        assertTrue("Initial > target must fail", createRes is GoalOperationResult.ValidationError)

        val validGoalId = (financeRepo.upsertSavingsGoalWithValidation(
            com.example.data.local.entity.SavingsGoalEntity(
                name = "Emergency Fund",
                targetAmount = BigDecimal("1000.00"),
                currentAmount = BigDecimal("400.00"),
                targetDateEpochMillis = System.currentTimeMillis() + 86_400_000L * 30
            )
        ) as GoalOperationResult.Success).goalId

        // Attempt to contribute 700 when only 600 remains -> rejected
        val overContribute = financeRepo.contributeToGoalWithValidation(validGoalId, BigDecimal("700.00"))
        assertTrue(overContribute is GoalOperationResult.ValidationError)

        // Valid contribution 400 -> currentAmount becomes 800
        assertTrue(financeRepo.contributeToGoalWithValidation(validGoalId, BigDecimal("400.00")) is GoalOperationResult.Success)
        assertEquals(0, BigDecimal("800.00").compareTo(db.savingsGoalDao().getGoalById(validGoalId)!!.currentAmount))

        val contributions = db.savingsGoalDao().getContributionsForGoalOnce(validGoalId)
        assertEquals(2, contributions.size)
        val secondContribution = contributions.first { it.amount.compareTo(BigDecimal("400.00")) == 0 }

        // Edit to 700 => 400 + 700 = 1100 > 1000 => rejected
        assertTrue(financeRepo.updateGoalContribution(secondContribution.id, BigDecimal("700.00"), null) is GoalOperationResult.ValidationError)

        // Edit to 500 => 400 + 500 = 900 => accepted
        assertTrue(financeRepo.updateGoalContribution(secondContribution.id, BigDecimal("500.00"), "Boost") is GoalOperationResult.Success)
        assertEquals(0, BigDecimal("900.00").compareTo(db.savingsGoalDao().getGoalById(validGoalId)!!.currentAmount))

        // Delete contribution => 400 remains
        assertTrue(financeRepo.deleteGoalContribution(secondContribution.id) is GoalOperationResult.Success)
        assertEquals(0, BigDecimal("400.00").compareTo(db.savingsGoalDao().getGoalById(validGoalId)!!.currentAmount))
    }

    @Test
    fun `Room MIGRATION_1_2 preserves existing data and upgrades schema non-destructively`() {
        val dbFile = File(context.cacheDir, "migration_test_v1.db").apply { delete() }
        val v1Db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        try {
            v1Db.execSQL("CREATE TABLE `users` (`id` INTEGER NOT NULL, `name` TEXT NOT NULL, `email` TEXT NOT NULL, `currencyCode` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            v1Db.execSQL("CREATE TABLE `financial_profiles` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `userId` INTEGER NOT NULL, `monthlyIncome` TEXT NOT NULL, `fixedExpenses` TEXT NOT NULL, `protectedSavings` TEXT NOT NULL, `monthlyBudgetOverride` TEXT, `updatedAt` INTEGER NOT NULL, FOREIGN KEY(`userId`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
            v1Db.execSQL("CREATE UNIQUE INDEX `index_financial_profiles_userId` ON `financial_profiles` (`userId`)")
            v1Db.execSQL("CREATE TABLE `categories` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `iconKey` TEXT NOT NULL, `colorHex` TEXT NOT NULL, `isExpenseCategory` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            v1Db.execSQL("CREATE TABLE `transactions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `merchantName` TEXT, `amount` TEXT NOT NULL, `type` TEXT NOT NULL, `categoryId` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `paymentMethod` TEXT NOT NULL, `source` TEXT NOT NULL, `status` TEXT NOT NULL, `isTransfer` INTEGER NOT NULL, `accountRef` TEXT, `bankSender` TEXT, `referenceId` TEXT, `fingerprint` TEXT, `availableBalance` TEXT, `notes` TEXT, FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)")
            v1Db.execSQL("CREATE INDEX `index_transactions_categoryId` ON `transactions` (`categoryId`)")
            v1Db.execSQL("CREATE INDEX `index_transactions_timestamp` ON `transactions` (`timestamp`)")
            v1Db.execSQL("CREATE UNIQUE INDEX `index_transactions_fingerprint` ON `transactions` (`fingerprint`)")
            v1Db.execSQL("CREATE INDEX `index_transactions_referenceId` ON `transactions` (`referenceId`)")
            v1Db.execSQL("CREATE TABLE `budgets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `yearMonth` TEXT NOT NULL, `plannedIncome` TEXT NOT NULL, `fixedExpenses` TEXT NOT NULL, `protectedSavings` TEXT NOT NULL, `flexibleBudget` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)")
            v1Db.execSQL("CREATE UNIQUE INDEX `index_budgets_yearMonth` ON `budgets` (`yearMonth`)")
            v1Db.execSQL("CREATE TABLE `category_budgets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `budgetId` INTEGER NOT NULL, `categoryId` TEXT NOT NULL, `limitAmount` TEXT NOT NULL, FOREIGN KEY(`budgetId`) REFERENCES `budgets`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
            v1Db.execSQL("CREATE UNIQUE INDEX `index_category_budgets_budgetId_categoryId` ON `category_budgets` (`budgetId`, `categoryId`)")
            v1Db.execSQL("CREATE INDEX `index_category_budgets_categoryId` ON `category_budgets` (`categoryId`)")
            v1Db.execSQL("CREATE TABLE `savings_goals` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `targetAmount` TEXT NOT NULL, `currentAmount` TEXT NOT NULL, `targetDateEpochMillis` INTEGER NOT NULL, `iconKey` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)")
            v1Db.execSQL("CREATE TABLE `savings_contributions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `goalId` INTEGER NOT NULL, `amount` TEXT NOT NULL, `note` TEXT, `timestamp` INTEGER NOT NULL, FOREIGN KEY(`goalId`) REFERENCES `savings_goals`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
            v1Db.execSQL("CREATE INDEX `index_savings_contributions_goalId` ON `savings_contributions` (`goalId`)")
            v1Db.execSQL("CREATE TABLE `recurring_payments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `amount` TEXT NOT NULL, `categoryId` TEXT NOT NULL, `dayOfMonth` INTEGER NOT NULL, `isPaidForCurrentMonth` INTEGER NOT NULL, `isActive` INTEGER NOT NULL, FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)")
            v1Db.execSQL("CREATE INDEX `index_recurring_payments_categoryId` ON `recurring_payments` (`categoryId`)")
            v1Db.execSQL("CREATE TABLE `spending_rules` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `ruleCode` TEXT NOT NULL, `name` TEXT NOT NULL, `thresholdPercent` INTEGER NOT NULL, `isEnabled` INTEGER NOT NULL)")
            v1Db.execSQL("CREATE TABLE `sms_senders` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `senderPattern` TEXT NOT NULL, `bankDisplayName` TEXT NOT NULL, `isTrusted` INTEGER NOT NULL)")
            v1Db.execSQL("CREATE UNIQUE INDEX `index_sms_senders_senderPattern` ON `sms_senders` (`senderPattern`)")
            v1Db.execSQL("CREATE TABLE `processed_sms` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `messageFingerprint` TEXT NOT NULL, `senderHeader` TEXT NOT NULL, `maskedAccount` TEXT, `amount` TEXT, `transactionType` TEXT, `referenceId` TEXT, `transactionTimestamp` INTEGER NOT NULL, `processedAt` INTEGER NOT NULL, `decision` TEXT NOT NULL)")
            v1Db.execSQL("CREATE UNIQUE INDEX `index_processed_sms_messageFingerprint` ON `processed_sms` (`messageFingerprint`)")
            v1Db.execSQL("CREATE INDEX `index_processed_sms_referenceId` ON `processed_sms` (`referenceId`)")
            v1Db.execSQL("CREATE INDEX `index_processed_sms_transactionTimestamp` ON `processed_sms` (`transactionTimestamp`)")
            v1Db.execSQL("CREATE TABLE `notifications` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `message` TEXT NOT NULL, `level` TEXT NOT NULL, `relatedTransactionId` INTEGER, `isRead` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)")
            v1Db.execSQL("CREATE TABLE `no_spend_challenges` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `targetDays` INTEGER NOT NULL, `completedDays` INTEGER NOT NULL, `isActive` INTEGER NOT NULL, `lastCheckedDateIso` TEXT, `createdAt` INTEGER NOT NULL)")

            v1Db.execSQL("INSERT INTO `categories` (`id`, `name`, `iconKey`, `colorHex`, `isExpenseCategory`, `sortOrder`) VALUES ('bills', 'Bills', 'receipt', '#3B82F6', 1, 1)")
            v1Db.execSQL("INSERT INTO `recurring_payments` (`id`, `title`, `amount`, `categoryId`, `dayOfMonth`, `isPaidForCurrentMonth`, `isActive`) VALUES (1, 'Wifi', '799.00', 'bills', 10, 0, 1)")
            v1Db.execSQL("INSERT INTO `spending_rules` (`ruleCode`, `name`, `thresholdPercent`, `isEnabled`) VALUES ('DAILY_75', 'Daily Warning', 75, 1)")
            v1Db.execSQL("INSERT INTO `spending_rules` (`ruleCode`, `name`, `thresholdPercent`, `isEnabled`) VALUES ('DAILY_75', 'Daily Warning Dup', 75, 1)")
            v1Db.execSQL("PRAGMA user_version = 1")
        } finally {
            v1Db.close()
        }

        val migrated = Room.databaseBuilder(context, ArthikaDatabase::class.java, dbFile.absolutePath)
            .addMigrations(ArthikaDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
        try {
            val writable = migrated.openHelper.writableDatabase
            assertEquals(2, writable.version)
            val billsCursor = writable.query("SELECT title, paidYearMonth, startYearMonth FROM recurring_payments WHERE id = 1")
            assertTrue(billsCursor.moveToFirst())
            assertEquals("Wifi", billsCursor.getString(0))
            billsCursor.close()

            val rulesCursor = writable.query("SELECT COUNT(*) FROM spending_rules WHERE ruleCode = 'DAILY_75'")
            assertTrue(rulesCursor.moveToFirst())
            assertEquals(1, rulesCursor.getInt(0))
            rulesCursor.close()

            val newTableCursor = writable.query("SELECT name FROM sqlite_master WHERE type='table' AND name='recurring_bill_payments'")
            assertTrue(newTableCursor.moveToFirst())
            newTableCursor.close()
        } finally {
            migrated.close()
            dbFile.delete()
        }
    }

    @Test
    fun `offline manual transaction entry persists and recalculates dashboard`() = runTest {
        val seeder = DatabaseSeeder(db)
        seeder.seedSystemDefaultsIfNeeded()
        financeRepo.completeUserSetup(
            name = "Priya",
            monthlyIncome = BigDecimal("25000.00"),
            fixedExpenses = BigDecimal("7000.00"),
            protectedSavings = BigDecimal("3000.00")
        )

        val initialTxns = db.transactionDao().observeAllTransactions().first()
        assertEquals(0, initialTxns.size)

        financeRepo.addManualTransaction(
            title = "Metro Pass",
            amount = BigDecimal("450.00"),
            type = TransactionType.DEBIT,
            categoryId = "transportation",
            timestamp = System.currentTimeMillis(),
            paymentMethod = PaymentMethod.UPI,
            notes = "Monthly travel"
        )

        val updatedTxns = db.transactionDao().observeAllTransactions().first()
        assertEquals(1, updatedTxns.size)

        val summary = DynamicBudgetEngine().calculateSummary(
            profile = db.financialProfileDao().getProfileOnce(),
            transactions = updatedTxns,
            recurringPayments = db.recurringPaymentDao().getActiveRecurringPaymentsOnce(),
            categories = db.categoryDao().getCategoriesOnce(),
            categoryBudgets = db.budgetDao().observeAllCategoryBudgets().first(),
            referenceDate = LocalDate.now()
        )
        assertEquals(0, BigDecimal("450.00").compareTo(summary.spentToday))
    }

    @Test
    fun `database starts completely empty of user transactions budgets and goals`() = runTest {
        val seeder = DatabaseSeeder(db)
        seeder.seedSystemDefaultsIfNeeded()

        assertTrue(db.categoryDao().getCategoriesOnce().isNotEmpty())
        assertTrue(db.smsAuditDao().getSendersOnce().isNotEmpty())
        assertTrue(db.spendingRuleDao().observeRulesOnce().isNotEmpty())

        assertEquals(null, db.userDao().getUserOnce())
        assertEquals(null, db.financialProfileDao().getProfileOnce())
        assertEquals(0, db.transactionDao().observeAllTransactions().first().size)
        assertEquals(0, db.budgetDao().getAllBudgetsOnce().size)
        assertEquals(0, db.budgetDao().observeAllCategoryBudgets().first().size)
        assertEquals(0, db.savingsGoalDao().getAllGoalsOnce().size)
        assertEquals(0, db.recurringPaymentDao().getActiveRecurringPaymentsOnce().size)
        assertEquals(0, db.noSpendChallengeDao().observeChallenges().first().size)
        assertEquals(0, db.notificationDao().observeNotifications().first().size)
        assertEquals(0, db.smsAuditDao().observeRecentProcessedSms().first().size)
    }

    @Test
    fun `category budget updates lazily and survives income profile updates and clears cleanly`() = runTest {
        financeRepo.updateCategoryBudget("food", BigDecimal("4000.00"))
        assertEquals(1, db.budgetDao().getAllBudgetsOnce().size)
        assertEquals(1, db.budgetDao().observeAllCategoryBudgets().first().size)

        financeRepo.updateFinancialProfile(
            monthlyIncome = BigDecimal("35000.00"),
            fixedExpenses = BigDecimal("10000.00"),
            protectedSavings = BigDecimal("5000.00")
        )
        val catBudgetsAfterProfileUpdate = db.budgetDao().observeAllCategoryBudgets().first()
        assertEquals(1, catBudgetsAfterProfileUpdate.size)
        assertEquals(0, BigDecimal("4000.00").compareTo(catBudgetsAfterProfileUpdate.first().limitAmount))

        financeRepo.clearAllUserData()
        assertEquals(null, db.userDao().getUserOnce())
        assertEquals(null, db.financialProfileDao().getProfileOnce())
        assertEquals(0, db.budgetDao().getAllBudgetsOnce().size)
        assertEquals(0, db.budgetDao().observeAllCategoryBudgets().first().size)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `MainViewModel initializes with zero balances and empty lists on fresh database`() = runTest {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val store = androidx.lifecycle.ViewModelStore()
        try {
            val seeder = DatabaseSeeder(db)
            seeder.seedSystemDefaultsIfNeeded()

            val vm = com.example.presentation.MainViewModel(
                financeRepository = financeRepo,
                smsTransactionRepository = smsRepo,
                smsInboxScanner = com.example.data.sms.SmsInboxScanner(context, smsRepo),
                preferencesDataStore = prefs,
                dynamicBudgetEngine = DynamicBudgetEngine(),
                spendingGuardEngine = SpendingGuardEngine(),
                financialInsightEngine = com.example.domain.budget.FinancialInsightEngine()
            )
            store.put("main_vm", vm)

            val state = vm.uiState.first { it.isLoaded }
            assertEquals("", state.userName)
            assertTrue(state.transactions.isEmpty())
            assertTrue(state.goals.isEmpty())
            assertTrue(state.recurringPayments.isEmpty())
            assertTrue(state.challenges.isEmpty())
            assertTrue(state.insights.isEmpty())
            val summary = state.summary!!
            assertEquals(0, BigDecimal.ZERO.compareTo(summary.monthlyIncome))
            assertEquals(0, BigDecimal.ZERO.compareTo(summary.totalExpensesThisMonth))
            assertEquals(0, BigDecimal.ZERO.compareTo(summary.protectedSavings))
            assertEquals(0, BigDecimal.ZERO.compareTo(summary.flexibleBudget))
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `payment exceeding category daily or single-payment limit creates immediate notification alerts`() = runTest {
        val seeder = DatabaseSeeder(db)
        seeder.seedSystemDefaultsIfNeeded()

        financeRepo.completeUserSetup(
            name = "Priya",
            monthlyIncome = BigDecimal("30000.00"),
            fixedExpenses = BigDecimal("10000.00"),
            protectedSavings = BigDecimal("5000.00")
        )
        financeRepo.updateCategoryBudget("food", BigDecimal("1000.00"))
        prefs.setSinglePaymentLimit(BigDecimal("1200.00"))

        val result = financeRepo.saveManualTransactionWithAlerts(
            title = "Team Dinner",
            amount = BigDecimal("1500.00"),
            type = TransactionType.DEBIT,
            categoryId = "food",
            timestamp = System.currentTimeMillis(),
            paymentMethod = PaymentMethod.UPI,
            notes = null,
            isTransfer = false
        )

        assertTrue(result.triggeredAlerts.any { it.id == "single_payment_exceeded" })
        assertTrue(result.triggeredAlerts.any { it.id == "cat_food_exceeded" })
        assertTrue(result.triggeredAlerts.any { it.id == "daily_exceeded" })

        val notifications = db.notificationDao().observeNotifications().first()
        assertTrue(notifications.any { it.title.contains("Single payment limit exceeded") })
        assertTrue(notifications.any { it.title.contains("Food budget exceeded") })
    }

    @Test
    fun `disabled daily rule suppresses approaching daily warning while keeping exceeded alert`() = runTest {
        val seeder = DatabaseSeeder(db)
        seeder.seedSystemDefaultsIfNeeded()
        val rules = db.spendingRuleDao().observeRulesOnce().map { rule ->
            if (rule.ruleCode == "DAILY_75") rule.copy(isEnabled = false) else rule
        }

        val today = LocalDate.of(2026, 11, 21) // 10 days left
        val profile = FinancialProfileEntity(
            monthlyIncome = BigDecimal("20000.00"),
            fixedExpenses = BigDecimal("5000.00"),
            protectedSavings = BigDecimal("5000.00")
        )
        // flexible = 10000 over 10 days => 1000/day. Spend 800 today (80%)
        val txn = TransactionEntity(
            id = 1L,
            title = "Groceries",
            amount = BigDecimal("800.00"),
            type = TransactionType.DEBIT,
            categoryId = "food",
            timestamp = today.atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        val summary = DynamicBudgetEngine().calculateSummary(
            profile = profile,
            transactions = listOf(txn),
            recurringPayments = emptyList(),
            categories = DatabaseSeeder.defaultCategories(),
            categoryBudgets = emptyList(),
            referenceDate = today
        )

        val alertsWhenDisabled = SpendingGuardEngine().evaluateAlerts(
            summary = summary,
            today = today,
            rules = rules
        )
        assertTrue(alertsWhenDisabled.none { it.id == "daily_75" })

        val enabledRules = db.spendingRuleDao().observeRulesOnce()
        val alertsWhenEnabled = SpendingGuardEngine().evaluateAlerts(
            summary = summary,
            today = today,
            rules = enabledRules
        )
        assertTrue(alertsWhenEnabled.any { it.id == "daily_75" })
    }

    @Test
    fun `FinancialAlertManager detects approaching and exceeded daily transaction volume from Room rules`() = runTest {
        val seeder = DatabaseSeeder(db)
        seeder.seedSystemDefaultsIfNeeded()

        val rules = db.spendingRuleDao().observeRulesOnce()
        assertTrue(rules.any { it.ruleCode == "VOLUME_10" && it.thresholdPercent == 10 })

        val alertManager = FinancialAlertManager(
            context = context,
            db = db,
            preferencesDataStore = prefs,
            dynamicBudgetEngine = DynamicBudgetEngine(),
            spendingGuardEngine = SpendingGuardEngine()
        )
        prefs.setDailyAlertsEnabled(true)

        val now = System.currentTimeMillis()
        // Insert 8 debit transactions today (80% of VOLUME_10 = approaching limit)
        repeat(8) { idx ->
            db.transactionDao().insertTransaction(
                TransactionEntity(
                    title = "Coffee $idx",
                    amount = BigDecimal("50.00"),
                    type = TransactionType.DEBIT,
                    categoryId = "food",
                    timestamp = now + idx
                )
            )
        }

        val approachingAlerts = alertManager.evaluateAndNotify(postSystemNotification = false)
        val volumeApproaching = approachingAlerts.find { it.id == "volume_approaching" }
        assertNotNull(volumeApproaching)
        assertEquals(
            ThresholdStatus.APPROACHING_LIMIT,
            alertManager.classifyStatus(volumeApproaching!!.level)
        )

        // Calling evaluateAndNotify again the same day deduplicates ORANGE approaching warnings
        val duplicateCheck = alertManager.evaluateAndNotify(postSystemNotification = false)
        assertTrue(duplicateCheck.none { it.id == "volume_approaching" })

        // Insert 2 more debits to hit 10 transactions today (100% of VOLUME_10 = budget/volume exceeded)
        repeat(2) { idx ->
            val rowId = db.transactionDao().insertTransaction(
                TransactionEntity(
                    title = "Snack $idx",
                    amount = BigDecimal("40.00"),
                    type = TransactionType.DEBIT,
                    categoryId = "food",
                    timestamp = now + 100 + idx
                )
            )
            if (idx == 1) {
                val exceededAlerts = alertManager.evaluateAndNotify(
                    triggeringTransactionId = rowId,
                    postSystemNotification = false
                )
                val volumeExceeded = exceededAlerts.find { it.id == "volume_exceeded" }
                assertNotNull(volumeExceeded)
                assertEquals(
                    ThresholdStatus.BUDGET_EXCEEDED,
                    alertManager.classifyStatus(volumeExceeded!!.level)
                )

                // Re-running for the same transactionId (e.g. WorkManager immediate check) avoids duplicate notification
                val workerReplay = alertManager.evaluateAndNotify(
                    triggeringTransactionId = rowId,
                    postSystemNotification = false
                )
                assertTrue(workerReplay.none { it.id == "volume_exceeded" })
            }
        }
    }
}
