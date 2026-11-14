package com.example.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.presentation.MainViewModel

class ArthikaViewModelFactory(
    private val container: ArthikaAppContainer
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            return MainViewModel(
                financeRepository = container.financeRepository,
                smsTransactionRepository = container.smsTransactionRepository,
                smsInboxScanner = container.smsInboxScanner,
                preferencesDataStore = container.preferencesDataStore,
                dynamicBudgetEngine = container.dynamicBudgetEngine,
                spendingGuardEngine = container.spendingGuardEngine,
                financialInsightEngine = container.financialInsightEngine
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
