package com.kakdela.p2p.data

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.kakdela.p2p.security.CryptoManager
import kotlinx.coroutines.tasks.await

class IdentityRepository(private val context: Context) {
    // CryptoManager - это object, его не нужно создавать через конструктор
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    suspend fun publishIdentity(phoneNumber: String, name: String): Boolean {
        return try {
            if (auth.currentUser == null) {
                auth.signInAnonymously().await()
            }

            // Используем статические методы объекта CryptoManager
            // (Предполагается, что в CryptoManager.kt вы используете lazy инициализацию ключей внутри)
            val userId = "user_${phoneNumber.hashCode()}"
            val phoneHash = phoneNumber.hashCode().toString()
            
            // Получаем публичный ключ через метод объекта
            val publicKeyset = CryptoManager.getMyPublicKeyStr()

            val dhtRecord = mapOf(
                "user_id" to userId,
                "phone_hash" to phoneHash,
                "public_key" to publicKeyset,
                "display_name" to name,
                "last_seen" to System.currentTimeMillis()
            )

            db.collection("dht_identities")
                .document(phoneHash)
                .set(dhtRecord)
                .await()
                
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun findPeerByPhone(phone: String): AppContact? {
        val phoneHash = phone.hashCode().toString()
        val doc = db.collection("dht_identities").document(phoneHash).get().await()
        
        return if (doc.exists()) {
            AppContact(
                name = doc.getString("display_name") ?: "Unknown",
                phoneNumber = phone,
                uid = doc.getString("user_id"),
                publicKey = doc.getString("public_key"),
                isRegistered = true
            )
        } else {
            null
        }
    }
}

