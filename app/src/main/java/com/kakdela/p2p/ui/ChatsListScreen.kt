package com.kakdela.p2p.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ChatsListScreen(navController: NavHostController) {
    val viewModel: ChatsListViewModel = viewModel()
    val chats by viewModel.chats.collectAsState()
    val currentUserId = Firebase.auth.currentUser?.uid ?: ""

    LaunchedEffect(currentUserId) {
        if (currentUserId.isNotEmpty()) {
            viewModel.loadChats(currentUserId)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Как дела?",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Black
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    // Здесь можно открыть экран нового чата
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.Black
            ) {
                Text("+", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(innerPadding)
        ) {
            if (chats.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Нет чатов. Начните новый!",
                            color = Color.Gray,
                            fontSize = 18.sp
                        )
                    }
                }
            } else {
                items(chats) { chat ->
                    ChatListItem(chat = chat) {
                        navController.navigate("chat/${chat.id}")
                    }
                }
            }
        }
    }
}

data class ChatDisplay(
    val id: String,
    val title: String,
    val lastMessage: String,
    val time: String
)

@Composable
fun ChatListItem(chat: ChatDisplay, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = chat.title.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = chat.title,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = chat.lastMessage.ifEmpty { "Нет сообщений" },
                color = Color.Gray,
                fontSize = 14.sp,
                maxLines = 1
            )
        }

        Text(
            text = chat.time,
            color = Color.Gray,
            fontSize = 12.sp
        )
    }

    HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.3f))
}
