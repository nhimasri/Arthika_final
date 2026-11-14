package com.example.data.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.example.data.local.ArthikaDatabase
import com.example.data.local.UserPreferencesDataStore
import com.example.data.local.entity.NotificationEntity
import com.example.data.local.entity.ProcessedSmsEntity
import com.example.data.local.entity.SmsSenderEntity
import com.example.data.local.entity.TransactionEntity
import com.example.domain.budget.BudgetAlertEvaluator
import com.example.domain.model.SmsProcessDecision
import com.example.domain.model.SpendingAlert
import com.example.domain.model.SpendingGuardLevel
import com.example.domain.model.TransactionSource
import com.example.domain.model.TransactionStatus
import com.example.domain.model.TransactionType
import com.example.domain.sms.DuplicateCheckResult
import com.example.domain.sms.SmsParseResult
import com.example.domain.sms.SmsTransactionParser
import com.example.domain.sms.TransactionDeduplicationEngine
import com.example.utils.CurrencyFormatter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

sealed class SmsImportOutcome {
    data class Imported(
        val transaction: TransactionEntity,
        val triggeredAlerts: List<SpendingAlert> = emptyList()
    ) : SmsImportOutcome()
    data class Duplicate(val reason: String) : SmsImportOutcome()
    data class Rejected(val decision: SmsProcessDecision, val reason: String) : SmsImportOutcome()
}

