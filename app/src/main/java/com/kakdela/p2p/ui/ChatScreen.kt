package com.kakdela.p2p.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kakdela.p2p.R
import com.kakdela.p2p.data.Message
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ChatScreen(
    chatId: String,
    currentUserId: String
) {
    val viewModel: ChatViewModel = viewModel()
    val messages by viewModel.messages.collectAsState()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }

    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    LaunchedEffect(chatId) {
        viewModel.start(chatId)
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(messages.lastIndex)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Неоновый фон
        Image(
            painter = painterResource(id = R.drawable.chat_background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.6f
        )

        Column(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(messages) { message ->
                    val isOwn = message.senderId == currentUserId

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start
                    ) {
                        if (!isOwn) {
                            AvatarPlaceholder(
                                name = if (chatId == "global") "Ч" else "С"  // "Ч" для ЧёКаВо?, "С" для собеседника
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        Column(
                            horizontalAlignment = if (isOwn) Alignment.End else Alignment.Start
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (isOwn) MaterialTheme.colorScheme.primary else Color(0xFF2A2A2A),
                                modifier = Modifier.widthIn(max = 300.dp)
                            ) {
                                Text(
                                    text = message.text,
                                    modifier = Modifier.padding(12.dp),
                                    color = if (isOwn) Color.Black else Color.White,
                                    fontSize = 16.sp
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 4.dp, start = 12.dp, end = 12.dp)
                            ) {
                                if (isOwn) {
                                    // Галочки
                                    if (message.isRead) {
                                        Icon(
                                            painter = painterResource(android.R.drawable.stat_notify_chat),
                                            contentDescription = "Прочитано",
                                            tint = Color(0xFF00FFF0),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    } else if (message.isDelivered) {
                                        Icon(
                                            painter = painterResource(android.R.drawable.stat_notify_chat),
                                            contentDescription = "Доставлено",
                                            tint = Color.Gray,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    } else {
                                        Icon(
                                            painter = painterResource(android.R.drawable.presence_away),
                                            contentDescription = "Отправлено",
                                            tint = Color.Gray,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(4.dp))
                                }

                                Text(
                                    text = timeFormat.format(Date(message.timestamp)),
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                        }

                        if (isOwn) {
                            Spacer(modifier = Modifier.width(8.dp))
                            AvatarPlaceholder("Я")
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .background(Color(0xFF1A1A1A), RoundedCornerShape(24.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .weight(1f)
                        .padding(12.dp),
                    decorationBox = { inner ->
                        if (text.isEmpty()) {
                            Text(
                                text = stringResource(R.string.enter_message),
                                color = Color.Gray,
                                fontSize = 16.sp
                            )
                        }
                        inner()
                    }
                )

                IconButton(
                    onClick = {
                        if (text.isNotBlank()) {
                            viewModel.send(
                                chatId,
                                Message(
                                    text = text,
                                    senderId = currentUserId,
                                    isDelivered = false,
                                    isRead = false
                                )
                            )
                            text = ""
                        }
                    }
                ) {
                    Icon(
                        painter = painterResource(android.R.drawable.ic_menu_send),
                        contentDescription = "Отправить",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun AvatarPlaceholder(name: String) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name.firstOrNull()?.uppercase() ?: "?",
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )
    }
}
