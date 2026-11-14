package com.example.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.TransactionEntity
import com.example.domain.model.FinancialSummary
import com.example.domain.model.PaymentMethod
import com.example.domain.model.TransactionSource
import com.example.domain.model.TransactionType
import com.example.ui.theme.ArthikaTheme
import com.example.utils.CurrencyFormatter
import com.example.utils.DateUtils
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewDetectionBottomSheet(
    transaction: TransactionEntity,
    categories: List<CategoryEntity>,
    onConfirm: (merchant: String, categoryId: String, markAsTransfer: Boolean) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val semantic = ArthikaTheme.colors
    var merchant by remember(transaction.id) {
        mutableStateOf(transaction.merchantName ?: transaction.title)
    }
    var selectedCategoryId by remember(transaction.id) {
        mutableStateOf(transaction.categoryId)
    }
    var markAsTransfer by remember(transaction.id) {
        mutableStateOf(transaction.isTransfer)
    }
    var categoryExpanded by remember { mutableStateOf(false) }

    val selectedCategory = categories.find { it.id == selectedCategoryId }
    val visuals = resolveCategoryVisuals(selectedCategoryId, semantic)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            Surface(
                color = semantic.warningTint,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Pending review — not counted in your budget until confirmed.",
                    style = MaterialTheme.typography.labelMedium,
                    color = semantic.warning,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(visuals.bg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = visuals.icon,
                        contentDescription = null,
                        tint = visuals.tint,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    val dirLabel = if (transaction.type == TransactionType.DEBIT) "Debit" else "Credit"
                    Text(
                        text = "$dirLabel ${CurrencyFormatter.formatInrExact(transaction.amount)}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = DateUtils.formatDateTime(transaction.timestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!transaction.accountRef.isNullOrBlank()) {
                        Text(
                            text = "Account ending ${transaction.accountRef.takeLast(4)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(
                value = merchant,
                onValueChange = { merchant = it },
                label = { Text("Merchant or recipient") },
                isError = merchant.isBlank(),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("review_merchant_input")
            )

            Spacer(modifier = Modifier.height(14.dp))

            Box(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { categoryExpanded = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = visuals.icon,
                            contentDescription = null,
                            tint = visuals.tint,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Category",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = selectedCategory?.name ?: "Other",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = "Select category"
                        )
                    }
                }

                DropdownMenu(
                    expanded = categoryExpanded,
                    onDismissRequest = { categoryExpanded = false }
                ) {
                    categories.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat.name) },
                            onClick = {
                                selectedCategoryId = cat.id
                                categoryExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { markAsTransfer = !markAsTransfer }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.SwapHoriz,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Mark as transfer between my accounts",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = markAsTransfer,
                    onCheckedChange = { markAsTransfer = it },
                    modifier = Modifier.testTag("review_transfer_switch")
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { onConfirm(merchant.trim(), selectedCategoryId, markAsTransfer) },
                    enabled = merchant.isNotBlank(),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("review_confirm_button")
                ) {
                    Text("Confirm", fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = onDelete,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = semantic.danger
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("review_delete_button")
                ) {
                    Text("Reject", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditTransactionSheet(
    existingTransaction: TransactionEntity?,
    initialType: TransactionType,
    categories: List<CategoryEntity>,
    summary: FinancialSummary?,
    singlePaymentLimit: BigDecimal? = null,
    onSave: (
        title: String,
        amount: BigDecimal,
        type: TransactionType,
        categoryId: String,
        paymentMethod: PaymentMethod,
        notes: String?,
        isTransfer: Boolean
    ) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val semantic = ArthikaTheme.colors
    var type by remember(existingTransaction) {
        mutableStateOf(existingTransaction?.type ?: initialType)
    }
    var title by remember(existingTransaction) {
        mutableStateOf(existingTransaction?.title.orEmpty())
    }
    var amountText by remember(existingTransaction) {
        mutableStateOf(existingTransaction?.amount?.stripTrailingZeros()?.toPlainString().orEmpty())
    }
    var categoryId by remember(existingTransaction) {
        mutableStateOf(existingTransaction?.categoryId ?: "food")
    }
    var paymentMethod by remember(existingTransaction) {
        mutableStateOf(existingTransaction?.paymentMethod ?: PaymentMethod.UPI)
    }
    var notes by remember(existingTransaction) {
        mutableStateOf(existingTransaction?.notes.orEmpty())
    }
    var isTransfer by remember(existingTransaction) {
        mutableStateOf(existingTransaction?.isTransfer ?: false)
    }

    val parsedAmount = amountText.toBigDecimalOrNull()
    val isAmountValid = parsedAmount != null && parsedAmount > BigDecimal.ZERO

    val preSaveWarnings = remember(parsedAmount, type, isTransfer, categoryId, summary, singlePaymentLimit) {
        if (type != TransactionType.DEBIT || isTransfer || parsedAmount == null || parsedAmount <= BigDecimal.ZERO) {
            emptyList()
        } else {
            val warnings = mutableListOf<Pair<String, Boolean>>() // message to isUrgentRed
            val effectiveDelta = if (existingTransaction != null &&
                existingTransaction.type == TransactionType.DEBIT &&
                !existingTransaction.isTransfer
            ) {
                (parsedAmount - existingTransaction.amount).coerceAtLeast(BigDecimal.ZERO)
            } else {
                parsedAmount
            }

            if (singlePaymentLimit != null && singlePaymentLimit > BigDecimal.ZERO && parsedAmount > singlePaymentLimit) {
                warnings.add("Exceeds your ${CurrencyFormatter.formatInr(singlePaymentLimit)} single-payment limit." to true)
            }
            if (summary != null) {
                if (summary.flexibleBudget > BigDecimal.ZERO && effectiveDelta > summary.remainingFlexibleBudget) {
                    val over = (effectiveDelta - summary.remainingFlexibleBudget).abs()
                    warnings.add("Exceeds remaining monthly flexible budget by ${CurrencyFormatter.formatInr(over)}." to true)
                }
                if (summary.flexibleBudget > BigDecimal.ZERO && (summary.spentToday + effectiveDelta) > summary.dailyAllowance) {
                    warnings.add("Exceeds today's ${CurrencyFormatter.formatInr(summary.dailyAllowance)} safe-to-spend limit." to true)
                }
                val catBreakdown = summary.categoryBreakdowns.find { it.categoryId == categoryId }
                if (catBreakdown != null && catBreakdown.budgetLimit > BigDecimal.ZERO) {
                    val projectedCatSpent = if (existingTransaction?.categoryId == categoryId &&
                        existingTransaction.type == TransactionType.DEBIT &&
                        !existingTransaction.isTransfer
                    ) {
                        catBreakdown.spentAmount - existingTransaction.amount + parsedAmount
                    } else {
                        catBreakdown.spentAmount + parsedAmount
                    }
                    if (projectedCatSpent > catBreakdown.budgetLimit) {
                        val overCat = projectedCatSpent - catBreakdown.budgetLimit
                        warnings.add("Exceeds ${catBreakdown.categoryName} monthly limit by ${CurrencyFormatter.formatInr(overCat)}." to true)
                    } else if (projectedCatSpent >= catBreakdown.budgetLimit.multiply(BigDecimal("0.85"))) {
                        warnings.add("Brings ${catBreakdown.categoryName} near its monthly limit (${CurrencyFormatter.formatInr(catBreakdown.budgetLimit)})." to false)
                    }
                }
            }
            warnings
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = if (existingTransaction == null) "Add Transaction" else "Edit Transaction",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            if (existingTransaction?.source == TransactionSource.SMS) {
                Surface(
                    color = semantic.softBlueTint,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val meta = buildString {
                        append("Detected via SMS")
                        existingTransaction.bankSender?.takeIf { it.isNotBlank() }?.let { append(" ($it)") }
                        existingTransaction.referenceId?.takeIf { it.isNotBlank() }?.let { append(" · Ref $it") }
                    }
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.labelMedium,
                        color = semantic.softBlue,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = type == TransactionType.DEBIT,
                    onClick = { type = TransactionType.DEBIT },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) {
                    Text("Expense (Debit)")
                }
                SegmentedButton(
                    selected = type == TransactionType.CREDIT,
                    onClick = { type = TransactionType.CREDIT },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) {
                    Text("Income (Credit)")
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title / Merchant") },
                placeholder = { Text("e.g., Swiggy, Metro Card, Freelance") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("txn_title_input")
            )

            OutlinedTextField(
                value = amountText,
                onValueChange = { input ->
                    if (input.isEmpty() || input.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                        amountText = input
                    }
                },
                label = { Text("Amount (₹)") },
                prefix = { Text("₹ ") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("txn_amount_input")
            )

            val hasRedWarning = preSaveWarnings.any { it.second }
            val haptics = LocalHapticFeedback.current
            LaunchedEffect(hasRedWarning) {
                if (hasRedWarning) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            }

            AnimatedVisibility(
                visible = preSaveWarnings.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                val bannerBg = if (hasRedWarning) semantic.dangerTint else semantic.warningTint
                val bannerTint = if (hasRedWarning) semantic.danger else semantic.warning
                Surface(
                    color = bannerBg,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WarningAmber,
                            contentDescription = null,
                            tint = bannerTint
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = if (hasRedWarning) "Spending Guard: Limit Exceeded" else "Spending Guard Warning",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = bannerTint
                            )
                            preSaveWarnings.forEach { (msg, _) ->
                                Text(
                                    text = "• $msg",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = bannerTint
                                )
                            }
                        }
                    }
                }
            }

            Text(
                text = "Category",
                style = MaterialTheme.typography.labelLarge
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                categories.forEach { cat ->
                    FilterChip(
                        selected = categoryId == cat.id,
                        onClick = { categoryId = cat.id },
                        label = { Text(cat.name) }
                    )
                }
            }

            Text(
                text = "Payment Method",
                style = MaterialTheme.typography.labelLarge
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    PaymentMethod.UPI,
                    PaymentMethod.CARD,
                    PaymentMethod.CASH,
                    PaymentMethod.NET_BANKING,
                    PaymentMethod.BANK_TRANSFER
                ).forEach { method ->
                    FilterChip(
                        selected = paymentMethod == method,
                        onClick = { paymentMethod = method },
                        label = { Text(method.label) }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Self-transfer (exclude from budget)",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = isTransfer, onCheckedChange = { isTransfer = it })
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (optional)") },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (onDelete != null) {
                    OutlinedButton(
                        onClick = onDelete,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                    ) {
                        Text("Delete")
                    }
                }
                Button(
                    onClick = {
                        if (parsedAmount != null && parsedAmount > BigDecimal.ZERO && title.isNotBlank()) {
                            onSave(title, parsedAmount, type, categoryId, paymentMethod, notes.ifBlank { null }, isTransfer)
                        }
                    },
                    enabled = isAmountValid && title.isNotBlank(),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .weight(1.5f)
                        .height(50.dp)
                        .testTag("save_transaction_button")
                ) {
                    Text("Save Transaction", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsImportBottomSheet(
    onImportSms: (sender: String, body: String) -> Unit,
    onScanInbox: () -> Unit,
    isScanningInbox: Boolean,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    var sender by remember { mutableStateOf("") }
    var smsBody by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Sms,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Import Bank SMS",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Runs local SmsTransactionParser & duplicate detector",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedTextField(
                value = sender,
                onValueChange = { sender = it },
                label = { Text("Sender Header (optional, e.g. HDFCBK)") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = smsBody,
                onValueChange = { smsBody = it },
                label = { Text("Paste Bank or UPI SMS Alert") },
                placeholder = { Text("Paste the transaction alert text from your SMS app...") },
                minLines = 3,
                maxLines = 5,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sms_paste_input")
            )

            OutlinedButton(
                onClick = {
                    val clipText = clipboardManager.getText()?.text
                    if (!clipText.isNullOrBlank()) {
                        smsBody = clipText
                    }
                },
                shape = RoundedCornerShape(50),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Paste from Clipboard")
            }

            Button(
                onClick = {
                    if (smsBody.isNotBlank()) {
                        onImportSms(sender, smsBody)
                        onDismiss()
                    }
                },
                enabled = smsBody.isNotBlank(),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("parse_import_sms_button")
            ) {
                Text("Parse & Import SMS", fontWeight = FontWeight.SemiBold)
            }

            OutlinedButton(
                onClick = onScanInbox,
                enabled = !isScanningInbox,
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(if (isScanningInbox) "Scanning Inbox..." else "Scan Device SMS Inbox (Last 30 Days)")
            }
        }
    }
}
