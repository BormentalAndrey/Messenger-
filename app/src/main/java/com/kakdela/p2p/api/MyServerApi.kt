package com.kakdela.p2p.api

import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

// ================= MODELS =================

data class UserPayload(
    val hash: String?,
    val ip: String?,
    val port: Int,
    val publicKey: String?,
    val phone: String?,
    val email: String? = null,
    val passwordHash: String? = null
)

data class ServerResponse(
    val success: Boolean = false,
    val ip: String? = null,
    val publicKey: String? = null,
    val hash: String? = null,
    val userNode: UserPayload? = null
)

// ================= API =================

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

    // Исправлено: поддержка поиска через Hash (используется в IdentityRepository)
    @POST("api.php")
    suspend fun findPeer(
        @Query("action") action: String = "get_peer",
        @Body payload: Map<String, String>
    ): ServerResponse

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
