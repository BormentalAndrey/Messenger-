package com.kakdela.p2p.data

import android.util.Log
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.kakdela.p2p.data.remote.WebViewApiClient
import com.kakdela.p2p.security.CryptoManager
import kotlinx.coroutines.tasks.await
import java.util.*

class UserManager(
    private val identityRepo: IdentityRepository // Добавляем репозиторий для получения Hash и IP
) {
    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private val TAG = "UserManager"

    suspend fun saveUserToFirestore(name: String, rawPhoneNumber: String) {
        val uid = auth.currentUser?.uid ?: return
        
        // 1. Очищаем номер: оставляем только цифры
        val cleanPhoneNumber = rawPhoneNumber.filter { it.isDigit() }

        // --- ЛОГИКА FIRESTORE ---
        val userData = mapOf(
            "uid" to uid,
            "name" to name,
            "phoneNumber" to cleanPhoneNumber,
            "createdAt" to System.currentTimeMillis(),
            "status" to "online"
        )

        try {
            db.collection("users")
                .document(uid)
                .set(userData, com.google.firebase.firestore.SetOptions.merge())
                .await()
            Log.d(TAG, "User saved to Firestore successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Firestore save failed", e)
        }

        // --- ЛОГИКА MYSQL (API.PHP) ---
        // Синхронизируем данные с твоим хостингом
        try {
            val myHash = identityRepo.getMyId() // Твой уникальный Hash (P2P ID)
            val myPublicKey = CryptoManager.getMyPublicKey() // Твой публичный ключ
            
            val payload = UserPayload(
                hash = myHash,
                phone_hash = cleanPhoneNumber, // Используем номер как идентификатор для поиска
                publicKey = myPublicKey,
                ip = identityRepo.getCurrentIp(), // Метод должен быть в IdentityRepository
                port = 8888, // Твой стандартный UDP порт
                phone = cleanPhoneNumber,
                lastSeen = System.currentTimeMillis()
            )

            // Отправляем данные на твой api.php
            val response = WebViewApiClient.apiService.announce(payload)
            if (response.success) {
                Log.d(TAG, "User announced to MySQL successfully")
            } else {
                Log.e(TAG, "MySQL error: ${response.error}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync with MySQL api.php", e)
        }
    }
}
