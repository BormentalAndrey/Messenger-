package com.kakdela.p2p.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

@Composable
fun NewChatScreen(navController: NavHostController) {
    var phoneNumber by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val currentUserId = Firebase.auth.currentUser?.uid ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Новый чат", style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = phoneNumber,
            onValueChange = { phoneNumber = it },
            label = { Text("Номер телефона собеседника (+7...)") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (phoneNumber.isBlank()) {
                    error = "Введите номер"
                    return@Button
                }
                isLoading = true
                error = null

                // 1. Ищем пользователя по номеру в коллекции users
                Firebase.firestore.collection("users")
                    .whereEqualTo("phoneNumber", phoneNumber)
                    .get()
                    .addOnSuccessListener { documents ->
                        if (documents.isEmpty) {
                            error = "Пользователь с таким номером не найден"
                            isLoading = false
                            return@addOnSuccessListener
                        }

                        val targetUserId = documents.documents[0].id

                        if (targetUserId == currentUserId) {
                            error = "Нельзя начать чат с собой"
                            isLoading = false
                            return@addOnSuccessListener
                        }

                        // 2. Создаём уникальный chatId
                        val participants = listOf(currentUserId, targetUserId).sorted()
                        val chatId = participants.joinToString("_")

                        // 3. Создаём документ чата (если не существует)
                        Firebase.firestore.collection("chats").document(chatId)
                            .set(mapOf(
                                "participantIds" to participants,
                                "title" to phoneNumber,  // Или имя из профиля
                                "lastMessage" to "",
                                "timestamp" to com.google.firebase.Timestamp.now()
                            ))
                            .addOnSuccessListener {
                                navController.navigate("chat/$chatId") {
                                    popUpTo("chats") { inclusive = false }
                                }
                            }

                        isLoading = false
                    }
                    .addOnFailureListener {
                        error = "Ошибка поиска"
                        isLoading = false
                    }
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp))
            else Text("Начать чат")
        }

        error?.let {
            Spacer(modifier = Modifier.height(16.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}
