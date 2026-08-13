package com.fitnessapp.tracker.util

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import java.io.InputStream
import java.io.OutputStream

class CryptoManager {

    companion object {
        private const val ALGORITHM = "AES/GCM/NoPadding"
        private const val KEY_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val SALT_LENGTH = 16
        private const val IV_LENGTH = 12
        private const val TAG_LENGTH_BIT = 128
        private const val ITERATION_COUNT = 65536
        private const val KEY_LENGTH_BIT = 256
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance(KEY_ALGORITHM)
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATION_COUNT, KEY_LENGTH_BIT)
        val secretKey = factory.generateSecret(spec)
        return SecretKeySpec(secretKey.encoded, "AES")
    }

    fun encrypt(plainTextBytes: ByteArray, outputStream: OutputStream, password: String) {
        val salt = ByteArray(SALT_LENGTH)
        SecureRandom().nextBytes(salt)
        
        val key = deriveKey(password, salt)
        
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        
        val iv = cipher.iv
        
        outputStream.write(salt)
        outputStream.write(iv)
        
        // Write the encrypted payload
        val cipherText = cipher.doFinal(plainTextBytes)
        outputStream.write(cipherText)
    }

    fun decrypt(inputStream: InputStream, password: String): ByteArray {
        val salt = ByteArray(SALT_LENGTH)
        val saltRead = inputStream.read(salt)
        if (saltRead != SALT_LENGTH) throw Exception("Invalid backup file: Missing salt")
        
        val iv = ByteArray(IV_LENGTH)
        val ivRead = inputStream.read(iv)
        if (ivRead != IV_LENGTH) throw Exception("Invalid backup file: Missing IV")
        
        val cipherText = inputStream.readBytes()
        
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance(ALGORITHM)
        val spec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
        
        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        return cipher.doFinal(cipherText)
    }
}
