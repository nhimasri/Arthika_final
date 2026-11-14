package com.example.domain.sms

import com.example.domain.model.PaymentMethod
import com.example.domain.model.SmsProcessDecision
import com.example.domain.model.TransactionStatus
import com.example.domain.model.TransactionType
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ParsedSmsTransaction(
    val title: String,
    val merchantName: String?,
    val bankName: String,
    val senderHeader: String,
    val type: TransactionType,
    val amount: BigDecimal,
    val accountRef: String?,
    val referenceId: String?,
    val availableBalance: BigDecimal?,
    val timestampMillis: Long,
    val paymentMethod: PaymentMethod,
    val suggestedCategoryId: String,
    val isTransfer: Boolean,
    val status: TransactionStatus,
    val isTrustedSender: Boolean
)

sealed class SmsParseResult {
    data class Valid(val transaction: ParsedSmsTransaction) : SmsParseResult()
    data class Rejected(val decision: SmsProcessDecision, val reason: String) : SmsParseResult()
}

class SmsTransactionParser(
    private val trustedSenderMap: Map<String, String> = DEFAULT_TRUSTED_SENDERS
) {

    fun parse(
        rawSender: String,
        messageBody: String,
        receivedTimestampMillis: Long = System.currentTimeMillis()
    ): SmsParseResult {
        val body = messageBody.trim()
        if (body.isBlank()) {
            return SmsParseResult.Rejected(SmsProcessDecision.IGNORED_INSUFFICIENT, "Empty message")
        }

        val normalizedSender = normalizeSenderHeader(rawSender)
        val lower = body.lowercase(Locale.ENGLISH)

        // 1. Reject OTP / Authentication messages immediately (never store or log)
        if (OTP_KEYWORDS.any { lower.contains(it) }) {
            return SmsParseResult.Rejected(SmsProcessDecision.REJECTED_OTP, "OTP or authentication alert")
        }

        // 2. Reject personal 10-digit phone numbers to avoid false positives from casual chats
        if (isPersonalPhoneNumber(rawSender)) {
            return SmsParseResult.Rejected(
                SmsProcessDecision.IGNORED_INSUFFICIENT,
                "Personal phone number sender ignored"
            )
        }

        // 3. Reject promotional / marketing / loan spam messages
        if (PROMO_KEYWORDS.any { lower.contains(it) }) {
            return SmsParseResult.Rejected(SmsProcessDecision.REJECTED_PROMO, "Promotional message")
        }

        // 4. Reject non-completed statement/reminder alerts (due bills, credit limits, min balance)
        if (NON_TRANSACTION_PHRASES.any { lower.contains(it) } &&
            !hasExplicitTransactionVerb(lower)
        ) {
            return SmsParseResult.Rejected(
                SmsProcessDecision.IGNORED_INSUFFICIENT,
                "Informational reminder, not a completed transaction"
            )
        }

        val trustedEntry = trustedSenderMap.entries.firstOrNull { (code, _) ->
            normalizedSender.contains(code)
        }
        val isTrustedSender = trustedEntry != null

        // If sender is unknown and has zero banking context, reject safely
        if (!isTrustedSender && !hasBankingContext(lower)) {
            return SmsParseResult.Rejected(
                SmsProcessDecision.IGNORED_INSUFFICIENT,
                "Unknown non-financial sender"
            )
        }

        // 5. Determine DEBIT vs CREDIT and check for directional or amount ambiguity
        val typeResult = detectTransactionTypeWithAmbiguity(lower)
            ?: return SmsParseResult.Rejected(
                SmsProcessDecision.IGNORED_INSUFFICIENT,
                "Could not determine credit or debit direction"
            )
        val type = typeResult.first
        val hasDirectionAmbiguity = typeResult.second

        // 6. Extract primary transaction monetary amount (ignoring available balance suffix)
        val bodyWithoutBal = body.replace(BALANCE_CLAUSE_REGEX, " ")
        val nonBalanceAmounts = AMOUNT_REGEX.findAll(bodyWithoutBal)
            .mapNotNull { runCatching { BigDecimal(it.groupValues[1].replace(",", "").trim()) }.getOrNull() }
            .filter { it > BigDecimal.ZERO }
            .distinctBy { it.stripTrailingZeros() }
            .toList()
        val hasMultipleAmounts = nonBalanceAmounts.size > 1

        val amount = nonBalanceAmounts.firstOrNull() ?: extractTransactionAmount(body)
            ?: return SmsParseResult.Rejected(
                SmsProcessDecision.IGNORED_INSUFFICIENT,
                "No valid monetary amount found"
            )

        if (amount <= BigDecimal.ZERO) {
            return SmsParseResult.Rejected(
                SmsProcessDecision.IGNORED_INSUFFICIENT,
                "Non-positive amount"
            )
        }

        val accountRef = extractAccountRef(body)
        val referenceId = extractReferenceId(body)
        val availableBalance = extractAvailableBalance(body)
        val paymentMethod = detectPaymentMethod(lower)
        val merchant = extractMerchantOrRecipient(body, type)
        val extractedDateMillis = extractDateMillis(body, receivedTimestampMillis)
        val isTransfer = detectOwnAccountTransfer(lower)
        val bankName = trustedEntry?.value ?: inferBankNameFromBody(body) ?: normalizedSender.ifBlank { "Bank" }

        val categoryId = classifyCategory(
            lowerBody = lower,
            merchant = merchant,
            type = type,
            paymentMethod = paymentMethod
        )

        val isSalary = type == TransactionType.CREDIT && SALARY_KEYWORDS.any { lower.contains(it) }
        val isRefund = type == TransactionType.CREDIT && REFUND_KEYWORDS.any { lower.contains(it) }
        val isAtm = paymentMethod == PaymentMethod.CASH || lower.contains("atm")

        val title = when {
            isTransfer -> "Self transfer"
            isSalary -> "Salary credit"
            isRefund -> merchant?.let { "$it refund" } ?: "Refund"
            isAtm -> "ATM withdrawal"
            merchant != null -> merchant
            paymentMethod == PaymentMethod.UPI -> "UPI payment"
            type == TransactionType.CREDIT -> "Account credit"
            else -> "Unknown merchant"
        }

        val needsReview = !isTrustedSender ||
            hasDirectionAmbiguity ||
            hasMultipleAmounts ||
            (type == TransactionType.DEBIT && categoryId == "other" && merchant == null && !isAtm && !isTransfer) ||
            (merchant == null && !isSalary && !isRefund && !isAtm && !isTransfer)

        val status = if (needsReview) TransactionStatus.NEEDS_REVIEW else TransactionStatus.CONFIRMED

        return SmsParseResult.Valid(
            ParsedSmsTransaction(
                title = title,
                merchantName = merchant,
                bankName = bankName,
                senderHeader = normalizedSender.ifBlank { rawSender.take(12) },
                type = type,
                amount = amount,
                accountRef = accountRef,
                referenceId = referenceId,
                availableBalance = availableBalance,
                timestampMillis = extractedDateMillis,
                paymentMethod = paymentMethod,
                suggestedCategoryId = categoryId,
                isTransfer = isTransfer,
                status = status,
                isTrustedSender = isTrustedSender
            )
        )
    }

    private fun normalizeSenderHeader(raw: String): String {
        val cleaned = raw.trim().uppercase(Locale.ENGLISH)
        // Indian DLT headers look like AD-HDFCBK, VM-ICICIB, AX-SBIINB
        return cleaned.substringAfterLast('-').replace(Regex("[^A-Z0-9]"), "")
    }

    private fun isPersonalPhoneNumber(raw: String): Boolean {
        val digitsOnly = raw.filter { it.isDigit() }
        return digitsOnly.length >= 10 && raw.none { it.isLetter() }
    }

    private fun hasBankingContext(lower: String): Boolean {
        return BANKING_CONTEXT_KEYWORDS.any { lower.contains(it) }
    }

    private fun hasExplicitTransactionVerb(lower: String): Boolean {
        return DEBIT_KEYWORDS.any { lower.contains(it) } || CREDIT_KEYWORDS.any { lower.contains(it) }
    }

    private fun detectTransactionTypeWithAmbiguity(lower: String): Pair<TransactionType, Boolean>? {
        val sanitized = lower.replace("credit card", "card")
        val hasDebit = DEBIT_KEYWORDS.any { Regex("\\b${Regex.escape(it)}\\b").containsMatchIn(sanitized) }
        val hasCredit = CREDIT_KEYWORDS.any { Regex("\\b${Regex.escape(it)}\\b").containsMatchIn(sanitized) }

        return when {
            hasDebit && !hasCredit -> TransactionType.DEBIT to false
            hasCredit && !hasDebit -> TransactionType.CREDIT to false
            hasDebit && hasCredit -> {
                val debitIndex = DEBIT_KEYWORDS.mapNotNull {
                    Regex("\\b${Regex.escape(it)}\\b").find(sanitized)?.range?.first
                }.minOrNull()
                val creditIndex = CREDIT_KEYWORDS.mapNotNull {
                    Regex("\\b${Regex.escape(it)}\\b").find(sanitized)?.range?.first
                }.minOrNull()
                when {
                    debitIndex != null && (creditIndex == null || debitIndex < creditIndex) ->
                        TransactionType.DEBIT to true
                    creditIndex != null ->
                        TransactionType.CREDIT to true
                    else -> null
                }
            }
            else -> null
        }
    }

    private fun extractTransactionAmount(body: String): BigDecimal? {
        // Strip available balance clause first so we never capture balance as transaction amount
        val bodyWithoutBal = body.replace(BALANCE_CLAUSE_REGEX, " ")
        val match = AMOUNT_REGEX.find(bodyWithoutBal) ?: AMOUNT_REGEX.find(body) ?: return null
        val numericText = match.groupValues[1].replace(",", "").trim()
        return runCatching { BigDecimal(numericText) }.getOrNull()
    }

    private fun extractAvailableBalance(body: String): BigDecimal? {
        val balMatch = BALANCE_CLAUSE_REGEX.find(body) ?: return null
        val amountMatch = AMOUNT_REGEX.find(balMatch.value) ?: return null
        val numericText = amountMatch.groupValues[1].replace(",", "").trim()
        return runCatching { BigDecimal(numericText) }.getOrNull()
    }

    private fun extractAccountRef(body: String): String? {
        val match = ACCOUNT_REGEX.find(body) ?: return null
        val digits = match.groupValues[1].takeLast(4)
        return if (digits.length >= 3) "XX$digits" else null
    }

    private fun extractReferenceId(body: String): String? {
        val match = REFERENCE_REGEX.find(body) ?: return null
        return match.groupValues[1].trim().takeIf { it.length >= 6 }
    }

    private fun detectPaymentMethod(lower: String): PaymentMethod {
        return when {
            lower.contains("upi") || lower.contains("vpa") || lower.contains("@") -> PaymentMethod.UPI
            lower.contains("atm") || lower.contains("cash") -> PaymentMethod.CASH
            lower.contains("card") || lower.contains("pos") -> PaymentMethod.CARD
            lower.contains("neft") || lower.contains("imps") || lower.contains("rtgs") -> PaymentMethod.BANK_TRANSFER
            lower.contains("netbanking") || lower.contains("net banking") -> PaymentMethod.NET_BANKING
            lower.contains("wallet") || lower.contains("paytm wallet") -> PaymentMethod.WALLET
            else -> PaymentMethod.BANK_TRANSFER
        }
    }

    private fun extractMerchantOrRecipient(body: String, type: TransactionType): String? {
        for (regex in MERCHANT_REGEXES) {
            val match = regex.find(body)
            if (match != null) {
                val raw = match.groupValues[1]
                    .replace(Regex("\\s+"), " ")
                    .trim(' ', '.', ',', '-', ':', ';')
                if (raw.length in 2..40 &&
                    !raw.lowercase(Locale.ENGLISH).startsWith("your ") &&
                    !raw.lowercase(Locale.ENGLISH).startsWith("rs") &&
                    !raw.lowercase(Locale.ENGLISH).startsWith("inr")
                ) {
                    return raw.split(" ").joinToString(" ") { word ->
                        if (word.all { it.isUpperCase() } && word.length <= 4) word
                        else word.lowercase(Locale.ENGLISH).replaceFirstChar { it.titlecase(Locale.ENGLISH) }
                    }
                }
            }
        }
        if (type == TransactionType.CREDIT && body.lowercase(Locale.ENGLISH).contains("refund")) {
            return "Merchant Refund"
        }
        return null
    }

    private fun extractDateMillis(body: String, fallbackMillis: Long): Long {
        val zone = ZoneId.systemDefault()
        val match = DATE_REGEX.find(body) ?: return fallbackMillis
        val dateStr = match.value.trim()
        val formatters = listOf(
            DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d-MMM-yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd-MMM-yy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd/MM/yy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd-MM-yy", Locale.ENGLISH)
        )
        for (fmt in formatters) {
            val parsed = runCatching { LocalDate.parse(dateStr, fmt) }.getOrNull()
            if (parsed != null) {
                val fallbackTime = LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(fallbackMillis),
                    zone
                ).toLocalTime()
                return parsed.atTime(fallbackTime).atZone(zone).toInstant().toEpochMilli()
            }
        }
        return fallbackMillis
    }

    private fun detectOwnAccountTransfer(lower: String): Boolean {
        return TRANSFER_KEYWORDS.any { lower.contains(it) }
    }

    private fun inferBankNameFromBody(body: String): String? {
        val lower = body.lowercase(Locale.ENGLISH)
        return when {
            lower.contains("hdfc") -> "HDFC Bank"
            lower.contains("icici") -> "ICICI Bank"
            lower.contains("sbi") -> "State Bank of India"
            lower.contains("axis") -> "Axis Bank"
            lower.contains("kotak") -> "Kotak Bank"
            lower.contains("idfc") -> "IDFC First Bank"
            lower.contains("paytm") -> "Paytm"
            lower.contains("phonepe") -> "PhonePe"
            lower.contains("gpay") || lower.contains("google pay") -> "Google Pay"
            else -> null
        }
    }

    private fun classifyCategory(
        lowerBody: String,
        merchant: String?,
        type: TransactionType,
        paymentMethod: PaymentMethod
    ): String {
        if (type == TransactionType.CREDIT) return "other"
        val combined = "$lowerBody ${merchant.orEmpty().lowercase(Locale.ENGLISH)}"
        return when {
            FOOD_KEYWORDS.any { combined.contains(it) } -> "food"
            TRANSPORT_KEYWORDS.any { combined.contains(it) } -> "transportation"
            SHOPPING_KEYWORDS.any { combined.contains(it) } -> "shopping"
            BILLS_KEYWORDS.any { combined.contains(it) } -> "bills"
            ENTERTAINMENT_KEYWORDS.any { combined.contains(it) } -> "entertainment"
            HEALTHCARE_KEYWORDS.any { combined.contains(it) } -> "healthcare"
            EDUCATION_KEYWORDS.any { combined.contains(it) } -> "education"
            paymentMethod == PaymentMethod.CASH -> "other"
            else -> "other"
        }
    }

    companion object {
        val DEFAULT_TRUSTED_SENDERS = mapOf(
            "HDFCBK" to "HDFC Bank",
            "ICICIB" to "ICICI Bank",
            "SBIINB" to "State Bank of India",
            "SBIPSG" to "SBI UPI",
            "AXISBK" to "Axis Bank",
            "KOTAKB" to "Kotak Mahindra Bank",
            "INDUSB" to "IndusInd Bank",
            "YESBNK" to "Yes Bank",
            "IDFCFB" to "IDFC First Bank",
            "PNBSMS" to "Punjab National Bank",
            "BOBSMS" to "Bank of Baroda",
            "FEDBNK" to "Federal Bank",
            "CANBNK" to "Canara Bank",
            "PAYTM" to "Paytm",
            "GPAY" to "Google Pay",
            "PHONEPE" to "PhonePe"
        )

        private val OTP_KEYWORDS = listOf(
            "otp", "one time password", "one-time password", "verification code",
            "do not share", "never share", "cvv", "mpin", "login code", "2fa", "passcode"
        )

        private val PROMO_KEYWORDS = listOf(
            "pre-approved", "pre approved", "apply now", "claim now", "click here",
            "win ", "congratulations", "% off", "discount", "cashback up to",
            "flat off", "limited period", "loan offer", "emi starting", "shop now", "exclusive offer"
        )

        private val NON_TRANSACTION_PHRASES = listOf(
            "is due on", "due date", "minimum balance", "credit limit", "statement generated",
            "payment reminder", "outstanding balance is", "total amount due"
        )

        private val BANKING_CONTEXT_KEYWORDS = listOf(
            "a/c", "acct", "account", "upi", "bank", "card", "neft", "imps", "rtgs", "vpa", "avl bal"
        )

        private val DEBIT_KEYWORDS = listOf(
            "debited", "spent", "paid", "withdrawn", "deducted", "purchase", "charged", "sent", "dr"
        )

        private val CREDIT_KEYWORDS = listOf(
            "credited", "received", "deposited", "refund", "reversed", "salary", "cr"
        )

        private val SALARY_KEYWORDS = listOf("salary", "payroll", "stipend", "wages")
        private val REFUND_KEYWORDS = listOf("refund", "reversed", "reversal", "cashback")
        private val TRANSFER_KEYWORDS = listOf(
            "self transfer", "own account", "transfer between", "to self", "own a/c"
        )

        private val AMOUNT_REGEX = Regex(
            pattern = "(?:rs\\.?|inr|₹)\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)",
            option = RegexOption.IGNORE_CASE
        )

        private val BALANCE_CLAUSE_REGEX = Regex(
            pattern = "(?:avl\\.?\\s*bal(?:ance)?|available\\s*bal(?:ance)?|bal(?:ance)?\\s*:?)\\s*(?:is\\s*)?(?:rs\\.?|inr|₹)?\\s*[0-9][0-9,]*(?:\\.[0-9]{1,2})?",
            option = RegexOption.IGNORE_CASE
        )

        private val ACCOUNT_REGEX = Regex(
            pattern = "(?:a/c|acct|account|card)\\s*(?:no\\.?|ending|number)?\\s*(?:with)?\\s*[xX*#]*([0-9]{3,4})\\b",
            option = RegexOption.IGNORE_CASE
        )

        private val REFERENCE_REGEX = Regex(
            pattern = "(?:upi\\s*ref(?:\\s*no)?|ref(?:\\s*no|#)?|utr|txn\\s*(?:id|no))\\s*[:\\-#]?\\s*([A-Za-z0-9]{6,22})",
            option = RegexOption.IGNORE_CASE
        )

        private val DATE_REGEX = Regex(
            pattern = "\\b(\\d{1,2}[-/](?:[A-Za-z]{3}|\\d{1,2})[-/]\\d{2,4})\\b"
        )

        private val MERCHANT_REGEXES = listOf(
            Regex("(?:at|to|towards)\\s+([A-Za-z0-9][A-Za-z0-9 &._'-]{1,35}?)(?=\\s+(?:on|via|ref|upi|avl|bal|from|using|\\d{2}[-/])|$)", RegexOption.IGNORE_CASE),
            Regex("(?:vpa|info[:/])\\s*([A-Za-z0-9._-]+@[A-Za-z0-9]+|[A-Za-z0-9 &._'-]{2,30})", RegexOption.IGNORE_CASE),
            Regex("by\\s+([A-Za-z0-9][A-Za-z0-9 &._'-]{2,30}?)(?=\\s+(?:on|ref|via|$))", RegexOption.IGNORE_CASE)
        )

        private val FOOD_KEYWORDS = listOf(
            "swiggy", "zomato", "blinkit", "zepto", "bigbasket", "domino", "pizza",
            "starbucks", "cafe", "coffee", "restaurant", "bakery", "food", "dining", "burger", "kfc"
        )
        private val TRANSPORT_KEYWORDS = listOf(
            "uber", "ola", "rapido", "metro", "irctc", "petrol", "fuel", "diesel",
            "shell", "hpcl", "bpcl", "indian oil", "fastag", "cab", "bus", "flight", "indigo"
        )
        private val SHOPPING_KEYWORDS = listOf(
            "amazon", "flipkart", "myntra", "ajio", "nykaa", "meesho", "decathlon",
            "reliance", "trends", "mall", "store", "mart", "retail"
        )
        private val BILLS_KEYWORDS = listOf(
            "electricity", "bescom", "tata power", "adani", "airtel", "jio", "vodafone",
            "broadband", "wifi", "gas", "water", "recharge", "dth", "billpay", "rent"
        )
        private val ENTERTAINMENT_KEYWORDS = listOf(
            "netflix", "spotify", "bookmyshow", "pvr", "inox", "hotstar", "prime video",
            "youtube", "cinema", "movie", "steam", "playstation"
        )
        private val HEALTHCARE_KEYWORDS = listOf(
            "apollo", "pharmeasy", "1mg", "practo", "hospital", "clinic", "pharmacy", "medical", "medplus"
        )
        private val EDUCATION_KEYWORDS = listOf(
            "udemy", "coursera", "tuition", "college", "university", "school", "book", "exam", "unacademy"
        )
    }
}
