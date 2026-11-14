package com.example.presentation.goals

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
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
import com.example.data.local.entity.NoSpendChallengeEntity
import com.example.data.local.entity.SavingsContributionEntity
import com.example.data.local.entity.SavingsGoalEntity
import com.example.presentation.ArthikaUiState
import com.example.ui.theme.ArthikaTheme
import com.example.utils.CurrencyFormatter
import com.example.utils.DateUtils
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@Composable
fun GoalsScreen(
    uiState: ArthikaUiState,
    onUpsertGoal: (id: Long, name: String, target: BigDecimal, current: BigDecimal, targetDateMillis: Long, iconKey: String) -> Unit,
    onContributeGoal: (goal: SavingsGoalEntity, amount: BigDecimal, note: String?) -> Unit,
    onUpdateContribution: (contributionId: Long, newAmount: BigDecimal, newNote: String?) -> Unit,
    onDeleteContribution: (contributionId: Long) -> Unit,
    onDeleteGoal: (goalId: Long) -> Unit,
    onCheckInChallenge: (challenge: NoSpendChallengeEntity) -> Unit,
    onAddChallenge: (title: String, targetDays: Int) -> Unit
) {
    val semantic = ArthikaTheme.colors
    var showCreateGoalDialog by remember { mutableStateOf(false) }
    var contributingGoal by remember { mutableStateOf<SavingsGoalEntity?>(null) }
    var historyGoalId by remember { mutableStateOf<Long?>(null) }
    var showNewChallengeDialog by remember { mutableStateOf(false) }

    val totalSaved = uiState.goals.fold(BigDecimal.ZERO) { acc, g -> acc + g.currentAmount }
    val totalTarget = uiState.goals.fold(BigDecimal.ZERO) { acc, g -> acc + g.targetAmount }

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
                            text = "Savings Goals",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Protected milestones & No-Spend streaks",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = { showCreateGoalDialog = true },
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        modifier = Modifier.testTag("add_goal_button")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("New Goal", maxLines = 1, softWrap = false)
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(semantic.heroBrush)
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(semantic.mint.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Savings,
                                contentDescription = null,
                                tint = semantic.mint,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Total Goal Savings",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.75f)
                            )
                            Text(
                                text = CurrencyFormatter.formatInr(totalSaved),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Across ${uiState.goals.size} active goals (Target ${CurrencyFormatter.formatInr(totalTarget)})",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.75f)
                            )
                        }
                    }
                }
            }

            if (uiState.goals.isEmpty()) {
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
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No savings goals yet",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tap 'New Goal' to start tracking milestones like an Emergency Fund or Laptop.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(uiState.goals, key = { it.id }) { goal ->
                    val contributions = uiState.goalContributionsByGoalId[goal.id].orEmpty()
                    SavingsGoalCard(
                        goal = goal,
                        contributionCount = contributions.size,
                        onContributeClick = { contributingGoal = goal },
                        onHistoryClick = { historyGoalId = goal.id },
                        onDeleteClick = { onDeleteGoal(goal.id) },
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
                        text = "No-Spend Challenges",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = { showNewChallengeDialog = true }) {
                        Text("+ Start Challenge")
                    }
                }
            }

            if (uiState.challenges.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, semantic.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Start a No-Spend Challenge to build daily spending discipline.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(uiState.challenges, key = { it.id }) { challenge ->
                    val checkedToday = challenge.lastCheckedDateIso == LocalDate.now().toString()
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, semantic.cardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(semantic.warningTint),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.LocalFireDepartment,
                                    contentDescription = null,
                                    tint = semantic.warning
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = challenge.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${challenge.completedDays} / ${challenge.targetDays} days completed",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(
                                onClick = { onCheckInChallenge(challenge) },
                                enabled = !checkedToday && challenge.completedDays < challenge.targetDays,
                                shape = RoundedCornerShape(50)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircleOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (checkedToday) "Done" else "Check in")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateGoalDialog) {
        CreateGoalDialog(
            onSave = { name, target, initial, months ->
                val targetDateMillis = LocalDate.now()
                    .plusMonths(months.toLong().coerceAtLeast(1))
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
                onUpsertGoal(0L, name, target, initial, targetDateMillis, "flag")
                showCreateGoalDialog = false
            },
            onDismiss = { showCreateGoalDialog = false }
        )
    }

    contributingGoal?.let { goal ->
        ContributeGoalDialog(
            goal = goal,
            onContribute = { amount, note ->
                onContributeGoal(goal, amount, note)
                contributingGoal = null
            },
            onDismiss = { contributingGoal = null }
        )
    }

    historyGoalId?.let { goalId ->
        val selectedGoal = uiState.goals.firstOrNull { it.id == goalId }
        if (selectedGoal != null) {
            val contributions = uiState.goalContributionsByGoalId[goalId].orEmpty()
            GoalContributionsHistoryDialog(
                goal = selectedGoal,
                contributions = contributions,
                onUpdateContribution = onUpdateContribution,
                onDeleteContribution = onDeleteContribution,
                onDismiss = { historyGoalId = null }
            )
        } else {
            historyGoalId = null
        }
    }

    if (showNewChallengeDialog) {
        CreateChallengeDialog(
            onSave = { title, days ->
                onAddChallenge(title, days)
                showNewChallengeDialog = false
            },
            onDismiss = { showNewChallengeDialog = false }
        )
    }
}

