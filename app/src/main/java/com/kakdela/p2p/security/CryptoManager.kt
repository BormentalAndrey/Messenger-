package com.kakdela.p2p.security

import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.CleartextKeysetHandle
import com.google.crypto.tink.JsonKeysetReader
import com.google.crypto.tink.JsonKeysetWriter
import com.google.crypto.tink.hybrid.HybridConfig
import com.google.crypto.tink.hybrid.HybridDecrypt
import com.google.crypto.tink.hybrid.HybridEncrypt
import com.google.crypto.tink.hybrid.HybridKeyTemplates
import com.google.crypto.tink.signature.PublicKeySign
import com.google.crypto.tink.signature.PublicKeyVerify
import com.google.crypto.tink.signature.SignatureConfig
import com.google.crypto.tink.signature.SignatureKeyTemplates
import java.io.ByteArrayOutputStream

object CryptoManager {

    private const val TAG = "CryptoManager"

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

    fun generateKeysIfNeeded(context: Context) {
        if (myEncryptKeyset == null || mySignKeyset == null) {
            myEncryptKeyset = KeysetHandle.generateNew(HybridKeyTemplates.ECIES_P256_HKDF_HMAC_SHA256_AES128_GCM)
            mySignKeyset = KeysetHandle.generateNew(SignatureKeyTemplates.ECDSA_P256)
            saveKeys(context)
        }
    }

    fun sign(data: ByteArray): ByteArray =
        try {
            mySignKeyset!!.getPrimitive(PublicKeySign::class.java).sign(data)
        } catch (e: Exception) {
            ByteArray(0)
        }

    fun verify(signature: ByteArray, data: ByteArray, pubKeyStr: String): Boolean =
        try {
            val handle = CleartextKeysetHandle.read(JsonKeysetReader.withString(pubKeyStr))
            val verifier = handle.getPrimitive(PublicKeyVerify::class.java)
            verifier.verify(signature, data)
            true
        } catch (e: Exception) {
            false
        }

    fun encryptMessage(message: String, peerPublicKey: String): String =
        try {
            val handle = CleartextKeysetHandle.read(JsonKeysetReader.withString(peerPublicKey))
            val encryptor = handle.getPrimitive(HybridEncrypt::class.java)
            val encrypted = encryptor.encrypt(message.toByteArray(), null)
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
        } catch (e: Exception) {
            "[Ошибка шифрования]"
        }

    fun decryptMessage(base64: String): String =
        try {
            val decryptor = myEncryptKeyset!!.getPrimitive(HybridDecrypt::class.java)
            val decrypted = decryptor.decrypt(Base64.decode(base64, Base64.NO_WRAP), null)
            String(decrypted)
        } catch (e: Exception) {
            "[Ошибка дешифрования]"
        }

    fun getMyPublicKeyStr(): String =
        try {
            val stream = ByteArrayOutputStream()
            CleartextKeysetHandle.write(myEncryptKeyset!!.publicKeysetHandle, JsonKeysetWriter.withOutputStream(stream))
            stream.toString("UTF-8")
        } catch (e: Exception) {
            ""
        }

    fun savePeerPublicKey(peerHash: String, key: String) {
        peerPublicKeys[peerHash] = key
    }

    fun getPeerPublicKey(peerHash: String): String? = peerPublicKeys[peerHash]

    private fun saveKeys(context: Context) {
        val prefs = context.getSharedPreferences("crypto_keys", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("enc_key", writeKey(myEncryptKeyset))
            putString("sign_key", writeKey(mySignKeyset))
            apply()
        }
    }

    fun loadKeys(context: Context) {
        val prefs = context.getSharedPreferences("crypto_keys", Context.MODE_PRIVATE)
        prefs.getString("enc_key", null)?.let { myEncryptKeyset = readKey(it) }
        prefs.getString("sign_key", null)?.let { mySignKeyset = readKey(it) }
    }

    private fun writeKey(handle: KeysetHandle?): String {
        val stream = ByteArrayOutputStream()
        CleartextKeysetHandle.write(handle, JsonKeysetWriter.withOutputStream(stream))
        return stream.toString("UTF-8")
    }

    private fun readKey(str: String): KeysetHandle = CleartextKeysetHandle.read(JsonKeysetReader.withString(str))
}
