package com.example.di

import android.content.Context
import com.example.data.local.ArthikaDatabase
import com.example.data.local.DatabaseSeeder
import com.example.data.local.UserPreferencesDataStore
import com.example.data.repository.FinanceRepository
import com.example.data.repository.SmsTransactionRepository
import com.example.data.sms.SmsInboxScanner
import com.example.domain.budget.BudgetAlertEvaluator
import com.example.domain.budget.DynamicBudgetEngine
import com.example.domain.budget.FinancialAlertManager
import com.example.domain.budget.FinancialInsightEngine
import com.example.domain.budget.SpendingGuardEngine
import com.example.domain.sms.TransactionDeduplicationEngine

class ArthikaAppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: ArthikaDatabase by lazy { ArthikaDatabase.build(appContext) }
    val seeder: DatabaseSeeder by lazy { DatabaseSeeder(database) }
    val preferencesDataStore: UserPreferencesDataStore by lazy { UserPreferencesDataStore(appContext) }

    val dynamicBudgetEngine: DynamicBudgetEngine by lazy { DynamicBudgetEngine() }
    val spendingGuardEngine: SpendingGuardEngine by lazy { SpendingGuardEngine() }
    val financialInsightEngine: FinancialInsightEngine by lazy { FinancialInsightEngine() }
    val deduplicationEngine: TransactionDeduplicationEngine by lazy { TransactionDeduplicationEngine() }

    val financialAlertManager: FinancialAlertManager by lazy {
        FinancialAlertManager(
            context = appContext,
            db = database,
            preferencesDataStore = preferencesDataStore,
            dynamicBudgetEngine = dynamicBudgetEngine,
            spendingGuardEngine = spendingGuardEngine
        )
    }

    val budgetAlertEvaluator: BudgetAlertEvaluator by lazy {
        BudgetAlertEvaluator(
            context = appContext,
            db = database,
            preferencesDataStore = preferencesDataStore,
            dynamicBudgetEngine = dynamicBudgetEngine,
            spendingGuardEngine = spendingGuardEngine,
            financialAlertManager = financialAlertManager
        )
    }

    val financeRepository: FinanceRepository by lazy {
        FinanceRepository(database, seeder, budgetAlertEvaluator, financialAlertManager)
    }

    val smsTransactionRepository: SmsTransactionRepository by lazy {
        SmsTransactionRepository(database, preferencesDataStore, deduplicationEngine, budgetAlertEvaluator)
    }

    val smsInboxScanner: SmsInboxScanner by lazy {
        SmsInboxScanner(appContext, smsTransactionRepository)
    }
}
