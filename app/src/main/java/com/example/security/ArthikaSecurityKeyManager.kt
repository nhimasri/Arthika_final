package com.example.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

/**
 * Manages hardware-backed Android Keystore AES-256-GCM keys for encrypting the local
 * SQLCipher Room database passphrase, as well as salted PBKDF2WithHmacSHA256 PIN hashing.
 */
object ArthikaSecurityKeyManager {

    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val MASTER_KEY_ALIAS = "arthika_db_master_key_v1"
    private const val PREFS_NAME = "arthika_vault_keystore_prefs"
    private const val KEY_WRAPPED_DB_SECRET = "wrapped_db_secret"
    private const val KEY_WRAPPED_DB_IV = "wrapped_db_iv"
    private const val KEY_FALLBACK_SECRET = "test_fallback_db_secret"

    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val PBKDF2_ITERATIONS = 120_000
    private const val PBKDF2_KEY_BITS = 256

    @Synchronized
    fun getOrCreateDatabasePassphrase(context: Context): ByteArray {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val wrappedB64 = prefs.getString(KEY_WRAPPED_DB_SECRET, null)
        val ivB64 = prefs.getString(KEY_WRAPPED_DB_IV, null)

        val keystoreResult = runCatching { getOrCreateAndroidKeystoreSecretKey(appContext) }
        val keystoreKey = keystoreResult.getOrNull()
        if (keystoreKey != null) {
            if (!wrappedB64.isNullOrBlank() && !ivB64.isNullOrBlank()) {
                val decrypted = runCatching {
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    val iv = Base64.decode(ivB64, Base64.NO_WRAP)
                    val ciphertext = Base64.decode(wrappedB64, Base64.NO_WRAP)
                    cipher.init(Cipher.DECRYPT_MODE, keystoreKey, GCMParameterSpec(128, iv))
                    cipher.doFinal(ciphertext)
                }.getOrElse { error ->
                    throw SecurityException(
                        "Failed to unwrap SQLCipher database key using Android Keystore",
                        error
                    )
                }
                if (decrypted.size != 32) {
                    throw SecurityException("Unwrapped SQLCipher database key has invalid length")
                }
                return decrypted
            }

            val rawKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, keystoreKey)
            val encrypted = cipher.doFinal(rawKey)
            val committed = prefs.edit()
                .putString(KEY_WRAPPED_DB_SECRET, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .putString(KEY_WRAPPED_DB_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .remove(KEY_FALLBACK_SECRET)
                .commit()
            if (!committed) {
                rawKey.fill(0)
                throw SecurityException("Failed to persist wrapped SQLCipher database key")
            }
            return rawKey
        }

        // Only allow in-process test fallback in Robolectric JVM unit test environments
        if (!isRobolectricEnvironment()) {
            throw SecurityException(
                "Android Keystore unavailable; refusing to store database key without hardware encryption",
                keystoreResult.exceptionOrNull()
            )
        }

        val existingFallback = prefs.getString(KEY_FALLBACK_SECRET, null)
        if (!existingFallback.isNullOrBlank()) {
            return Base64.decode(existingFallback, Base64.NO_WRAP)
        }
        val fallbackBytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY_FALLBACK_SECRET, Base64.encodeToString(fallbackBytes, Base64.NO_WRAP))
            .apply()
        return fallbackBytes
    }

    fun isRobolectricEnvironment(): Boolean {
        return android.os.Build.FINGERPRINT.equals("robolectric", ignoreCase = true)
    }

    fun isAndroidKeystoreActive(): Boolean {
        return runCatching {
            val ks = KeyStore.getInstance(KEYSTORE_PROVIDER)
            ks.load(null)
            ks.containsAlias(MASTER_KEY_ALIAS)
        }.getOrDefault(false)
    }

    private fun getOrCreateAndroidKeystoreSecretKey(context: Context): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
        keyStore.load(null)
        val existing = keyStore.getKey(MASTER_KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val hasStrongBox = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P &&
            context.packageManager.hasSystemFeature(
                android.content.pm.PackageManager.FEATURE_STRONGBOX_KEYSTORE
            )

        if (hasStrongBox && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            val strongBoxKey = runCatching {
                generateAesGcmKey(useStrongBox = true)
            }.getOrNull()
            if (strongBoxKey != null) return strongBoxKey
        }

        return generateAesGcmKey(useStrongBox = false)
    }

    private fun generateAesGcmKey(useStrongBox: Boolean): SecretKey {
        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val builder = KeyGenParameterSpec.Builder(
            MASTER_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)

        if (useStrongBox && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            builder.setIsStrongBoxBacked(true)
        }

        keyGen.init(builder.build())
        return keyGen.generateKey()
    }

    fun hashPin(pin: String, existingSaltBase64: String? = null): Pair<String, String> {
        val salt = if (!existingSaltBase64.isNullOrBlank()) {
            Base64.decode(existingSaltBase64, Base64.NO_WRAP)
        } else {
            ByteArray(16).also { SecureRandom().nextBytes(it) }
        }
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_BITS)
        val derived = try {
            SecretKeyFactory.getInstance(PBKDF2_ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
        val saltB64 = Base64.encodeToString(salt, Base64.NO_WRAP)
        val hashB64 = Base64.encodeToString(derived, Base64.NO_WRAP)
        return hashB64 to saltB64
    }

    fun verifyPin(pin: String, expectedHashBase64: String, saltBase64: String): Boolean {
        if (pin.isBlank() || expectedHashBase64.isBlank() || saltBase64.isBlank()) return false
        return runCatching {
            val (computedHashB64, _) = hashPin(pin, saltBase64)
            val expectedBytes = Base64.decode(expectedHashBase64, Base64.NO_WRAP)
            val computedBytes = Base64.decode(computedHashB64, Base64.NO_WRAP)
            MessageDigest.isEqual(expectedBytes, computedBytes)
        }.getOrDefault(false)
    }
}
