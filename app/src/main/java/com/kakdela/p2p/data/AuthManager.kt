package com.kakdela.p2p.data

import android.content.Context
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
 * Менеджер аутентификации и регистрации узла в P2P рое.
 * Отвечает за генерацию идентификаторов, хеширование и синхронизацию с сервером-трекером.
 */
class AuthManager(private val context: Context) {

    private val TAG = "AuthManager"
    private val db = ChatDatabase.getDatabase(context)
    private val nodeDao = db.nodeDao()
    
    // Используем синглтон API клиента
    private val api = WebViewApiClient

    // Соль для хеширования телефона (должна совпадать на всех узлах для поиска)
    private val PEPPER = "7fb8a1d2c3e4f5a6"

    /**
     * Основной метод входа/регистрации.
     * 1. Нормализует данные.
     * 2. Генерирует уникальный securityHash (ID личности).
     * 3. Отправляет анонс на сервер через WebView (проход антибота).
     * 4. При успехе сохраняет сессию локально.
     */
    suspend fun registerOrLogin(
        email: String,
        password: String,
        phone: String
    ): Boolean = withContext(Dispatchers.IO) {

        val normalizedPhone = normalizePhone(phone)
        val passwordHash = sha256(password)
        
        // Генерируем уникальный хэш пользователя (ID в глобальном рое)
        val securityHash = sha256("$normalizedPhone|$email|$passwordHash")
        
        // Генерируем публичный хэш телефона для поиска контактов другими людьми
        val phoneHash = sha256(normalizedPhone + PEPPER)
        
        // Получаем RSA публичный ключ (сгенерированный CryptoManager)
        val publicKey = CryptoManager.getMyPublicKeyStr()

        // Формируем данные узла
        val payload = UserPayload(
            hash = securityHash,
            phone_hash = phoneHash,
            ip = "0.0.0.0", // Сервер сам определит наш внешний IP
            port = 8888,    // Стандартный порт нашего P2P роя
            publicKey = publicKey,
            phone = normalizedPhone,
            email = email,
            lastSeen = System.currentTimeMillis()
        )

        try {
            Log.d(TAG, "Attempting server announce for hash: ${securityHash.take(8)}...")
            
            /* ИСПРАВЛЕНО: 
               Мы больше не используем UserRegistrationWrapper здесь.
               Передаем напрямую payload. WebViewApiClient сам обернет его в {"data": ...}
               Это устраняет ошибку "Invalid JSON data" на PHP стороне.
            */
            val response = api.announceSelf(payload)

            if (response.success) {
                Log.d(TAG, "Server announce SUCCESS: ${response.status}")
                
                // Сохраняем данные в локальную БД и SharedPreferences
                createLocalSession(payload, email, passwordHash, securityHash)
                return@withContext true
            } else {
                Log.w(TAG, "Server announce FAILED: ${response.error}")
                return@withContext false
            }

        } catch (e: Exception) {
            Log.e(TAG, "Auth Error: ${e.message}", e)
            return@withContext false
        }
    }

    /**
     * Сохраняет данные о собственной личности в локальную БД.
     */
    private suspend fun createLocalSession(
        payload: UserPayload,
        email: String,
        passwordHash: String,
        securityHash: String
    ) {
        try {
            // Сохраняем себя как основной узел в таблицу NodeEntity
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

            // Сохраняем флаг авторизации для быстрого доступа
            context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("is_logged_in", true)
                .putString("my_security_hash", securityHash)
                .putString("my_phone", payload.phone)
                .putString("my_email", email)
                .apply()

            Log.d(TAG, "Local session created successfully for $securityHash")

        } catch (e: Exception) {
            Log.e(TAG, "Local auth save failed", e)
        }
    }

    /**
     * Приводит номер телефона к международному формату 7XXXXXXXXXX.
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
     * SHA-256 хеширование.
     */
    private fun sha256(input: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
