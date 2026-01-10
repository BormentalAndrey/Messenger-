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
 * Менеджер авторизации.
 * Отвечает за регистрацию узла в MySQL Discovery и локальное сохранение сессии.
 */
class AuthManager(private val context: Context) {

    private val TAG = "AuthManager"
    private val nodeDao = ChatDatabase.getDatabase(context).nodeDao()
    private val PEPPER = "7fb8a1d2c3e4f5a6"

    // Настройка клиента для обхода ограничений бесплатного хостинга
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", "application/json")
                .build()
            chain.proceed(request)
        }
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val api: MyServerApi by lazy {
        Retrofit.Builder()
            .baseUrl("http://kakdela.infinityfree.me/") 
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MyServerApi::class.java)
    }

    /**
     * Регистрирует пользователя. 
     * Даже если сервер недоступен, создает локальный профиль для оффлайн-работы.
     */
    suspend fun registerOrLogin(email: String, password: String, phone: String): Boolean =
        withContext(Dispatchers.IO) {
            val finalPhone = normalizePhone(phone)
            val passHash = sha256(password)
            val securityHash = sha256("$finalPhone|$email|$passHash")
            
            try {
                val pubKey = CryptoManager.getMyPublicKeyStr()
                val phoneHash = sha256(finalPhone + PEPPER)

                val payload = UserPayload(
                    hash = securityHash,
                    phone_hash = phoneHash,
                    ip = "0.0.0.0", 
                    port = 8888,
                    publicKey = pubKey,
                    phone = finalPhone,
                    email = email,
                    lastSeen = System.currentTimeMillis()
                )

                val wrapper = UserRegistrationWrapper(
                    hash = securityHash,
                    data = payload
                )

                Log.d(TAG, "Попытка регистрации: $finalPhone")
                
                // Выполняем сетевой запрос
                val response = api.announceSelf(payload = wrapper)

                // ИСПРАВЛЕНО: Убрано обращение к response.message, так как поле может отсутствовать
                if (response.success) {
                    Log.d(TAG, "Сервер подтвердил регистрацию")
                } else {
                    Log.w(TAG, "Сервер вернул отрицательный статус: $response")
                }

                completeLocalAuth(payload, email, passHash, securityHash)
                return@withContext true

            } catch (e: Exception) {
                Log.e(TAG, "Ошибка при регистрации (Offline mode): ${e.message}")
                
                val fallbackPayload = UserPayload(
                    hash = securityHash,
                    phone_hash = sha256(finalPhone + PEPPER),
                    publicKey = CryptoManager.getMyPublicKeyStr(),
                    phone = finalPhone,
                    email = email
                )
                completeLocalAuth(fallbackPayload, email, passHash, securityHash)
                return@withContext true 
            }
        }

    private suspend fun completeLocalAuth(payload: UserPayload, email: String, passHash: String, hash: String) {
        try {
            saveUserToLocalDb(payload, email, passHash)
            
            // Сохраняем флаг входа для NavGraph
            context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE).edit().apply {
                putString("my_security_hash", hash)
                putString("my_phone", payload.phone)
                putBoolean("is_logged_in", true)
                apply()
            }
            Log.d(TAG, "Локальная сессия создана")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка локального сохранения: ${e.message}")
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

    private fun normalizePhone(raw: String): String {
        var digits = raw.replace(Regex("[^0-9]"), "")
        return when {
            digits.length == 10 && digits.startsWith("9") -> "7$digits"
            digits.length == 11 && digits.startsWith("8") -> "7" + digits.substring(1)
            else -> digits
        }
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }
}
