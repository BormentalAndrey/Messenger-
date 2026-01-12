package com.kakdela.p2p.api

import android.os.Environment
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
import java.io.File
import java.util.Date
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
    private const val TAG = "P2P_NETWORK_DEBUG"
    private const val LOG_FILE_NAME = "p2p_log.txt"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(45, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(45, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain: Interceptor.Chain ->
                val originalRequest = chain.request()
                
                // 1. Подготовка заголовков (Browser Emulator)
                val requestBuilder = originalRequest.newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Mobile Safari/537.36")
                    .header("Accept", "application/json, text/plain, */*")

                CookieStore.testCookie?.let {
                    requestBuilder.header("Cookie", it)
                }

                val request = requestBuilder.build()
                
                // 2. Сбор данных для логирования
                val logEntry = StringBuilder()
                logEntry.append("\n[${Date()}] ${request.method} ${request.url}\n")
                
                if (request.method == "POST") {
                    val buffer = Buffer()
                    request.body?.writeTo(buffer)
                    logEntry.append("PAYLOAD: ${buffer.readUtf8()}\n")
                }

                // 3. Запись в системный лог (Logcat)
                Log.e(TAG, logEntry.toString())

                // 4. Запись в файл (для Termux: /sdcard/Documents/p2p_log.txt)
                writeLogToFile(logEntry.toString())

                val response: Response = chain.proceed(request)

                // 5. Логирование ответа
                val responseLog = "<<< [RESPONSE]: ${response.code}\n"
                Log.e(TAG, responseLog)
                writeLogToFile(responseLog)

                // 6. Проверка на Anti-Bot
                val contentType = response.body?.contentType()?.toString()
                if (contentType?.contains("text/html", ignoreCase = true) == true) {
                    Log.e(TAG, "!!! ANTI-BOT DETECTED !!!")
                    writeLogToFile("!!! ANTI-BOT DETECTED (HTML received instead of JSON) !!!\n")
                    NetworkEvents.triggerAuth()
                }

                response
            }
            .build()
    }

    /**
     * Записывает строку лога в публичную папку Documents.
     */
    private fun writeLogToFile(text: String) {
        try {
            val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            if (!publicDir.exists()) publicDir.mkdirs()
            val file = File(publicDir, LOG_FILE_NAME)
            file.appendText(text)
        } catch (e: Exception) {
            // Если нет прав на запись, хотя бы выведем ошибку в консоль
            Log.e(TAG, "File Log Error: ${e.message}")
        }
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
