package com.kakdela.p2p.security

import com.google.crypto.tink.HybridDecrypt
import com.google.crypto.tink.HybridEncrypt
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.hybrid.HybridConfig
import com.google.crypto.tink.hybrid.HybridKeyTemplates
import java.util.Base64

object CryptoManager {

    init {
        HybridConfig.register()
    }

    fun generateKeysetHandle(): KeysetHandle {
        return KeysetHandle.generateNew(HybridKeyTemplates.ECIES_P256_HKDF_HMAC_SHA256_AES128_GCM)
    }

    // Метод для Репозитория (работает с байтами)
    fun encryptMessage(text: String, publicKeyStr: String): ByteArray {
        // В реальном ТЗ тут должен быть десериализатор ключа, пока используем заглушку генерации для теста связи
        val handle = generateKeysetHandle() 
        val hybridEncrypt = handle.publicKeysetHandle.getPrimitive(HybridEncrypt::class.java)
        return hybridEncrypt.encrypt(text.toByteArray(Charsets.UTF_8), null)
    }

    fun decryptMessage(cipherBytes: ByteArray): String {
        // Здесь должен использоваться ваш сохраненный приватный ключ
        val handle = generateKeysetHandle()
        val hybridDecrypt = handle.getPrimitive(HybridDecrypt::class.java)
        val decryptedBytes = hybridDecrypt.decrypt(cipherBytes, null)
        return String(decryptedBytes, Charsets.UTF_8)
    }

    // Методы для ViewModel (работают со строками)
    fun encrypt(plainText: String, handle: KeysetHandle): String {
        val hybridEncrypt = handle.getPrimitive(HybridEncrypt::class.java)
        val ciphertext = hybridEncrypt.encrypt(plainText.toByteArray(Charsets.UTF_8), null)
        return Base64.getEncoder().encodeToString(ciphertext)
    }

    fun decrypt(cipherText: String, handle: KeysetHandle): String {
        val hybridDecrypt = handle.getPrimitive(HybridDecrypt::class.java)
        val decodedBytes = Base64.getDecoder().decode(cipherText)
        return String(hybridDecrypt.decrypt(decodedBytes, null), Charsets.UTF_8)
    }
}

