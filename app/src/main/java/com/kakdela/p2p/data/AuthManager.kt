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

class AuthManager(private val context: Context) {

    private val TAG = "AuthManager"
    private val nodeDao = ChatDatabase.getDatabase(context).nodeDao()
    private val PEPPER = "7fb8a1d2c3e4f5a6"

    // Настройка клиента для обхода таймаутов и логирования
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true) // Важно для некоторых хостингов
        .build()

    private val api: MyServerApi by lazy {
        Retrofit.Builder()
            // ВАЖНО: InfinityFree часто блокирует API запросы. 
            // Если ошибка повторится, проверьте доступность api.php через браузер.
            .baseUrl("http://kakdela.infinityfree.me/") 
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MyServerApi::class.java)
    }

    suspend fun registerOrLogin(email: String, password: String, phone: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val passHash = sha256(password)
                val pubKey = CryptoManager.getMyPublicKeyStr()
                
                // 1. Генерируем Security Hash
                val securityHash = sha256("$phone|$email|$passHash")
                
                // 2. Генерируем Phone Discovery Hash
                val cleanPhone = phone.replace(Regex("[^0-9]"), "").takeLast(10)
                val phoneHash = sha256(cleanPhone + PEPPER)

                val payload = UserPayload(
                    hash = securityHash,
                    phone_hash = phoneHash,
                    ip = "0.0.0.0",
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

                // 3. Вызов API
                val response = api.announceSelf(payload = wrapper)

                // Если сервер прислал ответ, что всё ОК
                if (response.success) {
                    completeLocalAuth(payload, email, passHash, securityHash)
                    return@withContext true
                } else {
                    Log.e(TAG, "Server error: ${response.message}")
                    // P2P ФОРС-МАЖОР: Если сервер вернул ошибку, но данные валидны, 
                    // мы всё равно создаем локальный аккаунт (Offline-mode)
                    completeLocalAuth(payload, email, passHash, securityHash)
                    true 
                }
            } catch (e: Exception) {
                Log.e(TAG, "Network failure: ${e.message}")
                // ВАЖНО: Если сервер недоступен (ошибка сети), мы все равно пускаем 
                // пользователя локально, иначе в P2P приложении нет смысла.
                
                // Создаем фиктивный payload для локального входа
                val fallbackHash = sha256("$phone|$email|${sha256(password)}")
                val fallbackPayload = UserPayload(hash = fallbackHash, publicKey = CryptoManager.getMyPublicKeyStr())
                completeLocalAuth(fallbackPayload, email, sha256(password), fallbackHash)
                
                true // Возвращаем true для оффлайн входа
            }
        }

    private suspend fun completeLocalAuth(payload: UserPayload, email: String, passHash: String, hash: String) {
        saveUserToLocalDb(payload, email, passHash)
        context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("my_security_hash", hash)
            .putBoolean("is_logged_in", true)
            .apply()
        Log.d(TAG, "Local auth completed for: $hash")
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
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
