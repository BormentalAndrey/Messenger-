package com.kakdela.p2p.data

import android.content.Context
import android.telephony.SmsManager
import android.util.Log
import com.kakdela.p2p.api.UserPayload
import com.kakdela.p2p.api.WebViewApiClient
import com.kakdela.p2p.data.local.ChatDatabase
import com.kakdela.p2p.data.local.NodeEntity
import com.kakdela.p2p.security.CryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/**
 * Менеджер аутентификации и регистрации узла в P2P-сети.
 * Работает СТРОГО синхронно с IdentityRepository и CryptoManager.
 */
class AuthManager(private val context: Context) {

    private val TAG = "AuthManager"

    private val db = ChatDatabase.getDatabase(context)
    private val nodeDao = db.nodeDao()
    private val api = WebViewApiClient

    // ДОЛЖЕН совпадать с IdentityRepository
    private val PEPPER = "7fb8a1d2c3e4f5a6b7c8d9e0f1a2b3c4"

    /**
     * Регистрация / вход по email + пароль
     */
    suspend fun registerOrLogin(
        email: String,
        password: String,
        phone: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val normalizedPhone = normalizePhone(phone)
            val passwordHash = sha256(password)

            CryptoManager.init(context)
            val securityHash = CryptoManager.getMyIdentityHash()
            if (securityHash.isEmpty()) {
                Log.e(TAG, "Identity hash generation failed")
                return@withContext false
            }

            val phoneHash = sha256(normalizedPhone + PEPPER)
            val publicKey = CryptoManager.getMyPublicKeyStr()

            val payload = UserPayload(
                hash = securityHash,
                phone_hash = phoneHash,
                publicKey = publicKey,
                ip = "0.0.0.0",
                port = 8888,
                phone = normalizedPhone,
                lastSeen = System.currentTimeMillis()
            )

            Log.d(TAG, "Announce self: ${securityHash.take(8)}")
            val response = api.announceSelf(payload)
            if (!response.success) {
                Log.w(TAG, "Server reject: ${response.error}")
                return@withContext false
            }

            createLocalSession(payload, email, passwordHash)
            Log.i(TAG, "Auth SUCCESS for ${securityHash.take(8)}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Auth error", e)
            false
        }
    }

    /**
     * Регистрация / вход по телефону с OTP
     */
    suspend fun registerOrLoginByPhone(
        phone: String,
        otpCode: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val normalizedPhone = normalizePhone(phone)

            // В продакшн можно здесь проверить OTP на сервере / через SMS fallback
            // Для P2P прототипа OTP используется как временный пароль
            val passwordHash = sha256(otpCode)

            CryptoManager.init(context)
            val securityHash = CryptoManager.getMyIdentityHash()
            if (securityHash.isEmpty()) {
                Log.e(TAG, "Identity hash generation failed")
                return@withContext false
            }

            val phoneHash = sha256(normalizedPhone + PEPPER)
            val publicKey = CryptoManager.getMyPublicKeyStr()

            val payload = UserPayload(
                hash = securityHash,
                phone_hash = phoneHash,
                publicKey = publicKey,
                ip = "0.0.0.0",
                port = 8888,
                phone = normalizedPhone,
                lastSeen = System.currentTimeMillis()
            )

            Log.d(TAG, "Announce self (phone): ${securityHash.take(8)}")
            val response = api.announceSelf(payload)
            if (!response.success) {
                Log.w(TAG, "Server reject: ${response.error}")
                return@withContext false
            }

            // Сохраняем локально без email
            createLocalSession(payload, email = "", passwordHash = passwordHash)
            Log.i(TAG, "Phone Auth SUCCESS for ${securityHash.take(8)}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Phone auth error", e)
            false
        }
    }

    /**
     * Сохраняет локальную сессию.
     */
    private suspend fun createLocalSession(
        payload: UserPayload,
        email: String,
        passwordHash: String
    ) {
        try {
            nodeDao.upsert(
                NodeEntity(
                    userHash = payload.hash,
                    phone_hash = payload.phone_hash ?: "",
                    email = email,
                    passwordHash = passwordHash,
                    phone = payload.phone ?: "",
                    ip = payload.ip ?: "0.0.0.0",
                    port = payload.port,
                    publicKey = payload.publicKey,
                    lastSeen = System.currentTimeMillis()
                )
            )

            context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("is_logged_in", true)
                .putString("my_security_hash", payload.hash)
                .putString("my_phone", payload.phone)
                .putString("my_phone_hash", payload.phone_hash)
                .putString("my_email", email)
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Local session save failed", e)
        }
    }

    /**
     * Приведение телефона к международному формату.
     */
    private fun normalizePhone(raw: String): String {
        val digits = raw.replace(Regex("[^0-9]"), "")
        return when {
            digits.length == 10 && digits.startsWith("9") -> "7$digits"
            digits.length == 11 && digits.startsWith("8") -> "7${digits.substring(1)}"
            digits.length == 11 && digits.startsWith("7") -> digits
            else -> digits
        }
    }

    /**
     * SHA-256.
     */
    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }

    /**
     * Отправка SMS (fallback, используется по необходимости)
     */
    fun sendSmsFallback(phone: String, message: String) {
        try {
            val smsManager: SmsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION") SmsManager.getDefault()
            }
            smsManager?.sendTextMessage(phone, null, "[P2P] $message", null, null)
            Log.i(TAG, "SMS sent to $phone")
        } catch (e: Exception) {
            Log.e(TAG, "SMS sending failed: ${e.message}")
        }
    }
}
