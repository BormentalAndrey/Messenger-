package com.kakdela.p2p.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kakdela.p2p.auth.AuthManager
import com.kakdela.p2p.data.Message

@Composable
fun ChatScreen(
    chatId: String,
    viewModel: ChatViewModel = viewModel()
) {
    val messages by viewModel.messages.collectAsState()
    var text by remember { mutableStateOf("") }

    LaunchedEffect(chatId) {
        viewModel.start(chatId)
    }

    Column(modifier = Modifier.fillMaxSize()) {

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(8.dp)
        ) {
            items(messages) { message ->
                MessageItem(message)
            }
        }

        Row(
            modifier = Modifier.padding(8.dp)
        ) {
            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Сообщение") }
            )

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = {
                    if (text.isNotBlank()) {
                        AuthManager.ensureUser { uid ->
                            viewModel.send(
                                chatId,
                                Message(
                                    text = text,
                                    senderId = uid
                                )
                            )
                            text = ""
                        }
                    }
                }
            ) {
                Text("Send")
            }
        }
    }
}
