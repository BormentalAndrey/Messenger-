package com.kakdela.p2p.api

import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

// Модель данных для обмена с сервером (Torrent-лист)
data class UserPayload(
    val hash: String?,
    val ip: String?,
    val port: Int,
    val publicKey: String?,
    val phone: String?,
    val email: String? = null,         // Добавлено для синхронизации профилей
    val passwordHash: String? = null   // Добавлено для возможности оффлайн-входа
)

data class ServerResponse(
    val status: String? = null,
    val success: Boolean = false,      // Для AuthManager (response.success)
    val ip: String? = null,
    val publicKey: String? = null,
    val phone: String? = null,
    val userNode: UserPayload? = null  // Для входа: сервер может вернуть весь объект
)

interface MyServerApi {
    
    // Анонс себя в сети
    @POST("api.php")
    suspend fun announceSelf(
        @Query("action") action: String = "add_user",
        @Body payload: UserPayload
    ): ServerResponse

    // Получение списка 2500 узлов (Torrent-режим)
    @POST("api.php")
    suspend fun getAllNodes(
        @Query("action") action: String = "get_all_nodes"
    ): List<UserPayload>

    // Поиск конкретного пира по хешу
    @POST("api.php")
    suspend fun findPeer(
        @Query("action") action: String = "get_peer",
        @Body payload: Map<String, String>
    ): ServerResponse

    // Методы для AuthManager (исправляют Unresolved reference: serverLogin/serverRegister)
    @POST("api.php")
    suspend fun serverLogin(
        @Query("action") action: String = "login",
        @Body credentials: Map<String, String>
    ): ServerResponse

    @POST("api.php")
    suspend fun serverRegister(
        @Query("action") action: String = "register",
        @Body payload: UserPayload
    ): ServerResponse
}
