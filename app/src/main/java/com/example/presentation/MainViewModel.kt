package com.example.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.UserPreferences
import com.example.data.local.UserPreferencesDataStore
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
import com.example.data.repository.BillPaymentResult
import com.example.data.repository.CategoryOperationResult
import com.example.data.repository.FinanceRepository
import com.example.data.repository.GoalOperationResult
import com.example.data.repository.SmsImportOutcome
import com.example.data.repository.SmsTransactionRepository
import com.example.data.sms.InboxScanSummary
import com.example.data.sms.SmsInboxScanner
import com.example.domain.budget.DynamicBudgetEngine
import com.example.domain.budget.FinancialInsightEngine
import com.example.domain.budget.SpendingGuardEngine
import com.example.domain.model.FinancialInsight
import com.example.domain.model.FinancialSummary
import com.example.domain.model.PaymentMethod
import com.example.domain.model.SpendingAlert
import com.example.domain.model.TransactionSource
import com.example.domain.model.TransactionStatus
import com.example.domain.model.TransactionType
import com.example.domain.model.WhatIfSimulationResult
import com.example.ui.theme.ThemePreference
import com.example.utils.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

enum class TransactionFilterChip(val label: String) {
    ALL("All"),
    DEBIT("Debit"),
    CREDIT("Credit"),
    SMS("SMS"),
    MANUAL("Manual"),
    NEEDS_REVIEW("Needs review")
}

data class TransactionFilterState(
    val searchQuery: String = "",
    val chip: TransactionFilterChip = TransactionFilterChip.ALL,
    val categoryId: String? = null,
    val paymentMethod: PaymentMethod? = null
)

