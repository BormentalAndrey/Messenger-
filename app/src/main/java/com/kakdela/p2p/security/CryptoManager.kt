package com.kakdela.p2p.security

import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.crypto.tink.*
import com.google.crypto.tink.hybrid.*
import com.google.crypto.tink.signature.*
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

object CryptoManager {
    private const val TAG = "CryptoManager"
    private const val PREFS_NAME = "crypto_keys_prefs"
    private const val KEY_ENC = "enc_keyset_json"
    private const val KEY_SIGN = "sign_keyset_json"

    private var myEncryptKeyset: KeysetHandle? = null
    private var mySignKeyset: KeysetHandle? = null
    private val peerPublicKeys = mutableMapOf<String, String>()

    init {
        try {
            HybridConfig.register()
            SignatureConfig.register()
        } catch (e: Exception) {
            Log.e(TAG, "Tink init error", e)
        }
    }

    fun init(context: Context) {
        loadKeys(context)
        if (myEncryptKeyset == null || mySignKeyset == null) generateKeys(context)
    }

    fun generateKeysIfNeeded(context: Context) = init(context)

    private fun generateKeys(context: Context) {
        try {
            myEncryptKeyset = KeysetHandle.generateNew(HybridKeyTemplates.ECIES_P256_HKDF_HMAC_SHA256_AES128_GCM)
            mySignKeyset = KeysetHandle.generateNew(SignatureKeyTemplates.ECDSA_P256)
            saveKeys(context)
        } catch (e: Exception) { Log.e(TAG, "Key generation failed", e) }
    }

    // ИСПРАВЛЕНО: Явное указание класса примитива для устранения "infer type variable P"
    fun sign(data: ByteArray): ByteArray = try {
        val signer = mySignKeyset?.getPrimitive(PublicKeySign::class.java)
        signer?.sign(data) ?: byteArrayOf()
    } catch (e: Exception) { byteArrayOf() }

    fun verify(signature: ByteArray, data: ByteArray, pubKeyStr: String): Boolean = try {
        val handle = CleartextKeysetHandle.read(JsonKeysetReader.withString(pubKeyStr))
        val verifier = handle.getPrimitive(PublicKeyVerify::class.java)
        verifier.verify(signature, data)
        true
    } catch (e: Exception) { false }

    fun encryptMessage(message: String, peerPublicKey: String): String = try {
        val handle = CleartextKeysetHandle.read(JsonKeysetReader.withString(peerPublicKey))
        val encryptor = handle.getPrimitive(HybridEncrypt::class.java)
        val encrypted = encryptor.encrypt(message.toByteArray(StandardCharsets.UTF_8), null)
        Base64.encodeToString(encrypted, Base64.NO_WRAP)
    } catch (e: Exception) { message }

    fun decryptMessage(base64: String): String = try {
        val decryptor = myEncryptKeyset?.getPrimitive(HybridDecrypt::class.java)
        val decoded = Base64.decode(base64, Base64.NO_WRAP)
        val decrypted = decryptor?.decrypt(decoded, null)
        decrypted?.let { String(it, StandardCharsets.UTF_8) } ?: ""
    } catch (e: Exception) { "[Decrypt Error]" }

    fun getMyPublicKeyStr(): String = try {
        val stream = ByteArrayOutputStream()
        // ИСПРАВЛЕНО: корректное обращение к publicKeysetHandle
        myEncryptKeyset?.publicKeysetHandle?.let { publicHandle ->
            CleartextKeysetHandle.write(publicHandle, JsonKeysetWriter.withOutputStream(stream))
        }
        stream.toString("UTF-8")
    } catch (e: Exception) { "" }

    fun savePeerPublicKey(hash: String, key: String) { peerPublicKeys[hash] = key }
    fun getPeerPublicKey(hash: String): String? = peerPublicKeys[hash]

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
