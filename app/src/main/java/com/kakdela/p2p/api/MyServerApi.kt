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

// ================= MODELS (Модели данных) =================

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

// ================= API INTERFACE (Интерфейс запросов) =================

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

// ================= FACTORY (Сборка клиента) =================

object MyServerApiFactory {
    private const val BASE_URL = "http://kakdela.infinityfree.me/"
    private const val TAG = "P2P_NETWORK_DEBUG"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain: Interceptor.Chain ->
                val originalRequest = chain.request()
                
                // Формируем новый запрос с нужными заголовками
                val requestBuilder = originalRequest.newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36")
                    .header("Accept", "application/json")
                    .header("Connection", "keep-alive")

                // Подставляем куку обхода антибота из CookieStore
                CookieStore.testCookie?.let {
                    requestBuilder.header("Cookie", it)
                    Log.i(TAG, ">>> Используется кука: $it")
                }

                val request = requestBuilder.build()

                // ЛОГИРОВАНИЕ (Тот самый "Сниффер" в консоль)
                Log.i(TAG, ">>> ОТПРАВКА: ${request.method} ${request.url}")
                if (request.method == "POST") {
                    val buffer = Buffer()
                    request.body?.writeTo(buffer)
                    Log.d(TAG, ">>> ТЕЛО (JSON): ${buffer.readUtf8()}")
                }

                val response: Response = chain.proceed(request)

                // АНАЛИЗ ОТВЕТА
                Log.i(TAG, "<<< ОТВЕТ: ${response.code}")
                val contentType = response.body?.contentType()?.toString()

                // Если вместо JSON прилетел HTML — значит кука __test просрочена или неверна
                if (contentType?.contains("text/html", ignoreCase = true) == true) {
                    Log.e(TAG, "!!! ОБНАРУЖЕН АНТИБОТ (Блокировка AES) !!!")
                    Log.e(TAG, "!!! Перезапускаю авторизацию через WebView...")
                    NetworkEvents.triggerAuth()
                }

                response
            }
            .build()
    }

    val instance: MyServerApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MyServerApi::class.java)
    }
}
