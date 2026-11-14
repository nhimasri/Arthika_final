package com.example.presentation.budgets

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoGraph
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.RecurringPaymentEntity
import com.example.domain.model.CategoryBreakdown
import com.example.domain.model.SpendingGuardLevel
import com.example.presentation.ArthikaUiState
import com.example.presentation.components.SpendingGuardPill
import com.example.presentation.components.resolveCategoryVisuals
import com.example.ui.theme.ArthikaTheme
import com.example.utils.CurrencyFormatter
import java.math.BigDecimal
import java.time.YearMonth

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BudgetsScreen(
    uiState: ArthikaUiState,
    onUpdateFinancialProfile: (income: BigDecimal, fixed: BigDecimal, savings: BigDecimal) -> Unit,
    onUpdateCategoryBudget: (categoryId: String, limit: BigDecimal) -> Unit,
    onCreateCategory: (String) -> Unit,
    onRenameCategory: (categoryId: String, newName: String) -> Unit,
    onDeleteCategory: (categoryId: String) -> Unit,
    onAddRecurringPayment: (title: String, amount: BigDecimal, categoryId: String, dayOfMonth: Int) -> Unit,
    onPayRecurringBill: (billId: Long, partialAmount: BigDecimal?) -> Unit,
    onUnmarkRecurringBill: (billId: Long) -> Unit,
    onDeleteRecurringPayment: (id: Long) -> Unit,
    onOpenWhatIf: () -> Unit
) {
    val summary = uiState.summary ?: return
    val semantic = ArthikaTheme.colors
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<CategoryBreakdown?>(null) }
    var showCreateCategoryDialog by remember { mutableStateOf(false) }
    var showAddBillDialog by remember { mutableStateOf(false) }
    var payingBill by remember { mutableStateOf<Pair<RecurringPaymentEntity, BigDecimal>?>(null) }
    var reversingBill by remember { mutableStateOf<RecurringPaymentEntity?>(null) }
    val currentYmStr = YearMonth.now().toString()

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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp)
                    ) {
                        Text(
                            text = "Dynamic Budget",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Real-time allowance & Spending Guard",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = { showEditProfileDialog = true },
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("edit_budget_profile_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Configure", maxLines = 1, softWrap = false)
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(semantic.heroBrush)
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Budget Allocation Formula",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                            )
                            SpendingGuardPill(level = summary.guardLevel)
                        }

                        FormulaLine("Monthly Income", CurrencyFormatter.formatInr(summary.monthlyIncome))
                        if (summary.extraCreditsThisMonth > BigDecimal.ZERO) {
                            FormulaLine(
                                "Extra Income (Confirmed Credits)",
                                "+ ${CurrencyFormatter.formatInr(summary.extraCreditsThisMonth)}",
                                semantic.mint
                            )
                        }
                        FormulaLine("Fixed Expenses", "- ${CurrencyFormatter.formatInr(summary.fixedExpenses)}")
                        FormulaLine("Protected Savings", "- ${CurrencyFormatter.formatInr(summary.protectedSavings)}", semantic.mint)
                        if (summary.upcomingBillsReserved > BigDecimal.ZERO) {
                            FormulaLine(
                                "Upcoming Bills Reserved",
                                "- ${CurrencyFormatter.formatInr(summary.upcomingBillsReserved)}",
                                semantic.warning
                            )
                        }

                        Surface(
                            color = semantic.heroCardFooterBg,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 12.dp)
                                ) {
                                    Text(
                                        text = "Remaining Flexible Budget",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White.copy(alpha = 0.75f)
                                    )
                                    Text(
                                        text = CurrencyFormatter.formatInrExact(summary.remainingFlexibleBudget),
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "${summary.daysRemainingInMonth} days left",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.75f),
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                    Text(
                                        text = "${CurrencyFormatter.formatInrExact(summary.dailyAllowance)}/day",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = semantic.mint,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }

                        Text(
                            text = "Initial allowance was ${CurrencyFormatter.formatInrExact(summary.initialDailyAllowance)}/day · ${summary.revisionExplanation}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            item {
                val survivalBg = if (summary.isSurvivalModeActive) semantic.dangerTint else semantic.mintTint
                val survivalAccent = if (summary.isSurvivalModeActive) semantic.danger else semantic.mint
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, survivalAccent.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(survivalBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (summary.isSurvivalModeActive) Icons.Rounded.WarningAmber else Icons.Rounded.Shield,
                                    contentDescription = null,
                                    tint = survivalAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (summary.isSurvivalModeActive) "End-of-Month Survival Mode Active" else "Survival Mode Standby",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = survivalAccent
                                )
                                Text(
                                    text = "${CurrencyFormatter.formatInr(summary.remainingFlexibleBudget.coerceAtLeast(BigDecimal.ZERO))} remaining for ${summary.daysRemainingInMonth} days",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (summary.survivalAdvice.isNotEmpty()) {
                            summary.survivalAdvice.forEach { tip ->
                                Text(
                                    text = "• $tip",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        } else {
                            Text(
                                text = "Your daily safe limit (${CurrencyFormatter.formatInrExact(summary.dailyAllowance)}) is healthy. If early overspending drops your daily limit below 70% of plan, Survival Mode activates automatically.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    onClick = onOpenWhatIf,
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, semantic.cardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("budgets_what_if_card")
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(semantic.softBlueTint),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoGraph,
                                contentDescription = null,
                                tint = semantic.softBlue
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "What-If Financial Simulator",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Test a purchase before buying without altering your data",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "Try",
                            style = MaterialTheme.typography.labelLarge,
                            color = semantic.softBlue,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (uiState.alerts.isNotEmpty()) {
                item {
                    Text(
                        text = "Active Spending Guard Alerts",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(uiState.alerts, key = { it.id }) { alert ->
                    val accent = when (alert.level) {
                        SpendingGuardLevel.GREEN -> semantic.mint
                        SpendingGuardLevel.ORANGE -> semantic.warning
                        SpendingGuardLevel.RED -> semantic.danger
                    }
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = accent)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(alert.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(alert.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Category Budgets",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Month-isolated limits · Tap card to edit",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(
                        onClick = { showCreateCategoryDialog = true },
                        modifier = Modifier.testTag("add_category_button")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Category")
                    }
                }
            }

            items(summary.categoryBreakdowns, key = { it.categoryId }) { breakdown ->
                CategoryBudgetCard(
                    breakdown = breakdown,
                    onClick = { editingCategory = breakdown },
                    modifier = Modifier.animateItem()
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Upcoming Bills Reserve",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(
                        onClick = { showAddBillDialog = true },
                        modifier = Modifier.testTag("add_bill_button")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Bill")
                    }
                }
            }

            if (uiState.recurringPayments.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, semantic.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No upcoming bills added. Add recurring bills like Wi-Fi or electricity to reserve funds automatically.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(uiState.recurringPayments, key = { it.id }) { bill ->
                    val billPaymentsForThis = uiState.billPayments.filter { it.recurringPaymentId == bill.id }
                    val paidThisMonth = billPaymentsForThis.fold(BigDecimal.ZERO) { acc, p -> acc + p.paidAmount }
                    val isFullyPaid = paidThisMonth >= bill.amount ||
                        (billPaymentsForThis.isEmpty() && bill.isPaidForCurrentMonth && bill.paidYearMonth == currentYmStr)
                    val isPartiallyPaid = paidThisMonth > BigDecimal.ZERO && !isFullyPaid
                    val remainingDue = (bill.amount - paidThisMonth).coerceAtLeast(BigDecimal.ZERO)

                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, semantic.cardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isFullyPaid,
                                onCheckedChange = { wantPaid ->
                                    if (wantPaid && !isFullyPaid) {
                                        payingBill = bill to remainingDue
                                    } else if (!wantPaid && (isFullyPaid || isPartiallyPaid)) {
                                        reversingBill = bill
                                    }
                                }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = bill.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = when {
                                        isFullyPaid -> "Paid this month ($currentYmStr)"
                                        isPartiallyPaid -> "Paid ${CurrencyFormatter.formatInr(paidThisMonth)} · ${CurrencyFormatter.formatInr(remainingDue)} left · Due day ${bill.dayOfMonth}"
                                        else -> "Reserved · Due day ${bill.dayOfMonth}"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isFullyPaid) semantic.mint else semantic.warning
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = CurrencyFormatter.formatInr(bill.amount),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                if (!isFullyPaid) {
                                    Text(
                                        text = if (isPartiallyPaid) "Pay rest" else "Record pay",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.clickable { payingBill = bill to remainingDue }
                                    )
                                } else {
                                    Text(
                                        text = "Reverse",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.clickable { reversingBill = bill }
                                    )
                                }
                            }
                            IconButton(onClick = { onDeleteRecurringPayment(bill.id) }) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "Delete bill",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditProfileDialog) {
        EditFinancialProfileDialog(
            initialIncome = summary.monthlyIncome,
            initialFixed = summary.fixedExpenses,
            initialSavings = summary.protectedSavings,
            onSave = { inc, fix, sav ->
                onUpdateFinancialProfile(inc, fix, sav)
                showEditProfileDialog = false
            },
            onDismiss = { showEditProfileDialog = false }
        )
    }

    if (showCreateCategoryDialog) {
        CreateCategoryDialog(
            onCreate = { name ->
                onCreateCategory(name)
                showCreateCategoryDialog = false
            },
            onDismiss = { showCreateCategoryDialog = false }
        )
    }

    editingCategory?.let { cat ->
        EditCategoryLimitDialog(
            breakdown = cat,
            onSave = { newName, newLimit ->
                val trimmed = newName.trim()
                if (trimmed.isNotEmpty() && trimmed != cat.categoryName) {
                    onRenameCategory(cat.categoryId, trimmed)
                }
                onUpdateCategoryBudget(cat.categoryId, newLimit)
                editingCategory = null
            },
            onDeleteCategory = {
                onDeleteCategory(cat.categoryId)
                editingCategory = null
            },
            onDismiss = { editingCategory = null }
        )
    }

    if (showAddBillDialog) {
        AddRecurringBillDialog(
            categories = uiState.categories,
            onSave = { title, amount, catId, day ->
                onAddRecurringPayment(title, amount, catId, day)
                showAddBillDialog = false
            },
            onDismiss = { showAddBillDialog = false }
        )
    }

    payingBill?.let { (bill, remainingDue) ->
        PayRecurringBillDialog(
            bill = bill,
            remainingDue = remainingDue,
            onConfirmPay = { amountToPay ->
                onPayRecurringBill(bill.id, amountToPay)
                payingBill = null
            },
            onDismiss = { payingBill = null }
        )
    }

    reversingBill?.let { bill ->
        AlertDialog(
            onDismissRequest = { reversingBill = null },
            title = { Text("Reverse Bill Payment?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Unmarking ${bill.title} will delete the linked payment transaction(s) for $currentYmStr and reserve ${CurrencyFormatter.formatInr(bill.amount)} again in upcoming bills."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUnmarkRecurringBill(bill.id)
                        reversingBill = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = semantic.danger)
                ) { Text("Reverse Payment") }
            },
            dismissButton = {
                TextButton(onClick = { reversingBill = null }) { Text("Keep Paid") }
            }
        )
    }
}

@Composable
private fun FormulaLine(label: String, amount: String, valueColor: Color = Color.White) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.78f)
        )
        Text(
            text = amount,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = valueColor
        )
    }
}

@Composable
private fun CategoryBudgetCard(
    breakdown: CategoryBreakdown,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = ArthikaTheme.colors
    val visuals = resolveCategoryVisuals(breakdown.categoryId, semantic)
    val barBrush = when (breakdown.guardLevel) {
        SpendingGuardLevel.GREEN -> semantic.mintBarBrush
        SpendingGuardLevel.ORANGE -> semantic.warningBarBrush
        SpendingGuardLevel.RED -> semantic.dangerBarBrush
    }
    val trackColor = semantic.neutralTint
    val animatedRatio by animateFloatAsState(
        targetValue = breakdown.usageRatio.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing),
        label = "category_usage_ratio"
    )

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, semantic.cardBorder),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(visuals.bg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = visuals.icon,
                        contentDescription = null,
                        tint = visuals.tint,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = breakdown.categoryName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = when {
                            breakdown.budgetLimit <= BigDecimal.ZERO -> "No limit set · Tap to set"
                            breakdown.remainingAmount >= BigDecimal.ZERO -> "${CurrencyFormatter.formatInr(breakdown.remainingAmount)} left"
                            else -> "Over by ${CurrencyFormatter.formatInr(breakdown.remainingAmount.abs())}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (breakdown.remainingAmount < BigDecimal.ZERO) semantic.danger else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = if (breakdown.budgetLimit > BigDecimal.ZERO) {
                        "${CurrencyFormatter.formatInr(breakdown.spentAmount)} / ${CurrencyFormatter.formatInr(breakdown.budgetLimit)}"
                    } else {
                        CurrencyFormatter.formatInr(breakdown.spentAmount)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
            ) {
                val radius = CornerRadius(size.height / 2f, size.height / 2f)
                drawRoundRect(color = trackColor, size = size, cornerRadius = radius)
                val fillWidth = size.width * animatedRatio
                if (fillWidth > 0f) {
                    drawRoundRect(
                        brush = barBrush,
                        size = Size(fillWidth, size.height),
                        cornerRadius = radius
                    )
                }
            }
        }
    }
}

