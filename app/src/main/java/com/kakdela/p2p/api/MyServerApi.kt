package com.kakdela.p2p.api

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

// ================= MODELS =================

/**
 * Основная модель данных пользователя.
 * Соответствует структуре, которую PHP упаковывает в 'encrypted_data'.
 */
data class UserPayload(
    val hash: String,          // user_hash в базе данных
    val ip: String? = null,    // Определяется сервером автоматически через REMOTE_ADDR
    val port: Int = 8888,      // Порт для UDP прослушивания
    val publicKey: String,     // Публичный ключ для верификации подписей
    val phone: String? = null,
    val email: String? = null,
    val lastSeen: Long? = null
)

/**
 * Универсальный ответ сервера.
 * Поля 'users' и 'success' соответствуют вашим PHP case 'list_users' и 'add_user'.
 */
data class ServerResponse(
    val success: Boolean = false,
    val users: List<UserPayload>? = null, // Для действия 'list_users'
    val status: String? = null,           // Для default action
    val error: String? = null,            // Сообщения об ошибках
    val db_size_mb: Double? = null        // Статистика базы данных
)

// ================= API =================



interface MyServerApi {

    /**
     * Регистрация или обновление статуса "в сети".
     * Соответствует PHP: case 'add_user'
     */
    @POST("index.php")
    suspend fun announceSelf(
        @Query("action") action: String = "add_user",
        @Body payload: UserPayload
    ): ServerResponse

    /**
     * Получение списка всех активных узлов (до 2500 записей).
     * Соответствует PHP: case 'list_users'
     */
    @GET("index.php")
    suspend fun getAllNodes(
        @Query("action") action: String = "list_users"
    ): ServerResponse

    /**
     * Поиск конкретного пира. 
     * Так как в вашем PHP нет отдельного 'get_peer', мы используем 'list_users' 
     * и фильтруем результат на стороне клиента в IdentityRepository.
     */
    @GET("index.php")
    suspend fun findPeer(
        @Query("action") action: String = "list_users"
    ): ServerResponse

    /**
     * Авторизация (если используется расширенная таблица пользователей).
     */
    @POST("index.php")
    suspend fun serverLogin(
        @Query("action") action: String = "login",
        @Body credentials: Map<String, String>
    ): ServerResponse

    /**
     * Регистрация нового аккаунта.
     */
    @POST("index.php")
    suspend fun serverRegister(
        @Query("action") action: String = "add_user",
        @Body payload: UserPayload
    ): ServerResponse
}
