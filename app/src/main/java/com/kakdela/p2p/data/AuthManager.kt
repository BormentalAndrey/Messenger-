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

/**
 * AuthManager — ЕДИНСТВЕННАЯ точка:
 * - регистрации
 * - логина
 * - server announce
 * - создания локальной сессии
 *
 * Offline-first: сервер может быть недоступен
 */
class AuthManager(private val context: Context) {

    private val TAG = "AuthManager"
    private val db = ChatDatabase.getDatabase(context)
    private val nodeDao = db.nodeDao()

    /** Pepper для phone discovery (НЕ хранится на сервере) */
    private val PEPPER = "7fb8a1d2c3e4f5a6"

    /** OkHttp с защитой от free-hosting */
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Android 15; P2P-Messenger)"
                )
                .header("Accept", "application/json")
                .build()
            chain.proceed(req)
        }
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val api: MyServerApi by lazy {
        Retrofit.Builder()
            .baseUrl("http://kakdela.infinityfree.me/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MyServerApi::class.java)
    }

    /**
     * Регистрация / логин.
     * Всегда возвращает true, если локальная сессия создана.
     */
    suspend fun registerOrLogin(
        email: String,
        password: String,
        phone: String
    ): Boolean = withContext(Dispatchers.IO) {

        val normalizedPhone = normalizePhone(phone)
        val passwordHash = sha256(password)

        /** ГЛАВНЫЙ ID пользователя */
        val securityHash = sha256(
            "$normalizedPhone|$email|$passwordHash"
        )

        val phoneHash = sha256(normalizedPhone + PEPPER)
        val publicKey = CryptoManager.getMyPublicKeyStr()

        val payload = UserPayload(
            hash = securityHash,
            phone_hash = phoneHash,
            ip = "0.0.0.0", // сервер определит сам
            port = 8888,
            publicKey = publicKey,
            phone = normalizedPhone,
            email = email,
            lastSeen = System.currentTimeMillis()
        )

        val wrapper = UserRegistrationWrapper(
            hash = securityHash,
            data = payload
        )

        try {
            Log.d(TAG, "AnnounceSelf → server")
            val response = api.announceSelf(wrapper)

            if (response.success) {
                Log.d(TAG, "Server announce OK")
            } else {
                Log.w(TAG, "Server responded negative: $response")
            }

        } catch (e: Exception) {
            Log.w(
                TAG,
                "Server unreachable, offline mode: ${e.message}"
            )
        }

        /** В ЛЮБОМ СЛУЧАЕ создаём локальную сессию */
        createLocalSession(
            payload = payload,
            email = email,
            passwordHash = passwordHash,
            securityHash = securityHash
        )

        return@withContext true
    }

    /**
     * Локальная сессия — источник истины
     */
    private suspend fun createLocalSession(
        payload: UserPayload,
        email: String,
        passwordHash: String,
        securityHash: String
    ) {
        try {
            nodeDao.insert(
                NodeEntity(
                    userHash = payload.hash,
                    phone_hash = payload.phone_hash ?: "",
                    email = email,
                    passwordHash = passwordHash,
                    phone = payload.phone ?: "",
                    ip = payload.ip ?: "0.0.0.0",
                    port = payload.port,
                    publicKey = payload.publicKey,
                    lastSeen = System.currentTimeMillis()
                )
            )

            context.getSharedPreferences(
                "auth_prefs",
                Context.MODE_PRIVATE
            ).edit()
                .putBoolean("is_logged_in", true)
                .putString("my_security_hash", securityHash)
                .putString("my_phone", payload.phone)
                .apply()

            Log.d(TAG, "Local session created")

        } catch (e: Exception) {
            Log.e(TAG, "Local auth failed", e)
        }
    }

    /**
     * Нормализация телефона
     */
    private fun normalizePhone(raw: String): String {
        val digits = raw.replace(Regex("[^0-9]"), "")
        return when {
            digits.length == 10 && digits.startsWith("9") -> "7$digits"
            digits.length == 11 && digits.startsWith("8") ->
                "7${digits.substring(1)}"
            else -> digits
        }
    }

    /**
     * SHA-256 hex
     */
    private fun sha256(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
