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

                // 1. Заголовки (эмуляция браузера)
                val requestBuilder = originalRequest.newBuilder()
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Mobile Safari/537.36"
                    )
                    .header("Accept", "application/json, text/plain, */*")

                CookieStore.testCookie?.let {
                    requestBuilder.header("Cookie", it)
                }

                val request = requestBuilder.build()

                // 2. Лог запроса
                val logEntry = StringBuilder()
                logEntry.append("\n[${Date()}] ${request.method} ${request.url}\n")

                if (request.method == "POST") {
                    val buffer = Buffer()
                    request.body?.writeTo(buffer)
                    logEntry.append("PAYLOAD: ${buffer.readUtf8()}\n")
                }

                Log.e(TAG, logEntry.toString())
                writeLogToFile(logEntry.toString())

                val response: Response = chain.proceed(request)

                // 5. Лог ответа
                val responseLog = "<<< [RESPONSE]: ${response.code}\n"
                Log.e(TAG, responseLog)
                writeLogToFile(responseLog)

                // 6. Проверка на Anti-Bot и ошибки сервера (РАСШИРЕННЫЙ ВАРИАНТ)
                val responseBody = response.peekBody(Long.MAX_VALUE)
                val content = responseBody.string()
                val contentType = response.body?.contentType()?.toString()

                if (
                    contentType?.contains("text/html", ignoreCase = true) == true ||
                    content.contains("<html>", ignoreCase = true)
                ) {
                    val msg =
                        "!!! ANTI-BOT BLOCK DETECTED !!! Сервер вернул HTML. Запускаю WebView..."
                    Log.e(TAG, msg)
                    writeLogToFile(
                        "\n$msg\nPREVIEW:\n${content.take(1000)}\n--- END HTML PREVIEW ---\n"
                    )

                    // Сигнализируем UI
                    NetworkEvents.triggerAuth()

                    // Возвращаем безопасный JSON, чтобы Retrofit не упал
                    return@addInterceptor response.newBuilder()
                        .code(503)
                        .message("Anti-Bot Challenge")
                        .body(
                            okhttp3.ResponseBody.create(
                                okhttp3.MediaType.parse("application/json"),
                                """{ "success": false, "error": "anti_bot_wait" }"""
                            )
                        )
                        .build()
                }

                response
            }
            .build()
    }

    /**
     * Запись лога в /sdcard/Documents/p2p_log.txt
     */
    private fun writeLogToFile(text: String) {
        try {
            val publicDir =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            if (!publicDir.exists()) publicDir.mkdirs()
            val file = File(publicDir, LOG_FILE_NAME)
            file.appendText(text)
        } catch (e: Exception) {
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
