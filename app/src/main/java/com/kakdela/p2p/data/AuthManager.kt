package com.kakdela.p2p.data

import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

class AuthManager {
    private val db = Firebase.firestore
    private val auth = Firebase.auth

    // Функция, которая вызывается при нажатии кнопки "Войти"
    suspend fun completeSignIn(userName: String, phoneNumber: String): Boolean {
        val uid = auth.currentUser?.uid ?: return false
        val cleanPhone = phoneNumber.filter { it.isDigit() }

        val userRef = db.collection("users").document(uid)
        
        // Проверяем, существует ли уже такой пользователь
        val snapshot = userRef.get().await()
        
        if (!snapshot.exists()) {
            // Если пользователя нет — создаем новую запись
            val newUser = mapOf(
                "uid" to uid,
                "name" to userName,
                "phoneNumber" to cleanPhone,
                "status" to "online",
                "createdAt" to System.currentTimeMillis()
            )
            userRef.set(newUser).await()
        } else {
            // Если есть — просто обновляем статус и имя (если оно изменилось)
            userRef.update(mapOf(
                "status" to "online",
                "name" to userName
            )).await()
        }
        return true
    }
}

