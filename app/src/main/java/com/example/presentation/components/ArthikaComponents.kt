package com.example.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.TransactionEntity
import com.example.domain.model.FinancialSummary
import com.example.domain.model.SpendingGuardLevel
import com.example.domain.model.TransactionSource
import com.example.domain.model.TransactionStatus
import com.example.domain.model.TransactionType
import com.example.ui.theme.ArthikaTheme
import com.example.utils.CurrencyFormatter
import java.math.BigDecimal

@Composable
fun SafeToSpendHeroCard(
    summary: FinancialSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = ArthikaTheme.colors
    val targetRingStart = when (summary.guardLevel) {
        SpendingGuardLevel.GREEN -> semantic.mint
        SpendingGuardLevel.ORANGE -> semantic.warning
        SpendingGuardLevel.RED -> semantic.danger
    }
    val targetRingEnd = when (summary.guardLevel) {
        SpendingGuardLevel.GREEN -> semantic.mintEnd
        SpendingGuardLevel.ORANGE -> semantic.warningEnd
        SpendingGuardLevel.RED -> semantic.dangerEnd
    }
    val ringColor by animateColorAsState(
        targetValue = targetRingStart,
        animationSpec = tween(durationMillis = 450),
        label = "safe_to_spend_ring_start"
    )
    val ringColorEnd by animateColorAsState(
        targetValue = targetRingEnd,
        animationSpec = tween(durationMillis = 450),
        label = "safe_to_spend_ring_end"
    )

    val remainingRatio = if (summary.dailyAllowance > BigDecimal.ZERO) {
        (summary.remainingToday.coerceAtLeast(BigDecimal.ZERO).toFloat() /
            summary.dailyAllowance.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    val animatedSweep by animateFloatAsState(
        targetValue = remainingRatio * 360f,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "safe_to_spend_sweep"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("safe_to_spend_card")
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(semantic.heroBrush)
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Today's Safe-to-Spend",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                SpendingGuardPill(level = summary.guardLevel)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(124.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(124.dp)) {
                        val strokeWidth = 11.dp.toPx()
                        val diameter = size.minDimension - strokeWidth
                        val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
                        val arcSize = Size(diameter, diameter)

                        drawArc(
                            color = semantic.heroCardTrack,
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                        drawArc(
                            brush = Brush.linearGradient(listOf(ringColor, ringColorEnd)),
                            startAngle = -90f,
                            sweepAngle = animatedSweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AnimatedContent(
                            targetState = CurrencyFormatter.formatInr(summary.dailyAllowance),
                            transitionSpec = {
                                fadeIn(tween(220)) togetherWith fadeOut(tween(150))
                            },
                            label = "daily_allowance_value"
                        ) { formattedLimit ->
                            Text(
                                text = formattedLimit,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Text(
                            text = "daily limit",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.72f)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(24.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(ringColor)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Spent today",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.75f)
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = CurrencyFormatter.formatInr(summary.spentToday),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(ringColor.copy(alpha = 0.55f))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Remaining",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.75f)
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = CurrencyFormatter.formatInr(summary.remainingToday),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (summary.remainingToday < BigDecimal.ZERO) semantic.danger else Color.White,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                color = semantic.heroCardFooterBg,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = summary.revisionExplanation,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.9f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun SpendingGuardPill(level: SpendingGuardLevel, modifier: Modifier = Modifier) {
    val semantic = ArthikaTheme.colors
    val (bg, fg) = when (level) {
        SpendingGuardLevel.GREEN -> semantic.mintTint to semantic.mint
        SpendingGuardLevel.ORANGE -> semantic.warningTint to semantic.warning
        SpendingGuardLevel.RED -> semantic.dangerTint to semantic.danger
    }
    Surface(
        modifier = modifier,
        color = bg,
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text = level.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun TransactionRowCard(
    transaction: TransactionEntity,
    category: CategoryEntity?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = ArthikaTheme.colors
    val (icon, tint, bg) = resolveTransactionVisuals(transaction, category, semantic)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("transaction_card_${transaction.id}")
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, semantic.cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(bg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = category?.name ?: transaction.title,
                    tint = tint,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                val subtitle = when {
                    transaction.isTransfer -> "Own-account transfer"
                    transaction.status == TransactionStatus.NEEDS_REVIEW -> "Uncategorized · Tap to confirm"
                    transaction.type == TransactionType.CREDIT -> "Credit · ${transaction.paymentMethod.label}"
                    else -> "${category?.name ?: "Other"} · ${transaction.paymentMethod.label}"
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                val amountColor = when {
                    transaction.isTransfer -> MaterialTheme.colorScheme.onSurfaceVariant
                    transaction.type == TransactionType.CREDIT -> semantic.mint
                    else -> MaterialTheme.colorScheme.onSurface
                }
                val amountPrefix = if (transaction.type == TransactionType.CREDIT && !transaction.isTransfer) "+" else ""
                Text(
                    text = "$amountPrefix${CurrencyFormatter.formatInr(transaction.amount, showDecimalsIfNonZero = true)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = amountColor
                )
                Spacer(modifier = Modifier.height(4.dp))
                TransactionBadgePill(transaction = transaction)
            }

            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = "Inspect transaction",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun TransactionBadgePill(transaction: TransactionEntity) {
    val semantic = ArthikaTheme.colors
    val (label, bg, fg) = when {
        transaction.isTransfer -> Triple("Transfer", semantic.neutralTint, MaterialTheme.colorScheme.onSurfaceVariant)
        transaction.status == TransactionStatus.NEEDS_REVIEW -> Triple("Needs review", semantic.warningTint, semantic.warning)
        transaction.type == TransactionType.CREDIT -> Triple("Credit", semantic.mintTint, semantic.mint)
        transaction.source == TransactionSource.SMS -> Triple("SMS detected", semantic.softBlueTint, semantic.softBlue)
        else -> Triple("Manual", semantic.neutralTint, MaterialTheme.colorScheme.onSurfaceVariant)
    }

    Surface(
        color = bg,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
        )
    }
}

data class CategoryVisuals(
    val icon: ImageVector,
    val tint: Color,
    val bg: Color
)

@Composable
fun resolveTransactionVisuals(
    transaction: TransactionEntity,
    category: CategoryEntity?,
    semantic: com.example.ui.theme.ArthikaSemanticColors
): CategoryVisuals {
    if (transaction.isTransfer) {
        return CategoryVisuals(Icons.Rounded.SwapHoriz, semantic.softBlue, semantic.softBlueTint)
    }
    if (transaction.status == TransactionStatus.NEEDS_REVIEW) {
        return CategoryVisuals(Icons.AutoMirrored.Rounded.HelpOutline, semantic.warning, semantic.warningTint)
    }
    if (transaction.type == TransactionType.CREDIT) {
        return CategoryVisuals(Icons.Rounded.ArrowDownward, semantic.mint, semantic.mintTint)
    }
    if (transaction.title.contains("ATM", ignoreCase = true)) {
        return CategoryVisuals(Icons.Rounded.AccountBalance, semantic.softBlue, semantic.softBlueTint)
    }
    return resolveCategoryVisuals(category?.id ?: "other", semantic)
}

fun resolveCategoryVisuals(
    categoryId: String,
    semantic: com.example.ui.theme.ArthikaSemanticColors
): CategoryVisuals {
    return when (categoryId) {
        "food" -> CategoryVisuals(Icons.Rounded.Restaurant, semantic.danger, semantic.dangerTint)
        "transportation" -> CategoryVisuals(Icons.Rounded.DirectionsCar, semantic.softBlue, semantic.softBlueTint)
        "shopping" -> CategoryVisuals(Icons.Rounded.ShoppingBag, semantic.mint, semantic.mintTint)
        "bills" -> CategoryVisuals(Icons.Rounded.Bolt, semantic.warning, semantic.warningTint)
        "entertainment" -> CategoryVisuals(Icons.Rounded.Movie, semantic.softBlue, semantic.softBlueTint)
        "healthcare" -> CategoryVisuals(Icons.Rounded.LocalHospital, semantic.danger, semantic.dangerTint)
        "education" -> CategoryVisuals(Icons.Rounded.School, semantic.softBlue, semantic.softBlueTint)
        else -> CategoryVisuals(Icons.Rounded.Category, semantic.softBlue, semantic.neutralTint)
    }
}

@Composable
fun OverviewMetricTile(
    label: String,
    value: String,
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    modifier: Modifier = Modifier
) {
    val semantic = ArthikaTheme.colors
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, semantic.cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }
        }
    }
}
