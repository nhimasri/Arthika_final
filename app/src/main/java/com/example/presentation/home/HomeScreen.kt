package com.example.presentation.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.AutoGraph
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.entity.TransactionEntity
import com.example.domain.model.SpendingGuardLevel
import com.example.domain.model.TransactionStatus
import com.example.domain.model.TransactionType
import com.example.presentation.ArthikaUiState
import com.example.presentation.components.OverviewMetricTile
import com.example.presentation.components.SafeToSpendHeroCard
import com.example.presentation.components.TransactionRowCard
import com.example.ui.theme.ArthikaTheme
import com.example.utils.CurrencyFormatter
import com.example.utils.DateUtils
import java.time.YearMonth

@Composable
fun HomeScreen(
    uiState: ArthikaUiState,
    onNavigateToTransactions: () -> Unit,
    onNavigateToBudgets: () -> Unit,
    onNavigateToGoals: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onOpenAddTransaction: (TransactionType) -> Unit,
    onOpenWhatIf: () -> Unit,
    onOpenSmsImport: () -> Unit,
    onOpenNotifications: () -> Unit,
    onTransactionClick: (TransactionEntity) -> Unit
) {
    val semantic = ArthikaTheme.colors
    val summary = uiState.summary

    if (!uiState.isLoaded || summary == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val categoryMap = remember(uiState.categories) { uiState.categories.associateBy { it.id } }
    val pendingReview = uiState.pendingReviewTransactions
    val latestConfirmedFive = remember(uiState.transactions) {
        uiState.transactions.filter { it.status == TransactionStatus.CONFIRMED }.take(5)
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        val greeting = DateUtils.greetingForHour()
                        val displayGreeting = if (uiState.userName.isNotBlank()) "$greeting, ${uiState.userName}" else greeting
                        Text(
                            text = displayGreeting,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = DateUtils.formatMonthYear(YearMonth.now()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onOpenNotifications,
                            modifier = Modifier.testTag("home_notifications_button")
                        ) {
                            BadgedBox(
                                badge = {
                                    if (uiState.unreadNotificationCount > 0) {
                                        Badge {
                                            Text(uiState.unreadNotificationCount.toString())
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.NotificationsNone,
                                    contentDescription = "Alerts and notifications"
                                )
                            }
                        }
                        IconButton(
                            onClick = onNavigateToProfile,
                            modifier = Modifier.testTag("home_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = "Settings and profile"
                            )
                        }
                    }
                }
            }

            item {
                SafeToSpendHeroCard(
                    summary = summary,
                    onClick = onNavigateToBudgets
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OverviewMetricTile(
                            label = "Income",
                            value = CurrencyFormatter.formatInr(summary.monthlyIncome + summary.extraCreditsThisMonth),
                            icon = Icons.Rounded.AccountBalanceWallet,
                            iconTint = semantic.mint,
                            iconBg = semantic.mintTint,
                            modifier = Modifier.weight(1f)
                        )
                        OverviewMetricTile(
                            label = "Expenses",
                            value = CurrencyFormatter.formatInr(summary.totalExpensesThisMonth),
                            icon = Icons.Rounded.CreditCard,
                            iconTint = semantic.danger,
                            iconBg = semantic.dangerTint,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OverviewMetricTile(
                            label = "Protected savings",
                            value = CurrencyFormatter.formatInr(summary.protectedSavings),
                            icon = Icons.Rounded.Shield,
                            iconTint = semantic.mint,
                            iconBg = semantic.mintTint,
                            modifier = Modifier.weight(1f)
                        )
                        OverviewMetricTile(
                            label = "Flexible left",
                            value = CurrencyFormatter.formatInr(summary.remainingFlexibleBudget),
                            icon = Icons.Rounded.PieChart,
                            iconTint = semantic.softBlue,
                            iconBg = semantic.softBlueTint,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickActionPill(
                        label = "Add Expense",
                        icon = Icons.Rounded.Add,
                        tint = semantic.danger,
                        bg = semantic.dangerTint,
                        onClick = { onOpenAddTransaction(TransactionType.DEBIT) },
                        testTag = "quick_add_expense"
                    )
                    QuickActionPill(
                        label = "Add Income",
                        icon = Icons.Rounded.ArrowDownward,
                        tint = semantic.mint,
                        bg = semantic.mintTint,
                        onClick = { onOpenAddTransaction(TransactionType.CREDIT) },
                        testTag = "quick_add_income"
                    )
                    QuickActionPill(
                        label = "What-If Sim",
                        icon = Icons.Rounded.AutoGraph,
                        tint = semantic.softBlue,
                        bg = semantic.softBlueTint,
                        onClick = onOpenWhatIf,
                        testTag = "quick_what_if"
                    )
                    QuickActionPill(
                        label = "Import SMS",
                        icon = Icons.Rounded.Sms,
                        tint = MaterialTheme.colorScheme.primary,
                        bg = semantic.neutralTint,
                        onClick = onOpenSmsImport,
                        testTag = "quick_import_sms"
                    )
                    QuickActionPill(
                        label = "Savings Goals",
                        icon = Icons.Rounded.TrackChanges,
                        tint = semantic.mint,
                        bg = semantic.mintTint,
                        onClick = onNavigateToGoals,
                        testTag = "quick_goals"
                    )
                }
            }

            item {
                AnimatedVisibility(
                    visible = uiState.alerts.isNotEmpty(),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    uiState.alerts.firstOrNull()?.let { topAlert ->
                        val (alertBg, alertAccent) = when (topAlert.level) {
                            SpendingGuardLevel.GREEN -> semantic.mintTint to semantic.mint
                            SpendingGuardLevel.ORANGE -> semantic.warningTint to semantic.warning
                            SpendingGuardLevel.RED -> semantic.dangerTint to semantic.danger
                        }
                        Card(
                            onClick = onOpenNotifications,
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.2.dp, alertAccent.copy(alpha = 0.65f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("home_active_alert_card")
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(alertBg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.WarningAmber,
                                        contentDescription = null,
                                        tint = alertAccent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = topAlert.title,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = alertAccent,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = topAlert.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                    contentDescription = "View alerts",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            item {
                AnimatedVisibility(
                    visible = uiState.insights.isNotEmpty(),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    uiState.insights.firstOrNull()?.let { primaryInsight ->
                        val (bg, accent) = when (primaryInsight.level) {
                            SpendingGuardLevel.GREEN -> semantic.mintTint to semantic.mint
                            SpendingGuardLevel.ORANGE -> semantic.warningTint to semantic.warning
                            SpendingGuardLevel.RED -> semantic.dangerTint to semantic.danger
                        }
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (primaryInsight.id == "pending_sms_review") {
                                        onNavigateToTransactions()
                                    } else {
                                        onNavigateToBudgets()
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(bg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Lightbulb,
                                        contentDescription = null,
                                        tint = accent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Arthika Insight",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = accent,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = primaryInsight.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            if (pendingReview.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Needs Review (${pendingReview.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = semantic.warning
                        )
                        Text(
                            text = "Excluded from totals",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(pendingReview.take(3), key = { "pending_${it.id}" }) { txn ->
                    TransactionRowCard(
                        transaction = txn,
                        category = categoryMap[txn.categoryId],
                        onClick = { onTransactionClick(txn) },
                        modifier = Modifier.animateItem()
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Latest Transactions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onNavigateToTransactions)
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                            .testTag("see_all_transactions"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "See all",
                            style = MaterialTheme.typography.labelLarge,
                            color = semantic.softBlue
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = semantic.softBlue,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (latestConfirmedFive.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, semantic.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No confirmed transactions yet",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (pendingReview.isNotEmpty()) {
                                    "Confirm pending detections above or tap + to add an expense."
                                } else {
                                    "Tap + to add an expense or import a bank SMS alert."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(latestConfirmedFive, key = { it.id }) { txn ->
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

@Composable
private fun QuickActionPill(
    label: String,
    icon: ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    bg: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    testTag: String
) {
    val semantic = ArthikaTheme.colors
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, semantic.cardBorder),
        modifier = Modifier.testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(bg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(15.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
