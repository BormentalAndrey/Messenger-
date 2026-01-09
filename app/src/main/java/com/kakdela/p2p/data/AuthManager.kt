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
 * Управляет сессиями, регистрацией в DHT и локальным кэшированием профиля.
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
     * Сначала проверяет локальную БД (оффлайн), затем обращается к серверу.
     */
    suspend fun login(email: String, password: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val passHash = sha256(password)

                // 1. Локальный вход
                val localUser = nodeDao.getUserByEmail(email)
                if (localUser != null && localUser.passwordHash == passHash) {
                    Log.d(TAG, "Local login success")
                    return@withContext true
                }

                // 2. Онлайн вход
                // Исправлено: передаем Map, так как MyServerApi ожидает именованный параметр credentials
                val credentials = mapOf(
                    "email" to email,
                    "passwordHash" to passHash,
                    "action" to "login"
                )

                val response = api.serverLogin(credentials = credentials)

                if (response.success && response.userNode != null) {
                    saveUserToLocalDb(response.userNode, email, passHash)
                    return@withContext true
                }

                false
            } catch (e: Exception) {
                Log.e(TAG, "Login failed: ${e.message}")
                false
            }
        }

    /**
     * 🆕 Регистрация пользователя в P2P сети.
     */
    suspend fun register(
        email: String,
        password: String,
        phone: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val passHash = sha256(password)
            val pubKey = CryptoManager.getMyPublicKeyStr()
            
            // Генерируем ID на основе публичного ключа
            val myId = sha256(pubKey)

            val payload = UserPayload(
                hash = myId,
                ip = "0.0.0.0", // Будет обновлено сервером при получении запроса
                port = 8888,
                publicKey = pubKey,
                phone = phone,
                email = email,
                passwordHash = passHash
            )

            // Исправлено: передаем объект UserPayload напрямую в параметр payload
            val response = api.serverRegister(payload = payload)

            if (response.success && response.userNode != null) {
                saveUserToLocalDb(response.userNode, email, passHash)
                return@withContext true
            }

            false
        } catch (e: Exception) {
            Log.e(TAG, "Registration failed: ${e.message}")
            false
        }
    }

    /**
     * 💾 Сохранение профиля во внутреннюю БД для работы в оффлайне.
     */
    private suspend fun saveUserToLocalDb(
        node: UserPayload,
        email: String,
        passHash: String
    ) {
        nodeDao.insert(
            NodeEntity(
                userHash = node.hash ?: sha256(node.publicKey ?: ""),
                email = email,
                passwordHash = passHash,
                phone = node.phone ?: "",
                ip = node.ip ?: "0.0.0.0",
                port = node.port ?: 8888,
                publicKey = node.publicKey ?: "",
                lastSeen = System.currentTimeMillis()
            )
        )
    }
}
