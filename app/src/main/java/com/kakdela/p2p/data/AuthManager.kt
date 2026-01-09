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
    
    // Секретный "перец" для анонимизации номеров телефонов (согласно ТЗ)
    private val PEPPER = "7fb8a1d2c3e4f5a6"

    private val api: MyServerApi by lazy {
        Retrofit.Builder()
            .baseUrl("http://kakdela.infinityfree.me/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MyServerApi::class.java)
    }

    /**
     * Универсальный метод входа/регистрации.
     * Реализует логику создания P2P-личности и её анонса на Discovery-сервер.
     */
    suspend fun registerOrLogin(email: String, password: String, phone: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val passHash = sha256(password)
                val pubKey = CryptoManager.getMyPublicKeyStr()
                
                // 1. Генерируем Security Hash (основной ID пользователя в системе)
                val securityHash = sha256("$phone|$email|$passHash")
                
                // 2. Генерируем Phone Discovery Hash (для сопоставления контактов)
                val cleanPhone = phone.replace(Regex("[^0-9]"), "").takeLast(10)
                val phoneHash = sha256(cleanPhone + PEPPER)

                // 3. Подготовка полезной нагрузки (UserPayload)
                val payload = UserPayload(
                    hash = securityHash,
                    phone_hash = phoneHash,
                    ip = "0.0.0.0", // Сервер определит реальный IP при получении запроса
                    port = 8888,
                    publicKey = pubKey,
                    phone = phone,
                    email = email,
                    lastSeen = System.currentTimeMillis()
                )

                // 4. Упаковка в Wrapper (Имена параметров исправлены согласно ошибкам компилятора)
                val wrapper = UserRegistrationWrapper(
                    hash = securityHash, // Исправлено с securityHash на hash
                    data = payload       // Исправлено с userPayload на data
                )

                // 5. Вызов API (action=add_user в api.php)
                val response = api.announceSelf(payload = wrapper)

                if (response.success) {
                    // Кэшируем данные о себе в локальную БД для работы в оффлайне
                    saveUserToLocalDb(payload, email, passHash)
                    
                    // Сохраняем сессию локально
                    context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
                        .edit()
                        .putString("my_security_hash", securityHash)
                        .putBoolean("is_logged_in", true)
                        .apply()
                    
                    Log.d(TAG, "Successfully authenticated: $securityHash")
                    return@withContext true
                } else {
                    Log.e(TAG, "Server returned failure for registration")
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Auth exception: ${e.message}")
                false
            }
        }

    /**
     * Сохранение профиля текущего пользователя в Room.
     */
    private suspend fun saveUserToLocalDb(
        node: UserPayload,
        email: String,
        passHash: String
    ) {
        nodeDao.insert(
            NodeEntity(
                userHash = node.hash,
                phone_hash = node.phone_hash ?: "", // Имя параметра соответствует NodeEntity
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
