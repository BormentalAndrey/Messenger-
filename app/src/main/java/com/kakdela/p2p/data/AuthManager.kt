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
            // Если пользователь не авторизован в Firebase, делаем это анонимно
            // Это даст нам UID, необходимый для создания документа в Firestore
            if (auth.currentUser == null) {
                auth.signInAnonymously().await()
            }

            val uid = auth.currentUser?.uid ?: return false
            val cleanPhone = phoneNumber.filter { it.isDigit() }

            val userRef = db.collection("users").document(uid)
            val snapshot = userRef.get().await()
            
            if (!snapshot.exists()) {
                val newUser = mapOf(
                    "uid" to uid,
                    "name" to userName,
                    "phoneNumber" to cleanPhone,
                    "status" to "online",
                    "createdAt" to System.currentTimeMillis()
                )
                userRef.set(newUser).await()
            } else {
                userRef.update(mapOf(
                    "status" to "online",
                    "name" to userName
                )).await()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false // Если сработает этот блок, в приложении будет "Ошибка при создании профиля"
        }
    }
}

