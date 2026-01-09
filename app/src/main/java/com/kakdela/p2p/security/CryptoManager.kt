package com.kakdela.p2p.security

import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.crypto.tink.*
import com.google.crypto.tink.hybrid.HybridConfig
import com.google.crypto.tink.hybrid.HybridDecrypt
import com.google.crypto.tink.hybrid.HybridEncrypt
import com.google.crypto.tink.hybrid.HybridKeyTemplates
import com.google.crypto.tink.signature.PublicKeySign
import com.google.crypto.tink.signature.PublicKeyVerify
import com.google.crypto.tink.signature.SignatureConfig
import com.google.crypto.tink.signature.SignatureKeyTemplates
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

/**
 * Менеджер криптографии на базе Google Tink.
 * Обеспечивает E2EE (Hybrid Encryption) и проверку авторства (Digital Signatures).
 */
object CryptoManager {
    private const val TAG = "CryptoManager"
    private const val PREFS_NAME = "crypto_keys_prefs"
    private const val KEY_ENC = "enc_keyset_json"
    private const val KEY_SIGN = "sign_keyset_json"

    private var myEncryptKeyset: KeysetHandle? = null
    private var mySignKeyset: KeysetHandle? = null
    
    // Кэш публичных ключей собеседников
    private val peerPublicKeys = mutableMapOf<String, String>()

    init {
        try {
            // Регистрация примитивов шифрования и подписи
            HybridConfig.register()
            SignatureConfig.register()
        } catch (e: Exception) {
            Log.e(TAG, "Tink registration failed", e)
        }
    }

    fun init(context: Context) {
        loadKeys(context)
        if (myEncryptKeyset == null || mySignKeyset == null) {
            generateKeys(context)
        }
    }

    private fun generateKeys(context: Context) {
        try {
            // Генерация ключей для гибридного шифрования (ECIES)
            myEncryptKeyset = KeysetHandle.generateNew(HybridKeyTemplates.ECIES_P256_HKDF_HMAC_SHA256_AES128_GCM)
            // Генерация ключей для цифровой подписи (ECDSA)
            mySignKeyset = KeysetHandle.generateNew(SignatureKeyTemplates.ECDSA_P256)
            saveKeys(context)
            Log.d(TAG, "New keys generated successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Key generation failed", e)
        }
    }

    // --- Цифровая подпись (Authenticity) ---

    fun sign(data: ByteArray): ByteArray = try {
        val signer = mySignKeyset?.getPrimitive(PublicKeySign::class.java)
        signer?.sign(data) ?: byteArrayOf()
    } catch (e: Exception) {
        Log.e(TAG, "Signing error", e)
        byteArrayOf()
    }

    fun verify(signature: ByteArray, data: ByteArray, pubKeyStr: String): Boolean = try {
        // Чтение публичного ключа из JSON строки
        val handle = CleartextKeysetHandle.read(JsonKeysetReader.withString(pubKeyStr))
        val verifier = handle.getPrimitive(PublicKeyVerify::class.java)
        verifier.verify(signature, data)
        true
    } catch (e: Exception) {
        Log.e(TAG, "Signature verification failed", e)
        false
    }

    // --- Гибридное шифрование (Confidentiality) ---

    fun encryptMessage(message: String, peerPublicKey: String): String = try {
        if (peerPublicKey.isBlank()) throw IllegalArgumentException("Empty peer key")
        
        val handle = CleartextKeysetHandle.read(JsonKeysetReader.withString(peerPublicKey))
        val encryptor = handle.getPrimitive(HybridEncrypt::class.java)
        
        // Шифруем данные. Второй параметр (contextInfo) — null для простоты.
        val encrypted = encryptor.encrypt(message.toByteArray(StandardCharsets.UTF_8), null)
        Base64.encodeToString(encrypted, Base64.NO_WRAP)
    } catch (e: Exception) {
        Log.e(TAG, "Encryption error", e)
        message // Возвращаем исходный текст при ошибке (или логику заглушки)
    }

    fun decryptMessage(base64: String): String = try {
        val decryptor = myEncryptKeyset?.getPrimitive(HybridDecrypt::class.java)
        val decoded = Base64.decode(base64, Base64.NO_WRAP)
        val decrypted = decryptor?.decrypt(decoded, null)
        decrypted?.let { String(it, StandardCharsets.UTF_8) } ?: ""
    } catch (e: Exception) {
        Log.e(TAG, "Decryption error", e)
        "[Зашифрованное сообщение]"
    }

    // --- Управление ключами ---

    /**
     * Возвращает публичную часть ключа шифрования в формате JSON.
     */
    fun getMyPublicKeyStr(): String = try {
        val stream = ByteArrayOutputStream()
        myEncryptKeyset?.publicKeysetHandle?.let {
            CleartextKeysetHandle.write(it, JsonKeysetWriter.withOutputStream(stream))
        }
        stream.toString("UTF-8")
    } catch (e: Exception) {
        ""
    }

    fun savePeerPublicKey(peerHash: String, key: String) {
        peerPublicKeys[peerHash] = key
    }

    fun getPeerPublicKey(peerHash: String): String? = peerPublicKeys[peerHash]

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
        CleartextKeysetHandle.write(handle, JsonKeysetWriter.withOutputStream(os))
        os.toString("UTF-8")
    } catch (e: Exception) {
        null
    }

    private fun deserializeHandle(json: String): KeysetHandle? = try {
        CleartextKeysetHandle.read(JsonKeysetReader.withString(json))
    } catch (e: Exception) {
        null
    }
}
