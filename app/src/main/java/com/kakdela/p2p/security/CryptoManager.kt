package com.kakdela.p2p.security

import android.content.Context
import android.util.Base64
import com.google.crypto.tink.*
import com.google.crypto.tink.hybrid.HybridConfig
import com.google.crypto.tink.hybrid.HybridKeyTemplates
import com.google.crypto.tink.signature.SignatureConfig
import com.google.crypto.tink.signature.SignatureKeyTemplates
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object CryptoManager {
    private var myPrivateKey: KeysetHandle? = null
    private var mySigningKey: KeysetHandle? = null

    // Временное хранилище известных публичных ключей (в идеале — БД Room)
    private val publicKeyCache = mutableMapOf<String, String>()

    fun init(context: Context) {
        HybridConfig.register()
        SignatureConfig.register()
        generateKeys(context)
    }

    /**
     * Проверка, созданы ли ключи.
     */
    fun isKeyReady(): Boolean = myPrivateKey != null && mySigningKey != null

    /**
     * Генерация новой пары ключей для E2EE и цифровой подписи.
     */
    fun generateKeys(context: Context) {
        try {
            if (myPrivateKey == null) {
                myPrivateKey = KeysetHandle.generateNew(HybridKeyTemplates.ECIES_P256_HKDF_HMAC_SHA256_AES128_GCM)
            }
            if (mySigningKey == null) {
                mySigningKey = KeysetHandle.generateNew(SignatureKeyTemplates.ECDSA_P256)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun sign(data: ByteArray): ByteArray {
        val signer = mySigningKey?.getPrimitive(PublicKeySign::class.java)
        return signer?.sign(data) ?: ByteArray(0)
    }

    fun verify(data: ByteArray, signature: ByteArray, publicKeyStr: String): Boolean {
        return try {
            val publicHandle = CleartextKeysetHandle.read(JsonKeysetReader.withString(publicKeyStr))
            val verifier = publicHandle.getPrimitive(PublicKeyVerify::class.java)
            verifier.verify(signature, data)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun getPublicKeyByHash(hash: String): String? = publicKeyCache[hash]

    fun savePeerPublicKey(hash: String, key: String) {
        publicKeyCache[hash] = key
    }

    fun getMyPublicKeyStr(): String {
        val handle = myPrivateKey?.publicKeysetHandle ?: return ""
        val stream = ByteArrayOutputStream()
        CleartextKeysetHandle.write(handle, JsonKeysetWriter.withOutputStream(stream))
        return stream.toString("UTF-8")
    }

    fun encryptMessage(text: String, recipientPublicKeyStr: String): ByteArray {
        return try {
            val publicHandle = CleartextKeysetHandle.read(JsonKeysetReader.withString(recipientPublicKeyStr))
            val hybridEncrypt = publicHandle.getPrimitive(HybridEncrypt::class.java)
            hybridEncrypt.encrypt(text.toByteArray(Charsets.UTF_8), null)
        } catch (e: Exception) { ByteArray(0) }
    }

    fun decryptMessage(cipherBytes: ByteArray): String {
        return try {
            val hybridDecrypt = myPrivateKey?.getPrimitive(HybridDecrypt::class.java)
            val decryptedBytes = hybridDecrypt?.decrypt(cipherBytes, null)
            String(decryptedBytes!!, Charsets.UTF_8)
        } catch (e: Exception) { "[Ошибка расшифровки]" }
    }
}