@Composable
private fun EditFinancialProfileDialog(
    initialIncome: BigDecimal,
    initialFixed: BigDecimal,
    initialSavings: BigDecimal,
    onSave: (BigDecimal, BigDecimal, BigDecimal) -> Unit,
    onDismiss: () -> Unit
) {
    var income by remember { mutableStateOf(initialIncome.stripTrailingZeros().toPlainString()) }
    var fixed by remember { mutableStateOf(initialFixed.stripTrailingZeros().toPlainString()) }
    var savings by remember { mutableStateOf(initialSavings.stripTrailingZeros().toPlainString()) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val parsedIncome = income.toBigDecimalOrNull() ?: BigDecimal.ZERO
    val parsedFixed = fixed.toBigDecimalOrNull() ?: BigDecimal.ZERO
    val parsedSavings = savings.toBigDecimalOrNull() ?: BigDecimal.ZERO
    val overAllocated = parsedIncome > BigDecimal.ZERO && (parsedFixed + parsedSavings) > parsedIncome

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configure Monthly Budget", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = income,
                    onValueChange = {
                        if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                            income = it
                            errorMsg = null
                        }
                    },
                    label = { Text("Monthly Income (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                OutlinedTextField(
                    value = fixed,
                    onValueChange = {
                        if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                            fixed = it
                            errorMsg = null
                        }
                    },
                    label = { Text("Fixed Expenses (Rent, EMI)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                OutlinedTextField(
                    value = savings,
                    onValueChange = {
                        if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                            savings = it
                            errorMsg = null
                        }
                    },
                    label = { Text("Protected Savings Target") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                if (overAllocated) {
                    Text(
                        text = "Fixed expenses + protected savings exceed your income. Flexible budget will be ₹0.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ArthikaTheme.colors.warning
                    )
                }
                errorMsg?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val i = income.ifBlank { "0" }.toBigDecimalOrNull()
                    val f = fixed.ifBlank { "0" }.toBigDecimalOrNull()
                    val s = savings.ifBlank { "0" }.toBigDecimalOrNull()
                    if (i == null || f == null || s == null || i < BigDecimal.ZERO || f < BigDecimal.ZERO || s < BigDecimal.ZERO) {
                        errorMsg = "Enter valid non-negative numbers."
                    } else {
                        onSave(i, f, s)
                    }
                }
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun CreateCategoryDialog(
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Spending Category", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errorMsg = null
                    },
                    label = { Text("Category Name (e.g., Fitness)") },
                    isError = errorMsg != null,
                    singleLine = true
                )
                errorMsg?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val cleaned = name.trim()
                when {
                    cleaned.isBlank() -> errorMsg = "Enter a category name."
                    cleaned.length > 24 -> errorMsg = "Keep name within 24 characters."
                    else -> onCreate(cleaned)
                }
            }) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun EditCategoryLimitDialog(
    breakdown: CategoryBreakdown,
    onSave: (newName: String, newLimit: BigDecimal) -> Unit,
    onDeleteCategory: () -> Unit,
    onDismiss: () -> Unit
) {
    val semantic = ArthikaTheme.colors
    var nameInput by remember { mutableStateOf(breakdown.categoryName) }
    var limit by remember {
        mutableStateOf(
            if (breakdown.budgetLimit > BigDecimal.ZERO) {
                breakdown.budgetLimit.stripTrailingZeros().toPlainString()
            } else {
                ""
            }
        )
    }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete '${breakdown.categoryName}'?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Existing transactions and recurring bills in '${breakdown.categoryName}' will be safely moved to 'Other'.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDelete = false
                        onDeleteCategory()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = semantic.danger)
                ) { Text("Delete Category") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Category & Limit", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = {
                        nameInput = it
                        errorMsg = null
                    },
                    label = { Text("Category Name") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = limit,
                    onValueChange = {
                        if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                            limit = it
                            errorMsg = null
                        }
                    },
                    label = { Text("Monthly Limit (₹, 0 = no limit)") },
                    placeholder = { Text("0") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                errorMsg?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (breakdown.budgetLimit > BigDecimal.ZERO) {
                        TextButton(onClick = { onSave(nameInput.trim().ifBlank { breakdown.categoryName }, BigDecimal.ZERO) }) {
                            Text("Remove Limit")
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }
                    if (breakdown.categoryId != "other") {
                        TextButton(
                            onClick = { confirmDelete = true },
                            colors = ButtonDefaults.textButtonColors(contentColor = semantic.danger)
                        ) {
                            Text("Delete Category")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val cleanedName = nameInput.trim()
                val parsedLimit = if (limit.isBlank()) BigDecimal.ZERO else limit.toBigDecimalOrNull()
                when {
                    cleanedName.isBlank() -> errorMsg = "Category name cannot be empty."
                    cleanedName.length > 24 -> errorMsg = "Keep name within 24 characters."
                    parsedLimit == null || parsedLimit < BigDecimal.ZERO -> errorMsg = "Enter a valid monthly limit."
                    else -> onSave(cleanedName, parsedLimit)
                }
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddRecurringBillDialog(
    categories: List<CategoryEntity>,
    onSave: (String, BigDecimal, String, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val expenseCategories = remember(categories) {
        categories.filter { it.isExpenseCategory }.ifEmpty { categories }
    }
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var day by remember { mutableStateOf("20") }
    var catId by remember(expenseCategories) {
        mutableStateOf(
            expenseCategories.firstOrNull { it.id == "bills" }?.id
                ?: expenseCategories.firstOrNull()?.id
                ?: "bills"
        )
    }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Upcoming Bill", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        errorMsg = null
                    },
                    label = { Text("Bill Name (e.g., Mobile Plan)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = {
                        if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                            amount = it
                            errorMsg = null
                        }
                    },
                    label = { Text("Amount (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                OutlinedTextField(
                    value = day,
                    onValueChange = {
                        if (it.isEmpty() || it.all(Char::isDigit)) {
                            day = it
                            errorMsg = null
                        }
                    },
                    label = { Text("Due Day of Month (1-31)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    expenseCategories.forEach { c ->
                        FilterChip(
                            selected = catId == c.id,
                            onClick = { catId = c.id },
                            label = { Text(c.name) }
                        )
                    }
                }
                errorMsg?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amount.toBigDecimalOrNull()
                    val d = day.toIntOrNull()
                    when {
                        title.isBlank() -> errorMsg = "Enter a bill name."
                        amt == null || amt <= BigDecimal.ZERO -> errorMsg = "Enter a positive bill amount."
                        d == null || d !in 1..31 -> errorMsg = "Due day must be between 1 and 31."
                        else -> onSave(title.trim(), amt, catId, d)
                    }
                }
            ) { Text("Reserve") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun PayRecurringBillDialog(
    bill: RecurringPaymentEntity,
    remainingDue: BigDecimal,
    onConfirmPay: (BigDecimal?) -> Unit,
    onDismiss: () -> Unit
) {
    var payAmountInput by remember {
        mutableStateOf(remainingDue.stripTrailingZeros().toPlainString())
    }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pay ${bill.title}", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Remaining due for ${YearMonth.now()}: ${CurrencyFormatter.formatInrExact(remainingDue)}. Recording this payment creates one expense transaction and releases only the paid portion from upcoming reserves.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = payAmountInput,
                    onValueChange = {
                        if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                            payAmountInput = it
                            errorMsg = null
                        }
                    },
                    label = { Text("Payment Amount (₹)") },
                    prefix = { Text("₹ ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                errorMsg?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsed = payAmountInput.toBigDecimalOrNull()
                    when {
                        parsed == null || parsed <= BigDecimal.ZERO -> errorMsg = "Enter a valid amount greater than ₹0."
                        parsed > remainingDue -> errorMsg = "Amount exceeds remaining due (${CurrencyFormatter.formatInrExact(remainingDue)})."
                        else -> onConfirmPay(parsed)
                    }
                }
            ) { Text("Record Payment") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
