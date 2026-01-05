package com.dchat.core.security

import android.util.Base64
import com.google.crypto.tink.HybridDecrypt
import com.google.crypto.tink.HybridEncrypt
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.hybrid.HybridConfig
import com.google.crypto.tink.signature.SignatureConfig
import java.security.MessageDigest

/**
 * Реализация пунктов 4.1, 5.2 и 10.1 ТЗ.
 */
object CryptoEngine {

    private const val GLOBAL_SALT = "D_CHAT_V1_STATIC_SALT_CHANGE_THIS_IN_PROD"

    init {
        HybridConfig.register()
        SignatureConfig.register()
    }

    // --- 1. Генерация идентичности ---

    fun generateNewKeys(): KeysetHandle {
        // ECIES P256 HKDF HMAC SHA256 AES128 GCM (Стандарт Tink для E2EE)
        return KeysetHandle.generateNew(com.google.crypto.tink.hybrid.HybridKeyTemplates.ECIES_P256_HKDF_HMAC_SHA256_AES128_GCM)
    }

    fun getUserId(keysetHandle: KeysetHandle): String {
        // user_id = SHA-256(Public Key)
        val pubKey = keysetHandle.publicKeysetHandle.toString().toByteArray()
        return hashSha256(pubKey)
    }

    // --- 2. Логика хеширования номера (Search Protocol) ---

    fun normalizeAndHashPhone(phoneRaw: String): String {
        // 1. Нормализация (упрощенная, лучше использовать libphonenumber)
        val normalized = phoneRaw.filter { it.isDigit() || it == '+' } // Оставляем + и цифры
        
        // 2. Хеширование: SHA-256( SHA-256(phone) + SALT )
        val firstHash = sha256Bytes(normalized.toByteArray())
        val combined = firstHash + GLOBAL_SALT.toByteArray()
        val secondHash = sha256Bytes(combined)
        
        return Base64.encodeToString(secondHash, Base64.NO_WRAP)
    }

    // --- 3. Шифрование сообщений ---

    fun encryptForPeer(plaintext: String, peerPublicKeyJson: String): ByteArray {
        val publicHandle = KeysetHandle.readNoSecret(peerPublicKeyJson.toByteArray())
        val encryptor = publicHandle.getPrimitive(HybridEncrypt::class.java)
        return encryptor.encrypt(plaintext.toByteArray(), null)
    }

    fun decryptMessage(cipherText: ByteArray, myPrivateKeys: KeysetHandle): String {
        val decryptor = myPrivateKeys.getPrimitive(HybridDecrypt::class.java)
        val decrypted = decryptor.decrypt(cipherText, null)
        return String(decrypted)
    }

    // --- Helpers ---

    private fun hashSha256(data: ByteArray): String {
        return Base64.encodeToString(sha256Bytes(data), Base64.NO_WRAP)
    }

    private fun sha256Bytes(data: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest(data)
    }
}
