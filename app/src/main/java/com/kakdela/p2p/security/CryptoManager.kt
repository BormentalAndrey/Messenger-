package com.kakdela.p2p.security

import android.util.Base64
import com.google.crypto.tink.CleartextKeysetHandle
import com.google.crypto.tink.HybridDecrypt
import com.google.crypto.tink.HybridEncrypt
import com.google.crypto.tink.JsonKeysetReader
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.hybrid.HybridConfig
import com.google.crypto.tink.hybrid.HybridKeyTemplates
import java.io.ByteArrayOutputStream

object CryptoManager {
    private var myPrivateKey: KeysetHandle? = null

    init {
        HybridConfig.register()
        // В реальном приложении здесь нужно загрузить свой приватный ключ из Keystore/File
        // Для примера генерируем новый, если нет (но это сбросит ключ при перезапуске)
        if (myPrivateKey == null) {
            myPrivateKey = generateKeysetHandle()
        }
    }

    fun generateKeysetHandle(): KeysetHandle = 
        KeysetHandle.generateNew(HybridKeyTemplates.ECIES_P256_HKDF_HMAC_SHA256_AES128_GCM)

    // Метод для получения своего публичного ключа в виде строки (JSON)
    fun getMyPublicKeyStr(): String {
        val handle = myPrivateKey?.publicKeysetHandle ?: return ""
        val stream = ByteArrayOutputStream()
        // ВНИМАНИЕ: CleartextKeysetHandle используется только для примера/отладки
        // В продакшене нужно использовать BinaryKeysetWriter без Cleartext для приватных ключей
        CleartextKeysetHandle.write(handle, com.google.crypto.tink.JsonKeysetWriter.withOutputStream(stream))
        return stream.toString("UTF-8")
    }

    // --- Методы, которые вызывал MessageRepository ---

    fun encryptMessage(text: String, recipientPublicKeyStr: String): ByteArray {
        try {
            // Восстанавливаем KeysetHandle из строки публичного ключа
            val publicHandle = CleartextKeysetHandle.read(JsonKeysetReader.withString(recipientPublicKeyStr))
            val hybridEncrypt = publicHandle.getPrimitive(HybridEncrypt::class.java)
            return hybridEncrypt.encrypt(text.toByteArray(Charsets.UTF_8), null)
        } catch (e: Exception) {
            e.printStackTrace()
            return ByteArray(0)
        }
    }

    fun decryptMessage(cipherBytes: ByteArray): String {
        try {
            val privateKey = myPrivateKey ?: throw IllegalStateException("No private key found")
            val hybridDecrypt = privateKey.getPrimitive(HybridDecrypt::class.java)
            val decryptedBytes = hybridDecrypt.decrypt(cipherBytes, null)
            return String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            return "[Decryption Error]"
        }
    }
}

