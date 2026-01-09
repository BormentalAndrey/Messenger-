package com.kakdela.p2p.data

import android.content.Context
import com.kakdela.p2p.api.MyServerApi
import com.kakdela.p2p.api.UserPayload
import com.kakdela.p2p.data.local.ChatDatabase
import com.kakdela.p2p.data.local.NodeEntity
import com.kakdela.p2p.security.CryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.security.MessageDigest

class AuthManager(private val context: Context) {

    private val nodeDao = ChatDatabase.getDatabase(context).nodeDao()

    private val api: MyServerApi by lazy {
        Retrofit.Builder()
            .baseUrl("http://kakdela.infinityfree.me/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MyServerApi::class.java)
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    /**
     * 🔐 Вход пользователя
     */
    suspend fun login(email: String, password: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val passHash = sha256(password)

                // 1️⃣ Локальный оффлайн-вход
                val localUser = nodeDao.getUserByEmail(email)
                if (localUser != null && localUser.passwordHash == passHash) {
                    return@withContext true
                }

                // 2️⃣ Онлайн-вход (JSON → String)
                val json = JSONObject().apply {
                    put("email", email)
                    put("passwordHash", passHash)
                }

                val response = api.serverLogin(json.toString())

                if (response.success && response.userNode != null) {
                    saveUserToLocalDb(response.userNode, email, passHash)
                    return@withContext true
                }

                false
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }

    /**
     * 🆕 Регистрация пользователя
     */
    suspend fun register(
        email: String,
        password: String,
        phone: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val passHash = sha256(password)
            val myId = sha256(CryptoManager.getMyPublicKeyStr())

            val payload = UserPayload(
                hash = myId,
                ip = "0.0.0.0",
                port = 8888,
                publicKey = CryptoManager.getMyPublicKeyStr(),
                phone = phone,
                email = email,
                passwordHash = passHash
            )

            // ⚠️ Сервер принимает String
            val response = api.serverRegister(JSONObject().apply {
                put("hash", payload.hash)
                put("ip", payload.ip)
                put("port", payload.port)
                put("publicKey", payload.publicKey)
                put("phone", payload.phone)
                put("email", payload.email)
                put("passwordHash", payload.passwordHash)
            }.toString())

            if (response.success && response.userNode != null) {
                saveUserToLocalDb(response.userNode, email, passHash)
                return@withContext true
            }

            false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 💾 Сохранение пользователя локально
     */
    private suspend fun saveUserToLocalDb(
        node: UserPayload,
        email: String,
        passHash: String
    ) {
        nodeDao.insert(
            NodeEntity(
                userHash = node.hash.orEmpty(),
                email = email,
                passwordHash = passHash,
                phone = node.phone.orEmpty(),
                ip = node.ip ?: "0.0.0.0",
                port = node.port,
                publicKey = node.publicKey.orEmpty(),
                lastSeen = System.currentTimeMillis()
            )
        )
    }
}
