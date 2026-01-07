package com.kakdela.p2p.security

import android.content.Context
import android.util.Base64
import com.google.crypto.tink.*
import com.google.crypto.tink.hybrid.HybridConfig
import com.google.crypto.tink.hybrid.HybridKeyTemplates
import com.google.crypto.tink.signature.SignatureConfig
import com.google.crypto.tink.signature.SignatureKeyTemplates
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.KeySpec
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object CryptoManager {
    private var myPrivateKey: KeysetHandle? = null
    private var mySigningKey: KeysetHandle? = null // Для цифровых подписей пакетов

    private const val PBKDF2_ITERATIONS = 65536
    private const val KEY_LENGTH = 256
    private const val SALT_SIZE = 16
    private const val IV_SIZE = 12
    private const val TAG_SIZE = 128

    /**
     * Вызывается из MainActivity. Инициализирует Tink и проверяет ключи.
     */
    fun init(context: Context) {
        try {
            HybridConfig.register()
            SignatureConfig.register()

            // В идеале здесь должна быть загрузка из Android Keystore
            // Для текущей версии генерируем новые, если их нет в памяти
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

    /* ----------------------------------------------------
       P2P PACKET SIGNING (Для IdentityRepository)
     ---------------------------------------------------- */

    fun sign(data: ByteArray): ByteArray {
        return try {
            val signer = mySigningKey?.getPrimitive(PublicKeySign::class.java)
            signer?.sign(data) ?: ByteArray(0)
        } catch (e: Exception) {
            ByteArray(0)
        }
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

    /**
     * В текущей архитектуре P2P мы получаем публичный ключ из DHT по хэшу.
     * Возвращает строку ключа, которую можно использовать в verify.
     */
    fun getPublicKeyByHash(hash: String): String? {
        // Здесь должен быть запрос в локальную базу Room или DHT слайс.
        // Пока возвращаем заглушку или механизм поиска.
        return null 
    }

    /* ----------------------------------------------------
       IDENTITY EXPORT/IMPORT
     ---------------------------------------------------- */

    fun exportEncryptedKeyset(password: String): String {
        return try {
            val stream = ByteArrayOutputStream()
            CleartextKeysetHandle.write(myPrivateKey, JsonKeysetWriter.withOutputStream(stream))
            val rawKeyset = stream.toByteArray()

            val salt = ByteArray(SALT_SIZE)
            SecureRandom().nextBytes(salt)

            val secretKey = deriveKey(password, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            
            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(rawKeyset)
            val result = salt + iv + encryptedBytes
            
            Base64.encodeToString(result, Base64.NO_WRAP)
        } catch (e: Exception) { "" }
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec: KeySpec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH)
        val tmp = factory.generateSecret(spec)
        return SecretKeySpec(tmp.encoded, "AES")
    }

    /* ----------------------------------------------------
       MESSAGE ENCRYPTION
     ---------------------------------------------------- */

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

