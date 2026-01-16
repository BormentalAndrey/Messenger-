package com.kakdela.p2p.api

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

/**
 * Основной класс, описывающий пользователя/узел.
 * Аннотация @Keep гарантирует, что названия полей не будут изменены при сборке Release версии.
 */
@Keep
data class UserPayload(
    @SerializedName("hash") 
    val hash: String,

    @SerializedName("phone_hash") 
    val phone_hash: String? = null,

    @SerializedName("ip") 
    val ip: String = "0.0.0.0", // В PHP это обязательное поле

    @SerializedName("port") 
    val port: Int = 8888, // В PHP это обязательное поле (int)

    @SerializedName("publicKey") 
    val publicKey: String, // В PHP это обязательное поле

    @SerializedName("phone") 
    val phone: String? = null,

    @SerializedName("email") 
    val email: String? = null,

    @SerializedName("lastSeen") 
    val lastSeen: Long = System.currentTimeMillis() // Используем Long для соответствия BIGINT/Timestamp
)

/**
 * Обертка для регистрации пользователя.
 * Используйте этот класс, если ваш API ожидает вложенную структуру {"hash": "...", "data": {...}}
 */
@Keep
data class UserRegistrationWrapper(
    @SerializedName("hash") 
    val hash: String,
    
    @SerializedName("data") 
    val data: UserPayload? = null
)

/**
 * Ответ сервера на запросы пользователя.
 * Полностью соответствует JSON-ответу вашего api.php.
 */
@Keep
data class ServerResponse(
    @SerializedName("success") 
    val success: Boolean = false,

    @SerializedName("users") 
    val users: List<UserPayload>? = null, // Список узлов для действия get_nodes

    @SerializedName("status") 
    val status: String? = null,

    @SerializedName("error") 
    val error: String? = null,
    
    // Поля для отладки, если они включены в PHP (опционально)
    @SerializedName("debug_received_body")
    val debugBody: String? = null,
    
    @SerializedName("action_received")
    val actionReceived: String? = null
)
