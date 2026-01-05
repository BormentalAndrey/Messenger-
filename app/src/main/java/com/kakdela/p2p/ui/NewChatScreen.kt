package com.kakdela.p2p.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.google.firebase.Timestamp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.kakdela.p2p.data.Chat

@Composable
fun NewChatScreen(navController: NavHostController) {
    val context = LocalContext.current
    var phoneNumber by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val currentUserId = Firebase.auth.currentUser?.uid ?: return

    // Для групповых чатов: список выбранных пользователей (UID)
    val selectedUsers = remember { mutableStateListOf<String>() }
    val availableUsers = remember { mutableStateListOf<Pair<String, String>>() } // UID + Phone

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Top
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

                Firebase.firestore.collection("users")
                    .whereEqualTo("phoneNumber", phoneNumber)
                    .get()
                    .addOnSuccessListener { documents ->
                        isLoading = false
                        if (documents.isEmpty) {
                            error = "Пользователь с таким номером не найден"
                            return@addOnSuccessListener
                        }

                        val targetUserId = documents.documents[0].id

                        if (targetUserId == currentUserId) {
                            error = "Нельзя начать чат с собой"
                            return@addOnSuccessListener
                        }

                        val participants = listOf(currentUserId, targetUserId).sorted()
                        val chatId = participants.joinToString("_")

                        // Создаём чат, если не существует
                        Firebase.firestore.collection("chats").document(chatId)
                            .set(
                                mapOf(
                                    "participantIds" to participants,
                                    "title" to phoneNumber, // Можно заменить на имя из профиля
                                    "lastMessage" to "",
                                    "timestamp" to Timestamp.now()
                                )
                            )
                            .addOnSuccessListener {
                                navController.navigate("chat/$chatId") {
                                    popUpTo("chats") { inclusive = false }
                                }
                            }
                    }
                    .addOnFailureListener {
                        isLoading = false
                        error = "Ошибка поиска"
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

        Spacer(modifier = Modifier.height(32.dp))

        // ==== Секция для группового чата ====
        Text("Выберите участников для группы:")
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(availableUsers) { (uid, phone) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .clickable {
                            if (selectedUsers.contains(uid)) selectedUsers.remove(uid)
                            else selectedUsers.add(uid)
                        },
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(phone)
                    if (selectedUsers.contains(uid)) Text("✓", color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        if (selectedUsers.size > 1) {
            Button(
                onClick = {
                    val allParticipants = selectedUsers + currentUserId
                    val chatData = Chat(
                        isGroup = true,
                        title = "Групповой чат", // Можно добавить ввод названия
                        adminIds = listOf(currentUserId),
                        participantIds = allParticipants
                    )

                    Firebase.firestore.collection("chats")
                        .add(chatData)
                        .addOnSuccessListener { doc ->
                            navController.navigate("chat/${doc.id}")
                        }
                        .addOnFailureListener {
                            Toast.makeText(context, "Ошибка создания группы", Toast.LENGTH_SHORT).show()
                        }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Создать группу (${selectedUsers.size})")
            }
        }
    }

    // ==== Загрузка списка всех пользователей для групп (кроме себя) ====
    LaunchedEffect(Unit) {
        Firebase.firestore.collection("users")
            .get()
            .addOnSuccessListener { docs ->
                availableUsers.clear()
                docs.documents.forEach { doc ->
                    val uid = doc.id
                    val phone = doc.getString("phoneNumber") ?: ""
                    if (uid != currentUserId) availableUsers.add(uid to phone)
                }
            }
    }
}
