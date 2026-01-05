package com.kakdela.p2p.data

import android.content.Context
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.kakdela.p2p.security.CryptoManager
import kotlinx.coroutines.tasks.await

/**
 * Реализация Identity Layer (Пункты 4 и 5 ТЗ).
 * Отвечает за публикацию анонимного профиля в сеть.
 */
class IdentityRepository(context: Context) {
    private val crypto = CryptoManager(context)
    private val db = Firebase.firestore
    private val auth = Firebase.auth

    // 1. "Регистрация" - это публикация ключа в DHT
    suspend fun publishIdentity(phoneNumber: String, name: String): Boolean {
        return try {
            // Анонимный вход в сеть (чтобы иметь право писать в DHT)
            if (auth.currentUser == null) {
                auth.signInAnonymously().await()
            }

            // Генерируем ID и Хеш номера
            val userId = crypto.getMyUserId()
            val phoneHash = crypto.hashPhoneNumber(phoneNumber)
            
            // Сериализуем публичный ключ для публикации
            // (В реальном коде это JSON от Tink public keyset)
            val publicKeyset = crypto.getMyKeys().publicKeysetHandle.toString()

            val dhtRecord = mapOf(
                "user_id" to userId,
                "phone_hash" to phoneHash,
                "public_key" to publicKeyset, // Для E2EE
                "display_name" to name, // Опционально, можно шифровать
                "last_seen" to System.currentTimeMillis()
            )

            // Публикуем в "DHT" (коллекция identities)
            // Ключом документа делаем Хеш Номера, чтобы искать O(1)
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

    // 2. Поиск контакта по номеру (Пункт 5.2 и 5.3)
    suspend fun findPeerByPhone(phone: String): AppContact? {
        val phoneHash = crypto.hashPhoneNumber(phone)
        
        val doc = db.collection("dht_identities").document(phoneHash).get().await()
        
        return if (doc.exists()) {
            AppContact(
                name = doc.getString("display_name") ?: "Unknown",
                phoneNumber = phone,
                uid = doc.getString("user_id"), // Это ID на основе ключа
                publicKey = doc.getString("public_key"),
                isRegistered = true
            )
        } else {
            null
        }
    }
}
