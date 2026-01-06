package com.kakdela.p2p.security

import com.google.crypto.tink.HybridDecrypt
import com.google.crypto.tink.HybridEncrypt
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.hybrid.HybridConfig
import com.google.crypto.tink.hybrid.HybridKeyTemplates
import java.util.Base64

object CryptoManager {
    init {
        // Регистрация в 1.20.0 обязательна
        HybridConfig.register()
    }

    fun generateKeysetHandle(): KeysetHandle = 
        KeysetHandle.generateNew(HybridKeyTemplates.ECIES_P256_HKDF_HMAC_SHA256_AES128_GCM)

    fun encrypt(plainText: String, publicHandle: KeysetHandle): String {
        val hybridEncrypt = publicHandle.getPrimitive(HybridEncrypt::class.java)
        val ciphertext = hybridEncrypt.encrypt(plainText.toByteArray(Charsets.UTF_8), null)
        return Base64.getEncoder().encodeToString(ciphertext)
    }

    fun decrypt(cipherText: String, privateHandle: KeysetHandle): String {
        val hybridDecrypt = privateHandle.getPrimitive(HybridDecrypt::class.java)
        val decodedBytes = Base64.getDecoder().decode(cipherText)
        return String(hybridDecrypt.decrypt(decodedBytes, null), Charsets.UTF_8)
    }
}

