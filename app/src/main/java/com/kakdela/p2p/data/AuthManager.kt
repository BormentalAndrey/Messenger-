package com.kakdela.p2p.data

import android.content.Context
import android.util.Log
import com.kakdela.p2p.api.UserPayload
import com.kakdela.p2p.api.UserRegistrationWrapper
import com.kakdela.p2p.api.WebViewApiClient
import com.kakdela.p2p.data.local.ChatDatabase
import com.kakdela.p2p.data.local.NodeEntity
import com.kakdela.p2p.security.CryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

class AuthManager(private val context: Context) {

    private val TAG = "AuthManager"
    private val db = ChatDatabase.getDatabase(context)
    private val nodeDao = db.nodeDao()
    
    // Используем синглтон
    private val api = WebViewApiClient

    private val PEPPER = "7fb8a1d2c3e4f5a6"

    suspend fun registerOrLogin(
        email: String,
        password: String,
        phone: String
    ): Boolean = withContext(Dispatchers.IO) {

        val normalizedPhone = normalizePhone(phone)
        val passwordHash = sha256(password)
        val securityHash = sha256("$normalizedPhone|$email|$passwordHash")
        val phoneHash = sha256(normalizedPhone + PEPPER)
        val publicKey = CryptoManager.getMyPublicKeyStr()

        val payload = UserPayload(
            hash = securityHash,
            phone_hash = phoneHash,
            ip = "0.0.0.0",
            port = 8888,
            publicKey = publicKey,
            phone = normalizedPhone,
            email = email,
            lastSeen = System.currentTimeMillis()
        )

        try {
            Log.d(TAG, "Attempting server announce via WebView...")
            
            // Оборачиваем данные
            val wrapper = UserRegistrationWrapper(securityHash, payload)
            
            // Отправляем через WebView (теперь корректно сериализуется)
            val response = api.announceSelf(wrapper)

            if (response.success) {
                Log.d(TAG, "Server announce SUCCESS")
                createLocalSession(payload, email, passwordHash, securityHash)
                return@withContext true
            } else {
                Log.w(TAG, "Server announce FAILED: ${response.error}")
                // Если ошибка "wait", retry механизм внутри WebViewApiClient уже отработал 3 раза
                return@withContext false
            }

        } catch (e: Exception) {
            Log.e(TAG, "Auth Error: ${e.message}")
            return@withContext false
        }
    }

    private suspend fun createLocalSession(
        payload: UserPayload,
        email: String,
        passwordHash: String,
        securityHash: String
    ) {
        try {
            nodeDao.insert(
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
                .putString("my_security_hash", securityHash)
                .putString("my_phone", payload.phone)
                .putString("my_email", email)
                .apply()

            Log.d(TAG, "Local session created successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Local auth failed", e)
        }
    }

    private fun normalizePhone(raw: String): String {
        val digits = raw.replace(Regex("[^0-9]"), "")
        return when {
            digits.length == 10 && digits.startsWith("9") -> "7$digits"
            digits.length == 11 && digits.startsWith("8") -> "7${digits.substring(1)}"
            else -> digits
        }
    }

    private fun sha256(input: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
