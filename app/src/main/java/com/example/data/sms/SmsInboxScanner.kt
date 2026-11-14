package com.example.data.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.example.data.repository.SmsImportOutcome
import com.example.data.repository.SmsTransactionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InboxScanSummary(
    val scannedCount: Int,
    val importedCount: Int,
    val duplicateCount: Int,
    val permissionGranted: Boolean
)

class SmsInboxScanner(
    private val context: Context,
    private val smsTransactionRepository: SmsTransactionRepository
) {
    suspend fun scanRecentInbox(daysBack: Int = 30): InboxScanSummary = withContext(Dispatchers.IO) {
        val hasReadSms = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasReadSms) {
            return@withContext InboxScanSummary(0, 0, 0, permissionGranted = false)
        }

        val cutoffMillis = System.currentTimeMillis() - (daysBack * 24L * 60L * 60L * 1000L)
        var scanned = 0
        var imported = 0
        var duplicates = 0

        val projection = arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE
        )
        val selection = "${Telephony.Sms.DATE} >= ?"
        val selectionArgs = arrayOf(cutoffMillis.toString())

        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${Telephony.Sms.DATE} DESC"
        )?.use { cursor ->
            val addressIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY)
            val dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE)

            while (cursor.moveToNext() && scanned < 200) {
                val sender = if (addressIdx >= 0) cursor.getString(addressIdx).orEmpty() else ""
                val body = if (bodyIdx >= 0) cursor.getString(bodyIdx).orEmpty() else ""
                val date = if (dateIdx >= 0) cursor.getLong(dateIdx) else System.currentTimeMillis()
                scanned++

                when (smsTransactionRepository.importSmsManuallyOrFromInbox(
                    rawSender = sender,
                    messageBody = body,
                    receivedAtMillis = date,
                    postNotification = false
                )) {
                    is SmsImportOutcome.Imported -> imported++
                    is SmsImportOutcome.Duplicate -> duplicates++
                    is SmsImportOutcome.Rejected -> Unit
                }
            }
        }

        InboxScanSummary(
            scannedCount = scanned,
            importedCount = imported,
            duplicateCount = duplicates,
            permissionGranted = true
        )
    }
}
