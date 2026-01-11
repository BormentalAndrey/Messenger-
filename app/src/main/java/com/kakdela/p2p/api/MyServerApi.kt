package com.kakdela.p2p.api

import android.util.Log
import com.google.gson.annotations.SerializedName
import com.kakdela.p2p.network.CookieStore
import com.kakdela.p2p.network.NetworkEvents
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// ================= MODELS =================

data class UserPayload(
    @SerializedName("hash") val hash: String,
    @SerializedName("phone_hash") val phone_hash: String?,
    @SerializedName("ip") val ip: String? = null,
    @SerializedName("port") val port: Int = 8888,
    @SerializedName("publicKey") val publicKey: String,
    @SerializedName("phone") val phone: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("lastSeen") val lastSeen: Long? = System.currentTimeMillis()
)

data class ServerResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("users") val users: List<UserPayload>? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("error") val error: String? = null
)

data class UserRegistrationWrapper(
    @SerializedName("hash") val hash: String,
    @SerializedName("data") val data: UserPayload
)

// ================= API INTERFACE =================

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
}

// ================= FACTORY =================

object MyServerApiFactory {
    private const val BASE_URL = "http://kakdela.infinityfree.me/"
    private const val TAG = "MyServerApi"

    // Создаем логгер как отдельную переменную для чистоты кода
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY // BODY для отладки, HEADERS для продакшна
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor(loggingInterceptor)
        .addInterceptor { chain: Interceptor.Chain ->
            val originalRequest = chain.request()
            val requestBuilder = originalRequest.newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36")
                .header("Accept", "application/json")

            // Подставляем куку __test из хранилища
            CookieStore.testCookie?.let {
                requestBuilder.header("Cookie", it)
            }

            val request = requestBuilder.build()
            val response: Response = chain.proceed(request)

            // ПРОВЕРКА НА АНТИБОТ (InfinityFree AES Challenge)
            // Если сервер вернул HTML вместо JSON — значит кука протухла или неверна
            val contentType = response.body?.contentType()?.toString()
            if (contentType != null && contentType.contains("text/html", ignoreCase = true)) {
                Log.w(TAG, "Обнаружена страница-заглушка! Требуется авторизация через WebView.")
                
                // Генерируем событие для UI, чтобы открылось WebView
                NetworkEvents.triggerAuth()
            }

            response
        }
        .build()

    /**
     * Экземпляр API. 
     * Используем конвертер Gson для автоматического парсинга JSON в объекты.
     */
    val instance: MyServerApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MyServerApi::class.java)
    }
}
