package com.kakdela.p2p.data

import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

class AuthManager {
    private val db = Firebase.firestore
    private val auth = Firebase.auth

    suspend fun completeSignIn(userName: String, phoneNumber: String): Boolean {
        return try {
            // 1. Авторизация (получаем UID)
            if (auth.currentUser == null) {
                auth.signInAnonymously().await()
            }

            val uid = auth.currentUser?.uid ?: return false

            // 2. Исправление номера (Нормализация)
            val digitsOnly = phoneNumber.filter { it.isDigit() }
            val cleanPhone = if (digitsOnly.startsWith("8") && digitsOnly.length == 11) {
                "7" + digitsOnly.substring(1)
            } else {
                digitsOnly
            }

            // 3. Сохранение в базу
            val userRef = db.collection("users").document(uid)
            val snapshot = userRef.get().await()
            
            val userData = mapOf(
                "uid" to uid,
                "name" to userName,
                "phoneNumber" to cleanPhone,
                "status" to "online",
                "createdAt" to System.currentTimeMillis()
            )

            if (!snapshot.exists()) {
                userRef.set(userData).await()
            } else {
                userRef.update(mapOf(
                    "status" to "online",
                    "name" to userName,
                    "phoneNumber" to cleanPhone
                )).await()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
