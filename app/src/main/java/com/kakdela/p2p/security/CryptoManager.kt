package com.kakdela.p2p.security

import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.crypto.tink.*
import com.google.crypto.tink.hybrid.HybridConfig
import com.google.crypto.tink.hybrid.HybridKeyTemplates
import com.google.crypto.tink.signature.SignatureConfig
import com.google.crypto.tink.signature.SignatureKeyTemplates
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object CryptoManager {
    private var myPrivateKey: KeysetHandle? = null
    private var mySigningKey: KeysetHandle? = null

    // Кэш публичных ключей других пользователей: Hash -> Keyset (String)
    private val publicKeyCache = mutableMapOf<String, String>()

    fun init(context: Context) {
        HybridConfig.register()
        SignatureConfig.register()
        // Пытаемся загрузить ключи, если они уже были созданы
        loadKeysFromPrefs(context)
    }

    fun isKeyReady(): Boolean = myPrivateKey != null && mySigningKey != null

    /**
     * Генерация ключей: ECIES для шифрования и ECDSA для подписей.
     */
    fun generateKeys(context: Context) {
        try {
            if (myPrivateKey == null) {
                myPrivateKey = KeysetHandle.generateNew(HybridKeyTemplates.ECIES_P256_HKDF_HMAC_SHA256_AES128_GCM)
            }
            if (mySigningKey == null) {
                mySigningKey = KeysetHandle.generateNew(SignatureKeyTemplates.ECDSA_P256)
            }
            saveKeysToPrefs(context)
        } catch (e: Exception) {
            Log.e("CryptoManager", "Error generating keys", e)
        }
    }

    // --- ПОДПИСЬ И ПРОВЕРКА (Signature) ---

    fun sign(data: ByteArray): ByteArray {
        return try {
            val signer = mySigningKey?.getPrimitive(PublicKeySign::class.java)
            signer?.sign(data) ?: ByteArray(0)
        } catch (e: Exception) { ByteArray(0) }
    }

    fun verify(data: ByteArray, signature: ByteArray, publicKeyStr: String): Boolean {
        return try {
            val publicHandle = CleartextKeysetHandle.read(JsonKeysetReader.withString(publicKeyStr))
            val verifier = publicHandle.getPrimitive(PublicKeyVerify::class.java)
            verifier.verify(signature, data)
            true
        } catch (e: Exception) { false }
    }

    // --- ШИФРОВАНИЕ СООБЩЕНИЙ (Hybrid E2EE) ---

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

    // --- РАБОТА С ХЭШАМИ И ПАМЯТЬЮ ---

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

    // --- ВОССТАНОВЛЕНИЕ ЛИЧНОСТИ (Email Backup / Restore) ---

    /**
     * Создает зашифрованный бэкап приватных ключей для загрузки на сервер.
     */
    fun createBackupPayload(password: String): String {
        val stream = ByteArrayOutputStream()
        CleartextKeysetHandle.write(myPrivateKey!!, JsonKeysetWriter.withOutputStream(stream))
        val privateData = stream.toString("UTF-8")
        return encryptWithPassword(privateData, password)
    }

    /**
     * Восстанавливает личность из зашифрованной строки с сервера.
     */
    fun restoreIdentity(encryptedData: String, password: String): Boolean {
        return try {
            val decryptedJson = decryptWithPassword(encryptedData, password)
            myPrivateKey = CleartextKeysetHandle.read(JsonKeysetReader.withString(decryptedJson))
            // После восстановления шифрующего ключа, подписывающий ключ обычно создается заново
            // или восстанавливается из того же бэкапа (в данном примере для простоты — шифрующий).
            true
        } catch (e: Exception) { false }
    }

    // --- ВНУТРЕННИЕ МЕТОДЫ AES-GCM ДЛЯ БЭКАПА ---

    private fun encryptWithPassword(data: String, pass: String): String {
        val salt = ByteArray(16).apply { SecureRandom().nextBytes(this) }
        val spec = PBEKeySpec(pass.toCharArray(), salt, 65536, 256)
        val key = SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded, "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(data.toByteArray())
        return Base64.encodeToString(salt + iv + encrypted, Base64.NO_WRAP)
    }

    private fun decryptWithPassword(encrypted: String, pass: String): String {
        val bytes = Base64.decode(encrypted, Base64.NO_WRAP)
        val salt = bytes.sliceArray(0..15)
        val iv = bytes.sliceArray(16..27)
        val data = bytes.sliceArray(28 until bytes.size)
        val spec = PBEKeySpec(pass.toCharArray(), salt, 65536, 256)
        val key = SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded, "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return String(cipher.doFinal(data))
    }

    private fun saveKeysToPrefs(context: Context) {
        val prefs = context.getSharedPreferences("crypto_keys", Context.MODE_PRIVATE)
        val stream = ByteArrayOutputStream()
        myPrivateKey?.let {
            CleartextKeysetHandle.write(it, JsonKeysetWriter.withOutputStream(stream))
            prefs.edit().putString("pri_key", stream.toString("UTF-8")).apply()
        }
    }

    private fun loadKeysFromPrefs(context: Context) {
        val prefs = context.getSharedPreferences("crypto_keys", Context.MODE_PRIVATE)
        val priStr = prefs.getString("pri_key", null)
        if (priStr != null) {
            myPrivateKey = CleartextKeysetHandle.read(JsonKeysetReader.withString(priStr))
        }
    }
}
