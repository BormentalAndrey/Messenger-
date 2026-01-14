package com.kakdela.p2p.api

import com.google.gson.annotations.SerializedName

/**
 * Основной класс, описывающий пользователя.
 */
data class UserPayload(
    @SerializedName("hash") val hash: String,
    @SerializedName("phone_hash") val phone_hash: String? = null,
    @SerializedName("ip") val ip: String? = "0.0.0.0", // дефолтный IP, безопасный для сети
    @SerializedName("port") val port: Int = 8888,
    @SerializedName("publicKey") val publicKey: String,
    @SerializedName("phone") val phone: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("lastSeen") val lastSeen: Long? = System.currentTimeMillis()
)

/**
 * Обертка для регистрации пользователя.
 * Поле data необязательное, чтобы можно было отправлять только hash.
 */
data class UserRegistrationWrapper(
    @SerializedName("hash") val hash: String,
    @SerializedName("data") val data: UserPayload? = null
)

/**
 * Ответ сервера на запросы пользователя.
 */
data class ServerResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("users") val users: List<UserPayload>? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("error") val error: String? = null
)