class SmsTransactionRepository(
    private val db: ArthikaDatabase,
    private val preferencesDataStore: UserPreferencesDataStore,
    private val deduplicationEngine: TransactionDeduplicationEngine,
    private val alertEvaluator: BudgetAlertEvaluator? = null
) {
    val processedSmsAuditFlow: Flow<List<ProcessedSmsEntity>> = db.smsAuditDao().observeRecentProcessedSms()
    val trustedSendersFlow: Flow<List<SmsSenderEntity>> = db.smsAuditDao().observeSenders()

    suspend fun processIncomingBroadcastSms(
        rawSender: String,
        messageBody: String,
        receivedAtMillis: Long
    ): SmsImportOutcome {
        val prefs = preferencesDataStore.preferencesFlow.first()
        if (!prefs.smsDetectionEnabled) {
            return SmsImportOutcome.Rejected(
                SmsProcessDecision.IGNORED_DISABLED,
                "Automatic SMS detection is disabled"
            )
        }
        return processSmsInternal(rawSender, messageBody, receivedAtMillis, postNotification = true)
    }

    suspend fun importSmsManuallyOrFromInbox(
        rawSender: String,
        messageBody: String,
        receivedAtMillis: Long,
        postNotification: Boolean = false
    ): SmsImportOutcome {
        return processSmsInternal(rawSender, messageBody, receivedAtMillis, postNotification)
    }

    private suspend fun processSmsInternal(
        rawSender: String,
        messageBody: String,
        receivedAtMillis: Long,
        postNotification: Boolean
    ): SmsImportOutcome {
        val sendersFromDb = db.smsAuditDao().getSendersOnce()
            .filter { it.isTrusted }
            .associate { it.senderPattern to it.bankDisplayName }
        val parser = SmsTransactionParser(
            trustedSenderMap = if (sendersFromDb.isNotEmpty()) sendersFromDb else SmsTransactionParser.DEFAULT_TRUSTED_SENDERS
        )

        val parseResult = parser.parse(rawSender, messageBody, receivedAtMillis)
        return when (parseResult) {
            is SmsParseResult.Rejected -> {
                // Only record non-OTP audit entry (never store OTPs or raw SMS body!)
                if (parseResult.decision != SmsProcessDecision.REJECTED_OTP &&
                    parseResult.decision != SmsProcessDecision.IGNORED_INSUFFICIENT
                ) {
                    db.smsAuditDao().insertProcessedSms(
                        ProcessedSmsEntity(
                            messageFingerprint = "rej_${rawSender.take(8)}_${receivedAtMillis}_${messageBody.length}",
                            senderHeader = rawSender.take(16),
                            transactionTimestamp = receivedAtMillis,
                            decision = parseResult.decision
                        )
                    )
                }
                SmsImportOutcome.Rejected(parseResult.decision, parseResult.reason)
            }

            is SmsParseResult.Valid -> {
                val parsed = parseResult.transaction
                val fingerprint = deduplicationEngine.computeFingerprint(parsed)
                val persistedOutcome: SmsImportOutcome = try {
                    db.withTransaction {
                        val dupCheck = deduplicationEngine.findDuplicate(
                            parsed = parsed,
                            fingerprint = fingerprint,
                            transactionDao = db.transactionDao(),
                            smsAuditDao = db.smsAuditDao()
                        )

                        when (dupCheck) {
                            is DuplicateCheckResult.Duplicate -> {
                                db.smsAuditDao().insertProcessedSms(
                                    ProcessedSmsEntity(
                                        messageFingerprint = "${fingerprint}_dup_${receivedAtMillis}",
                                        senderHeader = parsed.senderHeader,
                                        maskedAccount = parsed.accountRef,
                                        amount = parsed.amount,
                                        transactionType = parsed.type,
                                        referenceId = parsed.referenceId,
                                        transactionTimestamp = parsed.timestampMillis,
                                        decision = SmsProcessDecision.DUPLICATE
                                    )
                                )
                                SmsImportOutcome.Duplicate(dupCheck.reason)
                            }

                            is DuplicateCheckResult.Unique -> {
                                val entity = TransactionEntity(
                                    title = parsed.title,
                                    merchantName = parsed.merchantName,
                                    amount = parsed.amount,
                                    type = parsed.type,
                                    categoryId = parsed.suggestedCategoryId,
                                    timestamp = parsed.timestampMillis,
                                    paymentMethod = parsed.paymentMethod,
                                    source = TransactionSource.SMS,
                                    status = parsed.status,
                                    isTransfer = parsed.isTransfer,
                                    accountRef = parsed.accountRef,
                                    bankSender = parsed.senderHeader,
                                    referenceId = parsed.referenceId,
                                    fingerprint = fingerprint,
                                    availableBalance = parsed.availableBalance
                                )
                                val rowId = db.transactionDao().insertTransaction(entity)
                                val saved = entity.copy(id = rowId)

                                db.smsAuditDao().insertProcessedSms(
                                    ProcessedSmsEntity(
                                        messageFingerprint = fingerprint,
                                        senderHeader = parsed.senderHeader,
                                        maskedAccount = parsed.accountRef,
                                        amount = parsed.amount,
                                        transactionType = parsed.type,
                                        referenceId = parsed.referenceId,
                                        transactionTimestamp = parsed.timestampMillis,
                                        decision = SmsProcessDecision.IMPORTED
                                    )
                                )

                                if (postNotification) {
                                    val dirLabel = if (parsed.type == TransactionType.DEBIT) "Debit" else "Credit"
                                    val needsReviewNote = if (parsed.status == TransactionStatus.NEEDS_REVIEW) {
                                        " Tap to confirm or reject."
                                    } else ""
                                    db.notificationDao().insert(
                                        NotificationEntity(
                                            title = "$dirLabel ${CurrencyFormatter.formatInrExact(parsed.amount)} detected",
                                            message = "${parsed.title} via ${parsed.bankName}.$needsReviewNote",
                                            level = if (parsed.status == TransactionStatus.NEEDS_REVIEW) {
                                                SpendingGuardLevel.ORANGE
                                            } else {
                                                SpendingGuardLevel.GREEN
                                            },
                                            relatedTransactionId = rowId
                                        )
                                    )
                                }

                                SmsImportOutcome.Imported(saved, emptyList())
                            }
                        }
                    }
                } catch (_: SQLiteConstraintException) {
                    SmsImportOutcome.Duplicate("Duplicate transaction rejected by database constraint")
                }

                if (persistedOutcome is SmsImportOutcome.Imported) {
                    val saved = persistedOutcome.transaction
                    // CRITICAL: Pending (NEEDS_REVIEW) SMS must NOT affect spending calculations or trigger overspend alerts until confirmed.
                    val triggeredAlerts = if (postNotification && saved.status == TransactionStatus.CONFIRMED) {
                        alertEvaluator?.evaluateAfterTransaction(saved, postSystemNotification = true).orEmpty()
                    } else {
                        emptyList()
                    }
                    SmsImportOutcome.Imported(saved, triggeredAlerts)
                } else {
                    persistedOutcome
                }
            }
        }
    }
}
