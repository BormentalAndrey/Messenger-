package com.kakdela.p2p.security

import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.crypto.tink.*
import com.google.crypto.tink.hybrid.*
import com.google.crypto.tink.signature.*
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object CryptoManager {
    private const val TAG = "CryptoManager"
    private const val PREFS_NAME = "crypto_keys_prefs"
    private const val KEY_ENC = "enc_keyset_json"
    private const val KEY_SIGN = "sign_keyset_json"

    private var myEncryptKeyset: KeysetHandle? = null
    private var mySignKeyset: KeysetHandle? = null
    
    // Кеш публичных ключей собеседников
    private val peerPublicKeys = mutableMapOf<String, String>()

    init {
        try {
            HybridConfig.register()
            SignatureConfig.register()
        } catch (e: Exception) {
            Log.e(TAG, "Tink init error", e)
        }
    }

    /**
     * Основная инициализация. Загружает ключи или генерирует новые.
     */
    fun init(context: Context) {
        if (myEncryptKeyset != null && mySignKeyset != null) return // Уже инициализировано

        loadKeys(context)
        if (myEncryptKeyset == null || mySignKeyset == null) {
            Log.i(TAG, "Keys not found, generating new identity...")
            generateKeys(context)
        }
    }

    private fun generateKeys(context: Context) {
        try {
            // ECIES для шифрования сообщений
            myEncryptKeyset = KeysetHandle.generateNew(HybridKeyTemplates.ECIES_P256_HKDF_HMAC_SHA256_AES128_GCM)
            // ECDSA для цифровой подписи
            mySignKeyset = KeysetHandle.generateNew(SignatureKeyTemplates.ECDSA_P256)
            saveKeys(context)
        } catch (e: Exception) {
            Log.e(TAG, "Key generation failed", e)
        }
    }

    /**
     * Создает цифровую подпись данных
     */
    fun sign(data: ByteArray): ByteArray = try {
        val signer = mySignKeyset?.getPrimitive(PublicKeySign::class.java)
        signer?.sign(data) ?: byteArrayOf()
    } catch (e: Exception) {
        Log.e(TAG, "Signing failed", e)
        byteArrayOf()
    }

    /**
     * Проверяет цифровую подпись
     */
    fun verify(signature: ByteArray, data: ByteArray, pubKeyStr: String): Boolean = try {
        val handle = CleartextKeysetHandle.read(JsonKeysetReader.withString(pubKeyStr))
        val verifier = handle.getPrimitive(PublicKeyVerify::class.java)
        verifier.verify(signature, data)
        true
    } catch (e: Exception) {
        Log.w(TAG, "Signature verification failed")
        false
    }

    /**
     * Шифрует сообщение для конкретного получателя (по его Public Key)
     */
    fun encryptMessage(message: String, peerPublicKey: String): String = try {
        val handle = CleartextKeysetHandle.read(JsonKeysetReader.withString(peerPublicKey))
        val encryptor = handle.getPrimitive(HybridEncrypt::class.java)
        val encrypted = encryptor.encrypt(message.toByteArray(StandardCharsets.UTF_8), null)
        Base64.encodeToString(encrypted, Base64.NO_WRAP)
    } catch (e: Exception) {
        Log.e(TAG, "Encryption failed", e)
        message // В случае ошибки возвращаем открытый текст (или лучше пустую строку в проде)
    }

    /**
     * Расшифровывает сообщение, адресованное нам
     */
    fun decryptMessage(base64: String): String = try {
        val decryptor = myEncryptKeyset?.getPrimitive(HybridDecrypt::class.java)
        val decoded = Base64.decode(base64, Base64.NO_WRAP)
        val decrypted = decryptor?.decrypt(decoded, null)
        decrypted?.let { String(it, StandardCharsets.UTF_8) } ?: ""
    } catch (e: Exception) {
        Log.e(TAG, "Decryption error", e)
        "[Decryption Failed]"
    }

    /**
     * Возвращает наш публичный ключ в формате JSON String
     */
    fun getMyPublicKeyStr(): String = try {
        val stream = ByteArrayOutputStream()
        myEncryptKeyset?.publicKeysetHandle?.let { publicHandle ->
            CleartextKeysetHandle.write(publicHandle, JsonKeysetWriter.withOutputStream(stream))
        }
        stream.toString("UTF-8")
    } catch (e: Exception) {
        Log.e(TAG, "Failed to export public key", e)
        ""
    }

    /**
     * Генерирует уникальный Hash (Fingerprint) на основе публичного ключа.
     * Это и есть "ID пользователя" для базы данных.
     */
    fun getMyIdentityHash(): String {
        val pubKey = getMyPublicKeyStr()
        if (pubKey.isEmpty()) return ""
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(pubKey.toByteArray(StandardCharsets.UTF_8))
            // Конвертируем в Hex строку
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }
    }

    fun savePeerPublicKey(hash: String, key: String) {
        peerPublicKeys[hash] = key
    }

    fun getPeerPublicKey(hash: String): String? = peerPublicKeys[hash]

    /* === PERSISTENCE === */

    private fun saveKeys(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString(KEY_ENC, serializeHandle(myEncryptKeyset))
            putString(KEY_SIGN, serializeHandle(mySignKeyset))
            apply()
        }
    }

    private fun loadKeys(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.getString(KEY_ENC, null)?.let { myEncryptKeyset = deserializeHandle(it) }
        prefs.getString(KEY_SIGN, null)?.let { mySignKeyset = deserializeHandle(it) }
    }

    private fun serializeHandle(handle: KeysetHandle?): String? = try {
        val os = ByteArrayOutputStream()
        handle?.let { CleartextKeysetHandle.write(it, JsonKeysetWriter.withOutputStream(os)) }
        os.toString("UTF-8")
    } catch (e: Exception) { null }

    private fun deserializeHandle(json: String): KeysetHandle? = try {
        CleartextKeysetHandle.read(JsonKeysetReader.withString(json))
    } catch (e: Exception) { null }
}
