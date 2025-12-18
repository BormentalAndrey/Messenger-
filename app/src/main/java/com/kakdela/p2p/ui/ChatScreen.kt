package com.kakdela.p2p.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import com.kakdela.p2p.model.ChatMessage

// Неоновые цвета
private val NeonCyan = Color(0xFF00FFFF)
private val NeonMagenta = Color(0xFFFF00FF)
private val NeonPink = Color(0xFFE91E63)

@Composable
fun ChatScreen(
    chatId: String,
    messages: List<ChatMessage>,
    onSendMessage: (String) -> Unit,
    onScheduleMessage: (String, Long) -> Unit
) {
    var textState by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A))) { // Тёмный фон для неона
        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            reverseLayout = false
        ) {
            items(messages) { message ->
                ChatBubble(message)
            }
        }

        // Полупрозрачная нижняя панель с лёгким блюром
        Surface(
            modifier = Modifier.blur(12.dp), // Glass-эффект
            color = Color.Black.copy(alpha = 0.4f),
            elevation = 16.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
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
                    colors = TextFieldDefaults.textFieldColors(
                        backgroundColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        textColor = Color.White
                    )
                )

                NeonIconButton(onClick = {
                    val timeInMs = System.currentTimeMillis() + 10000
                    onScheduleMessage(textState, timeInMs)
                    textState = ""
                }) {
                    Icon(Icons.Outlined.Schedule, contentDescription = "Schedule", tint = NeonCyan)
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
    }
}

// Кастомная неоновая кнопка с многослойным glow
@Composable
fun NeonIconButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .shadow(8.dp, CircleShape, clip = false, ambientColor = NeonMagenta, spotColor = NeonMagenta)
            .shadow(16.dp, CircleShape, clip = false, ambientColor = NeonCyan, spotColor = NeonCyan)
            .shadow(24.dp, CircleShape, clip = false, ambientColor = NeonPink.copy(alpha = 0.6f), spotColor = NeonPink.copy(alpha = 0.6f))
            .padding(8.dp),
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
    val bubbleColor = if (isMe) NeonCyan.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.9f) // Лёгкий неон для своих
    val shape: Shape = if (isMe) {
        RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
    } else {
        RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
    }

    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = alignment) {
        Surface(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .shadow(8.dp, shape, clip = false, ambientColor = if (isMe) NeonCyan else Color.Transparent),
            shape = shape,
            color = bubbleColor
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(text = message.text, fontSize = 16.sp, color = if (isMe) Color.White else Color.Black)
                Text(
                    text = "12:00",
                    fontSize = 10.sp,
                    color = Color.Gray,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}                                        contentDescription = null,
                                        modifier = Modifier.fillMaxWidth().height(200.dp),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                            if (msg.text.isNotEmpty()) {
                                Text(text = msg.text, fontSize = 16.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

