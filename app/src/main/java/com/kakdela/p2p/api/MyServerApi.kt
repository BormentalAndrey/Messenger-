package com.kakdela.p2p.api

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// ================= MODELS =================

/**
 * Модель данных пользователя для обмена с сервером.
 * Имена полей в @SerializedName СТРОГО соответствуют колонкам в MySQL (см. скриншот).
 */
data class UserPayload(
    @SerializedName("hash") val hash: String,              // Security ID
    @SerializedName("phone_hash") val phone_hash: String?, // Discovery ID
    @SerializedName("ip") val ip: String?,
    @SerializedName("port") val port: Int = 8888,
    @SerializedName("publicKey") val publicKey: String,
    @SerializedName("phone") val phone: String?,
    @SerializedName("email") val email: String?,
    @SerializedName("lastSeen") val lastSeen: Long?
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

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "KakDela-P2P/1.0 (Android)")
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
