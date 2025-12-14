package com.kakdela.p2p.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kakdela.p2p.auth.AuthManager
import com.kakdela.p2p.data.Message

@Composable
fun ChatScreen(
    chatId: String,
    viewModel: ChatViewModel = viewModel(key = chatId)
) {
    val messages by viewModel.messages.collectAsState()
    var text by remember { mutableStateOf("") }

    // ✅ Запуск при смене chatId
    LaunchedEffect(chatId) {
        viewModel.start(chatId)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {

        // 📜 Список сообщений
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            reverseLayout = true,
            contentPadding = PaddingValues(8.dp)
        ) {
            items(messages.reversed()) { message ->
                MessageBubble(message)
            }
        }

        // ✉️ Поле ввода
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Сообщение") },
                singleLine = true
            )

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                enabled = text.isNotBlank(),
                onClick = {
                    AuthManager.ensureUser { uid ->
                        viewModel.send(
                            chatId,
                            Message(
                                text = text.trim(),
                                senderId = uid,
                                timestamp = System.currentTimeMillis()
                            )
                        )
                        text = ""
                    }
                }
            ) {
                Text("Отправить")
            }
        }
    }
}

@Composable
private fun MessageBubble(message: Message) {
    val isMine = AuthManager.currentUserId == message.senderId

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (isMine)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.padding(4.dp)
        ) {
            Text(
                text = message.text,
                modifier = Modifier.padding(12.dp),
                color = if (isMine) Color.White else Color.Black
            )
        }
    }
}
