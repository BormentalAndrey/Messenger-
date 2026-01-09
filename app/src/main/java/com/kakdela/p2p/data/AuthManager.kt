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
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.security.MessageDigest

/**
 * Менеджер авторизации.
 * Управляет регистрацией в сети P2P и локальным кэшированием профиля.
 */
class AuthManager(private val context: Context) {

    private val TAG = "AuthManager"
    private val nodeDao = ChatDatabase.getDatabase(context).nodeDao()
    
    // Используем тот же PEPPER, что и в IdentityRepository для консистентности хэшей
    private val PEPPER = "7fb8a1d2c3e4f5a6"

    private val api: MyServerApi by lazy {
        Retrofit.Builder()
            .baseUrl("http://kakdela.infinityfree.me/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MyServerApi::class.java)
    }

    /**
     * 🔐 Универсальный метод входа/регистрации (регистрация личности в P2P сети).
     * В P2P авторизация — это подтверждение владения ключами и хэшем.
     */
    suspend fun registerOrLogin(email: String, password: String, phone: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val passHash = sha256(password)
                val pubKey = CryptoManager.getMyPublicKeyStr()
                
                // 1. Генерируем Security Hash (ID пользователя)
                val securityHash = sha256("$phone|$email|$passHash")
                
                // 2. Генерируем Phone Discovery Hash (для поиска контактами)
                val cleanPhone = phone.replace(Regex("[^0-9]"), "").takeLast(10)
                val phoneHash = sha256(cleanPhone + PEPPER)

                // 3. Подготовка данных для сервера (соответствует ТЗ и api.php)
                val payload = UserPayload(
                    hash = securityHash,
                    phone_hash = phoneHash,
                    ip = "0.0.0.0", // Сервер сам определит IP отправителя
                    port = 8888,
                    publicKey = pubKey,
                    phone = phone,
                    email = email,
                    lastSeen = System.currentTimeMillis()
                )

                val wrapper = UserRegistrationWrapper(
                    securityHash = securityHash,
                    userPayload = payload
                )

                // 4. Отправка на сервер через api.php (action=add_user)
                val response = api.announceSelf(payload = wrapper)

                if (response.success) {
                    saveUserToLocalDb(payload, email, passHash)
                    // Сохраняем состояние авторизации в SharedPreferences
                    context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
                        .edit()
                        .putString("my_security_hash", securityHash)
                        .putBoolean("is_logged_in", true)
                        .apply()
                    
                    Log.d(TAG, "Auth success for: $securityHash")
                    return@withContext true
                }

                false
            } catch (e: Exception) {
                Log.e(TAG, "Auth failed: ${e.message}")
                false
            }
        }

    /**
     * 💾 Кэширование профиля в локальную БД Room.
     */
    private suspend fun saveUserToLocalDb(
        node: UserPayload,
        email: String,
        passHash: String
    ) {
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
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
