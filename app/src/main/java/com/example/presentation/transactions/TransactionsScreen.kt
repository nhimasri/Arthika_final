package com.example.presentation.transactions

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.entity.TransactionEntity
import com.example.domain.model.PaymentMethod
import com.example.domain.model.TransactionStatus
import com.example.presentation.ArthikaUiState
import com.example.presentation.TransactionFilterChip
import com.example.presentation.components.TransactionRowCard
import com.example.ui.theme.ArthikaTheme
import com.example.utils.DateUtils
import com.example.utils.NotificationHelper
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    uiState: ArthikaUiState,
    onSearchQueryChange: (String) -> Unit,
    onSelectFilterChip: (TransactionFilterChip) -> Unit,
    onSelectCategoryFilter: (String?) -> Unit,
    onSelectPaymentMethodFilter: (PaymentMethod?) -> Unit,
    onClearExtraFilters: () -> Unit,
    onOpenSmsImport: () -> Unit,
    onScanInbox: () -> Unit,
    onTransactionClick: (TransactionEntity) -> Unit
) {
    val context = LocalContext.current
    val semantic = ArthikaTheme.colors
    val categoryMap = remember(uiState.categories) { uiState.categories.associateBy { it.id } }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }

    val readSmsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onScanInbox()
        } else {
            onOpenSmsImport()
        }
    }

    val triggerScanWithPermission = {
        if (NotificationHelper.hasReadSmsPermission(context)) {
            onScanInbox()
        } else {
            readSmsPermissionLauncher.launch(Manifest.permission.READ_SMS)
        }
    }

    val pendingInFilter = remember(uiState.filteredTransactions) {
        uiState.filteredTransactions.filter { it.status == TransactionStatus.NEEDS_REVIEW }
    }
    val confirmedInFilter = remember(uiState.filteredTransactions) {
        uiState.filteredTransactions.filter { it.status == TransactionStatus.CONFIRMED }
    }

    val groupedConfirmedByDate = remember(confirmedInFilter) {
        val today = LocalDate.now()
        confirmedInFilter.groupBy { txn ->
            DateUtils.formatDayHeader(DateUtils.toLocalDate(txn.timestamp), today)
        }
    }

    val hasExtraFilters = uiState.filterState.categoryId != null ||
        uiState.filterState.paymentMethod != null

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Transactions",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "All your money movement",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row {
                    IconButton(
                        onClick = { showFilterDialog = true },
                        modifier = Modifier.testTag("transactions_filter_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FilterList,
                            contentDescription = "Filter by category or payment method",
                            tint = if (hasExtraFilters) semantic.softBlue else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "More transaction actions"
                            )
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Import Bank SMS") },
                                onClick = {
                                    showOverflowMenu = false
                                    onOpenSmsImport()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Scan SMS Inbox (30 days)") },
                                onClick = {
                                    showOverflowMenu = false
                                    triggerScanWithPermission()
                                }
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = uiState.filterState.searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Search transactions") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (uiState.filterState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(50),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = semantic.softBlue,
                    unfocusedBorderColor = semantic.cardBorder
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .testTag("search_transactions_input")
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TransactionFilterChip.entries.forEach { chip ->
                    val selected = uiState.filterState.chip == chip
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        border = if (selected) null else BorderStroke(1.dp, semantic.cardBorder),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { onSelectFilterChip(chip) }
                            .testTag("filter_chip_${chip.name.lowercase()}")
                    ) {
                        Text(
                            text = chip.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            PullToRefreshBox(
                isRefreshing = uiState.isScanningInbox,
                onRefresh = triggerScanWithPermission,
                modifier = Modifier.fillMaxSize()
            ) {
                if (pendingInFilter.isEmpty() && groupedConfirmedByDate.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ReceiptLong,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 10.dp)
                            )
                            Text(
                                text = "No matching transactions",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Try clearing filters, pull down to scan SMS, or tap + to add one.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (pendingInFilter.isNotEmpty()) {
                            stickyHeader(key = "header_pending_review") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.96f))
                                        .padding(top = 6.dp, bottom = 4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Transaction Review (${pendingInFilter.size})",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = semantic.warning
                                        )
                                        Text(
                                            text = "Not in budget until confirmed",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            items(pendingInFilter, key = { "review_${it.id}" }) { txn ->
                                TransactionRowCard(
                                    transaction = txn,
                                    category = categoryMap[txn.categoryId],
                                    onClick = { onTransactionClick(txn) },
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }

                        groupedConfirmedByDate.forEach { (dateLabel, itemsInDay) ->
                            stickyHeader(key = "header_$dateLabel") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.96f))
                                        .padding(top = 6.dp, bottom = 4.dp)
                                ) {
                                    Text(
                                        text = dateLabel,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                            }
                            items(itemsInDay, key = { it.id }) { txn ->
                                TransactionRowCard(
                                    transaction = txn,
                                    category = categoryMap[txn.categoryId],
                                    onClick = { onTransactionClick(txn) },
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFilterDialog) {
        AlertDialog(
            onDismissRequest = { showFilterDialog = false },
            title = { Text("Filter Transactions", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Category", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = uiState.filterState.categoryId == null,
                            onClick = { onSelectCategoryFilter(null) },
                            label = { Text("All") }
                        )
                        uiState.categories.forEach { cat ->
                            FilterChip(
                                selected = uiState.filterState.categoryId == cat.id,
                                onClick = { onSelectCategoryFilter(cat.id) },
                                label = { Text(cat.name) }
                            )
                        }
                    }

                    Text("Payment Method", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = uiState.filterState.paymentMethod == null,
                            onClick = { onSelectPaymentMethodFilter(null) },
                            label = { Text("All") }
                        )
                        PaymentMethod.entries.forEach { method ->
                            FilterChip(
                                selected = uiState.filterState.paymentMethod == method,
                                onClick = { onSelectPaymentMethodFilter(method) },
                                label = { Text(method.label) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFilterDialog = false }) {
                    Text("Done")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    onClearExtraFilters()
                    showFilterDialog = false
                }) {
                    Text("Reset")
                }
            }
        )
    }
}
