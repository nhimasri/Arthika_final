package com.example.presentation.profile

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.data.local.entity.SpendingRuleEntity
import com.example.presentation.ArthikaUiState
import com.example.ui.theme.ArthikaTheme
import com.example.ui.theme.ThemePreference
import com.example.utils.CurrencyFormatter
import com.example.utils.DateUtils
import com.example.utils.NotificationHelper
import java.math.BigDecimal

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    uiState: ArthikaUiState,
    onUpdateUserName: (String) -> Unit,
    onToggleSmsDetection: (Boolean) -> Unit,
    onOpenSmsImport: () -> Unit,
    onScanInbox: () -> Unit,
    onDeleteAllSmsData: () -> Unit,
    onToggleSpendingRule: (SpendingRuleEntity) -> Unit,
    onUpdateSinglePaymentLimit: (BigDecimal?) -> Unit,
    onToggleDailyAlerts: (Boolean) -> Unit,
    onThemeChange: (ThemePreference) -> Unit,
    onClearAllData: () -> Unit
) {
    val context = LocalContext.current
    val semantic = ArthikaTheme.colors
    var showEditNameDialog by remember { mutableStateOf(false) }
    var showDeleteSmsConfirm by remember { mutableStateOf(false) }
    var showClearAllConfirm by remember { mutableStateOf(false) }
    var showAllSenders by remember { mutableStateOf(false) }
    var singleLimitInput by remember(uiState.preferences.singlePaymentLimit) {
        mutableStateOf(
            uiState.preferences.singlePaymentLimit?.stripTrailingZeros()?.toPlainString().orEmpty()
        )
    }
    var singleLimitError by remember { mutableStateOf<String?>(null) }

    var hasReceiveSms by remember { mutableStateOf(NotificationHelper.hasReceiveSmsPermission(context)) }
    var hasReadSms by remember { mutableStateOf(NotificationHelper.hasReadSmsPermission(context)) }
    var hasNotificationPermission by remember { mutableStateOf(NotificationHelper.hasNotificationPermission(context)) }
    var smsPermissionNotice by remember { mutableStateOf<String?>(null) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasReceiveSms = NotificationHelper.hasReceiveSmsPermission(context)
        hasReadSms = NotificationHelper.hasReadSmsPermission(context)
        hasNotificationPermission = NotificationHelper.hasNotificationPermission(context)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted || NotificationHelper.hasNotificationPermission(context)
        if (granted) {
            onToggleDailyAlerts(true)
        }
    }

    val receiveSmsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasReceiveSms = granted || NotificationHelper.hasReceiveSmsPermission(context)
        onToggleSmsDetection(granted)
        smsPermissionNotice = if (granted) {
            null
        } else {
            "Receive SMS permission was denied, so automatic SMS detection remains off. You can still paste bank SMS or log entries manually."
        }
    }

    val readSmsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasReadSms = granted || NotificationHelper.hasReadSmsPermission(context)
        if (granted) {
            smsPermissionNotice = null
            onScanInbox()
        } else {
            smsPermissionNotice = "Read SMS permission is required only to scan past inbox messages. Automatic live detection relies on Receive SMS."
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    text = "Profile & Privacy",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, semantic.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(semantic.softBlueTint),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = null,
                                tint = semantic.softBlue,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = uiState.userName.ifBlank { "Arthika User" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Currency: INR (₹) · Offline Room Vault",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { showEditNameDialog = true }) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Edit profile name")
                        }
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, semantic.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Sms, contentDescription = null, tint = semantic.softBlue)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Automatic SMS Detection",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (uiState.preferences.smsDetectionEnabled && hasReceiveSms) {
                                        "Active via RECEIVE_SMS"
                                    } else if (hasReadSms && !hasReceiveSms) {
                                        "Needs RECEIVE_SMS permission for automatic alerts"
                                    } else {
                                        "Off · Paste Bank SMS works without permissions"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (uiState.preferences.smsDetectionEnabled && hasReceiveSms) {
                                        semantic.mint
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                            Switch(
                                checked = uiState.preferences.smsDetectionEnabled && hasReceiveSms,
                                onCheckedChange = { enable ->
                                    if (enable && !hasReceiveSms) {
                                        receiveSmsLauncher.launch(Manifest.permission.RECEIVE_SMS)
                                    } else {
                                        smsPermissionNotice = null
                                        onToggleSmsDetection(enable)
                                    }
                                },
                                modifier = Modifier.testTag("profile_sms_toggle")
                            )
                        }

                        Surface(
                            color = semantic.mintTint,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Lock,
                                    contentDescription = null,
                                    tint = semantic.mint,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "On-device rule engine only. Raw SMS messages and OTPs are never uploaded or stored.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Text(
                            text = buildString {
                                append("Live Detection (RECEIVE_SMS): ")
                                append(if (hasReceiveSms) "Granted" else "Not granted")
                                append(" · Inbox Scan (READ_SMS): ")
                                append(if (hasReadSms) "Granted" else "Not granted")
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        smsPermissionNotice?.let { notice ->
                            Surface(
                                color = semantic.warningTint,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = notice,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onOpenSmsImport,
                                shape = RoundedCornerShape(50),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("profile_paste_sms_button")
                            ) {
                                Text("Paste Bank SMS")
                            }
                            OutlinedButton(
                                onClick = {
                                    if (hasReadSms) {
                                        onScanInbox()
                                    } else {
                                        readSmsLauncher.launch(Manifest.permission.READ_SMS)
                                    }
                                },
                                enabled = !uiState.isScanningInbox,
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (uiState.isScanningInbox) "Scanning..." else "Scan Inbox")
                            }
                        }

                        if (uiState.trustedSenders.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Supported Bank Senders (${uiState.trustedSenders.size})",
                                    style = MaterialTheme.typography.labelLarge
                                )
                                if (uiState.trustedSenders.size > 8) {
                                    TextButton(onClick = { showAllSenders = !showAllSenders }) {
                                        Text(if (showAllSenders) "Show fewer" else "Show all")
                                    }
                                }
                            }
                            val visibleSenders = if (showAllSenders) uiState.trustedSenders else uiState.trustedSenders.take(8)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                visibleSenders.forEach { sender ->
                                    Surface(
                                        color = semantic.neutralTint,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = sender.senderPattern,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }

                        if (uiState.processedSmsLogs.isNotEmpty()) {
                            Text(
                                text = "SMS Privacy Audit Log (Hashes & Decisions Only)",
                                style = MaterialTheme.typography.labelLarge
                            )
                            uiState.processedSmsLogs.take(4).forEach { log ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${log.senderHeader} · ${log.amount?.let { CurrencyFormatter.formatInr(it) } ?: "--"}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        text = "${log.decision.name} · ${DateUtils.formatDate(log.processedAt)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = { showDeleteSmsConfirm = true },
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = semantic.danger),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Delete Imported SMS Data")
                        }
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, semantic.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Spending Guard Rules",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        uiState.spendingRules.forEach { rule ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(rule.name, style = MaterialTheme.typography.bodyMedium)
                                    val thresholdText = if (rule.ruleCode == "VOLUME_10") {
                                        "Limit: ${rule.thresholdPercent} outgoing txns/day"
                                    } else {
                                        "Threshold: ${rule.thresholdPercent}%"
                                    }
                                    Text(
                                        text = thresholdText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = rule.isEnabled,
                                    onCheckedChange = { onToggleSpendingRule(rule) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Single-Payment Limit Alert (Optional)",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Alert immediately whenever any single expense exceeds this amount.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = singleLimitInput,
                                onValueChange = { input ->
                                    if (input.isEmpty() || input.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                                        singleLimitInput = input
                                        singleLimitError = null
                                    }
                                },
                                label = { Text("Max per payment (₹)") },
                                placeholder = { Text("No limit") },
                                prefix = { Text("₹ ") },
                                isError = singleLimitError != null,
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("single_payment_limit_input")
                            )
                            Button(
                                onClick = {
                                    if (singleLimitInput.isBlank()) {
                                        singleLimitError = null
                                        onUpdateSinglePaymentLimit(null)
                                    } else {
                                        val parsed = singleLimitInput.toBigDecimalOrNull()
                                        if (parsed == null || parsed <= BigDecimal.ZERO) {
                                            singleLimitError = "Enter an amount > ₹0 or tap Clear."
                                        } else {
                                            singleLimitError = null
                                            onUpdateSinglePaymentLimit(parsed)
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("save_single_payment_limit_button")
                            ) {
                                Text("Save")
                            }
                            if (uiState.preferences.singlePaymentLimit != null) {
                                TextButton(
                                    onClick = {
                                        singleLimitInput = ""
                                        singleLimitError = null
                                        onUpdateSinglePaymentLimit(null)
                                    }
                                ) {
                                    Text("Clear")
                                }
                            }
                        }
                        singleLimitError?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, semantic.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Appearance & Alerts",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ThemePreference.entries.forEach { mode ->
                                FilterChip(
                                    selected = uiState.preferences.themePreference == mode,
                                    onClick = { onThemeChange(mode) },
                                    label = { Text(mode.name.lowercase().replaceFirstChar { it.titlecase() }) }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Payment & Limit Notifications",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = if (hasNotificationPermission) {
                                        "System notifications active when limits are exceeded"
                                    } else {
                                        "Android notification permission needed for system alerts"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (hasNotificationPermission) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        semantic.warning
                                    }
                                )
                            }
                            Switch(
                                checked = uiState.preferences.dailyAlertsEnabled && hasNotificationPermission,
                                onCheckedChange = { enabled ->
                                    if (enabled && !hasNotificationPermission &&
                                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                                    ) {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        onToggleDailyAlerts(enabled)
                                    }
                                },
                                modifier = Modifier.testTag("profile_alerts_toggle")
                            )
                        }

                        if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            OutlinedButton(
                                onClick = {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                },
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Allow Android Notifications")
                            }
                        }

                        OutlinedButton(
                            onClick = { showClearAllConfirm = true },
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = semantic.danger),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Clear All Financial Data")
                        }
                    }
                }
            }
        }
    }

    if (showEditNameDialog) {
        var nameInput by remember { mutableStateOf(uiState.userName) }
        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            title = { Text("Update Display Name") },
            text = {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Your Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    onUpdateUserName(nameInput)
                    showEditNameDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showEditNameDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showDeleteSmsConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteSmsConfirm = false },
            title = { Text("Delete Imported SMS Data?") },
            text = {
                Text("This permanently deletes all SMS-detected transactions and SMS audit records from your local database. Manual transactions stay intact.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteAllSmsData()
                        showDeleteSmsConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = semantic.danger)
                ) { Text("Delete All") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSmsConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            title = { Text("Clear All Financial Data?") },
            text = {
                Text("This permanently deletes all transactions, goals, recurring bills, challenges, notifications, and resets your budget numbers to ₹0.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onClearAllData()
                        showClearAllConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = semantic.danger)
                ) { Text("Clear All") }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirm = false }) { Text("Cancel") }
            }
        )
    }
}
