package com.kakdela.p2p.security

import com.google.crypto.tink.HybridDecrypt
import com.google.crypto.tink.HybridEncrypt
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.hybrid.HybridConfig
import com.google.crypto.tink.hybrid.HybridKeyTemplates
import java.util.Base64

object CryptoManager {

    init {
        // Регистрация конфигурации гибридного шифрования
        HybridConfig.register()
    }

    /**
     * Генерирует новую пару ключей (приватный + публичный)
     */
    fun generateKeysetHandle(): KeysetHandle {
        return KeysetHandle.generateNew(HybridKeyTemplates.ECIES_P256_HKDF_HMAC_SHA256_AES128_GCM)
    }

    /**
     * Шифрование текста с использованием публичного ключа получателя
     */
    fun encrypt(plainText: String, publicKeysetHandle: KeysetHandle): String {
        val hybridEncrypt = publicKeysetHandle.getPrimitive(HybridEncrypt::class.java)
        val ciphertext = hybridEncrypt.encrypt(plainText.toByteArray(Charsets.UTF_8), null)
        return Base64.getEncoder().encodeToString(ciphertext)
    }

    /**
     * Дешифрование текста с использованием своего приватного ключа
     */
    fun decrypt(cipherText: String, privateKeysetHandle: KeysetHandle): String {
        val hybridDecrypt = privateKeysetHandle.getPrimitive(HybridDecrypt::class.java)
        val decodedBytes = Base64.getDecoder().decode(cipherText)
        val decryptedBytes = hybridDecrypt.decrypt(decodedBytes, null)
        return String(decryptedBytes, Charsets.UTF_8)
    }
}

