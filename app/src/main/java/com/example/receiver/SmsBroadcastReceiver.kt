package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.example.ArthikaApplication
import com.example.data.repository.SmsImportOutcome
import com.example.domain.model.TransactionType
import com.example.utils.CurrencyFormatter
import com.example.utils.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        val app = context.applicationContext as? ArthikaApplication ?: return
        val pendingResult = goAsync()

        // Group multi-part SMS segments by originating address
        val grouped = mutableMapOf<String, StringBuilder>()
        var timestamp = System.currentTimeMillis()
        for (sms in messages) {
            val sender = sms.originatingAddress.orEmpty()
            val body = sms.messageBody.orEmpty()
            timestamp = sms.timestampMillis.takeIf { it > 0L } ?: timestamp
            grouped.getOrPut(sender) { StringBuilder() }.append(body)
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                for ((sender, bodyBuilder) in grouped) {
                    val outcome = app.container.smsTransactionRepository.processIncomingBroadcastSms(
                        rawSender = sender,
                        messageBody = bodyBuilder.toString(),
                        receivedAtMillis = timestamp
                    )
                    if (outcome is SmsImportOutcome.Imported) {
                        val txn = outcome.transaction
                        val label = if (txn.type == TransactionType.DEBIT) "Debit" else "Credit"
                        val isReview = txn.status == com.example.domain.model.TransactionStatus.NEEDS_REVIEW
                        NotificationHelper.showAlert(
                            context = context,
                            notificationId = txn.id.toInt(),
                            title = if (isReview) {
                                "Review $label (${CurrencyFormatter.formatInrExact(txn.amount)})"
                            } else {
                                "$label ${CurrencyFormatter.formatInrExact(txn.amount)} detected"
                            },
                            message = if (isReview) {
                                "${txn.title} needs your confirmation before affecting your budget."
                            } else {
                                "${txn.title} automatically recorded from SMS."
                            },
                            level = if (isReview) {
                                com.example.domain.model.SpendingGuardLevel.ORANGE
                            } else {
                                com.example.domain.model.SpendingGuardLevel.GREEN
                            }
                        )
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
