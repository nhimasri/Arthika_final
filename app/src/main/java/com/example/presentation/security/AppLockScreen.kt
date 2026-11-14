package com.example.presentation.security

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ArthikaTheme
import kotlinx.coroutines.delay

@Composable
fun AppLockScreen(
    hasPinConfigured: Boolean,
    biometricAvailable: Boolean,
    biometricEnabled: Boolean,
    onVerifyPin: (String) -> Boolean,
    onRequestBiometric: () -> Unit,
    onUnlocked: () -> Unit
) {
    // Prevent hardware back button from bypassing the lock screen
    BackHandler(enabled = true) { }

    val semantic = ArthikaTheme.colors
    val haptics = LocalHapticFeedback.current

    var enteredPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var failedAttempts by remember { mutableIntStateOf(0) }
    var cooldownSeconds by remember { mutableIntStateOf(0) }

    LaunchedEffect(cooldownSeconds) {
        if (cooldownSeconds > 0) {
            delay(1000L)
            cooldownSeconds -= 1
            if (cooldownSeconds == 0) {
                errorMessage = null
            }
        }
    }

    LaunchedEffect(biometricAvailable, biometricEnabled) {
        if (biometricAvailable && biometricEnabled) {
            onRequestBiometric()
        }
    }

    fun appendDigit(digit: String) {
        if (cooldownSeconds > 0 || !hasPinConfigured || enteredPin.length >= 4) return
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        errorMessage = null
        val updated = enteredPin + digit
        enteredPin = updated
        if (updated.length == 4) {
            if (onVerifyPin(updated)) {
                enteredPin = ""
                failedAttempts = 0
                onUnlocked()
            } else {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                enteredPin = ""
                failedAttempts += 1
                if (failedAttempts >= 5) {
                    cooldownSeconds = 30
                    errorMessage = "Too many attempts. Try again in 30s or use Biometrics."
                } else {
                    errorMessage = "Incorrect PIN (${5 - failedAttempts} attempts left)"
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(semantic.heroBrush)
            .safeDrawingPadding()
            .testTag("vault_lock_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(semantic.mint.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = "Arthika Vault Locked",
                        tint = semantic.mint,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "Arthika Vault Locked",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (hasPinConfigured) {
                        "Enter your 4-digit PIN or verify biometrics to view your balances"
                    } else {
                        "Verify biometrics or device lock to access your encrypted data"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.78f),
                    textAlign = TextAlign.Center
                )

                if (hasPinConfigured) {
                    Spacer(modifier = Modifier.height(28.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        repeat(4) { index ->
                            val filled = index < enteredPin.length
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (filled) semantic.mint else Color.White.copy(alpha = 0.25f)
                                    )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                AnimatedVisibility(
                    visible = errorMessage != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Text(
                        text = if (cooldownSeconds > 0) {
                            "Vault temporarily locked (${cooldownSeconds}s)"
                        } else {
                            errorMessage.orEmpty()
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = semantic.danger,
                        textAlign = TextAlign.Center
                    )
                }
            }

            if (hasPinConfigured) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val rows = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9")
                    )
                    rows.forEach { rowDigits ->
                        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                            rowDigits.forEach { d ->
                                PinKeyButton(
                                    label = d,
                                    enabled = cooldownSeconds == 0,
                                    onClick = { appendDigit(d) },
                                    testTag = "pin_digit_$d"
                                )
                            }
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(22.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (biometricAvailable && biometricEnabled) {
                            Surface(
                                shape = CircleShape,
                                color = semantic.mint.copy(alpha = 0.16f),
                                border = BorderStroke(1.dp, semantic.mint.copy(alpha = 0.4f)),
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(CircleShape)
                                    .clickable(onClick = onRequestBiometric)
                                    .testTag("biometric_unlock_button")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Fingerprint,
                                        contentDescription = "Unlock with biometrics",
                                        tint = semantic.mint,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.size(68.dp))
                        }

                        PinKeyButton(
                            label = "0",
                            enabled = cooldownSeconds == 0,
                            onClick = { appendDigit("0") },
                            testTag = "pin_digit_0"
                        )

                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.10f),
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .clickable(enabled = enteredPin.isNotEmpty()) {
                                    enteredPin = enteredPin.dropLast(1)
                                }
                                .testTag("pin_backspace_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Backspace,
                                    contentDescription = "Delete PIN digit",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            } else if (biometricAvailable) {
                OutlinedButton(
                    onClick = onRequestBiometric,
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, semantic.mint),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("biometric_unlock_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Fingerprint,
                        contentDescription = null,
                        tint = semantic.mint
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Unlock with Biometrics", fontWeight = FontWeight.SemiBold)
                }
            }

            Text(
                text = "AES-256 Encrypted Local Vault · Android Keystore Protected",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.55f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun PinKeyButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        shape = CircleShape,
        color = Color.White.copy(alpha = if (enabled) 0.12f else 0.05f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
        modifier = Modifier
            .size(68.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(testTag)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
    }
}
