package com.kakdela.p2p.security

import android.util.Base64
import com.google.crypto.tink.*
import com.google.crypto.tink.hybrid.HybridConfig
import com.google.crypto.tink.hybrid.HybridKeyTemplates
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.security.spec.KeySpec
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object CryptoManager {
    private var myPrivateKey: KeysetHandle? = null

    // Настройки для превращения пароля в ключ (Key Derivation)
    private const val PBKDF2_ITERATIONS = 65536
    private const val KEY_LENGTH = 256
    private const val SALT_SIZE = 16
    private const val IV_SIZE = 12 // Для AES-GCM
    private const val TAG_SIZE = 128

    init {
        HybridConfig.register()
        // В реальном приложении: проверка наличия ключа в локальном хранилище устройства
        if (myPrivateKey == null) {
            myPrivateKey = KeysetHandle.generateNew(HybridKeyTemplates.ECIES_P256_HKDF_HMAC_SHA256_AES128_GCM)
        }
    }

    /**
     * Превращает приватный ключи в зашифрованную строку для хранения у других пользователей (DHT).
     * Использует пароль пользователя для шифрования.
     */
    fun exportEncryptedKeyset(password: String): String {
        return try {
            // 1. Получаем приватный ключ в сыром виде (JSON)
            val stream = ByteArrayOutputStream()
            CleartextKeysetHandle.write(myPrivateKey, JsonKeysetWriter.withOutputStream(stream))
            val rawKeyset = stream.toByteArray()

            // 2. Генерируем случайную Соль
            val salt = ByteArray(SALT_SIZE)
            SecureRandom().nextBytes(salt)

            // 3. Создаем ключ из пароля
            val secretKey = deriveKey(password, salt)

            // 4. Шифруем AES-GCM
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(rawKeyset)

            // 5. Упаковываем: SALT + IV + ENCRYPTED_DATA
            val result = salt + iv + encryptedBytes
            Base64.encodeToString(result, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    /**
     * Восстанавливает приватный ключ из зашифрованной строки, полученной из сети.
     */
    fun importEncryptedKeyset(encryptedData: String, password: String): Boolean {
        return try {
            val fullData = Base64.decode(encryptedData, Base64.NO_WRAP)
            
            // 1. Извлекаем компоненты
            val salt = fullData.copyOfRange(0, SALT_SIZE)
            val iv = fullData.copyOfRange(SALT_SIZE, SALT_SIZE + IV_SIZE)
            val cipherText = fullData.copyOfRange(SALT_SIZE + IV_SIZE, fullData.size)

            // 2. Деривация ключа из пароля с той же солью
            val secretKey = deriveKey(password, salt)

            // 3. Дешифровка
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(TAG_SIZE, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            
            val decryptedRawKeyset = cipher.doFinal(cipherText)

            // 4. Загружаем KeysetHandle
            myPrivateKey = CleartextKeysetHandle.read(JsonKeysetReader.withBytes(decryptedRawKeyset))
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * PBKDF2 алгоритм для генерации криптографического ключа из текстового пароля.
     */
    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec: KeySpec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH)
        val tmp = factory.generateSecret(spec)
        return SecretKeySpec(tmp.encoded, "AES")
    }

    // --- Стандартные методы P2P обмена ---

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
        } catch (e: Exception) {
            ByteArray(0)
        }
    }

    fun decryptMessage(cipherBytes: ByteArray): String {
        return try {
            val hybridDecrypt = myPrivateKey?.getPrimitive(HybridDecrypt::class.java)
            val decryptedBytes = hybridDecrypt?.decrypt(cipherBytes, null)
            String(decryptedBytes!!, Charsets.UTF_8)
        } catch (e: Exception) {
            "[Decryption Error]"
        }
    }
}

