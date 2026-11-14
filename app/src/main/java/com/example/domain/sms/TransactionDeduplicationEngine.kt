package com.example.domain.sms

import com.example.data.local.dao.SmsAuditDao
import com.example.data.local.dao.TransactionDao
import com.example.data.local.entity.TransactionEntity
import java.math.RoundingMode
import java.security.MessageDigest
import java.util.Locale

class TransactionDeduplicationEngine {

    fun computeFingerprint(parsed: ParsedSmsTransaction): String {
        val normalizedAmount = parsed.amount.setScale(2, RoundingMode.HALF_UP).toPlainString()
        val timeBucket = parsed.timestampMillis / (5 * 60 * 1000L) // 5-minute window bucket
        val keyParts = listOf(
            parsed.bankName.lowercase(Locale.ENGLISH),
            parsed.type.name,
            normalizedAmount,
            parsed.accountRef.orEmpty().lowercase(Locale.ENGLISH),
            parsed.referenceId.orEmpty().uppercase(Locale.ENGLISH),
            if (parsed.referenceId != null) "REF" else timeBucket.toString()
        ).joinToString("|")

        val digest = MessageDigest.getInstance("SHA-256").digest(keyParts.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(32)
    }

    suspend fun findDuplicate(
        parsed: ParsedSmsTransaction,
        fingerprint: String,
        transactionDao: TransactionDao,
        smsAuditDao: SmsAuditDao
    ): DuplicateCheckResult {
        // 1. Exact fingerprint check in processed_sms audit log or transactions table
        if (smsAuditDao.findProcessedByFingerprint(fingerprint) != null ||
            transactionDao.findByFingerprint(fingerprint) != null
        ) {
            return DuplicateCheckResult.Duplicate("Matching message fingerprint already recorded")
        }

        // 2. Exact transaction reference ID + amount + type check
        val ref = parsed.referenceId
        if (!ref.isNullOrBlank()) {
            val byRef = transactionDao.findByReference(ref, parsed.amount, parsed.type)
            if (byRef != null) {
                return DuplicateCheckResult.Duplicate("Matching reference ID ($ref) already exists")
            }
        }

        // 3. Multi-signal window check (±10 minutes, same amount + type + account/bank)
        val windowMillis = 10 * 60 * 1000L
        val nearby = transactionDao.findPotentialDuplicatesInWindow(
            amount = parsed.amount,
            type = parsed.type,
            minTs = parsed.timestampMillis - windowMillis,
            maxTs = parsed.timestampMillis + windowMillis
        )

        val fuzzyMatch = nearby.firstOrNull { existing ->
            val sameAccount = !parsed.accountRef.isNullOrBlank() &&
                existing.accountRef == parsed.accountRef
            val sameSender = existing.bankSender != null &&
                existing.bankSender.equals(parsed.senderHeader, ignoreCase = true)
            val sameMerchant = !parsed.merchantName.isNullOrBlank() &&
                existing.merchantName?.equals(parsed.merchantName, ignoreCase = true) == true
            sameAccount || sameSender || sameMerchant
        }

        if (fuzzyMatch != null) {
            return DuplicateCheckResult.Duplicate(
                "Duplicate alert within 10-minute window for ${parsed.amount} (${fuzzyMatch.title})"
            )
        }

        return DuplicateCheckResult.Unique(fingerprint)
    }

    fun isSameTransaction(a: ParsedSmsTransaction, b: TransactionEntity): Boolean {
        if (a.type != b.type || a.amount.compareTo(b.amount) != 0) return false
        if (!a.referenceId.isNullOrBlank() && a.referenceId == b.referenceId) return true
        val diff = kotlin.math.abs(a.timestampMillis - b.timestamp)
        val sameAccount = !a.accountRef.isNullOrBlank() && a.accountRef == b.accountRef
        return diff <= 10 * 60 * 1000L && sameAccount
    }
}

sealed class DuplicateCheckResult {
    data class Unique(val fingerprint: String) : DuplicateCheckResult()
    data class Duplicate(val reason: String) : DuplicateCheckResult()
}
