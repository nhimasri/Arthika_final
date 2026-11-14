package com.example.domain.sms

import com.example.domain.model.PaymentMethod
import com.example.domain.model.SmsProcessDecision
import com.example.domain.model.TransactionStatus
import com.example.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class SmsTransactionParserTest {

    private val parser = SmsTransactionParser()
    private val dedup = TransactionDeduplicationEngine()

    @Test
    fun `parses standard bank debit SMS`() {
        val result = parser.parse(
            rawSender = "AD-HDFCBK",
            messageBody = "Your A/c XX1234 has been debited by Rs. 500.00 on 12-Nov-2026 at Swiggy. Avl Bal Rs. 14,500.00"
        )
        assertTrue(result is SmsParseResult.Valid)
        val txn = (result as SmsParseResult.Valid).transaction
        assertEquals(TransactionType.DEBIT, txn.type)
        assertEquals(0, BigDecimal("500.00").compareTo(txn.amount))
        assertEquals("XX1234", txn.accountRef)
        assertEquals("food", txn.suggestedCategoryId)
        assertEquals(0, BigDecimal("14500.00").compareTo(txn.availableBalance))
        assertEquals(TransactionStatus.CONFIRMED, txn.status)
    }

    @Test
    fun `parses bank credit SMS`() {
        val result = parser.parse(
            rawSender = "VM-ICICIB",
            messageBody = "Your account XX1234 has been credited with INR 5000 towards Salary."
        )
        assertTrue(result is SmsParseResult.Valid)
        val txn = (result as SmsParseResult.Valid).transaction
        assertEquals(TransactionType.CREDIT, txn.type)
        assertEquals(0, BigDecimal("5000").compareTo(txn.amount))
        assertEquals("Salary credit", txn.title)
    }

    @Test
    fun `parses UPI transaction SMS`() {
        val result = parser.parse(
            rawSender = "AX-AXISBK",
            messageBody = "UPI transaction of Rs. 250 debited from your A/c XX9876 to Uber India. UPI Ref No 6318290112."
        )
        assertTrue(result is SmsParseResult.Valid)
        val txn = (result as SmsParseResult.Valid).transaction
        assertEquals(TransactionType.DEBIT, txn.type)
        assertEquals(PaymentMethod.UPI, txn.paymentMethod)
        assertEquals("transportation", txn.suggestedCategoryId)
        assertEquals("6318290112", txn.referenceId)
    }

    @Test
    fun `parses Rs INR Rupee symbol commas and decimal formats`() {
        val samples = listOf(
            "Your A/c XX1111 debited by ₹1,25,000.50 at Amazon" to BigDecimal("125000.50"),
            "INR 2,499 spent on Card XX2222 at Flipkart" to BigDecimal("2499"),
            "Rs 99.75 debited from A/c XX3333 at Metro" to BigDecimal("99.75")
        )
        for ((msg, expected) in samples) {
            val res = parser.parse("AD-HDFCBK", msg)
            assertTrue("Expected valid parse for $msg", res is SmsParseResult.Valid)
            assertEquals(0, expected.compareTo((res as SmsParseResult.Valid).transaction.amount))
        }
    }

    @Test
    fun `rejects personal 10-digit unknown sender`() {
        val result = parser.parse(
            rawSender = "+919876543210",
            messageBody = "Hey I debited Rs 500 from ATM for dinner"
        )
        assertTrue(result is SmsParseResult.Rejected)
        assertEquals(SmsProcessDecision.IGNORED_INSUFFICIENT, (result as SmsParseResult.Rejected).decision)
    }

    @Test
    fun `marks unknown alphanumeric sender with bank keywords as needs review`() {
        val result = parser.parse(
            rawSender = "VK-NEWBNK",
            messageBody = "Your A/c XX4321 debited by Rs. 800.00 on 12-Nov-2026."
        )
        assertTrue(result is SmsParseResult.Valid)
        assertEquals(TransactionStatus.NEEDS_REVIEW, (result as SmsParseResult.Valid).transaction.status)
    }

    @Test
    fun `marks SMS with conflicting debit and credit verbs as needs review`() {
        val result = parser.parse(
            rawSender = "AD-HDFCBK",
            messageBody = "Your A/c XX1234 debited by Rs. 850.00 at Swiggy; earlier refund credited."
        )
        assertTrue(result is SmsParseResult.Valid)
        assertEquals(TransactionStatus.NEEDS_REVIEW, (result as SmsParseResult.Valid).transaction.status)
    }

    @Test
    fun `marks SMS with multiple monetary amounts as needs review`() {
        val result = parser.parse(
            rawSender = "AD-HDFCBK",
            messageBody = "Your A/c XX1234 debited by Rs. 850.00 at Swiggy with fee Rs. 25.00."
        )
        assertTrue(result is SmsParseResult.Valid)
        assertEquals(TransactionStatus.NEEDS_REVIEW, (result as SmsParseResult.Valid).transaction.status)
    }

    @Test
    fun `credit card spend does not trigger credit direction ambiguity when merchant is known`() {
        val result = parser.parse(
            rawSender = "AD-HDFCBK",
            messageBody = "INR 1,200.00 spent on your Credit Card XX2222 at Zomato on 12-Nov-2026."
        )
        assertTrue(result is SmsParseResult.Valid)
        val txn = (result as SmsParseResult.Valid).transaction
        assertEquals(TransactionType.DEBIT, txn.type)
        assertEquals(TransactionStatus.CONFIRMED, txn.status)
    }

    @Test
    fun `rejects promotional messages`() {
        val result = parser.parse(
            rawSender = "AD-HDFCBK",
            messageBody = "Congratulations! Pre-approved personal loan of Rs. 5,00,000 credited in 10 mins. Apply now!"
        )
        assertTrue(result is SmsParseResult.Rejected)
        assertEquals(SmsProcessDecision.REJECTED_PROMO, (result as SmsParseResult.Rejected).decision)
    }

    @Test
    fun `rejects OTP and authentication messages`() {
        val result = parser.parse(
            rawSender = "AD-HDFCBK",
            messageBody = "654321 is your OTP for Rs. 1,200.00 transaction on A/c XX1234. Do not share with anyone."
        )
        assertTrue(result is SmsParseResult.Rejected)
        assertEquals(SmsProcessDecision.REJECTED_OTP, (result as SmsParseResult.Rejected).decision)
    }

    @Test
    fun `rejects informational non-transaction due reminders`() {
        val result = parser.parse(
            rawSender = "AD-ICICIB",
            messageBody = "Reminder: Your credit card bill of Rs. 4,500.00 is due on 20-Nov-2026. Minimum balance Rs. 500."
        )
        assertTrue(result is SmsParseResult.Rejected)
        assertEquals(SmsProcessDecision.IGNORED_INSUFFICIENT, (result as SmsParseResult.Rejected).decision)
    }

    @Test
    fun `marks ambiguous debit without merchant as needs review`() {
        val result = parser.parse(
            rawSender = "AD-HDFCBK",
            messageBody = "Your A/c XX1234 has been debited by Rs. 800.00."
        )
        assertTrue(result is SmsParseResult.Valid)
        val txn = (result as SmsParseResult.Valid).transaction
        assertEquals(TransactionStatus.NEEDS_REVIEW, txn.status)
        assertEquals("other", txn.suggestedCategoryId)
    }

    @Test
    fun `computes identical fingerprint for duplicate SMS and distinct for different transactions`() {
        val ts = 1762960000000L
        val first = (parser.parse(
            "AD-HDFCBK",
            "Your A/c XX1234 debited by Rs. 800.00. UPI Ref No 9988776655.",
            ts
        ) as SmsParseResult.Valid).transaction

        val duplicate = (parser.parse(
            "AD-HDFCBK",
            "Rs. 800.00 debited from A/c XX1234. Ref# 9988776655.",
            ts + 30_000L
        ) as SmsParseResult.Valid).transaction

        val different = (parser.parse(
            "AD-HDFCBK",
            "Your A/c XX1234 debited by Rs. 800.00. UPI Ref No 1122334455.",
            ts + 60_000L
        ) as SmsParseResult.Valid).transaction

        assertEquals(dedup.computeFingerprint(first), dedup.computeFingerprint(duplicate))
        assertNotEquals(dedup.computeFingerprint(first), dedup.computeFingerprint(different))
    }
}
