package com.kakdela.p2p.data

import android.content.Context
import android.util.Log
import com.kakdela.p2p.api.MyServerApiFactory
import com.kakdela.p2p.api.UserPayload
import com.kakdela.p2p.api.UserRegistrationWrapper
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
    private val api = MyServerApiFactory.instance

    // Pepper должен совпадать с IdentityRepository
    private val PEPPER = "7fb8a1d2c3e4f5a6"

    suspend fun registerOrLogin(
        email: String,
        password: String,
        phone: String
    ): Boolean = withContext(Dispatchers.IO) {

        val normalizedPhone = normalizePhone(phone)
        val passwordHash = sha256(password)
        
        // Генерируем уникальный hash
        val securityHash = sha256("$normalizedPhone|$email|$passwordHash")
        
        // Генерируем hash телефона для поиска другими пользователями
        val phoneHash = sha256(normalizedPhone + PEPPER)
        
        val publicKey = CryptoManager.getMyPublicKeyStr()

        // Создаем payload для отправки на сервер
        val payload = UserPayload(
            hash = securityHash,
            phone_hash = phoneHash,
            ip = "0.0.0.0", // Сервер заменит на REMOTE_ADDR
            port = 8888,
            publicKey = publicKey,
            phone = normalizedPhone,
            email = email,
            lastSeen = System.currentTimeMillis()
        )

        try {
            Log.d(TAG, "Attempting server announce...")
            val wrapper = UserRegistrationWrapper(securityHash, payload)
            val response = api.announceSelf(payload = wrapper)
            
            if (response.success) {
                Log.d(TAG, "Server announce SUCCESS")
            } else {
                Log.w(TAG, "Server announce FAILED: ${response.error}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Offline mode activated: ${e.message}")
        }

        // Локальное сохранение сессии (Источник истины для приложения)
        createLocalSession(payload, email, passwordHash, securityHash)

        return@withContext true
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

            context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE).edit()
                .putBoolean("is_logged_in", true)
                .putString("my_security_hash", securityHash)
                .putString("my_phone", payload.phone)
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
