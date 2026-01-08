package com.kakdela.p2p.api

import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

// Модель данных для обмена с сервером
data class UserPayload(
    val hash: String,
    val ip: String,
    val port: Int,
    val publicKey: String,
    val phone: String // Важно: отправляем свой номер
)

data class ServerResponse(
    val status: String? = null,
    val ip: String? = null,
    val publicKey: String? = null,
    val phone: String? = null
)

interface MyServerApi {
    @POST("api.php")
    suspend fun announceSelf(
        @Query("action") action: String = "add_user",
        @Body payload: UserPayload
    ): ServerResponse

    @POST("api.php")
    suspend fun getAllNodes(
        @Query("action") action: String = "get_all_nodes"
    ): List<UserPayload>

    @POST("api.php")
    suspend fun findPeer(
        @Query("action") action: String = "get_peer",
        @Body payload: Map<String, String>
    ): ServerResponse
}
