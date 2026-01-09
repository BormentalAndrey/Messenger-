package com.kakdela.p2p.security

import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.crypto.tink.*
import com.google.crypto.tink.hybrid.*
import com.google.crypto.tink.signature.*
import java.io.ByteArrayOutputStream

object CryptoManager {

    private const val TAG = "CryptoManager"
    private const val PREFS = "crypto_keys"

    private var encryptKeyset: KeysetHandle? = null
    private var signKeyset: KeysetHandle? = null

    private val peerKeys = mutableMapOf<String, String>()

    // ================= INIT =================

    fun init(context: Context) {
        try {
            HybridConfig.register()
            SignatureConfig.register()
            loadKeys(context)
            if (encryptKeyset == null || signKeyset == null) {
                generateKeys(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Init error", e)
        }
    }

    // ================= KEYS =================

    private fun generateKeys(context: Context) {
        encryptKeyset = KeysetHandle.generateNew(
            HybridKeyTemplates.ECIES_P256_HKDF_HMAC_SHA256_AES128_GCM
        )
        signKeyset = KeysetHandle.generateNew(
            SignatureKeyTemplates.ECDSA_P256
        )
        saveKeys(context)
    }

    fun getMyPublicKeyStr(): String = writeKey(encryptKeyset?.publicKeysetHandle)

    fun savePeerPublicKey(peerHash: String, key: String) {
        peerKeys[peerHash] = key
    }

    fun getPeerPublicKey(peerHash: String): String? = peerKeys[peerHash]

    // ================= SIGN =================

    fun sign(data: ByteArray): ByteArray =
        try {
            signKeyset!!
                .getPrimitive(PublicKeySign::class.java)
                .sign(data)
        } catch (e: Exception) {
            ByteArray(0)
        }

    fun verify(signature: ByteArray, data: ByteArray, pubKeyStr: String): Boolean =
        try {
            val handle = readKey(pubKeyStr)
            handle.getPrimitive(PublicKeyVerify::class.java)
                .verify(signature, data)
            true
        } catch (e: Exception) {
            false
        }

    // ================= ENCRYPT =================

    fun encryptMessage(message: String, peerPublicKey: String): String =
        try {
            val handle = readKey(peerPublicKey)
            val encryptor = handle.getPrimitive(HybridEncrypt::class.java)
            Base64.encodeToString(
                encryptor.encrypt(message.toByteArray(), null),
                Base64.NO_WRAP
            )
        } catch (e: Exception) {
            "[ENCRYPT_ERROR]"
        }

    fun decryptMessage(base64: String): String =
        try {
            val decryptor =
                encryptKeyset!!
                    .getPrimitive(HybridDecrypt::class.java)
            String(
                decryptor.decrypt(
                    Base64.decode(base64, Base64.NO_WRAP),
                    null
                )
            )
        } catch (e: Exception) {
            "[DECRYPT_ERROR]"
        }

    // ================= STORAGE =================

    private fun saveKeys(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("enc", writeKey(encryptKeyset))
            .putString("sign", writeKey(signKeyset))
            .apply()
    }

    private fun loadKeys(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString("enc", null)?.let { encryptKeyset = readKey(it) }
        prefs.getString("sign", null)?.let { signKeyset = readKey(it) }
    }

    private fun writeKey(handle: KeysetHandle?): String {
        if (handle == null) return ""
        val stream = ByteArrayOutputStream()
        CleartextKeysetHandle.write(handle, JsonKeysetWriter.withOutputStream(stream))
        return stream.toString("UTF-8")
    }

    private fun readKey(str: String): KeysetHandle =
        CleartextKeysetHandle.read(JsonKeysetReader.withString(str))
}