@Composable
private fun SavingsGoalCard(
    goal: SavingsGoalEntity,
    contributionCount: Int,
    onContributeClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = ArthikaTheme.colors
    val progress = if (goal.targetAmount > BigDecimal.ZERO) {
        goal.currentAmount.divide(goal.targetAmount, 4, RoundingMode.HALF_UP).toFloat().coerceIn(0f, 1f)
    } else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing),
        label = "goal_progress"
    )
    val pct = if (goal.targetAmount > BigDecimal.ZERO && goal.currentAmount >= goal.targetAmount) {
        100
    } else {
        (progress * 100f).toInt().coerceIn(0, 99)
    }
    val remaining = (goal.targetAmount - goal.currentAmount).coerceAtLeast(BigDecimal.ZERO)
    val isFunded = goal.targetAmount > BigDecimal.ZERO && remaining <= BigDecimal.ZERO

    val today = LocalDate.now()
    val targetDate = DateUtils.toLocalDate(goal.targetDateEpochMillis)
    val daysUntil = ChronoUnit.DAYS.between(today, targetDate).coerceAtLeast(7)
    val weeksUntil = (daysUntil / 7.0).coerceAtLeast(1.0)
    val monthsUntil = (daysUntil / 30.0).coerceAtLeast(1.0)
    val weeklyContribution = remaining.divide(BigDecimal.valueOf(weeksUntil), 0, RoundingMode.CEILING)
    val monthlyContribution = remaining.divide(BigDecimal.valueOf(monthsUntil), 0, RoundingMode.CEILING)

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, semantic.cardBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(semantic.mintTint),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Flag,
                        contentDescription = null,
                        tint = semantic.mint,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = goal.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Target date: ${DateUtils.formatDate(goal.targetDateEpochMillis)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    color = semantic.mintTint,
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = "$pct%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = semantic.mint,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${CurrencyFormatter.formatInr(goal.currentAmount)} saved",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = semantic.mint
                )
                Text(
                    text = "Goal: ${CurrencyFormatter.formatInr(goal.targetAmount)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            val trackColor = semantic.neutralTint
            val barBrush = semantic.mintBarBrush
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
            ) {
                val radius = CornerRadius(size.height / 2f, size.height / 2f)
                drawRoundRect(color = trackColor, size = size, cornerRadius = radius)
                val fillW = size.width * animatedProgress
                if (fillW > 0f) {
                    drawRoundRect(brush = barBrush, size = Size(fillW, size.height), cornerRadius = radius)
                }
            }

            Text(
                text = if (isFunded) {
                    "Goal 100% funded!"
                } else {
                    "Remaining: ${CurrencyFormatter.formatInr(remaining)} · Weekly: ${CurrencyFormatter.formatInr(weeklyContribution)} · Monthly: ${CurrencyFormatter.formatInr(monthlyContribution)}"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (isFunded) semantic.mint else MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onHistoryClick,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (contributionCount > 0) "History ($contributionCount)" else "History",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDeleteClick) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "Delete goal",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = onContributeClick,
                        enabled = !isFunded,
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = if (isFunded) "Funded" else "+ Contribute",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateGoalDialog(
    onSave: (name: String, target: BigDecimal, current: BigDecimal, months: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var current by remember { mutableStateOf("") }
    var months by remember { mutableStateOf("6") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Savings Goal", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errorMsg = null
                    },
                    label = { Text("Goal Name (e.g., Trip, Laptop)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = target,
                    onValueChange = {
                        if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                            target = it
                            errorMsg = null
                        }
                    },
                    label = { Text("Target Amount (₹)") },
                    placeholder = { Text("0") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                OutlinedTextField(
                    value = current,
                    onValueChange = {
                        if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                            current = it
                            errorMsg = null
                        }
                    },
                    label = { Text("Already Saved (₹)") },
                    placeholder = { Text("0") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                OutlinedTextField(
                    value = months,
                    onValueChange = {
                        if (it.isEmpty() || it.all(Char::isDigit)) {
                            months = it
                            errorMsg = null
                        }
                    },
                    label = { Text("Months to Target (1-600)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
                val t = target.toBigDecimalOrNull()
                val c = if (current.isBlank()) BigDecimal.ZERO else current.toBigDecimalOrNull()
                val m = months.toIntOrNull()
                when {
                    name.isBlank() -> errorMsg = "Enter a goal name."
                    t == null || t <= BigDecimal.ZERO -> errorMsg = "Target amount must be greater than ₹0."
                    c == null || c < BigDecimal.ZERO -> errorMsg = "Saved amount cannot be negative."
                    c > t -> errorMsg = "Initial saved cannot exceed the goal target."
                    m == null || m !in 1..600 -> errorMsg = "Months must be between 1 and 600."
                    else -> onSave(name.trim(), t, c, m)
                }
            }) { Text("Save Goal") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ContributeGoalDialog(
    goal: SavingsGoalEntity,
    onContribute: (BigDecimal, String?) -> Unit,
    onDismiss: () -> Unit
) {
    val remaining = (goal.targetAmount - goal.currentAmount).coerceAtLeast(BigDecimal.ZERO)
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Contribute to ${goal.name}", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Remaining to target: ${CurrencyFormatter.formatInrExact(remaining)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = {
                        if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                            amount = it
                            errorMsg = null
                        }
                    },
                    label = { Text("Contribution Amount (₹)") },
                    placeholder = { Text("0") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
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
                val amt = amount.toBigDecimalOrNull()
                when {
                    amt == null || amt <= BigDecimal.ZERO -> errorMsg = "Enter a positive contribution amount."
                    amt > remaining -> errorMsg = "Amount exceeds remaining target (${CurrencyFormatter.formatInrExact(remaining)})."
                    else -> onContribute(amt, note.ifBlank { null })
                }
            }) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun GoalContributionsHistoryDialog(
    goal: SavingsGoalEntity,
    contributions: List<SavingsContributionEntity>,
    onUpdateContribution: (Long, BigDecimal, String?) -> Unit,
    onDeleteContribution: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var editingContribution by remember { mutableStateOf<SavingsContributionEntity?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${goal.name} Contributions", fontWeight = FontWeight.Bold) },
        text = {
            if (contributions.isEmpty()) {
                Text(
                    text = "No contribution entries recorded yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(contributions, key = { it.id }) { item ->
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = CurrencyFormatter.formatInrExact(item.amount),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = ArthikaTheme.colors.mint
                                    )
                                    Text(
                                        text = buildString {
                                            append(DateUtils.formatDate(item.timestamp))
                                            if (!item.note.isNullOrBlank()) {
                                                append(" · ")
                                                append(item.note)
                                            }
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { editingContribution = item }) {
                                    Icon(Icons.Rounded.Edit, contentDescription = "Edit contribution", modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = { onDeleteContribution(item.id) }) {
                                    Icon(
                                        Icons.Rounded.DeleteOutline,
                                        contentDescription = "Delete contribution",
                                        tint = ArthikaTheme.colors.danger,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            HorizontalDivider(color = ArthikaTheme.colors.cardBorder)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )

    editingContribution?.let { existing ->
        val maxAllowed = (goal.targetAmount - (goal.currentAmount - existing.amount).coerceAtLeast(BigDecimal.ZERO))
            .coerceAtLeast(existing.amount)
        EditContributionDialog(
            contribution = existing,
            maxAllowed = maxAllowed,
            onSave = { newAmt, newNote ->
                onUpdateContribution(existing.id, newAmt, newNote)
                editingContribution = null
            },
            onDismiss = { editingContribution = null }
        )
    }
}

@Composable
private fun EditContributionDialog(
    contribution: SavingsContributionEntity,
    maxAllowed: BigDecimal,
    onSave: (BigDecimal, String?) -> Unit,
    onDismiss: () -> Unit
) {
    var amount by remember { mutableStateOf(contribution.amount.stripTrailingZeros().toPlainString()) }
    var note by remember { mutableStateOf(contribution.note.orEmpty()) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Contribution", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Max allowed within goal target: ${CurrencyFormatter.formatInrExact(maxAllowed)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
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
                val amt = amount.toBigDecimalOrNull()
                when {
                    amt == null || amt <= BigDecimal.ZERO -> errorMsg = "Enter a positive amount."
                    amt > maxAllowed -> errorMsg = "Exceeds max allowed (${CurrencyFormatter.formatInrExact(maxAllowed)})."
                    else -> onSave(amt, note.ifBlank { null })
                }
            }) { Text("Update") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun CreateChallengeDialog(
    onSave: (String, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var days by remember { mutableStateOf("3") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New No-Spend Challenge", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        errorMsg = null
                    },
                    label = { Text("Challenge Title") },
                    placeholder = { Text("e.g., 3-Day Zero Impulse Spend") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = days,
                    onValueChange = {
                        if (it.isEmpty() || it.all(Char::isDigit)) {
                            days = it
                            errorMsg = null
                        }
                    },
                    label = { Text("Target Days (1-30)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
                val d = days.toIntOrNull()
                when {
                    title.isBlank() -> errorMsg = "Enter a challenge title."
                    d == null || d !in 1..30 -> errorMsg = "Target days must be between 1 and 30."
                    else -> onSave(title.trim(), d)
                }
            }) { Text("Start") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