data class ArthikaUiState(
    val isLoaded: Boolean = false,
    val userName: String = "",
    val preferences: UserPreferences = UserPreferences(),
    val summary: FinancialSummary? = null,
    val alerts: List<SpendingAlert> = emptyList(),
    val insights: List<FinancialInsight> = emptyList(),
    val transactions: List<TransactionEntity> = emptyList(),
    val pendingReviewTransactions: List<TransactionEntity> = emptyList(),
    val filteredTransactions: List<TransactionEntity> = emptyList(),
    val filterState: TransactionFilterState = TransactionFilterState(),
    val categories: List<CategoryEntity> = emptyList(),
    val goals: List<SavingsGoalEntity> = emptyList(),
    val goalContributionsByGoalId: Map<Long, List<SavingsContributionEntity>> = emptyMap(),
    val recurringPayments: List<RecurringPaymentEntity> = emptyList(),
    val billPayments: List<RecurringBillPaymentEntity> = emptyList(),
    val spendingRules: List<SpendingRuleEntity> = emptyList(),
    val challenges: List<NoSpendChallengeEntity> = emptyList(),
    val notifications: List<NotificationEntity> = emptyList(),
    val unreadNotificationCount: Int = 0,
    val processedSmsLogs: List<ProcessedSmsEntity> = emptyList(),
    val trustedSenders: List<SmsSenderEntity> = emptyList(),
    val whatIfResult: WhatIfSimulationResult? = null,
    val isScanningInbox: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val financeRepository: FinanceRepository,
    private val smsTransactionRepository: SmsTransactionRepository,
    private val smsInboxScanner: SmsInboxScanner,
    private val preferencesDataStore: UserPreferencesDataStore,
    private val dynamicBudgetEngine: DynamicBudgetEngine,
    private val spendingGuardEngine: SpendingGuardEngine,
    private val financialInsightEngine: FinancialInsightEngine
) : ViewModel() {

    private val activeYearMonthFlow = MutableStateFlow(YearMonth.now())
    private val filterStateFlow = MutableStateFlow(TransactionFilterState())
    private val whatIfResultFlow = MutableStateFlow<WhatIfSimulationResult?>(null)
    private val isScanningInboxFlow = MutableStateFlow(false)

    private val _toastMessage = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

    init {
        ensureMonthRolloverSynced()
    }

    fun ensureMonthRolloverSynced() {
        val currentYm = YearMonth.now()
        if (activeYearMonthFlow.value != currentYm) {
            activeYearMonthFlow.value = currentYm
        }
        viewModelScope.launch {
            financeRepository.ensureCurrentMonthBudget(currentYm)
        }
    }

    private val monthBudgetFlow = activeYearMonthFlow.flatMapLatest { ym ->
        financeRepository.observeCurrentMonthBudgetFlow(ym)
    }

    private val monthCategoryBudgetsFlow = activeYearMonthFlow.flatMapLatest { ym ->
        financeRepository.observeCategoryBudgetsForMonth(ym)
    }

    private val monthBillPaymentsFlow = activeYearMonthFlow.flatMapLatest { ym ->
        financeRepository.observeBillPaymentsForMonth(ym)
    }

    private val profileAndBudgetFlow = combine(
        financeRepository.userFlow,
        financeRepository.profileFlow,
        monthBudgetFlow
    ) { user, profile, monthlyBudget ->
        Triple(user, profile, monthlyBudget)
    }

    private val coreDataFlow = combine(
        profileAndBudgetFlow,
        financeRepository.allTransactionsFlow,
        financeRepository.categoriesFlow,
        monthCategoryBudgetsFlow
    ) { userProfileBudget, txns, categories, catBudgets ->
        CoreBundle(
            user = userProfileBudget.first,
            profile = userProfileBudget.second,
            monthlyBudget = userProfileBudget.third,
            transactions = txns,
            categories = categories,
            categoryBudgets = catBudgets
        )
    }

    private val planningEntitiesFlow = combine(
        financeRepository.goalsFlow,
        financeRepository.allContributionsFlow,
        financeRepository.recurringPaymentsFlow,
        monthBillPaymentsFlow
    ) { goals, contributions, recurring, billPayments ->
        PlanningEntitiesBundle(goals, contributions, recurring, billPayments)
    }

    private val planningDataFlow = combine(
        planningEntitiesFlow,
        financeRepository.spendingRulesFlow,
        financeRepository.challengesFlow,
        preferencesDataStore.preferencesFlow
    ) { entities, rules, challenges, prefs ->
        PlanningBundle(
            goals = entities.goals,
            contributions = entities.contributions,
            recurring = entities.recurring,
            billPayments = entities.billPayments,
            rules = rules,
            challenges = challenges,
            preferences = prefs
        )
    }

    private val auxDataFlow = combine(
        financeRepository.notificationsFlow,
        financeRepository.unreadNotificationCountFlow,
        smsTransactionRepository.processedSmsAuditFlow,
        smsTransactionRepository.trustedSendersFlow
    ) { notifications, unreadCount, smsLogs, senders ->
        AuxBundle(notifications, unreadCount, smsLogs, senders)
    }

    val uiState: StateFlow<ArthikaUiState> = combine(
        coreDataFlow,
        planningDataFlow,
        auxDataFlow,
        filterStateFlow,
        whatIfResultFlow,
        isScanningInboxFlow
    ) { values ->
        val core = values[0] as CoreBundle
        val plan = values[1] as PlanningBundle
        val aux = values[2] as AuxBundle
        val filter = values[3] as TransactionFilterState
        val whatIf = values[4] as WhatIfSimulationResult?
        val scanning = values[5] as Boolean

        val today = LocalDate.now()
        val summary = dynamicBudgetEngine.calculateSummary(
            profile = core.profile,
            transactions = core.transactions,
            recurringPayments = plan.recurring,
            categories = core.categories,
            categoryBudgets = core.categoryBudgets,
            referenceDate = today,
            monthlyBudget = core.monthlyBudget,
            monthBillPayments = plan.billPayments
        )

        val todayTransactionCount = core.transactions.count {
            it.status == TransactionStatus.CONFIRMED &&
                it.type == TransactionType.DEBIT &&
                !it.isTransfer &&
                DateUtils.toLocalDate(it.timestamp) == today
        }

        val alerts = spendingGuardEngine.evaluateAlerts(
            summary = summary,
            today = today,
            rules = plan.rules,
            singlePaymentLimit = plan.preferences.singlePaymentLimit,
            todayTransactionCount = todayTransactionCount
        )
        val pendingTxns = core.transactions.filter { it.status == TransactionStatus.NEEDS_REVIEW }
        val insights = financialInsightEngine.generateInsights(summary, pendingTxns.size, today)
        val filtered = applyTransactionFilters(core.transactions, filter, core.categories)

        ArthikaUiState(
            isLoaded = true,
            userName = core.user?.name.orEmpty(),
            preferences = plan.preferences,
            summary = summary,
            alerts = alerts,
            insights = insights,
            transactions = core.transactions,
            pendingReviewTransactions = pendingTxns,
            filteredTransactions = filtered,
            filterState = filter,
            categories = core.categories,
            goals = plan.goals,
            goalContributionsByGoalId = plan.contributions.groupBy { it.goalId },
            recurringPayments = plan.recurring,
            billPayments = plan.billPayments,
            spendingRules = plan.rules,
            challenges = plan.challenges,
            notifications = aux.notifications,
            unreadNotificationCount = aux.unreadCount,
            processedSmsLogs = aux.smsLogs,
            trustedSenders = aux.senders,
            whatIfResult = whatIf,
            isScanningInbox = scanning
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ArthikaUiState()
        )

    private fun applyTransactionFilters(
        transactions: List<TransactionEntity>,
        filter: TransactionFilterState,
        categories: List<CategoryEntity>
    ): List<TransactionEntity> {
        val catNames = categories.associate { it.id to it.name }
        return transactions.filter { txn ->
            val chipMatch = when (filter.chip) {
                TransactionFilterChip.ALL -> true
                TransactionFilterChip.DEBIT -> txn.type == TransactionType.DEBIT
                TransactionFilterChip.CREDIT -> txn.type == TransactionType.CREDIT
                TransactionFilterChip.SMS -> txn.source == TransactionSource.SMS
                TransactionFilterChip.MANUAL -> txn.source == TransactionSource.MANUAL
                TransactionFilterChip.NEEDS_REVIEW -> txn.status == TransactionStatus.NEEDS_REVIEW
            }
            val categoryMatch = filter.categoryId == null || txn.categoryId == filter.categoryId
            val methodMatch = filter.paymentMethod == null || txn.paymentMethod == filter.paymentMethod
            val q = filter.searchQuery.trim()
            val queryMatch = q.isEmpty() ||
                txn.title.contains(q, ignoreCase = true) ||
                txn.merchantName.orEmpty().contains(q, ignoreCase = true) ||
                txn.notes.orEmpty().contains(q, ignoreCase = true) ||
                catNames[txn.categoryId].orEmpty().contains(q, ignoreCase = true) ||
                txn.amount.toPlainString().contains(q)

            chipMatch && categoryMatch && methodMatch && queryMatch
        }
    }

    fun updateSearchQuery(query: String) {
        filterStateFlow.value = filterStateFlow.value.copy(searchQuery = query)
    }

    fun selectFilterChip(chip: TransactionFilterChip) {
        filterStateFlow.value = filterStateFlow.value.copy(chip = chip)
    }

    fun selectCategoryFilter(categoryId: String?) {
        filterStateFlow.value = filterStateFlow.value.copy(categoryId = categoryId)
    }

    fun selectPaymentMethodFilter(method: PaymentMethod?) {
        filterStateFlow.value = filterStateFlow.value.copy(paymentMethod = method)
    }

    fun clearExtraFilters() {
        filterStateFlow.value = filterStateFlow.value.copy(categoryId = null, paymentMethod = null)
    }

    fun completeOnboarding(
        name: String,
        monthlyIncome: BigDecimal,
        fixedExpenses: BigDecimal,
        protectedSavings: BigDecimal,
        enableSms: Boolean,
        scanInboxIfPermitted: Boolean = enableSms
    ) {
        viewModelScope.launch {
            financeRepository.completeUserSetup(
                name = name,
                monthlyIncome = monthlyIncome,
                fixedExpenses = fixedExpenses,
                protectedSavings = protectedSavings
            )
            preferencesDataStore.setSmsDetectionEnabled(enableSms)
            preferencesDataStore.setOnboardingCompleted(true)
            if (scanInboxIfPermitted) {
                smsInboxScanner.scanRecentInbox(daysBack = 30)
            }
        }
    }

    fun setSmsDetectionEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesDataStore.setSmsDetectionEnabled(enabled)
            _toastMessage.tryEmit(
                if (enabled) "Automatic SMS detection enabled" else "Automatic SMS detection paused"
            )
        }
    }

    fun setDailyAlertsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesDataStore.setDailyAlertsEnabled(enabled)
            _toastMessage.tryEmit(
                if (enabled) "Payment & limit notifications enabled" else "Payment & limit notifications paused"
            )
        }
    }

    fun setSinglePaymentLimit(limit: BigDecimal?) {
        viewModelScope.launch {
            preferencesDataStore.setSinglePaymentLimit(limit)
            _toastMessage.tryEmit(
                if (limit != null && limit > BigDecimal.ZERO) {
                    "Single-payment limit set to ${com.example.utils.CurrencyFormatter.formatInr(limit)}"
                } else {
                    "Single-payment limit cleared"
                }
            )
        }
    }

    fun setThemePreference(theme: ThemePreference) {
        viewModelScope.launch {
            preferencesDataStore.setThemePreference(theme)
        }
    }

    fun updateUserName(name: String) {
        viewModelScope.launch {
            financeRepository.updateUserName(name)
            _toastMessage.tryEmit("Profile updated")
        }
    }

    fun addManualTransaction(
        title: String,
        amount: BigDecimal,
        type: TransactionType,
        categoryId: String,
        timestamp: Long,
        paymentMethod: PaymentMethod,
        notes: String?,
        isTransfer: Boolean
    ) {
        viewModelScope.launch {
            val result = financeRepository.saveManualTransactionWithAlerts(
                title = title,
                amount = amount,
                type = type,
                categoryId = categoryId,
                timestamp = timestamp,
                paymentMethod = paymentMethod,
                notes = notes,
                isTransfer = isTransfer
            )
            val topAlert = result.triggeredAlerts.firstOrNull()
            if (topAlert != null) {
                _toastMessage.tryEmit("Alert: ${topAlert.title} — ${topAlert.message}")
            } else {
                _toastMessage.tryEmit("Transaction saved")
            }
        }
    }

    fun updateTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            val alerts = financeRepository.updateTransaction(transaction)
            val topAlert = alerts.firstOrNull()
            if (topAlert != null) {
                _toastMessage.tryEmit("Alert: ${topAlert.title} — ${topAlert.message}")
            } else {
                _toastMessage.tryEmit("Transaction updated")
            }
        }
    }

    fun confirmReviewedTransaction(
        transaction: TransactionEntity,
        merchant: String,
        categoryId: String,
        markAsTransfer: Boolean
    ) {
        viewModelScope.launch {
            val alerts = financeRepository.confirmReviewedTransaction(
                transaction = transaction,
                updatedMerchant = merchant,
                updatedCategoryId = categoryId,
                markAsTransfer = markAsTransfer
            )
            val topAlert = alerts.firstOrNull()
            if (topAlert != null) {
                _toastMessage.tryEmit("Alert: ${topAlert.title} — ${topAlert.message}")
            } else {
                _toastMessage.tryEmit("Transaction confirmed")
            }
        }
    }

    fun deleteTransaction(id: Long) {
        viewModelScope.launch {
            financeRepository.deleteTransaction(id)
            _toastMessage.tryEmit("Transaction removed")
        }
    }

    fun importSmsText(sender: String, body: String) {
        viewModelScope.launch {
            when (val outcome = smsTransactionRepository.importSmsManuallyOrFromInbox(
                rawSender = sender.trim(),
                messageBody = body,
                receivedAtMillis = System.currentTimeMillis(),
                postNotification = true
            )) {
                is SmsImportOutcome.Imported -> {
                    val topAlert = outcome.triggeredAlerts.firstOrNull()
                    if (topAlert != null) {
                        _toastMessage.tryEmit("Alert: ${topAlert.title} — ${topAlert.message}")
                    } else if (outcome.transaction.status == TransactionStatus.NEEDS_REVIEW) {
                        _toastMessage.tryEmit("Detected '${outcome.transaction.title}' — waiting in Needs Review")
                    } else {
                        _toastMessage.tryEmit("Imported ${outcome.transaction.title} (${outcome.transaction.amount})")
                    }
                }
                is SmsImportOutcome.Duplicate -> {
                    _toastMessage.tryEmit("Duplicate blocked: ${outcome.reason}")
                }
                is SmsImportOutcome.Rejected -> {
                    _toastMessage.tryEmit("Ignored: ${outcome.reason}")
                }
            }
        }
    }

    fun scanSmsInbox() {
        viewModelScope.launch {
            isScanningInboxFlow.value = true
            try {
                val summary: InboxScanSummary = smsInboxScanner.scanRecentInbox(daysBack = 30)
                if (!summary.permissionGranted) {
                    _toastMessage.tryEmit("READ_SMS permission not granted. Use Paste SMS Import instead.")
                } else {
                    _toastMessage.tryEmit(
                        "Scanned ${summary.scannedCount} SMS: ${summary.importedCount} imported, ${summary.duplicateCount} duplicates skipped"
                    )
                }
            } finally {
                isScanningInboxFlow.value = false
            }
        }
    }

    fun updateFinancialProfile(income: BigDecimal, fixedExpenses: BigDecimal, protectedSavings: BigDecimal) {
        viewModelScope.launch {
            financeRepository.updateFinancialProfile(income, fixedExpenses, protectedSavings)
            _toastMessage.tryEmit("Budget allocation updated")
        }
    }

    fun updateCategoryBudget(categoryId: String, limitAmount: BigDecimal) {
        viewModelScope.launch {
            financeRepository.updateCategoryBudget(categoryId, limitAmount, YearMonth.now())
            _toastMessage.tryEmit(
                if (limitAmount <= BigDecimal.ZERO) "Category limit removed" else "Category limit updated"
            )
        }
    }

    fun createCategory(name: String) {
        viewModelScope.launch {
            when (val res = financeRepository.createCategory(name)) {
                is CategoryOperationResult.Success -> _toastMessage.tryEmit("Category '${name.trim()}' created")
                is CategoryOperationResult.ValidationError -> _toastMessage.tryEmit(res.message)
            }
        }
    }

    fun renameCategory(categoryId: String, newName: String) {
        viewModelScope.launch {
            when (val res = financeRepository.renameCategory(categoryId, newName)) {
                is CategoryOperationResult.Success -> _toastMessage.tryEmit("Category updated")
                is CategoryOperationResult.ValidationError -> _toastMessage.tryEmit(res.message)
            }
        }
    }

    fun deleteCategory(categoryId: String) {
        viewModelScope.launch {
            when (val res = financeRepository.deleteCategory(categoryId)) {
                is CategoryOperationResult.Success -> _toastMessage.tryEmit("Category deleted & items moved to Other")
                is CategoryOperationResult.ValidationError -> _toastMessage.tryEmit(res.message)
            }
        }
    }

    fun addRecurringPayment(title: String, amount: BigDecimal, categoryId: String, dayOfMonth: Int) {
        viewModelScope.launch {
            financeRepository.addRecurringPayment(title, amount, categoryId, dayOfMonth)
            _toastMessage.tryEmit("Upcoming bill reserved")
        }
    }

    fun recordRecurringBillPayment(billId: Long, partialAmount: BigDecimal? = null) {
        viewModelScope.launch {
            when (val res = financeRepository.recordRecurringBillPayment(billId, partialAmount, YearMonth.now())) {
                is BillPaymentResult.Paid -> {
                    val topAlert = res.triggeredAlerts.firstOrNull()
                    if (topAlert != null) {
                        _toastMessage.tryEmit("Bill recorded · Alert: ${topAlert.title}")
                    } else {
                        _toastMessage.tryEmit("Bill payment recorded in transactions")
                    }
                }
                is BillPaymentResult.Rejected -> _toastMessage.tryEmit(res.message)
            }
        }
    }

    fun unmarkRecurringBillPaid(billId: Long) {
        viewModelScope.launch {
            financeRepository.unmarkRecurringBillPaid(billId, YearMonth.now())
            _toastMessage.tryEmit("Bill payment reversed for this month")
        }
    }

    fun toggleRecurringPaid(id: Long, isPaid: Boolean) {
        if (isPaid) {
            recordRecurringBillPayment(id, null)
        } else {
            unmarkRecurringBillPaid(id)
        }
    }

    fun deleteRecurringPayment(id: Long) {
        viewModelScope.launch {
            financeRepository.deleteRecurringPayment(id)
            _toastMessage.tryEmit("Recurring bill removed")
        }
    }

    fun simulateWhatIf(title: String, amount: BigDecimal) {
        val currentSummary = uiState.value.summary ?: return
        val goals = uiState.value.goals
        whatIfResultFlow.value = dynamicBudgetEngine.simulateWhatIfPurchase(
            summary = currentSummary,
            purchaseTitle = title,
            purchaseAmount = amount,
            goals = goals
        )
    }

    fun clearWhatIf() {
        whatIfResultFlow.value = null
    }

    fun upsertGoal(
        id: Long,
        name: String,
        targetAmount: BigDecimal,
        currentAmount: BigDecimal,
        targetDateMillis: Long,
        iconKey: String
    ) {
        viewModelScope.launch {
            when (val res = financeRepository.upsertSavingsGoalWithValidation(
                SavingsGoalEntity(
                    id = id,
                    name = name.trim(),
                    targetAmount = targetAmount,
                    currentAmount = currentAmount,
                    targetDateEpochMillis = targetDateMillis,
                    iconKey = iconKey
                )
            )) {
                is GoalOperationResult.Success -> _toastMessage.tryEmit("Savings goal saved")
                is GoalOperationResult.ValidationError -> _toastMessage.tryEmit(res.message)
            }
        }
    }

    fun contributeToGoal(goal: SavingsGoalEntity, amount: BigDecimal, note: String?) {
        viewModelScope.launch {
            when (val res = financeRepository.contributeToGoalWithValidation(goal.id, amount, note)) {
                is GoalOperationResult.Success -> _toastMessage.tryEmit("Added contribution to ${goal.name}")
                is GoalOperationResult.ValidationError -> _toastMessage.tryEmit(res.message)
            }
        }
    }

    fun updateGoalContribution(contributionId: Long, newAmount: BigDecimal, newNote: String?) {
        viewModelScope.launch {
            when (val res = financeRepository.updateGoalContribution(contributionId, newAmount, newNote)) {
                is GoalOperationResult.Success -> _toastMessage.tryEmit("Contribution updated")
                is GoalOperationResult.ValidationError -> _toastMessage.tryEmit(res.message)
            }
        }
    }

    fun deleteGoalContribution(contributionId: Long) {
        viewModelScope.launch {
            when (val res = financeRepository.deleteGoalContribution(contributionId)) {
                is GoalOperationResult.Success -> _toastMessage.tryEmit("Contribution removed")
                is GoalOperationResult.ValidationError -> _toastMessage.tryEmit(res.message)
            }
        }
    }

    fun deleteGoal(goalId: Long) {
        viewModelScope.launch {
            financeRepository.deleteSavingsGoal(goalId)
            _toastMessage.tryEmit("Savings goal deleted")
        }
    }

    fun addNoSpendChallenge(title: String, targetDays: Int) {
        viewModelScope.launch {
            financeRepository.addNoSpendChallenge(title, targetDays)
            _toastMessage.tryEmit("No-Spend Challenge started")
        }
    }

    fun checkInNoSpendChallenge(challenge: NoSpendChallengeEntity) {
        viewModelScope.launch {
            financeRepository.checkInNoSpendChallenge(challenge)
            _toastMessage.tryEmit("Checked in for today!")
        }
    }

    fun toggleSpendingRule(rule: SpendingRuleEntity) {
        viewModelScope.launch {
            financeRepository.toggleSpendingRule(rule)
        }
    }

    fun markAllNotificationsRead() {
        viewModelScope.launch {
            financeRepository.markAllNotificationsRead()
        }
    }

    fun deleteAllSmsTransactions() {
        viewModelScope.launch {
            financeRepository.deleteAllSmsTransactions()
            _toastMessage.tryEmit("All SMS-imported transactions & audit logs deleted")
        }
    }

    fun clearAllUserData() {
        viewModelScope.launch {
            financeRepository.clearAllUserData()
            _toastMessage.tryEmit("All financial records cleared")
        }
    }

    private data class CoreBundle(
        val user: UserEntity?,
        val profile: FinancialProfileEntity?,
        val monthlyBudget: BudgetEntity?,
        val transactions: List<TransactionEntity>,
        val categories: List<CategoryEntity>,
        val categoryBudgets: List<CategoryBudgetEntity>
    )

    private data class PlanningEntitiesBundle(
        val goals: List<SavingsGoalEntity>,
        val contributions: List<SavingsContributionEntity>,
        val recurring: List<RecurringPaymentEntity>,
        val billPayments: List<RecurringBillPaymentEntity>
    )

    private data class PlanningBundle(
        val goals: List<SavingsGoalEntity>,
        val contributions: List<SavingsContributionEntity>,
        val recurring: List<RecurringPaymentEntity>,
        val billPayments: List<RecurringBillPaymentEntity>,
        val rules: List<SpendingRuleEntity>,
        val challenges: List<NoSpendChallengeEntity>,
        val preferences: UserPreferences
    )

    private data class AuxBundle(
        val notifications: List<NotificationEntity>,
        val unreadCount: Int,
        val smsLogs: List<ProcessedSmsEntity>,
        val senders: List<SmsSenderEntity>
    )
}
