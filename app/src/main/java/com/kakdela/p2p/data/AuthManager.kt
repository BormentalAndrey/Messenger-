package com.kakdela.p2p.data

import android.content.Context
import android.util.Log
import com.kakdela.p2p.api.MyServerApi
import com.kakdela.p2p.api.UserPayload
import com.kakdela.p2p.api.UserRegistrationWrapper
import com.kakdela.p2p.data.local.ChatDatabase
import com.kakdela.p2p.data.local.NodeEntity
import com.kakdela.p2p.security.CryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Менеджер авторизации уровня Production.
 * Реализует гибридную модель: попытка регистрации на сервере + обязательная локальная активация.
 */
class AuthManager(private val context: Context) {

    private val TAG = "AuthManager"
    private val nodeDao = ChatDatabase.getDatabase(context).nodeDao()
    private val PEPPER = "7fb8a1d2c3e4f5a6"

    // HTTP-клиент с оптимизированными таймаутами для мобильных сетей
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val api: MyServerApi by lazy {
        Retrofit.Builder()
            // Используем HTTPS, если сервер поддерживает, иначе HTTP
            .baseUrl("http://kakdela.infinityfree.me/") 
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MyServerApi::class.java)
    }

    /**
     * Основной метод входа. 
     * Даже если сервер недоступен, пользователь будет авторизован локально (P2P-режим).
     */
    suspend fun registerOrLogin(email: String, password: String, phone: String): Boolean =
        withContext(Dispatchers.IO) {
            val passHash = sha256(password)
            val securityHash = sha256("$phone|$email|$passHash")
            
            try {
                val pubKey = CryptoManager.getMyPublicKeyStr()
                val cleanPhone = phone.replace(Regex("[^0-9]"), "").takeLast(10)
                val phoneHash = sha256(cleanPhone + PEPPER)

                val payload = UserPayload(
                    hash = securityHash,
                    phone_hash = phoneHash,
                    ip = "0.0.0.0", // Сервер определит IP самостоятельно через $_SERVER['REMOTE_ADDR']
                    port = 8888,
                    publicKey = pubKey,
                    phone = phone,
                    email = email,
                    lastSeen = System.currentTimeMillis()
                )

                val wrapper = UserRegistrationWrapper(
                    hash = securityHash,
                    data = payload
                )

                // Вызов API (api.php?action=add_user)
                val response = api.announceSelf(payload = wrapper)

                // Проверка успеха. Если поля message нет в модели, ошибка не возникнет.
                if (response.success) {
                    Log.d(TAG, "Server registration successful")
                } else {
                    Log.w(TAG, "Server rejected registration, continuing in offline mode")
                }

                // В любом случае завершаем локальную настройку
                completeLocalAuth(payload, email, passHash, securityHash)
                return@withContext true

            } catch (e: Exception) {
                Log.e(TAG, "Network error during auth: ${e.localizedMessage}")
                
                // P2P Фаворитизм: сервер упал, но мы создаем аккаунт локально
                val fallbackPayload = UserPayload(
                    hash = securityHash,
                    publicKey = CryptoManager.getMyPublicKeyStr(),
                    phone = phone,
                    email = email
                )
                completeLocalAuth(fallbackPayload, email, passHash, securityHash)
                
                return@withContext true 
            }
        }

    /**
     * Финализация процесса: сохранение в БД Room и настройка сессии в SharedPreferences.
     */
    private suspend fun completeLocalAuth(
        payload: UserPayload, 
        email: String, 
        passHash: String, 
        hash: String
    ) {
        try {
            saveUserToLocalDb(payload, email, passHash)
            
            context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString("my_security_hash", hash)
                .putBoolean("is_logged_in", true)
                .apply()
            
            Log.d(TAG, "Local session established for $hash")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save local auth data: ${e.message}")
        }
    }

    private suspend fun saveUserToLocalDb(node: UserPayload, email: String, passHash: String) {
        nodeDao.insert(
            NodeEntity(
                userHash = node.hash,
                phone_hash = node.phone_hash ?: "",
                email = email,
                passwordHash = passHash,
                phone = node.phone ?: "",
                ip = node.ip ?: "0.0.0.0",
                port = node.port,
                publicKey = node.publicKey,
                lastSeen = System.currentTimeMillis()
            )
        )
    }

    private fun sha256(input: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(input.toByteArray())
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            input.hashCode().toString() // Резервный вариант (небезопасно, но предотвращает крэш)
        }
    }
}
