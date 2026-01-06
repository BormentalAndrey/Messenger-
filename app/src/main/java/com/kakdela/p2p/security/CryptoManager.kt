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

    fun generateKeysetHandle(): KeysetHandle = 
        KeysetHandle.generateNew(HybridKeyTemplates.ECIES_P256_HKDF_HMAC_SHA256_AES128_GCM)

    fun encrypt(text: String, handle: KeysetHandle): String {
        val primitive = handle.getPrimitive(HybridEncrypt::class.java)
        val encrypted = primitive.encrypt(text.toByteArray(), null)
        return Base64.getEncoder().encodeToString(encrypted)
    }

    fun decrypt(base64Text: String, handle: KeysetHandle): String {
        val primitive = handle.getPrimitive(HybridDecrypt::class.java)
        val decrypted = primitive.decrypt(Base64.getDecoder().decode(base64Text), null)
        return String(decrypted)
    }
}

