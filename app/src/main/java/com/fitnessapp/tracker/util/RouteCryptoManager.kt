package com.fitnessapp.tracker.util

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages hardware-backed AES-256-GCM encryption and decryption for GPS route traces.
 *
 * All cryptographic operations use keys generated inside and protected by the
 * Android KeyStore (TEE / StrongBox). Key material never leaves hardware enclave.
 */
@Singleton
class RouteCryptoManager @Inject constructor() {

    companion object {
        private const val TAG = "RouteCryptoManager"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "smart_track_gps_route_key"
        private const val CIPHER_ALGORITHM = "AES/GCM/NoPadding"
        private const val IV_LENGTH_BYTES = 12
        private const val TAG_LENGTH_BITS = 128
        private const val PAYLOAD_PREFIX_V1 = "enc:v1:"
    }

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    @Synchronized
    private fun getOrCreateSecretKey(): SecretKey {
        if (keyStore.containsAlias(KEY_ALIAS)) {
            val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (entry != null) {
                return entry.secretKey
            }
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    /**
     * Encrypts the raw GPS route JSON string using AES-256-GCM.
     * Output format: "enc:v1:<Base64(IV + Ciphertext + Tag)>"
     */
    fun encryptRoute(routeJson: String): String {
        if (routeJson.isBlank() || routeJson == "[]") {
            return "[]"
        }

        return try {
            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)

            val iv = cipher.iv
            val plainBytes = routeJson.toByteArray(Charsets.UTF_8)
            val cipherText = cipher.doFinal(plainBytes)

            // Combined IV (12 bytes) + Ciphertext & Tag
            val combined = ByteArray(iv.size + cipherText.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)

            val base64 = Base64.encodeToString(combined, Base64.NO_WRAP)
            "$PAYLOAD_PREFIX_V1$base64"
        } catch (e: Exception) {
            Log.e(TAG, "Failed to encrypt route data, falling back to empty track", e)
            "[]"
        }
    }

    /**
     * Decrypts an encrypted GPS route payload.
     * Supports backward compatibility with unencrypted legacy JSON strings.
     */
    fun decryptRoute(encryptedPayload: String?): String {
        if (encryptedPayload.isNullOrBlank() || encryptedPayload == "[]") {
            return "[]"
        }

        // Backward compatibility: If not starting with our prefix, it's a legacy unencrypted JSON string
        if (!encryptedPayload.startsWith(PAYLOAD_PREFIX_V1)) {
            return encryptedPayload
        }

        return try {
            val base64Cipher = encryptedPayload.substring(PAYLOAD_PREFIX_V1.length)
            val combined = Base64.decode(base64Cipher, Base64.NO_WRAP)

            if (combined.size < IV_LENGTH_BYTES) {
                Log.e(TAG, "Encrypted payload too short to contain IV")
                return "[]"
            }

            val iv = ByteArray(IV_LENGTH_BYTES)
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH_BYTES)

            val cipherTextSize = combined.size - IV_LENGTH_BYTES
            val cipherText = ByteArray(cipherTextSize)
            System.arraycopy(combined, IV_LENGTH_BYTES, cipherText, 0, cipherTextSize)

            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
            val spec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            val plainBytes = cipher.doFinal(cipherText)
            String(plainBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt route payload", e)
            "[]"
        }
    }
}
