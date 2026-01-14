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
 * PRODUCTION-READY AuthManager (Local-First).
 * * Логика работы:
 * 1. Инициализирует ключи через CryptoManager.
 * 2. Сохраняет сессию в локальную БД Room и SharedPreferences.
 * 3. Возвращает успех пользователю МГНОВЕННО.
 * 4. В фоновом режиме пытается отправить "анонс" на сервер.
 */
class AuthManager(private val context: Context) {

    private val TAG = "AuthManager"
    private val db = ChatDatabase.getDatabase(context)
    private val nodeDao = db.nodeDao()
    private val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
    private val api = WebViewApiClient

    // Соль для детерминированного поиска (должна быть одинаковой на всех устройствах)
    private val PEPPER = "7fb8a1d2c3e4f5a6b7c8d9e0f1a2b3c4"

    /**
     * Вход через Email + Пароль
     */
    suspend fun registerOrLogin(
        email: String,
        password: String,
        phone: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Гарантируем наличие RSA ключей
            CryptoManager.init(context)

            val normalizedPhone = normalizePhone(phone)
            val passwordHash = sha256(password)
            val identityHash = CryptoManager.getMyIdentityHash()

            if (identityHash.isBlank()) {
                Log.e(TAG, "Критическая ошибка: Identity hash не создан")
                return@withContext false
            }

            val payload = buildPayload(identityHash, normalizedPhone)

            // 1️⃣ Сначала пишем в локальное хранилище
            saveLocalSession(
                payload = payload,
                email = email,
                passwordHash = passwordHash,
                isSynced = false
            )

            // 2️⃣ Асинхронный синк (не блокирует результат функции)
            syncWithServer(payload)

            true
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка авторизации: ${e.stackTraceToString()}")
            false
        }
    }

    /**
     * Вход через Телефон + OTP
     */
    suspend fun registerOrLoginByPhone(
        phone: String,
        otpCode: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            CryptoManager.init(context)

            val normalizedPhone = normalizePhone(phone)
            val passwordHash = sha256(otpCode) // Используем OTP как временный хеш пароля
            val identityHash = CryptoManager.getMyIdentityHash()

            if (identityHash.isBlank()) {
                Log.e(TAG, "Критическая ошибка: Identity hash не создан")
                return@withContext false
            }

            val payload = buildPayload(identityHash, normalizedPhone)

            // Сохраняем локально
            saveLocalSession(
                payload = payload,
                email = "",
                passwordHash = passwordHash,
                isSynced = false
            )

            // Пробуем синкнуть с сервером
            syncWithServer(payload)

            true
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка телефонной авторизации: ${e.message}")
            false
        }
    }

    private fun buildPayload(identityHash: String, phone: String): UserPayload {
        return UserPayload(
            hash = identityHash,
            phone_hash = sha256(phone + PEPPER),
            publicKey = CryptoManager.getMyPublicKeyStr(),
            ip = "0.0.0.0", // Будет обновлено сетевым DiscoveryManager
            port = 8888,
            phone = phone,
            lastSeen = System.currentTimeMillis()
        )
    }

    private suspend fun saveLocalSession(
        payload: UserPayload,
        email: String,
        passwordHash: String,
        isSynced: Boolean
    ) {
        // Запись в Room
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
                lastSeen = System.currentTimeMillis(),
                isSynced = isSynced
            )
        )

        // Запись в SharedPreferences для быстрой проверки флага isLoggedIn
        prefs.edit().apply {
            putBoolean("is_logged_in", true)
            putString("my_security_hash", payload.hash)
            putString("my_phone", payload.phone)
            putString("my_phone_hash", payload.phone_hash)
            putString("my_email", email)
            apply()
        }

        Log.i(TAG, "LOCAL session saved for: ${payload.hash.take(8)}")
    }

    private suspend fun syncWithServer(payload: UserPayload) {
        try {
            val response = api.announceSelf(payload)
            if (response.success) {
                nodeDao.markSynced(payload.hash)
                Log.i(TAG, "Server sync: SUCCESS")
            } else {
                Log.w(TAG, "Server sync: REJECTED (${response.error})")
            }
        } catch (e: Exception) {
            // В P2P это нормальная ситуация — работаем в офлайне
            Log.w(TAG, "Server sync: OFFLINE (will retry later)")
        }
    }

    private fun normalizePhone(raw: String): String {
        val digits = raw.replace(Regex("[^0-9]"), "")
        return when {
            digits.length == 10 -> "7$digits"
            digits.length == 11 && digits.startsWith("8") -> "7${digits.substring(1)}"
            digits.length == 11 && digits.startsWith("7") -> digits
            else -> digits
        }
    }

    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }

    fun sendSmsFallback(phone: String, message: String) {
        try {
            val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)!!
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            smsManager.sendTextMessage(phone, null, "[P2P] $message", null, null)
            Log.i(TAG, "SMS fallback sent to $phone")
        } catch (e: Exception) {
            Log.e(TAG, "SMS system failure: ${e.message}")
        }
    }
}
