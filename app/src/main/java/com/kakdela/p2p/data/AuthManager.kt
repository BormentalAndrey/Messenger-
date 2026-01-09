package com.kakdela.p2p.data

import android.content.Context
import android.util.Log
import com.kakdela.p2p.api.MyServerApi
import com.kakdela.p2p.api.UserPayload
import com.kakdela.p2p.data.local.ChatDatabase
import com.kakdela.p2p.data.local.NodeEntity
import com.kakdela.p2p.security.CryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.security.MessageDigest

/**
 * Менеджер авторизации.
 * Управляет сессиями, регистрацией и локальным кэшированием профиля.
 */
class AuthManager(private val context: Context) {

    private val TAG = "AuthManager"
    private val nodeDao = ChatDatabase.getDatabase(context).nodeDao()

    private val api: MyServerApi by lazy {
        Retrofit.Builder()
            .baseUrl("http://kakdela.infinityfree.me/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MyServerApi::class.java)
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    /**
     * 🔐 Вход пользователя.
     * Исправлено: заменено response.userNode на response.users?.firstOrNull()
     */
    suspend fun login(email: String, password: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val passHash = sha256(password)

                // 1. Локальная проверка для оффлайн доступа
                val localUser = nodeDao.getUserByEmail(email)
                if (localUser != null && localUser.passwordHash == passHash) {
                    Log.d(TAG, "Local login success")
                    return@withContext true
                }

                // 2. Онлайн вход через сервер
                val credentials = mapOf(
                    "email" to email,
                    "passwordHash" to passHash
                )

                val response = api.serverLogin(credentials = credentials)

                // В продакшн API данные приходят в списке users
                val userNode = response.users?.firstOrNull()

                if (response.success && userNode != null) {
                    saveUserToLocalDb(userNode, email, passHash)
                    return@withContext true
                }

                false
            } catch (e: Exception) {
                Log.e(TAG, "Login failed: ${e.message}")
                false
            }
        }

    /**
     * 🆕 Регистрация пользователя.
     * Исправлено: удален лишний параметр passwordHash из UserPayload
     */
    suspend fun register(
        email: String,
        password: String,
        phone: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val passHash = sha256(password)
            val pubKey = CryptoManager.getMyPublicKeyStr()
            
            // Генерируем уникальный hash пользователя (номер+почта+пароль)
            val myId = sha256("$phone:$email:$passHash")

            val payload = UserPayload(
                hash = myId,
                ip = "0.0.0.0",
                port = 8888,
                publicKey = pubKey,
                phone = phone,
                email = email
            )

            val response = api.serverRegister(payload = payload)
            
            // Проверяем успешность и наличие данных пользователя в ответе
            val registeredNode = response.users?.firstOrNull()

            if (response.success && registeredNode != null) {
                saveUserToLocalDb(registeredNode, email, passHash)
                return@withContext true
            }

            false
        } catch (e: Exception) {
            Log.e(TAG, "Registration failed: ${e.message}")
            false
        }
    }

    /**
     * 💾 Кэширование профиля в Room.
     */
    private suspend fun saveUserToLocalDb(
        node: UserPayload,
        email: String,
        passHash: String
    ) {
        nodeDao.insert(
            NodeEntity(
                userHash = node.hash,
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
}
