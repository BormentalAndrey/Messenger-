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
import com.kakdela.p2p.data.Chat
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ChatsListScreen(
    navController: NavHostController,
    viewModel: ChatsListViewModel = viewModel()
) {
    val chats by viewModel.chats.collectAsState()
    val currentUserId = Firebase.auth.currentUser?.uid ?: ""

    LaunchedEffect(Unit) {
        viewModel.loadChats(currentUserId)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Как дела?", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate("new_chat") },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Text("+", fontSize = 24.sp)
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black)
        ) {
            items(chats, key = { it.id }) { chat ->
                ChatListItem(
                    chat = chat,
                    currentUserId = currentUserId,
                    onClick = {
                        navController.navigate("chat/${chat.id}")
                    }
                )
            }
        }
    }
}

@Composable
fun ChatListItem(
    chat: Chat,
    currentUserId: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Аватарка-заглушка
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
                maxLines = 1,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = chat.timestamp?.let {
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(it)
                } ?: "",
                color = Color.Gray,
                fontSize = 12.sp
            )
            if (chat.unreadCount > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Badge(containerColor = MaterialTheme.colorScheme.primary) {
                    Text(chat.unreadCount.toString())
                }
            }
        }
    }

    HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.3f))
}
