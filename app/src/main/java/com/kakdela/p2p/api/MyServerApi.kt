package com.kakdela.p2p.api

import android.util.Log
import com.google.gson.annotations.SerializedName
import com.kakdela.p2p.network.CookieStore
import com.kakdela.p2p.network.NetworkEvents
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okio.Buffer
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// ================= MODELS (Дата-классы для JSON) =================

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
    /**
     * Регистрация/обновление узла в сети
     */
    @POST("api.php")
    suspend fun announceSelf(
        @Query("action") action: String = "add_user",
        @Body payload: UserRegistrationWrapper
    ): ServerResponse

    /**
     * Получение списка всех активных пиров
     */
    @GET("api.php")
    suspend fun getAllNodes(
        @Query("action") action: String = "list_users"
    ): ServerResponse
}

// ================= FACTORY (OkHttp + Retrofit) =================

object MyServerApiFactory {
    private const val BASE_URL = "http://kakdela.infinityfree.me/"
    // Используем уровень ERROR для критических логов, чтобы они были видны в Termux без фильтров
    private const val TAG = "P2P_NETWORK_DEBUG"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(45, TimeUnit.SECONDS) // Увеличил таймаут для медленного хостинга
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(45, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain: Interceptor.Chain ->
                val originalRequest = chain.request()
                
                // Сборка запроса с имитацией реального браузера (защита от ботов)
                val requestBuilder = originalRequest.newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Mobile Safari/537.36")
                    .header("Accept", "application/json, text/plain, */*")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Cache-Control", "no-cache")

                // Применяем куку обхода AES-защиты InfinityFree
                CookieStore.testCookie?.let {
                    requestBuilder.header("Cookie", it)
                    Log.e(TAG, ">>> [COOKIE]: $it")
                }

                val request = requestBuilder.build()

                // ЛОГИРОВАНИЕ ПЕРЕД ОТПРАВКОЙ (Твой внутренний сниффер)
                Log.e(TAG, ">>> [REQUEST]: ${request.method} ${request.url}")
                
                if (request.method == "POST") {
                    try {
                        val buffer = Buffer()
                        request.body?.writeTo(buffer)
                        Log.e(TAG, ">>> [PAYLOAD]: ${buffer.readUtf8()}")
                    } catch (e: Exception) {
                        Log.e(TAG, ">>> [ERROR] Не удалось прочитать Body: ${e.message}")
                    }
                }

                val response: Response = chain.proceed(request)

                // АНАЛИЗ ОТВЕТА СЕРВЕРА
                Log.e(TAG, "<<< [RESPONSE]: Code ${response.code}")
                
                val contentType = response.body?.contentType()?.toString()

                /* КРИТИЧЕСКИЙ МОМЕНТ: 
                   Если сервер прислал HTML вместо JSON — значит нас заблокировал антибот.
                */
                if (contentType?.contains("text/html", ignoreCase = true) == true) {
                    Log.e(TAG, "!!! [ALERT]: Обнаружена HTML-заглушка! Кука устарела.")
                    NetworkEvents.triggerAuth() // Вызов WebView для получения новой куки
                }

                response
            }
            .build()
    }

    /**
     * Основная точка доступа к API
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
