package com.kakdela.p2p.data

import android.content.Context
import com.kakdela.p2p.api.MyServerApi
import com.kakdela.p2p.api.UserPayload
import com.kakdela.p2p.data.local.ChatDatabase
import com.kakdela.p2p.data.local.NodeEntity
import com.kakdela.p2p.security.CryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.security.MessageDigest

class AuthManager(private val context: Context) {

    private val nodeDao = ChatDatabase.getDatabase(context).nodeDao()

    private val api: MyServerApi by lazy {
        Retrofit.Builder()
            .baseUrl("http://kakdela.infinityfree.me/") // Используем единый домен
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MyServerApi::class.java)
    }

    private fun hashPassword(password: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(password.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    suspend fun login(email: String, password: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val inputPassHash = hashPassword(password)

            // 1. Оффлайн вход (из локальной Torrent-базы)
            val localUser = nodeDao.getUserByEmail(email)
            if (localUser != null && localUser.passwordHash == inputPassHash) {
                return@withContext true
            }

            // 2. Онлайн вход через сервер
            val credentials = mapOf(
                "email" to email,
                "passwordHash" to inputPassHash
            )
            val response = api.serverLogin(credentials)
            
            if (response.success && response.userNode != null) {
                saveUserToLocalDb(response.userNode, email, inputPassHash)
                return@withContext true
            }
            false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun register(email: String, password: String, phone: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val passHash = hashPassword(password)
            val myId = hashPassword(CryptoManager.getMyPublicKeyStr())
            
            val payload = UserPayload(
                hash = myId,
                ip = "0.0.0.0", // IP обновится при первом анонсе
                port = 8888,
                publicKey = CryptoManager.getMyPublicKeyStr(),
                phone = phone,
                email = email,
                passwordHash = passHash
            )

            val response = api.serverRegister(payload)
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

    private suspend fun saveUserToLocalDb(node: UserPayload, email: String, passHash: String) {
        nodeDao.insert(
            NodeEntity(
                userHash = node.hash ?: "",
                email = email,
                passwordHash = passHash,
                phone = node.phone ?: "",
                ip = node.ip ?: "0.0.0.0",
                port = node.port,
                publicKey = node.publicKey ?: "",
                lastSeen = System.currentTimeMillis()
            )
        )
    }
}
