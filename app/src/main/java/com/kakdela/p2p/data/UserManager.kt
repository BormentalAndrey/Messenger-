package com.kakdela.p2p.data

import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

class UserManager {
    private val db = Firebase.firestore
    private val auth = Firebase.auth

    suspend fun saveUserToFirestore(name: String, rawPhoneNumber: String) {
        val uid = auth.currentUser?.uid ?: return
        
        // Очищаем номер: оставляем только цифры для удобного поиска
        val cleanPhoneNumber = rawPhoneNumber.filter { it.isDigit() }

        val userData = mapOf(
            "uid" to uid,
            "name" to name,
            "phoneNumber" to cleanPhoneNumber,
            "createdAt" to System.currentTimeMillis(),
            "status" to "online"
        )

        try {
            // Используем .set() с merge, чтобы не затереть данные, если пользователь заходит повторно
            db.collection("users")
                .document(uid)
                .set(userData, com.google.firebase.firestore.SetOptions.merge())
                .await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

