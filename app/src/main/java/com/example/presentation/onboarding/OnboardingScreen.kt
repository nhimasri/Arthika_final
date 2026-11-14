package com.example.presentation.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoGraph
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ArthikaTheme
import com.example.utils.NotificationHelper
import java.math.BigDecimal

@Composable
fun OnboardingScreen(
    onFinishOnboarding: (
        name: String,
        monthlyIncome: BigDecimal,
        fixedExpenses: BigDecimal,
        protectedSavings: BigDecimal,
        smsEnabled: Boolean,
        scanInboxIfPermitted: Boolean
    ) -> Unit
) {
    val context = LocalContext.current
    val semantic = ArthikaTheme.colors
    var step by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var incomeText by remember { mutableStateOf("") }
    var fixedText by remember { mutableStateOf("") }
    var savingsText by remember { mutableStateOf("") }
    var permissionNotice by remember { mutableStateOf<String?>(null) }

    val parsedIncome = incomeText.toBigDecimalOrNull() ?: BigDecimal.ZERO
    val parsedFixed = fixedText.toBigDecimalOrNull() ?: BigDecimal.ZERO
    val parsedSavings = savingsText.toBigDecimalOrNull() ?: BigDecimal.ZERO
    val allocationExceedsIncome = parsedIncome > BigDecimal.ZERO && (parsedFixed + parsedSavings) > parsedIncome

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val receiveGranted = results[Manifest.permission.RECEIVE_SMS] == true ||
            NotificationHelper.hasReceiveSmsPermission(context)
        val readGranted = results[Manifest.permission.READ_SMS] == true ||
            NotificationHelper.hasReadSmsPermission(context)
        when {
            receiveGranted -> {
                onFinishOnboarding(name, parsedIncome, parsedFixed, parsedSavings, true, readGranted)
            }
            readGranted -> {
                permissionNotice = "Automatic live SMS detection requires RECEIVE_SMS permission, which was declined. Only one-time inbox scanning or manual Paste SMS is available."
                onFinishOnboarding(name, parsedIncome, parsedFixed, parsedSavings, false, true)
            }
            else -> {
                permissionNotice = "SMS permissions were declined. You can continue using Arthika manually or paste bank SMS alerts anytime."
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val forward = targetState > initialState
                (slideInHorizontally(tween(260)) { width -> if (forward) width / 3 else -width / 3 } +
                    fadeIn(tween(240))) togetherWith
                    (slideOutHorizontally(tween(200)) { width -> if (forward) -width / 3 else width / 3 } +
                        fadeOut(tween(180)))
            },
            label = "onboarding_step_transition"
        ) { currentStep ->
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                if (currentStep == 0) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(semantic.heroBrush),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Shield,
                                contentDescription = "Arthika",
                                tint = semantic.mint,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Text(
                            text = "Step 1 of 2 · Financial Setup",
                            style = MaterialTheme.typography.labelLarge,
                            color = semantic.mint,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "Set up your monthly budget baseline.",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        Text(
                            text = "Enter your actual monthly numbers so Arthika can calculate your real Safe-to-Spend allowance. All data stays locally on your device.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Your Name") },
                            placeholder = { Text("e.g., Priya") },
                            leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("onboarding_name_input")
                        )

                        OutlinedTextField(
                            value = incomeText,
                            onValueChange = { if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d{0,2}$"))) incomeText = it },
                            label = { Text("Monthly Income (₹)") },
                            placeholder = { Text("0") },
                            prefix = { Text("₹ ") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("onboarding_income_input")
                        )

                        OutlinedTextField(
                            value = fixedText,
                            onValueChange = { if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d{0,2}$"))) fixedText = it },
                            label = { Text("Fixed Monthly Expenses (Rent, EMI)") },
                            placeholder = { Text("0") },
                            prefix = { Text("₹ ") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("onboarding_fixed_input")
                        )

                        OutlinedTextField(
                            value = savingsText,
                            onValueChange = { if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d{0,2}$"))) savingsText = it },
                            label = { Text("Protected Monthly Savings Target") },
                            placeholder = { Text("0") },
                            prefix = { Text("₹ ") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("onboarding_savings_input")
                        )

                        if (allocationExceedsIncome) {
                            Surface(
                                color = semantic.warningTint,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Fixed expenses + protected savings exceed your monthly income, leaving ₹0 flexible allowance.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = semantic.warning,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { step = 1 },
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("onboarding_next_button")
                        ) {
                            Text(
                                text = "Continue",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Step 2 of 2 · SMS Detection",
                                style = MaterialTheme.typography.labelLarge,
                                color = semantic.mint,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(onClick = { step = 0 }) {
                                Text("Back")
                            }
                        }

                        Text(
                            text = "Connect your transaction messages to Arthika.",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        Text(
                            text = "Arthika uses RECEIVE_SMS for live bank alert detection and READ_SMS for optional 30-day inbox import, strictly offline on your device.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        FeatureCard(
                            icon = Icons.Rounded.Sms,
                            iconTint = semantic.softBlue,
                            iconBg = semantic.softBlueTint,
                            title = "Automatic Bank & UPI Detection",
                            description = "Extracts debit, credit, merchant, and account references from supported bank alerts. Ambiguous messages wait for your review."
                        )

                        FeatureCard(
                            icon = Icons.Rounded.Lock,
                            iconTint = semantic.mint,
                            iconBg = semantic.mintTint,
                            title = "Strict Local Privacy",
                            description = "100% offline rule-based parsing. OTPs, promotional messages, and personal chats are ignored and never stored."
                        )

                        FeatureCard(
                            icon = Icons.Rounded.AutoGraph,
                            iconTint = semantic.warning,
                            iconBg = semantic.warningTint,
                            title = "Dynamic Safe-to-Spend Engine",
                            description = "Automatically recalculates your daily allowance after every confirmed expense and reserves upcoming bills."
                        )

                        if (permissionNotice != null) {
                            Surface(
                                color = semantic.warningTint,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = permissionNotice!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                val hasReceive = NotificationHelper.hasReceiveSmsPermission(context)
                                val hasRead = NotificationHelper.hasReadSmsPermission(context)

                                if (hasReceive) {
                                    onFinishOnboarding(name, parsedIncome, parsedFixed, parsedSavings, true, hasRead)
                                } else {
                                    val perms = buildList {
                                        add(Manifest.permission.RECEIVE_SMS)
                                        add(Manifest.permission.READ_SMS)
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            add(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                    }.toTypedArray()
                                    smsPermissionLauncher.launch(perms)
                                }
                            },
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("enable_sms_onboarding_button")
                        ) {
                            Text(
                                text = "Enable SMS Detection",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                onFinishOnboarding(name, parsedIncome, parsedFixed, parsedSavings, false, false)
                            },
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("continue_without_sms_button")
                        ) {
                            Text(
                                text = "Continue Without SMS",
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureCard(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    title: String,
    description: String
) {
    val semantic = ArthikaTheme.colors
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, semantic.cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
