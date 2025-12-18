package com.kakdela.p2p.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Модель сообщения
data class Message(
    val text: String,
    val isMine: Boolean,
    val time: String = "12:00"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen() {
    val messages = remember { mutableStateListOf(
        Message("Привет! Как тебе неоновый стиль?", false, "10:05"),
        Message("Выглядит круто!", true, "10:06")
    ) }
    var textState by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("ГЛОБАЛЬНЫЙ ЧАТ", fontWeight = FontWeight.Bold, color = Color(0xFF00FFF0)) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color.Black // Полностью черный фон
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Список сообщений
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                reverseLayout = false
            ) {
                items(messages) { msg ->
                    MessageBubble(msg)
                }
            }

            // Поле ввода
            ChatInput(
                text = textState,
                onTextChange = { textState = it },
                onSend = {
                    if (textState.isNotBlank()) {
                        messages.add(Message(textState, true, "12:05"))
                        textState = ""
                    }
                }
            )
        }
    }
}

@Composable
fun MessageBubble(message: Message) {
    val alignment = if (message.isMine) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleColor = if (message.isMine) Color(0xFF000000) else Color(0xFF1A1A1A)
    val neonColor = if (message.isMine) Color(0xFF00FFF0) else Color(0xFFFF00C8)
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = alignment
    ) {
        Column(horizontalAlignment = alignment) {
            Surface(
                color = bubbleColor,
                shape = RoundedCornerShape(
                    topStart = 16.dp, 
                    topEnd = 16.dp, 
                    bottomStart = if (message.isMine) 16.dp else 0.dp,
                    bottomEnd = if (message.isMine) 0.dp else 16.dp
                ),
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .border(1.dp, neonColor, RoundedCornerShape(16.dp)) // Неоновая обводка
                    .shadow(elevation = 4.dp, spotColor = neonColor) // Эффект свечения
            ) {
                Text(
                    text = message.text,
                    color = Color.White,
                    modifier = Modifier.padding(12.dp),
                    fontSize = 16.sp
                )
            }
            Text(
                text = message.time,
                color = neonColor.copy(alpha = 0.6f),
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
fun ChatInput(text: String, onTextChange: (String) -> Unit, onSend: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .navigationBarsPadding() // Чтобы не перекрывалось системной полоской
            .imePadding(), // Чтобы поднималось вместе с клавиатурой
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier
                .weight(1f)
                .border(1.dp, Color(0xFF00FFF0), RoundedCornerShape(24.dp)),
            placeholder = { Text("Сообщение...", color = Color.Gray) },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF0D0D0D),
                unfocusedContainerColor = Color(0xFF0D0D0D),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = Color.White
            ),
            shape = RoundedCornerShape(24.dp)
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        IconButton(
            onClick = onSend,
            modifier = Modifier
                .background(Color(0xFF00FFF0), CircleShape)
                .size(48.dp)
        ) {
            Icon(Icons.Default.Send, contentDescription = null, tint = Color.Black)
        }
    }
}

