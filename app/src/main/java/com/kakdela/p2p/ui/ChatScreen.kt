package com.kakdela.p2p.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kakdela.p2p.model.ChatMessage

// Неоновые цвета
private val NeonCyan = Color(0xFF00FFFF)
private val NeonMagenta = Color(0xFFFF00FF)
private val NeonPink = Color(0xFFE91E63)
private val DarkBackground = Color(0xFF0A0A0A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatId: String,
    messages: List<ChatMessage>,
    onSendMessage: (String) -> Unit,
    onScheduleMessage: (String, Long) -> Unit
) {
    var textState by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text("ЧАТ: $chatId", 
                        color = NeonCyan, 
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.shadow(4.dp, ambientColor = NeonCyan, spotColor = NeonCyan)
                    ) 
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        bottomBar = {
            // Нижняя панель ввода с эффектом стекла
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding(),
                color = Color.Black.copy(alpha = 0.7f),
                tonalElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .padding(8.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NeonIconButton(onClick = { /* Прикрепить файл */ }) {
                        Icon(Icons.Outlined.AttachFile, contentDescription = "Attach", tint = NeonCyan)
                    }

                    TextField(
                        value = textState,
                        onValueChange = { textState = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Сообщение...", color = Color.Gray) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = NeonCyan,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    NeonIconButton(onClick = {
                        val timeInMs = System.currentTimeMillis() + 10000
                        onScheduleMessage(textState, timeInMs)
                        textState = ""
                    }) {
                        Icon(Icons.Outlined.Schedule, contentDescription = "Schedule", tint = NeonMagenta)
                    }

                    NeonIconButton(onClick = {
                        if (textState.isNotBlank()) {
                            onSendMessage(textState)
                            textState = ""
                        }
                    }) {
                        Icon(Icons.Filled.Send, contentDescription = "Send", tint = NeonCyan)
                    }
                }
            }
        },
        containerColor = DarkBackground
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 8.dp),
            reverseLayout = false
        ) {
            items(messages) { message ->
                ChatBubble(message)
            }
        }
    }
}

@Composable
fun NeonIconButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .shadow(4.dp, CircleShape, ambientColor = NeonCyan, spotColor = NeonCyan)
            .border(1.dp, NeonCyan.copy(alpha = 0.3f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        IconButton(onClick = onClick) {
            content()
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    val isMe = message.isMine
    val alignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart
    val neonColor = if (isMe) NeonCyan else NeonMagenta
    
    val shape: Shape = if (isMe) {
        RoundedCornerShape(16.dp, 16.dp, 2.dp, 16.dp)
    } else {
        RoundedCornerShape(16.dp, 16.dp, 16.dp, 2.dp)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        contentAlignment = alignment
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .border(1.dp, neonColor.copy(alpha = 0.5f), shape)
                .shadow(12.dp, shape, ambientColor = neonColor, spotColor = neonColor),
            shape = shape,
            color = if (isMe) Color.Black else Color(0xFF1A1A1A)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = message.text,
                    fontSize = 15.sp,
                    color = Color.White
                )
                
                Text(
                    text = "12:00", // Здесь можно добавить реальное время из message
                    fontSize = 10.sp,
                    color = neonColor.copy(alpha = 0.7f),
                    modifier = Modifier.align(Alignment.End).padding(top = 4.dp)
                )
            }
        }
    }
}

