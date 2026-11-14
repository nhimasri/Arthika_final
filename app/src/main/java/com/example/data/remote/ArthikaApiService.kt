package com.example.data.remote

import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class RemoteTransactionDto(
    val clientId: Long,
    val title: String,
    val amount: String,
    val type: String,
    val categoryId: String,
    val timestamp: Long,
    val paymentMethod: String,
    val source: String,
    val status: String,
    val isTransfer: Boolean
)

@JsonClass(generateAdapter = true)
data class RemoteBudgetSyncDto(
    val yearMonth: String,
    val monthlyIncome: String,
    val fixedExpenses: String,
    val protectedSavings: String,
    val flexibleBudget: String
)

@JsonClass(generateAdapter = true)
data class SyncResponseDto(
    val syncedCount: Int,
    val serverTimestamp: Long
)

/**
 * Retrofit contract prepared for future Node.js + Express.js + PostgreSQL backend synchronization.
 * Note: Raw SMS text is NEVER part of any remote payload by design.
 */
interface ArthikaApiService {
    @POST("api/v1/transactions/sync")
    suspend fun syncTransactions(
        @Body transactions: List<RemoteTransactionDto>
    ): Response<SyncResponseDto>

    @POST("api/v1/budgets/sync")
    suspend fun syncBudget(
        @Body budget: RemoteBudgetSyncDto
    ): Response<SyncResponseDto>

    @GET("api/v1/summary")
    suspend fun getMonthlySummary(
        @Query("yearMonth") yearMonth: String
    ): Response<RemoteBudgetSyncDto>
}
