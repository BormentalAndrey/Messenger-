package com.kakdela.p2p.api

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// ================= MODELS =================

data class UserPayload(
    val hash: String,              // Security ID (SHA-256)
    val phone_hash: String? = null,// Хэш номера для поиска
    val ip: String? = null,        // IP для P2P
    val port: Int = 8888,          // UDP порт
    val publicKey: String,         // Публичный ключ для шифрования
    val phone: String? = null,     // Номер телефона
    val email: String? = null,     // Email
    val lastSeen: Long? = null     // Timestamp последнего онлайна
)

data class ServerResponse(
    val success: Boolean = false,
    val users: List<UserPayload>? = null,
    val status: String? = null,
    val error: String? = null,
    val db_size_mb: Double? = null
)

data class UserRegistrationWrapper(
    val hash: String,      // user_hash
    val data: UserPayload  // encrypted_data + phone_hash
)

// ================= API =================

interface MyServerApi {

    @POST("api.php")
    suspend fun announceSelf(
        @Query("action") action: String = "add_user",
        @Body payload: UserRegistrationWrapper
    ): ServerResponse

    @GET("api.php")
    suspend fun getAllNodes(
        @Query("action") action: String = "list_users"
    ): ServerResponse

    @GET("api.php")
    suspend fun checkServerStatus(): ServerResponse
}

// ================= FACTORY =================

object MyServerApiFactory {
    private const val BASE_URL = "http://kakdela.infinityfree.me/" // твой сервер

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("Accept", "application/json")
                .build()
            chain.proceed(request)
        }
        .build()

    val instance: MyServerApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MyServerApi::class.java)
    }
}
