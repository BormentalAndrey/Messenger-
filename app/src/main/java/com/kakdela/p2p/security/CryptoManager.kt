package com.kakdela.p2p.security

import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.crypto.tink.*
import com.google.crypto.tink.hybrid.HybridConfig
import com.google.crypto.tink.hybrid.HybridDecrypt
import com.google.crypto.tink.hybrid.HybridEncrypt
import com.google.crypto.tink.hybrid.HybridKeyTemplates
import com.google.crypto.tink.signature.SignatureConfig
import com.google.crypto.tink.signature.SignatureKeyTemplates
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object CryptoManager {

    private var myPrivateKeyHandle: KeysetHandle? = null
    private var mySigningKeyHandle: KeysetHandle? = null

    init {
        try {
            HybridConfig.register()
            SignatureConfig.register()
        } catch (e: Exception) {
            Log.e("CryptoManager", "Tink init failed", e)
        }
    }

    fun init(context: Context) {
        loadKeysFromPrefs(context)
        if (!isKeyReady()) generateKeys(context)
    }

    fun isKeyReady(): Boolean = myPrivateKeyHandle != null && mySigningKeyHandle != null

    fun generateKeysIfNeeded(context: Context) {
        if (!isKeyReady()) generateKeys(context)
    }

    fun generateKeys(context: Context) {
        try {
            myPrivateKeyHandle = KeysetHandle.generateNew(HybridKeyTemplates.ECIES_P256_HKDF_HMAC_SHA256_AES128_GCM)
            mySigningKeyHandle = KeysetHandle.generateNew(SignatureKeyTemplates.ECDSA_P256)
            saveKeysToPrefs(context)
        } catch (e: Exception) {
            Log.e("CryptoManager", "Generation error", e)
        }
    }

    // -------------------- ПОДПИСЬ --------------------

    fun sign(data: ByteArray): ByteArray = try {
        val signer = mySigningKeyHandle?.getPrimitive(PublicKeySign::class.java)
        signer?.sign(data) ?: ByteArray(0)
    } catch (e: Exception) { ByteArray(0) }

    fun verify(signature: ByteArray, data: ByteArray, pubKeyStr: String): Boolean = try {
        val handle = CleartextKeysetHandle.read(JsonKeysetReader.withString(pubKeyStr))
        val verifier = handle.getPrimitive(PublicKeyVerify::class.java)
        verifier.verify(signature, data)
        true
    } catch (e: Exception) { false }

    // -------------------- ШИФРОВАНИЕ --------------------

    fun encryptFor(pubKeyStr: String, data: ByteArray): ByteArray = try {
        val handle = CleartextKeysetHandle.read(JsonKeysetReader.withString(pubKeyStr))
        val primitive = handle.getPrimitive(HybridEncrypt::class.java)
        primitive.encrypt(data, null)
    } catch (e: Exception) { ByteArray(0) }

    fun decrypt(data: ByteArray): ByteArray = try {
        val primitive = myPrivateKeyHandle?.getPrimitive(HybridDecrypt::class.java)
        primitive?.decrypt(data, null) ?: ByteArray(0)
    } catch (e: Exception) { ByteArray(0) }

    fun getMyPublicKeyStr(): String = try {
        val publicHandle = myPrivateKeyHandle?.publicKeysetHandle
        val stream = ByteArrayOutputStream()
        CleartextKeysetHandle.write(publicHandle, JsonKeysetWriter.withOutputStream(stream))
        stream.toString("UTF-8")
    } catch (e: Exception) { "" }

    // -------------------- AES BACKUP --------------------

    private fun encryptWithPassword(data: String, pass: String): String {
        val salt = ByteArray(16).apply { SecureRandom().nextBytes(this) }
        val spec = PBEKeySpec(pass.toCharArray(), salt, 65536, 256)
        val key = SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded, "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encrypted = cipher.doFinal(data.toByteArray())
        return Base64.encodeToString(salt + cipher.iv + encrypted, Base64.NO_WRAP)
    }

    // -------------------- ХРАНЕНИЕ --------------------

    private fun saveKeysToPrefs(context: Context) {
        val prefs = context.getSharedPreferences("crypto_keys", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        
        myPrivateKeyHandle?.let {
            val stream = ByteArrayOutputStream()
            CleartextKeysetHandle.write(it, JsonKeysetWriter.withOutputStream(stream))
            editor.putString("pri_key", stream.toString("UTF-8"))
        }
        mySigningKeyHandle?.let {
            val stream = ByteArrayOutputStream()
            CleartextKeysetHandle.write(it, JsonKeysetWriter.withOutputStream(stream))
            editor.putString("sign_key", stream.toString("UTF-8"))
        }
        editor.apply()
    }

    private fun loadKeysFromPrefs(context: Context) {
        val prefs = context.getSharedPreferences("crypto_keys", Context.MODE_PRIVATE)
        prefs.getString("pri_key", null)?.let {
            myPrivateKeyHandle = CleartextKeysetHandle.read(JsonKeysetReader.withString(it))
        }
        prefs.getString("sign_key", null)?.let {
            mySigningKeyHandle = CleartextKeysetHandle.read(JsonKeysetReader.withString(it))
        }
    }
}
