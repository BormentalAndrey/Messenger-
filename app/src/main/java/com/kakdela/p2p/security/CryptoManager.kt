package com.kakdela.p2p.security

import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.crypto.tink.CleartextKeysetHandle
import com.google.crypto.tink.JsonKeysetReader
import com.google.crypto.tink.JsonKeysetWriter
import com.google.crypto.tink.hybrid.HybridConfig
import com.google.crypto.tink.hybrid.HybridDecrypt
import com.google.crypto.tink.hybrid.HybridEncrypt
import com.google.crypto.tink.hybrid.HybridKeyTemplates
import com.google.crypto.tink.signature.SignatureConfig
import com.google.crypto.tink.signature.SignatureKeyTemplates
import com.google.crypto.tink.PublicKeySign
import com.google.crypto.tink.PublicKeyVerify
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object CryptoManager {

    private var myPrivateKey: com.google.crypto.tink.KeysetHandle? = null
    private var mySigningKey: com.google.crypto.tink.KeysetHandle? = null

    // Кэш публичных ключей других пользователей: hash -> keyString
    private val publicKeyCache = mutableMapOf<String, String>()

    // -------------------- ИНИЦИАЛИЗАЦИЯ --------------------

    fun init(context: Context) {
        HybridConfig.register()
        SignatureConfig.register()
        loadKeysFromPrefs(context)
    }

    fun isKeyReady(): Boolean = myPrivateKey != null && mySigningKey != null

    fun generateKeysIfNeeded(context: Context) {
        if (!isKeyReady()) generateKeys(context)
    }

    fun generateKeys(context: Context) {
        try {
            if (myPrivateKey == null) {
                myPrivateKey = com.google.crypto.tink.KeysetHandle.generateNew(
                    HybridKeyTemplates.ECIES_P256_HKDF_HMAC_SHA256_AES128_GCM
                )
            }
            if (mySigningKey == null) {
                mySigningKey = com.google.crypto.tink.KeysetHandle.generateNew(
                    SignatureKeyTemplates.ECDSA_P256
                )
            }
            saveKeysToPrefs(context)
        } catch (e: Exception) {
            Log.e("CryptoManager", "Error generating keys", e)
        }
    }

    // -------------------- ПОДПИСЬ / ПРОВЕРКА --------------------

    fun sign(data: ByteArray): ByteArray {
        return try {
            val signer = mySigningKey?.getPrimitive(PublicKeySign::class.java)
            signer?.sign(data) ?: ByteArray(0)
        } catch (e: Exception) { ByteArray(0) }
    }

    fun verify(signature: ByteArray, data: ByteArray, pubKeyStr: String): Boolean {
        return try {
            val handle = CleartextKeysetHandle.read(JsonKeysetReader.withString(pubKeyStr))
            val verifier = handle.getPrimitive(PublicKeyVerify::class.java)
            verifier.verify(signature, data)
            true
        } catch (e: Exception) { false }
    }

    // -------------------- ШИФРОВАНИЕ / РАСШИФРОВКА --------------------

    fun encryptFor(pubKey: String, data: ByteArray): ByteArray {
        return try {
            val handle = CleartextKeysetHandle.read(JsonKeysetReader.withString(pubKey))
            val encrypt = handle.getPrimitive(HybridEncrypt::class.java)
            encrypt.encrypt(data, null)
        } catch (e: Exception) { ByteArray(0) }
    }

    fun decrypt(data: ByteArray): ByteArray {
        return try {
            val decrypt = myPrivateKey?.getPrimitive(HybridDecrypt::class.java)
            decrypt?.decrypt(data, null) ?: ByteArray(0)
        } catch (e: Exception) { ByteArray(0) }
    }

    fun encryptMessage(text: String, recipientPubKey: String): String {
        return Base64.encodeToString(encryptFor(recipientPubKey, text.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    fun decryptMessage(cipherText: String): String {
        return try {
            val bytes = Base64.decode(cipherText, Base64.NO_WRAP)
            String(decrypt(bytes), Charsets.UTF_8)
        } catch (e: Exception) { "[Ошибка расшифровки]" }
    }

    // -------------------- ПУБЛИЧНЫЕ КЛЮЧИ PEERS --------------------

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

    // -------------------- BACKUP / RESTORE --------------------

    fun createBackupPayload(password: String): String {
        val stream = ByteArrayOutputStream()
        CleartextKeysetHandle.write(myPrivateKey!!, JsonKeysetWriter.withOutputStream(stream))
        val privateData = stream.toString("UTF-8")
        return encryptWithPassword(privateData, password)
    }

    fun restoreIdentity(encryptedData: String, password: String): Boolean {
        return try {
            val decrypted = decryptWithPassword(encryptedData, password)
            myPrivateKey = CleartextKeysetHandle.read(JsonKeysetReader.withString(decrypted))
            true
        } catch (e: Exception) { false }
    }

    // -------------------- AES-GCM INTERNAL --------------------

    private fun encryptWithPassword(data: String, pass: String): String {
        val salt = ByteArray(16).apply { SecureRandom().nextBytes(this) }
        val spec = PBEKeySpec(pass.toCharArray(), salt, 65536, 256)
        val key = SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded, "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
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
        return String(cipher.doFinal(data), Charsets.UTF_8)
    }

    // -------------------- PREFS STORAGE --------------------

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
        if (!priStr.isNullOrBlank()) {
            myPrivateKey = CleartextKeysetHandle.read(JsonKeysetReader.withString(priStr))
        }
    }
}
