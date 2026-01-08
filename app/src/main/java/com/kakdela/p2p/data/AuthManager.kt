package com.kakdela.p2p.data

import android.content.Context
import com.kakdela.p2p.api.MyServerApi
import com.kakdela.p2p.data.local.ChatDatabase
import com.kakdela.p2p.data.local.NodeEntity
import com.kakdela.p2p.security.CryptoUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

/**
 * Менеджер аутентификации пользователя.
 * Работает с локальной базой (Room) и сервером через PHP/MySQL.
 */
class AuthManager(context: Context) {

    private val nodeDao = ChatDatabase.getDatabase(context).nodeDao()

    // Retrofit API для сервера
    private val api: MyServerApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://yourdomain.infinityfreeapp.com/") // Замените на свой домен
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MyServerApi::class.java)
    }

    /**
     * Логин пользователя по email и паролю.
     * @return true, если логин успешен (локально или через сервер)
     */
    suspend fun login(email: String, password: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val inputPassHash = CryptoUtils.sha256(password)

            // 1️⃣ Локальная проверка через DHT-кэш
            val localUser: NodeEntity? = nodeDao.getUserByEmail(email)
            if (localUser != null && localUser.passwordHash == inputPassHash) {
                return@withContext true
            }

            // 2️⃣ Проверка через сервер
            val response = api.serverLogin(email, inputPassHash)
            if (response.success) {
                // Сохраняем пользователя в локальный кэш
                nodeDao.insert(response.userNode)
                return@withContext true
            }

            return@withContext false

        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }

    /**
     * Регистрация нового пользователя на сервере
     * и сохранение локально.
     */
    suspend fun register(email: String, password: String, phone: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val passHash = CryptoUtils.sha256(password)
            val response = api.serverRegister(email, passHash, phone)
            if (response.success) {
                nodeDao.insert(response.userNode)
                return@withContext true
            }
            return@withContext false
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }
}

/**
 * Retrofit API для сервера
 */
interface MyServerApi {

    @FormUrlEncoded
    @POST("auth/login.php")
    suspend fun serverLogin(
        @Field("email") email: String,
        @Field("passwordHash") passwordHash: String
    ): LoginResponse

    @FormUrlEncoded
    @POST("auth/register.php")
    suspend fun serverRegister(
        @Field("email") email: String,
        @Field("passwordHash") passwordHash: String,
        @Field("phone") phone: String
    ): LoginResponse
}

/**
 * Ответ от сервера при логине или регистрации
 */
data class LoginResponse(
    val success: Boolean,
    val userNode: NodeEntity
)
