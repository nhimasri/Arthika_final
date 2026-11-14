package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.ThemePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal

private val Context.arthikaDataStore: DataStore<Preferences> by preferencesDataStore(name = "arthika_prefs")

data class UserPreferences(
    val onboardingCompleted: Boolean = false,
    val smsDetectionEnabled: Boolean = false,
    val dailyAlertsEnabled: Boolean = true,
    val themePreference: ThemePreference = ThemePreference.SYSTEM,
    val singlePaymentLimit: BigDecimal? = null,
    val appLockEnabled: Boolean = false,
    val biometricUnlockEnabled: Boolean = true,
    val screenPrivacyEnabled: Boolean = true,
    val pinHash: String? = null,
    val pinSalt: String? = null
) {
    val hasPinConfigured: Boolean
        get() = !pinHash.isNullOrBlank() && !pinSalt.isNullOrBlank()
}

class UserPreferencesDataStore(private val context: Context) {

    private object Keys {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val SMS_DETECTION_ENABLED = booleanPreferencesKey("sms_detection_enabled")
        val DAILY_ALERTS_ENABLED = booleanPreferencesKey("daily_alerts_enabled")
        val THEME_PREFERENCE = stringPreferencesKey("theme_preference")
        val SINGLE_PAYMENT_LIMIT = stringPreferencesKey("single_payment_limit")
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val BIOMETRIC_UNLOCK_ENABLED = booleanPreferencesKey("biometric_unlock_enabled")
        val SCREEN_PRIVACY_ENABLED = booleanPreferencesKey("screen_privacy_enabled")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_SALT = stringPreferencesKey("pin_salt")
    }

    val preferencesFlow: Flow<UserPreferences> = context.arthikaDataStore.data.map { prefs ->
        val themeName = prefs[Keys.THEME_PREFERENCE] ?: ThemePreference.SYSTEM.name
        val theme = runCatching { ThemePreference.valueOf(themeName) }.getOrDefault(ThemePreference.SYSTEM)
        val rawCap = prefs[Keys.SINGLE_PAYMENT_LIMIT]?.trim().orEmpty()
        val parsedCap = rawCap.toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO }
        UserPreferences(
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
            smsDetectionEnabled = prefs[Keys.SMS_DETECTION_ENABLED] ?: false,
            dailyAlertsEnabled = prefs[Keys.DAILY_ALERTS_ENABLED] ?: true,
            themePreference = theme,
            singlePaymentLimit = parsedCap,
            appLockEnabled = prefs[Keys.APP_LOCK_ENABLED] ?: false,
            biometricUnlockEnabled = prefs[Keys.BIOMETRIC_UNLOCK_ENABLED] ?: true,
            screenPrivacyEnabled = prefs[Keys.SCREEN_PRIVACY_ENABLED] ?: true,
            pinHash = prefs[Keys.PIN_HASH],
            pinSalt = prefs[Keys.PIN_SALT]
        )
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.arthikaDataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setSmsDetectionEnabled(enabled: Boolean) {
        context.arthikaDataStore.edit { it[Keys.SMS_DETECTION_ENABLED] = enabled }
    }

    suspend fun setDailyAlertsEnabled(enabled: Boolean) {
        context.arthikaDataStore.edit { it[Keys.DAILY_ALERTS_ENABLED] = enabled }
    }

    suspend fun setThemePreference(theme: ThemePreference) {
        context.arthikaDataStore.edit { it[Keys.THEME_PREFERENCE] = theme.name }
    }

    suspend fun setSinglePaymentLimit(limit: BigDecimal?) {
        context.arthikaDataStore.edit { prefs ->
            if (limit == null || limit <= BigDecimal.ZERO) {
                prefs.remove(Keys.SINGLE_PAYMENT_LIMIT)
            } else {
                prefs[Keys.SINGLE_PAYMENT_LIMIT] = limit.stripTrailingZeros().toPlainString()
            }
        }
    }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        context.arthikaDataStore.edit { it[Keys.APP_LOCK_ENABLED] = enabled }
    }

    suspend fun setBiometricUnlockEnabled(enabled: Boolean) {
        context.arthikaDataStore.edit { it[Keys.BIOMETRIC_UNLOCK_ENABLED] = enabled }
    }

    suspend fun setScreenPrivacyEnabled(enabled: Boolean) {
        context.arthikaDataStore.edit { it[Keys.SCREEN_PRIVACY_ENABLED] = enabled }
    }

    suspend fun setPinCredentials(hash: String, salt: String) {
        context.arthikaDataStore.edit { prefs ->
            prefs[Keys.PIN_HASH] = hash
            prefs[Keys.PIN_SALT] = salt
            prefs[Keys.APP_LOCK_ENABLED] = true
        }
    }

    suspend fun clearSecurityLock() {
        context.arthikaDataStore.edit { prefs ->
            prefs[Keys.APP_LOCK_ENABLED] = false
            prefs.remove(Keys.PIN_HASH)
            prefs.remove(Keys.PIN_SALT)
        }
    }
}
