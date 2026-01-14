package com.kakdela.p2p.api

import com.google.gson.annotations.SerializedName

/**
 * Основная модель данных пользователя/узла в сети.
 */
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

/**
 * Обертка для регистрации или обновления данных узла на сервере.
 * Исправляет ошибки: No value passed for parameter 'data'
 */
data class UserRegistrationWrapper(
    @SerializedName("hash") val hash: String,
    @SerializedName("data") val data: UserPayload? = null
)

/**
 * Стандартный ответ сервера на запросы поиска и синхронизации.
 */
data class ServerResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("users") val users: List<UserPayload>? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("error") val error: String? = null
)
