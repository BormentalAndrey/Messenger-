package com.kakdela.p2p.api

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

// ================= MODELS =================

/**
 * Основная модель данных пользователя.
 * Полностью синхронизирована с колонками в MySQL и логикой API.php.
 */
data class UserPayload(
    val hash: String,              // Security ID (хэш ключа)
    val phone_hash: String? = null,// Discovery ID (хэш номера для поиска)
    val ip: String? = null,        // IP-адрес для P2P соединения
    val port: Int = 8888,          // UDP-порт
    val publicKey: String,         // Ключ для шифрования сообщений
    val phone: String? = null,     // Номер для отображения (внутри encrypted_data)
    val email: String? = null,     // Email пользователя
    val lastSeen: Long? = null     // Timestamp последнего онлайна
)

/**
 * Универсальный ответ сервера.
 */
data class ServerResponse(
    val success: Boolean = false,
    val users: List<UserPayload>? = null, // Список пиров для Discovery
    val status: String? = null,           // "Online"
    val error: String? = null,            // Ошибка, если есть
    val db_size_mb: Double? = null        // Размер БД на хостинге
)

// ================= API =================

interface MyServerApi {

    /**
     * Регистрация или обновление данных узла.
     * Отправляет UserPayload, включая phone_hash для колонки Discovery.
     */
    @POST("API.php")
    suspend fun announceSelf(
        @Query("action") action: String = "add_user",
        @Body payload: UserRegistrationWrapper
    ): ServerResponse

    /**
     * Получение всех активных пользователей для синхронизации контактов.
     * Сервер вернет список, где у каждого юзера есть phone_hash.
     */
    @GET("API.php")
    suspend fun getAllNodes(
        @Query("action") action: String = "list_users"
    ): ServerResponse

    /**
     * Пинг сервера для проверки связи и получения размера БД.
     */
    @GET("api.php")
    suspend fun checkServerStatus(): ServerResponse
}

/**
 * Обертка для запроса 'add_user', соответствующая PHP структуре:
 * $input['hash'] и $input['data']
 */
data class UserRegistrationWrapper(
    val hash: String,      // Пойдет в колонку user_hash
    val data: UserPayload  // Пойдет в колонку encrypted_data и phone_hash
)
