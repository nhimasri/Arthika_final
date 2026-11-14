package com.example.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.domain.model.SpendingGuardLevel

object NotificationHelper {
    const val CHANNEL_ID = "arthika_finance_alerts"
    const val LIMIT_EXCEEDED_CHANNEL_ID = "arthika_limit_exceeded"

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun hasReceiveSmsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasReadSmsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return

            val standardChannel = NotificationChannel(
                CHANNEL_ID,
                "Transaction & Budget Warnings",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Automatic SMS transaction detections and approaching budget limit warnings."
            }

            val urgentChannel = NotificationChannel(
                LIMIT_EXCEEDED_CHANNEL_ID,
                "Payment Limit Exceeded Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Immediate alerts when a payment exceeds daily, category, monthly, or single-payment limits."
                enableVibration(true)
            }

            manager.createNotificationChannel(standardChannel)
            manager.createNotificationChannel(urgentChannel)
        }
    }

    fun showAlert(
        context: Context,
        notificationId: Int,
        title: String,
        message: String,
        level: SpendingGuardLevel = SpendingGuardLevel.GREEN
    ) {
        if (!hasNotificationPermission(context)) return

        ensureChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isUrgent = level == SpendingGuardLevel.RED
        val channelId = if (isUrgent) LIMIT_EXCEEDED_CHANNEL_ID else CHANNEL_ID
        val priority = if (isUrgent) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(priority)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        }
    }
}
