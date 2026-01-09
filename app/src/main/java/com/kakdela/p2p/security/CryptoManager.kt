package com.kakdela.p2p.security

import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.crypto.tink.CleartextKeysetHandle
import com.google.crypto.tink.JsonKeysetReader
import com.google.crypto.tink.JsonKeysetWriter
import com.google.crypto.tink.KeysetHandle
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
 * Менеджер криптографии для P2P мессенджера.
 * Реализует E2EE шифрование (ECIES) и цифровую подпись (ECDSA).
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
            HybridConfig.register()
            SignatureConfig.register()
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка регистрации Tink конфигураций", e)
        }
    }

    /**
     * Инициализация менеджера: загрузка существующих ключей или генерация новых.
     */
    fun init(context: Context) {
        loadKeys(context)
        if (myEncryptKeyset == null || mySignKeyset == null) {
            generateKeys(context)
        }
    }

    /**
     * Алиас для совместимости с MainActivity и другими компонентами.
     */
    fun generateKeysIfNeeded(context: Context) {
        init(context)
    }

    private fun generateKeys(context: Context) {
        try {
            Log.d(TAG, "Генерация новой пары ключей...")
            myEncryptKeyset = KeysetHandle.generateNew(HybridKeyTemplates.ECIES_P256_HKDF_HMAC_SHA256_AES128_GCM)
            mySignKeyset = KeysetHandle.generateNew(SignatureKeyTemplates.ECDSA_P256)
            saveKeys(context)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при генерации ключей", e)
        }
    }

    // -------------------- ЦИФРОВАЯ ПОДПИСЬ --------------------

    fun sign(data: ByteArray): ByteArray = try {
        val signer = mySignKeyset?.getPrimitive(PublicKeySign::class.java)
        signer?.sign(data) ?: ByteArray(0)
    } catch (e: Exception) {
        Log.e(TAG, "Ошибка подписи данных", e)
        ByteArray(0)
    }

    fun verify(signature: ByteArray, data: ByteArray, pubKeyStr: String): Boolean = try {
        val handle = CleartextKeysetHandle.read(JsonKeysetReader.withString(pubKeyStr))
        val verifier = handle.getPrimitive(PublicKeyVerify::class.java)
        verifier.verify(signature, data)
        true
    } catch (e: Exception) {
        Log.e(TAG, "Ошибка верификации подписи", e)
        false
    }

    // -------------------- E2EE ШИФРОВАНИЕ --------------------

    fun encryptMessage(message: String, peerPublicKey: String): String = try {
        if (peerPublicKey.isBlank()) throw IllegalArgumentException("Пустой публичный ключ")
        
        val handle = CleartextKeysetHandle.read(JsonKeysetReader.withString(peerPublicKey))
        val encryptor = handle.getPrimitive(HybridEncrypt::class.java)
        
        val encrypted = encryptor.encrypt(message.toByteArray(StandardCharsets.UTF_8), null)
        Base64.encodeToString(encrypted, Base64.NO_WRAP)
    } catch (e: Exception) {
        Log.e(TAG, "Ошибка шифрования: ${e.message}")
        "[Ошибка шифрования]"
    }

    fun decryptMessage(base64: String): String = try {
        val decryptor = myEncryptKeyset?.getPrimitive(HybridDecrypt::class.java)
            ?: throw IllegalStateException("Keyset не инициализирован")
            
        val decodedData = Base64.decode(base64, Base64.NO_WRAP)
        val decrypted = decryptor.decrypt(decodedData, null)
        String(decrypted, StandardCharsets.UTF_8)
    } catch (e: Exception) {
        Log.e(TAG, "Ошибка дешифрования: ${e.message}")
        "[Ошибка дешифрования]"
    }

    // -------------------- УПРАВЛЕНИЕ КЛЮЧАМИ --------------------

    fun getMyPublicKeyStr(): String = try {
        val publicHandle = myEncryptKeyset?.publicKeysetHandle
        val stream = ByteArrayOutputStream()
        CleartextKeysetHandle.write(publicHandle, JsonKeysetWriter.withOutputStream(stream))
        stream.toString("UTF-8")
    } catch (e: Exception) {
        Log.e(TAG, "Ошибка экспорта публичного ключа", e)
        ""
    }

    fun savePeerPublicKey(peerHash: String, key: String) {
        if (peerHash.isNotEmpty() && key.isNotEmpty()) {
            peerPublicKeys[peerHash] = key
        }
    }

    fun getPeerPublicKey(peerHash: String): String? = peerPublicKeys[peerHash]

    // -------------------- ХРАНИЛИЩЕ (PERSISTENCE) --------------------

    private fun saveKeys(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().apply {
                putString(KEY_ENC, writeKeysetToString(myEncryptKeyset))
                putString(KEY_SIGN, writeKeysetToString(mySignKeyset))
                apply()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка сохранения ключей в SharedPreferences", e)
        }
    }

    private fun loadKeys(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val encJson = prefs.getString(KEY_ENC, null)
            val signJson = prefs.getString(KEY_SIGN, null)

            if (encJson != null) myEncryptKeyset = readKeysetFromString(encJson)
            if (signJson != null) mySignKeyset = readKeysetFromString(signJson)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка загрузки ключей", e)
        }
    }

    private fun writeKeysetToString(handle: KeysetHandle?): String? {
        if (handle == null) return null
        return try {
            val stream = ByteArrayOutputStream()
            CleartextKeysetHandle.write(handle, JsonKeysetWriter.withOutputStream(stream))
            stream.toString("UTF-8")
        } catch (e: Exception) {
            null
        }
    }

    private fun readKeysetFromString(json: String): KeysetHandle? {
        return try {
            CleartextKeysetHandle.read(JsonKeysetReader.withString(json))
        } catch (e: Exception) {
            null
        }
    }
}
